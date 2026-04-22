package org.gloyer057.cuffsx.item;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.gloyer057.cuffsx.cuff.CuffType;

public class ModItems {

    public static final HandcuffsItem HANDCUFFS_HANDS = new HandcuffsItem(
            new FabricItemSettings().maxCount(1), CuffType.HANDS);

    public static final HandcuffsItem HANDCUFFS_LEGS = new HandcuffsItem(
            new FabricItemSettings().maxCount(1), CuffType.LEGS);

    public static void register() {
        Registry.register(Registries.ITEM, new Identifier("cuffsx", "handcuffs_hands"), HANDCUFFS_HANDS);
        Registry.register(Registries.ITEM, new Identifier("cuffsx", "handcuffs_legs"), HANDCUFFS_LEGS);
    }
}
