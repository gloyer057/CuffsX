package org.gloyer057.cuffsx.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Рендерит HUD наручников:
 * - Прочность наручников на руках/ногах (левый нижний угол)
 * - Прогресс надевания (actionbar)
 */
@Environment(EnvType.CLIENT)
public class CuffHudRenderer {

    // Данные от сервера
    public static int handsHp = -1;
    public static int legsHp  = -1;
    public static int applyProgress  = -1; // прогресс надевания
    public static int removeProgress = -1; // прогресс снятия

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        TextRenderer tr = client.textRenderer;
        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();

        // Прочность наручников — левый нижний угол
        int y = screenH - 60;
        if (handsHp >= 0) {
            String text = "✋ " + handsHp + "%";
            int color = hpColor(handsHp);
            context.drawTextWithShadow(tr, Text.literal(text), 8, y, color);
            y += 12;
        }
        if (legsHp >= 0) {
            String text = "🦵 " + legsHp + "%";
            int color = hpColor(legsHp);
            context.drawTextWithShadow(tr, Text.literal(text), 8, y, color);
        }

        // Прогресс надевания
        if (applyProgress >= 0) {
            String bar = buildBar(applyProgress);
            String text = "Надевание: " + bar + " " + applyProgress + "%";
            int textW = tr.getWidth(text);
            context.drawTextWithShadow(tr, Text.literal(text),
                (screenW - textW) / 2, screenH / 2 + 30, 0xFFFFAA00);
        }

        // Прогресс снятия
        if (removeProgress >= 0) {
            String bar = buildBar(removeProgress);
            String text = "Снятие: " + bar + " " + removeProgress + "%";
            int textW = tr.getWidth(text);
            context.drawTextWithShadow(tr, Text.literal(text),
                (screenW - textW) / 2, screenH / 2 + 30, 0xFFFFAA00);
        }
    }

    private static int hpColor(int hp) {
        if (hp > 60) return 0xFF55FF55; // зелёный
        if (hp > 30) return 0xFFFFAA00; // оранжевый
        return 0xFFFF5555;              // красный
    }

    private static String buildBar(int percent) {
        int filled = percent / 10;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 10; i++) sb.append(i < filled ? "█" : "░");
        sb.append("]");
        return sb.toString();
    }
}
