package org.gloyer057.cuffsx.command;

import com.mojang.brigadier.CommandDispatcher;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.gloyer057.cuffsx.cuff.CuffLog;
import org.gloyer057.cuffsx.cuff.CuffManager;
import org.gloyer057.cuffsx.cuff.CuffRecord;
import org.gloyer057.cuffsx.cuff.CuffState;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

import static net.minecraft.server.command.CommandManager.literal;

public class CuffsxCommand {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            literal("cuffsx")
                .then(literal("reload")
                    .executes(ctx -> {
                        ServerCommandSource source = ctx.getSource();
                        if (!Permissions.check(source, "cuffsx.reload", 4)) {
                            source.sendError(Text.literal("У вас нет прав для выполнения этой команды."));
                            return 0;
                        }
                        CuffManager.setEnabled(true);
                        source.sendFeedback(() -> Text.literal("Конфигурация cuffsx перезагружена."), false);
                        return 1;
                    }))
                .then(literal("enable")
                    .executes(ctx -> {
                        ServerCommandSource source = ctx.getSource();
                        if (!Permissions.check(source, "cuffsx.enable", 4)) {
                            source.sendError(Text.literal("У вас нет прав для выполнения этой команды."));
                            return 0;
                        }
                        CuffManager.setEnabled(true);
                        source.sendFeedback(() -> Text.literal("Наручники включены."), false);
                        return 1;
                    }))
                .then(literal("disable")
                    .executes(ctx -> {
                        ServerCommandSource source = ctx.getSource();
                        if (!Permissions.check(source, "cuffsx.disable", 4)) {
                            source.sendError(Text.literal("У вас нет прав для выполнения этой команды."));
                            return 0;
                        }
                        CuffManager.setEnabled(false);
                        source.sendFeedback(() -> Text.literal("Наручники отключены."), false);
                        return 1;
                    }))
                .then(literal("list")
                    .executes(ctx -> {
                        ServerCommandSource source = ctx.getSource();
                        if (!Permissions.check(source, "cuffsx.list", 4)) {
                            source.sendError(Text.literal("У вас нет прав для выполнения этой команды."));
                            return 0;
                        }
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
                    .executes(ctx -> {
                        ServerCommandSource source = ctx.getSource();
                        if (!Permissions.check(source, "cuffsx.log", 4)) {
                            source.sendError(Text.literal("У вас нет прав для выполнения этой команды."));
                            return 0;
                        }
                        List<CuffLog.LogEntry> recent = CuffLog.getRecent();
                        if (recent.isEmpty()) {
                            source.sendFeedback(
                                () -> Text.literal("Нет взаимодействий за последние 2 часа."), false);
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
        );
    }
}
