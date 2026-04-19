package org.gloyer057.cuffsx.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import org.gloyer057.cuffsx.network.CuffBreakPayload;
import org.gloyer057.cuffsx.network.CuffHudPayload;
import org.gloyer057.cuffsx.network.CuffSyncPayload;

import java.util.UUID;

public class CuffsxClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Пакет синхронизации наручников (визуальный рендер)
        ClientPlayNetworking.registerGlobalReceiver(CuffSyncPayload.ID, (client, handler, buf, responseSender) -> {
            UUID uuid = buf.readUuid();
            boolean hands = buf.readBoolean();
            boolean legs  = buf.readBoolean();
            client.execute(() -> CuffClientState.update(uuid, hands, legs));
        });

        // Пакет HUD данных (прочность + прогресс надевания)
        ClientPlayNetworking.registerGlobalReceiver(CuffHudPayload.ID, (client, handler, buf, responseSender) -> {
            int handsHp      = buf.readInt();
            int legsHp       = buf.readInt();
            int applyProgress = buf.readInt();
            client.execute(() -> {
                CuffHudRenderer.handsHp       = handsHp;
                CuffHudRenderer.legsHp        = legsHp;
                CuffHudRenderer.applyProgress = applyProgress;
            });
        });

        // HUD рендер
        HudRenderCallback.EVENT.register((context, tickDelta) ->
            CuffHudRenderer.render(context, tickDelta));
    }

    /**
     * Отправить серверу сигнал о нажатии клавиши ломания наручников.
     * type: "HANDS" или "LEGS"
     */
    public static void sendBreakPacket(String type) {
        var buf = PacketByteBufs.create();
        buf.writeString(type);
        ClientPlayNetworking.send(CuffBreakPayload.ID, buf);
    }
}
