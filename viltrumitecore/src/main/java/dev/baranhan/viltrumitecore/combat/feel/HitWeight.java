package dev.baranhan.viltrumitecore.combat.feel;

/**
 * Weight ladder of a landed hit (wave1-22): hit-stop ticks, shake profile and trauma, whether it
 * earns a manga impact frame, and the strength of the air ring (0 = none). Heavier never stops
 * shorter than lighter (unit-tested).
 */
public enum HitWeight {
   LIGHT(1, ShakeProfile.LIGHT_HIT, 0.45F, false, 0.0F),
   MEDIUM(2, ShakeProfile.LIGHT_HIT, 0.8F, false, 0.0F),
   HEAVY(4, ShakeProfile.HEAVY_HIT, 0.85F, false, 0.45F),
   CRUSHING(6, ShakeProfile.SLAM, 0.9F, true, 0.8F),
   FINISHER(8, ShakeProfile.FINISHER, 1.0F, true, 1.0F);

   public final int hitStopTicks;
   public final ShakeProfile shake;
   public final float trauma;
   public final boolean impactFrame;
   public final float wave;

   HitWeight(int hitStopTicks, ShakeProfile shake, float trauma, boolean impactFrame, float wave) {
      this.hitStopTicks = hitStopTicks;
      this.shake = shake;
      this.trauma = trauma;
      this.impactFrame = impactFrame;
      this.wave = wave;
   }
}
