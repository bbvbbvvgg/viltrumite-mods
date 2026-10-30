package dev.baranhan.viltrumitecore.client.reinhard.erasure;

import static dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules.*;

/**
 * Pure, finite full-screen grading curves for the existing dash_impact chain, on the one scene
 * clock (0 = the N press, see ReinhardRules). No wall clock, registry, renderer or world access.
 * The glass itself (cracks, shards, rebuild) is geometry ({@link ErasureGlass}); the shader only
 * grades the frame:
 * <ul>
 *   <li>{@code crack}: the stopped-world tension grade (cold drain, vignette, critical shimmer);
 *       a faint drain from the stop, building over the crack growth, gone as the cracks close.</li>
 *   <li>{@code shatter}: the rupture pulse (white flash + chromatic split) peaking at the shatter.</li>
 *   <li>{@code dark}: the void the shards fall into (deeper drain and vignette on the frozen frame).</li>
 *   <li>{@code rebuild}: shock pulses as the returning shards snap home, strongest at the last one.</li>
 * </ul>
 */
public record ErasureUniforms(float crack, float shatter, float dark, float rebuild, float seed) {
   public static final ErasureUniforms ZERO = new ErasureUniforms(0,0,0,0,0);
   /** Stopped-world drain held before the first crack. */
   static final float STOP_GRADE = .22F;
   public static ErasureUniforms at(int tick, float partialTick) {
      if(tick < 0 || tick >= ERASE_LENGTH) return ZERO;
      float t = tick + unit(partialTick);
      if(t >= ERASE_LENGTH) return ZERO;
      return new ErasureUniforms(unit(crack(t)),unit(shatter(t)),unit(dark(t)),unit(rebuild(t)),1);
   }
   static float crack(float t) {
      if(t < ERASE_CRACK_START) return STOP_GRADE*smooth(t/6);
      if(t < ERASE_SHATTER_START) return STOP_GRADE+(1-STOP_GRADE)*ease((t-ERASE_CRACK_START)/(ERASE_SHATTER_START-ERASE_CRACK_START));
      if(t < ERASE_REBUILD_END) return 1;
      return 1-smooth((t-ERASE_REBUILD_END)/(ERASE_LENGTH-ERASE_REBUILD_END));
   }
   /** Builds over the critical rupture, peaks on the shatter, gone 10 ticks later. */
   static float shatter(float t) {
      if(t < ERASE_CRACK_END || t >= ERASE_SHATTER_START+10) return 0;
      if(t < ERASE_SHATTER_START) {float u=(t-ERASE_CRACK_END)/(ERASE_SHATTER_START-ERASE_CRACK_END); return .35F*u*u;}
      if(t < ERASE_SHATTER_START+1) return .35F+.65F*(t-ERASE_SHATTER_START);
      float u=(t-ERASE_SHATTER_START-1)/9; return (1-u)*(1-u);
   }
   static float dark(float t) {
      if(t < ERASE_SHATTER_START || t >= ERASE_REBUILD_END) return 0;
      if(t < ERASE_SHATTER_START+8) return smooth((t-ERASE_SHATTER_START)/8);
      if(t < ERASE_DARK_END) return 1;
      return 1-smooth((t-ERASE_DARK_END)/(ERASE_REBUILD_END-ERASE_DARK_END));
   }
   static float rebuild(float t) {
      float best=0;
      for(int i=0;i<ErasureGlass.SNAPS.length;i++) {
         float at=ErasureGlass.SNAPS[i],u=t-at;
         if(u<0 || u>=8) continue;
         float power=i==ErasureGlass.SNAPS.length-1 ? 1 : .35F+.12F*i;
         best=Math.max(best,power*(1-u/8)*(1-u/8));
      }
      return best;
   }
   public boolean active() {return crack+shatter+dark+rebuild > 0;}
   public ErasureUniforms withSeed(float value) {return active() ? new ErasureUniforms(crack,shatter,dark,rebuild,unit(value)) : ZERO;}
   static float smooth(float value) {float t=unit(value); return t*t*(3-2*t);}
   /** Slow start, steady build: the crack growth. */
   static float ease(float value) {float t=unit(value); return t*t*(2.2F-1.2F*t);}
   static float unit(float value) {return Float.isFinite(value) ? Math.max(0,Math.min(1,value)) : 0;}
}
