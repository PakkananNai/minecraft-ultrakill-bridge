using System;
using System.IO;
using MinecraftBridge.Protocol;

namespace MinecraftBridge.Tests
{
    public class ProtocolTests
    {
        private static int _assertions = 0;
        private static int _failures = 0;

        private static void Assert(bool condition, string message)
        {
            _assertions++;
            if (!condition)
            {
                _failures++;
                Console.ForegroundColor = ConsoleColor.Red;
                Console.WriteLine($"[FAIL] {message}");
                Console.ResetColor();
            }
            else
            {
                Console.WriteLine($"[PASS] {message}");
            }
        }

        public static int Main(string[] args)
        {
            Console.WriteLine("=== Running C# Protocol Unit Tests ===");

            TestHeaderRoundTrip();
            TestHeaderValidation();
            TestHelloMessageRoundTrip();
            TestHelloAckMessageRoundTrip();
            TestPingPongMessageRoundTrip();
            TestFrameMetadataRoundTrip();
            TestCameraStateRoundTrip();
            TestInputEventRoundTrip();
            TestInputFocusRoundTrip();
            TestRaycastRoundTrip();
            TestErrorAndShutdownRoundTrip();
            TestTruncatedPacketThrows();
            TestStringBoundaryChecks();

            Console.WriteLine("=======================================");
            Console.WriteLine($"Total assertions: {_assertions}, Failures: {_failures}");

            if (_failures > 0)
            {
                Console.ForegroundColor = ConsoleColor.Red;
                Console.WriteLine("C# PROTOCOL UNIT TESTS FAILED!");
                Console.ResetColor();
                return 1;
            }

            Console.ForegroundColor = ConsoleColor.Green;
            Console.WriteLine("ALL C# PROTOCOL UNIT TESTS PASSED!");
            Console.ResetColor();
            return 0;
        }

        private static void TestHeaderRoundTrip()
        {
            MessageHeader header = new MessageHeader(MessageType.CameraState, 42, 108);
            byte[] bytes = header.ToBytes();
            Assert(bytes.Length == ProtocolConstants.HeaderSize, "Header size is 16 bytes");

            MessageHeader parsed = MessageHeader.Parse(bytes, 0, true);
            Assert(parsed.Magic == ProtocolConstants.Magic, "Magic matches 0x4255434D");
            Assert(parsed.Version == 1, "Version is 1");
            Assert(parsed.Type == MessageType.CameraState, "MessageType is CameraState");
            Assert(parsed.SequenceId == 42, "SequenceId is 42");
            Assert(parsed.PayloadLength == 108, "PayloadLength is 108");
        }

        private static void TestHeaderValidation()
        {
            // Invalid Magic
            byte[] badMagic = new MessageHeader(MessageType.Ping, 1, 0).ToBytes();
            badMagic[0] = 0x00; // corrupt magic
            bool caughtBadMagic = false;
            try { MessageHeader.Parse(badMagic, 0, true); }
            catch (ProtocolValidationException) { caughtBadMagic = true; }
            Assert(caughtBadMagic, "Invalid magic throws ProtocolValidationException");

            // Version Mismatch
            MessageHeader badVerHeader = new MessageHeader(MessageType.Ping, 1, 0, version: 99);
            byte[] badVerBytes = badVerHeader.ToBytes();
            bool caughtBadVersion = false;
            try { MessageHeader.Parse(badVerBytes, 0, true); }
            catch (ProtocolVersionMismatchException ex)
            {
                caughtBadVersion = ex.ExpectedVersion == 1 && ex.ReceivedVersion == 99;
            }
            Assert(caughtBadVersion, "Version mismatch throws ProtocolVersionMismatchException with expected details");

            // Excessive Payload Length
            MessageHeader hugeLenHeader = new MessageHeader(MessageType.Ping, 1, 70000);
            byte[] hugeLenBytes = hugeLenHeader.ToBytes();
            bool caughtHugeLen = false;
            try { MessageHeader.Parse(hugeLenBytes, 0, true); }
            catch (ProtocolValidationException) { caughtHugeLen = true; }
            Assert(caughtHugeLen, "PayloadLength > 64KB throws ProtocolValidationException");

            // Truncated Header
            byte[] truncated = new byte[10];
            bool caughtTruncated = false;
            try { MessageHeader.Parse(truncated, 0, true); }
            catch (ProtocolTruncatedException) { caughtTruncated = true; }
            Assert(caughtTruncated, "Truncated buffer throws ProtocolTruncatedException");
        }

