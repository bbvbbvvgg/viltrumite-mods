package dev.baranhan.viltrumitecore.client.wish;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.client.render.vfx.ElectricVfx;
import dev.baranhan.viltrumitecore.client.render.vfx.FlashOverlay;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.hero.wish.WishSounds;
import dev.baranhan.viltrumitecore.hero.wish.WishTimeline;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * What a wish looks and sounds like. Shared base: Od Lagna's gold ring and the spiral of motes in the CALL,
 * a column of light and a body shell in the FULFIL, a flash and a shock ring on the swap. On top, each style
 * draws its hero's own climax (clock hands that stop, shadows' eyes, a thunderbolt, cards, plates, nanites,
 * heat vision, a holy pillar, frozen shards, light that lets go). Pure pixel VFX in the shared world pass;
 * the only stateful helpers are the global ElectricVfx bolts, camera shake and the white flash.
 */
final class WishSceneFx {
   private static final int BUILD_CUE = WishTimeline.CALL_END + 6;

   private WishSceneFx() {
   }

   // ---------------------------------------------------------------- sound and one-shot events

   static void play(Minecraft mc, WishScene scene, SoundEvent sound, float volume, float pitch) {
      if (mc.level == null) {
         return;
      }
      Vec3 at = position(mc, scene);
      if (at != null) {
         mc.level.playLocalSound(at.x, at.y + 1.0, at.z, sound, SoundSource.PLAYERS, volume, pitch, false);
      }
   }

   private static Vec3 position(Minecraft mc, WishScene scene) {
      Entity entity = mc.level == null ? null : mc.level.getEntity(scene.entityId);
      return entity != null ? entity.position() : scene.origin;
   }

   static void tick(Minecraft mc, WishScene scene) {
      int t = scene.age;
      Vec3 c = position(mc, scene);
      if (c == null) {
         return;
      }
      if (t == BUILD_CUE && scene.style.buildSound != null) {
         play(mc, scene, WishSceneClient.heroSound(scene.style.buildSound), 0.85F, 1.0F);
      }
      if (scene.style == WishStyle.THUNDER && !scene.swapped && (t == 20 || t == 27 || t == 33 || t == 37)) {
         ElectricVfx.crackle(c.add(0, 1.0, 0), 0.8F + 0.1F * (t - 20) / 4, 3 + (t - 20) / 6, 6, scene.seed + t);
      }
      if (scene.style == WishStyle.THUNDER && !scene.swapped && t == 30) {
         ElectricVfx.arc(c.add(0, 2.4, 0), c.add(0, 12, 0), 0.05F, 6, 2, scene.seed + 77);
      }
      if (scene.style == WishStyle.ARMOR && t == WishTimeline.SWAP + 2) {
         ElectricVfx.sparks(c.add(0, 0.2, 0), 18, 0.35F, 12, scene.seed + 5);
      }
   }

   /** The server swapped the hero: the climax of the scene. */
   static void onSwap(Minecraft mc, WishScene scene) {
      Vec3 c = position(mc, scene);
      if (c == null) {
         return;
      }
      WishStyle style = scene.style;
      if (style.climaxSound != null) {
         play(mc, scene, WishSceneClient.heroSound(style.climaxSound), 1.0F, 1.0F);
      }
      play(mc, scene, style == WishStyle.MORTAL ? WishSounds.RETURN : WishSounds.GRANTED, 1.0F, 1.0F);
      float shake = switch (style) {
         case THUNDER, ARMOR, CRIMSON -> 0.9F;
         case HOLY, SHADOW, TIME -> 0.55F;
         case MORTAL -> 0.0F;
         default -> 0.35F;
      };
      if (shake > 0) {
         CameraShake.addAt(c, shake, 24.0);
      }
      if (scene.own) {
         FlashOverlay.flash(style == WishStyle.MORTAL ? 0.3F : 0.6F, 8);
      } else if (mc.player != null && mc.player.position().distanceTo(c) < 12) {
         FlashOverlay.flash(0.18F, 6);
      }
      if (style == WishStyle.THUNDER) {
         ElectricVfx.bolt(c.add(0, 26, 0), c, 0.28F, 4, 10, scene.seed);
         ElectricVfx.skyFlash(c, 30.0F, 8, scene.seed + 1);
         if (mc.level != null) {
            ElectricVfx.ring(mc.level, c.add(0, 0.1, 0), 3.0, 0.07F, 10, 12, scene.seed + 2);
         }
         ElectricVfx.sparks(c.add(0, 0.3, 0), 24, 0.45F, 14, scene.seed + 3);
      } else if (style == WishStyle.TECH || style == WishStyle.CARDS) {
         ElectricVfx.sparks(c.add(0, 1.2, 0), 12, 0.25F, 10, scene.seed + 4);
      }
   }

