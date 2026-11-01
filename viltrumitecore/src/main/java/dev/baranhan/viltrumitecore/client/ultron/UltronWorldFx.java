package dev.baranhan.viltrumitecore.client.ultron;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.baranhan.viltrumitecore.client.chitauri.GlowDraw;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronNodeKind;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronNodeState;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronStateS2CPacket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * World overlays drawn from the synced uprising state (camera-relative, additive, inside UltronVfx's glow
 * batch): the data links between discovered nodes with pulses running out from the core, a signal pillar over
 * every far discovered node (red online, orange under construction, pale blue while hacked), a ground marker
 * on discovered planned sites, Ultron's upload tethers from the online nodes into Ultron Prime during the
 * finale, and a slow red halo over every machine he controls.
 */
final class UltronWorldFx {
   static final double LINK_RANGE = 200.0;
   static final double PILLAR_NEAR = 40.0;
   static final double PILLAR_FAR = 400.0;
   private static final float NODE_TOP = 4.1F;
   private static final Map<Integer, UltronStateS2CPacket.Node> BY_ID = new HashMap<>();

   private UltronWorldFx() {
   }

   static void draw(BufferBuilder buf, Matrix4f m, Vec3 eye, float partial, int clock) {
      UltronStateS2CPacket s = UltronClientState.get();
      ClientLevel level = Minecraft.getInstance().level;
      if (s == null || level == null) {
         return;
      }
      float time = clock + partial;
      List<UltronStateS2CPacket.Node> nodes = s.nodes();
      BY_ID.clear();
      for (UltronStateS2CPacket.Node n : nodes) {
         BY_ID.put(n.id(), n);
      }
      Vector3f a = new Vector3f();
      Vector3f b = new Vector3f();
      for (UltronStateS2CPacket.Node n : nodes) {
         if (!n.discovered()) {
            continue;
         }
         UltronNodeState state = UltronNodeState.byId(n.state());
         top(n, eye, a);
         double dist = Math.sqrt(a.x * a.x + a.z * a.z);
         for (int link : n.links()) {
            UltronStateS2CPacket.Node o = BY_ID.get(link);
            if (o == null || o.id() <= n.id() || !o.discovered()) {
               continue;
            }
            top(o, eye, b);
            double od = Math.sqrt(b.x * b.x + b.z * b.z);
            if (Math.min(dist, od) <= LINK_RANGE) {
               link(buf, m, n, o, a, b, time);
            }
         }
         if (state.standing() && dist > PILLAR_NEAR && dist < PILLAR_FAR) {
            pillar(buf, m, n, state, a, dist, time);
         } else if (state == UltronNodeState.PLANNED && dist < 96.0) {
            planned(buf, m, n, eye, time);
         }
      }
      if (s.uploading() && s.bossId() >= 0) {
         Entity boss = level.getEntity(s.bossId());
         if (boss != null) {
            tethers(buf, m, s, boss, eye, partial, time);
         }
      }
      for (int id : s.captured()) {
         Entity e = level.getEntity(id);
         if (e != null && e.distanceToSqr(eye) < 64.0 * 64.0) {
            halo(buf, m, e, eye, partial, time);
         }
      }
   }

   /** The top of a node's pylon, camera-relative. */
   private static void top(UltronStateS2CPacket.Node n, Vec3 eye, Vector3f out) {
      out.set((float)(n.x() + 0.5 - eye.x), (float)(n.y() + NODE_TOP - eye.y), (float)(n.z() + 0.5 - eye.z));
   }

   /** A sagging data cable between two node tops; pulses run along it while both ends are powered. */
   private static void link(BufferBuilder buf, Matrix4f m, UltronStateS2CPacket.Node n, UltronStateS2CPacket.Node o, Vector3f a, Vector3f b, float time) {
      boolean live = n.powered() && o.powered() && UltronNodeState.byId(n.state()) == UltronNodeState.ONLINE
         && UltronNodeState.byId(o.state()) == UltronNodeState.ONLINE;
      boolean broken = !UltronNodeState.byId(n.state()).standing() || !UltronNodeState.byId(o.state()).standing();
      if (broken) {
         return;
      }
      float span = a.distance(b);
      int segs = Mth.clamp((int)(span / 3.0F), 6, 24);
      float sag = Math.min(6.0F, span * 0.08F);
      Vector3f p0 = new Vector3f();
      Vector3f p1 = new Vector3f();
      Vector3f cam = new Vector3f();
      int alpha = live ? 70 : 28;
      for (int i = 0; i < segs; i++) {
         point(a, b, sag, i / (float)segs, p0);
         point(a, b, sag, (i + 1) / (float)segs, p1);
         cam.set(p0).negate();
         GlowDraw.ribbon(buf, m, p0, p1, cam, 0.09F, 0.09F, 255, live ? 50 : 90, live ? 40 : 80, alpha, alpha);
      }
      if (live) {
         // two pulses per link, running away from the lower id (towards the edge of the graph)
         for (int k = 0; k < 2; k++) {
            float t = ((time * 0.012F * (24.0F / Math.max(12.0F, span)) * 2.0F) + k * 0.5F + n.id() * 0.37F) % 1.0F;
            point(a, b, sag, t, p0);
            cam.set(p0).negate();
            float size = 0.35F + 0.1F * Mth.sin(time * 0.6F + k);
            GlowDraw.billboard(buf, m, p0, left(cam), upOf(cam), size, 0.3F, 255, 90, 70, 200);
         }
      }
   }

