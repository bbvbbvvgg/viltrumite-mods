package dev.baranhan.viltrumitecore.client.reinhard.finalpunch;

import dev.baranhan.viltrumitecore.hero.reinhard.finalpunch.FinalPunchRules;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure client timeline of THE FINAL PUNCH (scene ticks, 20 t/s, the server's
 * {@link FinalPunchRules} beats): every envelope the camera, the audio, the fist layers, the
 * chaos and the screen grade read. No Minecraft client state here (unit tested).
 */
public final class FinalPunchCues {
   /** Client tail after the scene: smoke, fades and the body's exit blend. */
   public static final int TAIL = 24;
   /** A cancel fades every layer out in this many ticks. */
   public static final int CANCEL_FADE = 10;
   /** The camera blends from / back to the player's own view. */
   public static final int BLEND_IN = FinalPunchRules.CAMERA_IN;
   public static final int BLEND_OUT = FinalPunchRules.LENGTH - FinalPunchRules.CAMERA_OUT;
   /** The body clip blends in over these ticks (from whatever pose he had: idle, crouch, walk). */
   public static final int BODY_IN = 8;
   /** Ring pulses (Phase III–IV): first tick and interval. */
   public static final int RING_PULSE_START = FinalPunchRules.RINGS;
   public static final int RING_PULSE_INTERVAL = 24;
   public static final int RING_PULSE_TICKS = 14;
   /** Ground air waves (Phase IV). */
   public static final int AIR_WAVE_INTERVAL = 16;
   public static final int AIR_WAVE_TICKS = 26;
   /** Hard camera cuts (everything else blends). */
   public static final int[] CUTS = {FinalPunchRules.SILENCE, FinalPunchRules.CONTACT};

   /** One scheduled one-shot of the client audio (the impact, jaw and clang come from the server). */
   public record Cue(int tick, String sound, float volume, float pitch, boolean atFist) {
   }

   private static final List<Cue> CUES;

   static {
      List<Cue> cues = new ArrayList<>();
      // Phase III: first sparks, the fist catches fire, the rings answer, the arcs crawl.
      for (int t = FinalPunchRules.SPARKS; t < FinalPunchRules.FIRE; t += 22) cues.add(new Cue(t, "spark", 0.45F, 1.25F, true));
      cues.add(new Cue(FinalPunchRules.FIRE, "ignite", 1.0F, 1.0F, true));
      for (int t = RING_PULSE_START; t < FinalPunchRules.SILENCE; t += RING_PULSE_INTERVAL) {
         cues.add(new Cue(t, "ring", 0.7F + 0.5F * (float) (t - RING_PULSE_START) / (FinalPunchRules.SILENCE - RING_PULSE_START), 1.0F - 0.15F * (float) (t - RING_PULSE_START) / (FinalPunchRules.SILENCE - RING_PULSE_START), true));
      }
      int[] arcs = {FinalPunchRules.ARCS, 257, 266, 281, 293, 301, 314, 322, 333, 341, 352, 359, 366, 374, 381, 387, 392};
      for (int t : arcs) cues.add(new Cue(t, "spark", 0.55F + 0.35F * (float) (t - FinalPunchRules.ARCS) / (FinalPunchRules.SILENCE - FinalPunchRules.ARCS), 0.85F + 0.03F * (t % 7), true));
      // Phase IV: the pressure of reality (cut by the silence).
      cues.add(new Cue(300, "pressure", 1.2F, 1.0F, false));
      // Phase V: the only sounds of the silence.
      cues.add(new Cue(FinalPunchRules.SILENCE, "crackle", 0.55F, 1.0F, true));
      cues.add(new Cue(FinalPunchRules.STRIKE, "whoosh", 1.1F, 0.8F, true));
      // Phase VII: the lowered fist hisses.
      cues.add(new Cue(FinalPunchRules.LAUNCH + 2, "smoke", 0.8F, 1.0F, true));
      cues.sort((a, b) -> Integer.compare(a.tick(), b.tick()));
      CUES = Collections.unmodifiableList(cues);
   }

   private FinalPunchCues() {
   }

   /** Every client one-shot, sorted by tick. */
   public static List<Cue> cues() {
      return CUES;
   }

   /** Cues that start on this whole tick. */
   public static List<Cue> cuesAt(int tick) {
      List<Cue> out = new ArrayList<>(2);
      for (Cue cue : CUES) {
         if (cue.tick() == tick) out.add(cue);
      }
      return out;
   }

   // ---------------------------------------------------------------- loops

