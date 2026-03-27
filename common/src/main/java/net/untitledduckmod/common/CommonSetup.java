package net.untitledduckmod.common;
import net.minecraft.block.DispenserBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ProjectileItem;

public class CommonSetup {
    public static void setupDispenserProjectile(Item item) {
        if (item instanceof ProjectileItem) {
            DispenserBlock.registerProjectileBehavior(item);
        }
    }
}
