package dev.baranhan.viltrumitecore.client.aftermath;

import net.minecraft.util.Mth;

/**
 * Pure numbers of the aftermath soundscape (unit tested): distance curves, how often each kind of sound may
 * happen near a ruin, and how many voices may overlap. Distances are blocks from the listener to the source.
 */
public final class AftermathMixer {
   /** Blocks per tick of sound: 343 m/s at 20 ticks per second. */
   public static final double SOUND_SPEED = 343.0 / 20.0;
   /** One-shots that may start within {@link #VOICE_WINDOW} ticks (all ruins together). */
   public static final int MAX_VOICES = 7;
   public static final int VOICE_WINDOW = 40;

   // one-shot kinds of the scene director
   public static final int CRUMBLE = 0;
   public static final int ROCKS = 1;
   public static final int DEBRIS = 2;
   public static final int COLLAPSE = 3;
   public static final int CREAK = 4;
   public static final int SPARKS = 5;
   public static final int CRACKLE = 6;
   public static final int EXPLOSION = 7;
   public static final int SIREN = 8;
   public static final int KINDS = 9;

   /** Audible range per kind (blocks). */
   private static final double[] RANGE = {44.0, 64.0, 96.0, 170.0, 72.0, 30.0, 22.0, 0.0, 0.0};
   /** Ticks a kind waits after it played (per ruin). */
   private static final int[] COOLDOWN = {14, 50, 70, 500, 90, 30, 3, 260, 700};

   private AftermathMixer() {
   }

   public static double range(int kind) {
      return RANGE[kind];
   }

   public static int cooldown(int kind) {
      return COOLDOWN[kind];
   }

   /** Pure: loudness of a source at a distance within its range: a soft knee near, a long tail far. */
   public static float falloff(double distance, double range) {
      if (range <= 0.0 || distance >= range) {
         return 0.0F;
      }

      float t = (float)(1.0 - Math.max(0.0, distance) / range);
      return Mth.clamp(t * t * (1.6F - 0.6F * t), 0.0F, 1.0F);
   }

   /** Pure: a bed (wind, rumble) around a ruin: full inside it, fading out over {@code reach} blocks outside its edge. */
   public static float bed(double distance, float radius, double reach) {
      double out = distance - radius;
      if (out <= 0.0) {
         return 1.0F;
      }

      return out >= reach ? 0.0F : (float)Math.pow(1.0 - out / reach, 1.5);
   }

   /** Pure: ticks until a sound from {@code distance} blocks arrives (capped). */
   public static int delay(double distance) {
      return (int)Math.min(30L, Math.round(Math.max(0.0, distance - 24.0) / SOUND_SPEED));
   }

   /**
    * Pure: chance per tick that a kind starts near a ruin with these channels (mix of {@code AftermathRules}).
    * Crumbles follow the debris channel, creaks the metal one, crackles the fire and smoulder ones, wires
    * only spark with power, sirens and far explosions follow their own channels.
    */
   public static float chance(int kind, float[] mix, float severity, boolean focus) {
      float debris = mix[dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathRules.DEBRIS] * (focus ? 1.4F : 1.0F);
      return switch (kind) {
         case CRUMBLE -> debris * 0.045F;
         case ROCKS -> debris * 0.012F;
         case DEBRIS -> debris * 0.006F;
         case COLLAPSE -> debris * severity * (focus ? 0.0035F : 0.0018F);
         case CREAK -> mix[dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathRules.METAL] * 0.009F;
         case SPARKS -> mix[dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathRules.SPARKS] * 0.03F;
         case CRACKLE -> (mix[dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathRules.FIRE] + mix[dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathRules.SMOLDER] * 0.6F) * 0.12F;
         case EXPLOSION -> mix[dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathRules.RUMBLE] * severity * 0.0025F;
         case SIREN -> mix[dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathRules.SIREN] * 0.0022F;
         default -> 0.0F;
      };
   }

   /** Pure: the voice limit lets quieter kinds through less when it is busy (0..1 of the chance kept). */
   public static float crowd(int recent, int kind) {
      if (recent >= MAX_VOICES) {
         return 0.0F;
      }

      float busy = recent / (float)MAX_VOICES;
      // big events always get a slot; trickles and crackles step back first
      boolean big = kind == COLLAPSE || kind == DEBRIS || kind == SIREN || kind == EXPLOSION;
      return big ? 1.0F : Mth.clamp(1.0F - busy * 1.2F, 0.0F, 1.0F);
   }
}
