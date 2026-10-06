package net.pakkanannai.mcfabricmod.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.util.ActionResult;
import net.pakkanannai.mcfabricmod.MinecraftBridgeMod;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class EntityInteractionMixin {
    @Inject(method = "attackEntity", at = @At("TAIL"))
    private void minecraftBridge$attackEntity(PlayerEntity player, Entity entity, CallbackInfo ci) {
        MinecraftBridgeMod.sendEntityInteraction(entity.getId(), 2, 0, entity.getX(), entity.getY(), entity.getZ());
    }

    @Inject(method = "interactEntity", at = @At("RETURN"))
    private void minecraftBridge$interactEntity(PlayerEntity player, Entity entity, Hand hand,
                                                  CallbackInfoReturnable<ActionResult> cir) {
        MinecraftBridgeMod.sendEntityInteraction(entity.getId(), 1, hand.ordinal(), entity.getX(), entity.getY(), entity.getZ());
    }

    @Inject(method = "interactEntityAtLocation", at = @At("RETURN"))
    private void minecraftBridge$interactEntityAtLocation(PlayerEntity player, Entity entity, EntityHitResult hit,
                                                            Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        MinecraftBridgeMod.sendEntityInteraction(entity.getId(), 1, hand.ordinal(),
                hit.getPos().x, hit.getPos().y, hit.getPos().z);
    }
}
