package org.gloyer057.cuffsx.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.gloyer057.cuffsx.cuff.CuffDurability;

@Environment(EnvType.CLIENT)
public class CuffHudRenderer {

    public static int handsHp = -1;
    public static int legsHp  = -1;
    public static int applyProgress  = -1;
    public static int removeProgress = -1;

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        TextRenderer tr = client.textRenderer;
        int screenH = client.getWindow().getScaledHeight();
        int screenW = client.getWindow().getScaledWidth();

        int y = screenH - 60;
        if (handsHp >= 0) {
            String text = "Руки: " + handsHp + "/" + CuffDurability.MAX_HP;
            context.drawTextWithShadow(tr, text, 8, y, hpColor(handsHp));
            y += 12;
        }
        if (legsHp >= 0) {
            String text = "Ноги: " + legsHp + "/" + CuffDurability.MAX_HP;
            context.drawTextWithShadow(tr, text, 8, y, hpColor(legsHp));
        }

        int progress = applyProgress >= 0 ? applyProgress : removeProgress;
        if (progress >= 0) {
            String label = applyProgress >= 0 ? "Надевание" : "Снятие";
            String bar = buildBar(progress);
            String text = bar + " " + label + " " + progress + "%";
            int textW = tr.getWidth(text);
            context.drawTextWithShadow(tr, text, (screenW - textW) / 2, screenH / 2 + 30, 0xFFFFAA00);
        }
    }

    private static int hpColor(int hp) {
        if (hp > 60) return 0xFF55FF55;
        if (hp > 30) return 0xFFFFAA00;
        return 0xFFFF5555;
    }

    private static String buildBar(int percent) {
        int filled = percent / 10;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 10; i++) sb.append(i < filled ? "#" : ".");
        sb.append("]");
        return sb.toString();
    }
}
