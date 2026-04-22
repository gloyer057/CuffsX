package org.gloyer057.cuffsx.cuff;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RemoveProgress {

    public record Entry(UUID applierUuid, UUID targetUuid, CuffType cuffType) {}

    public enum TickResult { IN_PROGRESS, COMPLETED }

    private static final Map<UUID, Entry> active = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ticks = new ConcurrentHashMap<>();

    private static final int TOTAL_TICKS = 40; // 2 seconds

    public static void start(ServerPlayerEntity applier, ServerPlayerEntity target, CuffType type) {
        active.put(applier.getUuid(), new Entry(applier.getUuid(), target.getUuid(), type));
        ticks.put(applier.getUuid(), 0);
    }

    public static boolean isActive(UUID applierUuid) {
        return active.containsKey(applierUuid);
    }

    public static Entry get(UUID applierUuid) {
        return active.get(applierUuid);
    }

    public static void cancel(UUID applierUuid) {
        active.remove(applierUuid);
        ticks.remove(applierUuid);
    }

    public static int getProgress(UUID applierUuid) {
        int t = ticks.getOrDefault(applierUuid, 0);
        return (int) ((t / (double) TOTAL_TICKS) * 100);
    }

    public static TickResult tick(UUID applierUuid) {
        int t = ticks.getOrDefault(applierUuid, 0) + 1;
        ticks.put(applierUuid, t);
        if (t >= TOTAL_TICKS) {
            active.remove(applierUuid);
            ticks.remove(applierUuid);
            return TickResult.COMPLETED;
        }
        return TickResult.IN_PROGRESS;
    }
}
