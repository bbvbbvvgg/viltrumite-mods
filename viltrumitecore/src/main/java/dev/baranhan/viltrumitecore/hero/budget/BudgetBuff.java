package dev.baranhan.viltrumitecore.hero.budget;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

/** Vanilla effect granted by a type D power-up. */
public enum BudgetBuff {
   NONE(null),
   STRENGTH(MobEffects.DAMAGE_BOOST),
   SPEED(MobEffects.MOVEMENT_SPEED),
   RESISTANCE(MobEffects.DAMAGE_RESISTANCE);

   private final MobEffect effect;

   BudgetBuff(MobEffect effect) {
      this.effect = effect;
   }

   public MobEffect effect() {
      return this.effect;
   }
}
