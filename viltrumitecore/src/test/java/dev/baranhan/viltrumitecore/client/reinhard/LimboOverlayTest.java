package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import dev.baranhan.viltrumitecore.client.reinhard.limbo.*;
import dev.baranhan.viltrumitecore.client.hero.BodyIsolationClient;
import dev.baranhan.viltrumitecore.client.render.vfx.FlashOverlay;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LimboOverlayTest {
   @ParameterizedTest @CsvSource({"0,0,0","10,0,.5","20,0,1","99,0,1","100,1,0","220,2,1","320,3,1","400,4,0","420,4,1","500,4,1","599,4,1"})
   void lineTimings(int elapsed,int line,float alpha){assertEquals(line,LimboOverlay.lineIndex(elapsed));assertEquals(alpha,LimboOverlay.lineAlpha(elapsed),1e-6);}
   @Test void tinyTextFadeCannotBecomeOpaqueViaVanillaFont(){assertEquals(0,LimboOverlay.textArgb(.005F));assertEquals(0,LimboOverlay.textArgb(0));assertEquals(13,LimboOverlay.textArgb(.05F)>>>24);assertEquals(0xFFFFFFFF,LimboOverlay.textArgb(1));}
   @Test void oldWhiteFlashesStillHaveLinearOutFade(){assertEquals(1,FlashOverlay.alpha(1,20,20,0));assertEquals(.5F,FlashOverlay.alpha(1,20,10,0));assertEquals(0,FlashOverlay.alpha(1,20,1,1));assertEquals(0,FlashOverlay.alpha(1,0,0,0));}
   @Test void flashCurve(){for(float[] pair:new float[][]{{-1,0},{0,0},{5,.5F},{10,1},{25,.5F},{40,0},{41,0}}){assertEquals(pair[1],LimboOverlay.flashAlpha(pair[0]),1e-6);assertEquals(pair[1],FlashOverlay.envelopeAlpha(1,10,30,pair[0]),1e-6);}}
   @Test void heartbeatStopsOnLeave(){var env=new Env();var state=new LimboOverlay.State(env);state.start(600);state.tick(true,false);assertEquals(1,env.beats);for(int i=0;i<24;i++)state.tick(true,false);assertEquals(2,env.beats);state.tick(false,true);assertEquals(1,env.stops);assertFalse(state.active());for(int i=0;i<50;i++)state.tick(false,true);assertEquals(2,env.beats);}
   @Test void heartbeatStopsOnEndAndLogout(){var env=new Env();var state=new LimboOverlay.State(env);state.start(600);state.tick(true,false);state.end();assertEquals(1,env.stops);state.tick(false,true);assertEquals(1,env.flashes);state.reset();assertFalse(state.active());assertEquals(-1,state.flashTicks());}
   @Test void handsAndHudHiddenOnlyInLimbo(){for(String dimension:List.of("minecraft:overworld","minecraft:the_nether","minecraft:the_end","another:the_void")) {assertFalse(LimboOverlay.hidesHands(dimension));assertFalse(BodyIsolationClient.active(dimension));}assertTrue(LimboOverlay.hidesHands("viltrumitecore:the_void"));for(String hud:List.of("hotbar","crosshair","experience_bar","player_health","food_level","armor_level"))assertTrue(LimboOverlay.hidesHud("viltrumitecore:the_void",hud));assertFalse(LimboOverlay.hidesHud("viltrumitecore:the_void","chat_panel"));assertFalse(LimboOverlay.hidesHud("viltrumitecore:the_void","debug_text"));assertFalse(LimboOverlay.hidesHud("minecraft:overworld","hotbar"));}
   @Test void loginKeepsStoredTextTime(){var state=new LimboOverlay.State(new Env());state.start(325);assertEquals(275,state.elapsed());assertEquals(2,LimboOverlay.lineIndex(state.elapsed()));state.tick(false,true);assertEquals(325,state.left());state.tick(true,false);assertEquals(324,state.left());}
   @Test void flashWaitsForOverworldFrames(){var env=new Env();var state=new LimboOverlay.State(env);state.start(2);state.end();for(int i=0;i<50;i++)state.tick(true,false);assertEquals(-1,state.flashTicks());assertEquals(0,env.flashes);state.tick(false,true);assertEquals(0,state.flashTicks());assertEquals(1,env.flashes);state.tick(false,true);assertEquals(1,state.flashTicks());}
   @Test void pendingStartSurvivesDimensionPacketOrder(){var env=new Env();var state=new LimboOverlay.State(env);state.start(600);for(int i=0;i<3;i++)state.tick(false,true);assertTrue(state.active());assertEquals(600,state.left());assertEquals(0,env.beats);state.tick(true,false);assertEquals(1,env.beats);}
   @Test void heartbeatPeriodIs24Ticks(){assertEquals(24,LimboOverlay.HEARTBEAT_TICKS);assertEquals(50,1200/LimboOverlay.HEARTBEAT_TICKS);}
   @Test void emptyDimensionEffectsAreBlackWithoutSkyClouds(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();var effects=new TheVoidEffects();assertTrue(Float.isNaN(effects.getCloudHeight()));assertEquals(DimensionSpecialEffects.SkyType.NONE,effects.skyType());assertTrue(effects.isFoggyAt(0,0));assertEquals(Vec3.ZERO,effects.getBrightnessDependentFogColor(new Vec3(1,1,1),1));}
   @Test void allElevenLocalesHaveFiveLinesAndHeartbeat() throws Exception {var dir=Path.of("src/main/resources/assets/viltrumitecore/lang");List<Path> files;try(var stream=Files.list(dir)){files=stream.filter(p->p.toString().endsWith(".json")).toList();}assertEquals(11,files.size());for(var file:files){var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();for(int line=1;line<=5;line++){var key="viltrumitecore.limbo.line"+line;assertTrue(json.has(key),file+" "+key);assertFalse(json.get(key).getAsString().isBlank());}}var sounds=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/viltrumitecore/sounds.json"))).getAsJsonObject();assertEquals("viltrumitecore:reinhard/limbo_heartbeat",sounds.getAsJsonObject("reinhard.limbo_heartbeat").getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString());}
   @Test void runtimeHooksDoNotUseAScreenAndHideGenericPanel() throws Exception {var overlay=read("client/reinhard/limbo/LimboOverlay.java");assertTrue(overlay.contains("RenderHandEvent"));assertTrue(overlay.contains("EventPriority.HIGHEST"));assertTrue(overlay.contains("MovementInputUpdateEvent"));assertTrue(overlay.contains("FlashOverlay.flashEnvelope"));assertFalse(overlay.contains("setScreen("));assertFalse(overlay.contains("extends Screen"));assertTrue(read("client/reinhard/limbo/TheVoidEffects.java").contains("RegisterDimensionSpecialEffectsEvent"));assertTrue(read("client/mixin/ViltrumiteInGameHudMixin.java").contains("BodyIsolationClient.active(player)"));assertTrue(read("client/reinhard/ReinhardClient.java").contains("LimboOverlay::onPacket"));assertTrue(read("client/reinhard/limbo/LimboHeartbeat.java").contains("extends AbstractTickableSoundInstance"));}
   static String read(String path) throws Exception {return Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/"+path));}
   static final class Env implements LimboOverlay.Environment {int beats,stops,flashes;public void beat(){beats++;}public void stopHeartbeat(){stops++;}public void flash(){flashes++;}}
}
