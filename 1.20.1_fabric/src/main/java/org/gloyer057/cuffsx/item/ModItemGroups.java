package org.gloyer057.cuffsx.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItemGroups {

    public static final ItemGroup CUFFSX_GROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(ModItems.HANDCUFFS_HANDS))
            .displayName(Text.literal("CuffsX"))
            .entries((context, entries) -> {
                entries.add(ModItems.HANDCUFFS_HANDS);
                entries.add(ModItems.HANDCUFFS_LEGS);
            })
            .build();

    public static void register() {
        Registry.register(Registries.ITEM_GROUP, new Identifier("cuffsx", "cuffsx_group"), CUFFSX_GROUP);
    }
}