        private static void TestHelloMessageRoundTrip()
        {
            HelloMessage original = new HelloMessage
            {
                ProtocolVersion = 1,
                ClientName = "MinecraftFabricMod",
                ClientVersion = "1.21.1",
                Capabilities = 0x0F
            };

            using (PacketWriter writer = new PacketWriter())
            {
                original.Serialize(writer);
                byte[] payload = writer.ToPayloadArray();

                using (PacketReader reader = new PacketReader(payload))
                {
                    HelloMessage deserialized = HelloMessage.Deserialize(reader);
                    Assert(deserialized.ProtocolVersion == original.ProtocolVersion, "Hello ProtocolVersion matches");
                    Assert(deserialized.ClientName == original.ClientName, "Hello ClientName matches");
                    Assert(deserialized.ClientVersion == original.ClientVersion, "Hello ClientVersion matches");
                    Assert(deserialized.Capabilities == original.Capabilities, "Hello Capabilities match");
                    Assert(reader.Remaining == 0, "Hello reader has 0 remaining bytes");
                }
            }
        }

        private static void TestHelloAckMessageRoundTrip()
        {
            HelloAckMessage original = new HelloAckMessage
            {
                Status = 0,
                AcceptedVersion = 1,
                SessionId = 0xDEADBEEF,
                ErrorMessage = "Success"
            };

            using (PacketWriter writer = new PacketWriter())
            {
                original.Serialize(writer);
                using (PacketReader reader = new PacketReader(writer.ToPayloadArray()))
                {
                    HelloAckMessage deserialized = HelloAckMessage.Deserialize(reader);
                    Assert(deserialized.Status == 0, "HelloAck Status is 0");
                    Assert(deserialized.AcceptedVersion == 1, "HelloAck AcceptedVersion is 1");
                    Assert(deserialized.SessionId == 0xDEADBEEF, "HelloAck SessionId matches");
                    Assert(deserialized.ErrorMessage == "Success", "HelloAck ErrorMessage matches");
                }
            }
        }

        private static void TestPingPongMessageRoundTrip()
        {
            ulong now = 1234567890123456789UL;
            PingMessage ping = new PingMessage { TimestampNs = now };
            using (PacketWriter writer = new PacketWriter())
            {
                ping.Serialize(writer);
                using (PacketReader reader = new PacketReader(writer.ToPayloadArray()))
                {
                    PingMessage deserialized = PingMessage.Deserialize(reader);
                    Assert(deserialized.TimestampNs == now, "Ping timestamp matches");
                }
            }

            PongMessage pong = new PongMessage { TimestampNs = now };
            using (PacketWriter writer = new PacketWriter())
            {
                pong.Serialize(writer);
                using (PacketReader reader = new PacketReader(writer.ToPayloadArray()))
                {
                    PongMessage deserialized = PongMessage.Deserialize(reader);
                    Assert(deserialized.TimestampNs == now, "Pong timestamp matches");
                }
            }
        }

        private static void TestFrameMetadataRoundTrip()
        {
            FrameMetadataMessage original = new FrameMetadataMessage
            {
                BufferId = 2,
                Width = 1920,
                Height = 1080,
                Stride = 1920 * 4,
                Format = 1, // RGBA8
                SequenceNumber = 9876543210UL,
                TimestampNs = 1122334455667788UL
            };

            using (PacketWriter writer = new PacketWriter())
            {
                original.Serialize(writer);
                byte[] packet = writer.BuildPacket(original.MessageType, 100);
                // First 16 bytes is header
                // First 16 bytes is header
                MessageHeader header = MessageHeader.Parse(packet, 0, true);
                Assert(header.Type == MessageType.FrameMetadata, "Packet header type is FrameMetadata");
                Assert(header.SequenceId == 100, "Packet header SeqId is 100");

                // Payload
                using (PacketReader reader = new PacketReader(packet, ProtocolConstants.HeaderSize, (int)header.PayloadLength))
                {
                    FrameMetadataMessage deserialized = FrameMetadataMessage.Deserialize(reader);
                    Assert(deserialized.BufferId == 2, "BufferId matches");
                    Assert(deserialized.Width == 1920, "Width matches 1920");
                    Assert(deserialized.Height == 1080, "Height matches 1080");
                    Assert(deserialized.Stride == 1920 * 4, "Stride matches 7680");
                    Assert(deserialized.Format == 1, "Format matches RGBA8");
                    Assert(deserialized.SequenceNumber == 9876543210UL, "SequenceNumber matches");
                    Assert(deserialized.TimestampNs == 1122334455667788UL, "TimestampNs matches");
                }
            }
        }

