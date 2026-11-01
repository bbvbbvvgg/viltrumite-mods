package dev.baranhan.viltrumitecore.hero.redhulk;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import javax.annotation.Nullable;

/** Panel abilities of the Red Hulk in slot order. {@code cooldown} indexes RedHulkState.cooldowns. */
public enum RedHulkAbility {
   CLAP("thermal_clap", HeroAction.REDHULK_CLAP, RedHulkRules.CD_CLAP),
   SEISMIC("seismic_slam", HeroAction.REDHULK_SEISMIC, RedHulkRules.CD_SEISMIC),
   LEAP("destruction_leap", HeroAction.REDHULK_LEAP, RedHulkRules.CD_LEAP),
   BLAST("heat_blast", HeroAction.REDHULK_BLAST, RedHulkRules.CD_BLAST),
   FURY("thermal_fury", HeroAction.REDHULK_FURY, RedHulkRules.CD_FURY),
   OVERHEAT("critical_overheat", HeroAction.REDHULK_OVERHEAT, RedHulkRules.CD_ULT);

   private final String key;
   private final HeroAction action;
   public final int cooldown;

   RedHulkAbility(String key, HeroAction action, int cooldown) {
      this.key = key;
      this.action = action;
      this.cooldown = cooldown;
   }

   public String key() {
      return this.key;
   }

   public String id() {
      return "red_hulk:" + this.key;
   }

   public HeroAction action() {
      return this.action;
   }

   public int cooldownTicks() {
      return RedHulkRules.COOLDOWN_TICKS[this.cooldown];
   }

   @Nullable
   public static RedHulkAbility byId(@Nullable String id) {
      if (id == null) {
         return null;
      }
      for (RedHulkAbility a : values()) {
         if (a.id().equals(id)) {
            return a;
         }
      }
      return null;
   }

   @Nullable
   public static RedHulkAbility byAction(HeroAction action) {
      for (RedHulkAbility a : values()) {
         if (a.action == action) {
            return a;
         }
      }
      return null;
   }
}
