package dev.baranhan.viltrumitecore.client.reinhard.cut;

import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.client.render.erasure.ErasurePath;
import dev.baranhan.viltrumitecore.client.render.erasure.ErasureStroke;
import dev.baranhan.viltrumitecore.client.render.erasure.ErasureStyle;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.DiagonalCutGeometry;
import dev.baranhan.viltrumitecore.network.packet.DiagonalCutS2CPacket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** REINHARD OVERHAUL items 10 and 14: The Hand wound and the blade wave of the diagonal World Cut. */
class DiagonalCutErasureTest {
   private static final float[] YAWS = {0F, 37F, 90F, 145F, 200F, 290F};
   private static final float[] PITCHES = {-35F, -12F, 0F, 20F, 35F};

   /** Seed 3 puts the narrowest width of the cut (28 * 0.76) right on the wound. */
   private static final long[] SEEDS = {12345L, 3L, 1023L, 0L};

   private static DiagonalCutGeometry shape(float pitch, float yaw) {
      return shape(pitch, yaw, 12345L);
   }

   private static DiagonalCutGeometry shape(float pitch, float yaw, long seed) {
      return new DiagonalCutGeometry(new Vec3(10.5, 64, -5.5), Vec3.directionFromRotation(pitch, yaw), seed);
   }

   private static DiagonalCutErasure.Wound wound(DiagonalCutGeometry shape, long start) {
      return new DiagonalCutErasure.Wound(null, start, ErasurePath.smooth(List.of(DiagonalCutErasure.path(shape))), 0.3F);
   }

   @AfterEach void reset() {
      DiagonalCutErasure.INSTANCE.clear();
   }

   @Test void woundLiesInTheDiagonalPlaneFromTheUpperRightToTheLowerLeft() {
      for (long seed : SEEDS) for (float yaw : YAWS) for (float pitch : PITCHES) {
         DiagonalCutGeometry g = shape(pitch, yaw, seed);
         String at = "yaw " + yaw + " pitch " + pitch + " seed " + seed;
         ErasurePath path = wound(g, 0L).path();
         for (int i = 0; i <= 80; i++) {
            Vec3 d = path.pointAt(path.length() * i / 80).subtract(g.origin());
            double along = d.dot(g.forward()), across = d.dot(g.span());
            assertTrue(Math.abs(d.dot(g.normal())) <= 1E-6, "in the cut plane, " + at);
            assertTrue(along > DiagonalCutErasure.TIP_ALONG - 0.5 && along < DiagonalCutErasure.TIP_ALONG + DiagonalCutErasure.BOW + 0.5,
               "in the first fifth of the cut, past its opening taper, " + at + ": " + along);
            assertTrue(Math.abs(across) <= 0.95 * g.width(along), "inside the cut, " + at);
         }
         Vec3 tail = path.pointAt(0).subtract(g.origin()), head = path.pointAt(path.length()).subtract(g.origin());
         assertTrue(tail.dot(g.span()) > 0.9 * g.width(tail.dot(g.forward())), "starts at the upper right edge of the cut, " + at);
         assertTrue(head.dot(g.span()) < -0.9 * g.width(head.dot(g.forward())), "ends at the lower left edge, " + at);
         assertTrue(tail.subtract(head).dot(g.right()) > 25, "starts on the caster's right, " + at);
         assertTrue(path.length() > 60 && path.length() < 95, "one colossal arc, " + at + ": " + path.length());
      }
   }

