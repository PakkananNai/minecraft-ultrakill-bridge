package net.pakkanannai.mcfabricmod.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.pakkanannai.mcfabricmod.MinecraftBridgeMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class LivingEntityDamageMixin {
    @Inject(method = "damage", at = @At("RETURN"))
    private void minecraftBridge$damage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || amount <= 0.0f || !((Object) this instanceof LivingEntity target)) return;
        MinecraftBridgeMod.sendDamageEvent(target, source, amount);
    }
}
