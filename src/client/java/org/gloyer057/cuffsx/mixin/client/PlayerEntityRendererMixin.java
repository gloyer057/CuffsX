package org.gloyer057.cuffsx.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import org.gloyer057.cuffsx.client.CuffsFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin
        extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {

    // Конструктор нужен для компиляции extends LivingEntityRenderer
    protected PlayerEntityRendererMixin(net.minecraft.client.render.entity.EntityRendererFactory.Context ctx,
                                         PlayerEntityModel<AbstractClientPlayerEntity> model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cuffsx_addFeatureRenderer(CallbackInfo ci) {
        this.addFeature(new CuffsFeatureRenderer<>(this));
    }
}