   /** Playtest item 4: the wound covers the cut's whole transverse sweep, not a third of it. */
   @Test void woundSweepsTheWholeWidthOfTheCut() {
      for (long seed : SEEDS) for (float pitch : PITCHES) {
         DiagonalCutGeometry g = shape(pitch, 37F, seed);
         ErasurePath path = wound(g, 0L).path();
         // Every share of the cut's width from edge to edge is crossed by the one stroke.
         for (double share = -0.9; share <= 0.9; share += 0.05) {
            boolean crossed = false;
            for (int i = 0; i < 400 && !crossed; i++) {
               Vec3 a = path.pointAt(path.length() * i / 400).subtract(g.origin());
               Vec3 b = path.pointAt(path.length() * (i + 1) / 400).subtract(g.origin());
               double sa = a.dot(g.span()) / g.width(a.dot(g.forward())), sb = b.dot(g.span()) / g.width(b.dot(g.forward()));
               crossed = (sa - share) * (sb - share) <= 0;
            }
            assertTrue(crossed, "share " + share + " of the width, seed " + seed + " pitch " + pitch);
         }
         // From the caster's eyes the plane is seen edge on: the wound runs past the screen's edges (about 45 degrees
         // from the centre along the diagonal at the default 70 degree FOV); PR #49's swipe reached 32 degrees.
         Vec3 eye = g.origin().add(0, 0.62, 0);
         double tip = Math.toDegrees(Math.acos(path.pointAt(0).subtract(eye).normalize().dot(g.forward())));
         double end = Math.toDegrees(Math.acos(path.pointAt(path.length()).subtract(eye).normalize().dot(g.forward())));
         assertTrue(tip > 45 && end > 45, "edge to edge on screen, seed " + seed + " pitch " + pitch + ": " + tip + ", " + end);
      }
   }

   @Test void woundKeepsTheOriginalProportionsAtTheSizeOfTheCut() {
      assertEquals(ErasureStroke.widthAt(1F) * DiagonalCutErasure.SCALE, ErasureStroke.widthAt(DiagonalCutErasure.SCALE), 1E-5);
      assertTrue(ErasureStroke.widthAt(DiagonalCutErasure.SCALE) > 8.5F, "a tube more than 8.5 blocks thick");
      float length = wound(shape(0F, 0F), 0L).path().length();
      float ratio = length / ErasureStroke.widthAt(DiagonalCutErasure.SCALE);
      assertTrue(ratio > 7 && ratio < 10, "the original length to width ratio (about 8.5): " + ratio);
      double along = DiagonalCutErasure.along(shape(0F, 0F));
      assertEquals(DiagonalCutErasure.TIP_ALONG + DiagonalCutErasure.BOW, along, 1E-6, "the wave sets off from the middle of the arc");
      ErasureStroke open = DiagonalCutErasure.stroke(wound(shape(0F, 0F), 0L), 3F);
      assertSame(DiagonalCutErasure.PROFILE, open.profile());
      for (float s = 0.2F * open.length(); s <= open.length(); s += 1F)
         assertTrue(open.radiusAt(s) > 0.65F * open.width() / 2, "thick over the sweep at " + s);
   }

   @Test void woundOpensOnTheImpactAndIsGoneTenSecondsLater() {
      assertEquals(10F, DiagonalCutErasure.HOLD + DiagonalCutErasure.CLOSE, 1E-6);
      long start = 1000L;
      assertEquals(0F, DiagonalCutErasure.sinceImpact(start, start + ReinhardRules.WORLD_CUT_BLAST, 0F), 1E-6);
      assertEquals(0.025F, DiagonalCutErasure.sinceImpact(start, start + ReinhardRules.WORLD_CUT_BLAST, 0.5F), 1E-6);
      assertEquals(10F, DiagonalCutErasure.sinceImpact(start, start + ReinhardRules.WORLD_CUT_BLAST + 200, 0F), 1E-6);
      assertTrue(DiagonalCutErasure.sinceImpact(start, start + ReinhardRules.WORLD_CUT_SWING, 0F) < 0, "not on the swing");
      DiagonalCutErasure.Wound w = wound(shape(10F, 60F), start);
      assertNull(DiagonalCutErasure.stroke(w, -0.01F), "nothing before the impact");
      ErasureStroke cut = DiagonalCutErasure.stroke(w, 0.1F);
      assertNotNull(cut);
      assertTrue(cut.headS() > 0 && cut.headS() < cut.length(), "the head is on its way");
      for (float since = DiagonalCutErasure.REVEAL; since <= DiagonalCutErasure.HOLD; since += 0.5F) {
         ErasureStroke open = DiagonalCutErasure.stroke(w, since);
         assertEquals(open.length(), open.headS(), 1E-3, "fully cut at " + since);
         assertEquals(0F, open.decay(), 1E-6, "open at " + since);
         assertFalse(open.finished());
      }
      assertEquals(0.5F, DiagonalCutErasure.stroke(w, 8.75F).decay(), 1E-5, "zips shut over the last 2.5 s");
      assertEquals(1F, DiagonalCutErasure.stroke(w, 10F).decay(), 1E-6);
      assertNull(DiagonalCutErasure.stroke(w, 10.05F), "gone ten seconds after the impact");
   }

