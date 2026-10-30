package dev.baranhan.viltrumitecore.client.ironman.hulkbuster;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationParser;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkMove;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkStrikes;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterMotion;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Spec item 1 (2026-10-28): the Mark 48 first-person arms must not cover the screen. A CPU replica of the
 * hand pass (HulkbusterFirstPersonFrame: the same placement, rest pose and clips as HulkbusterRenderer)
 * projected with the hand projection (fixed 70 degrees, near 0.05, 16:9). Not a game screenshot.
 * <ul>
 * <li>Rest (idle, motion clips): nothing in the centre band (|x| < 25% of the width from the centre),
 * geometry at the edges only in the bottom ~30% (NDC y <= -0.4).</li>
 * <li>Moves: the centre band only during at most 4 ticks per strike (the contact frames).</li>
 * <li>Nothing nearer than the near plane (no cut cubes).</li>
 * </ul>
 */
class HulkbusterFirstPersonCoverageTest {
   /** Bottom 30% of the screen: NDC -1 .. -0.4. */
   static final float REST_TOP = -0.4F;
   static Map<String, Animation> clips;
   static BakedGeoModel arms;

   @BeforeAll
   static void load() throws IOException {
      clips = AnimationParser.parse(HulkbusterClipsTest.json("animations/ironman/hulkbuster/mark48.animation.json"));
      arms = BakedGeoModel.parse(HulkbusterClipsTest.json("geo/ironman/hulkbuster/mark48_fp.geo.json"));
   }

   static HulkbusterFirstPersonFrame.Metrics frame(HulkbusterClips.Input in, HulkMove move, MovePhase phase, int step, double ticks) {
      HulkbusterRenderer.applyClips(arms, HulkbusterClips.select(in), true, clips::get);
      List<Vector3f> points = new ArrayList<>();
      for (boolean right : new boolean[]{true, false}) {
         float push = move == null ? 0.0F : HulkbusterClips.firstPersonPush(move, phase, step, ticks, right);
         points.addAll(HulkbusterFirstPersonFrame.arm(arms, right, push));
      }

      return HulkbusterFirstPersonFrame.metrics(points);
   }

   static HulkbusterClips.Input idle(double seconds, HulkbusterMotion.State state) {
      return new HulkbusterClips.Input(null, MovePhase.INTRO, 0, 0.0, state, seconds * 20.0, 0, seconds, 0.0F, 0.0, false);
   }

   @Test
   void restLeavesTheCentreFreeAndStaysLow() {
      StringBuilder report = new StringBuilder();
      for (double t = 0.0; t < 5.0; t += 0.25) {
         HulkbusterFirstPersonFrame.Metrics m = frame(idle(t, HulkbusterMotion.State.NONE), null, MovePhase.INTRO, 0, 0.0);
         report.append(String.format("idle t=%.2f edgeTop=%.3f centreTop=%.3f nearest=%.3f%n", t, m.edgeTop(), m.centreTop(), m.nearest()));
         assertTrue(m.centreTop() <= -1.0F + 1.0E-4F, "idle t=" + t + ": centre band covered up to " + m.centreTop() + "\n" + report);
         assertTrue(m.edgeTop() <= REST_TOP, "idle t=" + t + ": edges reach " + m.edgeTop() + "\n" + report);
         assertTrue(m.nearest() >= HulkbusterFirstPersonFrame.NEAR, "idle t=" + t + ": nearer than the near plane " + m.nearest());
      }

      System.out.println(report);
   }

   @Test
   void strikesEnterTheCentreOnlyAroundTheContact() {
      StringBuilder report = new StringBuilder();
      boolean ok = true;
      for (HulkMove move : HulkMove.values()) {
         for (int step = 0; step < move.maxSteps(); step++) {
            if (HulkStrikes.of(move, step) == null || HulkStrikes.of(move, step).step() != step) {
               continue;
            }

            int length = move.length(step);
            if (length > 200) {
               continue;
            }

            int centreTicks = 0;
            float nearest = Float.MAX_VALUE;
            for (int tick = 0; tick <= length; tick++) {
               MovePhase phase = StagedTimeline.phaseAt(move, tick, step);
               HulkbusterClips.Input in = new HulkbusterClips.Input(move, phase, step, tick, HulkbusterMotion.State.NONE, 0.0, 0, 0.0, 0.0F, 0.0, false);
               HulkbusterFirstPersonFrame.Metrics m = frame(in, move, phase, step, tick);
               nearest = Math.min(nearest, m.nearest());
               if (m.centreTop() > -1.0F + 1.0E-4F) {
                  centreTicks++;
               }
            }

            report.append(String.format("%s step %d: centre ticks %d / %d, nearest %.3f%n", move, step, centreTicks, length, nearest));
            ok &= centreTicks <= 4 && nearest >= HulkbusterFirstPersonFrame.NEAR;
         }
      }

      System.out.println(report);
      assertTrue(ok, report.toString());
   }
}
