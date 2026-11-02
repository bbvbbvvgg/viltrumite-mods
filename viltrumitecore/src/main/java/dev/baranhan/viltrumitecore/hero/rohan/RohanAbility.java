package dev.baranhan.viltrumitecore.hero.rohan;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import javax.annotation.Nullable;

/** Rohan's panel abilities in slot order (R, Y, Z, V, B, N). {@code cooldown} indexes the state cooldowns. */
public enum RohanAbility {
   DOOR("heavens_door", HeroAction.ROHAN_DOOR, RohanRules.CD_DOOR),
   SKETCH("air_sketch", HeroAction.ROHAN_SKETCH, RohanRules.CD_SKETCH),
   NIBS("pen_nibs", HeroAction.ROHAN_NIBS, RohanRules.CD_NIBS),
   COUNTER("faster_than_a_punch", HeroAction.ROHAN_COUNTER, RohanRules.CD_COUNTER),
   SELF_EDIT("self_edit", HeroAction.ROHAN_SELF_EDIT, RohanRules.CD_SELF_EDIT),
   MANUSCRIPT("manuscript", HeroAction.ROHAN_MANUSCRIPT, RohanRules.CD_MANUSCRIPT);

   private final String key;
   private final HeroAction action;
   public final int cooldown;

   RohanAbility(String key, HeroAction action, int cooldown) {
      this.key = key;
      this.action = action;
      this.cooldown = cooldown;
   }

   public String key() {
      return this.key;
   }

   public String id() {
      return "rohan:" + this.key;
   }

   public HeroAction action() {
      return this.action;
   }

   public int cooldownTicks() {
      return RohanRules.COOLDOWN_TICKS[this.cooldown];
   }

   /** Ink (or, for the Manuscript, Inspiration) the ability needs to start. */
   public float cost() {
      return switch (this) {
         case DOOR -> RohanRules.COST_DOOR;
         case SKETCH -> RohanRules.COST_SKETCH;
         case NIBS -> RohanRules.COST_NIBS;
         case COUNTER -> RohanRules.COST_COUNTER;
         case SELF_EDIT -> RohanRules.COST_SELF_EDIT;
         case MANUSCRIPT -> RohanRules.MANUSCRIPT_COST;
      };
   }

   /** The ultimate pays with Inspiration, everything else with Ink. */
   public boolean usesInspiration() {
      return this == MANUSCRIPT;
   }

   @Nullable
   public static RohanAbility byId(@Nullable String id) {
      if (id == null) {
         return null;
      }
      for (RohanAbility a : values()) {
         if (a.id().equals(id)) {
            return a;
         }
      }
      return null;
   }

   @Nullable
   public static RohanAbility byAction(HeroAction action) {
      for (RohanAbility a : values()) {
         if (a.action == action) {
            return a;
         }
      }
      return null;
   }
}