   @Test void woundPulsesAndShimmersOnlyWhileItHolds() {
      for (float seed : new float[]{0F, 0.3F, 0.77F}) {
         assertEquals(0F, DiagonalCutErasure.swell(0.1F, seed), 1E-6, "no pulse during the cut");
         assertEquals(ErasureStyle.CYAN, DiagonalCutErasure.style(0.1F, seed), "the original colours during the cut");
         assertEquals(0F, DiagonalCutErasure.swell(DiagonalCutErasure.HOLD + DiagonalCutErasure.CLOSE * 0.6F, seed), 1E-6);
         float min = 1F, max = 0F;
         java.util.Set<ErasureStyle> styles = new java.util.HashSet<>();
         for (float since = 1.2F; since < 7F; since += 0.05F) {
            float swell = DiagonalCutErasure.swell(since, seed);
            assertTrue(swell >= 0F && swell <= 1F);
            min = Math.min(min, swell);
            max = Math.max(max, swell);
            styles.add(DiagonalCutErasure.style(since, seed));
         }
         assertTrue(max > 0.9F && min < 0.05F, "slow soft pulses, seed " + seed);
         assertTrue(styles.size() > 50, "the colours drift while it holds");
      }
   }

   @Test void woundsAreBoundedAndDroppedWithTheirLevelOrOnceShut() {
      DiagonalCutErasure e = DiagonalCutErasure.INSTANCE;
      Object level = new Object(), other = new Object();
      DiagonalCutGeometry g = shape(0F, 0F);
      UUID cast = UUID.randomUUID(), caster = UUID.randomUUID();
      for (int phase = 0; phase <= 3; phase++) e.track(new DiagonalCutS2CPacket(cast, caster, g, 1000L, phase), level);
      assertEquals(1, e.size(), "one wound per cast, whatever packet comes first");
      e.prune(level, 1000L + ReinhardRules.WORLD_CUT_BLAST + 100);
      assertEquals(1, e.size());
      e.prune(level, 1000L + ReinhardRules.WORLD_CUT_BLAST + 201);
      assertEquals(0, e.size(), "dropped once shut");
      for (int i = 0; i < 40; i++) e.track(new DiagonalCutS2CPacket(UUID.randomUUID(), caster, g, 1000L, 0), level);
      assertEquals(DiagonalCutErasure.MAX, e.size());
      e.prune(other, 1000L);
      assertEquals(0, e.size(), "dropped on a level change");
      e.track(new DiagonalCutS2CPacket(cast, caster, g, 1000L, 1), level);
      e.clear();
      assertEquals(0, e.size());
   }

