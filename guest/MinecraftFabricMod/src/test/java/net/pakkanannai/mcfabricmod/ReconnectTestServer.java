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

/** Drops one established session, then accepts a fresh session/mapping to verify guest recovery. */
final class ReconnectTestServer implements AutoCloseable {
    private final ServerSocket server;
    private final Thread thread;
    private final BlockingQueue<Throwable> failures = new LinkedBlockingQueue<>();
    private volatile int connectionCount;
    private volatile boolean secondStreamSent;
    private volatile boolean sawPing;
    private volatile boolean sawShutdown;

    ReconnectTestServer() throws IOException {
        server = new ServerSocket(0);
        thread = new Thread(this::serve, "Guest-ReconnectTestServer");
        thread.setDaemon(true);
        thread.start();
    }

    private void serve() {
        try {
            serveFirstConnection();
            serveSecondConnection();
        } catch (Throwable failure) {
            if (!server.isClosed()) failures.offer(failure);
        }
    }

    private void serveFirstConnection() throws Exception {
        try (Socket socket = server.accept()) {
            connectionCount = 1;
            DataInputStream input = new DataInputStream(socket.getInputStream());
            requireHello(readPacket(input));
            sendAck(socket, 1001, 1);
            try (Mapping mapping = createMapping(1001)) {
                sendStart(socket, mapping, 1001, 2);
                Thread.sleep(250);
            }
        }
    }

    private void serveSecondConnection() throws Exception {
        try (Socket socket = server.accept()) {
            connectionCount = 2;
            DataInputStream input = new DataInputStream(socket.getInputStream());
            requireHello(readPacket(input));
            sendAck(socket, 1002, 1);
            try (Mapping mapping = createMapping(1002)) {
                sendStart(socket, mapping, 1002, 2);
                secondStreamSent = true;
                while (!socket.isClosed()) {
                    Packet packet = readPacket(input);
                    if (packet.header.getType() == MessageType.PING) {
                        sawPing = true;
                        Messages.PingMessage ping = Messages.PingMessage.deserialize(new PacketReader(packet.payload));
                        PacketWriter pong = new PacketWriter();
                        new Messages.PongMessage(ping.getTimestampNs()).serialize(pong);
                        socket.getOutputStream().write(pong.buildPacket(MessageType.PONG, 3));
                    } else if (packet.header.getType() == MessageType.SHUTDOWN) {
                        sawShutdown = true;
                        return;
                    }
                }
            }
        }
    }

    private static Mapping createMapping(long session) throws IOException {
        Path path = Path.of(System.getProperty("java.io.tmpdir"), "mcb-reconnect-" + session + "-" + System.nanoTime() + ".shm");
        return new Mapping(path, SharedFramebuffer.create(path, (int) session));
    }

    private static void sendAck(Socket socket, long session, int sequence) throws IOException {
        PacketWriter writer = new PacketWriter();
        new Messages.HelloAckMessage(0, ProtocolConstants.CURRENT_VERSION, session, "").serialize(writer);
        socket.getOutputStream().write(writer.buildPacket(MessageType.HELLO_ACK, sequence));
    }

    private static void sendStart(Socket socket, Mapping mapping, long session, int sequence) throws IOException {
        PacketWriter writer = new PacketWriter();
        new Messages.StartStreamMessage(session, mapping.path.toString(), mapping.framebuffer.generationHi(),
                mapping.framebuffer.generationLo()).serialize(writer);
        socket.getOutputStream().write(writer.buildPacket(MessageType.START_STREAM, sequence));
    }

    private static void requireHello(Packet packet) throws IOException, ProtocolException {
        if (packet.header.getType() != MessageType.HELLO) throw new IOException("Expected HELLO");
        Messages.HelloMessage hello = Messages.HelloMessage.deserialize(new PacketReader(packet.payload));
        if (!"MinecraftFabricMod".equals(hello.getClientName())) throw new IOException("Unexpected guest HELLO");
    }

    private static Packet readPacket(DataInputStream input) throws IOException, ProtocolException {
        byte[] headerBytes = new byte[ProtocolConstants.HEADER_SIZE];
        input.readFully(headerBytes);
        MessageHeader header = MessageHeader.parse(headerBytes, 0, true);
        if (header.getPayloadLength() < 0 || header.getPayloadLength() > ProtocolConstants.MAX_PAYLOAD_LENGTH)
            throw new IOException("Invalid payload length");
        byte[] payload = new byte[header.getPayloadLength()];
        input.readFully(payload);
        return new Packet(header, payload);
    }

    int connectionCount() { return connectionCount; }
    boolean secondStreamSent() { return secondStreamSent; }
    int port() { return server.getLocalPort(); }
    boolean sawPing() { return sawPing; }
    boolean sawShutdown() { return sawShutdown; }
    Throwable failure() { return failures.peek(); }

    @Override public void close() throws Exception {
        server.close();
        thread.join(TimeUnit.SECONDS.toMillis(3));
    }

    private record Packet(MessageHeader header, byte[] payload) {}

    private static final class Mapping implements AutoCloseable {
        final Path path;
        final SharedFramebuffer framebuffer;
        Mapping(Path path, SharedFramebuffer framebuffer) { this.path = path; this.framebuffer = framebuffer; }
        @Override public void close() throws Exception {
            framebuffer.close();
            Files.deleteIfExists(path);
        }
    }
}
