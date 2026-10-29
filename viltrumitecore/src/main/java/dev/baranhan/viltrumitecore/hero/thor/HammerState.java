package dev.baranhan.viltrumitecore.hero.thor;

import java.util.Locale;

/**
 * Where Mjölnir is. Ordinals travel in ThorFlags bits 0-1: HOLSTERED is 0,
 * so an empty snapshot shows the hammer on the belt. Append only.
 */
public enum HammerState {
   /** On the belt (right hip). */
   HOLSTERED,
   /** In the right hand. */
   IN_HAND,
   /** Flying away after a throw (not produced yet). */
   THROWN,
   /** Flying back to the hand (not produced yet). */
   RETURNING;

   private static final HammerState[] VALUES = values();

   public boolean away() {
      return this == THROWN || this == RETURNING;
   }

   public String key() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public static HammerState byOrdinal(int ordinal) {
      return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : HOLSTERED;
   }

   /** Saved key; unknown keys load as HOLSTERED. */
   public static HammerState fromKey(String key) {
      for (HammerState state : VALUES) {
         if (state.key().equals(key)) {
            return state;
         }
      }

      return HOLSTERED;
   }
}