   @Test void bladeWaveFliesOutOfTheWoundWithTheFrontAndDiesAtTheEnd() {
      DiagonalCutGeometry g = shape(15F, 120F);
      double from = DiagonalCutErasure.along(g);
      assertEquals(0, DiagonalCutScene.waveAlong(ReinhardRules.WORLD_CUT_BLAST), 1E-9);
      assertEquals(DiagonalCutGeometry.LENGTH, DiagonalCutScene.waveAlong(DiagonalCutScene.waveEnd()), 1E-9);
      assertEquals(0, DiagonalCutScene.waveAlpha(ReinhardRules.WORLD_CUT_BLAST + 2, 0, from), "not before it reaches the wound");
      double leave = ReinhardRules.WORLD_CUT_BLAST + from * ReinhardRules.WORLD_CUT_WAVE_TICKS / ReinhardRules.CUT_LENGTH;
      assertTrue(DiagonalCutScene.waveAlpha(leave + 0.3, 0, from) > 0, "it sets off from the wound");
      assertTrue(DiagonalCutScene.waveAlpha(leave + 0.3, 0, from) < DiagonalCutScene.waveAlpha(leave + 3, 0, from), "rising");
      double mid = ReinhardRules.WORLD_CUT_BLAST + 20;
      for (int trail = 1; trail < DiagonalCutScene.WAVE_ECHOES; trail++)
         assertTrue(DiagonalCutScene.waveAlpha(mid, trail, from) < DiagonalCutScene.waveAlpha(mid, trail - 1, from), "echoes fade");
      assertEquals(0, DiagonalCutScene.waveAlpha(DiagonalCutScene.waveEnd() + DiagonalCutScene.WAVE_FADE_TICKS, 0, from), 1E-9);
      assertEquals(g.point(DiagonalCutGeometry.LENGTH, 0, 0), DiagonalCutScene.waveCentre(g, DiagonalCutScene.waveEnd() + 3, 0), "rests at the end");
      for (double t = leave; t <= DiagonalCutScene.waveEnd(); t += 1.5) {
         double along = DiagonalCutScene.waveAlong(t);
         Vec3 centre = DiagonalCutScene.waveCentre(g, t, 0);
         double radius = DiagonalCutScene.waveRadius(g, along);
         assertTrue(radius <= g.width(Math.min(DiagonalCutGeometry.LENGTH - 1, along)), "within the cut");
         for (int i = 0; i < DiagonalCutScene.WAVE_POINTS; i++) {
            Vec3 d = CrescentWave.point(centre, g.span(), g.forward(), radius, radius * .22, i, DiagonalCutScene.WAVE_POINTS).subtract(g.origin());
            assertEquals(0, d.dot(g.normal()), 1E-6, "the wave flies in the cut plane");
            assertTrue(d.dot(g.forward()) <= along + 1E-6, "the middle leads, the ends trail");
         }
      }
   }

   @Test void crescentKeepsTheFormulaOfTheRetiredCutRainWave() {
      Vec3 centre = new Vec3(3, 70, -2), span = new Vec3(0, 0.6, 0.8), dir = new Vec3(1, 0, 0);
      double radius = 2.4;
      for (int i = 0; i < 20; i++) {
         double a = -1.2 + i * 2.4 / 19;
         Vec3 p = centre.add(span.scale(Math.sin(a) * radius)).add(dir.scale((Math.cos(a) - 1) * 1.2));
         assertEquals(p, CrescentWave.point(centre, span, dir, radius, 1.2, i, 20), "point " + i);
      }
      assertEquals(centre, CrescentWave.point(centre, span, dir, radius, 1.2, 10, 21), "the middle leads");
   }

   @Test void cutRainIsRetiredAndTheDiagonalCuesAreUntouched() throws Exception {
      Path root = Path.of("src/main/java/dev/baranhan/viltrumitecore/client/reinhard/cut");
      assertFalse(Files.exists(root.resolve("CutRainWaves.java")), "item 19: the Saw Sphere replaced the Cut Rain waves");
      String diagonal = Files.readString(root.resolve("DiagonalCutScene.java"));
      String render = diagonal.substring(diagonal.indexOf("public static void render("));
      assertTrue(render.contains("if(rift)"), "the old rift is only the fallback");
      assertTrue(diagonal.contains("boolean rift=!ErasureRenderer.available();"));
      assertTrue(diagonal.contains("DiagonalCutErasure.INSTANCE.track(p,"));
      assertEquals(2, diagonal.split("DiagonalCutErasure.INSTANCE.clear\\(\\)", -1).length - 1, "cleared on unload and without a level");
      assertFalse(diagonal.contains("STRIKE_VOICE"), "the Theresia strike stays on the ordinary World Cut");
      assertFalse(diagonal.contains("WorldCutAudio"), "the diagonal cut keeps its own audio");
      String client = Files.readString(root.resolveSibling("ReinhardClient.java"));
      assertTrue(client.contains("ErasureRenderer.register(dev.baranhan.viltrumitecore.client.reinhard.cut.DiagonalCutErasure.INSTANCE)"));
      assertTrue(client.contains("ErasureRenderer.register(dev.baranhan.viltrumitecore.client.reinhard.cut.WorldCutErasure.INSTANCE)"), "the ordinary World Cut keeps its The Hand chain");
   }
}
