package dev.baranhan.viltrumitecore.hero.budget;

import dev.baranhan.viltrumitecore.hero.HeroAction;

/**
 * The five shared mechanics of the Ultimate Hero Expansion. Every expansion
 * ability is one of these, renamed and recoloured per hero. Each type rides
 * an existing {@link HeroAction}, so no new wire ids are needed.
 */
public enum BudgetMoveType {
   /** Strike: nearest target in front, small knockback, arm swing. */
   A(HeroAction.LIGHT_PUNCH, "punch"),
   /** Energy projectile: an instant coloured beam. */
   B(HeroAction.SIGNATURE, "thunderclap"),
   /** Dash: fast forward movement. */
   C(HeroAction.STEP_DASH, "dash"),
   /** Power-up: temporary strength, speed or resistance. */
   D(HeroAction.CONTEXT_POWER, "speed"),
   /** Ultimate: a bigger strike with more damage, particles and a sound. */
   E(HeroAction.ULTIMATE, "supersonic");

   private final HeroAction action;
   private final String icon;

   BudgetMoveType(HeroAction action, String icon) {
      this.action = action;
      this.icon = icon;
   }

   public HeroAction action() {
      return this.action;
   }

   /** Shared panel icon (textures/gui/ability/&lt;icon&gt;.png); heroes reuse them. */
   public String icon() {
      return this.icon;
   }
}
