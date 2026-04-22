package org.gloyer057.cuffsx.cuff;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import me.lucko.fabric.api.permissions.v0.Permissions;

import java.util.ArrayList;
import java.util.List;

/**
 * Хранит записи о надевании наручников, которые "вышли" из основного лога (истекли 24ч).
 * TTL в этом файле — 6 часов с момента переноса.
 * Сохраняется в applies.dat в папке мира.
 */
public class CuffApplies {

    public record ApplyEntry(
            long timestamp,
            long transferredAt,
            String applierName,
            String targetName,
            CuffType cuffType,
            Vec3d coords
    ) {}

    // =====================================================================
    // TTL applies.dat — через сколько времени запись удаляется из applies.dat
    // отсчитывается с момента ПЕРЕНОСА из logs.dat, не с момента надевания.
    // Формула: часы * 60 * 60 * 1000
    // Сейчас: 6 часов. Чтобы изменить — поменяй число перед первым *
    // =====================================================================
    private static final long APPLIES_TTL_MS = 6L * 60 * 60 * 1000; // 6 часов

    public static class AppliesState extends PersistentState {
        public static final String KEY = "cuffsx_applies";
        private final List<ApplyEntry> entries = new ArrayList<>();

        public void addEntry(ApplyEntry e) {
            entries.add(e);
            markDirty();
        }

        public List<ApplyEntry> getEntries() {
            long cutoff = System.currentTimeMillis() - APPLIES_TTL_MS;
            boolean removed = entries.removeIf(e -> e.transferredAt() < cutoff);
            if (removed) markDirty();
            return List.copyOf(entries);
        }

        public int clearByTarget(String targetName) {
            int before = entries.size();
            entries.removeIf(e -> e.targetName().equalsIgnoreCase(targetName));
            int removed = before - entries.size();
            if (removed > 0) markDirty();
            return removed;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            long cutoff = System.currentTimeMillis() - APPLIES_TTL_MS;
            entries.removeIf(e -> e.transferredAt() < cutoff);
            NbtList list = new NbtList();
            for (ApplyEntry e : entries) {
                NbtCompound tag = new NbtCompound();
                tag.putLong("timestamp", e.timestamp());
                tag.putLong("transferredAt", e.transferredAt());
                tag.putString("applierName", e.applierName());
                tag.putString("targetName", e.targetName());
                tag.putString("cuffType", e.cuffType().toNbt());
                tag.putDouble("x", e.coords().x);
                tag.putDouble("y", e.coords().y);
                tag.putDouble("z", e.coords().z);
                list.add(tag);
            }
            nbt.put("entries", list);
            return nbt;
        }

        public static AppliesState fromNbt(NbtCompound nbt) {
            AppliesState state = new AppliesState();
            NbtList list = nbt.getList("entries", 10);
            long cutoff = System.currentTimeMillis() - APPLIES_TTL_MS;
            for (int i = 0; i < list.size(); i++) {
                NbtCompound tag = list.getCompound(i);
                long transferredAt = tag.getLong("transferredAt");
                if (transferredAt < cutoff) continue;
                long timestamp = tag.getLong("timestamp");
                String applierName = tag.getString("applierName");
                String targetName = tag.getString("targetName");
                CuffType cuffType = CuffType.fromNbt(tag.getString("cuffType"));
                Vec3d coords = new Vec3d(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
                state.entries.add(new ApplyEntry(timestamp, transferredAt, applierName, targetName, cuffType, coords));
            }
            return state;
        }

        public static AppliesState getOrCreate(MinecraftServer server) {
            return server.getOverworld()
                    .getPersistentStateManager()
                    .getOrCreate(AppliesState::fromNbt, AppliesState::new, KEY);
        }
    }

    private static MinecraftServer currentServer;

    public static void setServer(MinecraftServer server) {
        currentServer = server;
    }

    public static void transfer(CuffLog.LogEntry entry) {
        if (currentServer == null) return;
        long now = System.currentTimeMillis();
        ApplyEntry applyEntry = new ApplyEntry(
                entry.timestamp(), now,
                entry.applierName(), entry.targetName(),
                entry.cuffType(), entry.coords()
        );
        AppliesState.getOrCreate(currentServer).addEntry(applyEntry);

        String msg = String.format(
                "§e[CuffsX] §f24 часа назад наручники были одеты §b%s §fна §b%s",
                entry.applierName(), entry.targetName()
        );
        for (ServerPlayerEntity player : currentServer.getPlayerManager().getPlayerList()) {
            if (Permissions.check(player, "cuffsx.applies", false)) {
                player.sendMessage(Text.literal(msg), false);
            }
        }
    }

    public static List<ApplyEntry> getAll() {
        if (currentServer == null) return List.of();
        return AppliesState.getOrCreate(currentServer).getEntries();
    }

    public static int clearByTarget(String targetName) {
        if (currentServer == null) return 0;
        return AppliesState.getOrCreate(currentServer).clearByTarget(targetName);
    }
}
