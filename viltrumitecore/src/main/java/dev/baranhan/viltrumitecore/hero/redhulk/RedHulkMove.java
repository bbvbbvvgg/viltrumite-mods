package dev.baranhan.viltrumitecore.hero.redhulk;

import javax.annotation.Nullable;

/**
 * Timeline moves of the Red Hulk (snapshot {@code actionId}, append only). Length and contact tick (-1 = none /
 * dynamic) are authoritative on the server; the client plays the clip of the same name on the same clock.
 * Mirrored by MOVES in tools/assets/red_hulk_clips.py (RedHulkClipsTest compares them).
 */
public enum RedHulkMove {
   JAB_L("jab_l", 9, 4, 6),
   JAB_R("jab_r", 10, 5, 7),
   HOOK("hook", 12, 6, 8),
   UPPERCUT("uppercut", 13, 7, 9),
   HAMMER("hammer", 20, 12, 15),
   GRAB("grab", 9, 4, -1),
   /** Holding a body at arm's length (loops while the server holds it). */
   HOLD("hold", 20, -1, -1),
   THROW("throw", 12, 6, 9),
   GRAB_MISS("grab_miss", 10, -1, 5),
   CLAP("clap", 16, 9, 12),
   SEISMIC("seismic", 26, 15, 19),
   LEAP_CROUCH("leap_crouch", 10, 10, -1),
   LEAP_AIR("leap_air", 20, -1, -1),
   LEAP_LAND("leap_land", 16, 0, 8),
   BLAST("blast", 32, 12, 26),
   FURY("fury", 24, 10, 16),
   ULT_CHARGE("ult_charge", 60, -1, -1),
   ULT_RELEASE("ult_release", 18, 2, -1),
   ULT_RECOVER("ult_recover", 50, -1, -1),
   VENT("vent", 30, RedHulkRules.VENT_BURST_TICK, -1),
   STAGGER("stagger", 14, -1, 8),
   LANDING("landing", 12, 0, 4),
   ENTER("enter", 30, 12, 14);

   private final String clip;
   public final int length;
   public final int contact;
   /** From this tick on another ability, the next punch or a jump may cut the recovery short (-1 = never). */
   public final int cancelFrom;

   RedHulkMove(String clip, int length, int contact, int cancelFrom) {
      this.clip = clip;
      this.length = length;
      this.contact = contact;
      this.cancelFrom = cancelFrom;
   }

   public String clip() {
      return this.clip;
   }

   @Nullable
   public static RedHulkMove byId(int id) {
      RedHulkMove[] values = values();
      return id >= 0 && id < values.length ? values[id] : null;
   }

   public static RedHulkMove combo(int step) {
      return values()[JAB_L.ordinal() + Math.max(0, Math.min(RedHulkRules.COMBO_LENGTH - 1, step))];
   }

   public int comboStep() {
      return this.ordinal() <= HAMMER.ordinal() ? this.ordinal() : -1;
   }

   public boolean isCombo() {
      return this.ordinal() <= HAMMER.ordinal();
   }

   /** The clip loops while the server keeps the move (hold, flight, the ultimate charge). */
   public boolean loops() {
      return this == HOLD || this == LEAP_AIR;
   }

   /** Knockback does not interrupt these. */
   public boolean unstoppable() {
      return switch (this) {
         case HAMMER, SEISMIC, LEAP_CROUCH, LEAP_AIR, LEAP_LAND, FURY, ULT_CHARGE, ULT_RELEASE, VENT, ENTER -> true;
         default -> false;
      };
   }

   /** Walking input is ignored (speed modifier). */
   public boolean roots() {
      return switch (this) {
         case SEISMIC, LEAP_CROUCH, LEAP_LAND, BLAST, FURY, ULT_CHARGE, ULT_RELEASE, VENT, ENTER, CLAP -> true;
         default -> false;
      };
   }

   /** Moves that refuse every other input until they end (the ultimate, venting, the entrance). */
   public boolean locks() {
      return this == ULT_CHARGE || this == ULT_RELEASE || this == ULT_RECOVER || this == VENT;
   }

   /** May {@code elapsed} be cut short by a new input? */
   public boolean cancellable(int elapsed) {
      return this.cancelFrom >= 0 && elapsed >= this.cancelFrom;
   }

   public boolean ultimate() {
      return this == ULT_CHARGE || this == ULT_RELEASE || this == ULT_RECOVER;
   }
}
