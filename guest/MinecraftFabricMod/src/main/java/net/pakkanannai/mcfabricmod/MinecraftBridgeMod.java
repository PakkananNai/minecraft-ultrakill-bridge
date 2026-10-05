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
    private GuestControlClient controlClient;
    private MinecraftFramebufferCapture framebufferCapture;
    private GuestInputBridge inputBridge;

    @Override public void onInitializeClient() {
        controlClient = new GuestControlClient();
        framebufferCapture = new MinecraftFramebufferCapture(controlClient);
        inputBridge = new GuestInputBridge();
        controlClient.setInputBridge(inputBridge);
        controlClient.start();
        HudRenderCallback.EVENT.register((drawContext, tickCounter) ->
                framebufferCapture.capture(MinecraftClient.getInstance()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> inputBridge.tick(client));
        if (Boolean.getBoolean("minecraft.ultrakill.bridge.captureWithoutWorld")) {
            ClientTickEvents.END_CLIENT_TICK.register(client -> framebufferCapture.capture(client));
            LOGGER.info("Diagnostic framebuffer capture tick hook enabled");
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            LOGGER.info("Stopping Minecraft bridge framebuffer capture");
            framebufferCapture.close();
            LOGGER.info("Stopping Minecraft bridge guest connection");
            controlClient.close();
        });
        LOGGER.info("Minecraft bridge guest initialized");
    }
}
