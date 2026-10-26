package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.reinhard.reid.ReidDrawScene;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.network.packet.ReidSceneS2CPacket;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** REINHARD OVERHAUL item 15: one client scene per server draw, however its packets arrive. */
class ReidDrawSceneTest {
   private static final long START = 1000L;
   private static final Vec3 AT = new Vec3(10.37, 64.0, -3.81);

   @BeforeEach
   void reset() {
      ReidDrawScene.clear();
   }

   private static void packet(int phase, long now) {
      ReidDrawScene.onPacket(7, AT, START, phase, now);
   }

   @Test
   void sceneEndsWithoutPacket() {
      packet(ReidSceneS2CPacket.START, START);
      ReidDrawScene.prune(START + ReinhardRules.REID_DRAW_TICKS + ReidDrawScene.TIMEOUT_TICKS);
      assertNotNull(ReidDrawScene.of(7));
      ReidDrawScene.prune(START + ReinhardRules.REID_DRAW_TICKS + ReidDrawScene.TIMEOUT_TICKS + 1);
      assertNull(ReidDrawScene.of(7));
      assertNull(ReidDrawScene.get(7));
   }

   @Test
   void theSceneIsCentredOnTheExactPositionFromThePacket() {
      packet(ReidSceneS2CPacket.START, START);
      assertEquals(AT, ReidDrawScene.of(7).center(), "not the block corner or centre");
   }

   @Test
   void endLetsTheLightAndSoundDecayThenTheSceneGoes() {
      packet(ReidSceneS2CPacket.START, START);
      long end = START + ReinhardRules.REID_DRAW_TICKS;
      packet(ReidSceneS2CPacket.END, end);
      assertNull(ReidDrawScene.of(7), "the draw clock stops: the animation is in the drawn guard");
      ReidDrawScene.Active decaying = ReidDrawScene.get(7);
      assertNotNull(decaying);
      assertTrue(decaying.ending());
      assertFalse(decaying.aborted());
      assertTrue(ReidDrawScene.all().contains(decaying), "still rendered and heard");
      assertEquals(1.0F, ReidDrawScene.fade(decaying, end + 10, 0.0F), 1.0E-6F, "a natural end decays on the draw clock, not by a fade");
      assertEquals(130.0F, ReidDrawScene.tick(decaying, end + 10, 0.0F), 1.0E-4F, "the clock keeps running for the tail");
      ReidDrawScene.prune(end + ReidDrawScene.TAIL_TICKS);
      assertNotNull(ReidDrawScene.get(7));
      ReidDrawScene.prune(end + ReidDrawScene.TAIL_TICKS + 1);
      assertNull(ReidDrawScene.get(7));
   }

   @Test
   void abortFadesEverythingOutAtOnce() {
      packet(ReidSceneS2CPacket.START, START);
      long abort = START + 50;
      packet(ReidSceneS2CPacket.ABORT, abort);
      ReidDrawScene.Active fading = ReidDrawScene.get(7);
      assertTrue(fading.aborted());
      assertNull(ReidDrawScene.of(7));
      assertEquals(1.0F, ReidDrawScene.fade(fading, abort, 0.0F), 1.0E-6F);
      assertEquals(0.5F, ReidDrawScene.fade(fading, abort + ReidDrawScene.ABORT_FADE_TICKS / 2, 0.0F), 1.0E-6F);
      assertEquals(0.0F, ReidDrawScene.fade(fading, abort + ReidDrawScene.ABORT_FADE_TICKS, 0.0F), 1.0E-6F);
      float previous = 1.0F;
      for (float t = 0; t <= ReidDrawScene.ABORT_FADE_TICKS; t += 0.25F) {
         float fade = ReidDrawScene.fade(fading, abort + (long)t, t - (long)t);
         assertTrue(fade <= previous + 1.0E-6F, "monotone fade");
         previous = fade;
      }
      ReidDrawScene.prune(abort + ReidDrawScene.ABORT_FADE_TICKS);
      assertNotNull(ReidDrawScene.get(7));
      ReidDrawScene.prune(abort + ReidDrawScene.ABORT_FADE_TICKS + 1);
      assertNull(ReidDrawScene.get(7));
   }

   @Test
   void refreshesAndTheReleaseNeverRestartTheScene() {
      packet(ReidSceneS2CPacket.START, START);
      ReidDrawScene.Active scene = ReidDrawScene.of(7);
      packet(ReidSceneS2CPacket.START, START + 20);
      packet(ReidSceneS2CPacket.START, START + 40);
      packet(ReidSceneS2CPacket.RELEASE, START + ReinhardRules.REID_RELEASE_TICK);
      packet(ReidSceneS2CPacket.START, START + 100);
      assertSame(scene, ReidDrawScene.of(7));
      assertEquals(START, ReidDrawScene.of(7).startGameTime());
   }

