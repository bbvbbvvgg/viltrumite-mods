package dev.baranhan.viltrumitecore.client.reinhard.erasure;

import static dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules.*;

/**
 * Pure curves for the target's end (no Minecraft classes). The target keeps its exact frozen pose
 * and world position; from the shatter on it is drawn by the scene instead of the level (so the
 * glass carries the world away and leaves it alone in the void), slowly lights up white, then
 * comes apart cell by cell (model pixels) into rising light, feet first. A protected target
 * (verdict != erase) comes apart only partly and gathers back.
 */
public final class ErasureDissolve {
   private ErasureDissolve() {}
   /** Rising-light lifetime of a released cell, ticks. */
   public static final float MOTE_LIFE = 24;
   /** Width of the white-hot band just ahead of the dissolve front (threshold units). */
   public static final float EDGE = .07F;

   /** Does the scene draw the target instead of the level at t? */
   public static boolean sceneOwnsTarget(float t, boolean erase) {
      return t >= ERASE_SHATTER_START && t < (erase ? ERASE_LENGTH : ERASE_REBUILD_END);
   }
   /** White light 0..1: builds from the glow start, full at the dissolve; a survivor's light fades on the return. */
   public static float glow(float t, boolean erase) {
      if (!(t >= ERASE_GLOW_START)) return 0;
      float up = smooth((t - ERASE_GLOW_START) / (ERASE_DISSOLVE_START - ERASE_GLOW_START));
      if (erase || t < ERASE_REMOVE_TICK) return up;
      return 1 - smooth((t - ERASE_REMOVE_TICK) / (ERASE_REBUILD_END - 8 - ERASE_REMOVE_TICK));
   }
   /** Dissolve front 0..1 (threshold units, slightly past 1 so the last cell goes). */
   public static float front(float t, boolean erase) {
      if (!(t >= ERASE_DISSOLVE_START)) return 0;
      float span = ERASE_REMOVE_TICK - 2 - ERASE_DISSOLVE_START;
      if (erase) return 1.12F * ease((t - ERASE_DISSOLVE_START) / span);
      // survivor: comes apart to the waist, then gathers back by the removal tick
      float mid = ERASE_DISSOLVE_START + span * .6F;
      if (t < mid) return .5F * ease((t - ERASE_DISSOLVE_START) / (mid - ERASE_DISSOLVE_START));
      return .5F * (1 - smooth((t - mid) / (ERASE_REMOVE_TICK - mid)));
   }
   /** Threshold of a model cell: height (0 = feet, 1 = head) plus seeded noise. */
   public static float threshold(float height, float noise) {
      return .58F * clamp(height) + .42F * clamp(noise);
   }
   /** Stable hash noise 0..1 for a quantized model cell position. */
   public static float noise(int x, int y, int z, long seed) {
      long h = seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
      h ^= h >>> 29; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 32;
      return (h >>> 40) / (float)(1 << 24);
   }
   /**
    * Age of a released cell in ticks (front passed its threshold), negative while it is still whole.
    * Erase: real ticks since the front crossed the cell, so every mote dies MOTE_LIFE after its
    * release even once the front has stopped (the last cells must not hang in the air). Survivor:
    * the gap to the front, so its light runs back into the body as the front returns.
    */
   public static float released(float threshold, float t, boolean erase) {
      float f = front(t, erase);
      if (f <= threshold) return -1;
      float span = ERASE_REMOVE_TICK - 2 - ERASE_DISSOLVE_START;
      if (!erase) return (f - threshold) / 1.12F * span;
      return Math.max(0, t - (ERASE_DISSOLVE_START + span * easeInverse(clamp(threshold) / 1.12F)));
   }
   /** Soft bloom around the body 0..1: follows the light, dims as it comes apart, gone after the removal. */
   public static float bloom(float t, boolean erase) {
      float b = .3F * glow(t, erase) * (1 - .6F * Math.min(1, front(t, erase)));
      if (!erase) return b;
      return b * (1 - smooth((t - (ERASE_REMOVE_TICK - 2)) / 8));
   }
   /** Brightness of the hot band 0..1 (1 right at the front). */
   public static float edge(float threshold, float t, boolean erase) {
      float f = front(t, erase);
      if (f <= 0 || threshold < f) return 0;
      float d = threshold - f;
      return d >= EDGE ? 0 : 1 - d / EDGE;
   }
   /** The target's own view: white light closing in while it is erased. */
   public static float selfVignette(float t, boolean erase) {
      float g = glow(t, erase);
      if (!erase) return .45F * g;
      return Math.min(1, .55F * g + .45F * smooth((t - ERASE_DISSOLVE_START) / (ERASE_REMOVE_TICK - ERASE_DISSOLVE_START)));
   }
   static float clamp(float v) {return Float.isFinite(v) ? Math.max(0, Math.min(1, v)) : 0;}
   static float smooth(float v) {float t = clamp(v); return t * t * (3 - 2 * t);}
   /** Inverse of {@link #ease} on 0..1. */
   static float easeInverse(float y) {float v = clamp(y); return v < .5F ? (float)Math.sqrt(v / 2) : 1 - (float)Math.sqrt((1 - v) / 2);}
   static float ease(float v) {float t = clamp(v); return t < .5F ? 2 * t * t : 1 - 2 * (1 - t) * (1 - t);}
}
