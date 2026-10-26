package dev.baranhan.viltrumitecore.client.reinhard.cut;

import dev.baranhan.viltrumitecore.client.render.erasure.ErasurePath;
import dev.baranhan.viltrumitecore.client.render.erasure.ErasureRenderer;
import dev.baranhan.viltrumitecore.client.render.erasure.ErasureStroke;
import dev.baranhan.viltrumitecore.client.render.erasure.ErasureStyle;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutShape;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/**
 * The Hand erasure on the ordinary World Cut: ONE continuous tube along the whole cut (REINHARD OVERHAUL 3b,
 * playtest item 3).
 *
 * <p>PR #49 drew a chain of twelve short original strokes, each cut, held and shut on the 0.75 s original timeline:
 * in play that read as separate bursts with seams, never as one wound. Now a single held stroke runs over the whole
 * 200 blocks of the cut:
 * <ul>
 *   <li>its head is the visual front ({@link #front}, the clock of damage and carving), so it forms behind the front
 *       in {@link #REVEAL} seconds with the original flame ball and flash on its head;</li>
 *   <li>it stays open while the server carves, swelling with every World Cut pulse (the same admitted beat that
 *       drives the hum, the shake and the dust), and zips shut from the caster to the end once the server reports
 *       DONE, never before {@link #EARLIEST} and never after {@link #HOLD} seconds after the impact;</li>
 *   <li>its width profile is sized in blocks ({@link ErasureStroke.Profile#forLength}), not in fractions of the
 *       three-block original, so it is at full width a dozen blocks from the caster on; it is about 9.5 blocks thick
 *       ({@link #SCALE}), more than half the 17-block cut;</li>
 *   <li>it floats {@link #CLEARANCE} blocks over the cut's ground line, above the eyes of a standing player, inside
 *       the band the cut clears ({@link ReinhardRules#CUT_ABOVE}). A tube resting on the ground holds the caster's
 *       eyes inside its cross-section: seen from the start of the cut it is one round lens, not a wound running to the
 *       horizon. From here the caster sees it run overhead to the vanishing point, and the trench below stays in
 *       view. Near the caster it is also kept over the caster's own eyes (a cast from a ledge).</li>
 * </ul>
 *
 * <p>Performance: one stroke per cast, its rings spaced by the tube width (about 90 body rings, see
 * {@link ErasureStroke#bodyRings}), three draws per frame (shell, glass, aura) where the chain needed up to three per
 * piece; its path is built once per cast.
 *
 * <p>Client only. It reads the fixed packet frame of {@link WorldCutScene}: no new network data, no server work.
 * A scene that is pruned or cleared takes its stroke with it; the stroke is shut before the scene's 70-tick fade
 * after DONE ends.
 */
public final class WorldCutErasure implements ErasureRenderer.Provider {
   public static final WorldCutErasure INSTANCE = new WorldCutErasure();
   /** About 9.5 blocks thick. */
   static final float SCALE = 28F;
   /** The scene's ground line over the surface top (WorldCutScene.point). */
   static final double GROUND = 1.04;
   /** Gap from the ground line to the bottom of the tube: over the eyes (1.62) of a standing player. */
   static final double CLEARANCE = 2.4;
   static final double RADIUS = ErasureStroke.widthAt(SCALE) * 0.5;
   /** Tube axis over the surface top. */
   static final double LIFT = GROUND + CLEARANCE + RADIUS;
   /** Over the caster's eyes by at least EYE_CLEARANCE for the first EYE_HOLD blocks, easing down to the ground's
    *  height by CASTER_SPAN (a cast from a ledge or a jump). */
   static final double EYE = 1.62, EYE_CLEARANCE = 0.8, EYE_HOLD = 10, CASTER_SPAN = 40;
   /** Seconds the head takes from the caster to the end of the cut: the visual front. */
   static final float REVEAL = ReinhardRules.WORLD_CUT_WAVE_TICKS / 20F;
   /** Seconds after the impact: it shuts on DONE, not before EARLIEST and not after HOLD. */
   static final float EARLIEST = REVEAL + 0.5F, HOLD = 7.5F;
   /** Seconds zipping shut from the caster to the end; WorldCutScene fades for 3.5 s after DONE. */
   static final float CLOSE = 2.5F;
   /** Width profile in blocks: tail opening, body growth from GROW, a 5 % ripple every WAVE blocks. */
   static final float TAIL_BLOCKS = 12F, GROW = 0.82F, GROW_BLOCKS = 40F, RIPPLE = 0.05F, WAVE_BLOCKS = 37F;
   /** Brightening on a pulse, as on the diagonal wound. */
   static final float PULSE_LIFT = 0.12F;
   private static final double TICKS_PER_BLOCK = ReinhardRules.WORLD_CUT_WAVE_TICKS / (double)ReinhardRules.CUT_LENGTH;
   /** Surface smoothing, slices either side, and one Catmull-Rom control point every STEP blocks. */
   static final int SMOOTH = 5, STEP = 4;
   private final Map<UUID, Built> paths = new HashMap<>();

   /** The path of one cast, built once from its packet frame. */
   record Built(FrameKey key, ErasurePath path, float seed, ErasureStroke.Profile profile) {
   }

   /** Identity of the packet frame a path was built from. */
   private record FrameKey(Object shape, Object surface) {
   }

   private WorldCutErasure() {
   }

