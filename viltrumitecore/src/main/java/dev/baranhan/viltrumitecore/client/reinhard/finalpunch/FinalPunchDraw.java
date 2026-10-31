package dev.baranhan.viltrumitecore.client.reinhard.finalpunch;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Soft procedural shapes for the Final Punch (POSITION_COLOR quads, camera-relative): round
 * glows with a radial falloff, camera-facing ribbons and streaks, kite flames, soft rings and
 * jagged arcs. No flat square sprites: every shape's edge alpha goes to zero.
 */
final class FinalPunchDraw {
   private static final int GLOW_SEGMENTS = 14;
   final BufferBuilder buffer;
   final Vec3 eye;
   final Vec3 left;
   final Vec3 up;
   float fade = 1.0F;

   FinalPunchDraw(BufferBuilder buffer, Vec3 eye, Vector3f left, Vector3f up) {
      this.buffer = buffer;
      this.eye = eye;
      this.left = new Vec3(left.x(), left.y(), left.z());
      this.up = new Vec3(up.x(), up.y(), up.z());
   }

   void vertex(Vec3 p, int r, int g, int b, float a) {
      int alpha = Math.max(0, Math.min(255, Math.round(a * this.fade * 255.0F)));
      this.buffer.vertex(p.x - this.eye.x, p.y - this.eye.y, p.z - this.eye.z).color(r, g, b, alpha).endVertex();
   }