        private static void TestCameraStateRoundTrip()
        {
            CameraStateMessage original = new CameraStateMessage
            {
                PosX = 100.5,
                PosY = 64.0,
                PosZ = -250.75,
                Yaw = 90.0f,
                Pitch = -15.5f,
                Roll = 0.0f,
                Fov = 70.0f,
                SequenceNumber = 500
            };

            using (PacketWriter writer = new PacketWriter())
            {
                original.Serialize(writer);
                using (PacketReader reader = new PacketReader(writer.ToPayloadArray()))
                {
                    CameraStateMessage deserialized = CameraStateMessage.Deserialize(reader);
                    Assert(Math.Abs(deserialized.PosX - 100.5) < 0.0001, "Camera PosX matches");
                    Assert(Math.Abs(deserialized.PosY - 64.0) < 0.0001, "Camera PosY matches");
                    Assert(Math.Abs(deserialized.PosZ - (-250.75)) < 0.0001, "Camera PosZ matches");
                    Assert(Math.Abs(deserialized.Yaw - 90.0f) < 0.0001f, "Camera Yaw matches");
                    Assert(Math.Abs(deserialized.Pitch - (-15.5f)) < 0.0001f, "Camera Pitch matches");
                    Assert(Math.Abs(deserialized.Roll - 0.0f) < 0.0001f, "Camera Roll matches");
                    Assert(Math.Abs(deserialized.Fov - 70.0f) < 0.0001f, "Camera Fov matches");
                    Assert(deserialized.SequenceNumber == 500, "Camera SequenceNumber matches");
                }
            }
        }

        private static void TestInputEventRoundTrip()
        {
            InputEventMessage original = new InputEventMessage
            {
                EventType = 3, // MouseMoveRel
                KeyCode = 0,
                MouseDx = -12,
                MouseDy = 8,
                MouseButtons = 1,
                WheelDelta = 0
            };

            using (PacketWriter writer = new PacketWriter())
            {
                original.Serialize(writer);
                using (PacketReader reader = new PacketReader(writer.ToPayloadArray()))
                {
                    InputEventMessage deserialized = InputEventMessage.Deserialize(reader);
                    Assert(deserialized.EventType == 3, "Input EventType matches");
                    Assert(deserialized.MouseDx == -12, "MouseDx is -12");
                    Assert(deserialized.MouseDy == 8, "MouseDy is 8");
                    Assert(deserialized.MouseButtons == 1, "MouseButtons is 1");
                }
            }
        }

        private static void TestInputFocusRoundTrip()
        {
            InputFocusMessage original = new InputFocusMessage
            {
                HasFocus = true,
                ReleaseHeldKeys = true
            };

            using (PacketWriter writer = new PacketWriter())
            {
                original.Serialize(writer);
                using (PacketReader reader = new PacketReader(writer.ToPayloadArray()))
                {
                    InputFocusMessage deserialized = InputFocusMessage.Deserialize(reader);
                    Assert(deserialized.HasFocus == true, "InputFocus HasFocus is true");
                    Assert(deserialized.ReleaseHeldKeys == true, "InputFocus ReleaseHeldKeys is true");
                }
            }
        }

