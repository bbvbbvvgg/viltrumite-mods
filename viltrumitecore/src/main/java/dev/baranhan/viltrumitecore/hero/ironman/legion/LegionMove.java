package dev.baranhan.viltrumitecore.hero.ironman.legion;

/**
 * One-shot moves the client animates (synced id + start tick). The clip names
 * in {@code animations/ironman/legion_suit.animation.json} are {@link #clip()}.
 * Wire ordinal: append only.
 */
public enum LegionMove {
   NONE(""),
   READY("ready"),
   FIRE_RIGHT("fire_right"),
   FIRE_LEFT("fire_left"),
   FIRE_BOTH("fire_both"),
   MISSILES("missiles"),
   SIGNATURE("signature"),
   DODGE_LEFT("dodge_left"),
   DODGE_RIGHT("dodge_right"),
   HIT("hit"),
   LAND("land"),
   TAKEOFF("takeoff");

   private static final LegionMove[] VALUES = values();
   private final String clip;

   LegionMove(String clip) {
      this.clip = clip;
   }

   public String clip() {
      return this.clip;
   }

   public static LegionMove byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
   }

   /** Length of the move in ticks (the overlay fades out after it). */
   public int length() {
      return switch (this) {
         case NONE -> 0;
         case READY -> LegionRules.READY_TICKS;
         case FIRE_RIGHT, FIRE_LEFT -> 8;
         case FIRE_BOTH -> 10;
         case MISSILES -> 16;
         case SIGNATURE -> 20;
         case DODGE_LEFT, DODGE_RIGHT -> 10;
         case HIT -> 8;
         case LAND -> 12;
         case TAKEOFF -> 14;
      };
   }

   /** Firing moves own the arms (aim); the rest are whole-body overlays. */
   public boolean aims() {
      return this == FIRE_RIGHT || this == FIRE_LEFT || this == FIRE_BOTH || this == SIGNATURE;
   }
}