   /** Exact front along the cut, blocks, at elapsed tick t (unclamped). */
   static double front(double t) {
      return (t - ReinhardRules.WORLD_CUT_BLAST) / TICKS_PER_BLOCK;
   }

   /** Seconds after the impact (the BLAST tick of the shared server clock). */
   static float sinceImpact(double t) {
      return (float)((t - ReinhardRules.WORLD_CUT_BLAST) / 20.0);
   }

   /** Seconds after the impact at which it starts to shut, for DONE at elapsed tick {@code doneElapsed}
    *  (+inf while the server still works). Continuous: DONE arriving now never makes it jump. */
   static float closeAt(double doneElapsed) {
      double done = sinceImpact(doneElapsed);
      return (float)Math.min(HOLD, Math.max(EARLIEST, done));
   }

   /** Head along the stroke, blocks: the visual front carried over to the path's own length. */
   static float head(double t, float length) {
      return (float)(length * Math.max(0, Math.min(1, front(t) / ReinhardRules.CUT_LENGTH)));
   }

   /** True while anything of the stroke can be drawn: from the gathering flame to the end of the closing. */
   static boolean active(double t, float closeAt) {
      float since = sinceImpact(t);
      return since >= ErasureStroke.AURA_START && since <= closeAt + CLOSE;
   }

   @Override
   public void collect(List<ErasureStroke> out, float partialTick, long gameTime) {
      WorldCutScene.Active[] frames = WorldCutScene.frames();
      if (frames.length == 0) {
         paths.clear();
         return;
      }
      paths.keySet().removeIf(id -> {
         for (WorldCutScene.Active a : frames) if (a.packet.castId().equals(id)) return false;
         return true;
      });
      for (WorldCutScene.Active scene : frames) {
         double t = WorldCutScene.elapsed(scene.packet.startTime(), gameTime, partialTick);
         float close = closeAt(scene.acceptedUntil() + 1);
         if (!active(t, close)) continue;
         ErasureStroke stroke = stroke(built(scene), t, close, WorldCutPulse.envelope(scene.pulsePhase(t)));
         if (stroke != null) out.add(stroke);
      }
   }

   /** The stroke at elapsed tick t; null outside its window. */
   static ErasureStroke stroke(Built built, double t, float closeAt, float pulse) {
      if (!active(t, closeAt)) return null;
      float length = built.path().length();
      float swell = Math.max(0F, Math.min(1F, pulse));
      ErasureStyle style = swell > 0 ? ErasureStyle.shimmer(PULSE_LIFT * swell, 0F) : ErasureStyle.CYAN;
      return new ErasureStroke(built.path(), 0F, length, SCALE, built.seed(), style, true, built.profile())
         .updateHeld(sinceImpact(t), head(t, length), REVEAL, closeAt, CLOSE, swell);
   }

   private Built built(WorldCutScene.Active scene) {
      FrameKey key = new FrameKey(scene.packet.shape(), scene.packet.surface());
      Built built = paths.get(scene.packet.castId());
      if (built == null || !built.key.equals(key)) {
         built = build(key, scene.packet.shape(), scene.packet.surface());
         paths.put(scene.packet.castId(), built);
      }
      return built;
   }

   private static Built build(FrameKey key, WorldCutShape shape, int[] surface) {
      ErasurePath path = path(shape, surface);
      ErasureStroke.Profile profile = ErasureStroke.Profile.forLength(path.length(), TAIL_BLOCKS, GROW, GROW_BLOCKS,
         RIPPLE, WAVE_BLOCKS);
      return new Built(key, path, (shape.seed() & 0xFFFF) / 65536F, profile);
   }

   /** Test access: the stroke of a frame. */
   static Built built(WorldCutShape shape, int[] surface) {
      return build(new FrameKey(shape, surface), shape, surface);
   }

   /** The axis smoothed by Catmull-Rom through one point every {@link #STEP} blocks. */
   static ErasurePath path(WorldCutShape shape, int[] surface) {
      Vec3[] axis = axis(shape, surface);
      List<Vec3> control = new ArrayList<>(axis.length / STEP + 2);
      for (int i = 0; i < axis.length; i += STEP) control.add(axis[i]);
      if ((axis.length - 1) % STEP != 0) control.add(axis[axis.length - 1]);
      return ErasurePath.smooth(control);
   }

   /** The axis of the cut LIFT over its smoothed ground, one sample per block; near the caster never below the
    *  caster's eyes plus the gap. */
   static Vec3[] axis(WorldCutShape shape, int[] surface) {
      int n = surface.length;
      Vec3[] points = new Vec3[n + 1];
      double eyeFloor = shape.origin().y + EYE + EYE_CLEARANCE + RADIUS;
      for (int i = 0; i <= n; i++) {
         double sum = 0;
         int count = 0;
         for (int j = i - SMOOTH; j <= i + SMOOTH; j++) {
            int c = Math.max(0, Math.min(n - 1, j));
            sum += surface[c];
            count++;
         }
         double y = sum / count + LIFT;
         double near = 1 - smoothstep(EYE_HOLD, CASTER_SPAN, i);
         y += near * Math.max(0, eyeFloor - y);
         Vec3 p = shape.axis(i);
         points[i] = new Vec3(p.x, y, p.z);
      }
      return points;
   }

   private static double smoothstep(double edge0, double edge1, double x) {
      double t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
      return t * t * (3 - 2 * t);
   }
}
