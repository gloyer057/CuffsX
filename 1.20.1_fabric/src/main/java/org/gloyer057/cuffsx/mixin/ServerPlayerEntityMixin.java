package org.gloyer057.cuffsx.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.gloyer057.cuffsx.cuff.CuffManager;
import org.gloyer057.cuffsx.cuff.CuffType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    // Блокировка атаки при наручниках на руках
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void cuffsx_onAttack(Entity target, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayerEntity self)) return;
        if (CuffManager.isCuffed(self, CuffType.HANDS)) {
            ci.cancel();
        }
    }
}
