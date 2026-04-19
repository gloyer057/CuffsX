package org.gloyer057.cuffsx.cuff;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранит прочность наручников для каждого игрока.
 * Прочность: 0-100. При достижении 0 — наручники ломаются.
 * HANDS ломаются клавишами движения (W/A/S/D), LEGS — пробелом.
 */
public class CuffDurability {

    public static final int MAX_HP = 250;
    // Урон за тик при нажатой клавише
    private static final float DAMAGE_PER_TICK = 1.0f;

    // UUID -> прочность рук (0-100)
    private static final Map<UUID, Float> handsHp = new ConcurrentHashMap<>();
    // UUID -> прочность ног (0-100)
    private static final Map<UUID, Float> legsHp  = new ConcurrentHashMap<>();

    public static void initHands(UUID uuid) { handsHp.put(uuid, (float) MAX_HP); }
    public static void initLegs(UUID uuid)  { legsHp.put(uuid, (float) MAX_HP); }

    public static void removeHands(UUID uuid) { handsHp.remove(uuid); }
    public static void removeLegs(UUID uuid)  { legsHp.remove(uuid); }

    public static int getHandsHp(UUID uuid) {
        Float v = handsHp.get(uuid);
        return v == null ? -1 : Math.round(v);
    }

    public static int getLegsHp(UUID uuid) {
        Float v = legsHp.get(uuid);
        return v == null ? -1 : Math.round(v);
    }

    /**
     * Наносит урон наручникам на руках. Возвращает true если сломались.
     */
    public static boolean damageHands(UUID uuid) {
        Float hp = handsHp.get(uuid);
        if (hp == null) return false;
        hp -= DAMAGE_PER_TICK;
        if (hp <= 0) {
            handsHp.remove(uuid);
            return true;
        }
        handsHp.put(uuid, hp);
        return false;
    }

    /**
     * Наносит урон наручникам на ногах. Возвращает true если сломались.
     */
    public static boolean damageLegs(UUID uuid) {
        Float hp = legsHp.get(uuid);
        if (hp == null) return false;
        hp -= DAMAGE_PER_TICK;
        if (hp <= 0) {
            legsHp.remove(uuid);
            return true;
        }
        legsHp.put(uuid, hp);
        return false;
    }
}
