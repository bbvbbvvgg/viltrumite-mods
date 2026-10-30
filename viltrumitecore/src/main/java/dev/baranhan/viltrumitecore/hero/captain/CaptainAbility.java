package dev.baranhan.viltrumitecore.hero.captain;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import javax.annotation.Nullable;

/** Captain's panel abilities in slot order (spec §5.3-5.7). {@code cooldown} indexes CaptainState.cooldowns. */
public enum CaptainAbility {
   THROW("ricochet", HeroAction.CAPTAIN_THROW, CaptainRules.CD_THROW),
   RUSH("shield_rush", HeroAction.CAPTAIN_RUSH, CaptainRules.CD_RUSH),
   SLAM("vibranium_slam", HeroAction.CAPTAIN_SLAM, CaptainRules.CD_SLAM),
   ROLL("tactical_roll", HeroAction.CAPTAIN_ROLL, CaptainRules.CD_ROLL),
   ASSEMBLE("assemble", HeroAction.CAPTAIN_ASSEMBLE, CaptainRules.CD_ULT);

   private final String key;
   private final HeroAction action;
   public final int cooldown;

   CaptainAbility(String key, HeroAction action, int cooldown) {
      this.key = key;
      this.action = action;
      this.cooldown = cooldown;
   }

   public String key() {
      return this.key;
   }

   public String id() {
      return "captain:" + this.key;
   }

   public HeroAction action() {
      return this.action;
   }

   @Nullable
   public static CaptainAbility byId(@Nullable String id) {
      if (id == null) {
         return null;
      }
      for (CaptainAbility a : values()) {
         if (a.id().equals(id)) {
            return a;
         }
      }
      return null;
   }

   @Nullable
   public static CaptainAbility byAction(HeroAction action) {
      for (CaptainAbility a : values()) {
         if (a.action == action) {
            return a;
         }
      }
      return null;
   }
}
