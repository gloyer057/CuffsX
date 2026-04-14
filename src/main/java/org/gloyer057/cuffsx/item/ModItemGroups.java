package org.gloyer057.cuffsx.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItemGroups {
    public static final net.minecraft.item.ItemGroup CUFFSX_GROUP = Registry.register(
            Registries.ITEM_GROUP,
            new Identifier("cuffsx", "cuffsx"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.cuffsx.cuffsx"))
                    .icon(() -> new ItemStack(ModItems.HANDCUFFS_HANDS))
                    .entries((ctx, entries) -> {
                        entries.add(ModItems.HANDCUFFS_HANDS);
                        entries.add(ModItems.HANDCUFFS_LEGS);
                    })
                    .build()
    );

    public static void register() {
        // no-op: triggers static initializers
    }
}
