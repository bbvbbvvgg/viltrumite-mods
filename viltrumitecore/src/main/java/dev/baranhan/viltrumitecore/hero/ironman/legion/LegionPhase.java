package dev.baranhan.viltrumitecore.hero.ironman.legion;

import javax.annotation.Nullable;

/** Life phase of a Legion suit (synced). Wire ordinal: append only. */
public enum LegionPhase {
   /** Coming down from the sky on its own curve. */
   APPROACH,
   /** Feet first over the slot, thrusters at full. */
   BRAKE,
   /** The per-mark ready move. */
   READY,
   /** Following orders. */
   ACTIVE,
   /** Climbing away to Veronica. */
   LEAVE,
   /** Engines failed: falling, sparking, then broken. */
   CRASH;

   private static final LegionPhase[] VALUES = values();

   public static LegionPhase byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : ACTIVE;
   }

   @Nullable
   public static LegionPhase byIdOrNull(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : null;
   }

   /** Orders and targets apply only here. */
   public boolean commandable() {
      return this == ACTIVE;
   }

   /** The suit still counts in the squad (not leaving and not crashing). */
   public boolean fighting() {
      return this == APPROACH || this == BRAKE || this == READY || this == ACTIVE;
   }
}
