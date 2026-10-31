package dev.baranhan.viltrumitecore.hero.reinhard;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import java.util.Locale;

/**
 * Reinhard's own abilities. Append only: the ordinal is the action id.
 * Cooldown wire slots use snapshotIndex() because mode/Phoenix slots are reserved.
 */
public enum ReinhardAbility {
   LIGHT_PUNCH(ReinhardMode.RESTRAINED, 0, HeroAction.LIGHT_PUNCH, 3),
   STEP_DASH(ReinhardMode.RESTRAINED, 1, HeroAction.STEP_DASH, 3),
   CATCH(ReinhardMode.RESTRAINED, 2, HeroAction.CATCH_PROJECTILE, 3),
   FIST_COMBO(ReinhardMode.KNIGHT, 0, HeroAction.FIST_COMBO, 3),
   SKY_KICK(ReinhardMode.KNIGHT, 1, HeroAction.SKY_KICK, 3),
   GRAB_THROW(ReinhardMode.KNIGHT, 2, HeroAction.GRAB_THROW, 3),
   DASH_BEHIND(ReinhardMode.KNIGHT, 3, HeroAction.DASH_BEHIND, 3),
   STOMP(ReinhardMode.KNIGHT, 4, HeroAction.STOMP, 3),
   REFLECT(ReinhardMode.KNIGHT, 5, HeroAction.REFLECT_PROJECTILE, 3),
   REID(ReinhardMode.HOLY_SWORD, 0, HeroAction.REID, 4),
   SHEATH_STRIKE(ReinhardMode.HOLY_SWORD, 2, HeroAction.SHEATH_STRIKE, 4),
   SAW_SPHERE(ReinhardMode.HOLY_SWORD, 3, HeroAction.SAW_SPHERE, 4),
   WORLD_CUT(ReinhardMode.HOLY_SWORD, 4, HeroAction.WORLD_CUT, 5),
   WORLD_ERASURE(ReinhardMode.HOLY_SWORD, 5, HeroAction.WORLD_ERASURE, 6),
   DIAGONAL_WORLD_CUT(ReinhardMode.HOLY_SWORD, 2, HeroAction.DIAGONAL_WORLD_CUT, 6),
   REID_STRIKE(ReinhardMode.HOLY_SWORD, 0, HeroAction.PRIMARY_ATTACK, 6),
   /**
    * Secret (Shift + N, every mode): never on a page, never active, never in the panel or the
    * loadout (slot −1). Started only by ReinhardFinalPunchC2SPacket; see finalpunch/FinalPunch.
    */
   FINAL_PUNCH(ReinhardMode.RESTRAINED, -1, HeroAction.FINAL_PUNCH, 6);

   private final String name;
   private final String id;
   private final ReinhardMode mode;
   private final int slot;
   private final HeroAction action;
   private final int stage;

   ReinhardAbility(ReinhardMode mode, int slot, HeroAction action, int stage) {
      this.name = this.name().toLowerCase(Locale.ROOT);
      this.id = "reinhard:" + this.name;
      this.mode = mode;
      this.slot = slot;
      this.action = action;
      this.stage = stage;
   }

   /** Snapshot slots 14/15 are already mode/Phoenix; new cooldowns append after them. */
   public int snapshotIndex() { return ordinal()<14 ? ordinal() : ordinal()+2; }

   /** Snake-case name: icon file and lang suffix. */
   public String key() {
      return this.name;
   }

   public String id() {
      return this.id;
   }

   public ReinhardMode mode() {
      return this.mode;
   }

   /** 0-based slot on the mode's page. */
   public int slot() {
      return this.slot;
   }

   public HeroAction action() {
      return this.action;
   }

   /** Plan stage that implements the ability. */
   public int stage() {
      return this.stage;
   }

   public boolean shipped() {
      return this.stage <= ReinhardAbilities.SHIPPED_STAGE;
   }
}
