package org.gloyer057.cuffsx.cuff;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import org.gloyer057.cuffsx.item.ModItems;
import org.gloyer057.cuffsx.network.CuffHudPayload;
import org.gloyer057.cuffsx.network.CuffSyncPayload;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CuffManager {

    private static boolean enabled = true;

    // Кулдаун: номер тика последнего действия по UUID игрока
    private static final Map<UUID, Long> lastActionTick = new ConcurrentHashMap<>();
    private static long currentTick = 0;

    // UUID игроков которые надели наручники в текущем тике — защита от немедленного снятия
    private static final java.util.Set<UUID> appliedThisTick = ConcurrentHashMap.newKeySet();
    // UUID -> тик снятия наручников — защита от немедленного надевания (кулдаун 20 тиков = 1 сек)
    private static final Map<UUID, Long> removedAtTick = new ConcurrentHashMap<>();
    private static final long REMOVE_COOLDOWN_TICKS = 20L;

    public static void onTick() {
        currentTick++;
        appliedThisTick.clear();
    }

    public static boolean justApplied(UUID uuid) {
        return appliedThisTick.contains(uuid);
    }

    public static boolean justRemoved(UUID uuid) {
        Long tick = removedAtTick.get(uuid);
        return tick != null && currentTick - tick < REMOVE_COOLDOWN_TICKS;
    }

    private static final UUID LEGS_SPEED_MODIFIER_UUID =
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String LEGS_SPEED_MODIFIER_NAME = "cuffsx_legs_speed";

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean v) { enabled = v; }

    /**
     * Надеть наручники. Возвращает false если не удалось.
     */
    public static boolean applyCuffs(ServerPlayerEntity applier,
                                     ServerPlayerEntity target,
                                     CuffType type) {
        if (applier == target) return false;

        // Кулдаун — игнорируем повторные вызовы в том же тике или следующем
        Long lastTick = lastActionTick.get(applier.getUuid());
        if (lastTick != null && currentTick - lastTick < 2) return false;
        lastActionTick.put(applier.getUuid(), currentTick);

        if (!enabled) {
            applier.sendMessage(
                    net.minecraft.text.Text.literal("Наручники нельзя одеть на игрока в данный момент."),
                    false
            );
            return false;
        }

        // Проверка привата FTB Chunks
        if (ClaimChecker.isClaimed(applier, applier.getBlockPos())) {
            applier.sendMessage(
                net.minecraft.text.Text.literal("§cНельзя надеть наручники в чужом привате."), false);
            return false;
        }

        CuffState state = CuffState.getOrCreate(applier.getServer());

        if (state.hasCuff(target.getUuid(), type)) {
            return false;
        }

        CuffRecord record = new CuffRecord(
                target.getUuid(),
                applier.getUuid(),
                type,
                System.currentTimeMillis(),
                target.getPos(),
                applier.getName().getString(),
                target.getName().getString()
        );

        state.addRecord(record);
        applier.getMainHandStack().decrement(1);
        CuffLog.log(CuffLog.Action.APPLY, record);
        applyRestrictions(target, type);
        appliedThisTick.add(applier.getUuid());
        // Инициализируем прочность
        if (type == CuffType.HANDS) CuffDurability.initHands(target.getUuid());
        else CuffDurability.initLegs(target.getUuid());
        syncToClients(target);
        sendHud(target);

        return true;
    }

    /**
     * Снять наручники конкретного типа (по выбору снимающего).
     */
    public static boolean removeCuffsByType(ServerPlayerEntity applier,
                                             ServerPlayerEntity target,
                                             CuffType type) {
        if (applier == target) return false;
        if (appliedThisTick.contains(applier.getUuid())) return false;
        Long lastTick = lastActionTick.get(applier.getUuid());
        if (lastTick != null && currentTick - lastTick < 2) return false;
        lastActionTick.put(applier.getUuid(), currentTick);
        if (isCuffed(applier)) return false;

        CuffState state = CuffState.getOrCreate(applier.getServer());
        if (!state.hasCuff(target.getUuid(), type)) return false;

        CuffRecord removedRecord = state.getRecords(target.getUuid()).stream()
                .filter(r -> r.cuffType() == type).findFirst().orElse(null);
        state.removeRecord(target.getUuid(), type);

        ItemStack stack = new ItemStack(
                type == CuffType.HANDS ? ModItems.HANDCUFFS_HANDS : ModItems.HANDCUFFS_LEGS);
        if (!applier.getInventory().insertStack(stack)) applier.dropItem(stack, false);

        if (removedRecord != null) CuffLog.log(CuffLog.Action.REMOVE, removedRecord);
        removeRestrictions(target, type);
        if (type == CuffType.HANDS) CuffDurability.removeHands(target.getUuid());
        else CuffDurability.removeLegs(target.getUuid());
        LeashManager.detachByLeashed(target.getUuid());
        removedAtTick.put(applier.getUuid(), currentTick);
        syncToClients(target);
        sendHud(target);
        return true;
    }

    /**
     * Снять наручники. Возвращает снятый CuffType или null.
     */
    public static CuffType removeCuffs(ServerPlayerEntity applier,
                                       ServerPlayerEntity target) {
        if (applier == target) return null;

        // Если этот игрок только что надел наручники в этом тике — не снимать
        if (appliedThisTick.contains(applier.getUuid())) return null;

        // Кулдаун
        Long lastTick = lastActionTick.get(applier.getUuid());
        if (lastTick != null && currentTick - lastTick < 2) return null;
        lastActionTick.put(applier.getUuid(), currentTick);

        if (isCuffed(applier)) return null;
        if (!isCuffed(target)) return null;

        CuffState state = CuffState.getOrCreate(applier.getServer());

        // Priority: HANDS before LEGS
        final CuffType typeToRemove;
        if (state.hasCuff(target.getUuid(), CuffType.HANDS)) {
            typeToRemove = CuffType.HANDS;
        } else if (state.hasCuff(target.getUuid(), CuffType.LEGS)) {
            typeToRemove = CuffType.LEGS;
        } else {
            return null;
        }

        // Find the record before removing (for logging)
        CuffRecord removedRecord = state.getRecords(target.getUuid()).stream()
                .filter(r -> r.cuffType() == typeToRemove)
                .findFirst()
                .orElse(null);

        state.removeRecord(target.getUuid(), typeToRemove);

        // Give item back to applier
        ItemStack stack = new ItemStack(
                typeToRemove == CuffType.HANDS ? ModItems.HANDCUFFS_HANDS : ModItems.HANDCUFFS_LEGS
        );
        if (!applier.getInventory().insertStack(stack)) {
            // insertStack returns false when item couldn't be fully inserted
            applier.dropItem(stack, false);
        }

        if (removedRecord != null) {
            CuffLog.log(CuffLog.Action.REMOVE, removedRecord);
        }

        removeRestrictions(target, typeToRemove);
        // Убираем прочность
        if (typeToRemove == CuffType.HANDS) CuffDurability.removeHands(target.getUuid());
        else CuffDurability.removeLegs(target.getUuid());
        // Снимаем поводок если был
        LeashManager.detachByLeashed(target.getUuid());
        removedAtTick.put(applier.getUuid(), currentTick);
        syncToClients(target);
        sendHud(target);

        return typeToRemove;
    }

    public static boolean isCuffed(ServerPlayerEntity player, CuffType type) {
        CuffState state = CuffState.getOrCreate(player.getServer());
        return state.hasCuff(player.getUuid(), type);
    }

    public static boolean isCuffed(ServerPlayerEntity player) {
        return isCuffed(player, CuffType.HANDS) || isCuffed(player, CuffType.LEGS);
    }

    /**
     * Принудительно снять наручники конкретного типа (при поломке).
     * Предмет не возвращается.
     */
    public static void removeCuffsForced(ServerPlayerEntity target, CuffType type) {
        CuffState state = CuffState.getOrCreate(target.getServer());
        CuffRecord removedRecord = state.getRecords(target.getUuid()).stream()
                .filter(r -> r.cuffType() == type)
                .findFirst().orElse(null);
        state.removeRecord(target.getUuid(), type);
        if (removedRecord != null) CuffLog.log(CuffLog.Action.REMOVE, removedRecord);
        removeRestrictions(target, type);
        if (type == CuffType.HANDS) CuffDurability.removeHands(target.getUuid());
        else CuffDurability.removeLegs(target.getUuid());
        LeashManager.detachByLeashed(target.getUuid());
        syncToClients(target);
        sendHud(target);
    }

    /**
     * Надеть наручники немедленно (после завершения прогресса, без кулдауна).
     */
    public static boolean applyCuffsImmediate(ServerPlayerEntity applier,
                                               ServerPlayerEntity target,
                                               CuffType type) {
        if (!enabled) {
            applier.sendMessage(
                net.minecraft.text.Text.literal("Наручники нельзя одеть на игрока в данный момент."), false);
            return false;
        }
        CuffState state = CuffState.getOrCreate(applier.getServer());
        if (state.hasCuff(target.getUuid(), type)) return false;

        CuffRecord record = new CuffRecord(
                target.getUuid(), applier.getUuid(), type,
                System.currentTimeMillis(), target.getPos(),
                applier.getName().getString(), target.getName().getString()
        );
        state.addRecord(record);
        // Наручники уже забраны до вызова этого метода — decrement не нужен
        CuffLog.log(CuffLog.Action.APPLY, record);
        applyRestrictions(target, type);
        if (type == CuffType.HANDS) CuffDurability.initHands(target.getUuid());
        else CuffDurability.initLegs(target.getUuid());
        syncToClients(target);
        sendHud(target);
        return true;
    }

    /**
     * Отправить HUD данные конкретному игроку.
     */
    public static void sendHud(ServerPlayerEntity player) {
        sendHud(player, -1);
    }

    public static void sendHud(ServerPlayerEntity player, int applyProgress) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(CuffDurability.getHandsHp(player.getUuid()));
        buf.writeInt(CuffDurability.getLegsHp(player.getUuid()));
        buf.writeInt(applyProgress);
        ServerPlayNetworking.send(player, CuffHudPayload.ID, buf);
    }

    /**
     * Отправить всем игрокам в радиусе трекинга пакет синхронизации наручников.
     */
    public static void syncToClients(ServerPlayerEntity target) {
        CuffState state = CuffState.getOrCreate(target.getServer());
        boolean hands = state.hasCuff(target.getUuid(), CuffType.HANDS);
        boolean legs  = state.hasCuff(target.getUuid(), CuffType.LEGS);

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(target.getUuid());
        buf.writeBoolean(hands);
        buf.writeBoolean(legs);

        for (ServerPlayerEntity watcher : PlayerLookup.tracking(target)) {
            ServerPlayNetworking.send(watcher, CuffSyncPayload.ID, buf);
        }
        ServerPlayNetworking.send(target, CuffSyncPayload.ID, buf);
    }

    /**
     * Применить серверные ограничения (вызывается при надевании и при join).
     */
    public static void applyRestrictions(ServerPlayerEntity player, CuffType type) {
        if (type == CuffType.LEGS) {
            EntityAttributeInstance attr = player.getAttributeInstance(
                    EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (attr != null) {
                attr.removeModifier(LEGS_SPEED_MODIFIER_UUID);
                attr.addPersistentModifier(new EntityAttributeModifier(
                        LEGS_SPEED_MODIFIER_UUID,
                        LEGS_SPEED_MODIFIER_NAME,
                        -attr.getBaseValue(),
                        EntityAttributeModifier.Operation.ADDITION
                ));
            }
        }
        // HANDS restrictions are enforced via Mixin/events checking CuffState
    }

    /**
     * Снять серверные ограничения.
     */
    public static void removeRestrictions(ServerPlayerEntity player, CuffType type) {
        if (type == CuffType.LEGS) {
            EntityAttributeInstance attr = player.getAttributeInstance(
                    EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (attr != null) {
                attr.removeModifier(LEGS_SPEED_MODIFIER_UUID);
            }
        }
    }
}
