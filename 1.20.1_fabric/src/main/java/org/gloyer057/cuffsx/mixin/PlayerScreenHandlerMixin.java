package org.gloyer057.cuffsx.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import org.gloyer057.cuffsx.cuff.CuffManager;
import org.gloyer057.cuffsx.cuff.CuffType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerScreenHandler.class)
public abstract class PlayerScreenHandlerMixin {

    @Shadow public PlayerEntity owner;

    @Inject(method = "canInsertIntoSlot", at = @At("HEAD"), cancellable = true)
    private void cuffsx_blockInventory(ItemStack stack, Slot slot, CallbackInfoReturnable<Boolean> cir) {
        if (owner instanceof ServerPlayerEntity player) {
            if (CuffManager.isCuffed(player, CuffType.HANDS)) {
                cir.setReturnValue(false);
            }
        }
    }
}
