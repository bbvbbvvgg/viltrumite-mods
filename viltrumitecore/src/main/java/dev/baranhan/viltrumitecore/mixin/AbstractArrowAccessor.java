package dev.baranhan.viltrumitecore.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The arrow's own pickup item (protected in 1.20.1), for a hero catching it (HeroProjectiles). */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
   @Invoker("getPickupItem")
   ItemStack viltrumitecore$getPickupItem();
}
