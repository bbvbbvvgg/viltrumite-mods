package dev.baranhan.viltrumitecore.hero.captain;

import javax.annotation.Nullable;

/**
 * Timeline moves of Steve's body (snapshot {@code actionId}, append only). Each has an authoritative length and
 * contact tick (-1 = none / dynamic); the client picks the clip by {@link #clip()} on the same clock.
 */
public enum CaptainMove {
   SHIELD_1("shield_1", 9, 4),
   SHIELD_2("shield_2", 10, 5),
   SHIELD_3("shield_3", 11, 6),
   SHIELD_4("shield_4", 18, 12),
   FIST_1("fist_1", 7, 3),
   FIST_2("fist_2", 8, 4),
   FIST_3("fist_3", 12, 7),
   COUNTER("counter", 13, 6),
   RETURN_STRIKE("return_strike", 12, 6),
   THROW("throw", 10, CaptainRules.THROW_RELEASE_TICK),
   AIM("aim", CaptainRules.AIM_MAX, -1),
   CATCH("catch", 9, 0),
   RECALL("recall", 7, 2),
   RUSH("rush", CaptainRules.RUSH_TICKS, -1),
   RUSH_BASH("rush_bash", 9, 2),
   SLAM_LEAP("slam_leap", CaptainRules.SLAM_MAX_TICKS, -1),
   SLAM_IMPACT("slam_impact", 14, 0),
   ROLL("roll", CaptainRules.ROLL_TICKS, -1),
   PARRY("parry", 8, 0),
   ASSEMBLE_CALL("assemble_call", CaptainRules.CALL_TICKS, 8),
   ASSEMBLE_STAND("assemble_stand", CaptainRules.FORM_DEADLINE + CaptainRules.CIRCLE_TICKS - CaptainRules.CALL_TICKS, -1),
   LANDING("landing", 12, 0),
   ROLL_SWEEP("roll_sweep", 12, 6);

   private final String clip;
   public final int length;
   public final int contact;

   CaptainMove(String clip, int length, int contact) {
      this.clip = clip;
      this.length = length;
      this.contact = contact;
   }

   public String clip() {
      return this.clip;
   }

   @Nullable
   public static CaptainMove byId(int id) {
      CaptainMove[] values = values();
      return id >= 0 && id < values.length ? values[id] : null;
   }

   public static CaptainMove shield(int step) {
      return values()[SHIELD_1.ordinal() + Math.max(0, Math.min(3, step))];
   }

   public static CaptainMove fist(int step) {
      return values()[FIST_1.ordinal() + Math.max(0, Math.min(2, step))];
   }

   public boolean isCombo() {
      return this.ordinal() <= FIST_3.ordinal() || this == COUNTER || this == RETURN_STRIKE || this == ROLL_SWEEP;
   }

   /** Moves that knockback does not interrupt. */
   public boolean unstoppable() {
      return this == RUSH || this == SLAM_LEAP || this == SLAM_IMPACT || this == ASSEMBLE_CALL || this == ASSEMBLE_STAND;
   }

   /** Moves during which walking input is ignored (speed modifier). */
   public boolean roots() {
      return this == ASSEMBLE_CALL || this == ASSEMBLE_STAND || this == SLAM_IMPACT || this == RUSH_BASH;
   }

   /** Moves that need the shield in the hand. */
   public boolean usesShield() {
      return switch (this) {
         case SHIELD_1, SHIELD_2, SHIELD_3, SHIELD_4, COUNTER, THROW, AIM, RUSH_BASH, SLAM_LEAP, SLAM_IMPACT, PARRY, ASSEMBLE_STAND -> true;
         default -> false;
      };
   }
}
