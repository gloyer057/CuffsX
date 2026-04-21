package org.gloyer057.cuffsx.item;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.gloyer057.cuffsx.cuff.CuffType;

public class ModItems {
    public static final HandcuffsItem HANDCUFFS_HANDS = Registry.register(
            Registries.ITEM,
            new Identifier("cuffsx", "handcuffs_hands"),
            new HandcuffsItem(CuffType.HANDS)
    );

    public static final HandcuffsItem HANDCUFFS_LEGS = Registry.register(
            Registries.ITEM,
            new Identifier("cuffsx", "handcuffs_legs"),
            new HandcuffsItem(CuffType.LEGS)
    );

    public static void register() {
        // no-op: triggers static initializers
    }
}