   private static void point(Vector3f a, Vector3f b, float sag, float t, Vector3f out) {
      out.set(a).lerp(b, t);
      out.y -= sag * 4.0F * t * (1.0F - t);
   }

   /** A tall marker over a far node so players can find it across the city. */
   private static void pillar(BufferBuilder buf, Matrix4f m, UltronStateS2CPacket.Node n, UltronNodeState state, Vector3f topAt, double dist, float time) {
      int r, g, b;
      if (state == UltronNodeState.DISABLED) {
         r = 150;
         g = 210;
         b = 255;
      } else if (state == UltronNodeState.BUILDING) {
         r = 255;
         g = 130;
         b = 50;
      } else {
         r = 255;
         g = 45;
         b = 35;
      }
      float fade = (float)Mth.clamp((dist - PILLAR_NEAR) / 24.0, 0.0, 1.0) * (float)Mth.clamp((PILLAR_FAR - dist) / 80.0, 0.0, 1.0);
      float height = 34.0F + (UltronNodeKind.byId(n.kind()) == UltronNodeKind.CORE ? 26.0F : 0.0F);
      float width = (float)Math.max(0.25, dist * 0.006);
      Vector3f base = new Vector3f(topAt);
      Vector3f tip = new Vector3f(topAt).add(0.0F, height, 0.0F);
      Vector3f cam = new Vector3f(base).negate();
      int a = (int)(110 * fade * (n.powered() || state != UltronNodeState.ONLINE ? 1.0F : 0.5F));
      GlowDraw.ribbon(buf, m, base, tip, cam, width * 1.8F, width * 0.4F, r, g, b, a / 2, 0);
      GlowDraw.ribbon(buf, m, base, tip, cam, width * 0.6F, width * 0.2F, r, Math.min(255, g + 80), Math.min(255, b + 60), a, 0);
      float blink = (int)(time / 10.0F) % 2 == 0 ? 1.0F : 0.6F;
      Vector3f head = new Vector3f(topAt).add(0.0F, 1.0F, 0.0F);
      GlowDraw.billboard(buf, m, head, left(cam), upOf(cam), width * 4.0F, 0.3F, r, g, b, (int)(a * 1.4F * blink));
   }

