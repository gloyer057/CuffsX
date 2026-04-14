package org.gloyer057.cuffsx;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import org.gloyer057.cuffsx.cuff.CuffRecord;
import org.gloyer057.cuffsx.cuff.CuffType;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Feature: cuffsx-handcuffs-mod, Property 11: CuffRecord round-trip serialization
class CuffRecordRoundTripTest {

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.longs().flatMap(msb ->
                Arbitraries.longs().map(lsb -> new UUID(msb, lsb)));
    }

    @Provide
    Arbitrary<CuffType> cuffTypes() {
        return Arbitraries.of(CuffType.values());
    }

    @Property(tries = 100)
    void cuffRecordRoundTrip(
            @ForAll("uuids") UUID targetUUID,
            @ForAll("uuids") UUID applierUUID,
            @ForAll("cuffTypes") CuffType cuffType,
            @ForAll long timestamp,
            @ForAll double x,
            @ForAll double y,
            @ForAll double z,
            @ForAll @AlphaChars @StringLength(min = 1, max = 16) String applierName,
            @ForAll @AlphaChars @StringLength(min = 1, max = 16) String targetName
    ) {
        CuffRecord original = new CuffRecord(
                targetUUID, applierUUID, cuffType, timestamp,
                new Vec3d(x, y, z), applierName, targetName
        );

        NbtCompound nbt = original.toNbt();
        CuffRecord deserialized = CuffRecord.fromNbt(nbt);

        assertEquals(original.targetUUID(), deserialized.targetUUID());
        assertEquals(original.applierUUID(), deserialized.applierUUID());
        assertEquals(original.cuffType(), deserialized.cuffType());
        assertEquals(original.timestamp(), deserialized.timestamp());
        assertEquals(original.lockedPos().x, deserialized.lockedPos().x, 1e-9);
        assertEquals(original.lockedPos().y, deserialized.lockedPos().y, 1e-9);
        assertEquals(original.lockedPos().z, deserialized.lockedPos().z, 1e-9);
        assertEquals(original.applierName(), deserialized.applierName());
        assertEquals(original.targetName(), deserialized.targetName());
    }
}
