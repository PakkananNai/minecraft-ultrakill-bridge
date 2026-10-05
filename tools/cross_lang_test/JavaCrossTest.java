package com.bridge.minecraft.protocol;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.bridge.minecraft.protocol.Messages.Message;
import com.bridge.minecraft.protocol.Messages.HelloMessage;
import com.bridge.minecraft.protocol.Messages.HelloAckMessage;
import com.bridge.minecraft.protocol.Messages.PingMessage;
import com.bridge.minecraft.protocol.Messages.StartStreamMessage;
import com.bridge.minecraft.protocol.Messages.PongMessage;
import com.bridge.minecraft.protocol.Messages.FrameMetadataMessage;
import com.bridge.minecraft.protocol.Messages.CameraStateMessage;
import com.bridge.minecraft.protocol.Messages.InputEventMessage;
import com.bridge.minecraft.protocol.Messages.InputFocusMessage;
import com.bridge.minecraft.protocol.Messages.ErrorMessage;
import com.bridge.minecraft.protocol.Messages.ShutdownMessage;
import com.bridge.minecraft.protocol.MessageHeader;
import com.bridge.minecraft.protocol.PacketReader;
import com.bridge.minecraft.protocol.PacketWriter;
import com.bridge.minecraft.protocol.ProtocolConstants;
import com.bridge.minecraft.protocol.ProtocolException;
public class JavaCrossTest {
    private static final String INPUT_DIR = "tools/cross_lang_test/csharp_packets";
    private static final String OUTPUT_DIR = "tools/cross_lang_test/java_packets";

    public static void main(String[] args) throws IOException, ProtocolException {
        new File(OUTPUT_DIR).mkdirs();
        File dir = new File(INPUT_DIR);
        if (!dir.isDirectory()) {
            System.err.println("Input directory not found: " + INPUT_DIR);
            System.exit(1);
        }
        for (File file : dir.listFiles((d, name) -> name.endsWith(".bin"))) {
            System.out.println("Processing " + file.getName());
            byte[] data = Files.readAllBytes(file.toPath());
            MessageHeader header = MessageHeader.parse(data, 0, true);
            int payloadOffset = ProtocolConstants.HEADER_SIZE;
            int payloadLength = (int) header.getPayloadLength();
            PacketReader reader = new PacketReader(data, payloadOffset, payloadLength);
            Message message = null;
            switch (header.getType()) {
                case HELLO:
                    message = HelloMessage.deserialize(reader);
                    break;
                case HELLO_ACK:
                    message = HelloAckMessage.deserialize(reader);
                    break;
                case START_STREAM:
                    message = StartStreamMessage.deserialize(reader);
                    break;
                case PING:
                    message = PingMessage.deserialize(reader);
                    break;
                case PONG:
                    message = PongMessage.deserialize(reader);
                    break;
                case FRAME_METADATA:
                    message = FrameMetadataMessage.deserialize(reader);
                    break;
                case CAMERA_STATE:
                    message = CameraStateMessage.deserialize(reader);
                    break;
                case INPUT_EVENT:
                    message = InputEventMessage.deserialize(reader);
                    break;
                case INPUT_FOCUS:
                    message = InputFocusMessage.deserialize(reader);
                    break;
                case ERROR:
                    message = ErrorMessage.deserialize(reader);
                    break;
                case SHUTDOWN:
                    message = ShutdownMessage.deserialize(reader);
                    break;
                default:
                    System.err.println("Unsupported type: " + header.getType());
                    continue;
            }
            // Re‑serialize using Java implementation
            PacketWriter writer = new PacketWriter();
            message.serialize(writer);
            byte[] newPacket = writer.buildPacket(header.getType(), header.getSequenceId());
            Path outPath = Paths.get(OUTPUT_DIR, file.getName());
            Files.write(outPath, newPacket);
        }
        System.out.println("Java cross‑language test completed.");
    }
}
