package org.gloyer057.cuffsx.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import org.gloyer057.cuffsx.network.CuffBreakPayload;
import org.gloyer057.cuffsx.network.CuffHudPayload;
import org.gloyer057.cuffsx.network.CuffSyncPayload;

import java.util.Random;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class CuffsxClient implements ClientModInitializer {

    private static int handsBreakCooldown = 0;
    private static int legsBreakCooldown  = 0;
    private static boolean prevUseKey  = false;
    private static boolean prevJumping = false;
    private static final Random RNG = new Random();

    @Override
    public void onInitializeClient() {
        // Синхронизация наручников
        ClientPlayNetworking.registerGlobalReceiver(CuffSyncPayload.ID, (client, handler, buf, responseSender) -> {
            UUID uuid = buf.readUuid();
            boolean hands = buf.readBoolean();
            boolean legs  = buf.readBoolean();
            client.execute(() -> CuffClientState.update(uuid, hands, legs));
        });

        // HUD данные
        ClientPlayNetworking.registerGlobalReceiver(CuffHudPayload.ID, (client, handler, buf, responseSender) -> {
            int handsHp      = buf.readInt();
            int legsHp       = buf.readInt();
            int applyProg    = buf.readInt();
            int removeProg   = buf.readInt();
            client.execute(() -> {
                CuffHudRenderer.handsHp       = handsHp;
                CuffHudRenderer.legsHp        = legsHp;
                CuffHudRenderer.applyProgress  = applyProg;
                CuffHudRenderer.removeProgress = removeProg;
            });
        });

        // HUD рендер
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
            (drawContext, tickDelta) -> CuffHudRenderer.render(drawContext, tickDelta));

        // Регистрируем сгенерированные текстуры наручников
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(
            client -> CuffTextureGenerator.register());

        // Тик для ломания наручников
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            UUID uuid = client.player.getUuid();

            if (handsBreakCooldown > 0) handsBreakCooldown--;
            if (legsBreakCooldown  > 0) legsBreakCooldown--;

            // ПКМ — ломает наручники на руках
            if (CuffClientState.hasHands(uuid)) {
                boolean usePressed = client.options.useKey.isPressed();
                if (usePressed && !prevUseKey && handsBreakCooldown == 0) {
                    sendBreakPacket("HANDS");
                    handsBreakCooldown = RNG.nextInt(41);
                }
                prevUseKey = usePressed;
            } else prevUseKey = false;

            // Пробел — ломает наручники на ногах
            if (CuffClientState.hasLegs(uuid)) {
                boolean jumping = client.options.jumpKey.isPressed();
                if (jumping && !prevJumping && legsBreakCooldown == 0) {
                    sendBreakPacket("LEGS");
                    legsBreakCooldown = RNG.nextInt(41);
                }
                prevJumping = jumping;
            } else prevJumping = false;
        });
    }

    public static void sendBreakPacket(String type) {
        var buf = PacketByteBufs.create();
        buf.writeString(type);
        ClientPlayNetworking.send(CuffBreakPayload.ID, buf);
    }
}