   // ---------------------------------------------------------------- render

   static void render(BufferBuilder b, Vec3 cam, Camera camera, ClientLevel level, WishScene scene, Vec3 c, float t, boolean glow) {
      if (t < 0 || t > WishTimeline.LENGTH + 2) {
         return;
      }
      WishStyle s = scene.style;
      float e = WishTimeline.energy(t);
      if (glow) {
         base(b, cam, camera, scene, c, t, e);
      }
      float w = window(t);
      switch (s) {
         case TIME -> time(b, cam, camera, c, t, w, glow, s);
         case SHADOW -> shadow(b, cam, camera, scene, c, t, w, glow, s);
         case THUNDER -> thunder(b, cam, camera, c, t, w, glow, s);
         case CARDS -> cards(b, cam, camera, scene, c, t, w, glow, s);
         case ARMOR -> armor(b, cam, camera, scene, c, t, w, glow, s);
         case TECH -> tech(b, cam, camera, scene, level, c, t, w, glow, s);
         case CRIMSON -> crimson(b, cam, camera, level, scene, c, t, w, glow, s);
         case HOLY -> holy(b, cam, camera, scene, c, t, w, glow, s);
         case STILL -> still(b, cam, camera, scene, c, t, w, glow, s);
         case MORTAL -> mortal(b, cam, camera, scene, c, t, w, glow, s);
      }
   }

   /** 0..1 presence of the hero motif: in from the end of the CALL, out over the RESOLVE. */
   static float window(float t) {
      float in = Mth.clamp((t - WishTimeline.CALL_END) / 6.0F, 0, 1);
      float out = 1 - Mth.clamp((t - WishTimeline.FULFIL_END) / (float)(WishTimeline.LENGTH - WishTimeline.FULFIL_END), 0, 1);
      return Math.min(in, out);
   }

