package org.gloyer057.cuffsx.network;

import net.minecraft.util.Identifier;

/**
 * Пакет: сервер отправляет клиенту данные для HUD.
 * handsHp, legsHp: прочность 0-100, -1 если не надеты
 * leashTarget: UUID ведущего (или null)
 * applyProgress: прогресс надевания 0-100, -1 если не идёт
 */
public class CuffHudPayload {
    public static final Identifier ID = new Identifier("cuffsx", "cuff_hud");
}
