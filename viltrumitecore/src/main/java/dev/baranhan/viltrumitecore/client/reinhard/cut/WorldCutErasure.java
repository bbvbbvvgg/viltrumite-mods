package dev.baranhan.viltrumitecore.client.reinhard.cut;

import dev.baranhan.viltrumitecore.client.render.erasure.ErasurePath;
import dev.baranhan.viltrumitecore.client.render.erasure.ErasureRenderer;
import dev.baranhan.viltrumitecore.client.render.erasure.ErasureStroke;
import dev.baranhan.viltrumitecore.client.render.erasure.ErasureStyle;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutShape;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/** The Hand erasure on the ordinary World Cut: a chain of short original strokes runs with the visual front.
 *
 * The original stroke is about three blocks long and is cut in 0.1 s. The front of World Cut covers 200 blocks in
 * {@link ReinhardRules#WORLD_CUT_WAVE_TICKS} ticks. The tube is not stretched: the path is split into
 * {@link #SEGMENTS} pieces, each one an original stroke at {@link #SCALE}. Piece k is cut while the front crosses it,
 * so its head is the front itself; each piece then holds and zips shut on the original timeline, which leaves a
 * closing glass trail behind the front. Pieces overlap by {@link #OVERLAP} so the chain reads as one tube.
 *
 * Client only. It reads the fixed packet frame of {@link WorldCutScene}: no new network data, no server work.
 * A scene that is pruned or cleared takes its strokes with it. */
public final class WorldCutErasure implements ErasureRenderer.Provider {
   public static final WorldCutErasure INSTANCE = new WorldCutErasure();
   static final int SEGMENTS = 12;
   /** Original length-to-width ratio (about 8.5) for a 1.3-piece window of 200 / 12 blocks. */
   static final float SCALE = 7.5F;
   static final float OVERLAP = 0.3F;
   /** Tube axis above the surface top: the tube lies on the ground of the cut. */
   static final double LIFT = 1.04 + ErasureStroke.widthAt(SCALE) * 0.5 + 0.2;
   private static final double TICKS_PER_BLOCK = ReinhardRules.WORLD_CUT_WAVE_TICKS / (double)ReinhardRules.CUT_LENGTH;
   private static final double PIECE = ReinhardRules.CUT_LENGTH / (double)SEGMENTS;
   private static final int SMOOTH = 3;
   private final Map<UUID, Built> paths = new HashMap<>();

   private record Built(FrameKey key, ErasurePath path, float seed) {
   }

   /** Identity of the packet frame a path was built from. */
   private record FrameKey(Object shape, Object surface) {
   }

   private WorldCutErasure() {
   }

   /** Elapsed scene tick at which piece k starts: the front reaches its tail. */
   static double cutTick(int k) {
      return ReinhardRules.WORLD_CUT_BLAST + k * PIECE * TICKS_PER_BLOCK;
   }

   /** Exact front along the cut, blocks, at elapsed tick t (unclamped). */
   static double front(double t) {
      return (t - ReinhardRules.WORLD_CUT_BLAST) / TICKS_PER_BLOCK;
   }

   static float offset(int k) {
      return (float)(k * PIECE);
   }

   static float length(int k) {
      return (float)Math.min(PIECE * (1 + OVERLAP), ReinhardRules.CUT_LENGTH - k * PIECE);
   }

   /** Original stroke time of piece k, seconds; 0 when the front reaches it. */
   static float strokeTime(int k, double t) {
      return (float)((t - cutTick(k)) / 20.0);
   }

   /** Window of elapsed ticks in which anything of the chain can be drawn. */
   static boolean active(double t) {
      return t >= ReinhardRules.WORLD_CUT_BLAST + ErasureStroke.AURA_START * 20 && t <= cutTick(SEGMENTS - 1) + ErasureStroke.END * 20;
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
         if (!active(t)) continue;
         Built built = path(scene);
         double head = front(t);
         for (int k = 0; k < SEGMENTS; k++) {
            float time = strokeTime(k, t);
            if (time < (k == 0 ? ErasureStroke.AURA_START : 0F) || time > ErasureStroke.END) continue;
            float length = length(k);
            ErasureStroke stroke = new ErasureStroke(built.path, offset(k), length, SCALE, built.seed + k * 0.618F,
               ErasureStyle.CYAN, k == 0);
            stroke.update(time, (float)Math.max(0, Math.min(length, head - offset(k))));
            // Once the front reaches the next piece both heads sit on it: one flame ball, carried by the next piece.
            if (k + 1 < SEGMENTS && strokeTime(k + 1, t) >= 0) stroke.hideAura();
            out.add(stroke);
         }
      }
   }

   private Built path(WorldCutScene.Active scene) {
      FrameKey key = new FrameKey(scene.packet.shape(), scene.packet.surface());
      Built built = paths.get(scene.packet.castId());
      if (built == null || !built.key.equals(key)) {
         built = new Built(key, ErasurePath.sampled(axis(scene.packet.shape(), scene.packet.surface())),
            (scene.packet.shape().seed() & 0xFFFF) / 65536F);
         paths.put(scene.packet.castId(), built);
      }
      return built;
   }

   /** The axis of the cut at the height of its ground, one sample per block, the surface profile smoothed so the
    * tube glides over steps instead of breaking on them. */
   static Vec3[] axis(WorldCutShape shape, int[] surface) {
      int n = surface.length;
      Vec3[] points = new Vec3[n + 1];
      for (int i = 0; i <= n; i++) {
         double sum = 0;
         int count = 0;
         for (int j = i - SMOOTH; j <= i + SMOOTH; j++) {
            int c = Math.max(0, Math.min(n - 1, j));
            sum += surface[c];
            count++;
         }
         Vec3 p = shape.axis(i);
         points[i] = new Vec3(p.x, sum / count + LIFT, p.z);
      }
      return points;
   }
}
