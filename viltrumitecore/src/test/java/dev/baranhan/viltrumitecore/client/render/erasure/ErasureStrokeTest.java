package dev.baranhan.viltrumitecore.client.render.erasure;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ErasureStrokeTest {
   private static ErasurePath line(double length) {
      return ErasurePath.smooth(List.of(Vec3.ZERO, new Vec3(length / 2, 0, 0), new Vec3(length, 0, 0)));
   }

   @Test void vertexColourRadiusStaysNormalisedAtAnyScale() {
      for (float scale : new float[]{1F, 7.5F, 40F}) {
         ErasureStroke stroke = new ErasureStroke(line(3 * scale), 0F, (float)(3 * scale), scale, 0.3F, ErasureStyle.CYAN).update(0.2F);
         for (boolean shell : new boolean[]{false, true})
            for (ErasureStroke.Ring ring : stroke.rings(shell)) {
               assertTrue(ring.colourRadius() >= 0 && ring.colourRadius() <= 1, "scale " + scale + " shell " + shell);
            }
      }
   }

   @Test void scaleOneKeepsTheOriginalSizes() {
      ErasureStroke stroke = new ErasureStroke(line(3), 0.3F, ErasureStyle.CYAN).update(0.05F);
      assertEquals(0.34F, stroke.width, 1E-6);
      assertEquals(0.17F * (0.4F + 0.6F * stroke.aura), stroke.auraRadius(), 1E-6);
   }

   @Test void originalTimeline() {
      ErasureStroke stroke = new ErasureStroke(line(3), 0.3F, ErasureStyle.CYAN);
      assertTrue(stroke.update(-0.1F).auraVisible());
      assertFalse(stroke.tubeVisible());
      assertEquals(stroke.length(), stroke.update(0.1F).headS(), 1E-4);
      assertTrue(stroke.update(0.3F).tubeVisible());
      assertEquals(1F, stroke.update(0.75F).decay(), 1E-6);
      assertTrue(stroke.update(0.76F).finished());
   }

   @Test void continuationHasNoPreAura() {
      ErasureStroke stroke = new ErasureStroke(line(3), 0F, 3F, 1F, 0F, ErasureStyle.CYAN, false).update(-0.1F);
      assertFalse(stroke.auraVisible());
   }

   @Test void offsetWindowSlidesAlongThePath() {
      ErasurePath path = line(30);
      ErasureStroke stroke = new ErasureStroke(path, 10F, 5F, 1F, 0F, ErasureStyle.CYAN).update(0.2F);
      assertEquals(10.0, stroke.pointAt(0).x, 1E-3);
      assertTrue(stroke.bounds().maxX < 16.5);
   }

   // REINHARD OVERHAUL item 10: a stroke held open for seconds (the diagonal World Cut wound).
   @Test void heldStrokeCutsOpensHoldsThenZipsShut() {
      ErasureStroke stroke = new ErasureStroke(line(30), 0F, 30F, 10F, 0.3F, ErasureStyle.CYAN, false);
      stroke.updateHeld(0.1F, 12F, 0.25F, 7.5F, 2.5F, 0F);
      assertEquals(0.04F, stroke.time(), 1E-6, "the original cut is stretched over the reveal");
      assertEquals(1F, stroke.flash, 1E-6);
      assertEquals(0F, stroke.decay(), 1E-6);
      assertFalse(stroke.finished());
      stroke.updateHeld(5F, 30F, 0.25F, 7.5F, 2.5F, 0F);
      assertEquals(4.85F, stroke.time(), 1E-4, "the original clock runs on while it holds");
      assertEquals(0F, stroke.decay(), 1E-6, "no closing while it holds");
      assertTrue(stroke.tubeVisible());
      assertFalse(stroke.auraVisible(), "the head flame dies after the cut, as in the original");
      assertEquals(0.5F, stroke.updateHeld(8.75F, 30F, 0.25F, 7.5F, 2.5F, 0F).decay(), 1E-5);
      assertEquals(1F, stroke.updateHeld(10F, 30F, 0.25F, 7.5F, 2.5F, 0F).decay(), 1E-6);
      assertFalse(stroke.finished());
      assertTrue(stroke.updateHeld(10.01F, 30F, 0.25F, 7.5F, 2.5F, 0F).finished());
   }

   @Test void heldStrokeSwellWidensItAndUpdateRestoresTheOriginalTimeline() {
      ErasureStroke stroke = new ErasureStroke(line(30), 0F, 30F, 10F, 0.3F, ErasureStyle.CYAN, false);
      float calm = stroke.updateHeld(3F, 30F, 0.25F, 7.5F, 2.5F, 0F).width();
      assertEquals(calm * 1.1F, stroke.updateHeld(3F, 30F, 0.25F, 7.5F, 2.5F, 1F).width(), 1E-4);
      assertEquals(calm * 1.05F, stroke.updateHeld(3F, 30F, 0.25F, 7.5F, 2.5F, 0.5F).width(), 1E-4);
      assertFalse(stroke.update(0.5F).finished(), "update() takes the original END back");
      assertTrue(stroke.update(0.76F).finished());
      assertThrows(IllegalArgumentException.class, () -> stroke.updateHeld(1F, 1F, 0F, 7.5F, 2.5F, 0F));
      assertThrows(IllegalArgumentException.class, () -> stroke.updateHeld(1F, 1F, 0.25F, 0.1F, 2.5F, 0F));
      assertThrows(IllegalArgumentException.class, () -> stroke.updateHeld(1F, 1F, 0.25F, 7.5F, 0F, 0F));
   }

   @Test void shimmerAtRestIsTheOriginalStyle() {
      assertEquals(ErasureStyle.CYAN, ErasureStyle.shimmer(0F, 0F));
      ErasureStyle bright = ErasureStyle.shimmer(0.2F, 0.3F);
      assertNotEquals(ErasureStyle.CYAN, bright);
      assertEquals(ErasureStyle.CYAN.edge(), bright.edge(), "the dark outline stays");
      assertEquals(0xFFFFFF, ErasureStyle.CYAN.head().lift(1F).body());
      assertEquals(0x000000, ErasureStyle.CYAN.head().lift(-1F).body());
      assertSame(ErasureStyle.CYAN.head(), ErasureStyle.CYAN.head().lift(0F));
   }

   @Test void theTestSwipeHandsOutACopy() {
      Vec3[] copy = ErasureTestSwipe.originalSwipe();
      assertArrayEquals(ErasureTestSwipe.SWIPE, copy);
      copy[0] = Vec3.ZERO;
      assertNotEquals(Vec3.ZERO, ErasureTestSwipe.SWIPE[0], "/erasuretest keeps its own swipe");
   }

   // REINHARD OVERHAUL 3b: width profiles and ring spacing for strokes tens of blocks long (the World Cut wounds).
   @Test void originalProfileIsTheOriginalFormula() {
      for (float seed : new float[]{0F, 0.3F, 0.77F}) for (int i = 0; i <= 200; i++) {
         float u = i / 200F;
         float tail = Mth.sin(Mth.clamp(u / 0.38F, 0F, 1F) * Mth.HALF_PI);
         float grow = 0.62F + (1F - 0.62F) * ErasureStroke.smoothstep(0.2F, 0.9F, u);
         float expected = 0.5F * 0.34F * tail * grow * (1F + 0.04F * Mth.sin(u * 7.5F + seed * Mth.TWO_PI));
         assertEquals(expected, ErasureStroke.tubeHalfWidth(u, 0.34F, seed), 1E-7F, "u " + u);
      }
      assertSame(ErasureStroke.Profile.ORIGINAL, new ErasureStroke(line(3), 0.3F, ErasureStyle.CYAN).profile());
      assertSame(ErasureStroke.Profile.ORIGINAL, new ErasureStroke(line(3), 0F, 3F, 1F, 0F, ErasureStyle.CYAN, false).profile());
   }

   @Test void longProfileIsAtFullWidthAfterAFewBlocks() {
      ErasureStroke.Profile p = ErasureStroke.Profile.forLength(200F, 12F, 0.82F, 40F, 0.05F, 37F);
      assertEquals(0.06F, p.tail(), 1E-6);
      assertEquals(0F, p.halfWidth(0F, 10F, 0.3F), 1E-6, "the tail opens from a point");
      for (float blocks = 12; blocks <= 200; blocks += 1) assertTrue(p.halfWidth(blocks / 200, 10F, 0.3F) > 0.77F * 5F, "at " + blocks);
      for (float blocks = 40; blocks <= 200; blocks += 1) assertTrue(p.halfWidth(blocks / 200, 10F, 0.3F) >= 0.95F * 5F, "at " + blocks);
      assertEquals(1F, ErasureStroke.Profile.forLength(5F, 12F, 0.82F, 40F, 0.05F, 37F).tail(), 1E-6, "clamped to the path");
      assertThrows(IllegalArgumentException.class, () -> new ErasureStroke.Profile(0F, 0.5F, 0.1F, 0.2F, 0F, 0F));
      assertThrows(IllegalArgumentException.class, () -> new ErasureStroke.Profile(0.2F, 0.5F, 0.3F, 0.2F, 0F, 0F));
      assertThrows(IllegalArgumentException.class, () -> new ErasureStroke(line(3), 0F, 3F, 1F, 0F, ErasureStyle.CYAN, false, null));
   }

   @Test void longStrokesGetRingsByWidthWithinABound() {
      ErasureStroke original = new ErasureStroke(line(3), 0.3F, ErasureStyle.CYAN).update(0.3F);
      assertEquals(ErasureStroke.BODY_RINGS, original.bodyRings(), "the original keeps its 64 rings");
      ErasureStroke.Profile p = ErasureStroke.Profile.forLength(200F, 12F, 0.82F, 40F, 0.05F, 37F);
      ErasureStroke cut = new ErasureStroke(line(200), 0F, 200F, 28F, 0.3F, ErasureStyle.CYAN, false, p)
         .updateHeld(3F, 200F, 1.5F, 7.5F, 2.5F, 0F);
      int rings = cut.bodyRings();
      assertTrue(rings > ErasureStroke.BODY_RINGS && rings <= ErasureStroke.MAX_BODY_RINGS, "rings " + rings);
      assertTrue(cut.capS / rings <= 0.26F * cut.width(), "a ring every quarter width: " + cut.capS / rings);
      ErasureStroke huge = new ErasureStroke(line(5000), 0F, 5000F, 1F, 0.3F, ErasureStyle.CYAN, false, p)
         .updateHeld(3F, 5000F, 1.5F, 7.5F, 2.5F, 0F);
      assertEquals(ErasureStroke.MAX_BODY_RINGS, huge.bodyRings(), "bounded");
      assertEquals(rings + 1 + ErasureStroke.CAP_RINGS, cut.rings(false).size());
      assertEquals(rings + 1 + 2 * ErasureStroke.CAP_RINGS, cut.rings(true).size());
   }

   @Test void shellAndGlassShareOneCentreLinePerFrame() {
      ErasureStroke s = new ErasureStroke(line(30), 0F, 30F, 10F, 0.3F, ErasureStyle.CYAN, false)
         .updateHeld(1F, 30F, 0.25F, 7.5F, 2.5F, 0F);
      List<ErasureStroke.Ring> glass = s.rings(false), shell = s.rings(true);
      assertSame(glass.get(0).centre(), shell.get(ErasureStroke.CAP_RINGS).centre(), "built once, used by both passes");
      s.updateHeld(1.5F, 30F, 0.25F, 7.5F, 2.5F, 1F);
      assertNotEquals(glass.get(5).r(), s.rings(false).get(5).r(), "a new frame builds it again");
   }

   @Test void heldStrokeShowsItsPreAuraBeforeTheCut() {
      ErasureStroke s = new ErasureStroke(line(30), 0F, 30F, 10F, 0.3F, ErasureStyle.CYAN, true)
         .updateHeld(-0.15F, 0F, 1.5F, 7.5F, 2.5F, 0F);
      assertEquals(-0.15F, s.time(), 1E-6, "the original pre-aura clock");
      assertEquals(ErasureStroke.easeOut(0.5F), s.aura(), 1E-6);
      assertFalse(s.tubeVisible());
   }
}
