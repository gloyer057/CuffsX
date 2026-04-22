package org.gloyer057.cuffsx.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.gloyer057.cuffsx.client.CuffsFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cuffsx_addCuffsFeature(CallbackInfo ci) {
        PlayerEntityRenderer self = (PlayerEntityRenderer) (Object) this;
        // addFeature is protected in LivingEntityRenderer — call via the mixin's own access
        ((LivingEntityRendererAccessor) self).cuffsx_addFeature(new CuffsFeatureRenderer<>(self));
    }
}
