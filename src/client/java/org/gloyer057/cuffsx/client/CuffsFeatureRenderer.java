package org.gloyer057.cuffsx.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.*;
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

/**
 * Рендерит кольца наручников поверх модели игрока.
 */
@Environment(EnvType.CLIENT)
public class CuffsFeatureRenderer<T extends PlayerEntity, M extends PlayerEntityModel<T>>
        extends FeatureRenderer<T, M> {

    private static final Identifier TEXTURE_HANDS =
            new Identifier("cuffsx", "textures/entity/handcuffs_hands.png");
    private static final Identifier TEXTURE_LEGS =
            new Identifier("cuffsx", "textures/entity/handcuffs_legs.png");

    // Кольца для рук (запястья)
    private final ModelPart ringRightArm;
    private final ModelPart ringLeftArm;
    // Кольца для ног (щиколотки)
    private final ModelPart ringRightLeg;
    private final ModelPart ringLeftLeg;

    public CuffsFeatureRenderer(FeatureRendererContext<T, M> context) {
        super(context);

        // Тонкое кольцо: 4x2x4 пикселя, смещение вниз по руке/ноге
        ringRightArm = buildRing(-2, 10, -2, 4, 2, 4);
        ringLeftArm  = buildRing(-2, 10, -2, 4, 2, 4);
        ringRightLeg = buildRing(-2, 10, -2, 4, 2, 4);
        ringLeftLeg  = buildRing(-2, 10, -2, 4, 2, 4);
    }

    private ModelPart buildRing(int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
        ModelPartData data = new ModelData().getRoot();
        data.addChild("ring",
                ModelPartBuilder.create().uv(0, 0)
                        .cuboid(x, y, z, sizeX, sizeY, sizeZ, new Dilation(0.25f)),
                ModelTransform.NONE);
        return data.createPart(16, 16).getChild("ring");
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                       int light, T entity, float limbAngle, float limbDistance,
                       float tickDelta, float animationProgress, float headYaw, float headPitch) {

        UUID uuid = entity.getUuid();
        boolean hands = CuffClientState.hasHands(uuid);
        boolean legs  = CuffClientState.hasLegs(uuid);

        if (!hands && !legs) return;

        M model = getContextModel();

        if (hands) {
            VertexConsumer vc = vertexConsumers.getBuffer(
                    RenderLayer.getEntityCutoutNoCull(TEXTURE_HANDS));
            renderOnPart(matrices, vc, light, model.rightArm, ringRightArm);
            renderOnPart(matrices, vc, light, model.leftArm,  ringLeftArm);
        }

        if (legs) {
            VertexConsumer vc = vertexConsumers.getBuffer(
                    RenderLayer.getEntityCutoutNoCull(TEXTURE_LEGS));
            renderOnPart(matrices, vc, light, model.rightLeg, ringRightLeg);
            renderOnPart(matrices, vc, light, model.leftLeg,  ringLeftLeg);
        }
    }

    private void renderOnPart(MatrixStack matrices, VertexConsumer vc, int light,
                               ModelPart bodyPart, ModelPart ring) {
        matrices.push();
        bodyPart.rotate(matrices);
        ring.render(matrices, vc, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }
}
