using System;

namespace MinecraftBridge.Protocol
{
    public interface IMessage
    {
        MessageType MessageType { get; }
        void Serialize(PacketWriter writer);
    }

    public class HelloMessage : IMessage
    {
        public MessageType MessageType => MessageType.Hello;

        public ushort ProtocolVersion { get; set; } = ProtocolConstants.CurrentVersion;
        public string ClientName { get; set; } = string.Empty;
        public string ClientVersion { get; set; } = string.Empty;
        public uint Capabilities { get; set; }

        public void Serialize(PacketWriter writer)
        {
            writer.WriteUInt16(ProtocolVersion);
            writer.WriteString(ClientName);
            writer.WriteString(ClientVersion);
            writer.WriteUInt32(Capabilities);
        }

        public static HelloMessage Deserialize(PacketReader reader)
        {
            return new HelloMessage
            {
                ProtocolVersion = reader.ReadUInt16(),
                ClientName = reader.ReadString(),
                ClientVersion = reader.ReadString(),
                Capabilities = reader.ReadUInt32()
            };
        }
    }

    public class HelloAckMessage : IMessage
    {
        public MessageType MessageType => MessageType.HelloAck;

        public uint Status { get; set; } // 0 = OK, 1 = Version Mismatch, 2 = Rejected
        public ushort AcceptedVersion { get; set; }
        public uint SessionId { get; set; }
        public string ErrorMessage { get; set; } = string.Empty;

        public void Serialize(PacketWriter writer)
        {
            writer.WriteUInt32(Status);
            writer.WriteUInt16(AcceptedVersion);
            writer.WriteUInt32(SessionId);
            writer.WriteString(ErrorMessage);
        }

        public static HelloAckMessage Deserialize(PacketReader reader)
        {
            return new HelloAckMessage
            {
                Status = reader.ReadUInt32(),
                AcceptedVersion = reader.ReadUInt16(),
                SessionId = reader.ReadUInt32(),
                ErrorMessage = reader.ReadString()
            };
        }
    }

    public class PingMessage : IMessage
    {
        public MessageType MessageType => MessageType.Ping;
        public ulong TimestampNs { get; set; }

        public void Serialize(PacketWriter writer)
        {
            writer.WriteUInt64(TimestampNs);
        }

        public static PingMessage Deserialize(PacketReader reader)
        {
            return new PingMessage
            {
                TimestampNs = reader.ReadUInt64()
            };
        }
    }

    public class PongMessage : IMessage
    {
        public MessageType MessageType => MessageType.Pong;
        public ulong TimestampNs { get; set; }

        public void Serialize(PacketWriter writer)
        {
            writer.WriteUInt64(TimestampNs);
        }

        public static PongMessage Deserialize(PacketReader reader)
        {
            return new PongMessage
            {
                TimestampNs = reader.ReadUInt64()
            };
        }
    }

    public class FrameMetadataMessage : IMessage
    {
        public MessageType MessageType => MessageType.FrameMetadata;

        public uint BufferId { get; set; }
        public uint Width { get; set; }
        public uint Height { get; set; }
        public uint Stride { get; set; }
        public uint Format { get; set; } // 1 = RGBA8, 2 = BGRA8
        public ulong SequenceNumber { get; set; }
        public ulong TimestampNs { get; set; }

        public void Serialize(PacketWriter writer)
        {
            writer.WriteUInt32(BufferId);
            writer.WriteUInt32(Width);
            writer.WriteUInt32(Height);
            writer.WriteUInt32(Stride);
            writer.WriteUInt32(Format);
            writer.WriteUInt64(SequenceNumber);
            writer.WriteUInt64(TimestampNs);
        }

        public static FrameMetadataMessage Deserialize(PacketReader reader)
        {
            return new FrameMetadataMessage
            {
                BufferId = reader.ReadUInt32(),
                Width = reader.ReadUInt32(),
                Height = reader.ReadUInt32(),
                Stride = reader.ReadUInt32(),
                Format = reader.ReadUInt32(),
                SequenceNumber = reader.ReadUInt64(),
                TimestampNs = reader.ReadUInt64()
            };
        }
    }

    public class CameraStateMessage : IMessage
    {
        public MessageType MessageType => MessageType.CameraState;

