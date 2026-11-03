package dev.baranhan.viltrumitecore.hero.venom;

import javax.annotation.Nullable;

/**
 * Timeline moves of Venom (snapshot {@code actionId}, append only). Length and contact tick (-1 = none) are
 * authoritative on the server; the client poses the clip of the same name on the same clock (VenomPose). Keep in
 * sync with tools/assets/venom_clips.py (VenomClipsTest compares them).
 */
public enum VenomMove {
   TRANSFORM("transform", VenomRules.TRANSFORM_TICKS, VenomRules.STAGE_POSE, -1),
   REVERT("revert", VenomRules.REVERT_TICKS, -1, -1),
   CLAW_1("claw_1", 9, 4, 7),
   CLAW_2("claw_2", 9, 4, 7),
   CLAW_3("claw_3", 13, 6, 10),
   TENDRIL_SHOT("tendril_shot", 7, 3, 5),
   ZIP("zip", VenomRules.ZIP_MAX_TICKS, -1, 0),
   PULL("pull", 12, -1, 8),
   GRAB("grab", 8, 3, -1),
   HOLD("hold", VenomRules.HOLD_MAX_TICKS, -1, 0),
   THROW("throw", 12, 5, 9),
   SLAM("slam", 16, 8, 13),
   LASH_SWEEP("lash_sweep", 14, 6, 11),
   LASH_IMPALE("lash_impale", 14, 6, 11),
   LASH_SLAM("lash_slam", 20, 11, 16),
   SHELL("shell", VenomRules.SHELL_MAX_TICKS, -1, 0),
   RAGE("rage", 26, 10, -1),
   ENGULF("engulf", VenomRules.ENGULF_TICKS, 18, -1),
   POSSESS("possess", 300, -1, -1),
   RECOIL("recoil", VenomRules.RECOIL_TICKS, -1, -1),
   REFORM("reform", VenomRules.REFORM_TICKS, -1, 8),
   CLASH("clash", VenomRules.CLASH_TICKS + 6, 3, -1),
   TEAR_WEB("tear_web", 14, 7, -1),
   GRAB_MISS("grab_miss", 8, -1, 5);

   private final String clip;
   public final int length;
   public final int contact;
   /** From this tick another input may cut the move (-1 = never; 0 = any time). */
   public final int cancelFrom;

   VenomMove(String clip, int length, int contact, int cancelFrom) {
      this.clip = clip;
      this.length = length;
      this.contact = contact;
      this.cancelFrom = cancelFrom;
   }

   public String clip() {
      return this.clip;
   }

   @Nullable
   public static VenomMove byId(int id) {
      VenomMove[] values = values();
      return id >= 0 && id < values.length ? values[id] : null;
   }

   public static VenomMove claw(int step) {
      return values()[CLAW_1.ordinal() + Math.max(0, Math.min(VenomRules.COMBO_LENGTH - 1, step))];
   }

   public static VenomMove lash(int step) {
      return values()[LASH_SWEEP.ordinal() + Math.max(0, Math.min(2, step))];
   }

   public boolean isClaw() {
      return this == CLAW_1 || this == CLAW_2 || this == CLAW_3;
   }

   public boolean isLash() {
      return this == LASH_SWEEP || this == LASH_IMPALE || this == LASH_SLAM;
   }

   /**
    * Ends itself from its own tick (arrival or {@link VenomRules#PULL_MAX_TICKS}), not at {@link #length}: a pull
    * at the far end of the tendril needs longer than its clip.
    */
   public boolean selfEnding() {
      return this == PULL;
   }

   /** Loops its clip (held states). */
   public boolean loops() {
      return this == HOLD || this == SHELL || this == POSSESS || this == ZIP;
   }

   /** Walking input ignored (speed modifier). */
   public boolean roots() {
      return this == TRANSFORM || this == REVERT || this == ENGULF || this == POSSESS || this == RAGE || this == RECOIL || this == TEAR_WEB
         || this == CLASH;
   }

   /** Refuses every other input until it ends. */
   public boolean locks() {
      return this == TRANSFORM || this == REVERT || this == ENGULF || this == RECOIL || this == RAGE || this == CLASH;
   }

   /** Knockback does not interrupt these. */
   public boolean unstoppable() {
      return this == TRANSFORM || this == ENGULF || this == RAGE || this == SHELL || this == LASH_SLAM;
   }

   public boolean cancellable(int elapsed) {
      return this.cancelFrom >= 0 && elapsed >= this.cancelFrom;
   }
}
