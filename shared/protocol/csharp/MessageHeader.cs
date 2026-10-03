using System;

namespace MinecraftBridge.Protocol
{
    public struct MessageHeader
    {
        public uint Magic;
        public ushort Version;
        public MessageType Type;
        public uint SequenceId;
        public uint PayloadLength;

        public MessageHeader(MessageType type, uint sequenceId, uint payloadLength, ushort version = ProtocolConstants.CurrentVersion)
        {
            Magic = ProtocolConstants.Magic;
            Version = version;
            Type = type;
            SequenceId = sequenceId;
            PayloadLength = payloadLength;
        }

        public void Validate()
        {
            if (Magic != ProtocolConstants.Magic)
            {
                throw new ProtocolValidationException($"Invalid packet magic: 0x{Magic:X8}, expected 0x{ProtocolConstants.Magic:X8}");
            }

            if (Version != ProtocolConstants.CurrentVersion)
            {
                throw new ProtocolVersionMismatchException(ProtocolConstants.CurrentVersion, Version);
            }

            if (PayloadLength > ProtocolConstants.MaxPayloadLength)
            {
                throw new ProtocolValidationException($"Payload length {PayloadLength} exceeds maximum limit of {ProtocolConstants.MaxPayloadLength} bytes");
            }
        }

        public byte[] ToBytes()
        {
            byte[] bytes = new byte[ProtocolConstants.HeaderSize];
            WriteTo(bytes, 0);
            return bytes;
        }

        public void WriteTo(byte[] buffer, int offset)
        {
            if (buffer == null || buffer.Length < offset + ProtocolConstants.HeaderSize)
            {
                throw new ArgumentException("Buffer is too small to write header");
            }

            // Little-Endian write
            buffer[offset + 0] = (byte)(Magic & 0xFF);
            buffer[offset + 1] = (byte)((Magic >> 8) & 0xFF);
            buffer[offset + 2] = (byte)((Magic >> 16) & 0xFF);
            buffer[offset + 3] = (byte)((Magic >> 24) & 0xFF);

            buffer[offset + 4] = (byte)(Version & 0xFF);
            buffer[offset + 5] = (byte)((Version >> 8) & 0xFF);

            ushort typeVal = (ushort)Type;
            buffer[offset + 6] = (byte)(typeVal & 0xFF);
            buffer[offset + 7] = (byte)((typeVal >> 8) & 0xFF);

            buffer[offset + 8] = (byte)(SequenceId & 0xFF);
            buffer[offset + 9] = (byte)((SequenceId >> 8) & 0xFF);
            buffer[offset + 10] = (byte)((SequenceId >> 16) & 0xFF);
            buffer[offset + 11] = (byte)((SequenceId >> 24) & 0xFF);

            buffer[offset + 12] = (byte)(PayloadLength & 0xFF);
            buffer[offset + 13] = (byte)((PayloadLength >> 8) & 0xFF);
            buffer[offset + 14] = (byte)((PayloadLength >> 16) & 0xFF);
            buffer[offset + 15] = (byte)((PayloadLength >> 24) & 0xFF);
        }

        public static MessageHeader Parse(byte[] buffer, int offset = 0, bool validate = true)
        {
            if (buffer == null || buffer.Length < offset + ProtocolConstants.HeaderSize)
            {
                throw new ProtocolTruncatedException("Buffer too small to contain complete header");
            }

            uint magic = (uint)(buffer[offset + 0] |
                               (buffer[offset + 1] << 8) |
                               (buffer[offset + 2] << 16) |
                               (buffer[offset + 3] << 24));

            ushort version = (ushort)(buffer[offset + 4] | (buffer[offset + 5] << 8));
            ushort typeVal = (ushort)(buffer[offset + 6] | (buffer[offset + 7] << 8));

            uint sequenceId = (uint)(buffer[offset + 8] |
                                    (buffer[offset + 9] << 8) |
                                    (buffer[offset + 10] << 16) |
                                    (buffer[offset + 11] << 24));

            uint payloadLength = (uint)(buffer[offset + 12] |
                                       (buffer[offset + 13] << 8) |
                                       (buffer[offset + 14] << 16) |
                                       (buffer[offset + 15] << 24));

            MessageHeader header = new MessageHeader
            {
                Magic = magic,
                Version = version,
                Type = (MessageType)typeVal,
                SequenceId = sequenceId,
                PayloadLength = payloadLength
            };

            if (validate)
            {
                header.Validate();
            }

            return header;
        }

        public override string ToString()
        {
            return $"Header[Magic=0x{Magic:X8}, Ver={Version}, Type={Type}, Seq={SequenceId}, Len={PayloadLength}]";
        }
    }
}
