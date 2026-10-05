package com.bridge.minecraft.protocol;

public final class Messages {
    private Messages() {}

    public interface Message {
        MessageType getMessageType();
        void serialize(PacketWriter writer);
    }

    public static class HelloMessage implements Message {
        private short protocolVersion = ProtocolConstants.CURRENT_VERSION;
        private String clientName = "";
        private String clientVersion = "";
        private long capabilities = 0;

        public HelloMessage() {}

        public HelloMessage(short protocolVersion, String clientName, String clientVersion, long capabilities) {
            this.protocolVersion = protocolVersion;
            this.clientName = clientName;
            this.clientVersion = clientVersion;
            this.capabilities = capabilities;
        }

        @Override
        public MessageType getMessageType() { return MessageType.HELLO; }

        public short getProtocolVersion() { return protocolVersion; }
        public void setProtocolVersion(short protocolVersion) { this.protocolVersion = protocolVersion; }

        public String getClientName() { return clientName; }
        public void setClientName(String clientName) { this.clientName = clientName; }

        public String getClientVersion() { return clientVersion; }
        public void setClientVersion(String clientVersion) { this.clientVersion = clientVersion; }

        public long getCapabilities() { return capabilities; }
        public void setCapabilities(long capabilities) { this.capabilities = capabilities; }

        @Override
        public void serialize(PacketWriter writer) {
            writer.writeUInt16(protocolVersion);
            writer.writeString(clientName);
            writer.writeString(clientVersion);
            writer.writeUInt32(capabilities);
        }

        public static HelloMessage deserialize(PacketReader reader) throws ProtocolException {
            HelloMessage msg = new HelloMessage();
            msg.protocolVersion = (short) reader.readUInt16();
            msg.clientName = reader.readString();
            msg.clientVersion = reader.readString();
            msg.capabilities = reader.readUInt32();
            return msg;
        }
    }

    public static class HelloAckMessage implements Message {
        private long status;
        private short acceptedVersion;
        private long sessionId;
        private String errorMessage = "";

        public HelloAckMessage() {}

        public HelloAckMessage(long status, short acceptedVersion, long sessionId, String errorMessage) {
            this.status = status;
            this.acceptedVersion = acceptedVersion;
            this.sessionId = sessionId;
            this.errorMessage = errorMessage;
        }

        @Override
        public MessageType getMessageType() { return MessageType.HELLO_ACK; }

        public long getStatus() { return status; }
        public short getAcceptedVersion() { return acceptedVersion; }
        public long getSessionId() { return sessionId; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public void serialize(PacketWriter writer) {
            writer.writeUInt32(status);
            writer.writeUInt16(acceptedVersion);
            writer.writeUInt32(sessionId);
            writer.writeString(errorMessage);
        }

        public static HelloAckMessage deserialize(PacketReader reader) throws ProtocolException {
            HelloAckMessage msg = new HelloAckMessage();
            msg.status = reader.readUInt32();
            msg.acceptedVersion = (short) reader.readUInt16();
            msg.sessionId = reader.readUInt32();
            msg.errorMessage = reader.readString();
            return msg;
        }
    }

    public static class PingMessage implements Message {
        private long timestampNs;

        public PingMessage() {}
        public PingMessage(long timestampNs) { this.timestampNs = timestampNs; }

        @Override
        public MessageType getMessageType() { return MessageType.PING; }
        public long getTimestampNs() { return timestampNs; }

        @Override
        public void serialize(PacketWriter writer) {
            writer.writeUInt64(timestampNs);
        }

        public static PingMessage deserialize(PacketReader reader) throws ProtocolException {
            return new PingMessage(reader.readUInt64());
        }
    }

    public static class PongMessage implements Message {
        private long timestampNs;

        public PongMessage() {}
        public PongMessage(long timestampNs) { this.timestampNs = timestampNs; }

        @Override
        public MessageType getMessageType() { return MessageType.PONG; }
        public long getTimestampNs() { return timestampNs; }

        @Override
        public void serialize(PacketWriter writer) {
            writer.writeUInt64(timestampNs);
        }

        public static PongMessage deserialize(PacketReader reader) throws ProtocolException {
            return new PongMessage(reader.readUInt64());
        }
    }

    /** Advertises the initialized shared framebuffer mapping to the guest. */
    public static class StartStreamMessage implements Message {
        private long sessionId;
        private String mappingPath = "";
        private long generationHi;
        private long generationLo;

        public StartStreamMessage() {}
        public StartStreamMessage(long sessionId, String mappingPath, long generationHi, long generationLo) {
            this.sessionId = sessionId;
            this.mappingPath = mappingPath;
            this.generationHi = generationHi;
            this.generationLo = generationLo;
            validate();
        }

