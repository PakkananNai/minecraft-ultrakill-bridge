package com.bridge.minecraft.protocol;

public final class ProtocolConstants {
    private ProtocolConstants() {}

    /**
     * Magic header constant: ASCII "MCUB" (0x4255434D in Little-Endian).
     */
    public static final int MAGIC = 0x4255434D;

    /**
     * Current protocol version.
     */
    public static final short CURRENT_VERSION = 1;

    /**
     * Header size in bytes.
     */
    public static final int HEADER_SIZE = 16;

    /**
     * Maximum allowable payload size (64 KB).
     */
    public static final int MAX_PAYLOAD_LENGTH = 64 * 1024;

    /**
     * Maximum length of UTF-8 strings in bytes.
     */
    public static final int MAX_STRING_LENGTH = 1024;
}
