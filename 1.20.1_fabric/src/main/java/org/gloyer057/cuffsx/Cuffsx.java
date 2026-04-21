package org.gloyer057.cuffsx;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import me.lucko.fabric.api.permissions.v0.Permissions;
import org.gloyer057.cuffsx.command.CuffsxCommand;
import org.gloyer057.cuffsx.cuff.*;
import org.gloyer057.cuffsx.item.ModItemGroups;
import org.gloyer057.cuffsx.item.ModItems;
import org.gloyer057.cuffsx.network.CuffBreakPayload;
import org.gloyer057.cuffsx.network.CuffHudPayload;

import java.util.UUID;

public class Cuffsx implements ModInitializer {

    // Кулдаун на уровне callback — UUID -> время последнего действия в мс
    private static final java.util.Map<UUID, Long> callbackCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CALLBACK_COOLDOWN_MS = 500L; // 500мс = ~10 тиков

    @Override
    public void onInitialize() {
        ModItems.register();
        ModItemGroups.register();

        // Надевание/снятие/поводок через UseEntityCallback
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hand != net.minecraft.util.Hand.MAIN_HAND) return ActionResult.SUCCESS;
            if (!(player instanceof ServerPlayerEntity applier)) return ActionResult.PASS;
            if (!(entity instanceof ServerPlayerEntity target)) return ActionResult.PASS;
            if (applier == target) return ActionResult.PASS;

            // Кулдаун — блокируем повторные вызовы в течение 500мс
            long nowMs = System.currentTimeMillis();
            Long lastMs = callbackCooldown.get(applier.getUuid());
            if (lastMs != null && nowMs - lastMs < CALLBACK_COOLDOWN_MS) return ActionResult.SUCCESS;
            callbackCooldown.put(applier.getUuid(), nowMs);

            // Поводок
            if (applier.getMainHandStack().isOf(Items.LEAD)) {
                if (!CuffManager.isCuffed(applier) && CuffManager.isCuffed(target)) {
                    if (LeashManager.isLeashed(target.getUuid())) {
                        UUID holderUuid = LeashManager.getHolder(target.getUuid());
                        if (holderUuid != null && holderUuid.equals(applier.getUuid())) {
                            // Только владелец поводка может снять
                            LeashManager.detachByLeashed(target.getUuid());
                            CuffState leashState = CuffState.getOrCreate(applier.getServer());
                            for (CuffType t : CuffType.values()) {
                                if (leashState.hasCuff(target.getUuid(), t)) {
                                    leashState.updateLockedPos(target.getUuid(), t, target.getPos());
                                }
                            }
                            applier.sendMessage(Text.literal("§7Поводок снят."), true);
                        } else {
                            // Другой игрок пытается одеть поводок — уже есть
                            applier.sendMessage(Text.literal("§cНа " + target.getName().getString() + " уже одет поводок."), true);
                        }
                    } else {
                        LeashManager.attach(applier, target);
                        applier.sendMessage(Text.literal("§7Поводок надет."), true);
                    }
                    return ActionResult.SUCCESS;
                }
                return ActionResult.PASS;
            }

