using System;

namespace MinecraftBridge.Protocol
{
    public class ProtocolException : Exception
    {
        public ProtocolException(string message) : base(message) { }
        public ProtocolException(string message, Exception innerException) : base(message, innerException) { }
    }

    public class ProtocolValidationException : ProtocolException
    {
        public ProtocolValidationException(string message) : base(message) { }
    }

    public class ProtocolVersionMismatchException : ProtocolException
    {
        public ushort ExpectedVersion { get; }
        public ushort ReceivedVersion { get; }

        public ProtocolVersionMismatchException(ushort expected, ushort received)
            : base($"Protocol version mismatch: expected {expected}, received {received}")
        {
            ExpectedVersion = expected;
            ReceivedVersion = received;
        }
    }

    public class ProtocolTruncatedException : ProtocolException
    {
        public ProtocolTruncatedException(string message) : base(message) { }
    }
}
