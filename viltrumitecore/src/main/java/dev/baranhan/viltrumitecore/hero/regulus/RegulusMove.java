package dev.baranhan.viltrumitecore.hero.regulus;

import javax.annotation.Nullable;

/**
 * Regulus' timed body moves. The ordinal is the snapshot {@code actionId} (hero-owned wire value,
 * append only); the length is the authoritative clip length in ticks shared by every view
 * (checked against the generated clips by RegulusClipsTest). {@code contact} is the tick on which
 * the move acts on the world (hit, release, bind); -1 for none.
 */
public enum RegulusMove {
   LION_ON(RegulusRules.LION_ON_TICKS, RegulusRules.LION_PROTECT_AT, "lion_on"),
   LION_OFF(RegulusRules.LION_OFF_TICKS, -1, "lion_off"),
   SEIZURE(RegulusRules.SEIZURE_TICKS, -1, "seizure"),
   TOUCH_BACKHAND(12, 5, "touch_backhand"),
   TOUCH_PALM(13, 6, "touch_palm"),
   TOUCH_FLICK(16, 8, "touch_flick"),
   SAND(24, RegulusRules.SAND_RELEASE, "sand"),
   AIR_CUT(26, RegulusRules.SEVER_RELEASE, "air_cut"),
   AIR_CUT_VERTICAL(26, RegulusRules.SEVER_RELEASE, "air_cut_vertical"),
   TRAMPLE(RegulusRules.TRAMPLE_TICKS, RegulusRules.TRAMPLE_IMPACT, "trample"),
   REPLY(RegulusRules.REPLY_TICKS, -1, "reply"),
   REPLY_STRIKE(RegulusRules.REPLY_STRIKE_TICKS, RegulusRules.REPLY_STRIKE_CONTACT, "reply_strike"),
   LITTLE_KING(RegulusRules.KING_CAST, RegulusRules.KING_BIND_AT, "little_king"),
   REFUSE(10, -1, "refuse"),
   ULT(RegulusRules.ULT_LENGTH, RegulusRules.ULT_FLICK_CONTACT, "ult");

   private final int length;
   private final int contact;
   private final String clip;

   RegulusMove(int length, int contact, String clip) {
      this.length = length;
      this.contact = contact;
      this.clip = clip;
   }

   public int length() {
      return this.length;
   }

   public int contact() {
      return this.contact;
   }

   public String clip() {
      return this.clip;
   }

   public boolean isTouch() {
      return this == TOUCH_BACKHAND || this == TOUCH_PALM || this == TOUCH_FLICK;
   }

   /** Moves another cast may not interrupt (the body is committed). */
   public boolean locksCasting() {
      return this == ULT || this == SEIZURE || this == LION_ON || this == TRAMPLE;
   }

   /** Touch combo step 0..2 → move. */
   public static RegulusMove touch(int step) {
      return switch (Math.floorMod(step, 3)) {
         case 0 -> TOUCH_BACKHAND;
         case 1 -> TOUCH_PALM;
         default -> TOUCH_FLICK;
      };
   }

   /** Base / Lion damage of a touch step. */
   public float touchDamage(boolean lion) {
      return switch (this) {
         case TOUCH_BACKHAND -> lion ? 10.0F : 5.0F;
         case TOUCH_PALM -> lion ? 12.0F : 6.0F;
         case TOUCH_FLICK -> lion ? 14.0F : 8.0F;
         default -> 0.0F;
      };
   }

   @Nullable
   public static RegulusMove byId(int id) {
      RegulusMove[] values = values();
      return id >= 0 && id < values.length ? values[id] : null;
   }
}
