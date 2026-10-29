package dev.baranhan.viltrumitecore.client.jinwoo;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooMove;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Ribbon trails from the real dagger tips: world-space samples recorded by the third-person render,
 * view-space samples by the first-person arms. Right blade (Kasaka's Fang) purple to teal, left (Knight
 * Killer) violet. Additive POSITION_COLOR quads. Client only.
 */
public final class DaggerTrails {
   static final double LIFE = 0.16;
   static final int MAX_SAMPLES = 28;
   private static final Map<Long, ArrayDeque<Sample>> WORLD = new HashMap<>();
   private static final ArrayDeque<ViewSample>[] VIEW = newViews();

   record Sample(Vec3 tip, Vec3 base, long nanos) {
   }

   record ViewSample(Vector3f tip, Vector3f base, long nanos) {
   }

   @SuppressWarnings("unchecked")
   private static ArrayDeque<ViewSample>[] newViews() {
      return new ArrayDeque[]{new ArrayDeque<>(), new ArrayDeque<>()};
   }

   private DaggerTrails() {
   }

   /** Moves whose blades leave a trail. */
   public static boolean emitting(HeroPublicSnapshot s) {
      JinwooMove move = JinwooView.move(s);
      return move != null && (move.isCombo() || move == JinwooMove.RIPOSTE || move == JinwooMove.RUSH || move == JinwooMove.DIVE
         || move == JinwooMove.DIVE_LAND || move == JinwooMove.CHARGE || move == JinwooMove.STEP || move == JinwooMove.AUTHORITY_SLAM);
   }

   public static void record(int entity, int blade, Vec3 tip, Vec3 base, boolean emitting) {
      if (!emitting) {
         return;
      }
      ArrayDeque<Sample> q = WORLD.computeIfAbsent(((long)entity << 1) | blade, k -> new ArrayDeque<>());
      long now = Util.getNanos();
      Sample last = q.peekLast();
      if (last != null && now - last.nanos < 4_000_000L) {
         return;
      }
      q.addLast(new Sample(tip, base, now));
      while (q.size() > MAX_SAMPLES) {
         q.removeFirst();
      }
   }

   public static void recordView(int blade, Vector3f tip, Vector3f base, boolean emitting) {
      ArrayDeque<ViewSample> q = VIEW[blade];
      if (!emitting) {
         return;
      }
      long now = Util.getNanos();
      ViewSample last = q.peekLast();
      if (last != null && now - last.nanos < 4_000_000L) {
         return;
      }
      q.addLast(new ViewSample(tip, base, now));
      while (q.size() > MAX_SAMPLES) {
         q.removeFirst();
      }
   }

   static int[] color(int blade, float t) {
      // t: 0 newest .. 1 oldest
      if (blade == 0) {
         return new int[]{(int)(90 + 80 * t), (int)(230 - 150 * t), (int)(230 + 25 * t)};
      }
      return new int[]{(int)(170 - 40 * t), (int)(90 - 40 * t), 255};
   }

   /** World ribbons into a POSITION_COLOR quad buffer (camera-relative). */
   public static void renderWorld(BufferBuilder buffer, Vec3 cam) {
      long now = Util.getNanos();
      Iterator<Map.Entry<Long, ArrayDeque<Sample>>> it = WORLD.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<Long, ArrayDeque<Sample>> e = it.next();
         ArrayDeque<Sample> q = e.getValue();
         while (!q.isEmpty() && (now - q.peekFirst().nanos) / 1.0E9 > LIFE) {
            q.removeFirst();
         }
         if (q.isEmpty()) {
            it.remove();
            continue;
         }
         int blade = (int)(e.getKey() & 1L);
         Sample prev = null;
         for (Sample cur : q) {
            if (prev != null) {
               float a0 = 1.0F - (float)((now - prev.nanos) / 1.0E9 / LIFE);
               float a1 = 1.0F - (float)((now - cur.nanos) / 1.0E9 / LIFE);
               quad(buffer, prev.base.subtract(cam), prev.tip.subtract(cam), cur.tip.subtract(cam), cur.base.subtract(cam), blade, a0, a1);
            }
            prev = cur;
         }
      }
   }

   private static void quad(BufferBuilder b, Vec3 b0, Vec3 t0, Vec3 t1, Vec3 b1, int blade, float a0, float a1) {
      a0 = Math.max(0, a0);
      a1 = Math.max(0, a1);
      int[] c0 = color(blade, 1 - a0);
      int[] c1 = color(blade, 1 - a1);
      b.vertex(b0.x, b0.y, b0.z).color(c0[0], c0[1], c0[2], (int)(a0 * a0 * 40)).endVertex();
      b.vertex(t0.x, t0.y, t0.z).color(c0[0], c0[1], c0[2], (int)(a0 * 220)).endVertex();
      b.vertex(t1.x, t1.y, t1.z).color(c1[0], c1[1], c1[2], (int)(a1 * 220)).endVertex();
      b.vertex(b1.x, b1.y, b1.z).color(c1[0], c1[1], c1[2], (int)(a1 * a1 * 40)).endVertex();
   }

   /** First-person ribbons in the hand pass (view space; additive lightning type). */
   public static void renderView(PoseStack stack, MultiBufferSource buffers) {
      long now = Util.getNanos();
      VertexConsumer vc = null;
      for (int blade = 0; blade < 2; blade++) {
         ArrayDeque<ViewSample> q = VIEW[blade];
         while (!q.isEmpty() && (now - q.peekFirst().nanos) / 1.0E9 > LIFE) {
            q.removeFirst();
         }
         ViewSample prev = null;
         for (ViewSample cur : q) {
            if (prev != null) {
               if (vc == null) {
                  vc = buffers.getBuffer(RenderType.lightning());
               }
               float a0 = Math.max(0, 1.0F - (float)((now - prev.nanos) / 1.0E9 / LIFE));
               float a1 = Math.max(0, 1.0F - (float)((now - cur.nanos) / 1.0E9 / LIFE));
               int[] c0 = color(blade, 1 - a0);
               int[] c1 = color(blade, 1 - a1);
               vc.vertex(prev.base.x, prev.base.y, prev.base.z).color(c0[0], c0[1], c0[2], (int)(a0 * a0 * 30)).endVertex();
               vc.vertex(prev.tip.x, prev.tip.y, prev.tip.z).color(c0[0], c0[1], c0[2], (int)(a0 * 170)).endVertex();
               vc.vertex(cur.tip.x, cur.tip.y, cur.tip.z).color(c1[0], c1[1], c1[2], (int)(a1 * 170)).endVertex();
               vc.vertex(cur.base.x, cur.base.y, cur.base.z).color(c1[0], c1[1], c1[2], (int)(a1 * a1 * 30)).endVertex();
            }
            prev = cur;
         }
      }
   }

   public static void clear() {
      WORLD.clear();
      VIEW[0].clear();
      VIEW[1].clear();
   }
}
