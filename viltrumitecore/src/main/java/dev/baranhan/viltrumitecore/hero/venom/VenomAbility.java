package dev.baranhan.viltrumitecore.hero.venom;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import javax.annotation.Nullable;

/** Panel abilities of Venom in slot order (R, Y, Z, V, B, N). {@code cooldown} indexes the state cooldowns. */
public enum VenomAbility {
   SYMBIOSIS("symbiosis", HeroAction.VENOM_SYMBIOSIS, VenomRules.CD_TRANSFORM),
   TENDRIL("tendril", HeroAction.VENOM_TENDRIL, VenomRules.CD_TENDRIL),
   LASH("tentacle_lash", HeroAction.VENOM_LASH, VenomRules.CD_LASH),
   SHELL("symbiote_shell", HeroAction.VENOM_SHELL, VenomRules.CD_SHELL),
   RAGE("feeding_frenzy", HeroAction.VENOM_RAGE, VenomRules.CD_RAGE),
   TAKEOVER("host_takeover", HeroAction.VENOM_TAKEOVER, VenomRules.CD_TAKEOVER);

   private final String key;
   private final HeroAction action;
   public final int cooldown;

   VenomAbility(String key, HeroAction action, int cooldown) {
      this.key = key;
      this.action = action;
      this.cooldown = cooldown;
   }

   public String key() {
      return this.key;
   }

   public String id() {
      return "venom:" + this.key;
   }

   public HeroAction action() {
      return this.action;
   }

   public int cooldownTicks() {
      return VenomRules.COOLDOWN_TICKS[this.cooldown];
   }

   /** Only the transformation itself works for the bare host. */
   public boolean human() {
      return this == SYMBIOSIS;
   }

   @Nullable
   public static VenomAbility byId(@Nullable String id) {
      if (id == null) {
         return null;
      }
      for (VenomAbility a : values()) {
         if (a.id().equals(id)) {
            return a;
         }
      }
      return null;
   }

   @Nullable
   public static VenomAbility byAction(HeroAction action) {
      for (VenomAbility a : values()) {
         if (a.action == action) {
            return a;
         }
      }
      return null;
   }
}
