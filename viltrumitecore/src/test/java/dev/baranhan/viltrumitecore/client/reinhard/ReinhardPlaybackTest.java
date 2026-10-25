package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.pose.ActionClock;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReinhardPlaybackTest {
   private static Animation clip(Animation.LoopType loop) {
      return new Animation("test", 1, loop, Map.of(), List.of());
   }
   @Test void worldCinematicsStayOnTheAuthoritativeClockOfTheirScenes() {
      for (ReinhardAbility cinematic : new ReinhardAbility[]{ReinhardAbility.WORLD_CUT,
         ReinhardAbility.DIAGONAL_WORLD_CUT, ReinhardAbility.WORLD_ERASURE}) {
         var clock = new ActionClock();
         int id = cinematic.ordinal();
         assertEquals(10, ReinhardPlayback.sample(clock, id, 0, 10, 120, 100, 0F), 1e-6);
         // A late snapshot: frames keep coming, the pose stays on elapsed + partial (never ahead of the scene).
         for (int frame = 1; frame <= 10; frame++) {
            double now = 100 + frame * .25;
            float partial = (float)(now - Math.floor(now));
            assertEquals(10 + partial, ReinhardPlayback.sample(clock, id, 0, 10, 120, now, partial), 1e-6, cinematic + " at " + now);
         }
         assertEquals(120, ReinhardPlayback.sample(clock, id, 0, 130, 120, 110, .5F), 1e-6, "clamped to the cast length");
      }
   }
   @Test void ordinaryStrikesKeepTheBoundedPredictiveClock() {
      var clock = new ActionClock();
      int strike = ReinhardAbility.REID_STRIKE.ordinal();
      assertFalse(ReinhardPlayback.authoritative(strike));
      ReinhardPlayback.sample(clock, strike, 0, 4, 20, 100, 0F);
      double t = 0;
      for (int frame = 1; frame <= 10; frame++) {
         double now = 100 + frame * .25;
         t = ReinhardPlayback.sample(clock, strike, 0, 4, 20, now, (float)(now - Math.floor(now)));
      }
      assertTrue(t > 5 && t <= 7 + 1e-9, "strikes predict past a late snapshot, at most three ticks: " + t);
   }
   @Test void walkingStillCyclesAfterFirstSecondAndLongSession() {
      Animation walk = clip(Animation.LoopType.LOOP);
      for (double age : new double[]{0.25, 1.25, 10001.25})
         assertEquals(.25, ReinhardPlayback.time(walk, age), 1e-8);
   }
   @Test void heldActionsNeverWrap() {
      assertEquals(1, ReinhardPlayback.time(clip(Animation.LoopType.HOLD_ON_LAST_FRAME), 10));
   }
   @Test void invalidTimesCannotPoisonModel() {
      assertEquals(0, ReinhardPlayback.time(clip(Animation.LoopType.LOOP), Double.NaN));
      assertEquals(0, ReinhardPlayback.time(clip(Animation.LoopType.LOOP), -1));
   }
   @Test void contactsStayFullWeightButTransitionsRecoverToLocomotion() {
      assertEquals(0, ReinhardPlayback.weight(0, .4));
      assertEquals(1, ReinhardPlayback.weight(.15, .4));
      assertEquals(1, ReinhardPlayback.weight(.25, .4));
      assertEquals(0, ReinhardPlayback.weight(.4, .4));
      assertTrue(ReinhardPlayback.weight(.35, .4) < 1);
   }
   @Test void bothViewsUseBoundedHitStopTime() {
      assertEquals(5.5, ReinhardPlayback.actionTicks(8,16,.5F,3));
      assertEquals(16, ReinhardPlayback.actionTicks(30,16,.5F,0));
      assertEquals(0, ReinhardPlayback.actionTicks(0,16,0,3));
      assertEquals(4, ReinhardPlayback.actionTicks(4,16,Float.NaN,Double.NaN));
   }
   @Test void continuingActionsKeepTheirGuardAndRecoverAtTheEnd() {
      assertEquals(1, ReinhardPlayback.exit(0,.3));
      assertEquals(1, ReinhardPlayback.exit(.05,.3));
      assertEquals(0, ReinhardPlayback.exit(.3,.3));
   }
}
