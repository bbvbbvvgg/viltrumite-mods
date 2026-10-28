package dev.baranhan.viltrumitecore.client.jinwoo;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.client.render.vfx.SkyTint;
import dev.baranhan.viltrumitecore.client.render.vfx.SoundDuck;
import dev.baranhan.viltrumitecore.entity.jinwoo.ShadowKind;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooMove;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * «Армия теней, восстань!» on the client: the owner's three-shot cinematic through a client-only
 * camera entity (low frontal, side tracking over the rising army, high crane behind for the attack),
 * letterbox, FOV lean and shake for observers within 40 blocks, the sky darkening (SkyTint), other
 * sounds ducked (SoundDuck), and up to {@link JinwooRules#ULT_GHOSTS} light ghost soldiers (no
 * entities) rising, charging and dissolving. Client only.
 */
public final class JinwooUltScene {
   static final int BLEND = 6;
   private static final Object TINT = new Object();
   private static final Object DUCK = new Object();
   private static final Map<Integer, Scene> SCENES = new HashMap<>();
   @Nullable
   private static Marker camera;
   @Nullable
   private static CameraType savedType;
   private static float letterbox;
   private static float letterboxO;
   private static float lean;

   static final class Scene {
      final int caster;
      final long start;
      final Vec3 origin;
      final float yaw;
      final List<GhostSoldier> ghosts = new ArrayList<>();
      boolean waveShaken;

      Scene(int caster, long start, Vec3 origin, float yaw) {
         this.caster = caster;
         this.start = start;
         this.origin = origin;
         this.yaw = yaw;
      }
   }

   record GhostSoldier(ShadowKind kind, Vec3 pos, float yaw, int riseAt, int dissolveAt, float charge) {
   }

   private JinwooUltScene() {
   }

   public static boolean cameraActive() {
      Minecraft mc = Minecraft.getInstance();
      return camera != null && mc.getCameraEntity() == camera;
   }

   // ---------------------------------------------------------------- tick

   static void tick(Minecraft mc) {
      ClientLevel level = mc.level;
      long now = level.getGameTime();
      Set<Integer> seen = new HashSet<>();
      Scene own = null;
      Scene nearest = null;
      double best = Double.MAX_VALUE;
      for (Player p : level.players()) {
         HeroPublicSnapshot s = JinwooView.of(p);
         if (s == null || JinwooView.move(s) != JinwooMove.ARISE) {
            continue;
         }
         seen.add(p.getId());
         Scene scene = SCENES.get(p.getId());
         if (scene == null || now - scene.start > JinwooRules.ULT_LENGTH + 5) {
            scene = new Scene(p.getId(), now - s.actionElapsed(), p.position(), p.getYRot());
            spawnGhosts(scene, level, p.getId());
            SCENES.put(p.getId(), scene);
         }
         int t = (int)(now - scene.start);
         if (!scene.waveShaken && t >= JinwooRules.ULT_WAVE) {
            scene.waveShaken = true;
            CameraShake.cinematicAt(scene.origin, 2.2F, JinwooRules.ULT_OBSERVER_RANGE);
         }
         if (p == mc.player) {
            own = scene;
         }
         double d = mc.player == null ? 0 : mc.player.distanceToSqr(p);
         if (d < JinwooRules.ULT_OBSERVER_RANGE * JinwooRules.ULT_OBSERVER_RANGE && d < best) {
            best = d;
            nearest = scene;
         }
      }
      // scenes whose cast ended keep their ghosts until they dissolved
      SCENES.values().removeIf(sc -> !seen.contains(sc.caster) && now - sc.start > JinwooRules.ULT_LENGTH + 4
         || !seen.contains(sc.caster) && now - sc.start < JinwooRules.ULT_RISE_START);
      letterboxO = letterbox;
      boolean watching = nearest != null;
      letterbox += ((watching ? 1 : 0) - letterbox) * 0.25F;
      lean = nearest == null || own != null ? 0 : envelope((int)(now - nearest.start));
      if (watching) {
         SkyTint.INSTANCE.push(TINT, 120, () -> new SkyTint.Tint(0.62F, 0.5F, 0.95F, 0.55F, 1.0F));
         if (!SoundDuck.INSTANCE.holds(DUCK)) {
            SoundDuck.INSTANCE.duck(DUCK, SoundSource.MASTER, 0.3F, 10, jinwooSounds());
         }
      } else {
         SkyTint.INSTANCE.remove(TINT);
         if (SoundDuck.INSTANCE.holds(DUCK)) {
            SoundDuck.INSTANCE.release(DUCK, 20);
         }
      }
      if (own != null && mc.player != null && mc.player.isAlive()) {
         updateCamera(mc, own, (int)(now - own.start));
      } else {
         releaseCamera(mc);
      }
   }

