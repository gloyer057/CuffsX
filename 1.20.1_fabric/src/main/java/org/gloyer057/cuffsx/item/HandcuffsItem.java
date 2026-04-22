package org.gloyer057.cuffsx.item;

import net.minecraft.item.Item;
import org.gloyer057.cuffsx.cuff.CuffType;

public class HandcuffsItem extends Item {

    private final CuffType cuffType;

    public HandcuffsItem(Settings settings, CuffType cuffType) {
        super(settings);
        this.cuffType = cuffType;
    }

    public CuffType getCuffType() {
        return cuffType;
    }
}
