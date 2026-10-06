package net.pakkanannai.mcfabricmod;

import com.bridge.minecraft.protocol.Messages;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Sends lightweight, Minecraft-authoritative entity state to the host. */
final class GuestEntityBridge {
    private static final double POSITION_EPSILON_SQUARED = 0.0001;
    private static final float ROTATION_EPSILON = 0.5f;
    private final GuestControlClient controlClient;
    private final Map<Integer, State> known = new HashMap<>();
    private boolean wasConnected;

    GuestEntityBridge(GuestControlClient controlClient) {
        this.controlClient = controlClient;
    }

    void tick(MinecraftClient client) {
        boolean connected = controlClient.isConnected();
        if (!connected) {
            known.clear();
            wasConnected = false;
            return;
        }
        if (client.world == null || client.player == null) return;

        boolean fullSync = !wasConnected;
        wasConnected = true;
        Set<Integer> visible = new HashSet<>();
        for (Entity entity : client.world.getOtherEntities(client.player,
                client.player.getBoundingBox().expand(64.0), Entity::isAlive)) {

            int id = entity.getId();
            visible.add(id);
            State next = State.from(entity);
            State previous = known.get(id);
            if (fullSync || previous == null || previous.changed(next)) {
                controlClient.sendEntityUpdate(next.toMessage());
                if (next.maxHealth > 0 && (fullSync || previous == null || previous.health != next.health || previous.maxHealth != next.maxHealth))
                    controlClient.sendDamageEvent(next.toHealthMessage());
                known.put(id, next);
            }
        }

        known.keySet().removeIf(id -> {
            if (visible.contains(id)) return false;
            controlClient.sendEntityRemove(id);
            return true;
        });
    }

    private static final class State {
        final int id;
        final String type;
        final double x, y, z;
        final float yaw, pitch;
        final float velocityX, velocityY, velocityZ;
        final float health, maxHealth;
        final int flags;

        private State(int id, String type, double x, double y, double z, float yaw, float pitch,
                      float velocityX, float velocityY, float velocityZ, float health, float maxHealth, int flags) {
            this.id = id; this.type = type;
            this.x = x; this.y = y; this.z = z;
            this.yaw = yaw; this.pitch = pitch;
            this.velocityX = velocityX; this.velocityY = velocityY; this.velocityZ = velocityZ;
            this.health = health; this.maxHealth = maxHealth;
            this.flags = flags;
        }

        static State from(Entity entity) {
            Identifier typeId = EntityType.getId(entity.getType());
            var velocity = entity.getVelocity();
            int flags = entity.isOnGround() ? 1 : 0;
            float health = entity instanceof LivingEntity living ? living.getHealth() : 0.0f;
            float maxHealth = entity instanceof LivingEntity living ? living.getMaxHealth() : 0.0f;
            return new State(entity.getId(), typeId.toString(),
                    entity.getX(), entity.getY(), entity.getZ(),
                    entity.getYaw(), entity.getPitch(),
                    (float) velocity.x, (float) velocity.y, (float) velocity.z, health, maxHealth, flags);
        }

        boolean changed(State other) {
            double dx = x - other.x, dy = y - other.y, dz = z - other.z;
            double dvx = velocityX - other.velocityX, dvy = velocityY - other.velocityY, dvz = velocityZ - other.velocityZ;
            return !type.equals(other.type)
                    || dx * dx + dy * dy + dz * dz > POSITION_EPSILON_SQUARED
                    || Math.abs(yaw - other.yaw) > ROTATION_EPSILON
                    || Math.abs(pitch - other.pitch) > ROTATION_EPSILON
                    || dvx * dvx + dvy * dvy + dvz * dvz > POSITION_EPSILON_SQUARED
                    || health != other.health
                    || maxHealth != other.maxHealth
                    || flags != other.flags;
        }

        Messages.EntityUpdateMessage toMessage() {
            return new Messages.EntityUpdateMessage(id, type, x, y, z, yaw, pitch,
                    velocityX, velocityY, velocityZ, flags);
        }

        Messages.DamageEventMessage toHealthMessage() {
            return new Messages.DamageEventMessage(2, id, 0xffffffffL, 0.0f, health, maxHealth, "health");
        }
    }
}