   @Test
   void aLateJoinerEntersTheDrawAtTheRightMoment() {
      packet(ReidSceneS2CPacket.START, START + 60);
      ReidDrawScene.Active scene = ReidDrawScene.of(7);
      assertEquals(60.0F, ReidDrawScene.tick(scene, START + 60, 0.0F), 1.0E-6F, "not from tick 0");
      assertEquals(0.5F, ReidDrawScene.progress(scene.startGameTime(), START + 60, 0.0F), 1.0E-6F);
   }

   @Test
   void aLateRefreshOfAnEndedDrawIsIgnored() {
      packet(ReidSceneS2CPacket.START, START);
      packet(ReidSceneS2CPacket.END, START + ReinhardRules.REID_DRAW_TICKS);
      packet(ReidSceneS2CPacket.START, START + ReinhardRules.REID_DRAW_TICKS + 1);
      assertNull(ReidDrawScene.of(7), "the ended draw does not start again");
      ReidDrawScene.prune(START + 1000);
      packet(ReidSceneS2CPacket.RELEASE, START + 1001);
      assertNull(ReidDrawScene.get(7), "not even after its tail was pruned");
   }

   @Test
   void anAbortBeforeTheFirstPacketBlocksIt() {
      packet(ReidSceneS2CPacket.ABORT, START + 5);
      packet(ReidSceneS2CPacket.START, START + 6);
      assertNull(ReidDrawScene.get(7));
   }

   @Test
   void aNewerDrawReplacesTheOldOneAndAnOldEndCannotStopIt() {
      packet(ReidSceneS2CPacket.START, START);
      packet(ReidSceneS2CPacket.ABORT, START + 30);
      ReidDrawScene.onPacket(7, AT, START + 40, ReidSceneS2CPacket.START, START + 40);
      assertEquals(START + 40, ReidDrawScene.of(7).startGameTime(), "a new draw after the cut-short one");
      packet(ReidSceneS2CPacket.END, START + 41);
      assertNotNull(ReidDrawScene.of(7), "a late END of the older draw does not stop the new one");
      ReidDrawScene.onPacket(7, AT, START + 40, ReidSceneS2CPacket.START, START + 45);
      assertEquals(START + 40, ReidDrawScene.of(7).startGameTime());
      ReidDrawScene.onPacket(7, AT, START, ReidSceneS2CPacket.START, START + 46);
      assertEquals(START + 40, ReidDrawScene.of(7).startGameTime(), "an older start never replaces a newer one");
   }

   @Test
   void castersHaveTheirOwnScenes() {
      packet(ReidSceneS2CPacket.START, START);
      ReidDrawScene.onPacket(8, Vec3.ZERO, START + 3, ReidSceneS2CPacket.START, START + 3);
      packet(ReidSceneS2CPacket.ABORT, START + 4);
      assertNull(ReidDrawScene.of(7));
      assertNotNull(ReidDrawScene.of(8));
      assertEquals(2, ReidDrawScene.all().size());
   }

   @Test
   void theServerSequenceDeliveredTwiceGivesOneScene() {
      // Every packet the server sends for one draw, each one duplicated (e.g. two sends in one tick).
      Set<ReidDrawScene.Active> seen = new HashSet<>();
      for (int t = 0; t <= ReinhardRules.REID_DRAW_TICKS; t++) {
         int phase = t == ReinhardRules.REID_DRAW_TICKS ? ReidSceneS2CPacket.END
            : t == ReinhardRules.REID_RELEASE_TICK ? ReidSceneS2CPacket.RELEASE
            : t == 0 || t % ReinhardRules.REID_SCENE_REFRESH == 0 ? ReidSceneS2CPacket.START : -1;
         if (phase < 0) {
            continue;
         }
         for (int copy = 0; copy < 2; copy++) {
            packet(phase, START + t);
            if (ReidDrawScene.of(7) != null) {
               seen.add(ReidDrawScene.of(7));
            }
         }
      }
      assertEquals(1, seen.size(), "the scene never restarted");
      assertTrue(ReidDrawScene.get(7).ending());
   }

   @Test
   void crackRadiusGrows() {
      assertEquals(0.0F, ReidDrawScene.crackRadius(ReinhardRules.REID_CRACK_START - 1), 1.0E-6F);
      assertEquals(2.0F, ReidDrawScene.crackRadius(ReinhardRules.REID_CRACK_START), 1.0E-4F);
      float mid = ReidDrawScene.crackRadius((ReinhardRules.REID_CRACK_START + ReinhardRules.REID_CRACK_END) / 2.0F);
      assertTrue(mid > 2.0F && mid < 14.0F);
      assertEquals(14.0F, ReidDrawScene.crackRadius(ReinhardRules.REID_CRACK_END), 1.0E-4F);
      assertEquals(14.0F, ReidDrawScene.crackRadius(ReinhardRules.REID_DRAW_TICKS), 1.0E-4F);
   }

