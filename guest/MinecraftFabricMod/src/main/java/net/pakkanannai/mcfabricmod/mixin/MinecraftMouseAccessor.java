package net.pakkanannai.mcfabricmod.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mouse.class)
public interface MinecraftMouseAccessor {
    @Invoker("onMouseButton")
    void minecraftBridge$onMouseButton(long window, int button, int action, int mods);

    @Invoker("onMouseScroll")
    void minecraftBridge$onMouseScroll(long window, double horizontal, double vertical);
}
