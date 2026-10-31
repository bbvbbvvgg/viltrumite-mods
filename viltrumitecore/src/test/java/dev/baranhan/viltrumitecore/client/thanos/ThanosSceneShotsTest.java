package dev.baranhan.viltrumitecore.client.thanos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.thanos.ThanosSceneShots.Frame;
import dev.baranhan.viltrumitecore.client.thanos.ThanosSceneShots.Shot;
import dev.baranhan.viltrumitecore.client.thanos.ThanosSceneShots.V;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSceneTimeline;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * The scene camera: always finite and sane, starts and ends in the player's eyes, never jumps
 * without a hard cut (the camera entity would swoop across the jump for a tick), and cuts exactly
 * where the shots change (lines included).
 */
class ThanosSceneShotsTest {
   /** Thanos at the origin facing +z, the player 12 blocks in front looking at him. */
   static Frame near() {
      V o = new V(0.5, 64.0, 0.5);
      V fwd = new V(0.0, 0.0, 1.0);
      V left = new V(1.0, 0.0, 0.0);
      V gauntlet = o.add(left.scale(1.1)).add(fwd.scale(0.5)).up(3.0);
      V eye = o.add(fwd.scale(12.0)).up(1.62);
      return new Frame(true, o, fwd, left, gauntlet, gauntlet.up(0.1), o.up(3.45), o.add(fwd.scale(ThanosSceneClient.OD_NEAR)), eye,
         new V(0.0, -0.1, -1.0).normalize());
   }

   /** Thanos far away: the player alone, looking along +x. */
   static Frame far() {
      V feet = new V(200.5, 70.0, -40.5);
      V fwd = new V(1.0, 0.0, 0.0);
      V left = new V(0.0, 0.0, -1.0);
      V chest = feet.up(1.0);
      V eye = feet.up(1.62);
      return new Frame(false, feet, fwd, left, chest, chest, eye, feet.add(fwd.scale(ThanosSceneClient.OD_FAR)), eye, fwd);
   }

   private static double angle(V a, V b) {
      V x = a.normalize();
      V y = b.normalize();
      double dot = Math.max(-1.0, Math.min(1.0, x.x() * y.x() + x.y() * y.y() + x.z() * y.z()));
      return Math.toDegrees(Math.acos(dot));
   }

   @Test
   void everyShotIsFiniteAndSane() {
      for (Frame f : new Frame[]{near(), far()}) {
         for (float t = 0.0F; t <= ThanosSceneTimeline.LENGTH; t += 0.25F) {
            Shot s = ThanosSceneShots.at(f, t);
            assertTrue(s.pos().finite() && s.target().finite(), "finite at " + t);
            assertTrue(s.fov() >= 30.0F && s.fov() <= 100.0F, "fov " + s.fov() + " at " + t);
            assertTrue(s.pos().sub(s.target()).length() > 0.2, "the camera looks at something at " + t);
         }
      }
   }

   @Test
   void startsAndEndsInThePlayersEyes() {
      for (Frame f : new Frame[]{near(), far()}) {
         Shot first = ThanosSceneShots.at(f, 0.0F);
         assertEquals(0.0, first.pos().sub(f.eye()).length(), 1.0E-6, "starts in the eyes");
         assertEquals(ThanosSceneShots.NORMAL_FOV, first.fov(), 1.0E-4);
         assertTrue(angle(first.target().sub(first.pos()), f.look()) < 0.5, "looking where the player looked");
         Shot last = ThanosSceneShots.at(f, ThanosSceneShots.BACK_END);
         assertEquals(0.0, last.pos().sub(f.eye()).length(), 1.0E-3, "back in the eyes");
         assertEquals(ThanosSceneShots.NORMAL_FOV, last.fov(), 1.0E-3);
         assertTrue(angle(last.target().sub(last.pos()), f.look()) < 0.5);
      }

      assertEquals(0.0F, ThanosSceneShots.weight(-1.0F));
      assertEquals(0.0F, ThanosSceneShots.weight(ThanosSceneTimeline.LENGTH));
      assertEquals(1.0F, ThanosSceneShots.weight(ThanosSceneTimeline.SPEECH_START + 50), 1.0E-6F);
   }

