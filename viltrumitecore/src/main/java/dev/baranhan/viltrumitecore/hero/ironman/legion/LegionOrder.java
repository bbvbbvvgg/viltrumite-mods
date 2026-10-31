package dev.baranhan.viltrumitecore.hero.ironman.legion;

import java.util.Locale;
import javax.annotation.Nullable;

/** Squad orders (design §4). The wire uses the ordinal: append only. */
public enum LegionOrder {
   /** Wedge behind Tony; only threats close to him are engaged. */
   FOLLOW,
   /** Default: a ring around Tony, interception inside the guard radius. */
   GUARD,
   /** Tony's crosshair target (or the top threat) first. */
   ATTACK,
   /** Hold the point under Tony's crosshair. */
   HOLD,
   /** Fly back to Veronica. */
   RETURN;

   private static final LegionOrder[] VALUES = values();

   public String key() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   @Nullable
   public static LegionOrder byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : null;
   }

   /** Radius around the anchor (Tony or the held point) in which threats are engaged. */
   public double engageRadius() {
      return switch (this) {
         case FOLLOW -> LegionRules.FOLLOW_RADIUS;
         case GUARD -> LegionRules.GUARD_RADIUS;
         case ATTACK -> LegionRules.SCAN_RADIUS;
         case HOLD -> LegionRules.HOLD_RADIUS;
         case RETURN -> 0.0;
      };
   }
}
