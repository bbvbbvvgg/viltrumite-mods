package dev.baranhan.viltrumitecore.client.reinhard.erasure;

/** Pure, finite cinematic curves. No wall clock, registry, renderer or world access. */
public record ErasureUniforms(float crack, float shatter, float dark, float rebuild, float seed) {
   public static final ErasureUniforms ZERO = new ErasureUniforms(0,0,0,0,0);
   public static ErasureUniforms at(int tick, float partialTick) {
      if(tick < 0 || tick >= 150) return ZERO;
      float t = tick + unit(partialTick);
      if(t >= 150) return ZERO;
      if(t < 30) return new ErasureUniforms(unit(t/30),0,0,0,1);
      if(t < 70) return new ErasureUniforms(1,unit((t-30)/40),0,0,1);
      if(t < 110) return new ErasureUniforms(0,0,1,0,1);
      return new ErasureUniforms(0,0,unit(1-(t-110)/15),unit(1-(t-110)/40),1);
   }
   public boolean active() {return crack+shatter+dark+rebuild > 0;}
   public ErasureUniforms withSeed(float value) {return active() ? new ErasureUniforms(crack,shatter,dark,rebuild,unit(value)) : ZERO;}
   private static float unit(float value) {return Float.isFinite(value) ? Math.max(0,Math.min(1,value)) : 0;}
}