   private static void base(BufferBuilder b, Vec3 cam, Camera camera, WishScene scene, Vec3 c, float t, float e) {
      int gold = WishStyle.GOLD;
      int hero = scene.style.primary;
      float radius = 0.8F + 1.3F * e;
      ringH(b, cam, camera, c.add(0, 0.06, 0), radius, 36, t * 0.05F, 0.045F, gold, (int)(210 * e));
      ringH(b, cam, camera, c.add(0, 0.07, 0), radius * 0.62F, 22, -t * 0.08F, 0.035F, gold, (int)(150 * e));
      // runes: brighter dots on the outer ring
      for (int i = 0; i < 6; i++) {
         double a = i * Math.PI / 3 + t * 0.05;
         PixelVfx.crossGlow(b, cam, camera, c.add(Math.cos(a) * radius, 0.08, Math.sin(a) * radius), 0.06F, 255, 230, 160, (int)(200 * e));
      }
      // the spiral of the plea, turning to the hero's colour as the answer comes
      float tint = Mth.clamp((t - WishTimeline.CALL_END) / 16.0F, 0, 1);
      int col = WishScreen.mix(gold, hero, tint * 0.7F);
      int n = 30;
      for (int i = 0; i < n; i++) {
         float u = frac(i / (float)n + t * 0.022F + (scene.seed & 7) * 0.1F);
         double a = i * 2.39996 + t * 0.13;
         double r = 1.15 - 0.65 * u;
         Vec3 p = c.add(Math.cos(a) * r, 0.1 + 2.5 * u, Math.sin(a) * r);
         PixelVfx.billboardPixel(b, cam, camera, p, 0.04F, WishStyle.r(col), WishStyle.g(col), WishStyle.b(col),
            (int)(230 * e * Mth.sin((float)Math.PI * u)));
      }
      // the column of light that answers
      float colK = window(t) * (t < WishTimeline.SWAP ? Mth.clamp((t - WishTimeline.CALL_END) / 12.0F, 0, 1) : 1);
      if (colK > 0.01F) {
         float pulse = 0.85F + 0.15F * Mth.sin(t * 0.6F);
         PixelVfx.beamDots(b, cam, camera, c.add(0, 16, 0), c.add(0, 0.1, 0), 0.32F, 0.05F + 0.07F * colK * pulse, 255, 220, 150, (int)(110 * colK));
         PixelVfx.beamDots(b, cam, camera, c.add(0, 16, 0), c.add(0, 0.1, 0), 0.5F, 0.03F, WishStyle.r(hero), WishStyle.g(hero), WishStyle.b(hero),
            (int)(150 * colK));
      }
      if (t > WishTimeline.CALL_END && e > 0.2F) {
         PixelVfx.bodyShell(b, cam, camera, c.add(0, 1.0, 0), 0.6F, 1.1F, t / 20.0F, WishStyle.r(hero), WishStyle.g(hero), WishStyle.b(hero),
            (int)(170 * e));
      }
      float dt = t - WishTimeline.SWAP;
      if (dt >= 0 && dt < 12) {
         float k = 1 - dt / 12;
         PixelVfx.crossGlow(b, cam, camera, c.add(0, 1.1, 0), 0.5F + 1.6F * (float)Math.sqrt(dt / 12), 255, 240, 210, (int)(255 * k));
         ringH(b, cam, camera, c.add(0, 0.15, 0), 0.6F + 5.5F * (float)Math.sqrt(dt / 12), 56, 0, 0.06F, hero, (int)(230 * k));
      }
   }

   // ---------------------------------------------------------------- motifs

   /** DIO: a clock face whose hands race, then stop dead on the swap. */
   private static void time(BufferBuilder b, Vec3 cam, Camera camera, Vec3 c, float t, float w, boolean glow, WishStyle s) {
      if (!glow || w <= 0) {
         return;
      }
      Vec3 face = c.add(0, 1.05, 0);
      float r = 1.7F;
      int col = t < WishTimeline.SWAP ? s.primary : s.secondary;
      for (int i = 0; i < 12; i++) {
         double a = i * Math.PI / 6;
         PixelVfx.crossGlow(b, cam, camera, face.add(Math.cos(a) * r, 0, Math.sin(a) * r), i % 3 == 0 ? 0.07F : 0.04F, WishStyle.r(col),
            WishStyle.g(col), WishStyle.b(col), (int)(220 * w));
      }
      float spin = Math.min(t, WishTimeline.SWAP) - WishTimeline.CALL_END;
      double minute = spin > 0 ? 0.012 * spin * spin : 0;
      double hour = minute / 12 + 0.6;
      PixelVfx.beamDots(b, cam, camera, face, face.add(Math.cos(minute) * r * 0.9, 0, Math.sin(minute) * r * 0.9), 0.1F, 0.035F, 255, 245, 200,
         (int)(230 * w));
      PixelVfx.beamDots(b, cam, camera, face, face.add(Math.cos(hour) * r * 0.55, 0, Math.sin(hour) * r * 0.55), 0.1F, 0.05F, 255, 245, 200,
         (int)(230 * w));
      if (t >= WishTimeline.SWAP) {
         // the world holds its breath: a pale still sphere
         PixelVfx.sphereShell(b, cam, camera, c.add(0, 1.0, 0), 2.4F, 0, WishStyle.r(s.secondary), WishStyle.g(s.secondary), WishStyle.b(s.secondary),
            (int)(110 * w));
      }
   }