   void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int[] ca, float aa, int[] cb, float ab, int[] cc, float ac, int[] cd, float ad) {
      this.vertex(a, ca[0], ca[1], ca[2], aa);
      this.vertex(b, cb[0], cb[1], cb[2], ab);
      this.vertex(c, cc[0], cc[1], cc[2], ac);
      this.vertex(d, cd[0], cd[1], cd[2], ad);
   }

   /** A round billboard glow: {@code alpha} in the centre, zero on the rim. */
   void glow(Vec3 c, double radius, int r, int g, int b, float alpha) {
      this.glow(c, radius, r, g, b, alpha, r, g, b);
   }

   void glow(Vec3 c, double radius, int r, int g, int b, float alpha, int rr, int rg, int rb) {
      if (radius <= 0.001 || alpha <= 0.002F) return;
      Vec3 prev = null;
      for (int i = 0; i <= GLOW_SEGMENTS; i++) {
         double a = Math.PI * 2 * i / GLOW_SEGMENTS;
         Vec3 p = c.add(this.left.scale(Math.cos(a) * radius)).add(this.up.scale(Math.sin(a) * radius));
         if (prev != null) {
            this.vertex(c, r, g, b, alpha);
            this.vertex(c, r, g, b, alpha);
            this.vertex(prev, rr, rg, rb, 0.0F);
            this.vertex(p, rr, rg, rb, 0.0F);
         }
         prev = p;
      }
   }

   /** A billboard disc with a solid core and a soft rim (the small sun's body). */
   void disc(Vec3 c, double radius, double soft, int r, int g, int b, float alpha) {
      if (radius <= 0.001 || alpha <= 0.002F) return;
      double inner = radius * (1.0 - soft);
      Vec3 prevIn = null;
      Vec3 prevOut = null;
      for (int i = 0; i <= GLOW_SEGMENTS; i++) {
         double a = Math.PI * 2 * i / GLOW_SEGMENTS;
         Vec3 dir = this.left.scale(Math.cos(a)).add(this.up.scale(Math.sin(a)));
         Vec3 in = c.add(dir.scale(inner));
         Vec3 out = c.add(dir.scale(radius));
         if (prevIn != null) {
            this.vertex(c, r, g, b, alpha);
            this.vertex(c, r, g, b, alpha);
            this.vertex(prevIn, r, g, b, alpha);
            this.vertex(in, r, g, b, alpha);
            this.vertex(prevIn, r, g, b, alpha);
            this.vertex(in, r, g, b, alpha);
            this.vertex(out, r, g, b, 0.0F);
            this.vertex(prevOut, r, g, b, 0.0F);
         }
         prevIn = in;
         prevOut = out;
      }
   }

   /** Camera-facing ribbon from {@code a} (alpha {@code aa}, width {@code wa}) to {@code b}. */
   void streak(Vec3 a, Vec3 b, double wa, double wb, int[] ca, float aa, int[] cb, float ab) {
      Vec3 along = b.subtract(a);
      if (along.lengthSqr() < 1.0E-10) return;
      Vec3 view = a.add(b).scale(0.5).subtract(this.eye);
      Vec3 side = along.cross(view);
      double len = side.length();
      if (len < 1.0E-8) return;
      side = side.scale(1.0 / len);
      Vec3 sa = side.scale(wa * 0.5);
      Vec3 sb = side.scale(wb * 0.5);
      // Two halves: bright spine, transparent edges (soft ribbon).
      this.quad(a, b, b.add(sb), a.add(sa), ca, aa, cb, ab, cb, 0.0F, ca, 0.0F);
      this.quad(a, a.subtract(sa), b.subtract(sb), b, ca, aa, ca, 0.0F, cb, 0.0F, cb, ab);
   }

   void streak(Vec3 a, Vec3 b, double width, int r, int g, int bl, float aa, float ab) {
      int[] c = {r, g, bl};
      this.streak(a, b, width, width, c, aa, c, ab);
   }

   /** A kite flame from its base to its tip, facing the camera. */
   void flame(Vec3 base, Vec3 tip, double width, int[] core, int[] edge, float alpha) {
      Vec3 along = tip.subtract(base);
      if (along.lengthSqr() < 1.0E-8) return;
      Vec3 view = base.subtract(this.eye);
      Vec3 side = along.cross(view);
      double len = side.length();
      if (len < 1.0E-8) return;
      side = side.scale(width * 0.5 / len);
      Vec3 belly = base.add(along.scale(0.32));
      Vec3 l = belly.add(side);
      Vec3 r = belly.subtract(side);
      Vec3 foot = base.subtract(along.scale(0.12));
      this.quad(foot, r, tip, l, core, alpha * 0.7F, edge, 0.0F, edge, 0.0F, edge, 0.0F);
      this.quad(foot, r, belly, l, core, alpha * 0.7F, edge, 0.0F, core, alpha, edge, 0.0F);
      this.quad(belly, r, tip, l, core, alpha, edge, 0.0F, edge, 0.0F, edge, 0.0F);
   }

   /** A soft flat ring in the plane (u, v) around {@code c}. */
   void ring(Vec3 c, Vec3 u, Vec3 v, double radius, double band, int segments, int r, int g, int b, float alpha) {
      this.ring(c, u, v, radius, band, segments, r, g, b, alpha, 0, 0);
   }

   /** Same, with a rotating dash pattern ({@code dashes} bright arcs, phase in radians). */
   void ring(Vec3 c, Vec3 u, Vec3 v, double radius, double band, int segments, int r, int g, int b, float alpha, int dashes, double phase) {
      if (radius <= 0.001 || alpha <= 0.002F) return;
      double inner = Math.max(0.0, radius - band * 0.5);
      double outer = radius + band * 0.5;
      Vec3 pi = null;
      Vec3 pm = null;
      Vec3 po = null;
      float pa = 0;
      int[] col = {r, g, b};
      for (int i = 0; i <= segments; i++) {
         double a = Math.PI * 2 * i / segments;
         Vec3 dir = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
         Vec3 in = c.add(dir.scale(inner));
         Vec3 mid = c.add(dir.scale(radius));
         Vec3 out = c.add(dir.scale(outer));
         float da = dashes <= 0 ? alpha : alpha * (float) (0.25 + 0.75 * Math.pow(0.5 + 0.5 * Math.cos(a * dashes + phase), 2));
         if (pi != null) {
            this.quad(pi, in, mid, pm, col, 0.0F, col, 0.0F, col, da, col, pa);
            this.quad(pm, mid, out, po, col, pa, col, da, col, 0.0F, col, 0.0F);
         }
         pi = in;
         pm = mid;
         po = out;
         pa = da;
      }
   }

   /** A jagged electric arc: blue glow ribbon plus a thin white core. */
   void arc(Vec3 from, Vec3 to, long seed, int segments, double jitter, double width, float alpha) {
      Vec3 along = to.subtract(from);
      double len = along.length();
      if (len < 1.0E-4) return;
      Vec3 n1 = perpendicular(along);
      Vec3 n2 = along.cross(n1).normalize();
      Vec3 prev = from;
      int[] glow = {120, 170, 255};
      int[] core = {235, 245, 255};
      for (int i = 1; i <= segments; i++) {
         double f = (double) i / segments;
         Vec3 p = from.add(along.scale(f));
         if (i < segments) {
            double amp = jitter * len * Math.sin(Math.PI * f);
            p = p.add(n1.scale((FinalPunchCues.hash(seed, i * 2) - 0.5) * 2 * amp)).add(n2.scale((FinalPunchCues.hash(seed, i * 2 + 1) - 0.5) * 2 * amp));
         }
         this.streak(prev, p, width * 3.5, width * 3.5, glow, alpha * 0.35F, glow, alpha * 0.35F);
         this.streak(prev, p, width, width, core, alpha, core, alpha);
         prev = p;
      }
   }

   /** A small tumbling triangle (debris shard), drawn as a degenerate quad. */
   void shard(Vec3 c, double size, double spin, double tilt, int r, int g, int b, float alpha) {
      Vec3 u = this.left.scale(Math.cos(spin)).add(this.up.scale(Math.sin(spin)));
      Vec3 v = this.left.scale(-Math.sin(spin)).add(this.up.scale(Math.cos(spin))).scale(Math.cos(tilt));
      Vec3 a = c.add(u.scale(size));
      Vec3 b2 = c.add(u.scale(-0.5 * size)).add(v.scale(0.8 * size));
      Vec3 d = c.add(u.scale(-0.4 * size)).add(v.scale(-0.7 * size));
      int[] col = {r, g, b};
      int[] dark = {r * 3 / 4, g * 3 / 4, b * 3 / 4};
      this.quad(a, b2, d, d, col, alpha, dark, alpha, col, alpha, col, alpha);
   }

   static Vec3 perpendicular(Vec3 v) {
      Vec3 n = Math.abs(v.y) < 0.9 * v.length() ? v.cross(new Vec3(0, 1, 0)) : v.cross(new Vec3(1, 0, 0));
      double l = n.length();
      return l < 1.0E-9 ? new Vec3(1, 0, 0) : n.scale(1.0 / l);
   }
}
