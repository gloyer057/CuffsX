package org.gloyer057.cuffsx.network;

import net.minecraft.util.Identifier;

/**
 * Пакет: клиент сообщает серверу что игрок нажал клавишу ломания наручников.
 * type: "HANDS" или "LEGS"
 */
public class CuffBreakPayload {
    public static final Identifier ID = new Identifier("cuffsx", "cuff_break");
}