        private static void TestRaycastRoundTrip()
        {
            RaycastRequestMessage request = new RaycastRequestMessage { RequestId = 77, MaxDistance = 6.0f };
            using (PacketWriter writer = new PacketWriter())
            {
                request.Serialize(writer);
                using (PacketReader reader = new PacketReader(writer.ToPayloadArray()))
                {
                    RaycastRequestMessage decoded = RaycastRequestMessage.Deserialize(reader);
                    Assert(decoded.RequestId == 77, "Raycast request id matches");
                    Assert(Math.Abs(decoded.MaxDistance - 6.0f) < 0.0001f, "Raycast max distance matches");
                }
            }

            RaycastResponseMessage response = new RaycastResponseMessage { RequestId = 77, Hit = true, BlockX = 12, BlockY = 73, BlockZ = 53,
                Side = 1, HitX = 12.25, HitY = 73.5, HitZ = 53.0, Distance = 0.75f, BlockId = "minecraft:stone" };
            using (PacketWriter writer = new PacketWriter())
            {
                response.Serialize(writer);
                using (PacketReader reader = new PacketReader(writer.ToPayloadArray()))
                {
                    RaycastResponseMessage decoded = RaycastResponseMessage.Deserialize(reader);
                    Assert(decoded.RequestId == 77, "Raycast response id matches");
                    Assert(decoded.Hit, "Raycast hit flag matches");
                    Assert(decoded.BlockX == 12 && decoded.BlockY == 73 && decoded.BlockZ == 53, "Raycast block position matches");
                    Assert(decoded.Side == 1, "Raycast side matches");
                    Assert(Math.Abs(decoded.HitX - 12.25) < 0.0001 && Math.Abs(decoded.Distance - 0.75f) < 0.0001f, "Raycast hit geometry matches");
                    Assert(decoded.BlockId == "minecraft:stone", "Raycast block id matches");
                }
            }
        }

        private static void TestErrorAndShutdownRoundTrip()
        {
            ErrorMessage err = new ErrorMessage { ErrorCode = 404, Description = "Resource not found" };
            using (PacketWriter writer = new PacketWriter())
            {
                err.Serialize(writer);
                using (PacketReader reader = new PacketReader(writer.ToPayloadArray()))
                {
                    ErrorMessage deserialized = ErrorMessage.Deserialize(reader);
                    Assert(deserialized.ErrorCode == 404, "ErrorCode is 404");
                    Assert(deserialized.Description == "Resource not found", "Error description matches");
                }
            }

            ShutdownMessage shut = new ShutdownMessage { ReasonCode = 0, ReasonText = "User exited cleanly" };
            using (PacketWriter writer = new PacketWriter())
            {
                shut.Serialize(writer);
                using (PacketReader reader = new PacketReader(writer.ToPayloadArray()))
                {
                    ShutdownMessage deserialized = ShutdownMessage.Deserialize(reader);
                    Assert(deserialized.ReasonCode == 0, "Shutdown ReasonCode is 0");
                    Assert(deserialized.ReasonText == "User exited cleanly", "Shutdown ReasonText matches");
                }
            }
        }

        private static void TestTruncatedPacketThrows()
        {
            byte[] truncatedPayload = new byte[4]; // CameraState needs at least 8*3 + 4*4 + 8 = 48 bytes
            bool caught = false;
            try
            {
                using (PacketReader reader = new PacketReader(truncatedPayload))
                {
                    CameraStateMessage.Deserialize(reader);
                }
            }
            catch (ProtocolTruncatedException)
            {
                caught = true;
            }
            Assert(caught, "Deserializing truncated payload throws ProtocolTruncatedException");
        }

        private static void TestStringBoundaryChecks()
        {
            // Empty string
            using (PacketWriter writer = new PacketWriter())
            {
                writer.WriteString("");
                using (PacketReader reader = new PacketReader(writer.ToPayloadArray()))
                {
                    Assert(reader.ReadString() == "", "Empty string roundtrips as empty string");
                }
            }

            // UTF-8 multi-byte characters
            string unicode = "Minecraft × ULTRAKILL ブリッジ 🕹️";
            using (PacketWriter writer = new PacketWriter())
            {
                writer.WriteString(unicode);
                using (PacketReader reader = new PacketReader(writer.ToPayloadArray()))
                {
                    Assert(reader.ReadString() == unicode, "UTF-8 multi-byte string roundtrips perfectly");
                }
            }

            // Exceeding max string length
            string hugeString = new string('A', ProtocolConstants.MaxStringLength + 1);
            bool caughtTooLong = false;
            try
            {
                using (PacketWriter writer = new PacketWriter())
                {
                    writer.WriteString(hugeString);
                }
            }
            catch (ArgumentException)
            {
                caughtTooLong = true;
            }
            Assert(caughtTooLong, "Writing string > 1024 bytes throws ArgumentException");
        }
    }
}
