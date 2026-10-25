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
   @Test void zeroWhenIdle() {for(int t : new int[]{-99,-1,150,151,Integer.MAX_VALUE}) assertEquals(ErasureUniforms.ZERO, ErasureUniforms.at(t,.5F));}
   @ParameterizedTest @CsvSource({"0,0,0,0,0","15,.5,0,0,0","30,1,0,0,0","50,1,.5,0,0","70,0,0,1,0","90,0,0,1,0","110,0,0,1,1","130,0,0,0,.5","150,0,0,0,0"})
   void phaseCurves(int t,float crack,float shatter,float dark,float rebuild) {var u=ErasureUniforms.at(t,0);assertEquals(crack,u.crack(),1e-6);assertEquals(shatter,u.shatter(),1e-6);assertEquals(dark,u.dark(),1e-6);assertEquals(rebuild,u.rebuild(),1e-6);}
   @Test void rebuildMirrorsShatter() {for(int i=0;i<40;i++) assertEquals(ErasureUniforms.at(30+i,0).shatter(),ErasureUniforms.at(150-i,0).rebuild(),1e-6);}
   @Test void partialTicksAndDarkReturnFade() {assertEquals(.525F,ErasureUniforms.at(15,.75F).crack(),1e-6);assertEquals(.5F,ErasureUniforms.at(117,.5F).dark(),1e-6);assertEquals(0,ErasureUniforms.at(125,0).dark());}
   @Test void clampedAndFinite() {for(int t=-2;t<=152;t++) for(float pt : new float[]{-99,0,.9F,1,99,Float.NaN,Float.POSITIVE_INFINITY}) {var u=ErasureUniforms.at(t,pt);for(float value : new float[]{u.crack(),u.shatter(),u.dark(),u.rebuild(),u.seed()}) assertTrue(Float.isFinite(value)&&value>=0&&value<=1);}assertEquals(ErasureUniforms.at(15,0),ErasureUniforms.at(15,Float.NaN));}
   @Test void seedCannotLeakIntoIdleFrame() {assertEquals(ErasureUniforms.ZERO,ErasureUniforms.ZERO.withSeed(.8F));assertEquals(.8F,ErasureUniforms.at(40,0).withSeed(.8F).seed());assertEquals(0,ErasureUniforms.at(40,0).withSeed(Float.NaN).seed());}
   @Test void bridgeHasNeutralDefaultsAndSceneOwnsItsLifetime() {ErasurePost.bind(()->true,pt->ErasureUniforms.at(50,pt));try {assertTrue(ErasurePost.active());assertEquals(.5F,ErasurePost.uniforms(0).shatter());}finally{ErasurePost.reset();}assertFalse(ErasurePost.active());assertEquals(ErasureUniforms.ZERO,ErasurePost.uniforms(.8F));}
   @Test void postJsonDeclaresEraseUniforms() throws Exception {Set<String> names=Set.of("EraseCrack","EraseShatter","EraseDark","EraseRebuild","EraseSeed");var program=json("assets/minecraft/shaders/program/dash_impact.json").getAsJsonArray("uniforms");var post=json("assets/viltrumitecore/shaders/post/dash_impact.json");var passes=post.getAsJsonArray("passes");assertEquals(3,passes.size());var uniforms=passes.get(0).getAsJsonObject().getAsJsonArray("uniforms");for(String name:names){for(JsonArray list:List.of(program,uniforms)){var matches=new ArrayList<JsonObject>();for(var u:list) if(u.getAsJsonObject().get("name").getAsString().equals(name))matches.add(u.getAsJsonObject());assertEquals(1,matches.size(),name);assertEquals(0,matches.get(0).getAsJsonArray("values").get(0).getAsFloat());}}}
   @Test void existingShaderHasIdleFastPathAndOnePriorityBridge() throws Exception {var f=read("resources/assets/minecraft/shaders/program/dash_impact.fsh");assertTrue(f.contains("safeEraseCrack"));assertTrue(f.contains("eraseSite"));assertTrue(f.contains("pow(safeEraseRebuild, 3.0)"));var mixin=read("java/dev/baranhan/viltrumitecore/client/mixin/GameRendererDashMixin.java");assertTrue(mixin.contains("EraseCrack"));assertTrue(mixin.contains("ErasurePost.active()"));assertTrue(mixin.contains("viltrumiteflight"));assertTrue(mixin.contains("shutdownEffect()"));assertTrue(mixin.contains("loadEffect"));assertFalse(mixin.contains("new PostChain"));}
   static JsonObject json(String path) throws Exception {return JsonParser.parseString(read("resources/"+path)).getAsJsonObject();}
   static String read(String path) throws Exception {return Files.readString(Path.of("src/main/"+path));}
}
