package dev.baranhan.viltrumitecore.hero;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * A permanent hero passive carried as a vanilla effect (icon in the inventory,
 * no particles). The effect is refreshed with a long window, never every tick:
 * effects such as Regeneration act on {@code duration % interval}, so a
 * duration pinned to one value would never heal.
 */
public final class HeroPassiveEffect {
   /** Duration of one window. */
   public static final int WINDOW = 400;
   /** Refresh when the window falls to this. */
   public static final int REFRESH_AT = 100;

   private HeroPassiveEffect() {
   }

   /** Pure rule: does the passive need a new window over {@code current}? */
   public static boolean needsRefresh(int currentAmplifier, int currentDuration, boolean present, int amplifier) {
      if (!present) {
         return true;
      }

      // A stronger effect (potion, beacon) wins; ours comes back after it.
      if (currentAmplifier > amplifier) {
         return false;
      }

      return currentAmplifier < amplifier || currentDuration <= REFRESH_AT;
   }

   /** Is this instance our passive (and not a potion the player drank)? */
   public static boolean isPassive(MobEffectInstance instance, int amplifier) {
      return instance.getAmplifier() == amplifier && instance.isAmbient() && !instance.isVisible() && instance.getDuration() <= WINDOW;
   }

   /** Server tick: keep the passive on. */
   public static void keep(LivingEntity entity, MobEffect effect, int amplifier) {
      MobEffectInstance current = entity.getEffect(effect);
      boolean present = current != null;
      if (needsRefresh(present ? current.getAmplifier() : 0, present ? current.getDuration() : 0, present, amplifier)) {
         // Vanilla merge keeps a weaker, longer potion as the hidden effect under ours.
         entity.addEffect(new MobEffectInstance(effect, WINDOW, amplifier, true, false, true));
      }
   }

   /** Hero change: take only our passive away. */
   public static void drop(LivingEntity entity, MobEffect effect, int amplifier) {
      MobEffectInstance current = entity.getEffect(effect);
      if (current != null && isPassive(current, amplifier)) {
         entity.removeEffect(effect);
      }
   }
}
