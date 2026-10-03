namespace MinecraftBridge.Protocol
{
    public static class ProtocolConstants
    {
        /// <summary>
        /// Magic header identifier: ASCII "MCUB" (0x4255434D in Little-Endian).
        /// </summary>
        public const uint Magic = 0x4255434D;

        /// <summary>
        /// Current protocol specification version.
        /// </summary>
        public const ushort CurrentVersion = 1;

        /// <summary>
        /// Fixed length of a packet header in bytes.
        /// </summary>
        public const int HeaderSize = 16;

        /// <summary>
        /// Maximum allowable payload size (64 KB) to protect against memory exhaustion.
        /// </summary>
        public const uint MaxPayloadLength = 64 * 1024;

        /// <summary>
        /// Maximum length of UTF-8 strings in bytes.
        /// </summary>
        public const ushort MaxStringLength = 1024;
    }
}
