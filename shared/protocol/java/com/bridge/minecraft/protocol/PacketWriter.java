package com.bridge.minecraft.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class PacketWriter {
    private final ByteArrayOutputStream stream;

    public PacketWriter(int initialCapacity) {
        this.stream = new ByteArrayOutputStream(initialCapacity);
    }

    public PacketWriter() {
        this(256);
    }

    public int getLength() {
        return stream.size();
    }

    public void writeUInt8(int value) {
        stream.write(value & 0xFF);
    }

    public void writeInt8(byte value) {
        stream.write(value);
    }

    public void writeUInt16(int value) {
        stream.write(value & 0xFF);
        stream.write((value >> 8) & 0xFF);
    }

    public void writeInt16(short value) {
        writeUInt16(value);
    }

    public void writeUInt32(long value) {
        stream.write((int) (value & 0xFF));
        stream.write((int) ((value >> 8) & 0xFF));
        stream.write((int) ((value >> 16) & 0xFF));
        stream.write((int) ((value >> 24) & 0xFF));
    }

    public void writeInt32(int value) {
        stream.write(value & 0xFF);
        stream.write((value >> 8) & 0xFF);
        stream.write((value >> 16) & 0xFF);
        stream.write((value >> 24) & 0xFF);
    }

    public void writeUInt64(long value) {
        for (int i = 0; i < 8; i++) {
            stream.write((int) ((value >> (i * 8)) & 0xFF));
        }
    }

    public void writeInt64(long value) {
        writeUInt64(value);
    }

    public void writeFloat(float value) {
        writeInt32(Float.floatToIntBits(value));
    }

    public void writeDouble(double value) {
        writeInt64(Double.doubleToLongBits(value));
    }

    public void writeBoolean(boolean value) {
        stream.write(value ? 1 : 0);
    }

    public void writeString(String value) {
        if (value == null || value.isEmpty()) {
            writeUInt16(0);
            return;
        }

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > ProtocolConstants.MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("String byte length " + bytes.length +
                    " exceeds maximum " + ProtocolConstants.MAX_STRING_LENGTH);
        }

        writeUInt16(bytes.length);
        stream.write(bytes, 0, bytes.length);
    }

    public void writeBytes(byte[] buffer) {
        if (buffer != null && buffer.length > 0) {
            stream.write(buffer, 0, buffer.length);
        }
    }

    public byte[] toPayloadArray() {
        return stream.toByteArray();
    }

    public byte[] buildPacket(MessageType type, int sequenceId) {
        byte[] payload = stream.toByteArray();
        MessageHeader header = new MessageHeader(type, sequenceId, payload.length);
        byte[] packet = new byte[ProtocolConstants.HEADER_SIZE + payload.length];
        header.writeTo(packet, 0);
        System.arraycopy(payload, 0, packet, ProtocolConstants.HEADER_SIZE, payload.length);
        return packet;
    }
}
