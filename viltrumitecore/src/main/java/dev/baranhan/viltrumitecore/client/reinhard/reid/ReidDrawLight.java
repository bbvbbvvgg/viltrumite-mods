package dev.baranhan.viltrumitecore.client.reinhard.reid;

import dev.baranhan.viltrumitecore.client.reinhard.ReidDrawChoreo;

/**
 * REINHARD OVERHAUL item 15: the light of the Reid draw on the one draw timeline
 * ({@link ReidDrawChoreo}, draw ticks {@code d = 0..120}). Pure, so the column, the blade glow,
 * the ripples, the shake and the first-person bloom rise, tremble, flash and decay together with the
 * blade and the sound.
 *
 * <pre>
 *   0-33    nothing but the dark sky: the breath before the draw
 *   33-46   koiguchi: a thread of light leaks from the mouth and starts to climb
 *   46-86   the column grows with the exposed blade (to the sky by 70)
 *   86-92   strain: the light trembles
 *   92-96   the inhale: it dims a little before the release
 *   96      release: a flash runs up the column, a ring of light sweeps the ground
 *   96-106  the column stands at full light
 *  106-124  it decays and is gone shortly after the blade settles in the guard
 * </pre>
 */
public final class ReidDrawLight {
   public static final float LEAK = ReidDrawChoreo.KOIGUCHI - 1;
   public static final float FULL_SKY = 70;
   public static final float RELEASE = ReidDrawChoreo.RELEASE;
   /** The release surge starts this many ticks before the tip clears the mouth and peaks exactly at it. */
   public static final float ATTACK = 0.6F;
   public static final float DECAY_START = 106;
   public static final float DECAY_END = 124;
   /** Peak of the release flash on top of the standing light. */
   public static final float FLASH = 0.9F;
   /** Ring of light at the release: speed (blocks per tick) and reach (the server wave radius). */
   public static final float WAVE_SPEED = 1.6F;
   public static final float WAVE_RADIUS = 24.0F;
   /** The caster's own camera shakes less than the onlookers'. */
   public static final float CASTER_SHAKE = 0.6F;

   private ReidDrawLight() {
   }

   /** 0 → 1 over the last {@link #ATTACK} ticks before the release: every envelope crosses the release without a jump. */
   public static float attack(double d) {
      return smooth((d - (RELEASE - ATTACK)) / ATTACK);
   }

   /** 0..1 before the release: the leak, then the exposed blade's light. */
   public static float build(double d) {
      if (d < LEAK) return 0;
      float leak = smooth((d - LEAK) / 8);
      return leak * ReidDrawChoreo.glow(Math.min(d, RELEASE - 1e-3)) * 0.85F;
   }

   /** Strain tremble and the inhale, multiplying the light before the release (1 again at the release). */
   public static float tremble(double d) {
      if (d < ReidDrawChoreo.STRAIN || d >= RELEASE) return 1;
      double out = 1 - attack(d);
      double in = smooth((d - ReidDrawChoreo.STRAIN) / 2) * out;
      double flicker = 1 + 0.14 * in * Math.sin(d * 2.7) * Math.sin(d * 1.3 + 0.4);
      double inhale = 1 - 0.3 * smooth((d - ReidDrawChoreo.SNAP) / 3) * out;
      return (float)(flicker * inhale);
   }

   /** The release flash: surges over {@link #ATTACK}, 1 at the release, gone after ~12 ticks. */
   public static float flash(double d) {
      if (d < RELEASE - ATTACK) return 0;
      return (float)(attack(d) * Math.exp(-Math.max(0, d - RELEASE) / 3.5));
   }

   /** 1 until the decay, then down to 0 at {@link #DECAY_END}. */
   public static float tail(double d) {
      return 1 - smooth((d - DECAY_START) / (DECAY_END - DECAY_START));
   }

   /** Column light: rises with the blade, trembles, flashes at the release and decays. */
   public static float beam(double d) {
      float before = build(d) * tremble(d);
      float after = 0.85F + 0.15F * smooth((d - RELEASE) / 2);
      float k = attack(d);
      return (before + (after - before) * k) * tail(d) + FLASH * flash(d);
   }

   /** 0..1 of the column's height: it climbs from the leak and reaches the sky by {@link #FULL_SKY}. */
   public static float reach(double d) {
      if (d < LEAK) return 0;
      if (d >= FULL_SKY) return 1;
      return (float)Math.pow(smooth((d - LEAK) / (FULL_SKY - LEAK)), 0.6);
   }

   /** Core, glow and halo half widths (blocks) of the column. */
   public static float[] widths(double d) {
      float b = Math.min(1, beam(d)), f = flash(d);
      return new float[]{0.06F + 0.07F * b + 0.16F * f, 0.24F + 0.26F * b + 0.6F * f, 0.8F + 1.0F * b + 2.2F * f};
   }

   /** Light of the exposed blade and the mouth (0..1): the leak, the blade, the release, a lasting gleam. */
   public static float blade(double d) {
      if (d < LEAK) return 0;
      float glow = ReidDrawChoreo.glow(d);
      float after = d < RELEASE ? 1 : tail(d) * 0.6F + 0.4F;
      return Math.min(1, glow * smooth((d - LEAK) / 3) * tremble(d) * after + 0.6F * flash(d)) * (1 - smooth((d - 118) / 12));
   }

   /** Radius (blocks) of the release ring of light, or -1 when there is none. */
   public static float wave(double d) {
      if (d < RELEASE) return -1;
      float r = (float)(d - RELEASE) * WAVE_SPEED + 0.8F;
      return r > WAVE_RADIUS ? -1 : r;
   }

   /** Ticks between the air ripples (the air distortion around him): calm, then faster in the strain. */
   public static float ripplePeriod(double d) {
      return 14 - 7 * smooth((d - 46) / 40) - 2 * smooth((d - ReidDrawChoreo.STRAIN) / 2);
   }

   /** Ripples are emitted from the koiguchi until the release. */
   public static boolean ripples(double d) {
      return d >= ReidDrawChoreo.KOIGUCHI && d < RELEASE;
   }

   /** Camera shake power for an onlooker at the scene (falloff applied by the caller); continuous, so it never kicks in at once. */
   public static float shake(double d) {
      double rest = 0.12 * smooth((d - ReidDrawChoreo.REST_END) / 6);
      double pull = 0.58 * smooth((d - ReidDrawChoreo.KOIGUCHI) / (ReidDrawChoreo.STRAIN - ReidDrawChoreo.KOIGUCHI));
      double strain = 0.3 * smooth((d - ReidDrawChoreo.STRAIN) / 2);
      double inhale = 0.4 * smooth((d - ReidDrawChoreo.SNAP) / 3);
      double before = rest + pull + strain - inhale;
      double after = 2.6 * Math.exp(-Math.max(0, d - RELEASE) / 2.5);
      return (float)(before + (after - before) * attack(d));
   }

   static float smooth(double value) {
      double t = Math.max(0, Math.min(1, value));
      return (float)(t * t * (3 - 2 * t));
   }
}