   private static Set<ResourceLocation> jinwooSounds() {
      Set<ResourceLocation> set = new HashSet<>();
      for (ResourceLocation id : ForgeRegistries.SOUND_EVENTS.getKeys()) {
         if (id.getNamespace().equals(JinwooView.NS) && id.getPath().startsWith("jinwoo.")) {
            set.add(id);
         }
      }
      return set;
   }

   static float envelope(int t) {
      if (t < 0 || t > JinwooRules.ULT_LENGTH) {
         return 0;
      }
      return Math.min(1, t / 10.0F) * Math.min(1, (JinwooRules.ULT_LENGTH - t) / 10.0F);
   }

   private static void spawnGhosts(Scene scene, ClientLevel level, int seed) {
      Random rng = new Random(seed * 31L + scene.start);
      double yawRad = Math.toRadians(scene.yaw);
      for (int i = 0; i < JinwooRules.ULT_GHOSTS; i++) {
         // a wide crescent behind and beside the Monarch
         double side = (i % 2 == 0 ? 1 : -1) * (0.25 + (i / 2) / 18.0 * 1.25) * Math.PI / 2;
         double ang = yawRad + Math.PI + side * 1.1;
         double r = 3.5 + (i % 4) * 2.2 + rng.nextDouble() * 1.4;
         double x = scene.origin.x - Math.sin(ang) * r;
         double z = scene.origin.z + Math.cos(ang) * r;
         float ground = JinwooVfx.Domain.surface(level, Mth.floor(x), Mth.floor(scene.origin.y), Mth.floor(z));
         double y = Float.isNaN(ground) ? scene.origin.y : ground;
         ShadowKind kind = i % 9 == 4 ? ShadowKind.KNIGHT : i % 7 == 3 ? ShadowKind.INSECT : i % 11 == 6 ? ShadowKind.BEAST : ShadowKind.INFANTRY;
         int rise = JinwooRules.ULT_RISE_START + (int)((i * 7 % JinwooRules.ULT_GHOSTS) / (float)JinwooRules.ULT_GHOSTS * 16);
         int dissolve = JinwooRules.ULT_WAVE_END + 1 + rng.nextInt(10);
         scene.ghosts.add(new GhostSoldier(kind, new Vec3(x, y, z), scene.yaw + (float)rng.nextGaussian() * 6, rise, dissolve,
            5.0F + rng.nextFloat() * 3.0F));
      }
   }

   // ---------------------------------------------------------------- camera

   private static void updateCamera(Minecraft mc, Scene scene, int t) {
      if (t >= JinwooRules.ULT_LENGTH) {
         releaseCamera(mc);
         return;
      }
      if (camera == null || camera.level() != mc.level) {
         camera = new Marker(EntityType.MARKER, mc.level);
      }
      if (mc.getCameraEntity() != camera) {
         savedType = mc.options.getCameraType();
         mc.options.setCameraType(CameraType.FIRST_PERSON);
         mc.setCameraEntity(camera);
         Vec3[] shot = shot(scene, t);
         place(mc, camera, shot[0], shot[1], true);
      }
      Vec3[] shot = shot(scene, t + 1);
      place(mc, camera, shot[0], shot[1], false);
   }

