package net.pakkanannai.mcfabricmod;

import static org.junit.jupiter.api.Assertions.*;
import com.bridge.minecraft.protocol.MessageType;
import com.bridge.minecraft.protocol.Messages;
import com.bridge.minecraft.protocol.PacketReader;
import com.bridge.minecraft.protocol.PacketWriter;
import org.junit.jupiter.api.Test;

class EntityMessageTest {
    @Test
    void damageEventRoundTrip() throws Exception {
        Messages.DamageEventMessage original = new Messages.DamageEventMessage(1, 7, 3, 2.5f, 17.5f, 20.0f, "player");
        PacketWriter writer = new PacketWriter();
        original.serialize(writer);
        Messages.DamageEventMessage decoded = Messages.DamageEventMessage.deserialize(new PacketReader(writer.toPayloadArray()));
        assertEquals(1, decoded.getEventType());
        assertEquals(7, decoded.getTargetId());
        assertEquals(3, decoded.getAttackerId());
        assertEquals(2.5f, decoded.getAmount());
        assertEquals(17.5f, decoded.getHealth());
        assertEquals(20.0f, decoded.getMaxHealth());
        assertEquals("player", decoded.getSourceType());
    }

    @Test void entityUpdateRoundTrips() throws Exception {
        Messages.EntityUpdateMessage original = new Messages.EntityUpdateMessage(
                42, "minecraft:zombie", 1.25, 64.5, -3.75,
                90.0f, -12.5f, 0.1f, -0.2f, 0.3f, 1);
        PacketWriter writer = new PacketWriter();
        original.serialize(writer);
        PacketReader reader = new PacketReader(writer.toPayloadArray());
        Messages.EntityUpdateMessage decoded = Messages.EntityUpdateMessage.deserialize(reader);

        assertEquals(MessageType.ENTITY_UPDATE, original.getMessageType());
        assertEquals(0, reader.getRemaining());
        assertEquals(original.getEntityId(), decoded.getEntityId());
        assertEquals(original.getEntityType(), decoded.getEntityType());
        assertEquals(original.getPosX(), decoded.getPosX());
        assertEquals(original.getPosY(), decoded.getPosY());
        assertEquals(original.getPosZ(), decoded.getPosZ());
        assertEquals(original.getYaw(), decoded.getYaw());
        assertEquals(original.getPitch(), decoded.getPitch());
        assertEquals(original.getVelocityX(), decoded.getVelocityX());
        assertEquals(original.getVelocityY(), decoded.getVelocityY());
        assertEquals(original.getVelocityZ(), decoded.getVelocityZ());
        assertEquals(original.getFlags(), decoded.getFlags());
    }

    @Test void entityRemoveAndInteractionRoundTrip() throws Exception {
        Messages.EntityRemoveMessage remove = new Messages.EntityRemoveMessage(77);
        PacketWriter removeWriter = new PacketWriter();
        remove.serialize(removeWriter);
        Messages.EntityRemoveMessage decodedRemove =
                Messages.EntityRemoveMessage.deserialize(new PacketReader(removeWriter.toPayloadArray()));
        assertEquals(MessageType.ENTITY_REMOVE, remove.getMessageType());
        assertEquals(77, decodedRemove.getEntityId());

        Messages.EntityInteractionMessage interaction =
                new Messages.EntityInteractionMessage(77, 1, 0, 2.0, 3.0, 4.0);
        PacketWriter interactionWriter = new PacketWriter();
        interaction.serialize(interactionWriter);
        PacketReader interactionReader = new PacketReader(interactionWriter.toPayloadArray());
        Messages.EntityInteractionMessage decodedInteraction =
                Messages.EntityInteractionMessage.deserialize(interactionReader);
        assertEquals(MessageType.ENTITY_INTERACTION, interaction.getMessageType());
        assertEquals(0, interactionReader.getRemaining());
        assertEquals(interaction.getEntityId(), decodedInteraction.getEntityId());
        assertEquals(interaction.getInteractionType(), decodedInteraction.getInteractionType());
        assertEquals(interaction.getHand(), decodedInteraction.getHand());
        assertEquals(interaction.getHitX(), decodedInteraction.getHitX());
        assertEquals(interaction.getHitY(), decodedInteraction.getHitY());
        assertEquals(interaction.getHitZ(), decodedInteraction.getHitZ());
    }
}
