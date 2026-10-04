package net.pakkanannai.mcfabricmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MinecraftBridgeMod implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftBridge");
    private GuestControlClient controlClient;

    @Override public void onInitializeClient() {
        controlClient = new GuestControlClient();
        controlClient.start();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            LOGGER.info("Stopping Minecraft bridge guest connection");
            controlClient.close();
        });
        LOGGER.info("Minecraft bridge guest initialized");
    }
}