   /** Music: the swelling score from tick 8, cut dead at the silence. */
   public static float music(double t) {
      if (t < 8 || t >= FinalPunchRules.SILENCE) return 0.0F;
      return 0.55F + 0.45F * (float) ramp(t, 8, 300);
   }

   /** Wind loop: rises from Phase I to IV, cut at the silence. */
   public static float wind(double t) {
      if (t < FinalPunchRules.WINDUP_START || t >= FinalPunchRules.SILENCE) return 0.0F;
      double rise = ramp(t, FinalPunchRules.WINDUP_START, FinalPunchRules.WINDUP_START + 40) * (0.15 + 0.35 * ramp(t, 80, FinalPunchRules.WARP));
      return (float) (rise + 0.5 * storm(t));
   }

   public static float windPitch(double t) {
      return 0.8F + 0.45F * (float) ramp(t, FinalPunchRules.WINDUP_START, FinalPunchRules.SILENCE);
   }

   /** The fist's hum (Phase III–IV), cut at the silence. */
   public static float hum(double t) {
      if (t < FinalPunchRules.RINGS || t >= FinalPunchRules.SILENCE) return 0.0F;
      return (float) (ramp(t, FinalPunchRules.RINGS, FinalPunchRules.RINGS + 30) * (0.35 + 0.65 * ramp(t, FinalPunchRules.RINGS, FinalPunchRules.SUN + 40)));
   }

   public static float humPitch(double t) {
      return 0.7F + 0.7F * (float) ramp(t, FinalPunchRules.RINGS, FinalPunchRules.SILENCE);
   }

   /** The world's own sounds: full, ducked to almost nothing in the silence, back at the impact. */
   public static boolean ducked(double t) {
      return t >= FinalPunchRules.SILENCE && t < FinalPunchRules.CONTACT;
   }

   // ---------------------------------------------------------------- body

   /** Weight of the {@code final_punch} clip over the base pose (blend in, exit blend in the tail). */
   public static float bodyWeight(double t) {
      if (t < 0) return 0.0F;
      if (t < BODY_IN) return (float) smooth(t / BODY_IN);
      if (t <= FinalPunchRules.LENGTH) return 1.0F;
      return (float) (1.0 - smooth((t - FinalPunchRules.LENGTH) / 6.0));
   }

   /** 0..1 of the aim correction for a target whose jaw is not at a person's height (golem). */
   public static float aim(double t) {
      return (float) (smooth(ramp(t, 404, 438)) * (1.0 - smooth(ramp(t, 456, 474))));
   }

   // ---------------------------------------------------------------- fist layers

   public static double sparks(double t) {
      if (t < FinalPunchRules.SPARKS || t >= FinalPunchRules.CONTACT) return 0.0;
      return (0.35 + 0.65 * ramp(t, FinalPunchRules.SPARKS, FinalPunchRules.FIRE)) * calm(t);
   }

   public static double fire(double t) {
      if (t < FinalPunchRules.FIRE || t >= FinalPunchRules.CONTACT + 20) return 0.0;
      double grow = smooth(ramp(t, FinalPunchRules.FIRE, FinalPunchRules.FIRE + 30)) * (0.6 + 0.4 * ramp(t, FinalPunchRules.FIRE, FinalPunchRules.SUN));
      return grow * calm(t) * afterglow(t);
   }

   public static double rings(double t) {
      if (t < FinalPunchRules.RINGS || t >= FinalPunchRules.CONTACT) return 0.0;
      return smooth(ramp(t, FinalPunchRules.RINGS, FinalPunchRules.RINGS + 25)) * calm(t);
   }

   /** How many orbiting rings (1..3). */
   public static int ringCount(double t) {
      return t < FinalPunchRules.RINGS ? 0 : t < 232 ? 1 : t < FinalPunchRules.WARP ? 2 : 3;
   }

   public static double arcs(double t) {
      if (t < FinalPunchRules.ARCS || t >= FinalPunchRules.CONTACT) return 0.0;
      return smooth(ramp(t, FinalPunchRules.ARCS, FinalPunchRules.ARCS + 20)) * (0.5 + 0.5 * ramp(t, FinalPunchRules.ARCS, FinalPunchRules.SUN)) * calm(t);
   }

   /** Light pouring into the fist (just before and as it becomes a small sun). */
   public static double concentration(double t) {
      if (t < 280 || t >= FinalPunchRules.SILENCE) return 0.0;
      return smooth(ramp(t, 280, FinalPunchRules.SUN));
   }

   /** The small sun in the fist; compressed (smaller, whiter) through the silence and the strike. */
   public static double sun(double t) {
      if (t < FinalPunchRules.SUN - 20) return 0.0;
      if (t >= FinalPunchRules.CONTACT) return 1.0 - smooth(ramp(t, FinalPunchRules.CONTACT, FinalPunchRules.CONTACT + 4));
      return smooth(ramp(t, FinalPunchRules.SUN - 20, FinalPunchRules.SUN + 10));
   }

