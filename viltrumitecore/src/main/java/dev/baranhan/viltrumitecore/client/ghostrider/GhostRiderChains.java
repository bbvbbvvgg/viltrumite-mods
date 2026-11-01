package dev.baranhan.viltrumitecore.client.ghostrider;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderFlags;
import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderMove;
import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderRules;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Hell Chain: from the drawn fist to the chain's tip, a curve of white-hot links (two crossed quads per link,
 * textures/entity/ghost_rider/chain.png) with tongues of hellfire along it. The tip follows the synced move: the whip
 * of a lash (overhead, backhand, two-handed), the throw to the hooked body and the yank, the circle of the whirl, the
 * overhead slam, the tow behind the Hell Cycle. Render thread only.
 */
public final class GhostRiderChains {
   static final float LINK = 0.13F;
   private static final Vector3f AXIS = new Vector3f();

   private GhostRiderChains() {
   }

   /** Tip, slack and the hand of the chain for this frame, or null when the chain is wrapped round his chest. */
   record Shape(Vec3 hand, Vec3 tip, double sag, double whip) {
   }

   static void draw(ClientLevel level, Vec3 cam, PoseStack stack, MultiBufferSource.BufferSource buffers, float partial) {
      Matrix4f m = stack.last().pose();
      VertexConsumer links = null;
      float age = GhostRiderVfx.clientTicks + partial;
      for (AbstractClientPlayer p : level.players()) {
         HeroPublicSnapshot s = GhostRiderView.of(p);
         if (s == null || !GhostRiderView.chainOut(s) || p.distanceToSqr(cam) > 80 * 80) {
            continue;
         }
         Shape shape = shape(level, p, s, partial);
         if (shape == null) {
            continue;
         }
         if (links == null) {
            links = buffers.getBuffer(RenderType.entityCutoutNoCull(GhostRiderView.CHAIN));
         }
         drawLinks(links, m, cam, shape, age);
      }
      if (links != null) {
         buffers.endBatch(RenderType.entityCutoutNoCull(GhostRiderView.CHAIN));
         VertexConsumer vc = buffers.getBuffer(HellfireDraw.type());
         for (AbstractClientPlayer p : level.players()) {
            HeroPublicSnapshot s = GhostRiderView.of(p);
            if (s == null || !GhostRiderView.chainOut(s) || p.distanceToSqr(cam) > 80 * 80) {
               continue;
            }
            Shape shape = shape(level, p, s, partial);
            if (shape != null) {
               drawFire(vc, m, cam, shape, age, p.getId());
            }
         }
      }
   }