        @Override public MessageType getMessageType() { return MessageType.START_STREAM; }
        public long getSessionId() { return sessionId; }
        public String getMappingPath() { return mappingPath; }
        public long getGenerationHi() { return generationHi; }
        public long getGenerationLo() { return generationLo; }

        @Override public void serialize(PacketWriter writer) {
            validate();
            writer.writeUInt32(sessionId);
            writer.writeString(mappingPath);
            writer.writeUInt64(generationHi);
            writer.writeUInt64(generationLo);
        }

        public static StartStreamMessage deserialize(PacketReader reader) throws ProtocolException {
            StartStreamMessage message = new StartStreamMessage();
            message.sessionId = reader.readUInt32();
            message.mappingPath = reader.readString();
            message.generationHi = reader.readUInt64();
            message.generationLo = reader.readUInt64();
            try {
                message.validate();
            } catch (IllegalArgumentException invalid) {
                throw new ProtocolException.ValidationException(invalid.getMessage());
            }
            return message;
        }

        private void validate() {
            if (sessionId <= 0 || sessionId > 0xffffffffL) throw new IllegalArgumentException("START_STREAM SessionId must be non-zero uint32");
            if (mappingPath == null || mappingPath.equals("/") || !mappingPath.startsWith("/") ||
                    mappingPath.contains("\\") || mappingPath.contains("//") || mappingPath.endsWith("/") || mappingPath.indexOf('\0') >= 0)
                throw new IllegalArgumentException("START_STREAM MappingPath must be a normalized Linux file path");
            for (String segment : mappingPath.split("/", -1))
                if (segment.equals(".") || segment.equals("..")) throw new IllegalArgumentException("START_STREAM MappingPath must be normalized");
            if (generationHi == 0 && generationLo == 0) throw new IllegalArgumentException("START_STREAM generation must be non-zero");
        }
    }

    public static class FrameMetadataMessage implements Message {
        private long bufferId;
        private long width;
        private long height;
        private long stride;
        private long format;
        private long sequenceNumber;
        private long timestampNs;

        public FrameMetadataMessage() {}

        public FrameMetadataMessage(long bufferId, long width, long height, long stride, long format, long sequenceNumber, long timestampNs) {
            this.bufferId = bufferId;
            this.width = width;
            this.height = height;
            this.stride = stride;
            this.format = format;
            this.sequenceNumber = sequenceNumber;
            this.timestampNs = timestampNs;
        }

        @Override
        public MessageType getMessageType() { return MessageType.FRAME_METADATA; }

        public long getBufferId() { return bufferId; }
        public long getWidth() { return width; }
        public long getHeight() { return height; }
        public long getStride() { return stride; }
        public long getFormat() { return format; }
        public long getSequenceNumber() { return sequenceNumber; }
        public long getTimestampNs() { return timestampNs; }

        @Override
        public void serialize(PacketWriter writer) {
            writer.writeUInt32(bufferId);
            writer.writeUInt32(width);
            writer.writeUInt32(height);
            writer.writeUInt32(stride);
            writer.writeUInt32(format);
            writer.writeUInt64(sequenceNumber);
            writer.writeUInt64(timestampNs);
        }

        public static FrameMetadataMessage deserialize(PacketReader reader) throws ProtocolException {
            FrameMetadataMessage msg = new FrameMetadataMessage();
            msg.bufferId = reader.readUInt32();
            msg.width = reader.readUInt32();
            msg.height = reader.readUInt32();
            msg.stride = reader.readUInt32();
            msg.format = reader.readUInt32();
            msg.sequenceNumber = reader.readUInt64();
            msg.timestampNs = reader.readUInt64();
            return msg;
        }
    }

    public static class CameraStateMessage implements Message {
        private double posX, posY, posZ;
        private float yaw, pitch, roll, fov;
        private long sequenceNumber;

        public CameraStateMessage() {}

        public CameraStateMessage(double posX, double posY, double posZ, float yaw, float pitch, float roll, float fov, long sequenceNumber) {
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            this.fov = fov;
            this.sequenceNumber = sequenceNumber;
        }

        @Override
        public MessageType getMessageType() { return MessageType.CAMERA_STATE; }

        public double getPosX() { return posX; }
        public double getPosY() { return posY; }
        public double getPosZ() { return posZ; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
        public float getRoll() { return roll; }
        public float getFov() { return fov; }
        public long getSequenceNumber() { return sequenceNumber; }

        @Override
        public void serialize(PacketWriter writer) {
            writer.writeDouble(posX);
            writer.writeDouble(posY);
            writer.writeDouble(posZ);
            writer.writeFloat(yaw);
            writer.writeFloat(pitch);
            writer.writeFloat(roll);
            writer.writeFloat(fov);
            writer.writeUInt64(sequenceNumber);
        }

        public static CameraStateMessage deserialize(PacketReader reader) throws ProtocolException {
            CameraStateMessage msg = new CameraStateMessage();
            msg.posX = reader.readDouble();
            msg.posY = reader.readDouble();
            msg.posZ = reader.readDouble();
            msg.yaw = reader.readFloat();
            msg.pitch = reader.readFloat();
            msg.roll = reader.readFloat();
            msg.fov = reader.readFloat();
            msg.sequenceNumber = reader.readUInt64();
            return msg;
        }
    }

