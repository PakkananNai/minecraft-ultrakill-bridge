package net.pakkanannai.mcfabricmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MinecraftBridgeMod implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftBridge");
    private static GuestControlClient CONTROL_CLIENT;
    private GuestControlClient controlClient;
    private MinecraftFramebufferCapture framebufferCapture;
    private GuestInputBridge inputBridge;
    private GuestRaycastBridge raycastBridge;
    private GuestEntityBridge entityBridge;
    private long cameraSequence;
    private int cameraTickCounter;
    private boolean m10TestWorldStarted;
    private boolean m10ProbeSpawned;
    private int m10ProbeTicks;
    private int m10ProbeEntityId = Integer.MIN_VALUE;
    private boolean m10ProbeInteractionDone;
    private boolean m10ProbeRemoved;

    @Override public void onInitializeClient() {
        controlClient = new GuestControlClient();
        CONTROL_CLIENT = controlClient;
        framebufferCapture = new MinecraftFramebufferCapture(controlClient);
        inputBridge = new GuestInputBridge();
        raycastBridge = new GuestRaycastBridge();
        raycastBridge.setControlClient(controlClient);
        entityBridge = new GuestEntityBridge(controlClient);
        controlClient.setInputBridge(inputBridge);
        controlClient.setRaycastBridge(raycastBridge);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> client.options.pauseOnLostFocus = false);
        controlClient.start();
        HudRenderCallback.EVENT.register((drawContext, tickCounter) ->
                framebufferCapture.capture(MinecraftClient.getInstance()));
        ClientTickEvents.START_CLIENT_TICK.register(client -> inputBridge.tick(client));
        ClientTickEvents.END_CLIENT_TICK.register(client -> raycastBridge.tick(client));
        ClientTickEvents.END_CLIENT_TICK.register(client -> entityBridge.tick(client));
        ClientTickEvents.END_CLIENT_TICK.register(this::m10TestWorld);
        ClientTickEvents.END_CLIENT_TICK.register(this::sendCameraState);
        if (Boolean.getBoolean("minecraft.ultrakill.bridge.captureWithoutWorld")) {
            ClientTickEvents.END_CLIENT_TICK.register(client -> framebufferCapture.capture(client));
            LOGGER.info("Diagnostic framebuffer capture tick hook enabled");
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            LOGGER.info("Stopping Minecraft bridge framebuffer capture");
            framebufferCapture.close();
            LOGGER.info("Stopping Minecraft bridge guest connection");
            controlClient.close();
            CONTROL_CLIENT = null;
        });
        LOGGER.info("Minecraft bridge guest initialized");
    }

    public static void sendEntityInteraction(int entityId, int interactionType, int hand, double hitX, double hitY, double hitZ) {
        GuestControlClient client = CONTROL_CLIENT;
        if (client != null) client.sendEntityInteraction(entityId, interactionType, hand, hitX, hitY, hitZ);
    }

    public static void sendDamageEvent(net.minecraft.entity.LivingEntity target, net.minecraft.entity.damage.DamageSource source, float amount) {
        GuestControlClient client = CONTROL_CLIENT;
        if (client == null || !client.isConnected()) return;
        var attacker = source.getAttacker();
        long attackerId = attacker == null ? 0xffffffffL : attacker.getId();
        client.sendDamageEvent(new com.bridge.minecraft.protocol.Messages.DamageEventMessage(
                1, target.getId(), attackerId, amount, target.getHealth(), target.getMaxHealth(), "damage"));
    }

    private void m10TestWorld(MinecraftClient client) {
        if (!Boolean.getBoolean("minecraft.ultrakill.bridge.testWorld")) return;
        if (!m10TestWorldStarted) {
            if (client.world != null) return;
            m10TestWorldStarted = true;
            LOGGER.info("M10_TEST_WORLD_START New World");
            client.createIntegratedServerLoader().start("New World", () -> LOGGER.warn("M10_TEST_WORLD_CANCELLED"));
            return;
        }
        if (client.world == null || client.getServer() == null) return;
        if (++m10ProbeTicks < 40) return;
        var server = client.getServer();
        if (!m10ProbeSpawned) {
            m10ProbeSpawned = true;
            server.execute(() -> {
                var world = server.getOverworld();
                var spawn = world.getSpawnPos();
                var pig = net.minecraft.entity.EntityType.PIG.create(world);
                if (pig == null) return;
                pig.refreshPositionAndAngles(spawn.getX() + 2.0, spawn.getY(), spawn.getZ(), 0.0f, 0.0f);
                world.spawnEntity(pig);
                m10ProbeEntityId = pig.getId();
                LOGGER.info("M10_TEST_ENTITY_SPAWN id=" + m10ProbeEntityId);
            });
            return;
        }
        var target = client.world.getEntityById(m10ProbeEntityId);
        if (target != null && client.player != null && !m10ProbeInteractionDone && client.interactionManager != null) {
            m10ProbeInteractionDone = true;
            LOGGER.info("M10_TEST_ENTITY_INTERACTION id=" + m10ProbeEntityId);
            client.interactionManager.attackEntity(client.player, target);
        }
        if (!m10ProbeRemoved && m10ProbeTicks >= 140) {
            m10ProbeRemoved = true;
            server.execute(() -> {
                var targetServer = server.getOverworld().getEntityById(m10ProbeEntityId);
                if (targetServer != null) targetServer.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                LOGGER.info("M10_TEST_ENTITY_REMOVE id=" + m10ProbeEntityId);
            });
        }
    }

    private void sendCameraState(MinecraftClient client) {
        if (++cameraTickCounter < 3) return;
        cameraTickCounter = 0;
        if (client.getCameraEntity() == null || !controlClient.isConnected()) return;

        var camera = client.getCameraEntity();
        var position = camera.getCameraPosVec(1.0f);
        double fov = client.options.getFov().getValue();
        controlClient.sendCameraState(position.x, position.y, position.z,
                camera.getYaw(1.0f), camera.getPitch(1.0f), 0.0f, (float) fov, ++cameraSequence);
    }
}
