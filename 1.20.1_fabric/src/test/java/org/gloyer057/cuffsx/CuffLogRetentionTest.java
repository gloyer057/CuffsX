package org.gloyer057.cuffsx;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import net.minecraft.util.math.Vec3d;
import org.gloyer057.cuffsx.cuff.CuffLog;
import org.gloyer057.cuffsx.cuff.CuffRecord;
import org.gloyer057.cuffsx.cuff.CuffType;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

// Feature: cuffsx-handcuffs-mod, Property 14: log entries retained for 2 hours
class CuffLogRetentionTest {

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
    void logRetainsEntriesWithinTtl(
            @ForAll("uuids") UUID targetUUID,
            @ForAll("uuids") UUID applierUUID,
            @ForAll("cuffTypes") CuffType cuffType,
            @ForAll @AlphaChars @StringLength(min = 1, max = 16) String applierName,
            @ForAll @AlphaChars @StringLength(min = 1, max = 16) String targetName
    ) {
        CuffRecord record = new CuffRecord(
                targetUUID, applierUUID, cuffType,
                System.currentTimeMillis(),
                new Vec3d(0, 64, 0),
                applierName, targetName
        );

        CuffLog.log(CuffLog.Action.APPLY, record);

        List<CuffLog.LogEntry> recent = CuffLog.getRecent();
        boolean found = recent.stream().anyMatch(e ->
                e.targetName().equals(targetName)
                && e.applierName().equals(applierName)
                && e.cuffType() == cuffType
                && e.action() == CuffLog.Action.APPLY
        );

        assertTrue(found, "Entry logged just now should be present in getRecent()");
    }
}
