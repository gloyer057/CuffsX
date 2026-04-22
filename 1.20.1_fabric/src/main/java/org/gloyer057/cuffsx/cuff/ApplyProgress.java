package org.gloyer057.cuffsx.cuff;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ApplyProgress {

    public record Entry(UUID applierUuid, UUID targetUuid, CuffType cuffType, Vec3d startPos, int startTick) {}

    public enum TickResult { IN_PROGRESS, COMPLETED, CANCELLED_MOVED }

    private static final Map<UUID, Entry> active = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ticks = new ConcurrentHashMap<>();

    private static final int TOTAL_TICKS = 60; // 3 seconds
    private static final double MAX_MOVE = 0.5;

    public static void start(ServerPlayerEntity applier, ServerPlayerEntity target, CuffType type) {
        Entry entry = new Entry(applier.getUuid(), target.getUuid(), type, target.getPos(), 0);
        active.put(applier.getUuid(), entry);
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

    public static TickResult tick(UUID applierUuid, Vec3d currentTargetPos) {
        Entry entry = active.get(applierUuid);
        if (entry == null) return TickResult.CANCELLED_MOVED;

        if (currentTargetPos.distanceTo(entry.startPos()) > MAX_MOVE) {
            active.remove(applierUuid);
            ticks.remove(applierUuid);
            return TickResult.CANCELLED_MOVED;
        }

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
