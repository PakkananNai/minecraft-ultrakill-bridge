package net.pakkanannai.mcfabricmod;

import static org.junit.jupiter.api.Assertions.*;
import com.bridge.minecraft.protocol.MessageHeader;
import com.bridge.minecraft.protocol.MessageType;
import com.bridge.minecraft.protocol.Messages;
import com.bridge.minecraft.protocol.PacketReader;
import com.bridge.minecraft.protocol.PacketWriter;
import com.bridge.minecraft.protocol.ProtocolConstants;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class GuestControlClientTest {
    @Test void helloPacketHasCanonicalBytesAndFields() throws Exception {
        PacketWriter writer = new PacketWriter();
        new Messages.HelloMessage(ProtocolConstants.CURRENT_VERSION, "MinecraftFabricMod", "1.21.1", 0)
                .serialize(writer);
        byte[] packet = writer.buildPacket(MessageType.HELLO, 7);
        assertArrayEquals(new byte[] {
                0x4d, 0x43, 0x55, 0x42, 1, 0, 1, 0, 7, 0, 0, 0, 34, 0, 0, 0,
                1, 0, 18, 0, 'M','i','n','e','c','r','a','f','t','F','a','b','r','i','c','M','o','d',
                6, 0, '1','.','2','1','.','1', 0, 0, 0, 0
        }, packet);
        MessageHeader header = MessageHeader.parse(Arrays.copyOf(packet, ProtocolConstants.HEADER_SIZE), 0, true);
        assertEquals(MessageType.HELLO, header.getType());
        assertEquals(34, header.getPayloadLength());
        Messages.HelloMessage decoded = Messages.HelloMessage.deserialize(new PacketReader(packet, 16, 34));
        assertEquals(1, decoded.getProtocolVersion());
        assertEquals("MinecraftFabricMod", decoded.getClientName());
        assertEquals("1.21.1", decoded.getClientVersion());
        assertEquals(0, decoded.getCapabilities());
    }

    @Test void clientCompletesHandshakeHeartbeatAndGracefulDisconnect() throws Exception {
        try (TestServer server = new TestServer()) {
            GuestControlClient client = new GuestControlClient();
            client.start();
            Messages.HelloMessage hello = server.hello();
            assertNotNull(hello, "server received HELLO");
            assertEquals("MinecraftFabricMod", hello.getClientName());
            assertTrue(await(client::isConnected, 3, TimeUnit.SECONDS), "HELLO_ACK completes connection");
            assertTrue(await(client::hasValidatedFramebuffer, 4, TimeUnit.SECONDS), "START_STREAM mapping identity is opened and validated");
            assertTrue(await(server::sawPing, 4, TimeUnit.SECONDS), "client sends PING");
            assertNull(server.failure());
            client.close();
            assertTrue(await(server::sawShutdown, 4, TimeUnit.SECONDS), "client sends graceful SHUTDOWN");
            assertNull(server.failure());
        }
    }

    @Test void reconnectsWithFreshSessionAndMappingAfterHostDisconnect() throws Exception {
        try (ReconnectTestServer server = new ReconnectTestServer()) {
            GuestControlClient client = new GuestControlClient();
            client.start();
            assertTrue(await(() -> server.connectionCount() == 2 && server.secondStreamSent()
                    && client.isConnected() && client.activeFramebufferSessionId() == 1002L, 8, TimeUnit.SECONDS),
                    "guest reconnects and validates the replacement session mapping");
            assertTrue(await(server::sawPing, 4, TimeUnit.SECONDS), "reconnected guest resumes heartbeat");
            assertNull(server.failure());
            client.close();
            assertTrue(await(server::sawShutdown, 4, TimeUnit.SECONDS), "reconnected guest sends graceful SHUTDOWN");
            assertNull(server.failure());
        }
    }

    private static boolean await(java.util.function.BooleanSupplier condition, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return true;
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }
}
