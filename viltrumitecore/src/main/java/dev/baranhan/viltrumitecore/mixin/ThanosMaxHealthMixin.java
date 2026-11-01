package dev.baranhan.viltrumitecore.mixin;

import dev.baranhan.viltrumitecore.entity.thanos.ThanosEntity;
import dev.baranhan.viltrumitecore.entity.ultron.UltronPrime;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thanos's max health is 3000 x (1 + 0.6 x (heroes - 1)), past the 1024 at which vanilla clamps
 * the max-health attribute, and {@code getMaxHealth()} is final: for him it answers his synced
 * value ({@link ThanosEntity#thanosMaxHealth()}). Ultron Prime (1800 x (1 + 0.5 x (heroes - 1))) the same way
 * ({@link UltronPrime#primeMaxHealth()}). Every other entity is untouched.
 */
@Mixin({LivingEntity.class})
public abstract class ThanosMaxHealthMixin {
   @Inject(
      method = {"getMaxHealth"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void viltrumitecore$thanosMaxHealth(CallbackInfoReturnable<Float> cir) {
      if ((Object)this instanceof ThanosEntity thanos) {
         cir.setReturnValue(thanos.thanosMaxHealth());
      } else if ((Object)this instanceof UltronPrime prime) {
         cir.setReturnValue(prime.primeMaxHealth());
      }
   }
}