   @Nullable
   static Shape shape(ClientLevel level, AbstractClientPlayer p, HeroPublicSnapshot s, float partial) {
      GhostRiderMove move = GhostRiderView.move(s);
      float t = GhostRiderView.elapsed(s, partial);
      boolean left = move == GhostRiderMove.LASH_2;
      Vec3 hand = GhostRiderAnchors.hand(p, !left, partial);
      Vec3 look = p.getViewVector(partial);
      Vec3 flat = new Vec3(look.x, 0.0, look.z);
      flat = flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize();
      Vec3 right = new Vec3(-flat.z, 0.0, flat.x);
      Entity target = level.getEntity(s.controlTargetId());
      Vec3 targetPos = target != null ? target.getPosition(partial).add(0.0, target.getBbHeight() * 0.55, 0.0) : null;
      if (move == null || move == GhostRiderMove.HOOK && targetPos != null && t >= GhostRiderMove.HOOK.contact) {
         // the hooked body held on the chain, or towed behind the bike
         if (targetPos != null && (GhostRiderView.flag(s, GhostRiderFlags.CHAIN) || GhostRiderView.flag(s, GhostRiderFlags.TOW))) {
            return new Shape(hand, targetPos, 0.25, 0.0);
         }
         return null;
      }
      switch (move) {
         case LASH_1, LASH_3, BIKE_LASH -> {
            double reach = move == GhostRiderMove.BIKE_LASH ? GhostRiderRules.BIKE_LASH_REACH : GhostRiderRules.LASH_REACH;
            float contact = move.contact;
            float ext;
            float pitch;
            if (t < contact) {
               ext = 0.25F + 0.35F * t / contact;
               pitch = Mth.lerp(t / contact, 2.2F, 1.2F);          // over the shoulder, behind and above
            } else {
               float k = Math.min(1.0F, (t - contact) / 2.0F);
               ext = t < contact + 3.0F ? Mth.lerp(k, 0.6F, 1.0F) : Math.max(0.0F, 1.0F - (t - contact - 3.0F) / (move.length - contact - 3.0F));
               pitch = Mth.lerp(k, 1.2F, -0.12F);                  // cracks forward and down
            }
            Vec3 dir = flat.scale(Math.cos(pitch)).add(0, Math.sin(pitch), 0).add(look.scale(0.3)).normalize();
            Vec3 tip = hand.add(dir.scale(reach * ext));
            return new Shape(hand, tip, 0.1, t < contact + 2 ? 0.35 : 0.15);
         }
         case LASH_2 -> {
            float contact = move.contact;
            float sweep = t < contact ? Mth.lerp(t / contact, 1.6F, 0.9F) : Mth.lerp(Math.min(1.0F, (t - contact) / 2.5F), 0.9F, -1.1F);
            float ext = t < contact ? 0.5F + 0.5F * t / contact : Math.max(0.0F, 1.0F - (t - contact - 2.5F) / (move.length - contact - 2.5F));
            Vec3 dir = flat.scale(Math.cos(sweep)).add(right.scale(-Math.sin(sweep))).add(0, -0.08, 0).normalize();
            return new Shape(hand, hand.add(dir.scale(GhostRiderRules.LASH_REACH * Math.min(1.0F, ext))), 0.05, 0.25);
         }
         case HOOK, BIKE_HOOK, HOOK_MISS -> {
            float contact = Math.max(1, move.contact >= 0 ? move.contact : 3);
            if (targetPos != null && move != GhostRiderMove.HOOK_MISS) {
               double k = Math.min(1.0, t / contact);
               return new Shape(hand, hand.add(targetPos.subtract(hand).scale(k)), 0.05, 0.1);
            }
            double k = t < 4 ? t / 4.0 : Math.max(0.0, 1.0 - (t - 4.0) / 4.0);
            return new Shape(hand, hand.add(look.scale(GhostRiderRules.HOOK_RANGE * k)), 0.08, 0.1);
         }
         case YANK -> {
            if (targetPos != null) {
               return new Shape(hand, targetPos, 0.02, 0.0);
            }
            return null;
         }
         case WHIRL -> {
            float w = GhostRiderGear.bump(t, 0.0F, GhostRiderRules.WHIRL_TICKS, 3.0F);
            double ang = t * 0.9;
            double r = GhostRiderRules.WHIRL_RADIUS * (0.4 + 0.6 * w);
            Vec3 c = p.getPosition(partial).add(0, 1.1, 0);
            Vec3 tip = c.add(Math.cos(ang) * r, 0.35 * Math.sin(ang * 2.0), Math.sin(ang) * r);
            return new Shape(hand, tip, -0.2, 0.0);
         }
         case SLAM -> {
            float contact = GhostRiderRules.SLAM_CONTACT;
            double reach = GhostRiderRules.SLAM_RADIUS * 0.75;
            Vec3 tip;
            if (t < contact) {
               double a = Mth.lerp(t / contact, 2.4, 1.4);
               tip = hand.add(flat.scale(Math.cos(a) * reach * 0.6)).add(0, Math.sin(a) * reach * 0.6, 0);
            } else {
               double k = Math.min(1.0, (t - contact) / 1.5);
               Vec3 ground = p.getPosition(partial).add(flat.scale(reach)).add(0, 0.1, 0);
               Vec3 high = hand.add(flat.scale(Math.cos(1.4) * reach * 0.6)).add(0, Math.sin(1.4) * reach * 0.6, 0);
               tip = high.add(ground.subtract(high).scale(k));
               if (t > contact + 4) {
                  double back = Math.min(1.0, (t - contact - 4) / (GhostRiderRules.SLAM_TICKS - contact - 4));
                  tip = tip.add(hand.subtract(tip).scale(back));
               }
            }
            return new Shape(hand, tip, 0.12, 0.2);
         }
         default -> {
            if (targetPos != null && (GhostRiderView.flag(s, GhostRiderFlags.CHAIN) || GhostRiderView.flag(s, GhostRiderFlags.TOW))) {
               return new Shape(hand, targetPos, 0.25, 0.0);
            }
            return null;
         }
      }
   }

