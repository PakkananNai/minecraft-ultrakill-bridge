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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns the guest's background TCP control connection. */
public final class GuestControlClient implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftBridge");
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 47653;
    private static final int CONNECT_TIMEOUT_MS = 1500;
    private static final long HEARTBEAT_INTERVAL_MS = 2000;
    private static final long HEARTBEAT_TIMEOUT_MS = 6000;

    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final Object outputLock = new Object();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "MinecraftBridge-Heartbeat"));
    private volatile Socket socket;
    private volatile long lastPongNanos;
    private volatile long lastPingNanos;
    private volatile boolean handshaken;
    private int sequence;

    public void start() {
        if (!started.compareAndSet(false, true)) return;
        Thread thread = new Thread(this::connectionLoop, "MinecraftBridge-TCP");
        thread.setDaemon(true);
        thread.start();
    }

    private void connectionLoop() {
        try (Socket connected = new Socket()) {
            socket = connected;
            connected.connect(new InetSocketAddress(HOST, PORT), CONNECT_TIMEOUT_MS);
            connected.setTcpNoDelay(true);
            sendHello(connected);
            DataInputStream input = new DataInputStream(connected.getInputStream());
            MessageHeader ackHeader = readHeader(input);
            if (ackHeader.getType() != MessageType.HELLO_ACK) throw new ProtocolException.ValidationException("Expected HELLO_ACK, got " + ackHeader.getType());
            Messages.HelloAckMessage ack = Messages.HelloAckMessage.deserialize(new PacketReader(readPayload(input, ackHeader.getPayloadLength())));
            if (ack.getStatus() != 0 || ack.getAcceptedVersion() != ProtocolConstants.CURRENT_VERSION) {
                throw new ProtocolException.ValidationException("Host rejected handshake: " + ack.getErrorMessage());
            }
            handshaken = true;
            lastPongNanos = System.nanoTime();
            LOGGER.info("Connected to bridge at {}:{} (session {})", HOST, PORT, ack.getSessionId());
            heartbeat.scheduleAtFixedRate(this::heartbeatTick, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
            while (!stopping.get()) {
                MessageHeader header = readHeader(input);
                byte[] payload = readPayload(input, header.getPayloadLength());
                if (header.getType() == MessageType.PONG) {
                    Messages.PongMessage pong = Messages.PongMessage.deserialize(new PacketReader(payload));
                    if (pong.getTimestampNs() == lastPingNanos) lastPongNanos = System.nanoTime();
                } else if (header.getType() == MessageType.SHUTDOWN) {
                    LOGGER.info("Bridge requested guest shutdown");
                    break;
                } else {
                    LOGGER.debug("Ignoring bridge message {}", header.getType());
                }
            }
        } catch (SocketException e) {
            if (!stopping.get()) LOGGER.info("Bridge connection ended: {}", e.getMessage());
        } catch (IOException | ProtocolException e) {
            if (!stopping.get()) LOGGER.info("Bridge unavailable or protocol failed: {}", e.getMessage());
        } finally {
            handshaken = false;
            socket = null;
            heartbeat.shutdownNow();
            if (!stopping.get()) LOGGER.info("Bridge connection closed");
        }
    }

    private void sendHello(Socket connected) throws IOException {
        Messages.HelloMessage hello = new Messages.HelloMessage(ProtocolConstants.CURRENT_VERSION,
                "MinecraftFabricMod", "1.21.1", 0);
        PacketWriter writer = new PacketWriter();
        hello.serialize(writer);
        writePacket(connected, writer.buildPacket(MessageType.HELLO, nextSequence()));
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
            PacketWriter writer = new PacketWriter();
            new Messages.PingMessage(lastPingNanos).serialize(writer);
            writePacket(socket, writer.buildPacket(MessageType.PING, nextSequence()));
        } catch (IOException e) {
            LOGGER.debug("Could not send bridge heartbeat", e);
            closeSocket();
        }
    }

    private int nextSequence() { synchronized (outputLock) { return ++sequence; } }

    private void writePacket(Socket target, byte[] packet) throws IOException {
        if (target == null || target.isClosed()) throw new SocketException("Bridge socket is closed");
        synchronized (outputLock) {
            target.getOutputStream().write(packet);
            target.getOutputStream().flush();
        }
    }

    private static MessageHeader readHeader(DataInputStream input) throws IOException, ProtocolException {
        byte[] bytes = new byte[ProtocolConstants.HEADER_SIZE];
        input.readFully(bytes);
        MessageHeader header = MessageHeader.parse(bytes, 0, true);
        if (header.getPayloadLength() < 0 || header.getPayloadLength() > ProtocolConstants.MAX_PAYLOAD_LENGTH) {
            throw new ProtocolException.ValidationException("Invalid payload length " + header.getPayloadLength());
        }
        return header;
    }

    private static byte[] readPayload(DataInputStream input, int length) throws IOException {
        if (length < 0 || length > ProtocolConstants.MAX_PAYLOAD_LENGTH) throw new IOException("Invalid packet payload length: " + length);
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
        heartbeat.shutdownNow();
        if (handshaken) {
            try {
                PacketWriter writer = new PacketWriter();
                new Messages.ShutdownMessage(0, "Minecraft client stopping").serialize(writer);
                writePacket(socket, writer.buildPacket(MessageType.SHUTDOWN, nextSequence()));
            } catch (IOException e) {
                LOGGER.debug("Could not send graceful disconnect", e);
            }
        }
        closeSocket();
    }

    public boolean isConnected() { return handshaken && socket != null && !socket.isClosed(); }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }
}
