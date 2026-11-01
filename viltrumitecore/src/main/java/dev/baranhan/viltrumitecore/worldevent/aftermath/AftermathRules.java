package dev.baranhan.viltrumitecore.worldevent.aftermath;

import net.minecraft.util.Mth;

/**
 * Pure rules of the aftermath (no world access, unit tested): how long each stage lasts, how strong every
 * effect channel is at a given age, how hits merge into a zone and how far citizens keep away. Ages are game
 * ticks since the zone's last hit; {@code f} is the duration factor of the zone
 * ({@link #durationFactor}), so a bigger ruin smokes longer and the server config can stretch everything.
 */
public final class AftermathRules {
   /** Stage ends at duration factor 1 (ticks): 45 s of fresh destruction, 6 min smouldering, 16 min late, 2 h scar. */
   public static final int IMPACT_TICKS = 900;
   public static final int SMOLDER_TICKS = 7200;
   public static final int LATE_TICKS = 19200;
   public static final int SCAR_TICKS = 144000;
   public static final float MAX_RADIUS = 48.0F;
   public static final float MIN_RADIUS = 4.0F;
   public static final int MAX_ZONES = 48;
   public static final int MAX_SEEDS = 48;

   // effect channels of mix()
   public static final int SMOKE = 0;
   public static final int DUST = 1;
   public static final int FIRE = 2;
   public static final int HOT = 3;
   public static final int SPARKS = 4;
   public static final int DEBRIS = 5;
   public static final int METAL = 6;
   public static final int FOG = 7;
   public static final int SMOLDER = 8;
   public static final int RUMBLE = 9;
   public static final int WIND = 10;
   public static final int SIREN = 11;
   public static final int CHANNELS = 12;

   /** Zone flags (shared with the client). */
   public static final int FLAG_POWER_OUT = 1;
   public static final int FLAG_FOCUS = 2;
   public static final int FLAG_DECLARED = 4;
   public static final int FLAG_CITY = 8;

   private AftermathRules() {
   }

   /** Duration factor: severity 0 lasts half as long as the base, severity 1 one and a half times; times the config scale. */
   public static float durationFactor(float severity, double scale) {
      return (float)Mth.clamp((0.5 + Mth.clamp(severity, 0.0F, 1.0F)) * scale, 0.05, 20.0);
   }

   public static AftermathStage stage(long age, float f) {
      double t = age / (double)Math.max(0.05F, f);
      if (t < IMPACT_TICKS) {
         return AftermathStage.IMPACT;
      } else if (t < SMOLDER_TICKS) {
         return AftermathStage.SMOLDER;
      } else if (t < LATE_TICKS) {
         return AftermathStage.LATE;
      } else {
         return t < LATE_TICKS + SCAR_TICKS ? AftermathStage.SCAR : AftermathStage.GONE;
      }
   }

   /** Ticks (at factor {@code f}) when a stage begins: the inverse of {@link #stage}, for /aftermath age. */
   public static long stageStart(AftermathStage stage, float f) {
      int t = switch (stage) {
         case IMPACT -> 0;
         case SMOLDER -> IMPACT_TICKS;
         case LATE -> SMOLDER_TICKS;
         case SCAR -> LATE_TICKS;
         case GONE -> LATE_TICKS + SCAR_TICKS;
      };
      return (long)Math.ceil(t * (double)f);
   }

   /** Severity of a burst of destroyed blocks: 6 blocks ~0.07, 40 ~0.38, 200 ~0.65, 1600+ = 1. */
   public static float severityOf(int blocks) {
      if (blocks <= 4) {
         return 0.05F;
      }

      return Mth.clamp((float)(Math.log10(blocks / 4.0) / Math.log10(400.0)), 0.05F, 1.0F);
   }

   /** Severity after another hit of {@code added} on a zone of {@code current}: the stronger one plus a share of the other. */
   public static float mergeSeverity(float current, float added) {
      float hi = Math.max(current, added);
      float lo = Math.min(current, added);
      return Mth.clamp(hi + lo * 0.15F, 0.0F, 1.0F);
   }

