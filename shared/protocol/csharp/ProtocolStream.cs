using System;
using System.IO;

namespace MinecraftBridge.Protocol
{
    /// <summary>A canonical MCUB frame as carried on a byte stream.</summary>
    public sealed class ProtocolFrame
    {
        public MessageHeader Header { get; private set; }
        public byte[] Payload { get; private set; }

        internal ProtocolFrame(MessageHeader header, byte[] payload)
        {
            Header = header;
            Payload = payload;
        }
    }

    /// <summary>Reads and writes the canonical 16-byte MCUB framing.</summary>
    public static class ProtocolStream
    {
        public static ProtocolFrame ReadFrame(Stream stream)
        {
            if (stream == null) throw new ArgumentNullException(nameof(stream));
            byte[] headerBytes = new byte[ProtocolConstants.HeaderSize];
            ReadExactly(stream, headerBytes, 0, headerBytes.Length);
            MessageHeader header = MessageHeader.Parse(headerBytes);

            if (header.PayloadLength > ProtocolConstants.MaxPayloadLength)
                throw new ProtocolValidationException("Payload exceeds the canonical maximum");

            byte[] payload = new byte[(int)header.PayloadLength];
            if (payload.Length != 0) ReadExactly(stream, payload, 0, payload.Length);
            return new ProtocolFrame(header, payload);
        }

        public static void WriteFrame(Stream stream, MessageType type, uint sequenceId, byte[] payload)
        {
            if (stream == null) throw new ArgumentNullException(nameof(stream));
            if (payload == null) payload = new byte[0];
            if ((uint)payload.Length > ProtocolConstants.MaxPayloadLength)
                throw new ProtocolValidationException("Payload exceeds the canonical maximum");

            MessageHeader header = new MessageHeader(type, sequenceId, (uint)payload.Length);
            byte[] headerBytes = header.ToBytes();
            stream.Write(headerBytes, 0, headerBytes.Length);
            if (payload.Length != 0) stream.Write(payload, 0, payload.Length);
            stream.Flush();
        }

        public static void ReadExactly(Stream stream, byte[] buffer, int offset, int count)
        {
            int total = 0;
            while (total < count)
            {
                int read = stream.Read(buffer, offset + total, count - total);
                if (read <= 0)
                    throw new ProtocolTruncatedException("TCP stream closed before the frame was complete");
                total += read;
            }
        }
    }
}
