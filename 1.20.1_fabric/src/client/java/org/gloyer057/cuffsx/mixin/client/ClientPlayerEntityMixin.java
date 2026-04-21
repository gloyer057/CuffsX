package org.gloyer057.cuffsx.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.input.Input;
import org.gloyer057.cuffsx.client.CuffClientState;
import org.gloyer057.cuffsx.client.CuffsxClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;
import java.util.UUID;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    @Shadow public Input input;

    private int cuffsx_handsBreakCooldown = 0;
    private int cuffsx_legsBreakCooldown  = 0;

    // Предыдущее состояние ПКМ и пробела (для одиночного нажатия)
    private boolean cuffsx_prevUseKey  = false;
    private boolean cuffsx_prevJumping = false;

    private static final Random RNG = new Random();

    @Inject(method = "tick", at = @At("HEAD"))
    private void cuffsx_onTick(CallbackInfo ci) {
        ClientPlayerEntity self = (ClientPlayerEntity)(Object) this;
        UUID uuid = self.getUuid();
        MinecraftClient client = MinecraftClient.getInstance();

        if (cuffsx_handsBreakCooldown > 0) cuffsx_handsBreakCooldown--;
        if (cuffsx_legsBreakCooldown  > 0) cuffsx_legsBreakCooldown--;

        // ПКМ — ломает наручники на руках (одиночное нажатие)
        if (CuffClientState.hasHands(uuid)) {
            boolean usePressed = client.options.useKey.isPressed();
            if (usePressed && !cuffsx_prevUseKey && cuffsx_handsBreakCooldown == 0) {
                CuffsxClient.sendBreakPacket("HANDS");
                cuffsx_handsBreakCooldown = RNG.nextInt(41); // 0-40 тиков
            }
            cuffsx_prevUseKey = usePressed;
        } else {
            cuffsx_prevUseKey = false;
        }

        // Пробел — ломает наручники на ногах (одиночное нажатие)
        if (CuffClientState.hasLegs(uuid)) {
            boolean jumping = input.jumping;
            if (jumping && !cuffsx_prevJumping && cuffsx_legsBreakCooldown == 0) {
                CuffsxClient.sendBreakPacket("LEGS");
                cuffsx_legsBreakCooldown = RNG.nextInt(41);
            }
            cuffsx_prevJumping = jumping;
        } else {
            cuffsx_prevJumping = false;
        }
    }
}
