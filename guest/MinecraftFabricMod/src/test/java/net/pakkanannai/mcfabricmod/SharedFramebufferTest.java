package net.pakkanannai.mcfabricmod;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SharedFramebufferTest {
    @Test
    void publishesAndAcquiresLatestReadyFrame() throws Exception {
        Path path = Files.createTempDirectory("mcub-frame-").resolve("frame.shm");
        try {
            try (SharedFramebuffer frame = SharedFramebuffer.create(path, 7)) {
                assertEquals(SharedFramebuffer.MAPPING_SIZE, Files.size(path));
                int slot = frame.tryAcquireFreeSlot();
                assertEquals(0, slot);
                byte[] payload = { 1, 2, 3, 4 };
                assertEquals(1, frame.publish(slot, 1, 1, 4, payload));
                int acquired = frame.tryAcquireLatestReady();
                assertEquals(slot, acquired);
                SharedFramebuffer.FrameMetadata metadata = frame.metadata(acquired);
                assertEquals(1, metadata.sequence());
                assertEquals(1, metadata.width());
                assertEquals(1, metadata.height());
                assertEquals(4, metadata.stride());
                assertEquals(SharedFramebuffer.BGRA8, metadata.pixelFormat());
                assertEquals(4, metadata.payloadLength());
                assertNotEquals(0L, metadata.timestampNs());
                assertArrayEquals(payload, frame.readPayload(acquired));
                frame.releaseReading(acquired);
                assertEquals(-1, frame.tryAcquireLatestReady());

                int first = frame.tryAcquireFreeSlot();
                int second = frame.tryAcquireFreeSlot();
                int third = frame.tryAcquireFreeSlot();
                assertEquals(0, first);
                assertEquals(1, second);
                assertEquals(2, third);
                assertEquals(2, frame.publish(first, 1, 1, 4, new byte[] { 1, 1, 1, 1 }));
                assertEquals(3, frame.publish(second, 1, 1, 4, new byte[] { 2, 2, 2, 2 }));
                assertEquals(4, frame.publish(third, 1, 1, 4, new byte[] { 3, 3, 3, 3 }));
                int newest = frame.tryAcquireLatestReady();
                assertEquals(third, newest);
                assertArrayEquals(new byte[] { 3, 3, 3, 3 }, frame.readPayload(newest));
                frame.releaseReading(newest);
                frame.drainStaleReady(4);
                assertEquals(-1, frame.tryAcquireLatestReady());
            }
            try (SharedFramebuffer reopened = SharedFramebuffer.open(path)) {
                assertEquals(-1, reopened.tryAcquireLatestReady());
                assertEquals(0, reopened.tryAcquireFreeSlot());
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void publishesDirectByteBufferWithoutChangingItsPosition() throws Exception {
        Path path = Files.createTempDirectory("mcub-direct-").resolve("frame.shm");
        try (SharedFramebuffer frame = SharedFramebuffer.create(path, 9)) {
            int slot = frame.tryAcquireFreeSlot();
            ByteBuffer pixels = ByteBuffer.allocateDirect(8);
            pixels.put(new byte[] { 99, 99, 10, 20, 30, 40, 99, 99 });
            pixels.position(2).limit(6);
            assertEquals(1, frame.publish(slot, 1, 1, 4, pixels));
            assertEquals(2, pixels.position());
            int acquired = frame.tryAcquireLatestReady();
            assertArrayEquals(new byte[] { 10, 20, 30, 40 }, frame.readPayload(acquired));
            frame.releaseReading(acquired);

            int reserved = frame.tryAcquireFreeSlot();
            frame.cancelWriting(reserved);
            assertEquals(reserved, frame.tryAcquireFreeSlot());
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void openRequiresAdvertisedSessionAndGeneration() throws Exception {
        Path path = Files.createTempDirectory("mcub-identity-").resolve("frame.shm");
        long session;
        long generationHi;
        long generationLo;
        try (SharedFramebuffer created = SharedFramebuffer.create(path, 0xdeadbeef)) {
            session = created.sessionId();
            generationHi = created.generationHi();
            generationLo = created.generationLo();
        }
        try {
            assertEquals(0xdeadbeefL, session);
            try (SharedFramebuffer opened = SharedFramebuffer.open(path, session, generationHi, generationLo)) {
                assertEquals(session, opened.sessionId());
            }
            assertThrows(java.io.IOException.class,
                    () -> SharedFramebuffer.open(path, session + 1, generationHi, generationLo));
            assertThrows(java.io.IOException.class,
                    () -> SharedFramebuffer.open(path, session, generationHi, generationLo ^ 1L));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void closedMappingRejectsNewPublicationAndReservations() throws Exception {
        Path path = Files.createTempDirectory("mcub-closed-").resolve("frame.shm");
        SharedFramebuffer frame = SharedFramebuffer.create(path, 12);
        int reserved = frame.tryAcquireFreeSlot();
        assertEquals(0, reserved);
        frame.close();
        assertEquals(-1, frame.tryAcquireFreeSlot());
        assertThrows(IllegalStateException.class,
                () -> frame.publish(reserved, 1, 1, 4, new byte[] { 1, 2, 3, 4 }));
        frame.cancelWriting(reserved); // Closed mappings are abandoned, not recycled.
        Files.deleteIfExists(path);
    }

    @Test
    void rejectsNonZeroReservedMappingHeaderBytes() throws Exception {
        Path path = Files.createTempDirectory("mcub-reserved-header-").resolve("frame.shm");
        try (SharedFramebuffer created = SharedFramebuffer.create(path, 13)) { }
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(path, java.nio.file.StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[] { 1 }), 64);
        }
        try {
            assertThrows(java.io.IOException.class, () -> SharedFramebuffer.open(path));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void rejectsNonZeroReservedSlotBytesWithoutReadingPayload() throws Exception {
        Path path = Files.createTempDirectory("mcub-reserved-slot-").resolve("frame.shm");
        try (SharedFramebuffer created = SharedFramebuffer.create(path, 14)) {
            int slot = created.tryAcquireFreeSlot();
            created.publish(slot, 1, 1, 4, new byte[] { 1, 2, 3, 4 });
        }
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(path, java.nio.file.StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[] { 1 }), SharedFramebuffer.HEADER_SIZE + 72L);
        }
        try (SharedFramebuffer opened = SharedFramebuffer.open(path)) {
            assertEquals(-1, opened.tryAcquireLatestReady());
            assertEquals(0, opened.tryAcquireFreeSlot());
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void rejectsZeroSessionIdAndMetadataAccessWithoutOwnership() throws Exception {
        Path path = Files.createTempDirectory("mcub-session-zero-").resolve("frame.shm");
        assertThrows(IllegalArgumentException.class, () -> SharedFramebuffer.create(path, 0));
        try (SharedFramebuffer frame = SharedFramebuffer.create(path, 15)) {
            int slot = frame.tryAcquireFreeSlot();
            frame.publish(slot, 1, 1, 4, new byte[] { 1, 2, 3, 4 });
            assertThrows(IllegalStateException.class, () -> frame.metadata(slot));
            assertThrows(IllegalStateException.class, () -> frame.readPayload(slot));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void neverUsesInvalidPixelFormatOrFootprint() throws Exception {
        Path path = Files.createTempDirectory("mcub-frame-").resolve("frame.shm");
        try (SharedFramebuffer frame = SharedFramebuffer.create(path, 1)) {
            int slot = frame.tryAcquireFreeSlot();
            assertThrows(IllegalArgumentException.class, () -> frame.publish(slot, 1, 2, 8, new byte[4]));
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