    public static class InputEventMessage implements Message {
        private int eventType;
        private long keyCode;
        private int mouseDx;
        private int mouseDy;
        private long mouseButtons;
        private int wheelDelta;

        public InputEventMessage() {}

        public InputEventMessage(int eventType, long keyCode, int mouseDx, int mouseDy, long mouseButtons, int wheelDelta) {
            this.eventType = eventType;
            this.keyCode = keyCode;
            this.mouseDx = mouseDx;
            this.mouseDy = mouseDy;
            this.mouseButtons = mouseButtons;
            this.wheelDelta = wheelDelta;
        }

        @Override
        public MessageType getMessageType() { return MessageType.INPUT_EVENT; }

        public int getEventType() { return eventType; }
        public long getKeyCode() { return keyCode; }
        public int getMouseDx() { return mouseDx; }
        public int getMouseDy() { return mouseDy; }
        public long getMouseButtons() { return mouseButtons; }
        public int getWheelDelta() { return wheelDelta; }

        @Override
        public void serialize(PacketWriter writer) {
            writer.writeUInt8(eventType);
            writer.writeUInt32(keyCode);
            writer.writeInt32(mouseDx);
            writer.writeInt32(mouseDy);
            writer.writeUInt32(mouseButtons);
            writer.writeInt32(wheelDelta);
        }

        public static InputEventMessage deserialize(PacketReader reader) throws ProtocolException {
            InputEventMessage msg = new InputEventMessage();
            msg.eventType = reader.readUInt8();
            msg.keyCode = reader.readUInt32();
            msg.mouseDx = reader.readInt32();
            msg.mouseDy = reader.readInt32();
            msg.mouseButtons = reader.readUInt32();
            msg.wheelDelta = reader.readInt32();
            return msg;
        }
    }

    public static class InputFocusMessage implements Message {
        private boolean hasFocus;
        private boolean releaseHeldKeys;

        public InputFocusMessage() {}
        public InputFocusMessage(boolean hasFocus, boolean releaseHeldKeys) {
            this.hasFocus = hasFocus;
            this.releaseHeldKeys = releaseHeldKeys;
        }

        @Override
        public MessageType getMessageType() { return MessageType.INPUT_FOCUS; }

        public boolean isHasFocus() { return hasFocus; }
        public boolean isReleaseHeldKeys() { return releaseHeldKeys; }

        @Override
        public void serialize(PacketWriter writer) {
            writer.writeBoolean(hasFocus);
            writer.writeBoolean(releaseHeldKeys);
        }

        public static InputFocusMessage deserialize(PacketReader reader) throws ProtocolException {
            return new InputFocusMessage(reader.readBoolean(), reader.readBoolean());
        }
    }

    public static class ErrorMessage implements Message {
        private long errorCode;
        private String description = "";

        public ErrorMessage() {}
        public ErrorMessage(long errorCode, String description) {
            this.errorCode = errorCode;
            this.description = description;
        }

        @Override
        public MessageType getMessageType() { return MessageType.ERROR; }

        public long getErrorCode() { return errorCode; }
        public String getDescription() { return description; }

        @Override
        public void serialize(PacketWriter writer) {
            writer.writeUInt32(errorCode);
            writer.writeString(description);
        }

        public static ErrorMessage deserialize(PacketReader reader) throws ProtocolException {
            return new ErrorMessage(reader.readUInt32(), reader.readString());
        }
    }

    public static class ShutdownMessage implements Message {
        private long reasonCode;
        private String reasonText = "";

        public ShutdownMessage() {}
        public ShutdownMessage(long reasonCode, String reasonText) {
            this.reasonCode = reasonCode;
            this.reasonText = reasonText;
        }

        @Override
        public MessageType getMessageType() { return MessageType.SHUTDOWN; }

        public long getReasonCode() { return reasonCode; }
        public String getReasonText() { return reasonText; }

        @Override
        public void serialize(PacketWriter writer) {
            writer.writeUInt32(reasonCode);
            writer.writeString(reasonText);
        }

        public static ShutdownMessage deserialize(PacketReader reader) throws ProtocolException {
            return new ShutdownMessage(reader.readUInt32(), reader.readString());
        }
    }
}
