package dev.baranhan.viltrumitecore.entity.jinwoo;

/** Behaviour modes of a shadow (spec §6.2). */
public enum ShadowMode {
   FOLLOW, ATTACK, GUARD, SENTINEL, CHARGE;

   public static ShadowMode byOrdinal(int ordinal) {
      ShadowMode[] values = values();
      return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FOLLOW;
   }
}
