package dev.baranhan.viltrumitecore.hero.jinwoo;

/**
 * Timeline moves of Jinwoo's body (snapshot {@code actionId}). Each has an
 * authoritative length and contact tick; the client picks clips by {@link #clip()}.
 */
public enum JinwooMove {
   COMBO_1("combo_1", JinwooRules.COMBO_LENGTH[0], JinwooRules.COMBO_CONTACT[0]),
   COMBO_2("combo_2", JinwooRules.COMBO_LENGTH[1], JinwooRules.COMBO_CONTACT[1]),
   COMBO_3("combo_3", JinwooRules.COMBO_LENGTH[2], JinwooRules.COMBO_CONTACT[2]),
   COMBO_4("combo_4", JinwooRules.COMBO_LENGTH[3], JinwooRules.COMBO_CONTACT[3]),
   COMBO_5("combo_5", JinwooRules.COMBO_LENGTH[4], JinwooRules.COMBO_CONTACT[4]),
   LOW_SWEEP("low_sweep", JinwooRules.LOW_LENGTH, JinwooRules.LOW_CONTACT),
   AIR_1("air_1", JinwooRules.COMBO_LENGTH[0], JinwooRules.COMBO_CONTACT[0]),
   AIR_2("air_2", JinwooRules.COMBO_LENGTH[1], JinwooRules.COMBO_CONTACT[1]),
   DIVE("dive", JinwooRules.DIVE_MAX_TICKS, -1),
   DIVE_LAND("dive_land", 8, 0),
   RIPOSTE("riposte", 6, 2),
   DRAW("draw", JinwooRules.DRAW_TICKS, JinwooRules.DRAW_TICKS),
   SHEATH("sheath", JinwooRules.SHEATH_TICKS, JinwooRules.SHEATH_TICKS),
   STEP("step", JinwooRules.STEP_TICKS + 4, 0),
   RUSH("mutilation", JinwooRules.RUSH_LENGTH, JinwooRules.RUSH_FINAL_TICK),
   AUTHORITY_GRIP("authority_grip", JinwooRules.AUTHORITY_HOLD, 0),
   AUTHORITY_SLAM("authority_slam", JinwooRules.AUTHORITY_SLAM_TICKS, 4),
   EXTRACT("extraction", JinwooRules.EXTRACT_CHANNEL + 6, JinwooRules.EXTRACT_CHANNEL),
   EXCHANGE("exchange", JinwooRules.EXCHANGE_SINK * 2, JinwooRules.EXCHANGE_SINK),
   ARISE("arise", JinwooRules.ULT_LENGTH, JinwooRules.ULT_WAVE),
   SUMMON("summon", 16, 6),
   RECALL("recall", JinwooRules.RECALL_TICKS, 0),
   COMMAND_ATTACK("command_attack", 12, 4),
   COMMAND_GUARD("command_guard", 12, 4),
   COMMAND_SENTINEL("command_sentinel", 12, 4),
   CHARGE("monarch_charge", JinwooRules.CHARGE_TICKS + 6, JinwooRules.CHARGE_TICKS);

   private final String clip;
   public final int length;
   public final int contact;

   JinwooMove(String clip, int length, int contact) {
      this.clip = clip;
      this.length = length;
      this.contact = contact;
   }

   public String clip() {
      return this.clip;
   }

   public static JinwooMove byId(int id) {
      JinwooMove[] values = values();
      return id >= 0 && id < values.length ? values[id] : null;
   }

   public static JinwooMove combo(int step) {
      return values()[COMBO_1.ordinal() + Math.max(0, Math.min(4, step))];
   }

   public boolean isCombo() {
      return this.ordinal() <= COMBO_5.ordinal() || this == LOW_SWEEP || this == AIR_1 || this == AIR_2;
   }

   /** Moves that knockback does not interrupt (spec §4: Mutilation, Charge, ultimate). */
   public boolean unstoppable() {
      return this == RUSH || this == CHARGE || this == ARISE;
   }

   /** Moves that root the body (no walking input). */
   public boolean roots() {
      return this == EXTRACT || this == EXCHANGE || this == ARISE || this == AUTHORITY_SLAM;
   }
}
