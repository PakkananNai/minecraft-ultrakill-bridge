package com.bridge.minecraft.protocol;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JavaCrossTest {
    private static final String INPUT_DIR = "tools/cross_lang_test/csharp_packets";
    private static final String OUTPUT_DIR = "tools/cross_lang_test/java_packets";

    public static void main(String[] args) throws IOException {
        new File(OUTPUT_DIR).mkdirs();
        File dir = new File(INPUT_DIR);
        if (!dir.isDirectory()) {
            System.err.println("Input directory not found: " + INPUT_DIR);
            System.exit(1);
        }
        for (File file : dir.listFiles((d, name) -> name.endsWith(".bin"))) {
            System.out.println("Processing " + file.getName());
            byte[] data = Files.readAllBytes(file.toPath());
            MessageHeader header = MessageHeader.Parse(data, 0, true);
            int payloadOffset = ProtocolConstants.HeaderSize;
            int payloadLength = (int) header.getPayloadLength();
            PacketReader reader = new PacketReader(data, payloadOffset, payloadLength);
            IMessage message = null;
            switch (header.getType()) {
                case Hello:
                    message = HelloMessage.Deserialize(reader);
                    break;
                case HelloAck:
                    message = HelloAckMessage.Deserialize(reader);
                    break;
                case Ping:
                    message = PingMessage.Deserialize(reader);
                    break;
                case Pong:
                    message = PongMessage.Deserialize(reader);
                    break;
                case FrameMetadata:
                    message = FrameMetadataMessage.Deserialize(reader);
                    break;
                case CameraState:
                    message = CameraStateMessage.Deserialize(reader);
                    break;
                case InputEvent:
                    message = InputEventMessage.Deserialize(reader);
                    break;
                case InputFocus:
                    message = InputFocusMessage.Deserialize(reader);
                    break;
                case Error:
                    message = ErrorMessage.Deserialize(reader);
                    break;
                case Shutdown:
                    message = ShutdownMessage.Deserialize(reader);
                    break;
                default:
                    System.err.println("Unsupported type: " + header.getType());
                    continue;
            }
            // Re‑serialize using Java implementation
            PacketWriter writer = new PacketWriter();
            try {
                message.getClass().getMethod("Serialize", PacketWriter.class).invoke(message, writer);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            byte[] newPacket = writer.BuildPacket(header.getType(), header.getSequenceId());
            Path outPath = Path.of(OUTPUT_DIR, file.getName());
            Files.write(outPath, newPacket);
        }
        System.out.println("Java cross‑language test completed.");
    }
}
