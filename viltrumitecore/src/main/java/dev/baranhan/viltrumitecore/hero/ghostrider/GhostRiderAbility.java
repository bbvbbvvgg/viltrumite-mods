package dev.baranhan.viltrumitecore.hero.ghostrider;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import javax.annotation.Nullable;

/** Panel abilities of the Ghost Rider in slot order (R, Y, Z, V, B, N). {@code cooldown} indexes the state cooldowns. */
public enum GhostRiderAbility {
   CYCLE("hell_cycle", HeroAction.GHOSTRIDER_CYCLE, GhostRiderRules.CD_CYCLE),
   TRANSFORM("transformation", HeroAction.GHOSTRIDER_TRANSFORM, GhostRiderRules.CD_TRANSFORM),
   DASH("hell_dash", HeroAction.GHOSTRIDER_DASH, GhostRiderRules.CD_DASH),
   WHIRL("chain_whirl", HeroAction.GHOSTRIDER_WHIRL, GhostRiderRules.CD_WHIRL),
   WAVE("hellfire_wave", HeroAction.GHOSTRIDER_WAVE, GhostRiderRules.CD_WAVE),
   STARE("penance_stare", HeroAction.GHOSTRIDER_STARE, GhostRiderRules.CD_STARE);

   private final String key;
   private final HeroAction action;
   public final int cooldown;

   GhostRiderAbility(String key, HeroAction action, int cooldown) {
      this.key = key;
      this.action = action;
      this.cooldown = cooldown;
   }

   public String key() {
      return this.key;
   }

   public String id() {
      return "ghost_rider:" + this.key;
   }

   public HeroAction action() {
      return this.action;
   }

   public int cooldownTicks() {
      return GhostRiderRules.COOLDOWN_TICKS[this.cooldown];
   }

   /** Abilities Johnny Blaze can use without the Rider (the transformation itself and calling the bike). */
   public boolean human() {
      return this == CYCLE || this == TRANSFORM;
   }

   @Nullable
   public static GhostRiderAbility byId(@Nullable String id) {
      if (id == null) {
         return null;
      }
      for (GhostRiderAbility a : values()) {
         if (a.id().equals(id)) {
            return a;
         }
      }
      return null;
   }

   @Nullable
   public static GhostRiderAbility byAction(HeroAction action) {
      for (GhostRiderAbility a : values()) {
         if (a.action == action) {
            return a;
         }
      }
      return null;
   }
}
