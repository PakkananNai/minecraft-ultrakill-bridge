package com.bridge.minecraft.protocol;

public class MessageHeader {
    private final int magic;
    private final short version;
    private final MessageType type;
    private final int sequenceId;
    private final int payloadLength;

    public MessageHeader(MessageType type, int sequenceId, int payloadLength, short version) {
        this.magic = ProtocolConstants.MAGIC;
        this.version = version;
        this.type = type;
        this.sequenceId = sequenceId;
        this.payloadLength = payloadLength;
    }

    public MessageHeader(MessageType type, int sequenceId, int payloadLength) {
        this(type, sequenceId, payloadLength, ProtocolConstants.CURRENT_VERSION);
    }

    public MessageHeader(int magic, short version, MessageType type, int sequenceId, int payloadLength) {
        this.magic = magic;
        this.version = version;
        this.type = type;
        this.sequenceId = sequenceId;
        this.payloadLength = payloadLength;
    }

    public int getMagic() { return magic; }
    public short getVersion() { return version; }
    public MessageType getType() { return type; }
    public int getSequenceId() { return sequenceId; }
    public int getPayloadLength() { return payloadLength; }

    public void validate() throws ProtocolException {
        if (magic != ProtocolConstants.MAGIC) {
            throw new ProtocolException.ValidationException(
                String.format("Invalid packet magic: 0x%08X, expected 0x%08X", magic, ProtocolConstants.MAGIC));
        }

        if (version != ProtocolConstants.CURRENT_VERSION) {
            throw new ProtocolException.VersionMismatchException(ProtocolConstants.CURRENT_VERSION, version);
        }

        if (payloadLength < 0 || payloadLength > ProtocolConstants.MAX_PAYLOAD_LENGTH) {
            throw new ProtocolException.ValidationException(
                "Payload length " + payloadLength + " exceeds limit " + ProtocolConstants.MAX_PAYLOAD_LENGTH);
        }
    }

    public byte[] toBytes() {
        byte[] buffer = new byte[ProtocolConstants.HEADER_SIZE];
        writeTo(buffer, 0);
        return buffer;
    }

    public void writeTo(byte[] buffer, int offset) {
        if (buffer == null || buffer.length < offset + ProtocolConstants.HEADER_SIZE) {
            throw new IllegalArgumentException("Buffer too small for header");
        }

        // Little-Endian write
        buffer[offset + 0] = (byte) (magic & 0xFF);
        buffer[offset + 1] = (byte) ((magic >> 8) & 0xFF);
        buffer[offset + 2] = (byte) ((magic >> 16) & 0xFF);
        buffer[offset + 3] = (byte) ((magic >> 24) & 0xFF);

        buffer[offset + 4] = (byte) (version & 0xFF);
        buffer[offset + 5] = (byte) ((version >> 8) & 0xFF);

        int typeVal = type.getId();
        buffer[offset + 6] = (byte) (typeVal & 0xFF);
        buffer[offset + 7] = (byte) ((typeVal >> 8) & 0xFF);

        buffer[offset + 8] = (byte) (sequenceId & 0xFF);
        buffer[offset + 9] = (byte) ((sequenceId >> 8) & 0xFF);
        buffer[offset + 10] = (byte) ((sequenceId >> 16) & 0xFF);
        buffer[offset + 11] = (byte) ((sequenceId >> 24) & 0xFF);

        buffer[offset + 12] = (byte) (payloadLength & 0xFF);
        buffer[offset + 13] = (byte) ((payloadLength >> 8) & 0xFF);
        buffer[offset + 14] = (byte) ((payloadLength >> 16) & 0xFF);
        buffer[offset + 15] = (byte) ((payloadLength >> 24) & 0xFF);
    }

    public static MessageHeader parse(byte[] buffer, int offset, boolean validate) throws ProtocolException {
        if (buffer == null || buffer.length < offset + ProtocolConstants.HEADER_SIZE) {
            throw new ProtocolException.TruncatedException("Buffer too small for header");
        }

        int magic = (buffer[offset + 0] & 0xFF) |
                    ((buffer[offset + 1] & 0xFF) << 8) |
                    ((buffer[offset + 2] & 0xFF) << 16) |
                    ((buffer[offset + 3] & 0xFF) << 24);

        short version = (short) ((buffer[offset + 4] & 0xFF) | ((buffer[offset + 5] & 0xFF) << 8));
        int typeVal = (buffer[offset + 6] & 0xFF) | ((buffer[offset + 7] & 0xFF) << 8);

        int sequenceId = (buffer[offset + 8] & 0xFF) |
                         ((buffer[offset + 9] & 0xFF) << 8) |
                         ((buffer[offset + 10] & 0xFF) << 16) |
                         ((buffer[offset + 11] & 0xFF) << 24);

        int payloadLength = (buffer[offset + 12] & 0xFF) |
                            ((buffer[offset + 13] & 0xFF) << 8) |
                            ((buffer[offset + 14] & 0xFF) << 16) |
                            ((buffer[offset + 15] & 0xFF) << 24);

        MessageHeader header = new MessageHeader(magic, version, MessageType.fromId(typeVal), sequenceId, payloadLength);
        if (validate) {
            header.validate();
        }
        return header;
    }

    @Override
    public String toString() {
        return String.format("Header[Magic=0x%08X, Ver=%d, Type=%s, Seq=%d, Len=%d]",
                magic, version, type, sequenceId, payloadLength);
    }
}
