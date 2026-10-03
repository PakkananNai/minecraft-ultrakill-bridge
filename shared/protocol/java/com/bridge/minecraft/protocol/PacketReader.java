package com.bridge.minecraft.protocol;

import java.nio.charset.StandardCharsets;

public class PacketReader {
    private final byte[] buffer;
    private final int offset;
    private final int limit;
    private int position;

    public PacketReader(byte[] buffer, int offset, int length) {
        if (buffer == null) throw new IllegalArgumentException("Buffer cannot be null");
        if (offset < 0 || length < 0 || offset + length > buffer.length) {
            throw new IndexOutOfBoundsException("Offset and length exceed buffer boundary");
        }
        this.buffer = buffer;
        this.offset = offset;
        this.position = offset;
        this.limit = offset + length;
    }

    public PacketReader(byte[] buffer) {
        this(buffer, 0, buffer == null ? 0 : buffer.length);
    }

    public int getRemaining() {
        return limit - position;
    }

    private void checkRemaining(int bytes) throws ProtocolException.TruncatedException {
        if (getRemaining() < bytes) {
            throw new ProtocolException.TruncatedException(
                    "Expected at least " + bytes + " bytes, but only " + getRemaining() + " remain");
        }
    }

    public int readUInt8() throws ProtocolException.TruncatedException {
        checkRemaining(1);
        return buffer[position++] & 0xFF;
    }

    public byte readInt8() throws ProtocolException.TruncatedException {
        checkRemaining(1);
        return buffer[position++];
    }

    public int readUInt16() throws ProtocolException.TruncatedException {
        checkRemaining(2);
        int b0 = buffer[position++] & 0xFF;
        int b1 = buffer[position++] & 0xFF;
        return (b1 << 8) | b0;
    }

    public short readInt16() throws ProtocolException.TruncatedException {
        return (short) readUInt16();
    }

    public long readUInt32() throws ProtocolException.TruncatedException {
        checkRemaining(4);
        long b0 = buffer[position++] & 0xFFL;
        long b1 = buffer[position++] & 0xFFL;
        long b2 = buffer[position++] & 0xFFL;
        long b3 = buffer[position++] & 0xFFL;
        return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    public int readInt32() throws ProtocolException.TruncatedException {
        return (int) readUInt32();
    }

    public long readUInt64() throws ProtocolException.TruncatedException {
        checkRemaining(8);
        long result = 0;
        for (int i = 0; i < 8; i++) {
            long b = buffer[position++] & 0xFFL;
            result |= (b << (i * 8));
        }
        return result;
    }

    public long readInt64() throws ProtocolException.TruncatedException {
        return readUInt64();
    }

    public float readFloat() throws ProtocolException.TruncatedException {
        return Float.intBitsToFloat(readInt32());
    }

    public double readDouble() throws ProtocolException.TruncatedException {
        return Double.longBitsToDouble(readInt64());
    }

    public boolean readBoolean() throws ProtocolException.TruncatedException {
        return readUInt8() != 0;
    }

    public String readString() throws ProtocolException {
        int length = readUInt16();
        if (length == 0) {
            return "";
        }

        if (length > ProtocolConstants.MAX_STRING_LENGTH) {
            throw new ProtocolException.ValidationException(
                    "String length " + length + " exceeds limit " + ProtocolConstants.MAX_STRING_LENGTH);
        }

        checkRemaining(length);
        String str = new String(buffer, position, length, StandardCharsets.UTF_8);
        position += length;
        return str;
    }

    public byte[] readBytes(int count) throws ProtocolException.TruncatedException {
        if (count == 0) return new byte[0];
        checkRemaining(count);
        byte[] result = new byte[count];
        System.arraycopy(buffer, position, result, 0, count);
        position += count;
        return result;
    }
}
