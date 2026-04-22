package org.gloyer057.cuffsx.cuff;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;

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

    // =====================================================================
    // TTL основного лога (logs.dat) — через сколько времени запись "истекает"
    // и переносится в applies.dat (для APPLY) или удаляется (для REMOVE).
    // Формула: часы * 60 * 60 * 1000
    // Сейчас: 24 часа. Чтобы изменить — поменяй число перед первым *
    // =====================================================================
    static final long TTL_MS = 24L * 60 * 60 * 1000; // 24 часа

    public static class LogState extends PersistentState {
        public static final String KEY = "cuffsx_log";
        final List<LogEntry> entries = new ArrayList<>();

        public void addEntry(LogEntry e) {
            entries.add(e);
            markDirty();
        }

        /**
         * Удаляет истёкшие записи и возвращает их отдельным списком.
         * Вызывающий код (CuffLog) сам решает что делать с истёкшими.
         */
        public List<LogEntry> pruneExpired() {
            long cutoff = System.currentTimeMillis() - TTL_MS;
            List<LogEntry> expired = entries.stream()
                    .filter(e -> e.timestamp() < cutoff)
                    .toList();
            if (!expired.isEmpty()) {
                entries.removeAll(expired);
                markDirty();
            }
            return expired;
        }

        public List<LogEntry> getEntries() {
            return List.copyOf(entries);
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            // При сохранении просто удаляем истёкшие без уведомлений
            long cutoff = System.currentTimeMillis() - TTL_MS;
            entries.removeIf(e -> e.timestamp() < cutoff);
            NbtList list = new NbtList();
            for (LogEntry e : entries) {
                NbtCompound tag = new NbtCompound();
                tag.putLong("timestamp", e.timestamp());
                tag.putString("action", e.action().name());
                tag.putString("targetName", e.targetName());
                tag.putString("applierName", e.applierName());
                tag.putString("cuffType", e.cuffType().toNbt());
                tag.putDouble("x", e.coords().x);
                tag.putDouble("y", e.coords().y);
                tag.putDouble("z", e.coords().z);
                list.add(tag);
            }
            nbt.put("entries", list);
            return nbt;
        }

        public static LogState fromNbt(NbtCompound nbt) {
            LogState state = new LogState();
            NbtList list = nbt.getList("entries", 10);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound tag = list.getCompound(i);
                long ts = tag.getLong("timestamp");
                Action action = Action.valueOf(tag.getString("action"));
                String targetName = tag.getString("targetName");
                String applierName = tag.getString("applierName");
                CuffType cuffType = CuffType.fromNbt(tag.getString("cuffType"));
                Vec3d coords = new Vec3d(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
                state.entries.add(new LogEntry(ts, action, targetName, applierName, cuffType, coords));
            }
            return state;
        }

        public static LogState getOrCreate(MinecraftServer server) {
            return server.getOverworld()
                    .getPersistentStateManager()
                    .getOrCreate(LogState::fromNbt, LogState::new, KEY);
        }
    }

    private static MinecraftServer currentServer;

    public static void setServer(MinecraftServer server) {
        currentServer = server;
    }

    public static void log(Action action, CuffRecord record) {
        if (currentServer == null) return;
        LogEntry entry = new LogEntry(
                System.currentTimeMillis(), action,
                record.targetName(), record.applierName(),
                record.cuffType(), record.lockedPos()
        );
        LogState.getOrCreate(currentServer).addEntry(entry);
    }

    /**
     * Возвращает актуальные записи за последние 24 часа.
     * Истёкшие APPLY-записи переносятся в CuffApplies, REMOVE — удаляются.
     */
    public static List<LogEntry> getRecent() {
        if (currentServer == null) return List.of();
        LogState state = LogState.getOrCreate(currentServer);
        // Забираем истёкшие записи и обрабатываем здесь (не внутри LogState)
        List<LogEntry> expired = state.pruneExpired();
        for (LogEntry e : expired) {
            if (e.action() == Action.APPLY) {
                CuffApplies.transfer(e); // CuffApplies виден здесь — тот же пакет
            }
        }
        return state.getEntries();
    }
}