   /** A discovered site the network means to build on: a dashed red circle and a slow sweep on the ground. */
   private static void planned(BufferBuilder buf, Matrix4f m, UltronStateS2CPacket.Node n, Vec3 eye, float time) {
      Vector3f c = new Vector3f((float)(n.x() + 0.5 - eye.x), (float)(n.y() + 0.08 - eye.y), (float)(n.z() + 0.5 - eye.z));
      Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
      Vector3f p0 = new Vector3f();
      Vector3f p1 = new Vector3f();
      float r = 1.8F;
      float spin = time * 0.01F;
      for (int i = 0; i < 12; i++) {
         float a0 = spin + i * Mth.TWO_PI / 12.0F;
         float a1 = a0 + Mth.TWO_PI / 24.0F;
         p0.set(c.x + Mth.cos(a0) * r, c.y, c.z + Mth.sin(a0) * r);
         p1.set(c.x + Mth.cos(a1) * r, c.y, c.z + Mth.sin(a1) * r);
         GlowDraw.ribbon(buf, m, p0, p1, up, 0.08F, 0.08F, 255, 50, 40, 90, 90);
      }
      float pulse = (time * 0.02F + n.id() * 0.3F) % 1.0F;
      GlowDraw.ring(buf, m, c, new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F), r * pulse * 0.9F, r * pulse, 0.6F, 24, 255, 60, 40,
         (int)(80 * (1.0F - pulse)));
   }

   /** The finale: every online node pours itself into Ultron Prime through a crackling tether. */
   private static void tethers(BufferBuilder buf, Matrix4f m, UltronStateS2CPacket s, Entity boss, Vec3 eye, float partial, float time) {
      Vec3 at = boss.getPosition(partial).add(0.0, boss.getBbHeight() * 0.6, 0.0);
      Vector3f to = new Vector3f((float)(at.x - eye.x), (float)(at.y - eye.y), (float)(at.z - eye.z));
      Vector3f from = new Vector3f();
      Vector3f p0 = new Vector3f();
      Vector3f p1 = new Vector3f();
      Vector3f cam = new Vector3f();
      for (UltronStateS2CPacket.Node n : s.nodes()) {
         if (!n.discovered() || UltronNodeState.byId(n.state()) != UltronNodeState.ONLINE || !n.powered()) {
            continue;
         }
         top(n, eye, from);
         from.y += 0.4F;
         float span = from.distance(to);
         if (span > 220.0F) {
            continue;
         }
         int segs = Mth.clamp((int)(span / 2.0F), 8, 40);
         float lift = Math.min(10.0F, span * 0.15F);
         for (int i = 0; i < segs; i++) {
            arcPoint(from, to, lift, i / (float)segs, time, n.id(), i, p0);
            arcPoint(from, to, lift, (i + 1) / (float)segs, time, n.id(), i + 1, p1);
            cam.set(p0).negate();
            GlowDraw.ribbon(buf, m, p0, p1, cam, 0.28F, 0.28F, 255, 40, 30, 60, 60);
            GlowDraw.ribbon(buf, m, p0, p1, cam, 0.08F, 0.08F, 255, 200, 190, 160, 160);
         }
         for (int k = 0; k < 3; k++) {
            float t = (time * 0.03F + k / 3.0F + n.id() * 0.21F) % 1.0F;
            arcPoint(from, to, lift, t, time, n.id(), -1, p0);
            cam.set(p0).negate();
            GlowDraw.billboard(buf, m, p0, left(cam), upOf(cam), 0.45F, 0.3F, 255, 120, 90, 210);
         }
      }
      cam.set(to).negate();
      float swell = 0.8F + 0.2F * Mth.sin(time * 0.4F);
      GlowDraw.billboard(buf, m, to, left(cam), upOf(cam), 2.2F * swell, 0.2F, 255, 40, 30, 90);
   }

   /** A point on the tether arc with a little electric jitter (none at the ends). */
   private static void arcPoint(Vector3f a, Vector3f b, float lift, float t, float time, int id, int i, Vector3f out) {
      out.set(a).lerp(b, t);
      float bell = 4.0F * t * (1.0F - t);
      out.y += lift * bell;
      if (i >= 0 && bell > 0.05F) {
         float j = (float)(((int)(time * 0.5F) * 31 + i * 17 + id * 7) % 13) / 13.0F - 0.5F;
         float k = (float)(((int)(time * 0.5F) * 13 + i * 29 + id * 11) % 11) / 11.0F - 0.5F;
         out.x += j * 0.5F * bell;
         out.z += k * 0.5F * bell;
      }
   }

   /** A slow red ring and three orbiting motes over a captured machine. */
   private static void halo(BufferBuilder buf, Matrix4f m, Entity e, Vec3 eye, float partial, float time) {
      Vec3 p = e.getPosition(partial);
      float h = e.getBbHeight();
      float r = Math.max(0.5F, e.getBbWidth() * 0.75F);
      Vector3f c = new Vector3f((float)(p.x - eye.x), (float)(p.y + h + 0.35 - eye.y), (float)(p.z - eye.z));
      float bob = 0.06F * Mth.sin(time * 0.15F + e.getId());
      c.y += bob;
      GlowDraw.ring(buf, m, c, new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F), r * 0.8F, r, 0.6F, 28, 255, 40, 30, 120);
      Vector3f mote = new Vector3f();
      Vector3f cam = new Vector3f();
      for (int k = 0; k < 3; k++) {
         float ang = time * 0.12F + k * Mth.TWO_PI / 3.0F;
         mote.set(c.x + Mth.cos(ang) * r * 0.9F, c.y, c.z + Mth.sin(ang) * r * 0.9F);
         cam.set(mote).negate();
         GlowDraw.billboard(buf, m, mote, left(cam), upOf(cam), 0.14F, 0.4F, 255, 120, 90, 200);
      }
   }

   private static Vector3f left(Vector3f toCam) {
      Vector3f l = new Vector3f(0.0F, 1.0F, 0.0F).cross(toCam);
      if (l.lengthSquared() < 1.0E-6F) {
         l.set(1.0F, 0.0F, 0.0F);
      }
      return l.normalize();
   }

   private static Vector3f upOf(Vector3f toCam) {
      Vector3f l = left(toCam);
      Vector3f u = new Vector3f(toCam).cross(l);
      if (u.lengthSquared() < 1.0E-6F) {
         u.set(0.0F, 1.0F, 0.0F);
      }
      return u.normalize();
   }
}
