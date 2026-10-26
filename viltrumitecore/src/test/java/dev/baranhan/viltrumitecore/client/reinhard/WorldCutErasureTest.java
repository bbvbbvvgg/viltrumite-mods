package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.client.reinhard.cut.WorldCutErasureProbe;
import dev.baranhan.viltrumitecore.client.render.erasure.ErasurePath;
import dev.baranhan.viltrumitecore.client.render.erasure.ErasureStroke;
import dev.baranhan.viltrumitecore.client.render.erasure.ErasureStyle;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutShape;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** REINHARD OVERHAUL 3b, playtest item 3: ONE continuous The Hand erasure along the whole ordinary World Cut. */
class WorldCutErasureTest {
   private static final double BLAST = ReinhardRules.WORLD_CUT_BLAST;
   private static final float[] YAWS = {0F, 33F, 90F, 145F, 211F, 300F};

   private static WorldCutShape shape(float yaw, double y) {
      return new WorldCutShape(new Vec3(12.5, y, -40.25), Vec3.directionFromRotation(0, yaw), 77L);
   }

   private static int[] flat(int top) {
      int[] surface = new int[ReinhardRules.CUT_LENGTH];
      java.util.Arrays.fill(surface, top);
      return surface;
   }

   /** Rolling hills, a cliff and a pit: what the axis has to glide over. */
   private static int[] rough(int top) {
      int[] surface = new int[ReinhardRules.CUT_LENGTH];
      for (int i = 0; i < surface.length; i++)
         surface[i] = top + (int)Math.round(6 * Math.sin(i * 0.09) + (i >= 120 && i < 140 ? 9 : 0) - (i >= 60 && i < 66 ? 7 : 0));
      return surface;
   }

   @Test void oneStrokeFromTheGatheringFlameToTheLastClosingSeam() {
      WorldCutShape g = shape(33F, 64);
      int[] surface = flat(63);
      float close = WorldCutErasureProbe.closeAt(Double.POSITIVE_INFINITY);
      assertNull(WorldCutErasureProbe.stroke(g, surface, BLAST - 7, close, 0F), "nothing before the flame gathers");
      ErasureStroke gather = WorldCutErasureProbe.stroke(g, surface, BLAST - 4, close, 0F);
      assertNotNull(gather);
      assertTrue(gather.aura() > 0F, "the flame gathers on the tail before the impact");
      assertEquals(0F, gather.headS(), 1E-6, "no tube before the impact");
      for (double t = BLAST; t <= BLAST + 20 * (close + WorldCutErasureProbe.CLOSE); t += 0.5) {
         ErasureStroke s = WorldCutErasureProbe.stroke(g, surface, t, close, 0F);
         assertNotNull(s, "one stroke at " + t);
         assertEquals(0F, (float)(s.length() - WorldCutErasureProbe.path(g, surface).length()), 1E-3F, "the whole path");
      }
      assertNull(WorldCutErasureProbe.stroke(g, surface, BLAST + 20 * (close + WorldCutErasureProbe.CLOSE) + 1, close, 0F));
   }

   @Test void headIsTheVisualFrontAndTheTubeHasNoGap() {
      WorldCutShape g = shape(90F, 64);
      int[] surface = rough(63);
      float close = WorldCutErasureProbe.closeAt(Double.POSITIVE_INFINITY);
      for (int tick = 1; tick <= ReinhardRules.WORLD_CUT_WAVE_TICKS; tick++) {
         ErasureStroke s = WorldCutErasureProbe.stroke(g, surface, BLAST + tick, close, 0F);
         double front = Math.min(ReinhardRules.CUT_LENGTH, WorldCutErasureProbe.front(BLAST + tick));
         assertEquals(front / ReinhardRules.CUT_LENGTH, s.headS() / s.length(), 1E-4, "head on the front at " + tick);
         for (float d = 0.5F; d < s.headS() - 0.5F; d += 0.5F)
            assertTrue(s.radiusAt(d) > 0F, "no gap behind the front at " + d + " of " + s.headS());
      }
      assertEquals(1F, WorldCutErasureProbe.stroke(g, surface, BLAST + ReinhardRules.WORLD_CUT_WAVE_TICKS, close, 0F).headS()
         / WorldCutErasureProbe.stroke(g, surface, BLAST + ReinhardRules.WORLD_CUT_WAVE_TICKS, close, 0F).length(), 1E-5,
         "the whole cut when the front ends");
   }

