package net.pakkanannai.mcfabricmod;

import com.bridge.minecraft.protocol.MessageHeader;
import com.bridge.minecraft.protocol.MessageType;
import com.bridge.minecraft.protocol.Messages;
import com.bridge.minecraft.protocol.PacketReader;
import com.bridge.minecraft.protocol.PacketWriter;
import com.bridge.minecraft.protocol.ProtocolConstants;
import com.bridge.minecraft.protocol.ProtocolException;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns the guest's reconnecting background TCP control connection. */
public final class GuestControlClient implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftBridge");
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 47653;
    private static final int CONNECT_TIMEOUT_MS = 1500;
    private static final long HEARTBEAT_INTERVAL_MS = 2000;
    private static final long HEARTBEAT_TIMEOUT_MS = 6000;
    private static final long INITIAL_RECONNECT_DELAY_MS = 500;
    private static final long MAX_RECONNECT_DELAY_MS = 5000;

    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final Object outputLock = new Object();
    private final Object framebufferLock = new Object();
    private volatile ScheduledExecutorService heartbeat;
    private volatile Socket socket;
    private volatile long lastPongNanos;
    private volatile long lastPingNanos;
    private volatile boolean handshaken;
    private volatile SharedFramebuffer activeFramebuffer;
    private int sequence;
    private int expectedReceiveSequence = 1;
    private GuestInputBridge inputBridge;

    void setInputBridge(GuestInputBridge inputBridge) { this.inputBridge = inputBridge; }

    void sendCameraState(double posX, double posY, double posZ, float yaw, float pitch, float roll, float fov, long sequenceNumber) {
        if (!isConnected()) return;
        try {
            writeMessage(socket, new Messages.CameraStateMessage(posX, posY, posZ, yaw, pitch, roll, fov, sequenceNumber));
        } catch (IOException e) {
            LOGGER.debug("Could not send camera state", e);
            closeSocket();
        }
    }

    public void start() {
        if (!started.compareAndSet(false, true)) return;
        Thread thread = new Thread(this::connectionLoop, "MinecraftBridge-TCP");
        thread.setDaemon(true);
        thread.start();
    }

    private void connectionLoop() {
        long reconnectDelay = INITIAL_RECONNECT_DELAY_MS;
        while (!stopping.get()) {
            try (Socket connected = new Socket()) {
                socket = connected;
                synchronized (outputLock) { sequence = 0; }
                expectedReceiveSequence = 1;
                connected.connect(new InetSocketAddress(HOST, PORT), CONNECT_TIMEOUT_MS);
                connected.setTcpNoDelay(true);
                sendHello(connected);
                DataInputStream input = new DataInputStream(connected.getInputStream());
                MessageHeader ackHeader = readHeader(input);
                if (ackHeader.getType() != MessageType.HELLO_ACK)
                    throw new ProtocolException.ValidationException("Expected HELLO_ACK, got " + ackHeader.getType());
                requireReceiveSequence(ackHeader);
                PacketReader ackReader = new PacketReader(readPayload(input, ackHeader.getPayloadLength()));
                Messages.HelloAckMessage ack = Messages.HelloAckMessage.deserialize(ackReader);
                if (ackReader.getRemaining() != 0)
                    throw new ProtocolException.ValidationException("Unexpected HELLO_ACK trailing bytes");
                if (ack.getStatus() != 0 || ack.getAcceptedVersion() != ProtocolConstants.CURRENT_VERSION)
                    throw new ProtocolException.ValidationException("Host rejected handshake: " + ack.getErrorMessage());

                handshaken = true;
                lastPongNanos = System.nanoTime();
                LOGGER.info("Connected to bridge at {}:{} (session {})", HOST, PORT, ack.getSessionId());
                ScheduledExecutorService sessionHeartbeat = Executors.newSingleThreadScheduledExecutor(
                        r -> daemon(r, "MinecraftBridge-Heartbeat"));
                heartbeat = sessionHeartbeat;
                sessionHeartbeat.scheduleAtFixedRate(this::heartbeatTick, HEARTBEAT_INTERVAL_MS,
                        HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

                while (!stopping.get()) {
                    MessageHeader header = readHeader(input);
                    requireReceiveSequence(header);
                    byte[] payload = readPayload(input, header.getPayloadLength());
                    if (header.getType() == MessageType.INPUT_EVENT) {
                        PacketReader reader = new PacketReader(payload);
                        Messages.InputEventMessage event = Messages.InputEventMessage.deserialize(reader);
                        if (reader.getRemaining() != 0) throw new ProtocolException.ValidationException("Unexpected INPUT_EVENT trailing bytes");
                        GuestInputBridge bridge = inputBridge;
                        if (bridge != null) bridge.enqueueInput(event.getEventType(), event.getKeyCode(), event.getMouseDx(), event.getMouseDy(), event.getWheelDelta());
                    } else if (header.getType() == MessageType.INPUT_FOCUS) {
                        PacketReader reader = new PacketReader(payload);
                        Messages.InputFocusMessage focus = Messages.InputFocusMessage.deserialize(reader);
                        if (reader.getRemaining() != 0) throw new ProtocolException.ValidationException("Unexpected INPUT_FOCUS trailing bytes");
                        GuestInputBridge bridge = inputBridge;
                        if (bridge != null) bridge.enqueueFocus(focus.isHasFocus(), focus.isReleaseHeldKeys());
                    } else if (header.getType() == MessageType.START_STREAM) {
                        PacketReader reader = new PacketReader(payload);
                        Messages.StartStreamMessage start = Messages.StartStreamMessage.deserialize(reader);
                        if (reader.getRemaining() != 0)
                            throw new ProtocolException.ValidationException("Unexpected START_STREAM trailing bytes");
                        if (start.getSessionId() != ack.getSessionId())
                            throw new ProtocolException.ValidationException("START_STREAM session does not match HELLO_ACK");
                        SharedFramebuffer replacement = SharedFramebuffer.open(Path.of(start.getMappingPath()),
                                start.getSessionId(), start.getGenerationHi(), start.getGenerationLo());
                        SharedFramebuffer previous;
                        synchronized (framebufferLock) {
                            previous = activeFramebuffer;
                            activeFramebuffer = replacement;
                        }
                        if (previous != null) previous.close();
                        LOGGER.info("Shared framebuffer identity validated (session {}, path {})",
                                start.getSessionId(), start.getMappingPath());
                        reconnectDelay = INITIAL_RECONNECT_DELAY_MS;
                    } else if (header.getType() == MessageType.PONG) {
                        PacketReader reader = new PacketReader(payload);
                        Messages.PongMessage pong = Messages.PongMessage.deserialize(reader);
                        if (reader.getRemaining() != 0)
                            throw new ProtocolException.ValidationException("Unexpected PONG trailing bytes");
                        if (pong.getTimestampNs() == lastPingNanos) lastPongNanos = System.nanoTime();
                    } else if (header.getType() == MessageType.SHUTDOWN) {
                        LOGGER.info("Bridge requested guest shutdown");
                        stopping.set(true);
                        break;
                    } else {
                        LOGGER.debug("Ignoring bridge message {}", header.getType());
                    }
                }
            } catch (SocketException e) {
                if (!stopping.get()) LOGGER.info("Bridge connection ended: {}", e.getMessage());
            } catch (IOException | ProtocolException | RuntimeException e) {
                if (!stopping.get()) LOGGER.info("Bridge unavailable or protocol failed: {}", e.getMessage());
            } finally {
                handshaken = false;
                ScheduledExecutorService sessionHeartbeat = heartbeat;
                heartbeat = null;
                if (sessionHeartbeat != null) sessionHeartbeat.shutdownNow();
                clearActiveFramebuffer();
                socket = null;
            }

            if (!stopping.get()) {
                LOGGER.info("Retrying bridge connection in {} ms", reconnectDelay);
                try {
                    Thread.sleep(reconnectDelay);
                } catch (InterruptedException interrupted) {
                    if (stopping.get()) break;
                    Thread.currentThread().interrupt();
                    break;
                }
                reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY_MS);
            }
        }
    }

    private void clearActiveFramebuffer() {
        SharedFramebuffer previous;
        synchronized (framebufferLock) {
            previous = activeFramebuffer;
            activeFramebuffer = null;
        }
        if (previous != null) {
            try { previous.close(); }
            catch (IOException error) { LOGGER.warn("Could not close shared framebuffer mapping", error); }
        }
    }

    private void sendHello(Socket connected) throws IOException {
        Messages.HelloMessage hello = new Messages.HelloMessage(ProtocolConstants.CURRENT_VERSION,
                "MinecraftFabricMod", "1.21.1", 0);
        writeMessage(connected, hello);
    }

    private void heartbeatTick() {
        if (stopping.get() || !handshaken) return;
        long now = System.nanoTime();
        if (now - lastPongNanos > TimeUnit.MILLISECONDS.toNanos(HEARTBEAT_TIMEOUT_MS)) {
            LOGGER.warn("Bridge heartbeat timed out");
            closeSocket();
            return;
        }
        try {
            lastPingNanos = now;
            writeMessage(socket, new Messages.PingMessage(lastPingNanos));
        } catch (IOException e) {
            LOGGER.debug("Could not send bridge heartbeat", e);
            closeSocket();
        }
    }

    private void requireReceiveSequence(MessageHeader header) throws ProtocolException {
        if (header.getSequenceId() != expectedReceiveSequence)
            throw new ProtocolException.ValidationException("Expected host sequence " + expectedReceiveSequence +
                    ", received " + header.getSequenceId());
        if (expectedReceiveSequence == -1) throw new ProtocolException.ValidationException("Host sequence exhausted");
        expectedReceiveSequence++;
    }

    private void writeMessage(Socket target, Messages.Message message) throws IOException {
        if (target == null || target.isClosed()) throw new SocketException("Bridge socket is closed");
        synchronized (outputLock) {
            if (sequence == -1) throw new SocketException("Guest sequence exhausted");
            PacketWriter writer = new PacketWriter();
            message.serialize(writer);
            byte[] packet = writer.buildPacket(message.getMessageType(), ++sequence);
            target.getOutputStream().write(packet);
            target.getOutputStream().flush();
        }
    }

    private static MessageHeader readHeader(DataInputStream input) throws IOException, ProtocolException {
        byte[] bytes = new byte[ProtocolConstants.HEADER_SIZE];
        input.readFully(bytes);
        MessageHeader header = MessageHeader.parse(bytes, 0, true);
        if (header.getPayloadLength() < 0 || header.getPayloadLength() > ProtocolConstants.MAX_PAYLOAD_LENGTH)
            throw new ProtocolException.ValidationException("Invalid payload length " + header.getPayloadLength());
        return header;
    }

    private static byte[] readPayload(DataInputStream input, int length) throws IOException {
        if (length < 0 || length > ProtocolConstants.MAX_PAYLOAD_LENGTH)
            throw new IOException("Invalid packet payload length: " + length);
        byte[] payload = new byte[length];
        input.readFully(payload);
        return payload;
    }

    private void closeSocket() {
        Socket current = socket;
        if (current != null) try { current.close(); } catch (IOException ignored) { }
    }

    @Override public void close() {
        if (!stopping.compareAndSet(false, true)) return;
        ScheduledExecutorService currentHeartbeat = heartbeat;
        if (currentHeartbeat != null) currentHeartbeat.shutdownNow();
        if (handshaken) {
            try {
                LOGGER.info("Sending graceful bridge SHUTDOWN");
                writeMessage(socket, new Messages.ShutdownMessage(0, "Minecraft client stopping"));
            } catch (IOException e) {
                LOGGER.debug("Could not send graceful disconnect", e);
            }
        }
        closeSocket();
    }

    public boolean isConnected() { return handshaken && socket != null && !socket.isClosed(); }
    public boolean hasValidatedFramebuffer() { return activeFramebuffer != null; }
    long activeFramebufferSessionId() {
        SharedFramebuffer current = activeFramebuffer;
        return current == null ? -1L : current.sessionId();
    }

    CaptureReservation reserveCaptureSlot() {
        SharedFramebuffer current = activeFramebuffer;
        if (current == null) return null;
        int slot = current.tryAcquireFreeSlot();
        if (slot < 0) return null;
        if (activeFramebuffer != current) {
            current.cancelWriting(slot);
            return null;
        }
        return new CaptureReservation(current, slot);
    }

    boolean publishCapturedFrame(CaptureReservation reservation, int width, int height, int stride,
                                 ByteBuffer pixels) {
        if (activeFramebuffer != reservation.framebuffer) {
            reservation.framebuffer.cancelWriting(reservation.slot);
            return false;
        }
        reservation.framebuffer.publish(reservation.slot, width, height, stride, pixels);
        return true;
    }

    void cancelCapture(CaptureReservation reservation) {
        if (activeFramebuffer == reservation.framebuffer) reservation.framebuffer.cancelWriting(reservation.slot);
    }

    static final class CaptureReservation {
        final SharedFramebuffer framebuffer;
        final int slot;
        CaptureReservation(SharedFramebuffer framebuffer, int slot) { this.framebuffer = framebuffer; this.slot = slot; }
    }

    /** Called only from the Minecraft render thread with a completed BGRA8 frame. */
    public boolean publishFrame(int width, int height, int stride, ByteBuffer pixels) {
        CaptureReservation reservation = reserveCaptureSlot();
        if (reservation == null) return false;
        try {
            return publishCapturedFrame(reservation, width, height, stride, pixels);
        } catch (RuntimeException failure) {
            cancelCapture(reservation);
            throw failure;
        }
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }
}