   private static void place(Minecraft mc, Marker cam, Vec3 pos, Vec3 look, boolean snap) {
      BlockHitResult hit = mc.level.clip(new ClipContext(look, pos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, cam));
      if (hit.getType() == HitResult.Type.BLOCK) {
         Vec3 dir = pos.subtract(look).normalize();
         pos = hit.getLocation().subtract(dir.scale(0.3));
      }
      Vec3 d = look.subtract(pos);
      float yaw = (float)(Mth.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0F;
      float pitch = (float)(-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * (180.0 / Math.PI)));
      if (snap) {
         cam.setPos(pos);
         cam.xo = pos.x;
         cam.yo = pos.y;
         cam.zo = pos.z;
         cam.setYRot(yaw);
         cam.setXRot(pitch);
         cam.yRotO = yaw;
         cam.xRotO = pitch;
         return;
      }
      cam.xo = cam.getX();
      cam.yo = cam.getY();
      cam.zo = cam.getZ();
      cam.yRotO = cam.getYRot();
      cam.xRotO = cam.getXRot();
      cam.setPos(pos);
      cam.setYRot(cam.yRotO + Mth.wrapDegrees(yaw - cam.yRotO));
      cam.setXRot(pitch);
   }

   /** Pure-ish: [camera position, look target] of the owner's shot at ult tick t (blended at the cuts). */
   static Vec3[] shot(Scene scene, int t) {
      Vec3[] cur = rawShot(scene, t);
      for (int cut : new int[]{JinwooRules.ULT_RISE_START, JinwooRules.ULT_WAVE}) {
         if (t >= cut && t < cut + BLEND) {
            Vec3[] prev = rawShot(scene, cut - 1);
            float w = (float)JinwooPose.smooth((t - cut) / (double)BLEND);
            return new Vec3[]{prev[0].lerp(cur[0], w), prev[1].lerp(cur[1], w)};
         }
      }
      return cur;
   }

   private static Vec3[] rawShot(Scene scene, int t) {
      double yaw = Math.toRadians(scene.yaw);
      Vec3 f = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
      Vec3 r = new Vec3(-f.z, 0, f.x);
      Vec3 p = scene.origin;
      if (t < JinwooRules.ULT_RISE_START) {
         double u = t / (double)JinwooRules.ULT_RISE_START;
         // low frontal: slow push-in on the face, looking up
         return new Vec3[]{p.add(f.scale(3.2 - 0.6 * u)).add(r.scale(0.7)).add(0, 0.55, 0), p.add(0, 1.6, 0)};
      }
      if (t < JinwooRules.ULT_WAVE) {
         double u = (t - JinwooRules.ULT_RISE_START) / (double)(JinwooRules.ULT_WAVE - JinwooRules.ULT_RISE_START);
         double a = Math.toRadians(60 + 70 * u);
         Vec3 dir = f.scale(Math.cos(a)).add(r.scale(Math.sin(a)));
         return new Vec3[]{p.add(dir.scale(7.0)).add(0, 1.3 + 0.9 * u, 0), p.add(0, 1.2, 0).add(f.scale(-1.5))};
      }
      double u = Math.min(1, (t - JinwooRules.ULT_WAVE) / (double)(JinwooRules.ULT_LENGTH - JinwooRules.ULT_WAVE));
      return new Vec3[]{p.subtract(f.scale(6.5 + 2.0 * u)).add(0, 5.0 + 1.5 * u, 0), p.add(f.scale(9.0)).add(0, 0.5, 0)};
   }

   static void releaseCamera(Minecraft mc) {
      if (camera != null && mc.getCameraEntity() == camera && mc.player != null) {
         mc.setCameraEntity(mc.player);
         if (savedType != null) {
            mc.options.setCameraType(savedType);
         }
      }
      savedType = null;
      camera = null;
   }

   // ---------------------------------------------------------------- render

