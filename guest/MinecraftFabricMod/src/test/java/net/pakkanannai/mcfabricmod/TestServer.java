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
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Deterministic loopback peer used by the guest protocol tests. */
final class TestServer implements AutoCloseable {
    private final ServerSocket server;
    private final Thread thread;
    private final BlockingQueue<Throwable> failures = new LinkedBlockingQueue<>();
    private volatile Messages.HelloMessage hello;
    private volatile boolean sawPing;
    private volatile boolean sawShutdown;
    private volatile Path mappingPath;
    private volatile SharedFramebuffer mapping;
    private int hostSequence = 1;

    TestServer() throws IOException {
        server = new ServerSocket(47653);
        thread = new Thread(this::serve, "Guest-TestServer");
        thread.setDaemon(true);
        thread.start();
    }

    private void serve() {
        try (Socket socket = server.accept()) {
            DataInputStream input = new DataInputStream(socket.getInputStream());
            Packet packet = readPacket(input);
            if (packet.header.getType() != MessageType.HELLO) throw new IOException("Expected HELLO");
            hello = Messages.HelloMessage.deserialize(new PacketReader(packet.payload));
            PacketWriter ack = new PacketWriter();
            new Messages.HelloAckMessage(0, ProtocolConstants.CURRENT_VERSION, 12345, "").serialize(ack);
            socket.getOutputStream().write(ack.buildPacket(MessageType.HELLO_ACK, hostSequence++));
            mappingPath = Path.of(System.getProperty("java.io.tmpdir"), "mcb-test-" + System.nanoTime() + ".shm");
            mapping = SharedFramebuffer.create(mappingPath, 12345);
            PacketWriter start = new PacketWriter();
            new Messages.StartStreamMessage(12345, mappingPath.toString(), mapping.generationHi(), mapping.generationLo()).serialize(start);
            socket.getOutputStream().write(start.buildPacket(MessageType.START_STREAM, hostSequence++));
            while (!socket.isClosed()) {
                packet = readPacket(input);
                if (packet.header.getType() == MessageType.PING) {
                    sawPing = true;
                    Messages.PingMessage ping = Messages.PingMessage.deserialize(new PacketReader(packet.payload));
                    PacketWriter pong = new PacketWriter();
                    new Messages.PongMessage(ping.getTimestampNs()).serialize(pong);
                    socket.getOutputStream().write(pong.buildPacket(MessageType.PONG, hostSequence++));
                } else if (packet.header.getType() == MessageType.SHUTDOWN) {
                    Messages.ShutdownMessage shutdown = Messages.ShutdownMessage.deserialize(new PacketReader(packet.payload));
                    if (shutdown.getReasonCode() != 0 || !"Minecraft client stopping".equals(shutdown.getReasonText())) {
                        throw new IOException("Unexpected SHUTDOWN payload");
                    }
                    sawShutdown = true;
                    return;
                }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            if (!server.isClosed()) failures.offer(failure);
        }
    }

    private static Packet readPacket(DataInputStream input) throws IOException, ProtocolException {
        byte[] headerBytes = new byte[ProtocolConstants.HEADER_SIZE];
        input.readFully(headerBytes);
        MessageHeader header = MessageHeader.parse(headerBytes, 0, true);
        if (header.getPayloadLength() < 0 || header.getPayloadLength() > ProtocolConstants.MAX_PAYLOAD_LENGTH) throw new IOException("Bad payload length");
        byte[] payload = new byte[header.getPayloadLength()];
        input.readFully(payload);
        return new Packet(header, payload);
    }

    Messages.HelloMessage hello() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (hello == null && System.nanoTime() < deadline) Thread.sleep(10);
        return hello;
    }
    boolean sawPing() { return sawPing; }
    boolean sawShutdown() { return sawShutdown; }
    Throwable failure() { return failures.peek(); }

    @Override public void close() throws Exception {
        server.close();
        thread.join(2000);
        if (mapping != null) mapping.close();
        if (mappingPath != null) Files.deleteIfExists(mappingPath);
    }

    private record Packet(MessageHeader header, byte[] payload) {}
}
