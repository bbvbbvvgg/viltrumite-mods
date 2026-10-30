package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import dev.baranhan.viltrumitecore.client.reinhard.erasure.ErasureUniforms;
import dev.baranhan.viltrumitecore.client.reinhard.erasure.ErasurePost;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ErasureUniformsTest {
   @Test void zeroWhenIdle() {for(int t : new int[]{-99,-1,205,206,Integer.MAX_VALUE}) assertEquals(ErasureUniforms.ZERO, ErasureUniforms.at(t,.5F));}
   /** One clock from the press: stop grade, tension build, rupture flash, void, rebuild pulses, settle by 205. */
   @ParameterizedTest @CsvSource({"0,0,0,0,0","10,.22,0,0,0","55,.532,0,0,0","85,1,.0875,0,0","90,1,.35,0,0","91,1,1,1,0","94,1,0,.5,0",
      "120,1,0,1,0","168,1,0,.84375,.35","170,1,0,.768066,.196875","176,1,0,.5,.1692","192,1,0,0,1","204,0,0,0,0","205,0,0,0,0"})
   void phaseCurves(int t,float crack,float shatter,float dark,float rebuild) {
      var u=ErasureUniforms.at(t,0);
      if(t==85) crack=.22F+.78F*(65/70F)*(65/70F)*(2.2F-1.2F*(65/70F));
      if(t==91) dark=(float)(1/64.0*(3-2/8.0)*1);
      if(t==94) shatter=(float)Math.pow(1-3/9.0,2);
      if(t==204) {float v=12/13F;crack=1-v*v*(3-2*v);}
      assertEquals(crack,u.crack(),1e-5,"crack@"+t);assertEquals(shatter,u.shatter(),1e-5,"shatter@"+t);assertEquals(dark,u.dark(),1e-5,"dark@"+t);
      assertEquals(rebuild,u.rebuild(),1e-5,"rebuild@"+t);
   }
   @Test void finalSnapIsTheStrongestPulse() {
      float last=dev.baranhan.viltrumitecore.client.reinhard.erasure.ErasureGlass.SNAPS[5];
      for(int i=0;i<5;i++) {float at=dev.baranhan.viltrumitecore.client.reinhard.erasure.ErasureGlass.SNAPS[i];assertTrue(ErasureUniforms.at((int)Math.ceil(at),0).rebuild()<ErasureUniforms.at((int)last,0).rebuild());}
      assertEquals(1,ErasureUniforms.at((int)last,0).rebuild(),1e-6);
   }
   @Test void partialTicksMoveTheSameClock() {
      assertEquals(ErasureUniforms.at(56,0).crack(),ErasureUniforms.at(55,1).crack(),1e-6);
      assertTrue(ErasureUniforms.at(55,.5F).crack()>ErasureUniforms.at(55,0).crack());
      assertEquals(.5F,ErasureUniforms.at(176,0).dark(),1e-6);assertEquals(0,ErasureUniforms.at(192,0).dark());
   }
   @Test void clampedAndFinite() {for(int t=-2;t<=207;t++) for(float pt : new float[]{-99,0,.9F,1,99,Float.NaN,Float.POSITIVE_INFINITY}) {var u=ErasureUniforms.at(t,pt);for(float value : new float[]{u.crack(),u.shatter(),u.dark(),u.rebuild(),u.seed()}) assertTrue(Float.isFinite(value)&&value>=0&&value<=1);}assertEquals(ErasureUniforms.at(15,0),ErasureUniforms.at(15,Float.NaN));}
   @Test void seedCannotLeakIntoIdleFrame() {assertEquals(ErasureUniforms.ZERO,ErasureUniforms.ZERO.withSeed(.8F));assertEquals(.8F,ErasureUniforms.at(40,0).withSeed(.8F).seed());assertEquals(0,ErasureUniforms.at(40,0).withSeed(Float.NaN).seed());}
   @Test void bridgeHasNeutralDefaultsAndSceneOwnsItsLifetime() {ErasurePost.bind(()->true,pt->ErasureUniforms.at(90,pt));try {assertTrue(ErasurePost.active());assertEquals(.35F,ErasurePost.uniforms(0).shatter(),1e-6);}finally{ErasurePost.reset();}assertFalse(ErasurePost.active());assertEquals(ErasureUniforms.ZERO,ErasurePost.uniforms(.8F));}
   static JsonObject json(String path) throws Exception {return JsonParser.parseString(read("resources/"+path)).getAsJsonObject();}
   static String read(String path) throws Exception {return Files.readString(Path.of("src/main/"+path));}
}