   /** True when a hit at distance {@code d} from a zone of radius {@code r1} belongs to that zone. */
   public static boolean merges(double d, float r1, float r2) {
      return d <= r1 + r2 * 0.5F + 4.0F && bound(d, r1, r2)[0] <= MAX_RADIUS + 0.01F || d < 6.0;
   }

   /**
    * The smallest sphere around two spheres (centres {@code d} apart): {radius, shift of the first centre toward
    * the second}. A sphere inside the other changes nothing.
    */
   public static float[] bound(double d, float r1, float r2) {
      if (d + r2 <= r1) {
         return new float[]{r1, 0.0F};
      }

      if (d + r1 <= r2) {
         return new float[]{r2, (float)d};
      }

      float r = (float)((d + r1 + r2) / 2.0);
      return new float[]{r, (float)(r - r1)};
   }

   private static float lerp(double t, double a, double b) {
      return (float)(a + (b - a) * Mth.clamp(t, 0.0, 1.0));
   }

   private static double span(double t, double from, double to) {
      return (t - from) / (to - from);
   }

   /**
    * Effect channels 0..1 at an age: what a zone of this severity and cause mix shows and sounds like now.
    * Channels {@link #SMOKE} ... {@link #SIREN}. Fires always burn out: {@link #FIRE} is 0 from 60 % of the
    * smouldering stage on, {@link #SMOLDER} from 60 % of the late stage.
    */
   public static float[] mix(long age, float f, float severity, int causes, int flags) {
      float[] m = new float[CHANNELS];
      double t = Math.max(0L, age) / (double)Math.max(0.05F, f);
      float sev = Mth.clamp(severity, 0.0F, 1.0F);
      if (t >= LATE_TICKS + SCAR_TICKS) {
         return m;
      }

      double imp = IMPACT_TICKS;
      double smo = SMOLDER_TICKS;
      double late = LATE_TICKS;
      float ramp = (float)Mth.clamp(t / 120.0, 0.0, 1.0);
      // smoke: rises within seconds, holds while fresh, thins while smouldering, nearly gone late
      float smoke = t < imp ? 1.0F : t < smo ? lerp(span(t, imp, smo), 1.0, 0.4) : t < late ? lerp(span(t, smo, late), 0.4, 0.04) : 0.0F;
      m[SMOKE] = smoke * ramp * (0.35F + 0.65F * sev) * AftermathCause.weight(causes, AftermathCause.SMOKE);
      // dust: the cloud of the collapse, settling in a minute or two
      float base = t < imp ? 0.12F : t < smo ? lerp(span(t, imp, smo), 0.12, 0.03) : t < late ? lerp(span(t, smo, late), 0.03, 0.0) : 0.0F;
      float dust = (float)(0.88 * Math.exp(-t / 300.0)) + base;
      m[DUST] = Mth.clamp(dust, 0.0F, 1.0F) * (0.4F + 0.6F * sev) * AftermathCause.weight(causes, AftermathCause.DUST);
      float fire = t < imp ? 1.0F : t < smo ? lerp(span(t, imp, imp + (smo - imp) * 0.6), 1.0, 0.0) : 0.0F;
      m[FIRE] = fire * (0.3F + 0.7F * sev) * AftermathCause.weight(causes, AftermathCause.FIRE);
      float smoulder = t < imp ? lerp(t / imp, 0.6, 1.0) : t < smo ? lerp(span(t, imp, smo), 1.0, 0.25)
         : t < late ? lerp(span(t, smo, smo + (late - smo) * 0.6), 0.25, 0.0) : 0.0F;
      m[SMOLDER] = smoulder * (0.4F + 0.6F * sev)
         * Math.max(AftermathCause.weight(causes, AftermathCause.FIRE), AftermathCause.weight(causes, AftermathCause.HOT));
      m[HOT] = t < late ? (float)Math.exp(-t / 2400.0) * AftermathCause.weight(causes, AftermathCause.HOT) : 0.0F;
      float sparks = t < imp ? 1.0F : t < smo ? lerp(span(t, imp, smo), 0.35, 0.1) : t < late ? lerp(span(t, smo, late), 0.1, 0.0) : 0.0F;
      // with the power cut the wires are dead: only the glowing bits of the impact itself spark
      float power = (flags & FLAG_POWER_OUT) != 0 ? (t < imp ? 0.3F : 0.05F) : 1.0F;
      m[SPARKS] = sparks * power * AftermathCause.weight(causes, AftermathCause.SPARKS);
      float debris = t < imp ? lerp(t / imp, 1.0, 0.5) : t < smo ? lerp(span(t, imp, smo), 0.5, 0.15) : t < late ? lerp(span(t, smo, late), 0.15, 0.06) : 0.03F;
      m[DEBRIS] = debris * (0.4F + 0.6F * sev) * AftermathCause.weight(causes, AftermathCause.DEBRIS);
      m[METAL] = debris * (0.4F + 0.6F * sev) * AftermathCause.weight(causes, AftermathCause.METAL);
      float fog = t < imp ? 1.0F : t < smo ? lerp(span(t, imp, smo), 1.0, 0.35) : t < late ? lerp(span(t, smo, smo + (late - smo) * 0.5), 0.35, 0.0) : 0.0F;
      m[FOG] = fog * ramp * Mth.clamp((sev - 0.25F) / 0.6F, 0.0F, 1.0F)
         * Math.max(AftermathCause.weight(causes, AftermathCause.SMOKE), AftermathCause.weight(causes, AftermathCause.DUST));
      m[RUMBLE] = (t < imp ? lerp(t / imp, 1.0, 0.5) : t < smo ? lerp(span(t, imp, smo), 0.5, 0.1) : 0.0F) * (0.3F + 0.7F * sev);
      m[WIND] = (t < imp ? 0.25F : t < smo ? lerp(span(t, imp, smo), 0.3, 0.6) : t < late ? lerp(span(t, smo, late), 0.6, 0.9) : lerp(span(t, late, late + SCAR_TICKS), 0.9, 0.5))
         * (0.4F + 0.6F * sev);
      // sirens: help on the way some 20 s after the hit, loudest while it smoulders, gone halfway through the late stage
      m[SIREN] = t < 400.0 ? 0.0F : t < smo ? lerp(span(t, 400.0, 2400.0), 0.3, 1.0) : t < late ? lerp(span(t, smo, smo + (late - smo) * 0.5), 1.0, 0.0) : 0.0F;
      if ((flags & FLAG_CITY) == 0) {
         m[SIREN] *= 0.35F; // nobody calls the fire brigade to a crater in the wild, but a far town may
      }

      return m;
   }

