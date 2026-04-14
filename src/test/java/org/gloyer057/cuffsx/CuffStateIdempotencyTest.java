package org.gloyer057.cuffsx;

import net.jqwik.api.*;
import net.minecraft.util.math.Vec3d;
import org.gloyer057.cuffsx.cuff.CuffRecord;
import org.gloyer057.cuffsx.cuff.CuffState;
import org.gloyer057.cuffsx.cuff.CuffType;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Feature: cuffsx-handcuffs-mod, Property 5: duplicate cuff application is idempotent
class CuffStateIdempotencyTest {

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
    void addRecordMakesHasCuffTrue(
            @ForAll("uuids") UUID targetUUID,
            @ForAll("uuids") UUID applierUUID,
            @ForAll("cuffTypes") CuffType cuffType
    ) {
        CuffState state = new CuffState();
        CuffRecord record = new CuffRecord(
                targetUUID, applierUUID, cuffType,
                System.currentTimeMillis(), new Vec3d(0, 64, 0),
                "applier", "target"
        );

        state.addRecord(record);

        assertTrue(state.hasCuff(targetUUID, cuffType));
    }

    @Property(tries = 100)
    void duplicateCuffIsRejected(
            @ForAll("uuids") UUID targetUUID,
            @ForAll("uuids") UUID applierUUID,
            @ForAll("cuffTypes") CuffType cuffType
    ) {
        CuffState state = new CuffState();
        CuffRecord record = new CuffRecord(
                targetUUID, applierUUID, cuffType,
                System.currentTimeMillis(), new Vec3d(0, 64, 0),
                "applier", "target"
        );

        state.addRecord(record);
        int before = state.getAllRecords().size();
        state.addRecord(record); // повторное добавление той же записи

        assertEquals(before, state.getAllRecords().size());
    }
}
