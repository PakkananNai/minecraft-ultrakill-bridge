package net.pakkanannai.mcfabricmod;

import static org.junit.jupiter.api.Assertions.*;
import com.bridge.minecraft.protocol.MessageType;
import com.bridge.minecraft.protocol.Messages;
import com.bridge.minecraft.protocol.PacketReader;
import com.bridge.minecraft.protocol.PacketWriter;
import org.junit.jupiter.api.Test;

class StartStreamMessageTest {
    @Test void startStreamRoundTripsCanonicalIdentity() throws Exception {
        Messages.StartStreamMessage original = new Messages.StartStreamMessage(
                0xdeadbeefL, "/tmp/minecraft-bridge-session.shm", 0x1122334455667788L,
                0x7766554433221100L);
        PacketWriter writer = new PacketWriter();
        original.serialize(writer);
        byte[] payload = writer.toPayloadArray();
        assertEquals(MessageType.START_STREAM, original.getMessageType());
        assertEquals(4 + 2 + "/tmp/minecraft-bridge-session.shm".getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 16, payload.length);
        PacketReader reader = new PacketReader(payload);
        Messages.StartStreamMessage decoded = Messages.StartStreamMessage.deserialize(reader);
        assertEquals(0, reader.getRemaining());
        assertEquals(original.getSessionId(), decoded.getSessionId());
        assertEquals(original.getMappingPath(), decoded.getMappingPath());
        assertEquals(original.getGenerationHi(), decoded.getGenerationHi());
        assertEquals(original.getGenerationLo(), decoded.getGenerationLo());
    }

    @Test void startStreamRejectsNonAbsoluteOrTraversalPaths() {
        assertThrows(IllegalArgumentException.class, () -> new Messages.StartStreamMessage(1, "tmp/file.shm", 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Messages.StartStreamMessage(1, "/tmp/../file.shm", 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Messages.StartStreamMessage(1, "/tmp//file.shm", 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Messages.StartStreamMessage(1, "/tmp/", 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Messages.StartStreamMessage(1, "/tmp\\\\file.shm", 1, 0));
    }

    @Test void startStreamRejectsZeroIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new Messages.StartStreamMessage(0, "/tmp/file.shm", 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Messages.StartStreamMessage(1, "/tmp/file.shm", 0, 0));
    }
}
