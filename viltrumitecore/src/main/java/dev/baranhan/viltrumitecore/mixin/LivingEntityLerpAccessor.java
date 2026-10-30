package dev.baranhan.viltrumitecore.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Pending client interpolation target, settled at once when stopped time freezes the entity. */
@Mixin(LivingEntity.class)
public interface LivingEntityLerpAccessor {
   @Accessor("lerpSteps")
   int viltrumitecore$getLerpSteps();

   @Accessor("lerpSteps")
   void viltrumitecore$setLerpSteps(int steps);

   @Accessor("lerpX")
   double viltrumitecore$getLerpX();

   @Accessor("lerpY")
   double viltrumitecore$getLerpY();

   @Accessor("lerpZ")
   double viltrumitecore$getLerpZ();
}