   /** 0..1 compression of the fist light (Phase V: everything contained in the fist). */
   public static double compress(double t) {
      if (t < FinalPunchRules.SILENCE || t >= FinalPunchRules.CONTACT) return 0.0;
      return smooth(ramp(t, FinalPunchRules.SILENCE, FinalPunchRules.SILENCE + 4));
   }

   /** The light trail of the slow strike. */
   public static double trail(double t) {
      if (t < FinalPunchRules.STRIKE || t >= FinalPunchRules.CONTACT + 2) return 0.0;
      return smooth(ramp(t, FinalPunchRules.STRIKE, FinalPunchRules.STRIKE + 6));
   }

   /** 0..1 age of the impact flash (−1 = none). */
   public static double impact(double t) {
      if (t < FinalPunchRules.CONTACT || t >= FinalPunchRules.CONTACT + 40) return -1.0;
      return (t - FinalPunchRules.CONTACT) / 40.0;
   }

   /** The fist's residual glow after the punch (fades while it lowers). */
   public static double afterglow(double t) {
      return t < FinalPunchRules.CONTACT ? 1.0 : 1.0 - smooth(ramp(t, FinalPunchRules.CONTACT, FinalPunchRules.LOWER_FIST + 6));
   }

   public static double smoke(double t) {
      if (t < FinalPunchRules.LAUNCH + 2 || t >= FinalPunchRules.LENGTH + TAIL) return 0.0;
      return smooth(ramp(t, FinalPunchRules.LAUNCH + 2, FinalPunchRules.LAUNCH + 8)) * (1.0 - smooth(ramp(t, FinalPunchRules.LENGTH - 10, FinalPunchRules.LENGTH + TAIL)));
   }

   /** Phase V turns the outer layers down to a thin breath; the core keeps burning. */
   static double calm(double t) {
      return t < FinalPunchRules.SILENCE ? 1.0 : 1.0 - 0.85 * smooth(ramp(t, FinalPunchRules.SILENCE, FinalPunchRules.SILENCE + 4));
   }

   /** 0..1 charge of the fist (see {@link FinalPunchRules#charge}). */
   public static double charge(double t) {
      return FinalPunchRules.charge(t);
   }

   // ---------------------------------------------------------------- chaos

   /** 0..1 of the reality chaos (from the arcs, full in Phase IV, frozen in the silence). */
   public static double storm(double t) {
      if (t < FinalPunchRules.ARCS || t >= FinalPunchRules.SILENCE) return 0.0;
      return smooth(ramp(t, FinalPunchRules.ARCS, FinalPunchRules.WARP)) * (0.35 + 0.65 * FinalPunchRules.storm(Math.max(t, FinalPunchRules.WARP)));
   }

   /** Debris keeps hanging in the air through the silence, then the impact throws it away. */
   public static double debris(double t) {
      if (t < FinalPunchRules.WARP || t >= FinalPunchRules.CONTACT + 30) return 0.0;
      double in = smooth(ramp(t, FinalPunchRules.WARP, FinalPunchRules.WARP + 40));
      return t < FinalPunchRules.CONTACT ? in : 1.0 - smooth(ramp(t, FinalPunchRules.CONTACT + 6, FinalPunchRules.CONTACT + 30));
   }

   /** Visual time scale of particles and debris: normal, almost stopped in the silence, frozen in the hit-stop. */
   public static double timeScale(double t) {
      if (t >= FinalPunchRules.CONTACT && t < FinalPunchRules.LAUNCH) return 0.0;
      if (t >= FinalPunchRules.SILENCE && t < FinalPunchRules.CONTACT) return 0.06;
      return 1.0;
   }

   /** Age in ticks of the newest ring pulse (−1 = none). */
   public static double ringPulseAge(double t) {
      if (t < RING_PULSE_START || t >= FinalPunchRules.SILENCE + RING_PULSE_TICKS) return -1.0;
      double since = t - RING_PULSE_START;
      double last = Math.floor(since / RING_PULSE_INTERVAL) * RING_PULSE_INTERVAL;
      if (RING_PULSE_START + last >= FinalPunchRules.SILENCE) last -= RING_PULSE_INTERVAL;
      double age = since - last;
      return age < RING_PULSE_TICKS ? age : -1.0;
   }

