package org.gloyer057.cuffsx.cuff;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public record CuffRecord(
        UUID targetUUID,
        UUID applierUUID,
        CuffType cuffType,
        long timestamp,
        Vec3d lockedPos,
        String applierName,
        String targetName
) {
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("targetUUID", targetUUID.toString());
        nbt.putString("applierUUID", applierUUID.toString());
        nbt.putString("cuffType", cuffType.toNbt());
        nbt.putLong("timestamp", timestamp);
        nbt.putDouble("lockedX", lockedPos.x);
        nbt.putDouble("lockedY", lockedPos.y);
        nbt.putDouble("lockedZ", lockedPos.z);
        nbt.putString("applierName", applierName);
        nbt.putString("targetName", targetName);
        return nbt;
    }

    public static CuffRecord fromNbt(NbtCompound nbt) {
        return new CuffRecord(
                UUID.fromString(nbt.getString("targetUUID")),
                UUID.fromString(nbt.getString("applierUUID")),
                CuffType.fromNbt(nbt.getString("cuffType")),
                nbt.getLong("timestamp"),
                new Vec3d(
                        nbt.getDouble("lockedX"),
                        nbt.getDouble("lockedY"),
                        nbt.getDouble("lockedZ")
                ),
                nbt.getString("applierName"),
                nbt.getString("targetName")
        );
    }
}
