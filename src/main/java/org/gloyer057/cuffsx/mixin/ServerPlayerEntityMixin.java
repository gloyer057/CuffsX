package org.gloyer057.cuffsx.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.gloyer057.cuffsx.cuff.CuffManager;
import org.gloyer057.cuffsx.cuff.CuffType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void cuffsx_onJump(CallbackInfo ci) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        if (CuffManager.isCuffed(self, CuffType.LEGS)) {
            ci.cancel();
        }
    }

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void cuffsx_onInteractBlock(CallbackInfoReturnable<ActionResult> ci) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        if (CuffManager.isCuffed(self, CuffType.HANDS)) {
            ci.cancel();
        }
    }

    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void cuffsx_onInteractItem(CallbackInfoReturnable<net.minecraft.util.TypedActionResult<net.minecraft.item.ItemStack>> ci) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        if (CuffManager.isCuffed(self, CuffType.HANDS)) {
            ci.cancel();
        }
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void cuffsx_onAttack(Entity target, CallbackInfo ci) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        if (CuffManager.isCuffed(self, CuffType.HANDS)) {
            ci.cancel();
        }
    }
}