   /** Age in ticks of the newest ground air wave (−1 = none). */
   public static double airWaveAge(double t, int index) {
      if (t < FinalPunchRules.WARP) return -1.0;
      double start = FinalPunchRules.WARP + index * (double) AIR_WAVE_INTERVAL;
      if (start >= FinalPunchRules.SILENCE || t < start) return -1.0;
      double age = t - start;
      return age < AIR_WAVE_TICKS ? age : -1.0;
   }

   // ---------------------------------------------------------------- screen

   /** Letterbox bars 0..1. */
   public static double letterbox(double t) {
      if (t < 0 || t >= FinalPunchRules.LENGTH) return 0.0;
      return smooth(ramp(t, 0, BLEND_IN)) * (1.0 - smooth(ramp(t, FinalPunchRules.CAMERA_OUT, FinalPunchRules.LENGTH)));
   }

   /** Cold grade of the silence (TimeStopGrey of the shared post pass). */
   public static float grey(double t) {
      if (t < FinalPunchRules.SILENCE || t >= FinalPunchRules.CONTACT + 2) return 0.0F;
      return (float) (0.55 * smooth(ramp(t, FinalPunchRules.SILENCE, FinalPunchRules.SILENCE + 3)) * (1.0 - ramp(t, FinalPunchRules.CONTACT, FinalPunchRules.CONTACT + 2)));
   }

   /** Vignette of the silence and the charge peak (SnapVoid). */
   public static float vignette(double t) {
      double charge = 0.25 * smooth(ramp(t, FinalPunchRules.SUN - 20, FinalPunchRules.SILENCE)) * (t < FinalPunchRules.SILENCE ? 1 : 0);
      double silence = t >= FinalPunchRules.SILENCE && t < FinalPunchRules.CONTACT ? 0.7 * smooth(ramp(t, FinalPunchRules.SILENCE, FinalPunchRules.SILENCE + 3)) : 0.0;
      return (float) Math.max(charge, silence);
   }

   /** Radius 0..1.5 of the screen shock band (SnapShock): ring pulses around the fist, the impact. */
   public static float shock(double t) {
      if (t >= FinalPunchRules.CONTACT && t < FinalPunchRules.CONTACT + 22) {
         // Frozen small during the hit-stop, then it tears across the screen.
         if (t < FinalPunchRules.LAUNCH) return 0.08F;
         return (float) (0.08 + 1.42 * ramp(t, FinalPunchRules.LAUNCH, FinalPunchRules.CONTACT + 22));
      }
      double age = ringPulseAge(t);
      if (age < 0 || t < FinalPunchRules.WARP || t >= FinalPunchRules.SILENCE) return 0.0F;
      return (float) (0.05 + 0.55 * age / RING_PULSE_TICKS);
   }

   /** FOV multiplier: tight in the silence, a short punch-out at the impact. */
   public static float fov(double t) {
      if (t < FinalPunchRules.SILENCE) return (float) (1.0 - 0.06 * smooth(ramp(t, FinalPunchRules.SUN, FinalPunchRules.SILENCE)));
      if (t < FinalPunchRules.CONTACT) return (float) (0.94 - 0.12 * smooth(ramp(t, FinalPunchRules.SILENCE, FinalPunchRules.CONTACT)));
      if (t < FinalPunchRules.LAUNCH) return 0.80F;
      return (float) (0.80 + 0.20 * smooth(ramp(t, FinalPunchRules.LAUNCH, FinalPunchRules.CAMERA_OUT)));
   }

   /** The scene's own camera shake for the caster's view (0..1 power), controlled. */
   public static float shake(double t) {
      if (t >= FinalPunchRules.CONTACT && t < FinalPunchRules.CONTACT + 1) return 1.0F;
      double age = ringPulseAge(t);
      if (age >= 0 && age < 1 && t >= FinalPunchRules.WARP) return 0.12F + 0.18F * (float) storm(t);
      return 0.0F;
   }

   // ---------------------------------------------------------------- math

   public static double ramp(double t, double from, double to) {
      if (to <= from) return t >= to ? 1.0 : 0.0;
      double v = (t - from) / (to - from);
      return v <= 0 ? 0 : v >= 1 ? 1 : v;
   }

   public static double smooth(double v) {
      double x = v <= 0 ? 0 : v >= 1 ? 1 : v;
      return x * x * (3 - 2 * x);
   }

   /** Deterministic hash noise in [0, 1) (same on every client). */
   public static double hash(long seed, int index) {
      long x = seed * 0x9E3779B97F4A7C15L + index * 0xBF58476D1CE4E5B9L;
      x ^= x >>> 31;
      x *= 0x94D049BB133111EBL;
      x ^= x >>> 29;
      return (x >>> 11) * 0x1.0p-53;
   }
}
