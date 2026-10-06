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

    /// <summary>Advertises the initialized shared framebuffer mapping to the guest.</summary>
    public sealed class StartStreamMessage : IMessage
    {
        public MessageType MessageType => MessageType.StartStream;
        public uint SessionId { get; set; }
        public string MappingPath { get; set; } = string.Empty;
        public ulong GenerationHi { get; set; }
        public ulong GenerationLo { get; set; }

        public void Serialize(PacketWriter writer)
        {
            Validate();
            writer.WriteUInt32(SessionId);
            writer.WriteString(MappingPath);
            writer.WriteUInt64(GenerationHi);
            writer.WriteUInt64(GenerationLo);
        }

        public static StartStreamMessage Deserialize(PacketReader reader)
        {
            var message = new StartStreamMessage
            {
                SessionId = reader.ReadUInt32(),
                MappingPath = reader.ReadString(),
                GenerationHi = reader.ReadUInt64(),
                GenerationLo = reader.ReadUInt64()
            };
            message.Validate();
            return message;
        }

        private void Validate()
        {
            if (SessionId == 0) throw new ProtocolValidationException("START_STREAM SessionId must be non-zero");
            if (string.IsNullOrEmpty(MappingPath) || MappingPath == "/" || MappingPath[0] != '/' ||
                MappingPath.Contains("\\") || MappingPath.Contains("//") || MappingPath.EndsWith("/") || MappingPath.IndexOf('\0') >= 0)
                throw new ProtocolValidationException("START_STREAM MappingPath must be a normalized Linux file path");
            foreach (string segment in MappingPath.Split('/'))
                if (segment == "." || segment == "..") throw new ProtocolValidationException("START_STREAM MappingPath must be normalized");
            if (GenerationHi == 0 && GenerationLo == 0)
                throw new ProtocolValidationException("START_STREAM generation must be non-zero");
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

    public sealed class RaycastRequestMessage : IMessage
    {
        public MessageType MessageType => MessageType.RaycastRequest;
        public ulong RequestId { get; set; }
        public float MaxDistance { get; set; }

        public void Serialize(PacketWriter writer)
        {
            writer.WriteUInt64(RequestId);
            writer.WriteFloat(MaxDistance);
        }

        public static RaycastRequestMessage Deserialize(PacketReader reader)
        {
            return new RaycastRequestMessage
            {
                RequestId = reader.ReadUInt64(),
                MaxDistance = reader.ReadFloat()
            };
        }
    }

    public sealed class RaycastResponseMessage : IMessage
    {
        public MessageType MessageType => MessageType.RaycastResponse;
        public ulong RequestId { get; set; }
        public bool Hit { get; set; }
        public int BlockX { get; set; }
        public int BlockY { get; set; }
        public int BlockZ { get; set; }
        public byte Side { get; set; }
        public double HitX { get; set; }
        public double HitY { get; set; }
        public double HitZ { get; set; }
        public float Distance { get; set; }
        public string BlockId { get; set; } = string.Empty;

        public void Serialize(PacketWriter writer)
        {
            writer.WriteUInt64(RequestId);
            writer.WriteBoolean(Hit);
            writer.WriteInt32(BlockX);
            writer.WriteInt32(BlockY);
            writer.WriteInt32(BlockZ);
            writer.WriteUInt8(Side);
            writer.WriteDouble(HitX);
            writer.WriteDouble(HitY);
            writer.WriteDouble(HitZ);
            writer.WriteFloat(Distance);
            writer.WriteString(BlockId);
        }

        public static RaycastResponseMessage Deserialize(PacketReader reader)
        {
            return new RaycastResponseMessage
            {
                RequestId = reader.ReadUInt64(),
                Hit = reader.ReadBoolean(),
                BlockX = reader.ReadInt32(),
                BlockY = reader.ReadInt32(),
                BlockZ = reader.ReadInt32(),
                Side = reader.ReadUInt8(),
                HitX = reader.ReadDouble(),
                HitY = reader.ReadDouble(),
                HitZ = reader.ReadDouble(),
                Distance = reader.ReadFloat(),
                BlockId = reader.ReadString()
            };
        }
    }


    public sealed class DamageEventMessage : IMessage
    {
        public MessageType MessageType => MessageType.DamageEvent;
        public byte EventType { get; set; } // 1=damage, 2=health update
        public uint TargetId { get; set; }
        public uint AttackerId { get; set; } // uint.MaxValue = no attacker
        public float Amount { get; set; }
        public float Health { get; set; }
        public float MaxHealth { get; set; }
        public string SourceType { get; set; } = string.Empty;

        public void Serialize(PacketWriter writer)
        {
            writer.WriteUInt8(EventType); writer.WriteUInt32(TargetId); writer.WriteUInt32(AttackerId);
            writer.WriteFloat(Amount); writer.WriteFloat(Health); writer.WriteFloat(MaxHealth);
            writer.WriteString(SourceType);
        }

        public static DamageEventMessage Deserialize(PacketReader reader)
        {
            return new DamageEventMessage
            {
                EventType = reader.ReadUInt8(), TargetId = reader.ReadUInt32(), AttackerId = reader.ReadUInt32(),
                Amount = reader.ReadFloat(), Health = reader.ReadFloat(), MaxHealth = reader.ReadFloat(),
                SourceType = reader.ReadString()
            };
        }
    }

    public sealed class EntityUpdateMessage : IMessage
    {
        public MessageType MessageType => MessageType.EntityUpdate;
        public uint EntityId { get; set; }
        public string EntityType { get; set; } = string.Empty;
        public double PosX { get; set; }
        public double PosY { get; set; }
        public double PosZ { get; set; }
        public float Yaw { get; set; }
        public float Pitch { get; set; }
        public float VelocityX { get; set; }
        public float VelocityY { get; set; }
        public float VelocityZ { get; set; }
        public byte Flags { get; set; }

        public void Serialize(PacketWriter writer)
        {
            writer.WriteUInt32(EntityId);
            writer.WriteString(EntityType);
            writer.WriteDouble(PosX); writer.WriteDouble(PosY); writer.WriteDouble(PosZ);
            writer.WriteFloat(Yaw); writer.WriteFloat(Pitch);
            writer.WriteFloat(VelocityX); writer.WriteFloat(VelocityY); writer.WriteFloat(VelocityZ);
            writer.WriteUInt8(Flags);
        }

        public static EntityUpdateMessage Deserialize(PacketReader reader)
        {
            return new EntityUpdateMessage
            {
                EntityId = reader.ReadUInt32(), EntityType = reader.ReadString(),
                PosX = reader.ReadDouble(), PosY = reader.ReadDouble(), PosZ = reader.ReadDouble(),
                Yaw = reader.ReadFloat(), Pitch = reader.ReadFloat(),
                VelocityX = reader.ReadFloat(), VelocityY = reader.ReadFloat(), VelocityZ = reader.ReadFloat(),
                Flags = reader.ReadUInt8()
            };
        }
    }

    public sealed class EntityRemoveMessage : IMessage
    {
        public MessageType MessageType => MessageType.EntityRemove;
        public uint EntityId { get; set; }

        public void Serialize(PacketWriter writer) { writer.WriteUInt32(EntityId); }
        public static EntityRemoveMessage Deserialize(PacketReader reader)
        {
            return new EntityRemoveMessage { EntityId = reader.ReadUInt32() };
        }
    }

    public sealed class EntityInteractionMessage : IMessage
    {
        public MessageType MessageType => MessageType.EntityInteraction;
        public uint EntityId { get; set; }
        public byte InteractionType { get; set; }
        public byte Hand { get; set; }
        public double HitX { get; set; }
        public double HitY { get; set; }
        public double HitZ { get; set; }

        public void Serialize(PacketWriter writer)
        {
            writer.WriteUInt32(EntityId); writer.WriteUInt8(InteractionType); writer.WriteUInt8(Hand);
            writer.WriteDouble(HitX); writer.WriteDouble(HitY); writer.WriteDouble(HitZ);
        }

        public static EntityInteractionMessage Deserialize(PacketReader reader)
        {
            return new EntityInteractionMessage
            {
                EntityId = reader.ReadUInt32(), InteractionType = reader.ReadUInt8(), Hand = reader.ReadUInt8(),
                HitX = reader.ReadDouble(), HitY = reader.ReadDouble(), HitZ = reader.ReadDouble()
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
