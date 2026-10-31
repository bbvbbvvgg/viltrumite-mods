package dev.baranhan.viltrumitecore.hero.budget;

import java.util.function.Supplier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

/**
 * Vanilla effect granted by a type D power-up. The effect is resolved lazily so
 * the roster (and HeroRegistry) can be loaded before vanilla registries are
 * bootstrapped, e.g. in plain unit tests.
 */
public enum BudgetBuff {
   NONE(() -> null),
   STRENGTH(() -> MobEffects.DAMAGE_BOOST),
   SPEED(() -> MobEffects.MOVEMENT_SPEED),
   RESISTANCE(() -> MobEffects.DAMAGE_RESISTANCE);

   private final Supplier<MobEffect> effect;

   BudgetBuff(Supplier<MobEffect> effect) {
      this.effect = effect;
   }

   public MobEffect effect() {
      return this.effect.get();
   }
}