        public double PosX { get; set; }
        public double PosY { get; set; }
        public double PosZ { get; set; }
        public float Yaw { get; set; }
        public float Pitch { get; set; }
        public float Roll { get; set; }
        public float Fov { get; set; }
        public ulong SequenceNumber { get; set; }

        public void Serialize(PacketWriter writer)
        {
            writer.WriteDouble(PosX);
            writer.WriteDouble(PosY);
            writer.WriteDouble(PosZ);
            writer.WriteFloat(Yaw);
            writer.WriteFloat(Pitch);
            writer.WriteFloat(Roll);
            writer.WriteFloat(Fov);
            writer.WriteUInt64(SequenceNumber);
        }

        public static CameraStateMessage Deserialize(PacketReader reader)
        {
            return new CameraStateMessage
            {
                PosX = reader.ReadDouble(),
                PosY = reader.ReadDouble(),
                PosZ = reader.ReadDouble(),
                Yaw = reader.ReadFloat(),
                Pitch = reader.ReadFloat(),
                Roll = reader.ReadFloat(),
                Fov = reader.ReadFloat(),
                SequenceNumber = reader.ReadUInt64()
            };
        }
    }

    public class InputEventMessage : IMessage
    {
        public MessageType MessageType => MessageType.InputEvent;

        public byte EventType { get; set; } // 1=KeyDown, 2=KeyUp, 3=MouseMoveRel, 4=MouseDown, 5=MouseUp, 6=Wheel
        public uint KeyCode { get; set; }
        public int MouseDx { get; set; }
        public int MouseDy { get; set; }
        public uint MouseButtons { get; set; }
        public int WheelDelta { get; set; }

        public void Serialize(PacketWriter writer)
        {
            writer.WriteUInt8(EventType);
            writer.WriteUInt32(KeyCode);
            writer.WriteInt32(MouseDx);
            writer.WriteInt32(MouseDy);
            writer.WriteUInt32(MouseButtons);
            writer.WriteInt32(WheelDelta);
        }

        public static InputEventMessage Deserialize(PacketReader reader)
        {
            return new InputEventMessage
            {
                EventType = reader.ReadUInt8(),
                KeyCode = reader.ReadUInt32(),
                MouseDx = reader.ReadInt32(),
                MouseDy = reader.ReadInt32(),
                MouseButtons = reader.ReadUInt32(),
                WheelDelta = reader.ReadInt32()
            };
        }
    }

    public class InputFocusMessage : IMessage
    {
        public MessageType MessageType => MessageType.InputFocus;

        public bool HasFocus { get; set; }
        public bool ReleaseHeldKeys { get; set; }

        public void Serialize(PacketWriter writer)
        {
            writer.WriteBoolean(HasFocus);
            writer.WriteBoolean(ReleaseHeldKeys);
        }

        public static InputFocusMessage Deserialize(PacketReader reader)
        {
            return new InputFocusMessage
            {
                HasFocus = reader.ReadBoolean(),
                ReleaseHeldKeys = reader.ReadBoolean()
            };
        }
    }

    public class ErrorMessage : IMessage
    {
        public MessageType MessageType => MessageType.Error;

        public uint ErrorCode { get; set; }
        public string Description { get; set; } = string.Empty;

        public void Serialize(PacketWriter writer)
        {
            writer.WriteUInt32(ErrorCode);
            writer.WriteString(Description);
        }

        public static ErrorMessage Deserialize(PacketReader reader)
        {
            return new ErrorMessage
            {
                ErrorCode = reader.ReadUInt32(),
                Description = reader.ReadString()
            };
        }
    }

    public class ShutdownMessage : IMessage
    {
        public MessageType MessageType => MessageType.Shutdown;

        public uint ReasonCode { get; set; }
        public string ReasonText { get; set; } = string.Empty;

        public void Serialize(PacketWriter writer)
        {
            writer.WriteUInt32(ReasonCode);
            writer.WriteString(ReasonText);
        }

        public static ShutdownMessage Deserialize(PacketReader reader)
        {
            return new ShutdownMessage
            {
                ReasonCode = reader.ReadUInt32(),
                ReasonText = reader.ReadString()
            };
        }
    }
}