            // Надевание наручников
            if (applier.getMainHandStack().getItem() instanceof org.gloyer057.cuffsx.item.HandcuffsItem hi) {
                if (CuffManager.justRemoved(applier.getUuid())) return ActionResult.SUCCESS;

                // Проверка bypass — нельзя надеть наручники на игрока с правом cuffsx.bypass
                if (Permissions.check(target, "cuffsx.bypass", false)) {
                    String bypassMsg = "§cУ данного человека bypass к наручникам, вы не можете на него их надеть.";
                    applier.sendMessage(Text.literal(bypassMsg), true);  // action bar
                    applier.sendMessage(Text.literal(bypassMsg), false); // чат
                    return ActionResult.SUCCESS;
                }

                if (!CuffManager.isEnabled()) {
                    applier.sendMessage(Text.literal("§cНаручники нельзя одеть на игрока в данный момент."), false);
                    return ActionResult.SUCCESS;
                }

                if (ClaimChecker.isClaimed(applier, target.getBlockPos())) {
                    applier.sendMessage(Text.literal("§cНельзя надеть наручники — цель находится в привате."), false);
                    return ActionResult.SUCCESS;
                }

                // Проверяем что такого типа ещё нет (и не в процессе надевания)
                CuffState checkState = CuffState.getOrCreate(applier.getServer());
                if (checkState.hasCuff(target.getUuid(), hi.getCuffType())) {
                    String typeName = hi.getCuffType() == CuffType.HANDS ? "руки" : "ноги";
                    applier.sendMessage(Text.literal("§cНа " + target.getName().getString() + " уже надеты наручники на " + typeName + "."), false);
                    return ActionResult.SUCCESS;
                }
                // Проверяем активный прогресс надевания того же типа
                ApplyProgress.Entry existingProgress = ApplyProgress.get(target.getUuid());
                if (existingProgress == null) {
                    // Ищем по всем игрокам — есть ли кто-то кто уже надевает на target тот же тип
                    for (ServerPlayerEntity other : applier.getServer().getPlayerManager().getPlayerList()) {
                        ApplyProgress.Entry e = ApplyProgress.get(other.getUuid());
                        if (e != null && e.targetUuid().equals(target.getUuid()) && e.cuffType() == hi.getCuffType()) {
                            String typeName = hi.getCuffType() == CuffType.HANDS ? "руки" : "ноги";
                            applier.sendMessage(Text.literal("§cНа " + target.getName().getString() + " уже идёт процесс надевания наручников на " + typeName + "."), false);
                            return ActionResult.SUCCESS;
                        }
                    }
                }

                if (CuffManager.isCuffed(target)) {
                    applier.getMainHandStack().decrement(1);
                    applier.playerScreenHandler.syncState();
                    if (!CuffManager.applyCuffsImmediate(applier, target, hi.getCuffType())) {
                        net.minecraft.item.ItemStack ret = new net.minecraft.item.ItemStack(hi);
                        if (!applier.getInventory().insertStack(ret)) applier.dropItem(ret, false);
                    }
                    return ActionResult.SUCCESS;
                }

                applier.getMainHandStack().decrement(1);
                applier.playerScreenHandler.syncState();
                ApplyProgress.start(applier, target, hi.getCuffType());
                return ActionResult.SUCCESS;
            }

            // Отмена надевания — тот же игрок кликает ПКМ по той же цели пока прогресс активен
            if (ApplyProgress.isActive(applier.getUuid())) {
                ApplyProgress.Entry entry = ApplyProgress.get(applier.getUuid());
                if (entry != null && entry.targetUuid().equals(target.getUuid())) {
                    // Возвращаем наручники
                    net.minecraft.item.ItemStack returnStack = new net.minecraft.item.ItemStack(
                        entry.cuffType() == CuffType.HANDS
                            ? org.gloyer057.cuffsx.item.ModItems.HANDCUFFS_HANDS
                            : org.gloyer057.cuffsx.item.ModItems.HANDCUFFS_LEGS);
                    if (!applier.getInventory().insertStack(returnStack))
                        applier.dropItem(returnStack, false);
                    ApplyProgress.cancel(applier.getUuid());
                    CuffManager.sendHud(applier, -1);
                    applier.sendMessage(Text.literal("§cНадевание отменено."), true);
                    return ActionResult.SUCCESS;
                }
            }