   /** Point on the chain at u in 0..1: a quadratic curve sagging by {@code sag} * length, a whip wave on top. */
   static Vec3 at(Shape s, double u, float age) {
      Vec3 d = s.tip.subtract(s.hand);
      double len = d.length();
      Vec3 mid = s.hand.add(d.scale(0.5)).add(0, -s.sag * len, 0);
      double a = (1 - u) * (1 - u);
      double b = 2 * (1 - u) * u;
      double c = u * u;
      Vec3 p = s.hand.scale(a).add(mid.scale(b)).add(s.tip.scale(c));
      if (s.whip != 0.0 && len > 0.5) {
         Vec3 side = new Vec3(-d.z, 0, d.x);
         side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
         double wave = Math.sin(u * Math.PI * 3.0 - age * 1.3) * Math.sin(u * Math.PI) * s.whip * Math.min(1.0, len * 0.15);
         p = p.add(side.scale(wave)).add(0, wave * 0.5, 0);
      }
      return p;
   }

   static void drawLinks(VertexConsumer vc, Matrix4f m, Vec3 cam, Shape s, float age) {
      double len = s.tip.distanceTo(s.hand) * (1.0 + Math.abs(s.sag) * 0.6);
      int n = Mth.clamp((int)(len / LINK), 2, 140);
      Vec3 prev = at(s, 0.0, age);
      for (int i = 1; i <= n; i++) {
         Vec3 cur = at(s, i / (double)n, age);
         Vec3 d = cur.subtract(prev);
         double dl = d.length();
         if (dl > 1.0E-4) {
            Vec3 dir = d.scale(1.0 / dl);
            Vec3 a = Math.abs(dir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            Vec3 n1 = dir.cross(a).normalize();
            Vec3 n2 = dir.cross(n1).normalize();
            Vec3 side = (i % 2 == 0 ? n1 : n2).scale(0.045);
            // heat: white-hot near the fist, deep orange at the tip
            float heat = 1.0F - (float)i / n * 0.5F;
            int r = 255;
            int g = (int)(150 + 80 * heat);
            int b = (int)(70 + 90 * heat * heat);
            quad(vc, m, cam, prev.subtract(d.scale(0.25)), cur.add(d.scale(0.25)), side, r, g, b);
         }
         prev = cur;
      }
   }

   private static void quad(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, Vec3 side, int r, int g, int bl) {
      vertex(vc, m, a.subtract(side), cam, 0.0F, 1.0F, r, g, bl);
      vertex(vc, m, a.add(side), cam, 1.0F, 1.0F, r, g, bl);
      vertex(vc, m, b.add(side), cam, 1.0F, 0.0F, r, g, bl);
      vertex(vc, m, b.subtract(side), cam, 0.0F, 0.0F, r, g, bl);
   }

   private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 p, Vec3 cam, float u, float v, int r, int g, int b) {
      vc.vertex(m, (float)(p.x - cam.x), (float)(p.y - cam.y), (float)(p.z - cam.z)).color(r, g, b, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
         .uv2(LightTexture.FULL_BRIGHT).normal(0.0F, 1.0F, 0.0F).endVertex();
   }

   /** Tongues of hellfire every half block along the chain, a bigger one at the tip. */
   static void drawFire(VertexConsumer vc, Matrix4f m, Vec3 cam, Shape s, float age, int seed) {
      double len = s.tip.distanceTo(s.hand);
      int n = Mth.clamp((int)(len / 0.5), 1, 30);
      AXIS.set(0.0F, 1.0F, 0.0F);
      for (int i = 1; i <= n; i++) {
         Vec3 p = at(s, i / (double)n, age);
         float h = (0.16F + 0.1F * GhostRiderVfx.hash(seed + i)) * HellfireDraw.flicker(age, seed + i * 3);
         GhostRiderVfx.flame(vc, m, cam, p, AXIS, 0.16F, h, HellfireDraw.frame(age, seed + i * 2), HellfireDraw.TONGUE, 255, 255, 255, 210);
      }
      GhostRiderVfx.flame(vc, m, cam, s.tip, AXIS, 0.3F, 0.38F * HellfireDraw.flicker(age, seed), HellfireDraw.frame(age, seed + 5), HellfireDraw.BLAZE, 255, 255,
         255, 230);
   }
}
