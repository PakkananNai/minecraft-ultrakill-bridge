using System;
using System.IO;
using System.Reflection;
using MinecraftBridge.Protocol;

namespace CrossLangValidator
{
    class Program
    {
        static int Main(string[] args)
        {
            string inputDir = Path.Combine("tools", "cross_lang_test", "java_packets");
            if (!Directory.Exists(inputDir))
            {
                Console.Error.WriteLine($"Input directory not found: {inputDir}");
                return 1;
            }

            foreach (var file in Directory.GetFiles(inputDir, "*.bin"))
            {
                Console.WriteLine($"Validating {Path.GetFileName(file)}");
                byte[] data = File.ReadAllBytes(file);
                MessageHeader header = MessageHeader.Parse(data, 0, true);
                int payloadOffset = ProtocolConstants.HeaderSize;
                int payloadLength = (int)header.PayloadLength;
                var reader = new PacketReader(data, payloadOffset, payloadLength);
                IMessage message;
                try
                {
                    switch (header.Type)
                    {
                        case MessageType.Hello:
                            message = HelloMessage.Deserialize(reader);
                            break;
                        case MessageType.HelloAck:
                            message = HelloAckMessage.Deserialize(reader);
                            break;
                        case MessageType.StartStream:
                            message = StartStreamMessage.Deserialize(reader);
                            break;
                        case MessageType.Ping:
                            message = PingMessage.Deserialize(reader);
                            break;
                        case MessageType.Pong:
                            message = PongMessage.Deserialize(reader);
                            break;
                        case MessageType.FrameMetadata:
                            message = FrameMetadataMessage.Deserialize(reader);
                            break;
                        case MessageType.CameraState:
                            message = CameraStateMessage.Deserialize(reader);
                            break;
                        case MessageType.InputEvent:
                            message = InputEventMessage.Deserialize(reader);
                            break;
                        case MessageType.InputFocus:
                            message = InputFocusMessage.Deserialize(reader);
                            break;
                        case MessageType.Error:
                            message = ErrorMessage.Deserialize(reader);
                            break;
                        case MessageType.Shutdown:
                            message = ShutdownMessage.Deserialize(reader);
                            break;
                        default:
                            Console.Error.WriteLine($"Unsupported type: {header.Type}");
                            return 1;
                    }
                }
                catch (Exception ex)
                {
                    Console.Error.WriteLine($"Deserialization failed for {Path.GetFileName(file)}: {ex}");
                    return 1;
                }

                using var writer = new PacketWriter();
                MethodInfo mi = message.GetType().GetMethod("Serialize");
                mi.Invoke(message, new object[] { writer });
                byte[] newPacket = writer.BuildPacket(message.MessageType, header.SequenceId);
                if (!newPacket.AsSpan().SequenceEqual(data))
                {
                    Console.Error.WriteLine($"Round‑trip mismatch for {Path.GetFileName(file)}");
                    return 1;
                }
            }
            Console.WriteLine("All Java‑generated packets validated successfully.");
            return 0;
        }
    }
}