            // Снятие — пустые руки, applier свободен, target закован
            if (!(applier.getMainHandStack().getItem() instanceof org.gloyer057.cuffsx.item.HandcuffsItem)
                    && !(applier.getOffHandStack().getItem() instanceof org.gloyer057.cuffsx.item.HandcuffsItem)
                    && !CuffManager.isCuffed(applier)
                    && CuffManager.isCuffed(target)) {

                // Определяем тип по Y-позиции попадания
                CuffType typeToRemove;
                if (hitResult != null) {
                    double hitY = hitResult.getPos().y;
                    double midY = target.getY() + target.getHeight() / 2.0;
                    typeToRemove = hitY >= midY ? CuffType.HANDS : CuffType.LEGS;
                } else {
                    typeToRemove = CuffType.HANDS;
                }

                // Если нужного типа нет — пробуем другой
                CuffState state = CuffState.getOrCreate(applier.getServer());
                if (!state.hasCuff(target.getUuid(), typeToRemove)) {
                    typeToRemove = typeToRemove == CuffType.HANDS ? CuffType.LEGS : CuffType.HANDS;
                }
                if (!state.hasCuff(target.getUuid(), typeToRemove)) return ActionResult.PASS;

                final CuffType finalType = typeToRemove;

                // Повторное нажатие — отменяем прогресс снятия
                if (RemoveProgress.isActive(applier.getUuid())) {
                    RemoveProgress.cancel(applier.getUuid());
                    CuffManager.sendHud(applier, -1, -1);
                    applier.sendMessage(Text.literal("§cСнятие отменено."), true);
                    return ActionResult.SUCCESS;
                }

                // Запускаем прогресс снятия (2 секунды)
                RemoveProgress.start(applier, target, finalType);
                return ActionResult.SUCCESS;
            }

