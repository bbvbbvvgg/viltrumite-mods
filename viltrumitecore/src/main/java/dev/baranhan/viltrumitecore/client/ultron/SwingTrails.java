package dev.baranhan.viltrumitecore.client.ultron;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumitecore.client.chitauri.GlowDraw;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Short motion trails of striking parts (a fist, a blade hand, a palm): the rendered anchor is sampled every frame while
 * the strike is in its fast part and the last few ticks of its path are drawn as a tapering additive ribbon. World-space
 * samples, so the trail stays where the hand passed while the body moves. Render thread only.
 */
final class SwingTrails {
   private static final int MAX = 14;
   private final Map<Long, Trail> trails = new HashMap<>();

   /** Samples (when {@code on}) and draws the trail of one slot of one entity. */
   void draw(Entity e, int slot, float partial, float age, Matrix4f m, VertexConsumer vc, Vector3f toCam, Vec3 world, boolean on, float keep, float width,
      int r, int g, int b) {
      long key = (long)e.getId() << 3 | slot;
      Trail t = this.trails.get(key);
      if (t == null) {
         if (!on) {
            return;
         }
         if (this.trails.size() > 48) {
            this.trails.values().removeIf(old -> old.count == 0 || age - old.newest() > 40.0F || old.entity.isRemoved());
         }
         t = new Trail(e);
         this.trails.put(key, t);
      }
      if (on) {
         t.push(world, age);
      }
      t.expire(age, keep);
      if (t.count < 2) {
         if (!on && t.count == 0) {
            this.trails.remove(key);
         }
         return;
      }
      double ex = Mth.lerp(partial, e.xo, e.getX());
      double ey = Mth.lerp(partial, e.yo, e.getY());
      double ez = Mth.lerp(partial, e.zo, e.getZ());
      Vector3f p0 = new Vector3f();
      Vector3f p1 = new Vector3f();
      for (int i = 0; i < t.count - 1; i++) {
         float f0 = 1.0F - Mth.clamp((age - t.ages[i]) / keep, 0.0F, 1.0F);
         float f1 = 1.0F - Mth.clamp((age - t.ages[i + 1]) / keep, 0.0F, 1.0F);
         p0.set((float)(t.pts[i * 3] - ex), (float)(t.pts[i * 3 + 1] - ey), (float)(t.pts[i * 3 + 2] - ez));
         p1.set((float)(t.pts[i * 3 + 3] - ex), (float)(t.pts[i * 3 + 4] - ey), (float)(t.pts[i * 3 + 5] - ez));
         GlowDraw.ribbon(vc, m, p1, p0, toCam, width * f1, width * f0, r, g, b, (int)(150 * f1 * f1), (int)(150 * f0 * f0));
         GlowDraw.ribbon(vc, m, p1, p0, toCam, width * 0.35F * f1, width * 0.35F * f0, 255, 230, 210, (int)(160 * f1 * f1), (int)(160 * f0 * f0));
      }
   }

   private static final class Trail {
      final Entity entity;
      final double[] pts = new double[MAX * 3];
      final float[] ages = new float[MAX];
      int count;

      Trail(Entity entity) {
         this.entity = entity;
      }

      float newest() {
         return this.count == 0 ? -1000.0F : this.ages[0];
      }

      void push(Vec3 p, float age) {
         if (this.count > 0 && age <= this.ages[0]) {
            return;
         }
         if (this.count > 0) {
            double dx = p.x - this.pts[0], dy = p.y - this.pts[1], dz = p.z - this.pts[2];
            if (dx * dx + dy * dy + dz * dz < 0.0009) {
               return;
            }
         }
         System.arraycopy(this.pts, 0, this.pts, 3, this.pts.length - 3);
         System.arraycopy(this.ages, 0, this.ages, 1, this.ages.length - 1);
         this.pts[0] = p.x;
         this.pts[1] = p.y;
         this.pts[2] = p.z;
         this.ages[0] = age;
         this.count = Math.min(MAX, this.count + 1);
      }

      void expire(float age, float keep) {
         while (this.count > 0 && age - this.ages[this.count - 1] > keep) {
            this.count--;
         }
      }
   }
}
