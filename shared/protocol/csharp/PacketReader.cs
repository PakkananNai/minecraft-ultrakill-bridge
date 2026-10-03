using System;
using System.IO;
using System.Text;

namespace MinecraftBridge.Protocol
{
    public class PacketReader : IDisposable
    {
        private readonly MemoryStream _stream;
        private readonly BinaryReader _reader;

        public PacketReader(byte[] buffer, int offset, int count)
        {
            if (buffer == null) throw new ArgumentNullException(nameof(buffer));
            if (offset < 0 || count < 0 || offset + count > buffer.Length)
            {
                throw new ArgumentOutOfRangeException("Offset and count exceed buffer boundaries");
            }

            _stream = new MemoryStream(buffer, offset, count, false);
            _reader = new BinaryReader(_stream, Encoding.UTF8);
        }

        public PacketReader(byte[] buffer) : this(buffer, 0, buffer?.Length ?? 0) { }

        public int Remaining => (int)(_stream.Length - _stream.Position);

        private void CheckRemaining(int requiredBytes)
        {
            if (Remaining < requiredBytes)
            {
                throw new ProtocolTruncatedException($"Expected at least {requiredBytes} bytes, but only {Remaining} bytes remain in packet");
            }
        }

        public byte ReadUInt8()
        {
            CheckRemaining(1);
            return _reader.ReadByte();
        }

        public sbyte ReadInt8()
        {
            CheckRemaining(1);
            return _reader.ReadSByte();
        }

        public ushort ReadUInt16()
        {
            CheckRemaining(2);
            return _reader.ReadUInt16();
        }

        public short ReadInt16()
        {
            CheckRemaining(2);
            return _reader.ReadInt16();
        }

        public uint ReadUInt32()
        {
            CheckRemaining(4);
            return _reader.ReadUInt32();
        }

        public int ReadInt32()
        {
            CheckRemaining(4);
            return _reader.ReadInt32();
        }

        public ulong ReadUInt64()
        {
            CheckRemaining(8);
            return _reader.ReadUInt64();
        }

        public long ReadInt64()
        {
            CheckRemaining(8);
            return _reader.ReadInt64();
        }

        public float ReadFloat()
        {
            CheckRemaining(4);
            return _reader.ReadSingle();
        }

        public double ReadDouble()
        {
            CheckRemaining(8);
            return _reader.ReadDouble();
        }

        public bool ReadBoolean()
        {
            CheckRemaining(1);
            return _reader.ReadByte() != 0;
        }

        public string ReadString()
        {
            ushort length = ReadUInt16();
            if (length == 0) return string.Empty;

            if (length > ProtocolConstants.MaxStringLength)
            {
                throw new ProtocolValidationException($"Encountered string length {length} exceeding maximum {ProtocolConstants.MaxStringLength}");
            }

            CheckRemaining(length);
            byte[] bytes = _reader.ReadBytes(length);
            return Encoding.UTF8.GetString(bytes);
        }

        public byte[] ReadBytes(int count)
        {
            if (count == 0) return new byte[0];
            CheckRemaining(count);
            return _reader.ReadBytes(count);
        }

        public void Dispose()
        {
            _reader.Dispose();
            _stream.Dispose();
        }
    }
}
