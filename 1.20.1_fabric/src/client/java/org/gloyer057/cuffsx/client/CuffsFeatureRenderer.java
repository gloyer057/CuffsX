package org.gloyer057.cuffsx.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.util.UUID;

@Environment(EnvType.CLIENT)
public class CuffsFeatureRenderer<T extends PlayerEntity, M extends PlayerEntityModel<T>>
        extends FeatureRenderer<T, M> {

    private static final Identifier TEXTURE_HANDS = CuffTextureGenerator.HANDS_ID;
    private static final Identifier TEXTURE_LEGS  = CuffTextureGenerator.LEGS_ID;

    public CuffsFeatureRenderer(FeatureRendererContext<T, M> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                       int light, T entity, float limbAngle, float limbDistance,
                       float tickDelta, float animationProgress, float headYaw, float headPitch) {
        UUID uuid = entity.getUuid();
        boolean hasHands = CuffClientState.hasHands(uuid);
        boolean hasLegs  = CuffClientState.hasLegs(uuid);

        if (!hasHands && !hasLegs) return;

        M model = getContextModel();

        if (hasHands) {
            renderPart(matrices, vertexConsumers, light, model.rightArm, TEXTURE_HANDS);
            renderPart(matrices, vertexConsumers, light, model.leftArm, TEXTURE_HANDS);
        }

        if (hasLegs) {
            renderPart(matrices, vertexConsumers, light, model.rightLeg, TEXTURE_LEGS);
            renderPart(matrices, vertexConsumers, light, model.leftLeg, TEXTURE_LEGS);
        }
    }

    private void renderPart(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                             int light, ModelPart part, Identifier texture) {
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(texture));
        part.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1f);
    }
}