   @Test
   void progressFromGameTime() {
      assertEquals(0.5F, ReidDrawScene.progress(1000L, 1000L + ReinhardRules.REID_DRAW_TICKS / 2, 0.0F), 1.0E-4F);
      assertEquals(1.0F, ReidDrawScene.progress(1000L, 99999L, 0.0F), 1.0E-6F);
      assertEquals(0.0F, ReidDrawScene.progress(1000L, 900L, 0.0F), 1.0E-6F);
   }

   @Test
   void reidClipFollowsPhase() {
      assertEquals(ReinhardClips.REID_DRAW, ReinhardPoses.reidClip(ReinhardPoses.ReidPhase.DRAWING, 40.0).animation());
      assertEquals(2.0, ReinhardPoses.reidClip(ReinhardPoses.ReidPhase.DRAWING, 40.0).seconds(), 1.0E-9);
      assertEquals(ReinhardClips.REID_SHEATHE, ReinhardPoses.reidClip(ReinhardPoses.ReidPhase.SHEATHING, 5.0).animation());
      assertNull(ReinhardPoses.reidClip(ReinhardPoses.ReidPhase.SHEATHED, 5.0));
   }

   @Test
   void bladeSlidesOutAndBack() {
      assertEquals(0.0F, ReinhardPoses.bladeOut(ReinhardPoses.ReidPhase.SHEATHED, 0.0), 1.0E-6F);
      assertEquals(0.0F, ReinhardPoses.bladeOut(ReinhardPoses.ReidPhase.DRAWING, 0.0), 1.0E-6F);
      float mid = ReinhardPoses.bladeOut(ReinhardPoses.ReidPhase.DRAWING, ReinhardRules.REID_DRAW_TICKS / 2.0);
      assertTrue(mid > 0.0F && mid < 1.0F);
      assertEquals(1.0F, ReinhardPoses.bladeOut(ReinhardPoses.ReidPhase.DRAWING, ReinhardRules.REID_DRAW_TICKS), 1.0E-6F);
      assertEquals(1.0F, ReinhardPoses.bladeOut(ReinhardPoses.ReidPhase.SHEATHING, 0.0), 1.0E-6F);
      assertEquals(0.0F, ReinhardPoses.bladeOut(ReinhardPoses.ReidPhase.SHEATHING, ReinhardRules.REID_SHEATHE_TICKS), 1.0E-6F);
   }

   @Test
   void firstPersonPullFollowsBladeAndReturnsBeforeThePhaseEnds() {
      assertEquals(ReinhardFirstPersonPose.Offset.NONE, ReinhardFirstPersonPose.reid(ReinhardPoses.ReidPhase.SHEATHED, 10.0));
      assertTrue(Math.abs(ReinhardFirstPersonPose.reid(ReinhardPoses.ReidPhase.DRAWING, 100).pitch()) > 20);
      assertEquals(0, ReinhardFirstPersonPose.reid(ReinhardPoses.ReidPhase.DRAWING, ReinhardRules.REID_DRAW_TICKS).pitch(), 1.0E-4F);
   }

   @Test
   void firstPersonPullHandsOverToTheGuardWithoutStacking() {
      float pull = ReinhardFirstPersonPose.REID_PULL.pitch(), guard = ReinhardFirstPersonPose.DRAWN_GUARD.pitch();
      for (ReinhardPoses.ReidPhase phase : new ReinhardPoses.ReidPhase[]{ReinhardPoses.ReidPhase.DRAWING, ReinhardPoses.ReidPhase.SHEATHING}) {
         int length = phase == ReinhardPoses.ReidPhase.DRAWING ? ReinhardRules.REID_DRAW_TICKS : ReinhardRules.REID_SHEATHE_TICKS;
         float previousPitch = Float.NaN;
         for (double t = 0; t <= length; t += 0.25) {
            float a = ReinhardFirstPersonPose.reid(phase, t).pitch() / pull;
            float b = ReinhardFirstPersonPose.drawnGuard(phase, t).pitch() / guard;
            assertTrue(a + b <= 1.0F + 1.0E-4F, phase + " at " + t + ": pull " + a + " + guard " + b);
            float pitch = ReinhardFirstPersonPose.reid(phase, t).pitch() + ReinhardFirstPersonPose.drawnGuard(phase, t).pitch();
            // The 6 s draw is slow and heavy; the 1 s sheathe snaps the blade home (faster, still no pop).
            float limit = phase == ReinhardPoses.ReidPhase.DRAWING ? 3.0F : 12.0F;
            if (!Float.isNaN(previousPitch)) {
               assertTrue(Math.abs(pitch - previousPitch) < limit, phase + " pitch jumps at " + t);
            }
            previousPitch = pitch;
         }
      }
   }
}
