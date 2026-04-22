package org.gloyer057.cuffsx.cuff;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CuffDurability {

    public static final int MAX_HP = 250;

    private static final Map<UUID, Integer> handsHp = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> legsHp  = new ConcurrentHashMap<>();

    public static void initHands(UUID uuid) { handsHp.put(uuid, MAX_HP); }
    public static void initLegs(UUID uuid)  { legsHp.put(uuid, MAX_HP); }

    public static void removeHands(UUID uuid) { handsHp.remove(uuid); }
    public static void removeLegs(UUID uuid)  { legsHp.remove(uuid); }

    public static int getHandsHp(UUID uuid) { return handsHp.getOrDefault(uuid, -1); }
    public static int getLegsHp(UUID uuid)  { return legsHp.getOrDefault(uuid, -1); }

    /** Returns true if cuffs broke (hp <= 0). Damage is always 1 per click. */
    public static boolean damageHands(UUID uuid) {
        int hp = handsHp.getOrDefault(uuid, -1);
        if (hp < 0) return false;
        hp -= 1;
        if (hp <= 0) { handsHp.remove(uuid); return true; }
        handsHp.put(uuid, hp);
        return false;
    }

    public static boolean damageLegs(UUID uuid) {
        int hp = legsHp.getOrDefault(uuid, -1);
        if (hp < 0) return false;
        hp -= 1;
        if (hp <= 0) { legsHp.remove(uuid); return true; }
        legsHp.put(uuid, hp);
        return false;
    }
}
