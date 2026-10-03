package com.bridge.minecraft.protocol;

public class ProtocolException extends Exception {
    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }

    public static class ValidationException extends ProtocolException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class VersionMismatchException extends ProtocolException {
        private final short expected;
        private final short received;

        public VersionMismatchException(short expected, short received) {
            super("Protocol version mismatch: expected " + expected + ", received " + received);
            this.expected = expected;
            this.received = received;
        }

        public short getExpected() { return expected; }
        public short getReceived() { return received; }
    }

    public static class TruncatedException extends ProtocolException {
        public TruncatedException(String message) {
            super(message);
        }
    }
}
