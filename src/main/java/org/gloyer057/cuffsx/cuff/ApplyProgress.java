package org.gloyer057.cuffsx.cuff;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Отслеживает прогресс надевания наручников.
 * Надевающий должен держать ПКМ 5 секунд (100 тиков).
 * Цель не должна двигаться более чем на 0.1 блока за тик.
 */
public class ApplyProgress {

    public static final int REQUIRED_TICKS = 100; // 5 секунд

    public record Entry(
        UUID targetUuid,
        CuffType cuffType,
        int ticksHeld,
        Vec3d targetPosStart
    ) {}

    // applierUUID -> Entry
    private static final Map<UUID, Entry> progress = new ConcurrentHashMap<>();

    public static void start(ServerPlayerEntity applier, ServerPlayerEntity target, CuffType type) {
        progress.put(applier.getUuid(),
            new Entry(target.getUuid(), type, 0, target.getPos()));
    }

    public static void cancel(UUID applierUuid) {
        progress.remove(applierUuid);
    }

    public static Entry get(UUID applierUuid) {
        return progress.get(applierUuid);
    }

    public static boolean isActive(UUID applierUuid) {
        return progress.containsKey(applierUuid);
    }

    public static TickResult tick(UUID applierUuid, Vec3d currentTargetPos) {
        Entry e = progress.get(applierUuid);
        if (e == null) return TickResult.NOT_ACTIVE;

        // Проверяем смещение от позиции прошлого тика
        if (currentTargetPos.distanceTo(e.targetPosStart()) > 1.0) {
            progress.remove(applierUuid);
            return TickResult.CANCELLED_MOVED;
        }

        int newTicks = e.ticksHeld() + 1;
        if (newTicks >= REQUIRED_TICKS) {
            progress.remove(applierUuid);
            return TickResult.COMPLETED;
        }

        // Обновляем позицию каждый тик — проверяем движение за 1 тик, не от старта
        progress.put(applierUuid, new Entry(e.targetUuid(), e.cuffType(), newTicks, currentTargetPos));
        return TickResult.IN_PROGRESS;
    }

    public static int getProgress(UUID applierUuid) {
        Entry e = progress.get(applierUuid);
        if (e == null) return -1;
        return e.ticksHeld() * 100 / REQUIRED_TICKS;
    }

    public enum TickResult {
        NOT_ACTIVE, IN_PROGRESS, COMPLETED, CANCELLED_MOVED
    }
}
