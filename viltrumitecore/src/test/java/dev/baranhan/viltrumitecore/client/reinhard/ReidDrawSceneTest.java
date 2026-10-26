package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.reinhard.reid.ReidDrawScene;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.network.packet.ReidSceneS2CPacket;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ReidDrawSceneTest {
   @Test
   void sceneEndsWithoutPacket() {
      ReidDrawScene.clear();
      ReidDrawScene.onPacket(7, Vec3.ZERO, 1000L, ReidSceneS2CPacket.START);
      ReidDrawScene.prune(1000L + ReinhardRules.REID_DRAW_TICKS + ReidDrawScene.TIMEOUT_TICKS);
      assertNotNull(ReidDrawScene.of(7));
      ReidDrawScene.prune(1000L + ReinhardRules.REID_DRAW_TICKS + ReidDrawScene.TIMEOUT_TICKS + 1);
      assertNull(ReidDrawScene.of(7));
   }

   @Test
   void endPacketStopsScene() {
      ReidDrawScene.clear();
      ReidDrawScene.onPacket(7, Vec3.ZERO, 1000L, ReidSceneS2CPacket.START);
      ReidDrawScene.onPacket(7, Vec3.ZERO, 1000L, ReidSceneS2CPacket.END);
      assertNull(ReidDrawScene.of(7));
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
}
