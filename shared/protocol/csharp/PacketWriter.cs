using System;
using System.IO;
using System.Text;

namespace MinecraftBridge.Protocol
{
    public class PacketWriter : IDisposable
    {
        private readonly MemoryStream _stream;
        private readonly BinaryWriter _writer;

        public PacketWriter(int initialCapacity = 256)
        {
            _stream = new MemoryStream(initialCapacity);
            _writer = new BinaryWriter(_stream, Encoding.UTF8);
        }

        public int Length => (int)_stream.Length;

        public void WriteUInt8(byte value) => _writer.Write(value);
        public void WriteInt8(sbyte value) => _writer.Write(value);

        public void WriteUInt16(ushort value) => _writer.Write(value);
        public void WriteInt16(short value) => _writer.Write(value);

        public void WriteUInt32(uint value) => _writer.Write(value);
        public void WriteInt32(int value) => _writer.Write(value);

        public void WriteUInt64(ulong value) => _writer.Write(value);
        public void WriteInt64(long value) => _writer.Write(value);

        public void WriteFloat(float value) => _writer.Write(value);
        public void WriteDouble(double value) => _writer.Write(value);

        public void WriteBoolean(bool value) => _writer.Write((byte)(value ? 1 : 0));

        public void WriteString(string value)
        {
            if (string.IsNullOrEmpty(value))
            {
                WriteUInt16(0);
                return;
            }

            byte[] bytes = Encoding.UTF8.GetBytes(value);
            if (bytes.Length > ProtocolConstants.MaxStringLength)
            {
                throw new ArgumentException($"String length ({bytes.Length} bytes) exceeds maximum allowable length ({ProtocolConstants.MaxStringLength} bytes)");
            }

            WriteUInt16((ushort)bytes.Length);
            _writer.Write(bytes);
        }

        public void WriteBytes(byte[] buffer)
        {
            if (buffer != null && buffer.Length > 0)
            {
                _writer.Write(buffer);
            }
        }

        public byte[] ToPayloadArray()
        {
            _writer.Flush();
            return _stream.ToArray();
        }

        /// <summary>
        /// Wraps the payload into a full packet with the provided header fields.
        /// </summary>
        public byte[] BuildPacket(MessageType type, uint sequenceId)
        {
            _writer.Flush();
            byte[] payload = _stream.ToArray();
            MessageHeader header = new MessageHeader(type, sequenceId, (uint)payload.Length);

            byte[] packet = new byte[ProtocolConstants.HeaderSize + payload.Length];
            header.WriteTo(packet, 0);
            Buffer.BlockCopy(payload, 0, packet, ProtocolConstants.HeaderSize, payload.Length);
            return packet;
        }

        public void Dispose()
        {
            _writer.Dispose();
            _stream.Dispose();
        }
    }
}