   /** 0 = citizens stay away, 1 = they walk here as before. */
   public static float returnFactor(long age, float f) {
      double t = Math.max(0L, age) / (double)Math.max(0.05F, f);
      if (t < IMPACT_TICKS) {
         return 0.0F;
      } else if (t < SMOLDER_TICKS) {
         return lerp(span(t, IMPACT_TICKS, SMOLDER_TICKS), 0.1, 0.6);
      } else {
         return t < LATE_TICKS ? lerp(span(t, SMOLDER_TICKS, LATE_TICKS), 0.6, 1.0) : 1.0F;
      }
   }

   /** Radius around the zone centre citizens never walk into: the whole zone and a margin while fresh, then only the core. */
   public static float hazardRadius(float radius, long age, float f) {
      double t = Math.max(0L, age) / (double)Math.max(0.05F, f);
      if (t < IMPACT_TICKS) {
         return radius + 8.0F;
      } else if (t < SMOLDER_TICKS) {
         return lerp(span(t, IMPACT_TICKS, SMOLDER_TICKS), radius, radius * 0.5);
      } else {
         return t < LATE_TICKS ? lerp(span(t, SMOLDER_TICKS, LATE_TICKS), radius * 0.5, radius * 0.25) : 0.0F;
      }
   }

   /** Burst geometry to a zone radius: half the diagonal of the changed box plus a margin, clamped. */
   public static float burstRadius(int sizeX, int sizeY, int sizeZ) {
      double half = Math.sqrt(sizeX * (double)sizeX + sizeZ * (double)sizeZ + sizeY * (double)sizeY * 0.25) * 0.5;
      return Mth.clamp((float)half + 3.0F, MIN_RADIUS, 16.0F);
   }
}