   /** The local player is not drawn by the level while another camera entity is active: draw it here. */
   static void renderModels(PoseStack stack, MultiBufferSource.BufferSource buffers, Vec3 cam, float partial) {
      Minecraft mc = Minecraft.getInstance();
      if (cameraActive() && mc.player != null) {
         AbstractClientPlayer p = mc.player;
         Vec3 at = p.getPosition(partial);
         var dispatcher = mc.getEntityRenderDispatcher();
         dispatcher.render(p, at.x - cam.x, at.y - cam.y, at.z - cam.z, Mth.lerp(partial, p.yRotO, p.getYRot()), partial, stack, buffers,
            dispatcher.getPackedLightCoords(p, partial));
      }
      if (SCENES.isEmpty() || mc.level == null) {
         return;
      }
      long now = mc.level.getGameTime();
      for (Scene scene : SCENES.values()) {
         float t = now - scene.start + partial;
         if (scene.origin.distanceToSqr(cam) > 80 * 80) {
            continue;
         }
         for (GhostSoldier g : scene.ghosts) {
            float rise = t - g.riseAt;
            if (rise < 0) {
               continue;
            }
            float dissolve = t > g.dissolveAt ? t - g.dissolveAt : -1;
            if (dissolve > JinwooRules.SHADOW_DISSOLVE) {
               continue;
            }
            BakedGeoModel model = ShadowSoldierRenderer.model(g.kind);
            if (model == null) {
               continue;
            }
            float wave = Mth.clamp((t - JinwooRules.ULT_WAVE) / (JinwooRules.ULT_WAVE_END - JinwooRules.ULT_WAVE), 0, 1);
            float charge = (float)JinwooPose.smooth(wave) * g.charge;
            boolean attacking = t >= JinwooRules.ULT_WAVE && t < JinwooRules.ULT_WAVE_END + 4;
            double yawRad = Math.toRadians(g.yaw);
            Vec3 pos = g.pos.add(-Math.sin(yawRad) * charge, 0, Math.cos(yawRad) * charge);
            ShadowSoldierRenderer.pose(model, g.kind, t, attacking ? 0.9F : 0.0F, t / 8.0, attacking ? (t - JinwooRules.ULT_WAVE) % 16 : 1000,
               false, rise);
            float height = g.kind == ShadowKind.BEAST ? 1.5F : 2.0F;
            float sink = ShadowSoldierRenderer.sink(rise, dissolve, height);
            float alpha = dissolve >= 0 ? 1 - (float)JinwooPose.smooth(dissolve / JinwooRules.SHADOW_DISSOLVE) : 0.92F;
            stack.pushPose();
            stack.translate(pos.x - cam.x, pos.y - cam.y - sink, pos.z - cam.z);
            stack.mulPose(Axis.YP.rotationDegrees(180.0F - g.yaw));
            double dist = pos.distanceTo(cam);
            ShadowSoldierRenderer.draw(model, g.kind, false, stack, buffers, net.minecraft.client.renderer.LightTexture.pack(12, 12), OverlayTexture.NO_OVERLAY,
               Math.min(alpha, 0.92F), dist < ShadowSoldierRenderer.GLOW_LOD ? ShadowSoldierRenderer.eyes(rise - 18, false, true) : 0);
            stack.popPose();
         }
      }
   }

   /** Additive extras of the scene: the Monarch's eye flare in the call. */
   static void renderGlow(BufferBuilder b, Vec3 cam, Camera camera, float partial) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      long now = mc.level.getGameTime();
      for (Scene scene : SCENES.values()) {
         float t = now - scene.start + partial;
         if (t > 4 && t < JinwooRules.ULT_RISE_START + 6) {
            float k = (float)Math.sin(Math.PI * Mth.clamp((t - 4) / (JinwooRules.ULT_RISE_START + 2), 0, 1));
            var e = mc.level.getEntity(scene.caster);
            if (e != null) {
               Vec3 eye = e.getEyePosition(partial);
               PixelVfx.crossGlow(b, cam, camera, eye, 0.25F + 0.35F * k, 120, 190, 255, (int)(200 * k));
            }
         }
      }
   }

   public static void onRenderGui(RenderGuiEvent.Pre event) {
      float k = Mth.lerp(event.getPartialTick(), letterboxO, letterbox);
      if (k < 0.01F) {
         return;
      }
      var g = event.getGuiGraphics();
      int w = g.guiWidth(), h = g.guiHeight();
      int bar = Math.round(h * 0.11F * k);
      g.fill(0, 0, w, bar, 0xFF000000);
      g.fill(0, h - bar, w, h, 0xFF000000);
   }

   public static void onFov(ViewportEvent.ComputeFov event) {
      if (cameraActive()) {
         event.setFOV(64.0);
      } else if (lean > 0.01F) {
         event.setFOV(event.getFOV() * (1.0 - 0.08 * lean));
      }
   }

   public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
      if (lean > 0.01F && !cameraActive()) {
         event.setRoll(event.getRoll() + 2.5F * lean);
      }
   }

   static void clear() {
      SCENES.clear();
      releaseCamera(Minecraft.getInstance());
      SkyTint.INSTANCE.remove(TINT);
      SoundDuck.INSTANCE.release(DUCK);
      letterbox = letterboxO = lean = 0;
   }
}
