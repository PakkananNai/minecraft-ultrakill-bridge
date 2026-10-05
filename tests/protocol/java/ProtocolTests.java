package tests.protocol.java;

import com.bridge.minecraft.protocol.*;

public class ProtocolTests {
    private static int assertions = 0;
    private static int failures = 0;

    private static void assertTrue(boolean condition, String message) {
        assertions++;
        if (!condition) {
            failures++;
            System.err.println("[FAIL] " + message);
        } else {
            System.out.println("[PASS] " + message);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Running Java Protocol Unit Tests ===");

        testHeaderRoundTrip();
        testHeaderValidation();
        testHelloMessageRoundTrip();
        testHelloAckMessageRoundTrip();
        testPingPongRoundTrip();
        testFrameMetadataRoundTrip();
        testCameraStateRoundTrip();
        testInputEventRoundTrip();
        testInputFocusRoundTrip();
        testRaycastRoundTrip();
        testErrorAndShutdownRoundTrip();
        testTruncatedThrows();
        testStringBoundaryChecks();

        System.out.println("========================================");
        System.out.printf("Total assertions: %d, Failures: %d%n", assertions, failures);

        if (failures > 0) {
            System.err.println("JAVA PROTOCOL UNIT TESTS FAILED!");
            System.exit(1);
        }

        System.out.println("ALL JAVA PROTOCOL UNIT TESTS PASSED!");
        System.exit(0);
    }

    private static void testHeaderRoundTrip() {
        MessageHeader header = new MessageHeader(MessageType.CAMERA_STATE, 42, 108);
        byte[] bytes = header.toBytes();
        assertTrue(bytes.length == ProtocolConstants.HEADER_SIZE, "Header size is 16 bytes");

        try {
            MessageHeader parsed = MessageHeader.parse(bytes, 0, true);
            assertTrue(parsed.getMagic() == ProtocolConstants.MAGIC, "Magic matches 0x4255434D");
            assertTrue(parsed.getVersion() == 1, "Version is 1");
            assertTrue(parsed.getType() == MessageType.CAMERA_STATE, "MessageType is CAMERA_STATE");
            assertTrue(parsed.getSequenceId() == 42, "SequenceId is 42");
            assertTrue(parsed.getPayloadLength() == 108, "PayloadLength is 108");
        } catch (ProtocolException e) {
            assertTrue(false, "Header parsing threw unexpected exception: " + e.getMessage());
        }
    }

    private static void testHeaderValidation() {
        // Bad magic
        byte[] badMagic = new MessageHeader(MessageType.PING, 1, 0).toBytes();
        badMagic[0] = 0x00;
        boolean caughtBadMagic = false;
        try {
            MessageHeader.parse(badMagic, 0, true);
        } catch (ProtocolException.ValidationException e) {
            caughtBadMagic = true;
        } catch (Exception e) {
            // unexpected
        }
        assertTrue(caughtBadMagic, "Corrupt magic throws ValidationException");

        // Version mismatch
        MessageHeader badVerHeader = new MessageHeader(ProtocolConstants.MAGIC, (short) 99, MessageType.PING, 1, 0);
        byte[] badVerBytes = badVerHeader.toBytes();
        boolean caughtBadVer = false;
        try {
            MessageHeader.parse(badVerBytes, 0, true);
        } catch (ProtocolException.VersionMismatchException e) {
            caughtBadVer = (e.getExpected() == 1 && e.getReceived() == 99);
        } catch (Exception e) {
            // unexpected
        }
        assertTrue(caughtBadVer, "Version mismatch throws VersionMismatchException");

        // Excessive length
        MessageHeader hugeLenHeader = new MessageHeader(ProtocolConstants.MAGIC, (short) 1, MessageType.PING, 1, 70000);
        byte[] hugeLenBytes = hugeLenHeader.toBytes();
        boolean caughtHugeLen = false;
        try {
            MessageHeader.parse(hugeLenBytes, 0, true);
        } catch (ProtocolException.ValidationException e) {
            caughtHugeLen = true;
        } catch (Exception e) {
            // unexpected
        }
        assertTrue(caughtHugeLen, "PayloadLength > 64KB throws ValidationException");
    }

    private static void testHelloMessageRoundTrip() {
        try {
            Messages.HelloMessage original = new Messages.HelloMessage(
                    ProtocolConstants.CURRENT_VERSION, "MinecraftFabricMod", "1.21.1", 0x0F
            );
            PacketWriter writer = new PacketWriter();
            original.serialize(writer);

            PacketReader reader = new PacketReader(writer.toPayloadArray());
            Messages.HelloMessage deserialized = Messages.HelloMessage.deserialize(reader);

            assertTrue(deserialized.getProtocolVersion() == original.getProtocolVersion(), "Hello ProtocolVersion matches");
            assertTrue(deserialized.getClientName().equals(original.getClientName()), "Hello ClientName matches");
            assertTrue(deserialized.getClientVersion().equals(original.getClientVersion()), "Hello ClientVersion matches");
            assertTrue(deserialized.getCapabilities() == original.getCapabilities(), "Hello Capabilities match");
            assertTrue(reader.getRemaining() == 0, "Hello reader has 0 remaining bytes");
        } catch (Exception e) {
            assertTrue(false, "HelloMessage threw unexpected exception: " + e.getMessage());
        }
    }

    private static void testHelloAckMessageRoundTrip() {
        try {
            Messages.HelloAckMessage original = new Messages.HelloAckMessage(0, (short) 1, 0xDEADBEEFL, "Success");
            PacketWriter writer = new PacketWriter();
            original.serialize(writer);

            PacketReader reader = new PacketReader(writer.toPayloadArray());
            Messages.HelloAckMessage deserialized = Messages.HelloAckMessage.deserialize(reader);

            assertTrue(deserialized.getStatus() == 0, "HelloAck Status is 0");
            assertTrue(deserialized.getAcceptedVersion() == 1, "HelloAck AcceptedVersion is 1");
            assertTrue(deserialized.getSessionId() == 0xDEADBEEFL, "HelloAck SessionId matches");
            assertTrue(deserialized.getErrorMessage().equals("Success"), "HelloAck ErrorMessage matches");
        } catch (Exception e) {
            assertTrue(false, "HelloAckMessage threw unexpected exception: " + e.getMessage());
        }
    }

    private static void testPingPongRoundTrip() {
        try {
            long now = 1234567890123456789L;
            Messages.PingMessage ping = new Messages.PingMessage(now);
            PacketWriter writer = new PacketWriter();
            ping.serialize(writer);

            PacketReader reader = new PacketReader(writer.toPayloadArray());
            Messages.PingMessage deserPing = Messages.PingMessage.deserialize(reader);
            assertTrue(deserPing.getTimestampNs() == now, "Ping timestamp matches");

            Messages.PongMessage pong = new Messages.PongMessage(now);
            PacketWriter pWriter = new PacketWriter();
            pong.serialize(pWriter);

            PacketReader pReader = new PacketReader(pWriter.toPayloadArray());
            Messages.PongMessage deserPong = Messages.PongMessage.deserialize(pReader);
            assertTrue(deserPong.getTimestampNs() == now, "Pong timestamp matches");
        } catch (Exception e) {
            assertTrue(false, "Ping/Pong threw unexpected exception: " + e.getMessage());
        }
    }

    private static void testFrameMetadataRoundTrip() {
        try {
            Messages.FrameMetadataMessage original = new Messages.FrameMetadataMessage(
                    2, 1920, 1080, 1920 * 4, 1, 9876543210L, 1122334455667788L
            );

            PacketWriter writer = new PacketWriter();
            original.serialize(writer);
            byte[] packet = writer.buildPacket(original.getMessageType(), 100);

            MessageHeader header = MessageHeader.parse(packet, 0, true);
            assertTrue(header.getType() == MessageType.FRAME_METADATA, "Packet header type is FRAME_METADATA");
            assertTrue(header.getSequenceId() == 100, "Packet header SeqId is 100");

            PacketReader reader = new PacketReader(packet, ProtocolConstants.HEADER_SIZE, header.getPayloadLength());
            Messages.FrameMetadataMessage deser = Messages.FrameMetadataMessage.deserialize(reader);
            assertTrue(deser.getBufferId() == 2, "BufferId matches");
            assertTrue(deser.getWidth() == 1920, "Width matches 1920");
            assertTrue(deser.getHeight() == 1080, "Height matches 1080");
            assertTrue(deser.getStride() == 7680, "Stride matches 7680");
            assertTrue(deser.getFormat() == 1, "Format matches RGBA8");
            assertTrue(deser.getSequenceNumber() == 9876543210L, "SequenceNumber matches");
            assertTrue(deser.getTimestampNs() == 1122334455667788L, "TimestampNs matches");
        } catch (Exception e) {
            assertTrue(false, "FrameMetadata threw unexpected exception: " + e.getMessage());
        }
    }

    private static void testCameraStateRoundTrip() {
        try {
            Messages.CameraStateMessage original = new Messages.CameraStateMessage(
                    100.5, 64.0, -250.75, 90.0f, -15.5f, 0.0f, 70.0f, 500L
            );

            PacketWriter writer = new PacketWriter();
            original.serialize(writer);

            PacketReader reader = new PacketReader(writer.toPayloadArray());
            Messages.CameraStateMessage deser = Messages.CameraStateMessage.deserialize(reader);

            assertTrue(Math.abs(deser.getPosX() - 100.5) < 0.0001, "Camera PosX matches");
            assertTrue(Math.abs(deser.getPosY() - 64.0) < 0.0001, "Camera PosY matches");
            assertTrue(Math.abs(deser.getPosZ() - (-250.75)) < 0.0001, "Camera PosZ matches");
            assertTrue(Math.abs(deser.getYaw() - 90.0f) < 0.0001f, "Camera Yaw matches");
            assertTrue(Math.abs(deser.getPitch() - (-15.5f)) < 0.0001f, "Camera Pitch matches");
            assertTrue(Math.abs(deser.getRoll() - 0.0f) < 0.0001f, "Camera Roll matches");
            assertTrue(Math.abs(deser.getFov() - 70.0f) < 0.0001f, "Camera Fov matches");
            assertTrue(deser.getSequenceNumber() == 500L, "Camera SequenceNumber matches");
        } catch (Exception e) {
            assertTrue(false, "CameraState threw unexpected exception: " + e.getMessage());
        }
    }

    private static void testInputEventRoundTrip() {
        try {
            Messages.InputEventMessage original = new Messages.InputEventMessage(
                    3, 0, -12, 8, 1, 0
            );

            PacketWriter writer = new PacketWriter();
            original.serialize(writer);

            PacketReader reader = new PacketReader(writer.toPayloadArray());
            Messages.InputEventMessage deser = Messages.InputEventMessage.deserialize(reader);

            assertTrue(deser.getEventType() == 3, "Input EventType matches");
            assertTrue(deser.getMouseDx() == -12, "MouseDx is -12");
            assertTrue(deser.getMouseDy() == 8, "MouseDy is 8");
            assertTrue(deser.getMouseButtons() == 1, "MouseButtons is 1");
        } catch (Exception e) {
            assertTrue(false, "InputEvent threw unexpected exception: " + e.getMessage());
        }
    }

    private static void testInputFocusRoundTrip() {
        try {
            Messages.InputFocusMessage original = new Messages.InputFocusMessage(true, true);
            PacketWriter writer = new PacketWriter();
            original.serialize(writer);

            PacketReader reader = new PacketReader(writer.toPayloadArray());
            Messages.InputFocusMessage deser = Messages.InputFocusMessage.deserialize(reader);

            assertTrue(deser.isHasFocus(), "InputFocus HasFocus is true");
            assertTrue(deser.isReleaseHeldKeys(), "InputFocus ReleaseHeldKeys is true");
        } catch (Exception e) {
            assertTrue(false, "InputFocus threw unexpected exception: " + e.getMessage());
        }
    }

    private static void testRaycastRoundTrip() {
        try {
            Messages.RaycastRequestMessage request = new Messages.RaycastRequestMessage(77L, 6.0f);
            PacketWriter writer = new PacketWriter(); request.serialize(writer);
            Messages.RaycastRequestMessage decoded = Messages.RaycastRequestMessage.deserialize(new PacketReader(writer.toPayloadArray()));
            assertTrue(decoded.getRequestId() == 77L, "Raycast request id matches");
            assertTrue(Math.abs(decoded.getMaxDistance() - 6.0f) < 0.0001f, "Raycast max distance matches");

            Messages.RaycastResponseMessage response = new Messages.RaycastResponseMessage(77L, true, 12, 73, 53, 1,
                    12.25, 73.5, 53.0, 0.75f, "minecraft:stone");
            PacketWriter responseWriter = new PacketWriter(); response.serialize(responseWriter);
            Messages.RaycastResponseMessage decodedResponse = Messages.RaycastResponseMessage.deserialize(new PacketReader(responseWriter.toPayloadArray()));
            assertTrue(decodedResponse.getRequestId() == 77L, "Raycast response id matches");
            assertTrue(decodedResponse.isHit(), "Raycast hit flag matches");
            assertTrue(decodedResponse.getBlockX() == 12 && decodedResponse.getBlockY() == 73 && decodedResponse.getBlockZ() == 53, "Raycast block position matches");
            assertTrue(decodedResponse.getSide() == 1, "Raycast side matches");
            assertTrue(Math.abs(decodedResponse.getHitX() - 12.25) < 0.0001 && Math.abs(decodedResponse.getDistance() - 0.75f) < 0.0001f, "Raycast hit geometry matches");
            assertTrue(decodedResponse.getBlockId().equals("minecraft:stone"), "Raycast block id matches");
        } catch (Exception e) { assertTrue(false, "Raycast messages threw unexpected exception: " + e.getMessage()); }
    }

    private static void testErrorAndShutdownRoundTrip() {
        try {
            Messages.ErrorMessage err = new Messages.ErrorMessage(404, "Resource not found");
            PacketWriter writer = new PacketWriter();
            err.serialize(writer);

            PacketReader reader = new PacketReader(writer.toPayloadArray());
            Messages.ErrorMessage deserErr = Messages.ErrorMessage.deserialize(reader);
            assertTrue(deserErr.getErrorCode() == 404, "ErrorCode is 404");
            assertTrue(deserErr.getDescription().equals("Resource not found"), "Error description matches");

            Messages.ShutdownMessage shut = new Messages.ShutdownMessage(0, "User exited cleanly");
            PacketWriter sWriter = new PacketWriter();
            shut.serialize(sWriter);

            PacketReader sReader = new PacketReader(sWriter.toPayloadArray());
            Messages.ShutdownMessage deserShut = Messages.ShutdownMessage.deserialize(sReader);
            assertTrue(deserShut.getReasonCode() == 0, "Shutdown ReasonCode is 0");
            assertTrue(deserShut.getReasonText().equals("User exited cleanly"), "Shutdown ReasonText matches");
        } catch (Exception e) {
            assertTrue(false, "Error/Shutdown threw unexpected exception: " + e.getMessage());
        }
    }

    private static void testTruncatedThrows() {
        byte[] truncated = new byte[4];
        boolean caught = false;
        try {
            PacketReader reader = new PacketReader(truncated);
            Messages.CameraStateMessage.deserialize(reader);
        } catch (ProtocolException.TruncatedException e) {
            caught = true;
        } catch (Exception e) {
            // unexpected
        }
        assertTrue(caught, "Deserializing truncated payload throws TruncatedException");
    }

    private static void testStringBoundaryChecks() {
        try {
            // Empty string
            PacketWriter writer = new PacketWriter();
            writer.writeString("");
            PacketReader reader = new PacketReader(writer.toPayloadArray());
            assertTrue(reader.readString().isEmpty(), "Empty string roundtrips as empty string");

            // Multi-byte Unicode
            String unicode = "Minecraft × ULTRAKILL ブリッジ 🕹️";
            PacketWriter uWriter = new PacketWriter();
            uWriter.writeString(unicode);
            PacketReader uReader = new PacketReader(uWriter.toPayloadArray());
            assertTrue(uReader.readString().equals(unicode), "UTF-8 multi-byte string roundtrips perfectly");

            // Huge string throws
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i <= ProtocolConstants.MAX_STRING_LENGTH; i++) {
                sb.append('A');
            }
            boolean caughtTooLong = false;
            try {
                PacketWriter hWriter = new PacketWriter();
                hWriter.writeString(sb.toString());
            } catch (IllegalArgumentException e) {
                caughtTooLong = true;
            }
            assertTrue(caughtTooLong, "Writing string > 1024 bytes throws IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(false, "String boundary checks threw unexpected exception: " + e.getMessage());
        }
    }
}
