using System;
using System.IO;
using MinecraftBridge.Protocol;

namespace CrossLangTest
{
    class Program
    {
        static void Main(string[] args)
        {
            string outDir = Path.Combine("tools", "cross_lang_test", "csharp_packets");
            Directory.CreateDirectory(outDir);

            // Hello
            var hello = new HelloMessage { ProtocolVersion = 1, ClientName = "TestClient", ClientVersion = "1.0", Capabilities = 0x0F };
            WritePacket(hello, Path.Combine(outDir, "hello.bin"), 1);
            // HelloAck
            var helloAck = new HelloAckMessage { Status = 0, AcceptedVersion = 1, SessionId = 0x12345678, ErrorMessage = "" };
            WritePacket(helloAck, Path.Combine(outDir, "helloack.bin"), 2);
            // Ping
            var ping = new PingMessage { TimestampNs = 123456789UL };
            WritePacket(ping, Path.Combine(outDir, "ping.bin"), 3);
            // Pong
            var pong = new PongMessage { TimestampNs = 987654321UL };
            WritePacket(pong, Path.Combine(outDir, "pong.bin"), 4);
            // FrameMetadata
            var frame = new FrameMetadataMessage { BufferId = 1, Width = 1920, Height = 1080, Stride = 1920 * 4, Format = 1, SequenceNumber = 42UL, TimestampNs = 111222333444555ULL };
            WritePacket(frame, Path.Combine(outDir, "framemetadata.bin"), 5);
            // CameraState
            var cam = new CameraStateMessage { PosX = 10.0, PosY = 20.0, PosZ = 30.0, Yaw = 45.0f, Pitch = 10.0f, Roll = 0.0f, Fov = 70.0f, SequenceNumber = 6 };
            WritePacket(cam, Path.Combine(outDir, "camerastate.bin"), 6);
            // InputEvent
            var input = new InputEventMessage { EventType = 3, KeyCode = 0, MouseDx = -5, MouseDy = 5, MouseButtons = 1, WheelDelta = 0 };
            WritePacket(input, Path.Combine(outDir, "inputevent.bin"), 7);
            // InputFocus
            var focus = new InputFocusMessage { HasFocus = true, ReleaseHeldKeys = false };
            WritePacket(focus, Path.Combine(outDir, "inputfocus.bin"), 8);
            // Error
            var err = new ErrorMessage { ErrorCode = 404, Description = "Not Found" };
            WritePacket(err, Path.Combine(outDir, "error.bin"), 9);
            // Shutdown
            var shut = new ShutdownMessage { ReasonCode = 0, ReasonText = "Normal" };
            WritePacket(shut, Path.Combine(outDir, "shutdown.bin"), 10);
        }

        static void WritePacket(IMessage message, string path, uint seqId)
        {
            using (var writer = new PacketWriter())
            {
                var mi = message.GetType().GetMethod("Serialize");
                mi.Invoke(message, new object[] { writer });
                byte[] packet = writer.BuildPacket(message.MessageType, seqId);
                File.WriteAllBytes(path, packet);
            }
        }
    }
}