   @Test void hugeAndThickAlongTheWholeCut() {
      assertTrue(ErasureStroke.widthAt(WorldCutErasureProbe.SCALE) > 9F, "about 9.5 blocks thick");
      assertTrue(WorldCutErasureProbe.RADIUS * 1.1 * 1.04 < ReinhardRules.CUT_WIDTH / 2.0, "swell and ripple stay inside the cut");
      WorldCutShape g = shape(211F, 70);
      int[] surface = rough(69);
      ErasureStroke s = WorldCutErasureProbe.stroke(g, surface, BLAST + 60, WorldCutErasureProbe.closeAt(Double.POSITIVE_INFINITY), 0F);
      float full = s.width() / 2;
      for (float d = WorldCutErasureProbe.TAIL_BLOCKS; d <= s.length(); d += 1F)
         assertTrue(s.radiusAt(d) > 0.75F * full, "near full width at " + d + ": " + s.radiusAt(d) + " of " + full);
      assertTrue(s.radiusAt(2F) < 0.5F * full, "the tail still opens from a point");
   }

   @Test void liesAlongTheCutOverTheGroundAndUnderItsCeiling() {
      for (float yaw : YAWS) {
         WorldCutShape g = shape(yaw, 64);
         int[] surface = rough(63);
         ErasurePath path = WorldCutErasureProbe.path(g, surface);
         Vec3 start = g.axis(0), dir = g.dirXZ();
         for (float s = 0; s <= path.length(); s += 0.5F) {
            Vec3 p = path.pointAt(s);
            double along = (p.x - start.x) * dir.x + (p.z - start.z) * dir.z;
            double lateral = Math.abs((p.x - start.x) * dir.z - (p.z - start.z) * dir.x);
            assertTrue(lateral < 1E-6, "on the cut's axis, yaw " + yaw);
            assertTrue(along > -0.5 && along < ReinhardRules.CUT_LENGTH + 0.5, "inside the cut, yaw " + yaw + ": " + along);
            int slice = (int)Math.max(0, Math.min(ReinhardRules.CUT_LENGTH - 1, Math.floor(along)));
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int j = Math.max(0, slice - 6); j <= Math.min(ReinhardRules.CUT_LENGTH - 1, slice + 6); j++) {
               lo = Math.min(lo, surface[j]);
               hi = Math.max(hi, surface[j]);
            }
            assertTrue(p.y - WorldCutErasureProbe.RADIUS * 1.1 >= lo + 1, "over the ground near " + along);
            assertTrue(p.y + WorldCutErasureProbe.RADIUS * 1.1 <= hi + ReinhardRules.CUT_ABOVE + 1, "inside the band the cut clears near " + along);
         }
      }
   }

   @Test void standingPlayersSeeItOverheadNotEndOn() {
      // A tube holding the caster's eyes in its cross-section is one round lens from the start of the cut.
      WorldCutShape g = shape(0F, 64);
      Vec3[] axis = WorldCutErasureProbe.axis(g, flat(63));
      double eye = 64 + WorldCutErasureProbe.EYE;
      for (Vec3 p : axis) assertTrue(p.y - WorldCutErasureProbe.RADIUS > eye + 0.4, "over standing eyes: " + p.y);
      assertEquals(63 + WorldCutErasureProbe.GROUND + WorldCutErasureProbe.CLEARANCE + WorldCutErasureProbe.RADIUS, axis[100].y, 1E-9);
      // Cast from a ledge 15 blocks over the ground ahead: kept over the caster's eyes near the caster, then down.
      WorldCutShape ledge = shape(0F, 79);
      Vec3[] high = WorldCutErasureProbe.axis(ledge, flat(63));
      for (int i = 0; i <= WorldCutErasureProbe.EYE_HOLD; i++)
         assertTrue(high[i].y - WorldCutErasureProbe.RADIUS > 79 + WorldCutErasureProbe.EYE + 0.4, "over the caster at " + i);
      for (int i = 1; i < high.length; i++) assertTrue(Math.abs(high[i].y - high[i - 1].y) < 1.2, "eases down at " + i);
      for (int i = (int)WorldCutErasureProbe.CASTER_SPAN; i < high.length; i++) assertEquals(axis[i].y, high[i].y, 1E-9, "on the ground further on");
   }

   @Test void axisFollowsTheSurfaceSmoothly() {
      int[] surface = new int[200];
      for (int i = 100; i < 200; i++) surface[i] = 10;
      var axis = WorldCutErasureProbe.axis(surface);
      assertEquals(201, axis.length);
      for (int i = 1; i < axis.length; i++) assertTrue(Math.abs(axis[i].y - axis[i - 1].y) <= 10.0 / 11 + 1E-9);
      assertEquals(2.0, axis[0].horizontalDistance() - 0, 1E-9);
   }

   @Test void holdsWhileTheServerCarvesAndShutsOnDoneBeforeTheSceneFades() {
      assertEquals(WorldCutErasureProbe.HOLD, WorldCutErasureProbe.closeAt(Double.POSITIVE_INFINITY), 1E-6);
      assertEquals(5F, WorldCutErasureProbe.closeAt(BLAST + 100), 1E-5, "DONE five seconds after the impact");
      assertEquals(WorldCutErasureProbe.EARLIEST, WorldCutErasureProbe.closeAt(BLAST + 10), 1E-6, "never before the front is through");
      assertEquals(WorldCutErasureProbe.HOLD, WorldCutErasureProbe.closeAt(BLAST + 400), 1E-6, "never after HOLD");
      WorldCutShape g = shape(145F, 64);
      int[] surface = flat(63);
      for (int done = (int)BLAST + ReinhardRules.WORLD_CUT_WAVE_TICKS; done <= BLAST + 300; done += 7) {
         float close = WorldCutErasureProbe.closeAt(done);
         // WorldCutScene fades for 70 ticks after DONE, then drops the scene: the stroke is shut by then.
         assertTrue(BLAST + 20 * (close + WorldCutErasureProbe.CLOSE) <= done + 70 + 1E-6, "shut before the scene goes, DONE " + done);
         if (done <= BLAST + 20 * WorldCutErasureProbe.HOLD && done >= BLAST + 20 * WorldCutErasureProbe.EARLIEST)
            assertEquals(0F, WorldCutErasureProbe.stroke(g, surface, done, close, 0F).decay(), 1E-5, "DONE arriving never makes it jump");
         ErasureStroke shut = WorldCutErasureProbe.stroke(g, surface, BLAST + 20 * (close + WorldCutErasureProbe.CLOSE), close, 0F);
         assertEquals(1F, shut.decay(), 1E-5);
      }
      ErasureStroke open = WorldCutErasureProbe.stroke(g, surface, BLAST + 80, WorldCutErasureProbe.closeAt(Double.POSITIVE_INFINITY), 0F);
      assertEquals(0F, open.decay(), 1E-6, "open while the server works");
      assertEquals(open.length(), open.headS(), 1E-3);
   }

   @Test void pulsesSwellItLikeTheRestOfTheScene() {
      WorldCutShape g = shape(300F, 64);
      int[] surface = flat(63);
      float close = WorldCutErasureProbe.closeAt(Double.POSITIVE_INFINITY);
      ErasureStroke calm = WorldCutErasureProbe.stroke(g, surface, BLAST + 50, close, 0F);
      ErasureStroke beat = WorldCutErasureProbe.stroke(g, surface, BLAST + 50, close, 1F);
      assertEquals(calm.width() * 1.1F, beat.width(), 1E-4);
      assertEquals(ErasureStyle.CYAN, calm.style());
      assertNotEquals(ErasureStyle.CYAN, beat.style(), "it brightens on a beat");
   }

   @Test void collectReadsTheSceneBeatAndDoneClock() throws Exception {
      String source = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/client/reinhard/cut/WorldCutErasure.java"));
      String collect = source.substring(source.indexOf("public void collect("), source.indexOf("/** The stroke at elapsed tick t"));
      assertTrue(collect.contains("closeAt(scene.acceptedUntil() + 1)"), "DONE from the scene's own clock");
      assertTrue(collect.contains("WorldCutPulse.envelope(scene.pulsePhase(t))"), "the admitted beat of the scene");
      assertEquals(1, collect.split("out.add\\(", -1).length - 1, "one stroke per cast");
   }
}
