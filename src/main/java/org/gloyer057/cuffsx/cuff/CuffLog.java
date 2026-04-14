package org.gloyer057.cuffsx.cuff;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class CuffLog {

    public enum Action { APPLY, REMOVE }

    public record LogEntry(
            long timestamp,
            Action action,
            String targetName,
            String applierName,
            CuffType cuffType,
            Vec3d coords
    ) {}

    private static final List<LogEntry> entries = new ArrayList<>();
    private static final long TTL_MS = 2L * 60 * 60 * 1000; // 2 hours

    public static void log(Action action, CuffRecord record) {
        entries.add(new LogEntry(
                System.currentTimeMillis(),
                action,
                record.targetName(),
                record.applierName(),
                record.cuffType(),
                record.lockedPos()
        ));
    }

    /** Returns entries from the last 2 hours, pruning expired ones. */
    public static List<LogEntry> getRecent() {
        long cutoff = System.currentTimeMillis() - TTL_MS;
        entries.removeIf(e -> e.timestamp() < cutoff);
        return List.copyOf(entries);
    }
}
