package dev.baranhan.viltrumitecore.hero;

/**
 * Look and sound of the kit chop ({@code viltrumite:chop}) for one hero
 * ({@link HeroDefinition#chopStyle}, read on both sides; must not allocate per
 * lookup — the sweep VFX reads it every frame for every player).
 * {@link #DEFAULT} is exactly the legacy chop: a 1× arc, white core with a
 * pale red edge, no air ripple, the sweep sound at 1.5 / 0.5 and the crit at
 * 1.2 / 0.8. {@code soundVolume} / {@code soundPitch} scale both sounds.
 */
public record ChopStyle(boolean forceLeft, float arcScale, int trailInner, int trailOuter, boolean ripple, float soundVolume, float soundPitch) {
   /** Legacy sweep sound (PlayerEntityCoreMixin.tickChop) and crit sound (ChopImpactManager). */
   public static final float SWEEP_VOLUME = 1.5F;
   public static final float SWEEP_PITCH = 0.5F;
   public static final float CRIT_VOLUME = 1.2F;
   public static final float CRIT_PITCH = 0.8F;
   public static final ChopStyle DEFAULT = new ChopStyle(false, 1.0F, 0xFFFFFF, 0xFFC8C8, false, 1.0F, 1.0F);

   public float sweepVolume() {
      return SWEEP_VOLUME * this.soundVolume;
   }

   public float sweepPitch() {
      return SWEEP_PITCH * this.soundPitch;
   }

   public float critVolume() {
      return CRIT_VOLUME * this.soundVolume;
   }

   public float critPitch() {
      return CRIT_PITCH * this.soundPitch;
   }

   /** The side the server uses: a style that forces the left hand overrides the packet. */
   public static boolean resolveLeft(boolean packetLeft, ChopStyle style) {
      return style.forceLeft() || packetLeft;
   }
}