   /** Between two ticks with no cut the camera moves and turns like a camera, not a jump. */
   @Test
   void noJumpWithoutACut() {
      for (Frame f : new Frame[]{near(), far()}) {
         List<String> jumps = new ArrayList<>();
         for (int t = 0; t < ThanosSceneTimeline.LENGTH; t++) {
            Shot a = ThanosSceneShots.at(f, t);
            Shot b = ThanosSceneShots.at(f, t + 1);
            if (ThanosSceneShots.cutBetween(f.near(), t, t + 1)) {
               continue;
            }

            double moved = b.pos().sub(a.pos()).length();
            double turned = angle(a.target().sub(a.pos()), b.target().sub(b.pos()));
            if (moved > 2.0 || turned > 20.0 || Math.abs(a.fov() - b.fov()) > 4.0F) {
               jumps.add(t + "->" + (t + 1) + String.format(" (%.2f blocks, %.1f deg, fov %.1f->%.1f)", moved, turned, a.fov(), b.fov()));
            }
         }

         assertTrue(jumps.isEmpty(), (f.near() ? "near" : "far") + " jumps without a cut: " + jumps);
      }
   }

   @Test
   void cutsSitWhereTheShotsChange() {
      for (boolean nearPlan : new boolean[]{true, false}) {
         float[] cuts = nearPlan ? ThanosSceneShots.NEAR_CUTS : ThanosSceneShots.FAR_CUTS;
         for (int line = 1; line < ThanosSceneTimeline.LINES; line++) {
            float at = ThanosSceneTimeline.SPEECH_START + line * ThanosSceneTimeline.LINE_TICKS;
            boolean found = false;
            for (float c : cuts) {
               found |= c == at;
            }

            assertTrue(found, "a cut between line " + line + " and " + (line + 1) + " (" + at + ")");
         }

         for (int i = 1; i < cuts.length; i++) {
            assertTrue(cuts[i] > cuts[i - 1], "cuts in order");
         }

         assertTrue(ThanosSceneShots.cutBetween(nearPlan, ThanosSceneTimeline.SPEECH_END - 0.5F, ThanosSceneTimeline.SPEECH_END));
         assertTrue(!ThanosSceneShots.cutBetween(nearPlan, 0.0F, 1.0F), "the start blends");
      }
   }
   /**
    * Found in a real game: the last ticks of the way back flew through the hero's own head (his
    * face filled the screen for a moment). Wherever the camera is inside or right at his head on
    * the way out or back, his body is not drawn; on the wide shots it is.
    */
   @Test
   void theHeroIsNotDrawnWhereTheCameraPassesHisHead() {
      for (Frame f : new Frame[]{near(), far()}) {
         V e = f.eye();
         AABB body = new AABB(e.x() - 0.3, e.y() - 1.62, e.z() - 0.3, e.x() + 0.3, e.y() + 0.18, e.z() + 0.3);
         int hidden = 0;
         for (float t = 0.0F; t <= ThanosSceneTimeline.LENGTH; t += 0.25F) {
            Shot s = ThanosSceneShots.at(f, t);
            Vec3 cam = new Vec3(s.pos().x(), s.pos().y(), s.pos().z());
            boolean atHead = s.pos().sub(e).length() < 0.6;
            if (atHead) {
               assertTrue(ThanosSceneClient.hidesSelf(cam, body), (f.near() ? "near" : "far") + " t=" + t + ": the camera is at his head, his body must not be drawn");
               hidden++;
            }

            if (t > ThanosSceneShots.LEAVE + 1.0F && t < ThanosSceneShots.BACK_START) {
               assertTrue(!ThanosSceneClient.hidesSelf(cam, body) || s.pos().sub(e).length() < 1.5, "t=" + t + ": a wide shot shows him");
            }
         }

         assertTrue(hidden > 0, "the camera does reach his eyes");
         assertTrue(ThanosSceneClient.hidesSelf(new Vec3(e.x(), e.y(), e.z()), body));
         assertTrue(!ThanosSceneClient.hidesSelf(new Vec3(e.x(), e.y(), e.z() + 3.0), body));
      }
   }
}
