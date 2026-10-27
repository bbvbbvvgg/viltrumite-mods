package dev.baranhan.viltrumitecore.hero.thor.scene;

import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import javax.annotation.Nullable;

/**
 * When the visual grab window opens (decisions 35, 52, 57): only on a landed
 * hit of a normal LMB/RMB combo, at random, at most once per combo. A combo
 * ends on COMBO_END (B) or, without B, after {@code comboIdleTicks} without
 * a hit. Pure: the caller rolls the random number.
 */
public final class GrabWindowRule {
   /** Per-Thor combo memory. */
   public static final class Combo {
      boolean used;
      long lastHit = Long.MIN_VALUE;

      public boolean used() {
         return this.used;
      }
   }

   private GrabWindowRule() {
   }

   /** Normal combo moves only: no electric discharge, no MMB melee, no throw, no ultimate. */
   public static boolean comboMove(@Nullable ThorMove move) {
      return move == ThorMove.HAMMER_COMBO || move == ThorMove.FIST_COMBO;
   }

   /**
    * A combo hit landed at {@code now}; {@code roll} in [0, 1). True = open the
    * window now (and remember it for this combo).
    */
   public static boolean onHit(Combo combo, @Nullable ThorMove move, long now, float roll, float chance, int idleTicks, boolean targetValid) {
      if (combo.lastHit != Long.MIN_VALUE && now - combo.lastHit > idleTicks) {
         combo.used = false;
      }

      if (!comboMove(move)) {
         return false;
      }

      combo.lastHit = now;
      if (combo.used || !targetValid || roll >= chance) {
         return false;
      }

      combo.used = true;
      return true;
   }

   public static void onComboEnd(Combo combo) {
      combo.used = false;
      combo.lastHit = Long.MIN_VALUE;
   }
}
