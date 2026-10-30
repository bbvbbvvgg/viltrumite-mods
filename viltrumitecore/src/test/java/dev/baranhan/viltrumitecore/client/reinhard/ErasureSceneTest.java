package dev.baranhan.viltrumitecore.client.reinhard;

import static dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.client.reinhard.erasure.*;
import dev.baranhan.viltrumitecore.client.reinhard.erasure.ErasureScene.Cue;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ErasureSceneTest {
   static final class Env implements ErasureScene.SoundEnvironment {
      final List<String> played = new ArrayList<>(); final List<Integer> at = new ArrayList<>();
      int ducks, releases, stops, fadeIn = -1, fadeOut = -1; boolean held; ErasureScene.Timeline clock;
      public void play(Cue cue, float pitch) {played.add(cue.name()); at.add(clock == null ? -1 : clock.age());}
      public void duck(int fade) {ducks++; fadeIn = fade; held = true;}
      public void release(int fade) {releases++; fadeOut = fade; held = false;}
      public boolean ducking() {return held;}
      public void stop() {stops++;}
      int count(Cue cue) {return Collections.frequency(played, cue.name());}
   }
   static ErasureScene.Timeline run(Env env, int from, int to) {
      var clock = new ErasureScene.Timeline(from, env); env.clock = clock;
      for (int t = from + 1; t <= to; t++) clock.advance(t);
      return clock;
   }

   @Test void beatsFollowTheOneTimelineInOrder() {
      var beats = ErasureScene.BEATS;
      for (int i = 1; i < beats.size(); i++) assertTrue(beats.get(i).tick() >= beats.get(i - 1).tick(), "ordered: " + beats.get(i));
      assertEquals(ERASE_CRACK_START, beats.get(0).tick()); assertEquals(Cue.TENSION, beats.get(0).cue());
      assertTrue(beats.stream().anyMatch(b -> b.cue() == Cue.SHATTER && b.tick() == ERASE_SHATTER_START));
      assertTrue(beats.stream().anyMatch(b -> b.cue() == Cue.DISSOLVE && b.tick() == ERASE_DISSOLVE_START));
      assertEquals(Cue.RESTORE, beats.get(beats.size() - 1).cue()); assertEquals(ERASE_REBUILD_END, beats.get(beats.size() - 1).tick());
      assertEquals(ErasureGlass.SNAPS.length - 1, beats.stream().filter(b -> b.cue() == Cue.SNAP).count(), "a snap per ring, the last ring is the final impact");
      for (var b : beats) assertTrue(b.tick() < ERASE_LENGTH && b.pitch() > .5F && b.pitch() < 1.5F);
   }

   @Test void fullRunPlaysEveryBeatOnceOnItsTick() {
      var env = new Env(); var clock = run(env, 0, ERASE_LENGTH + 5);
      assertEquals(ErasureScene.BEATS.size(), env.played.size());
      for (int i = 0; i < env.played.size(); i++) {
         assertEquals(ErasureScene.BEATS.get(i).cue().name(), env.played.get(i));
         assertEquals(ErasureScene.BEATS.get(i).tick(), env.at.get(i));
      }
      assertEquals(1, env.ducks); assertEquals(ErasureScene.DUCK_FADE, env.fadeIn);
      assertEquals(1, env.releases); assertEquals(ErasureScene.RELEASE_FADE, env.fadeOut);
      assertTrue(clock.ended()); assertEquals(0, env.stops, "a finished scene lets its last beat ring out");
   }

   @Test void duckFromThePressReleasedOnTheFinalImpact() {
      var env = new Env(); var clock = run(env, 0, ERASE_REBUILD_END - 1);
      assertTrue(env.held); assertEquals(0, env.releases);
      clock.advance(ERASE_REBUILD_END); assertEquals(1, env.releases); assertFalse(env.held);
   }

   @Test void lateJoinCatchesUpWithoutReplayingEarlierBeats() {
      var env = new Env(); var clock = run(env, 100, 101);
      assertEquals(0, env.count(Cue.TENSION)); assertEquals(0, env.count(Cue.SHATTER)); assertEquals(0, env.count(Cue.CRACK));
      assertEquals(1, env.ducks); assertEquals(0, env.fadeIn, "joined mid-scene: already at depth");
      for (int t = 102; t <= ERASE_LENGTH; t++) clock.advance(t);
      assertEquals(1, env.count(Cue.DISSOLVE)); assertEquals(1, env.count(Cue.RESTORE));
   }

   @Test void beatWithinTheLateWindowStillPlays() {
      var env = new Env(); new ErasureScene.Timeline(ERASE_SHATTER_START + ErasureScene.LATE, env);
      assertEquals(1, env.count(Cue.SHATTER));
      var late = new Env(); new ErasureScene.Timeline(ERASE_SHATTER_START + ErasureScene.LATE + 1, late);
      assertEquals(0, late.count(Cue.SHATTER));
   }

   @Test void bigCatchUpJumpSkipsMissedBeats() {
      var env = new Env(); var clock = new ErasureScene.Timeline(0, env); env.clock = clock;
      clock.advance(150);
      assertEquals(0, env.played.size(), "every beat before 147 was missed: none replays at once");
      clock.advance(ERASE_DARK_END); assertEquals(1, env.count(Cue.INHALE));
   }

   @Test void joinAfterTheFinalImpactNeverDucks() {
      var env = new Env(); var clock = new ErasureScene.Timeline(ERASE_REBUILD_END + 2, env);
      assertEquals(0, env.ducks); assertEquals(1, env.count(Cue.RESTORE)); assertFalse(clock.ended());
   }

   @Test void staleSceneEndsAtOnceAndSilently() {
      var env = new Env(); var clock = new ErasureScene.Timeline(ERASE_LENGTH, env);
      assertTrue(clock.ended()); assertEquals(0, env.ducks); assertEquals(0, env.played.size());
      assertEquals(ErasureUniforms.ZERO, clock.uniforms(0));
   }

   @ParameterizedTest @EnumSource(ErasureScene.EndReason.class)
   void duckReleasedOnceOnEveryEnd(ErasureScene.EndReason reason) {
      var env = new Env(); var clock = run(env, 0, 60);
      clock.end(reason); clock.end(reason); clock.advance(120);
      assertEquals(1, env.releases); assertTrue(clock.ended()); assertFalse(env.held);
      assertEquals(reason == ErasureScene.EndReason.NORMAL ? 0 : 1, env.stops);
      assertEquals(0, env.count(Cue.DISSOLVE), "an ended scene plays nothing more");
      assertEquals(ErasureUniforms.ZERO, clock.uniforms(.5F));
   }

   @Test void levelChangeMidSceneReRegistersTheDuck() {
      var env = new Env(); var clock = new ErasureScene.Timeline(90, env); assertEquals(1, env.ducks); assertEquals(0, env.fadeIn);
      env.held = false; // SoundDuckEvents saw the new ClientLevel and cleared every duck
      clock.advance(91); assertEquals(2, env.ducks); assertTrue(env.held); assertEquals(0, env.fadeIn);
      clock.advance(92); assertEquals(2, env.ducks, "registered once, not every tick");
      clock.advance(ERASE_REBUILD_END); assertEquals(1, env.releases); env.held = false;
      clock.advance(ERASE_REBUILD_END + 1); assertEquals(2, env.ducks, "no duck after the release");
   }

   @Test void releasedDuckIsNotRestoredAfterACancel() {
      var env = new Env(); var clock = new ErasureScene.Timeline(40, env); clock.end(ErasureScene.EndReason.CANCEL);
      env.held = false; clock.advance(50); assertEquals(1, env.ducks);
   }

   @Test void localClockSurvivesDimensionGameTimeAndCatchUp() {
      assertEquals(60, ErasureScene.elapsed(60, 500, 500)); assertEquals(70, ErasureScene.elapsed(60, 500, 510));
      assertEquals(60, ErasureScene.elapsed(60, 500, 499)); assertEquals(Integer.MAX_VALUE, ErasureScene.elapsed(60, 0, Long.MAX_VALUE));
   }

   @Test void repeatedCatchUpDoesNotFreezeOrRewindScene() {
      var clock = new ErasureScene.Clock(60, 500); assertEquals(61, clock.at(501));
      clock.sync(90, 501); assertEquals(91, clock.at(502)); clock.sync(40, 503); assertEquals(93, clock.at(504));
   }

   @Test void glassOriginFollowsTheTargetOrFallsBackToTheCentre() {
      assertArrayEquals(new float[]{0, 0}, ErasureScene.origin(10, 10, 100, 100, 1.6F, false));
      assertArrayEquals(new float[]{0, 0}, ErasureScene.origin(Float.NaN, 10, 100, 100, 1.6F, true));
      assertArrayEquals(new float[]{0, 0}, ErasureScene.origin(50, 50, 100, 100, 1, true), 1e-6F);
      float[] o = ErasureScene.origin(75, 25, 100, 100, 2, true);
      assertEquals(1, o[0], 1e-6F); assertEquals(.5F, o[1], 1e-6F);
      float[] edge = ErasureScene.origin(100, 100, 100, 100, 1, true);
      assertEquals(.8F, edge[0], 1e-6F); assertEquals(-.8F, edge[1], 1e-6F);
   }

   @Test void shakeIsSceneTimeOnlyStrongestOnTheFinalImpactAndSettlesToZero() {
      assertEquals(0, ErasureShake.power(0)); assertEquals(0, ErasureShake.power(ERASE_CRACK_END), "no shake while the cracks grow");
      assertTrue(ErasureShake.power(ERASE_SHATTER_START) > .3F, "the rupture jolts");
      float last = ErasureGlass.SNAPS[ErasureGlass.SNAPS.length - 1];
      assertEquals(1, ErasureShake.power(last), 1e-6F);
      for (int i = 0; i < ErasureGlass.SNAPS.length - 1; i++) assertTrue(ErasureShake.power(ErasureGlass.SNAPS[i]) < 1);
      assertTrue(ErasureShake.power(ERASE_DARK_END + 1) > 0, "reality pulling back rumbles");
      assertEquals(0, ErasureShake.power(ERASE_LENGTH)); assertEquals(0, ErasureShake.power(ERASE_LENGTH - .001F), 1e-5F);
      assertEquals(ErasureShake.Offset.ZERO, ErasureShake.at(ERASE_LENGTH, 1));
      assertEquals(ErasureShake.Offset.ZERO, ErasureShake.at(last, 0), "the shake setting can turn it off");
      assertEquals(ErasureShake.at(180.25F, 1), ErasureShake.at(180.25F, 1), "deterministic: every client shakes alike");
      var o = ErasureShake.at(last + .5F, 2); var cap = ErasureShake.at(last + .5F, 99);
      assertEquals(o, cap, "setting clamped to 2");
      assertTrue(Math.abs(o.roll()) < 12 && Math.abs(o.pitch()) < 8 && Math.abs(o.yaw()) < 6);
      assertEquals(ErasureShake.at(last + .5F, Float.NaN), ErasureShake.at(last + .5F, 1));
   }

   @Test void runtimeWiring() throws Exception {
      var s = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/client/reinhard/erasure/ErasureScene.java"));
      for (String key : List.of("ScreenProjector.project", "SimpleSoundInstance.forUI", "WorldPauseClient.onEnd", "ErasurePost.bind",
         "CinematicHud.hideWhile", "SceneEntityHide.register", "ErasureGlassRenderer.render", "ErasureDissolveRenderer.render",
         "ErasureGlassRenderer.release()", "ErasureDissolveRenderer.reset()", "captureProjection"))
         assertTrue(s.contains(key), key);
      assertFalse(s.contains("new PostChain")); assertFalse(s.contains("GuiEntitySilhouette"), "no flat GUI silhouette: the real model dissolves");
      var client = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/client/reinhard/ReinhardClient.java"));
      for (String key : List.of("ErasureScene.init()", "ErasureScene::onPacket", "ErasureScene::onClientTick", "ErasureScene::onRenderLevel",
         "ErasureScene::onCameraAngles", "ErasureScene::onUnload", "ErasureScene::onLogout"))
         assertTrue(client.contains(key), key);
      var mixin = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/client/mixin/GameRendererDashMixin.java"));
      assertTrue(mixin.indexOf("ErasureScene.renderSeam") > mixin.lastIndexOf("ViltrumiteShaders.process"), "geometry after the grade");
      var mixins = Files.readString(Path.of("src/main/resources/viltrumitecore.client.mixins.json"));
      assertTrue(mixins.contains("\"SceneEntityHideMixin\""));
   }
}
