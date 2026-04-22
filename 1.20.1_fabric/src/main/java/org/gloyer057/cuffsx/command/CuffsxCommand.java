package org.gloyer057.cuffsx.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.gloyer057.cuffsx.cuff.*;
import org.gloyer057.cuffsx.item.ModItems;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class CuffsxCommand {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private static Predicate<ServerCommandSource> perm(String node) {
        return Permissions.require(node, 4);
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            literal("cuffsx")
                .then(literal("enable")
                    .requires(perm("cuffsx.enable"))
                    .executes(ctx -> {
                        CuffManager.setEnabled(true);
                        ctx.getSource().sendFeedback(() -> Text.literal("Наручники включены."), false);
                        return 1;
                    }))
                .then(literal("disable")
                    .requires(perm("cuffsx.disable"))
                    .executes(ctx -> {
                        CuffManager.setEnabled(false);
                        ctx.getSource().sendFeedback(() -> Text.literal("Наручники отключены."), false);
                        return 1;
                    }))
                .then(literal("list")
                    .requires(perm("cuffsx.list"))
                    .executes(ctx -> {
                        ServerCommandSource source = ctx.getSource();
                        CuffState state = CuffState.getOrCreate(source.getServer());
                        Collection<CuffRecord> all = state.getAllRecords();
                        if (all.isEmpty()) {
                            source.sendFeedback(() -> Text.literal("Нет игроков в наручниках."), false);
                        } else {
                            long now = System.currentTimeMillis();
                            for (CuffRecord r : all) {
                                long elapsed = (now - r.timestamp()) / 1000;
                                String msg = String.format("%s | %s | надел(а): %s | %d сек. назад",
                                        r.targetName(), r.cuffType(), r.applierName(), elapsed);
                                source.sendFeedback(() -> Text.literal(msg), false);
                            }
                        }
                        return 1;
                    }))
                .then(literal("log")
                    .requires(perm("cuffsx.log"))
                    .executes(ctx -> {
                        ServerCommandSource source = ctx.getSource();
                        List<CuffLog.LogEntry> recent = CuffLog.getRecent();
                        if (recent.isEmpty()) {
                            source.sendFeedback(() -> Text.literal("Нет взаимодействий за последние 24 часа."), false);
                        } else {
                            for (CuffLog.LogEntry e : recent) {
                                String action = e.action() == CuffLog.Action.APPLY ? "APPLY" : "REMOVE";
                                String time = TIME_FMT.format(Instant.ofEpochMilli(e.timestamp()));
                                String msg = String.format("%s | %s → %s | %s | (%.1f, %.1f, %.1f) | %s",
                                        action, e.applierName(), e.targetName(), e.cuffType(),
                                        e.coords().x, e.coords().y, e.coords().z, time);
                                source.sendFeedback(() -> Text.literal(msg), false);
                            }
                        }
                        return 1;
                    }))
                .then(literal("remove")
                    .requires(perm("cuffsx.remove"))
                    .then(argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerCommandSource source = ctx.getSource();
                            MinecraftServer server = source.getServer();
                            String playerName = StringArgumentType.getString(ctx, "player");

                            ServerPlayerEntity onlineTarget = server.getPlayerManager().getPlayer(playerName);

                            if (onlineTarget != null) {
                                CuffState state = CuffState.getOrCreate(server);
                                Set<CuffRecord> records = state.getRecords(onlineTarget.getUuid());
                                if (records.isEmpty()) {
                                    source.sendError(Text.literal("§cУ игрока " + playerName + " нет наручников."));
                                    return 0;
                                }
                                for (CuffType type : CuffType.values()) {
                                    if (state.hasCuff(onlineTarget.getUuid(), type)) {
                                        CuffManager.removeCuffsForced(onlineTarget, type);
                                        ItemStack newStack = new ItemStack(
                                                type == CuffType.HANDS ? ModItems.HANDCUFFS_HANDS : ModItems.HANDCUFFS_LEGS);
                                        try {
                                            ServerPlayerEntity executor = source.getPlayer();
                                            if (!executor.getInventory().insertStack(newStack))
                                                executor.dropItem(newStack, false);
                                        } catch (Exception ignored) {}
                                    }
                                }
                                source.sendFeedback(() -> Text.literal("§aНаручники сняты с " + playerName + "."), false);
                                return 1;
                            }

                            // Офлайн-игрок
                            UUID offlineUuid = resolveOfflineUuid(server, playerName);
                            if (offlineUuid == null) {
                                source.sendError(Text.literal("§cИгрок " + playerName + " не найден."));
                                return 0;
                            }

                            CuffState state = CuffState.getOrCreate(server);
                            Set<CuffRecord> records = state.getRecords(offlineUuid);
                            if (records.isEmpty()) {
                                source.sendError(Text.literal("§cУ игрока " + playerName + " нет наручников."));
                                return 0;
                            }

                            for (CuffType type : CuffType.values()) {
                                if (state.hasCuff(offlineUuid, type)) {
                                    CuffRecord removedRecord = state.getRecords(offlineUuid).stream()
                                            .filter(r -> r.cuffType() == type).findFirst().orElse(null);
                                    state.removeRecord(offlineUuid, type);
                                    if (removedRecord != null) CuffLog.log(CuffLog.Action.REMOVE, removedRecord);
                                    if (type == CuffType.HANDS) CuffDurability.removeHands(offlineUuid);
                                    else CuffDurability.removeLegs(offlineUuid);
                                    LeashManager.detachByLeashed(offlineUuid);
                                    ItemStack newStack = new ItemStack(
                                            type == CuffType.HANDS ? ModItems.HANDCUFFS_HANDS : ModItems.HANDCUFFS_LEGS);
                                    try {
                                        ServerPlayerEntity executor = source.getPlayer();
                                        if (!executor.getInventory().insertStack(newStack))
                                            executor.dropItem(newStack, false);
                                    } catch (Exception ignored) {}
                                }
                            }
                            source.sendFeedback(() -> Text.literal(
                                    "§aНаручники сняты с офлайн-игрока " + playerName + "."), false);
                            return 1;
                        })))
                .then(literal("applies")
                    .requires(perm("cuffsx.applies"))
                    .then(literal("info")
                        .executes(ctx -> {
                            ServerCommandSource source = ctx.getSource();
                            List<CuffApplies.ApplyEntry> entries = CuffApplies.getAll();
                            if (entries.isEmpty()) {
                                source.sendFeedback(() -> Text.literal("§7Нет записей в applies."), false);
                            } else {
                                source.sendFeedback(() -> Text.literal("§e=== Applies ==="), false);
                                for (CuffApplies.ApplyEntry e : entries) {
                                    String time = TIME_FMT.format(Instant.ofEpochMilli(e.timestamp()));
                                    String msg = String.format("§b%s §f→ §b%s §f| %s | %s",
                                            e.applierName(), e.targetName(), e.cuffType(), time);
                                    source.sendFeedback(() -> Text.literal(msg), false);
                                }
                            }
                            return 1;
                        }))
                    .then(literal("clear")
                        .then(argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                ServerCommandSource source = ctx.getSource();
                                String playerName = StringArgumentType.getString(ctx, "player");
                                int removed = CuffApplies.clearByTarget(playerName);
                                if (removed == 0) {
                                    source.sendFeedback(() -> Text.literal(
                                            "§7Нет записей applies для игрока " + playerName + "."), false);
                                } else {
                                    source.sendFeedback(() -> Text.literal(
                                            "§aУдалено " + removed + " applies-записей для " + playerName + "."), false);
                                }
                                return 1;
                            }))))
        );
    }

    private static UUID resolveOfflineUuid(MinecraftServer server, String playerName) {
        com.mojang.authlib.GameProfile profile = server.getUserCache() != null
                ? server.getUserCache().findByName(playerName).orElse(null)
                : null;
        return profile != null ? profile.getId() : null;
    }
}
