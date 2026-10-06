package net.pakkanannai.mcfabricmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.pakkanannai.mcfabricmod.mixin.MinecraftMouseAccessor;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Main-thread application of host-forwarded keyboard and mouse input. */
public final class GuestInputBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftBridge");
    private static final int PRESS = 1;
    private static final int RELEASE = 0;
    private final ConcurrentLinkedQueue<Object> queue = new ConcurrentLinkedQueue<>();
    private final Set<Integer> heldKeys = new HashSet<>();
    private final Set<Integer> heldMouseButtons = new HashSet<>();
    private final boolean[] loggedEventTypes = new boolean[7];
    private volatile boolean guestFocus;

    public GuestInputBridge() {
    }

    public void enqueueInput(int eventType, long keyCode, int dx, int dy, int wheelDelta) {
        queue.add(new Input(eventType, keyCode, dx, dy, wheelDelta));
    }

    public void enqueueFocus(boolean focus, boolean releaseHeldKeys) {
        queue.add(new Focus(focus, releaseHeldKeys));
    }

    public void tick(MinecraftClient client) {
        Object item;
        while ((item = queue.poll()) != null) {
            if (item instanceof Focus) applyFocus(client, (Focus) item);
            else applyInput(client, (Input) item);
        }
    }

    private void applyFocus(MinecraftClient client, Focus focus) {
        guestFocus = focus.focus;
        LOGGER.info("M7_GUEST_INPUT_FOCUS focus={} releaseHeldKeys={} thread={}", focus.focus, focus.releaseHeldKeys, Thread.currentThread().getName());
        if (focus.focus && client.currentScreen instanceof GameMenuScreen) {
            client.setScreen(null);
            LOGGER.info("M9_GAME_MENU_CLOSED_ON_FOCUS");
        }
        if (focus.releaseHeldKeys) releaseAll(client);
    }

    private void applyInput(MinecraftClient client, Input input) {
        if (!guestFocus) return;
        if (input.eventType >= 1 && input.eventType <= 6 && !loggedEventTypes[input.eventType]) {
            loggedEventTypes[input.eventType] = true;
            LOGGER.info("M7_GUEST_INPUT_APPLIED type={} key={} dx={} dy={} wheel={} thread={}", input.eventType, input.keyCode, input.dx, input.dy, input.wheelDelta, Thread.currentThread().getName());
        }
        long window = client.getWindow().getHandle();
        switch (input.eventType) {
                case 1:
                    client.keyboard.onKey(window, (int) input.keyCode, 0, PRESS, 0);
                    heldKeys.add((int) input.keyCode);
                    break;
                case 2:
                    client.keyboard.onKey(window, (int) input.keyCode, 0, RELEASE, 0);
                    heldKeys.remove((int) input.keyCode);
                    break;
                case 3:
                    if (client.player != null) {
                        double sensitivity = client.options.getMouseSensitivity().getValue();
                        double factor = sensitivity * 0.6D + 0.2D;
                        factor = factor * factor * factor * 8.0D;
                        client.player.changeLookDirection(input.dx * factor, input.dy * factor);
                    }
                    break;
                case 4:
                    ((MinecraftMouseAccessor) client.mouse).minecraftBridge$onMouseButton(window, (int) input.keyCode, PRESS, 0);
                    heldMouseButtons.add((int) input.keyCode);
                    break;
                case 5:
                    ((MinecraftMouseAccessor) client.mouse).minecraftBridge$onMouseButton(window, (int) input.keyCode, RELEASE, 0);
                    heldMouseButtons.remove((int) input.keyCode);
                    break;
                case 6:
                    ((MinecraftMouseAccessor) client.mouse).minecraftBridge$onMouseScroll(window, 0.0D, (double) input.wheelDelta);
                    break;
                default:
                    break;
            }
    }


    private void releaseAll(MinecraftClient client) {
        long window = client.getWindow().getHandle();
        for (Integer key : heldKeys) {
            client.keyboard.onKey(window, key, 0, RELEASE, 0);
        }
        heldKeys.clear();
        for (Integer button : heldMouseButtons) {
            ((MinecraftMouseAccessor) client.mouse).minecraftBridge$onMouseButton(window, button, RELEASE, 0);
        }
        heldMouseButtons.clear();
    }

    public boolean hasFocus() { return guestFocus; }

    private static final class Input {
        final int eventType, dx, dy, wheelDelta; final long keyCode;
        Input(int eventType, long keyCode, int dx, int dy, int wheelDelta) {
            this.eventType = eventType; this.keyCode = keyCode; this.dx = dx; this.dy = dy; this.wheelDelta = wheelDelta;
        }
    }
    private static final class Focus {
        final boolean focus, releaseHeldKeys;
        Focus(boolean focus, boolean releaseHeldKeys) { this.focus = focus; this.releaseHeldKeys = releaseHeldKeys; }
    }
}
