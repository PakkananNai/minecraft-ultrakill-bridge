package net.pakkanannai.mcfabricmod;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public final class SharedFramebuffer implements AutoCloseable {
    public static final int HEADER_SIZE = 128, SLOT_HEADER_SIZE = 128, SLOT_COUNT = 3;
    public static final int WIDTH = 3840, HEIGHT = 2160, BYTES_PER_PIXEL = 4;
    public static final int SLOT_CAPACITY = WIDTH * HEIGHT * BYTES_PER_PIXEL;
    public static final long SLOT_STRIDE = SLOT_HEADER_SIZE + (long) SLOT_CAPACITY;
    public static final long MAPPING_SIZE = HEADER_SIZE + SLOT_COUNT * SLOT_STRIDE;
    public static final int MAGIC = 0x5342434d, VERSION = 1, BGRA8 = 2;
    private static final int FREE=0, WRITING=1, READY=2, READING=3;
    private static final VarHandle STATE = MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private final FileChannel channel;
    private final MappedByteBuffer map;
    private long nextSequence;
    private volatile boolean closed;

    private SharedFramebuffer(FileChannel channel, MappedByteBuffer map) { this.channel=channel; this.map=(MappedByteBuffer) map.order(ByteOrder.LITTLE_ENDIAN); }

    public static SharedFramebuffer create(Path path, int sessionId) throws IOException {
        if (sessionId == 0) throw new IllegalArgumentException("sessionId must be non-zero");
        FileChannel c = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE);
        try { c.truncate(MAPPING_SIZE); c.position(MAPPING_SIZE - 1); c.write(ByteBuffer.wrap(new byte[]{0}));
            SharedFramebuffer f = new SharedFramebuffer(c, c.map(FileChannel.MapMode.READ_WRITE,0,MAPPING_SIZE));
            SecureRandom random = new SecureRandom();
            byte[] generation = new byte[16];
            random.nextBytes(generation);
            ByteBuffer generationBuffer = ByteBuffer.wrap(generation).order(ByteOrder.LITTLE_ENDIAN);
            long generationHi = generationBuffer.getLong();
            long generationLo = generationBuffer.getLong();
            if (generationHi == 0 && generationLo == 0) generationLo = 1;
            f.writeInt(0,MAGIC); f.writeShort(4,(short)VERSION); f.writeShort(6,(short)HEADER_SIZE); f.writeInt(8,SLOT_COUNT);
            f.writeInt(12,(int)SLOT_STRIDE); f.writeInt(16,SLOT_CAPACITY); f.writeInt(20,1); f.writeInt(24,sessionId);
            f.writeLong(32,generationHi); f.writeLong(40,generationLo);
            for(int i=0;i<SLOT_COUNT;i++) f.setState(i,FREE);
            f.map.force(); return f;
        } catch(Throwable t) { c.close(); throw t; }
    }

    public static SharedFramebuffer open(Path path) throws IOException {
        FileChannel c = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE);
        try {
            if(c.size()!=MAPPING_SIZE) throw new IOException("Shared framebuffer size mismatch");
            SharedFramebuffer f=new SharedFramebuffer(c,c.map(FileChannel.MapMode.READ_WRITE,0,MAPPING_SIZE));
            if(f.readInt(0)!=MAGIC || Short.toUnsignedInt(f.readShort(4))!=VERSION || Short.toUnsignedInt(f.readShort(6))!=HEADER_SIZE ||
               f.readInt(8)!=SLOT_COUNT || f.readInt(12)!=(int)SLOT_STRIDE || f.readInt(16)!=SLOT_CAPACITY)
                throw new IOException("Invalid shared framebuffer header");
            if (f.readInt(24) == 0) throw new IOException("Missing mapping session ID");
            if(f.readLong(32)==0 && f.readLong(40)==0) throw new IOException("Missing mapping generation");
            if (f.readInt(28) != 0 || f.readLong(56) != 0 || !allZero(f.map, 64, 64))
                throw new IOException("Non-zero reserved mapping header bytes");
            f.initializeNextSequence();
            return f;
        } catch(Throwable t){ c.close(); throw t; }
    }

    /** Opens only the mapping identity advertised by the authenticated control session. */
    public static SharedFramebuffer open(Path path, long expectedSessionId, long expectedGenerationHi,
                                         long expectedGenerationLo) throws IOException {
        if (expectedSessionId <= 0 || expectedSessionId > 0xffffffffL ||
                (expectedGenerationHi == 0 && expectedGenerationLo == 0))
            throw new IllegalArgumentException("Invalid expected mapping identity");
        SharedFramebuffer f = open(path);
        boolean accepted = false;
        try {
            long actualSessionId = Integer.toUnsignedLong(f.readInt(24));
            if (actualSessionId != expectedSessionId || f.readLong(32) != expectedGenerationHi ||
                    f.readLong(40) != expectedGenerationLo)
                throw new IOException("Shared framebuffer identity does not match START_STREAM");
            accepted = true;
            return f;
        } finally {
            if (!accepted) f.close();
        }
    }

    public long sessionId() { return Integer.toUnsignedLong(readInt(24)); }
    public long generationHi() { return readLong(32); }
    public long generationLo() { return readLong(40); }

    public int tryAcquireFreeSlot() {
        if (closed) return -1;
        for(int i=0;i<SLOT_COUNT;i++) if(compareState(i,FREE,WRITING)) return i;
        return -1;
    }

    public long publish(int slot, int width, int height, int stride, byte[] payload) {
        return publish(slot, width, height, stride, ByteBuffer.wrap(payload));
    }

    /** Publishes directly from a caller-owned buffer without allocating an intermediate frame array. */
    public synchronized long publish(int slot, int width, int height, int stride, ByteBuffer payload) {
        if (closed) throw new IllegalStateException("Shared framebuffer is closed");
        validateSlot(slot);
        ByteBuffer source = payload.duplicate();
        int length = source.remaining();
        validate(slot, width, height, stride, length);
        long sequence = nextSequence + 1L;
        if (sequence == 0L) throw new IllegalStateException("Framebuffer sequence exhausted");
        int o = slotOffset(slot);
        writeLong(o + 8, sequence);
        writeLong(o + 16, readLong(32));
        writeLong(o + 24, readLong(40));
        writeInt(o + 32, width);
        writeInt(o + 36, height);
        writeInt(o + 40, stride);
        writeInt(o + 44, BGRA8);
        writeInt(o + 48, length);
        writeInt(o + 52, 0);
        writeLong(o + 56, System.nanoTime());
        writeLong(o + 64, (readInt(20) & 1) == 0 ? 0 : fnv1a64(source.duplicate()));
        ByteBuffer destination = map.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        destination.position(o + SLOT_HEADER_SIZE);
        destination.limit(o + SLOT_HEADER_SIZE + length);
        destination.put(source);
        if (!compareState(slot, WRITING, READY))
            throw new IllegalStateException("Slot was not WRITING during publish");
        nextSequence = sequence;
        return sequence;
    }

    /** Releases a producer-owned WRITING slot after a failed capture or copy. */
    public void cancelWriting(int slot) {
        if (closed) return; // The mapping is abandoned on close; no slot may be reused.
        if (!compareState(slot, WRITING, FREE))
            throw new IllegalStateException("Slot was not WRITING during cancellation");
    }

    public int tryAcquireLatestReady() {
        if (closed) return -1;
        while (true) {
            int best=-1; long bestSequence=0;
            for(int i=0;i<SLOT_COUNT;i++) {
                if((int)STATE.getAcquire(map,slotOffset(i))!=READY) continue;
                long seq=readLong(slotOffset(i)+8);
                if(best<0 || Long.compareUnsigned(seq,bestSequence)>0){best=i;bestSequence=seq;}
            }
            if(best<0) return -1;
            if(!compareState(best,READY,READING)) continue;

            boolean valid = validMetadata(best)
                && readLong(slotOffset(best)+16)==readLong(32)
                && readLong(slotOffset(best)+24)==readLong(40)
                && ((readInt(20) & 1) == 0 || validateChecksum(best));
            if(!valid) {
                compareState(best,READING,FREE);
                continue;
            }
            return best;
        }
    }

    public FrameMetadata metadata(int slot) {
        validateSlot(slot);
        if ((int) STATE.getAcquire(map, slotOffset(slot)) != READING)
            throw new IllegalStateException("Metadata can only be read from an owned READING slot");
        int o = slotOffset(slot);
        return new FrameMetadata(readLong(o + 8), readLong(o + 56), readInt(o + 32), readInt(o + 36),
                readInt(o + 40), readInt(o + 44), readInt(o + 48), readLong(o + 64));
    }

    public byte[] readPayload(int slot) {
        validateSlot(slot);
        if ((int) STATE.getAcquire(map, slotOffset(slot)) != READING)
            throw new IllegalStateException("Payload can only be read from an owned READING slot");
        int o=slotOffset(slot), len=readInt(o+48);
        if(len<0 || len>SLOT_CAPACITY) throw new IllegalStateException("Invalid payload length");
        byte[] out=new byte[len]; ByteBuffer src=map.duplicate(); src.position(o+SLOT_HEADER_SIZE); src.get(out); return out;
    }

    public void releaseReading(int slot) { if(!compareState(slot,READING,FREE)) throw new IllegalStateException("Slot was not READING"); }

    public void drainStaleReady(long newestSequence) {
        for(int i=0;i<SLOT_COUNT;i++) if((int)STATE.getAcquire(map,slotOffset(i))==READY && Long.compareUnsigned(readLong(slotOffset(i)+8),newestSequence)<0)
            if(compareState(i,READY,READING)) compareState(i,READING,FREE);
    }

    private void initializeNextSequence() {
        long max = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            int state = (int) STATE.getAcquire(map, slotOffset(i));
            if (state == READY || state == READING) {
                long sequence = readLong(slotOffset(i) + 8);
                if (Long.compareUnsigned(sequence, max) > 0) max = sequence;
            }
        }
        nextSequence = max;
    }

    private boolean validateChecksum(int slot) {
        int o = slotOffset(slot), len = readInt(o + 48);
        long expected = readLong(o + 64), actual = 0xcbf29ce484222325L;
        ByteBuffer src = map.duplicate(); src.position(o + SLOT_HEADER_SIZE).limit(o + SLOT_HEADER_SIZE + len);
        while (src.hasRemaining()) { actual ^= (src.get() & 0xff); actual *= 0x100000001b3L; }
        return actual == expected;
    }

    private boolean validMetadata(int slot) {
        int o=slotOffset(slot), w=readInt(o+32), h=readInt(o+36), stride=readInt(o+40), fmt=readInt(o+44), len=readInt(o+48);
        if (readInt(o + 4) != 0 || readInt(o + 52) != 0 || !allZero(map, o + 72, 56)) return false;
        if(w<1||w>WIDTH||h<1||h>HEIGHT||fmt!=BGRA8||stride<w*4||stride>SLOT_CAPACITY||len<0||len>SLOT_CAPACITY)return false;
        long required=((long)h-1L)*stride+(long)w*4L; return required<=len;
    }
    private static void validate(int slot,int w,int h,int stride,int len){validateSlot(slot);if(w<1||w>WIDTH||h<1||h>HEIGHT||stride<w*4||stride>SLOT_CAPACITY||len<0||len>SLOT_CAPACITY)throw new IllegalArgumentException();long required=((long)h-1)*stride+(long)w*4L;if(required>len)throw new IllegalArgumentException("Payload is smaller than frame footprint");}
    private static void validateSlot(int slot){if(slot<0||slot>=SLOT_COUNT)throw new IllegalArgumentException("slot");}
    private int slotOffset(int slot){validateSlot(slot);return (int)(HEADER_SIZE+slot*SLOT_STRIDE);}
    private boolean compareState(int slot,int expected,int next){return STATE.compareAndSet(map,slotOffset(slot),expected,next);}
    private void setState(int slot,int state){STATE.setVolatile(map,slotOffset(slot),state);}
    private int readInt(int o){return map.getInt(o);} private short readShort(int o){return map.getShort(o);} private long readLong(int o){return map.getLong(o);}
    private void writeInt(int o,int v){map.putInt(o,v);} private void writeShort(int o,short v){map.putShort(o,v);} private void writeLong(int o,long v){map.putLong(o,v);}
    public record FrameMetadata(long sequence, long timestampNs, int width, int height, int stride,
                                int pixelFormat, int payloadLength, long checksum) {}

    private static boolean allZero(ByteBuffer source, int offset, int length) {
        for (int i = 0; i < length; i++) if (source.get(offset + i) != 0) return false;
        return true;
    }

    private static long fnv1a64(ByteBuffer data) {
        long hash = 0xcbf29ce484222325L;
        ByteBuffer source = data.duplicate();
        while (source.hasRemaining()) {
            hash ^= source.get() & 0xff;
            hash *= 0x100000001b3L;
        }
        return hash;
    }
    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        channel.close();
    }
}
