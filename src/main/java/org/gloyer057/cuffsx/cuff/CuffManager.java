package org.gloyer057.cuffsx.cuff;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.gloyer057.cuffsx.item.ModItems;

import java.util.UUID;

public class CuffManager {

    private static boolean enabled = true;

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

        if (!enabled) {
            applier.sendMessage(
                    net.minecraft.text.Text.literal("Наручники нельзя одеть на игрока в данный момент."),
                    false
            );
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

        return true;
    }

    /**
     * Снять наручники. Возвращает снятый CuffType или null.
     */
    public static CuffType removeCuffs(ServerPlayerEntity applier,
                                       ServerPlayerEntity target) {
        if (applier == target) return null;

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
        boolean inserted = applier.getInventory().insertStack(stack);
        if (!inserted) {
            applier.dropItem(stack, false);
        }

        if (removedRecord != null) {
            CuffLog.log(CuffLog.Action.REMOVE, removedRecord);
        }

        removeRestrictions(target, typeToRemove);

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