   /** Jinwoo: a pool of darkness, smoke rising from it, and the eyes of the soldiers opening around. */
   private static void shadow(BufferBuilder b, Vec3 cam, Camera camera, WishScene scene, Vec3 c, float t, float w, boolean glow, WishStyle s) {
      if (w <= 0) {
         return;
      }
      if (!glow) {
         disc(b, cam, c.add(0, 0.04, 0), 2.8F * w, 14, 6, 26, (int)(190 * w));
         for (int i = 0; i < 26; i++) {
            float u = frac(hash(scene.seed, i) + t * 0.03F);
            double a = hash(scene.seed, i + 40) * Math.PI * 2;
            double r = 0.6 + 2.0 * hash(scene.seed, i + 80);
            PixelVfx.billboardPixel(b, cam, camera, c.add(Math.cos(a) * r, 0.1 + 1.8 * u, Math.sin(a) * r), 0.08F, 20, 8, 40,
               (int)(170 * w * (1 - u)));
         }
         return;
      }
      for (int i = 0; i < 20; i++) {
         float u = frac(hash(scene.seed, i + 120) + t * 0.035F);
         double a = hash(scene.seed, i + 160) * Math.PI * 2;
         double r = 0.4 + 2.2 * hash(scene.seed, i + 200);
         PixelVfx.billboardPixel(b, cam, camera, c.add(Math.cos(a) * r, 0.1 + 2.2 * u, Math.sin(a) * r), 0.035F, WishStyle.r(s.primary),
            WishStyle.g(s.primary), WishStyle.b(s.primary), (int)(200 * w * Mth.sin((float)Math.PI * u)));
      }
      float eyes = Mth.clamp((t - (WishTimeline.SWAP - 4)) / 6.0F, 0, 1) * w;
      if (eyes > 0) {
         for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5 + 0.3;
            Vec3 head = c.add(Math.cos(a) * 3.2, 1.55 + 0.1 * Math.sin(i * 1.7), Math.sin(a) * 3.2);
            Vec3 side = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(0.09);
            PixelVfx.crossGlow(b, cam, camera, head.add(side), 0.035F, 150, 110, 255, (int)(255 * eyes));
            PixelVfx.crossGlow(b, cam, camera, head.subtract(side), 0.035F, 150, 110, 255, (int)(255 * eyes));
         }
      }
   }

   /** Thor: storm light around the body; the bolt itself is an ElectricVfx one-shot on the swap. */
   private static void thunder(BufferBuilder b, Vec3 cam, Camera camera, Vec3 c, float t, float w, boolean glow, WishStyle s) {
      if (!glow || w <= 0) {
         return;
      }
      float flicker = 0.6F + 0.4F * Mth.sin(t * 2.3F) * Mth.sin(t * 0.7F + 1);
      PixelVfx.crossGlow(b, cam, camera, c.add(0, 2.6, 0), 0.25F + 0.2F * flicker * w, 190, 230, 255, (int)(180 * w * flicker));
      ringH(b, cam, camera, c.add(0, 2.6, 0), 0.8F + 0.4F * flicker, 18, t * 0.3F, 0.03F, s.primary, (int)(200 * w));
   }

   /** Sinclair: a ring of cards closing in, then bursting out like a finale. */
   private static void cards(BufferBuilder b, Vec3 cam, Camera camera, WishScene scene, Vec3 c, float t, float w, boolean glow, WishStyle s) {
      if (w <= 0) {
         return;
      }
      float close = WishScreen.smooth((t - WishTimeline.CALL_END) / (float)(WishTimeline.SWAP - WishTimeline.CALL_END));
      float burst = Math.max(0, t - WishTimeline.SWAP) / 14.0F;
      double radius = t < WishTimeline.SWAP ? 2.4 - 1.7 * close : 0.7 + 5.0 * burst;
      int n = 12;
      for (int i = 0; i < n; i++) {
         double a = i * Math.PI * 2 / n + t * (0.08 + 0.12 * close);
         Vec3 p = c.add(Math.cos(a) * radius, 1.0 + 0.35 * Math.sin(i * 1.3 + t * 0.15) + burst * 0.8 * Math.sin(i), Math.sin(a) * radius);
         if (!glow) {
            PixelVfx.billboardPixel(b, cam, camera, p, 0.11F, 250, 244, 230, (int)(235 * w));
            PixelVfx.billboardPixel(b, cam, camera, p, 0.045F, i % 2 == 0 ? 220 : 30, 30, i % 2 == 0 ? 60 : 40, (int)(250 * w));
         } else {
            PixelVfx.billboardPixel(b, cam, camera, p, 0.17F, WishStyle.r(s.primary), WishStyle.g(s.primary), WishStyle.b(s.primary), (int)(60 * w));
         }
      }
      if (glow && burst > 0 && burst < 1) {
         for (int i = 0; i < 24; i++) {
            double a = hash(scene.seed, i) * Math.PI * 2;
            double up = 0.4 + 1.2 * hash(scene.seed, i + 30);
            Vec3 p = c.add(Math.cos(a) * 3.2 * burst, 1.2 + up * burst * 2 - 2.2 * burst * burst, Math.sin(a) * 3.2 * burst);
            int[] col = i % 3 == 0 ? new int[]{255, 80, 110} : i % 3 == 1 ? new int[]{255, 230, 120} : new int[]{140, 220, 255};
            PixelVfx.crossGlow(b, cam, camera, p, 0.05F, col[0], col[1], col[2], (int)(255 * (1 - burst)));
         }
      }
   }

   /** Hulkbuster: plates fly in from every side and lock onto the body; the ground answers the weight. */
   private static void armor(BufferBuilder b, Vec3 cam, Camera camera, WishScene scene, Vec3 c, float t, float w, boolean glow, WishStyle s) {
      if (w <= 0) {
         return;
      }
      float u = WishScreen.smooth((t - WishTimeline.CALL_END) / (float)(WishTimeline.SWAP - WishTimeline.CALL_END));
      for (int i = 0; i < 22; i++) {
         double a = hash(scene.seed, i) * Math.PI * 2;
         double h = 0.2 + 1.9 * hash(scene.seed, i + 50);
         Vec3 from = c.add(Math.cos(a) * 5.0, h + 2.0 * hash(scene.seed, i + 90), Math.sin(a) * 5.0);
         Vec3 to = c.add(Math.cos(a) * 0.55, h, Math.sin(a) * 0.55);
         float delay = Mth.clamp(u * 1.3F - 0.3F * hash(scene.seed, i + 130), 0, 1);
         Vec3 p = from.lerp(to, WishScreen.smooth(delay));
         float locked = t >= WishTimeline.SWAP ? 1 - Math.min(1, (t - WishTimeline.SWAP) / 6.0F) : 1;
         if (!glow) {
            PixelVfx.billboardPixel(b, cam, camera, p, 0.12F, 150, 30, 20, (int)(235 * w * locked));
            PixelVfx.billboardPixel(b, cam, camera, p, 0.06F, 230, 170, 60, (int)(235 * w * locked));
         } else if (delay < 1) {
            PixelVfx.beamDots(b, cam, camera, p, p.add(from.subtract(to).normalize().scale(0.6)), 0.12F, 0.03F, WishStyle.r(s.primary),
               WishStyle.g(s.primary), WishStyle.b(s.primary), (int)(150 * w));
         }
      }
      float dt = t - WishTimeline.SWAP;
      if (dt >= 0 && dt < 14) {
         float k = dt / 14;
         if (!glow) {
            ringH(b, cam, camera, c.add(0, 0.08, 0), 0.8F + 4.0F * k, 60, 0, 0.09F, 0x8A7A66, (int)(200 * (1 - k)));
         } else {
            ringH(b, cam, camera, c.add(0, 0.12, 0), 0.6F + 4.4F * k, 60, 0, 0.05F, s.secondary, (int)(200 * (1 - k)));
         }
      }
   }

   /** Iron Man: nanites stream out of the arc reactor over the body; the reactor flares on the swap. */
   private static void tech(BufferBuilder b, Vec3 cam, Camera camera, WishScene scene, ClientLevel level, Vec3 c, float t, float w, boolean glow,
      WishStyle s) {
      if (!glow || w <= 0) {
         return;
      }
      Entity entity = level.getEntity(scene.entityId);
      double yaw = Math.toRadians(entity instanceof net.minecraft.world.entity.LivingEntity l ? l.yBodyRot : scene.yaw);
      Vec3 front = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
      Vec3 chest = c.add(0, 1.35, 0).add(front.scale(0.3));
      float pulse = 0.7F + 0.3F * Mth.sin(t * 0.5F);
      float flare = t >= WishTimeline.SWAP ? 1 - Math.min(1, (t - WishTimeline.SWAP) / 10.0F) : 0;
      PixelVfx.crossGlow(b, cam, camera, chest, 0.07F + 0.05F * pulse + 0.35F * flare, 170, 245, 255, (int)(255 * w));
      for (int i = 0; i < 44; i++) {
         float p = WishScreen.smooth((t - 18 - i * 0.45F) / 9.0F);
         if (p <= 0) {
            continue;
         }
         double a = hash(scene.seed, i) * Math.PI * 2;
         double h = 0.1 + 1.8 * hash(scene.seed, i + 60);
         Vec3 target = c.add(Math.cos(a) * 0.42, h, Math.sin(a) * 0.42);
         Vec3 pos = chest.lerp(target, p);
         PixelVfx.billboardPixel(b, cam, camera, pos, 0.03F, WishStyle.r(s.primary), WishStyle.g(s.primary), WishStyle.b(s.primary), (int)(220 * w));
      }
      if (flare > 0) {
         PixelVfx.bodyShell(b, cam, camera, c.add(0, 1.0, 0), 0.5F, 1.05F, t / 20.0F, WishStyle.r(s.secondary), WishStyle.g(s.secondary),
            WishStyle.b(s.secondary), (int)(220 * flare));
      }
   }

   /** Homelander: the eyes heat up, two beams cut the air, a red shock ring on the swap. */
   private static void crimson(BufferBuilder b, Vec3 cam, Camera camera, ClientLevel level, WishScene scene, Vec3 c, float t, float w, boolean glow,
      WishStyle s) {
      if (!glow || w <= 0) {
         return;
      }
      Entity entity = level.getEntity(scene.entityId);
      if (entity == null) {
         return;
      }
      Vec3 eye = entity.getEyePosition();
      Vec3 look = entity.getViewVector(1.0F);
      Vec3 side = look.cross(new Vec3(0, 1, 0));
      side = side.lengthSqr() < 1.0E-6 ? new Vec3(0.1, 0, 0) : side.normalize().scale(0.1);
      float heat = Mth.clamp((t - 20) / 14.0F, 0, 1) * w;
      for (Vec3 e : new Vec3[]{eye.add(side).add(look.scale(0.25)), eye.subtract(side).add(look.scale(0.25))}) {
         PixelVfx.crossGlow(b, cam, camera, e, 0.03F + 0.06F * heat, 255, 60, 40, (int)(255 * heat));
         if (t > 32 && t < WishTimeline.SWAP + 4) {
            float beam = Math.min(1, (t - 32) / 3.0F) * w;
            Vec3 end = e.add(look.scale(9.0));
            PixelVfx.beamDots(b, cam, camera, e, end, 0.12F, 0.06F, 255, 40, 30, (int)(220 * beam));
            PixelVfx.beamDots(b, cam, camera, e, end, 0.2F, 0.025F, 255, 230, 210, (int)(240 * beam));
         }
      }
      float dt = t - WishTimeline.SWAP;
      if (dt >= 0 && dt < 12) {
         float k = dt / 12;
         ringH(b, cam, camera, c.add(0, 1.0, 0), 0.8F + 6.0F * k, 64, 0, 0.07F, s.primary, (int)(220 * (1 - k)));
         PixelVfx.beamDots(b, cam, camera, c.add(0, 0.2, 0), c.add(0, 2.5 + 10 * k, 0), 0.25F, 0.08F, 255, 70, 50, (int)(200 * (1 - k)));
      }
   }

   /** Reinhard: feathers of light falling, a halo over the head, and the holy sword's pillar on the swap. */
   private static void holy(BufferBuilder b, Vec3 cam, Camera camera, WishScene scene, Vec3 c, float t, float w, boolean glow, WishStyle s) {
      if (!glow || w <= 0) {
         return;
      }
      for (int i = 0; i < 28; i++) {
         float u = frac(hash(scene.seed, i) + t * 0.018F);
         double a = hash(scene.seed, i + 30) * Math.PI * 2 + u * 2;
         double r = 0.5 + 1.8 * hash(scene.seed, i + 60);
         PixelVfx.billboardPixel(b, cam, camera, c.add(Math.cos(a) * r, 4.2 - 4.0 * u, Math.sin(a) * r), 0.035F, 255, 250, 235,
            (int)(220 * w * Mth.sin((float)Math.PI * u)));
      }
      ringH(b, cam, camera, c.add(0, 2.25, 0), 0.32F, 16, t * 0.1F, 0.025F, s.primary, (int)(230 * w));
      float dt = t - WishTimeline.SWAP;
      if (dt >= -3) {
         float k = dt < 0 ? (dt + 3) / 3 : Math.max(0, 1 - dt / 16);
         for (int i = -1; i <= 1; i++) {
            Vec3 off = new Vec3(i * 0.14, 0, 0);
            PixelVfx.beamDots(b, cam, camera, c.add(0, 24, 0).add(off), c.add(off), 0.22F, 0.07F + 0.06F * k, 255, 245, 210, (int)(230 * k * w));
         }
         PixelVfx.crossGlow(b, cam, camera, c.add(0, 0.1, 0), 0.5F + 1.2F * k, WishStyle.r(s.secondary), WishStyle.g(s.secondary),
            WishStyle.b(s.secondary), (int)(200 * k * w));
      }
   }

   /** Regulus: shards of stopped time circle him, slow down and freeze where they are. */
   private static void still(BufferBuilder b, Vec3 cam, Camera camera, WishScene scene, Vec3 c, float t, float w, boolean glow, WishStyle s) {
      if (w <= 0) {
         return;
      }
      float slow = WishScreen.smooth((t - WishTimeline.CALL_END) / (float)(WishTimeline.SWAP - WishTimeline.CALL_END));
      double turn = Math.min(t, WishTimeline.SWAP) * 0.09 * (1 - 0.5 * slow);
      for (int i = 0; i < 18; i++) {
         double a = hash(scene.seed, i) * Math.PI * 2 + turn;
         double h = 0.2 + 2.0 * hash(scene.seed, i + 40);
         double r = 1.6 + 0.4 * hash(scene.seed, i + 80);
         Vec3 p = c.add(Math.cos(a) * r, h, Math.sin(a) * r);
         if (!glow) {
            PixelVfx.billboardPixel(b, cam, camera, p, 0.07F, 200, 210, 225, (int)(200 * w));
         } else {
            float twinkle = 0.5F + 0.5F * Mth.sin(t * 0.4F + i);
            PixelVfx.crossGlow(b, cam, camera, p, 0.04F, WishStyle.r(s.primary), WishStyle.g(s.primary), WishStyle.b(s.primary),
               (int)(220 * w * twinkle));
         }
      }
      float dt = t - WishTimeline.SWAP;
      if (glow && dt >= 0 && dt < 16) {
         PixelVfx.sphereShell(b, cam, camera, c.add(0, 1.0, 0), 1.3F, 0, 235, 242, 255, (int)(200 * (1 - dt / 16)));
      }
   }

   /** The return: the gold leaves the body and rises away, gently. */
   private static void mortal(BufferBuilder b, Vec3 cam, Camera camera, WishScene scene, Vec3 c, float t, float w, boolean glow, WishStyle s) {
      if (!glow) {
         return;
      }
      float lift = Mth.clamp((t - (WishTimeline.SWAP - 6)) / 22.0F, 0, 1);
      if (lift <= 0 || lift >= 1) {
         return;
      }
      for (int i = 0; i < 36; i++) {
         double a = hash(scene.seed, i) * Math.PI * 2;
         double r = 0.3 + 0.3 * hash(scene.seed, i + 40);
         double h = 0.2 + 1.6 * hash(scene.seed, i + 80);
         float u = Mth.clamp(lift * 1.4F - 0.4F * hash(scene.seed, i + 120), 0, 1);
         Vec3 p = c.add(Math.cos(a) * r * (1 + u), h + 6.0 * u * u, Math.sin(a) * r * (1 + u));
         PixelVfx.billboardPixel(b, cam, camera, p, 0.04F, 255, 232, 180, (int)(230 * (1 - u)));
      }
   }

   // ---------------------------------------------------------------- helpers

   /** Horizontal ring of pixels around {@code center}. */
   static void ringH(BufferBuilder b, Vec3 cam, Camera camera, Vec3 center, float radius, int n, float phase, float size, int rgb, int alpha) {
      if (alpha <= 2 || radius <= 0.01F) {
         return;
      }
      alpha = Math.min(255, alpha);
      for (int i = 0; i < n; i++) {
         double a = phase + i * Math.PI * 2 / n;
         PixelVfx.billboardPixel(b, cam, camera, center.add(Math.cos(a) * radius, 0, Math.sin(a) * radius), size, WishStyle.r(rgb), WishStyle.g(rgb),
            WishStyle.b(rgb), alpha);
      }
   }

   /** Flat dark disc on the ground (camera-relative fan of quads). */
   static void disc(BufferBuilder b, Vec3 cam, Vec3 c, float r, int red, int green, int blue, int alpha) {
      if (alpha <= 2 || r <= 0.01F) {
         return;
      }
      int seg = 20;
      float cx = (float)(c.x - cam.x);
      float cy = (float)(c.y - cam.y);
      float cz = (float)(c.z - cam.z);
      for (int i = 0; i < seg; i++) {
         double a0 = i * Math.PI * 2 / seg;
         double a1 = (i + 1) * Math.PI * 2 / seg;
         b.vertex(cx, cy, cz).color(red, green, blue, alpha).endVertex();
         b.vertex(cx + (float)Math.cos(a0) * r, cy, cz + (float)Math.sin(a0) * r).color(red, green, blue, 0).endVertex();
         b.vertex(cx + (float)Math.cos(a1) * r, cy, cz + (float)Math.sin(a1) * r).color(red, green, blue, 0).endVertex();
         b.vertex(cx, cy, cz).color(red, green, blue, alpha).endVertex();
      }
   }

   static float frac(float v) {
      return v - (float)Math.floor(v);
   }

   /** Deterministic 0..1 per (seed, index): every client draws the same scene. */
   static float hash(long seed, int i) {
      long x = seed * 0x9E3779B97F4A7C15L + i * 0xBF58476D1CE4E5B9L;
      x ^= x >>> 31;
      x *= 0x94D049BB133111EBL;
      x ^= x >>> 29;
      return (x >>> 40) / (float)(1L << 24);
   }
}