            return ActionResult.PASS;
        });

        // 10.3 — block interactions for HANDS
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (player instanceof ServerPlayerEntity sp && CuffManager.isCuffed(sp, CuffType.HANDS))
                return ActionResult.SUCCESS;
            return ActionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (player instanceof ServerPlayerEntity sp && CuffManager.isCuffed(sp, CuffType.HANDS))
                return ActionResult.SUCCESS;
            return ActionResult.PASS;
        });

        // 10.4 — item use blocked for HANDS
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) return TypedActionResult.pass(player.getStackInHand(hand));
            if (player instanceof ServerPlayerEntity sp && CuffManager.isCuffed(sp, CuffType.HANDS))
                return TypedActionResult.fail(player.getStackInHand(hand));
            return TypedActionResult.pass(player.getStackInHand(hand));
        });

        // Получаем пакет ломания наручников от клиента
        ServerPlayNetworking.registerGlobalReceiver(CuffBreakPayload.ID, (server, player, handler, buf, responseSender) -> {
            String type = buf.readString();
            server.execute(() -> {
                UUID uuid = player.getUuid();
                if ("HANDS".equals(type) && CuffManager.isCuffed(player, CuffType.HANDS)) {
                    boolean broken = CuffDurability.damageHands(uuid);
                    if (broken) {
                        CuffManager.removeCuffsForced(player, CuffType.HANDS);
                        player.sendMessage(Text.literal("§aВы сломали наручники на руках!"), true);
                    } else {
                        CuffManager.sendHud(player);
                    }
                } else if ("LEGS".equals(type) && CuffManager.isCuffed(player, CuffType.LEGS)) {
                    boolean broken = CuffDurability.damageLegs(uuid);
                    if (broken) {
                        CuffManager.removeCuffsForced(player, CuffType.LEGS);
                        player.sendMessage(Text.literal("§aВы сломали наручники на ногах!"), true);
                    } else {
                        CuffManager.sendHud(player);
                    }
                }
            });
        });

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            CuffLog.setServer(server);
            CuffApplies.setServer(server);
            CuffManager.onTick();
            // Очищаем старые записи кулдауна (старше 2 секунд)
            long now = System.currentTimeMillis();
            callbackCooldown.entrySet().removeIf(e -> now - e.getValue() > 2000L);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickLegs(server);
            tickLeash(server);
            tickApplyProgress(server);
            tickRemoveProgress(server);
            tickHudSync(server);
            tickLogExpiry(server);
        });

        // Автоснятие поводка при выходе игрока
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            UUID uuid = player.getUuid();

            // Если игрок был ведомым — обновляем lockedPos на текущую позицию,
            // чтобы при перезаходе он остался там же, а не телепортировался к месту надевания
            if (LeashManager.isLeashed(uuid)) {
                CuffState state = CuffState.getOrCreate(server);
                for (CuffType t : CuffType.values()) {
                    if (state.hasCuff(uuid, t)) {
                        state.updateLockedPos(uuid, t, player.getPos());
                    }
                }
                // Сообщение ведущему в action bar
                UUID holderUuid = LeashManager.getHolder(uuid);
                if (holderUuid != null) {
                    ServerPlayerEntity holder = server.getPlayerManager().getPlayer(holderUuid);
                    if (holder != null) {
                        holder.sendMessage(Text.literal("§eИгрок которого вы вели ливнул."), true);
                    }
                }
                LeashManager.detachByLeashed(uuid);
            }

            // Если игрок был ведущим — сообщение ведомому в action bar и обновляем lockedPos
            UUID leashedUuid = LeashManager.getLeashed(uuid);
            if (leashedUuid != null) {
                ServerPlayerEntity leashed = server.getPlayerManager().getPlayer(leashedUuid);
                if (leashed != null) {
                    leashed.sendMessage(Text.literal("§eИгрок ведущий вас вышел из игры."), true);
                    // Обновляем lockedPos ведомого на его текущую позицию
                    CuffState ls = CuffState.getOrCreate(server);
                    for (CuffType t : CuffType.values()) {
                        if (ls.hasCuff(leashedUuid, t))
                            ls.updateLockedPos(leashedUuid, t, leashed.getPos());
                    }
                }
                LeashManager.detachByHolder(uuid);
            }
        });

        // 10.6 — re-apply on join/respawn
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            CuffState state = CuffState.getOrCreate(server);
            state.getRecords(player.getUuid()).forEach(r -> {
                CuffManager.applyRestrictions(player, r.cuffType());
                // Восстанавливаем прочность если ещё не инициализирована
                if (r.cuffType() == CuffType.HANDS && CuffDurability.getHandsHp(player.getUuid()) < 0)
                    CuffDurability.initHands(player.getUuid());
                else if (r.cuffType() == CuffType.LEGS && CuffDurability.getLegsHp(player.getUuid()) < 0)
                    CuffDurability.initLegs(player.getUuid());
            });
            // Если наручников на ногах нет — гарантированно снимаем модификатор скорости.
            // Это фиксит кейс офлайн-снятия: модификатор остаётся в NBT игрока,
            // но при входе мы его убираем если CuffState не содержит LEGS-записи.
            if (!state.hasCuff(player.getUuid(), CuffType.LEGS)) {
                CuffManager.removeRestrictions(player, CuffType.LEGS);
            }
            CuffManager.syncToClients(player);
            CuffManager.sendHud(player);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            CuffState state = CuffState.getOrCreate(newPlayer.getServer());
            state.getRecords(newPlayer.getUuid()).forEach(r ->
                CuffManager.applyRestrictions(newPlayer, r.cuffType()));
            // Снимаем модификатор скорости если наручников на ногах нет
            if (!state.hasCuff(newPlayer.getUuid(), CuffType.LEGS)) {
                CuffManager.removeRestrictions(newPlayer, CuffType.LEGS);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            CuffsxCommand.register(dispatcher));

        // При смерти — снимаем наручники и дропаем предметы
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.ALLOW_DEATH.register((player, damageSource, damageAmount) -> {
            CuffState deathState = CuffState.getOrCreate(player.getServer());
            for (CuffType type : CuffType.values()) {
                if (deathState.hasCuff(player.getUuid(), type)) {
                    // Дропаем предмет наручников на месте смерти
                    net.minecraft.item.ItemStack drop = new net.minecraft.item.ItemStack(
                        type == CuffType.HANDS
                            ? org.gloyer057.cuffsx.item.ModItems.HANDCUFFS_HANDS
                            : org.gloyer057.cuffsx.item.ModItems.HANDCUFFS_LEGS);
                    player.dropItem(drop, true, false);
                    // Снимаем принудительно (без возврата предмета — уже дропнули)
                    CuffManager.removeCuffsForced(player, type);
                }
            }
            // Снимаем поводок если был
            LeashManager.detachByLeashed(player.getUuid());
            return true; // разрешаем смерть
        });
    }

    // Телепорт LEGS-закованных обратно (пропускаем тех кто на поводке)
    private void tickLegs(MinecraftServer server) {
        CuffState state = CuffState.getOrCreate(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // Если игрок на поводке — не телепортируем обратно, его ведут
            if (LeashManager.isLeashed(player.getUuid())) continue;

            state.getRecords(player.getUuid()).stream()
                .filter(r -> r.cuffType() == CuffType.LEGS)
                .findFirst()
                .ifPresent(r -> {
                    if (player.getPos().distanceTo(r.lockedPos()) > 0.1) {
                        player.teleport(r.lockedPos().x, r.lockedPos().y, r.lockedPos().z);
                    }
                });
        }
    }

    private static int leashTick = 0;

    // Ведение на поводке — каждые 10 тиков
    private void tickLeash(MinecraftServer server) {
        leashTick++;
        if (leashTick < 10) return;
        leashTick = 0;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID holderUuid = LeashManager.getHolder(player.getUuid());
            if (holderUuid == null) continue;

            ServerPlayerEntity holder = server.getPlayerManager().getPlayer(holderUuid);
            if (holder == null) {
                LeashManager.detachByLeashed(player.getUuid());
                continue;
            }

            // Проверяем что ведомый всё ещё в наручниках
            if (!CuffManager.isCuffed(player)) {
                LeashManager.detachByLeashed(player.getUuid());
                continue;
            }

            // Телепортируем ведомого к ведущему если дальше 3 блоков
            double dist = player.getPos().distanceTo(holder.getPos());
            if (dist > 3.0) {
                Vec3d target = holder.getPos();
                player.teleport(target.x, target.y, target.z);
            }
        }
    }

    // Прогресс надевания наручников
    private void tickApplyProgress(MinecraftServer server) {
        for (ServerPlayerEntity applier : server.getPlayerManager().getPlayerList()) {
            if (!ApplyProgress.isActive(applier.getUuid())) continue;
            // Если идёт снятие — не перезаписываем actionbar
            if (RemoveProgress.isActive(applier.getUuid())) continue;

            ApplyProgress.Entry entry = ApplyProgress.get(applier.getUuid());
            if (entry == null) continue;

            ServerPlayerEntity target = server.getPlayerManager().getPlayer(entry.targetUuid());
            if (target == null) {
                ApplyProgress.cancel(applier.getUuid());
                CuffManager.sendHud(applier, -1);
                continue;
            }

            ApplyProgress.TickResult result = ApplyProgress.tick(applier.getUuid(), target.getPos());

            switch (result) {
                case IN_PROGRESS -> {
                    int pct = ApplyProgress.getProgress(applier.getUuid());
                    applier.sendMessage(Text.literal("§eНадевание: " + pct + "%"), true);
                    CuffManager.sendHud(applier, pct);
                    // Уведомляем цель в actionbar (без title)
                    String typeName = entry.cuffType() == CuffType.HANDS ? "руки" : "ноги";
                    target.sendMessage(Text.literal("§cВас заковывают: §eПрогресс - " + pct + "%, тип - " + typeName), true);
                }
                case COMPLETED -> {
                    if (!CuffManager.applyCuffsImmediate(applier, target, entry.cuffType())) {
                        // Не удалось надеть — возвращаем наручники
                        net.minecraft.item.ItemStack ret = new net.minecraft.item.ItemStack(
                            entry.cuffType() == CuffType.HANDS
                                ? org.gloyer057.cuffsx.item.ModItems.HANDCUFFS_HANDS
                                : org.gloyer057.cuffsx.item.ModItems.HANDCUFFS_LEGS);
                        if (!applier.getInventory().insertStack(ret)) applier.dropItem(ret, false);
                        applier.sendMessage(Text.literal("§cНе удалось надеть наручники."), true);
                    } else {
                        applier.sendMessage(Text.literal("§aНаручники надеты!"), true);
                    }
                    CuffManager.sendHud(applier, -1);
                }
                case CANCELLED_MOVED -> {
                    // Возвращаем наручники
                    net.minecraft.item.ItemStack returnStack = new net.minecraft.item.ItemStack(
                        entry.cuffType() == CuffType.HANDS
                            ? org.gloyer057.cuffsx.item.ModItems.HANDCUFFS_HANDS
                            : org.gloyer057.cuffsx.item.ModItems.HANDCUFFS_LEGS);
                    if (!applier.getInventory().insertStack(returnStack))
                        applier.dropItem(returnStack, false);
                    applier.sendMessage(Text.literal("§cЦель сдвинулась — надевание отменено."), true);
                    CuffManager.sendHud(applier, -1);
                }
                default -> {}
            }
        }
    }

    // Периодическая синхронизация HUD прочности
    private static int hudSyncTick = 0;
    private void tickHudSync(MinecraftServer server) {
        hudSyncTick++;
        if (hudSyncTick < 20) return; // раз в секунду
        hudSyncTick = 0;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (CuffManager.isCuffed(player)) {
                CuffManager.sendHud(player);
                sendCuffStatusActionBar(player);
            }
        }
    }

    // =====================================================================
    // Как часто сервер проверяет истёкшие записи в logs.dat и applies.dat.
    // Единица — тики (20 тиков = 1 секунда).
    // Сейчас: 1200 тиков = 1 минута. Чтобы изменить — поменяй число.
    // Примеры: 600 = 30 сек, 6000 = 5 минут, 12000 = 10 минут
    // =====================================================================
    private static int logCheckTick = 0;
    private void tickLogExpiry(MinecraftServer server) {
        logCheckTick++;
        if (logCheckTick < 1200) return; // <-- вот это число и меняй
        logCheckTick = 0;
        CuffLog.getRecent(); // вызов триггерит перенос истёкших APPLY в applies.dat
        CuffApplies.getAll(); // вызов триггерит очистку истёкших applies
    }

    private void sendCuffStatusActionBar(ServerPlayerEntity player) {
        boolean hands = CuffManager.isCuffed(player, CuffType.HANDS);
        boolean legs  = CuffManager.isCuffed(player, CuffType.LEGS);
        if (!hands && !legs) return;
        // Не перебиваем прогресс надевания/снятия
        if (ApplyProgress.isActive(player.getUuid()) || RemoveProgress.isActive(player.getUuid())) return;
        String type;
        if (hands && legs) type = "руки и ноги";
        else if (hands)    type = "руки";
        else               type = "ноги";
        player.sendMessage(Text.literal("§cВы в наручниках [" + type + "]"), true);
    }

    // Прогресс снятия наручников (2 секунды)
    private void tickRemoveProgress(MinecraftServer server) {
        for (ServerPlayerEntity applier : server.getPlayerManager().getPlayerList()) {
            if (!RemoveProgress.isActive(applier.getUuid())) continue;

            RemoveProgress.Entry entry = RemoveProgress.get(applier.getUuid());
            if (entry == null) continue;

            ServerPlayerEntity target = server.getPlayerManager().getPlayer(entry.targetUuid());
            if (target == null || !CuffManager.isCuffed(target, entry.cuffType())) {
                RemoveProgress.cancel(applier.getUuid());
                CuffManager.sendHud(applier, -1);
                continue;
            }

            RemoveProgress.TickResult result = RemoveProgress.tick(applier.getUuid());

            switch (result) {
                case IN_PROGRESS -> {
                    int pct = RemoveProgress.getProgress(applier.getUuid());
                    applier.sendMessage(Text.literal("Снятие: " + pct + "%"), true);
                    CuffManager.sendHud(applier, -1, pct);
                }
                case COMPLETED -> {
                    CuffManager.removeCuffsByType(applier, target, entry.cuffType());
                    applier.sendMessage(Text.literal("§aНаручники сняты!"), true);
                    CuffManager.sendHud(applier, -1, -1);
                }
                default -> {}
            }
        }
    }
}
