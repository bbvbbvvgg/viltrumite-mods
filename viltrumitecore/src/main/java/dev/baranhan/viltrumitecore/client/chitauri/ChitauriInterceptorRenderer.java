package dev.baranhan.viltrumitecore.client.chitauri;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriInterceptor;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Interceptor skiff: the flight state picks the base clip (cruise, hover, boost, brake, the evasive roll, perched,
 * take-off, the burning fall) and the previous state's clip fades out under it, so state changes blend; the bank comes
 * from the synced roll, the yaw rate layers the turn clips, acceleration pitches the nacelles; the twin cannon recoil
 * and the hit jolt are one-shots. Exhaust glows at both nozzles, longer with speed and on boost, sputtering while it
 * falls. Client only.
 */
public class ChitauriInterceptorRenderer extends EntityRenderer<ChitauriInterceptor> {
   static final float PIVOT_Y = 0.5F;

   static final class Track {
      byte cur = -1;
      byte prev = -1;
      int changed = -1000;
      int prevStart;
      float turn;
      int seenTick = -1;
      int fxTick = -1;
   }

   private static final Map<ChitauriInterceptor, Track> TRACKS = new WeakHashMap<>();
   private final Matrix4f inverse = new Matrix4f();
   private final Vector4f point = new Vector4f();
   private final Vector3f[] nozzles = {new Vector3f(), new Vector3f()};
   private final boolean[] found = new boolean[2];

   public ChitauriInterceptorRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 0.6F;
   }

   @Override
   public ResourceLocation getTextureLocation(ChitauriInterceptor entity) {
      return ChitauriAssets.INTERCEPTOR_TEX;
   }

   @Override
   public void render(ChitauriInterceptor e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      BakedGeoModel model = AnimCache.model(ChitauriAssets.INTERCEPTOR_GEO);
      if (model == null) {
         return;
      }
      ChitauriPortalClient.Plane portal = ChitauriPortalClient.plane();
      if (portal != null && !portal.inFront(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()) + PIVOT_Y,
            Mth.lerp(partial, e.zo, e.getZ()), 1.2)) {
         return;
      }
      float age = e.tickCount + partial;
      Track t = track(e);
      float roll = Mth.lerp(partial, e.clientRollO, e.clientRoll);
      boolean falling = e.state() == ChitauriInterceptor.FALLING || !e.isAlive();
      float dying = e.deathTime > 0 ? e.deathTime - 1 + partial : -1.0F;
      pose(model, e, t, age, partial);
      float alpha = dying < 0 ? 1.0F : 1.0F - ChitauriSoldierRenderer.smooth((dying - (ChitauriInterceptor.DEATH_TICKS - 12)) / 10.0F);
      if (alpha <= 0.01F) {
         return;
      }
      this.inverse.set(stack.last().pose()).invert();
      this.found[0] = this.found[1] = false;
      stack.pushPose();
      stack.translate(0.0, PIVOT_Y, 0.0);
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partial, e.yRotO, e.getYRot())));
      stack.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial, e.xRotO, e.getXRot())));
      float spin = falling && dying >= 0 ? dying * 11.0F : 0.0F;
      stack.mulPose(Axis.ZP.rotationDegrees(-roll + spin));
      stack.translate(0.0, -PIVOT_Y, 0.0);
      int overlay = OverlayTexture.pack(OverlayTexture.u(0.0F), OverlayTexture.v(e.hurtTime > 0));
      RenderType type = alpha < 0.999F ? RenderType.entityTranslucent(ChitauriAssets.INTERCEPTOR_TEX) : RenderType.entityCutoutNoCull(ChitauriAssets.INTERCEPTOR_TEX);
      AnimRenderer.render(model, stack, buffers, buffers.getBuffer(type), light, overlay, 1.0F, 1.0F, 1.0F, alpha, this::hook);
      boolean shadowPass = ShaderCompat.isShadowPass();
      float speed = Mth.lerp(partial, e.clientSpeedO, e.clientSpeed);
      if (!shadowPass) {
         float g = engineGlow(speed, e.state(), falling, age, e.getId()) * alpha;
         AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(ChitauriAssets.INTERCEPTOR_GLOW)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, g, g, g, 1.0F, null);
      }
      stack.popPose();
      if (!shadowPass) {
         this.exhaust(e, stack, buffers, speed, falling, age, alpha, partial);
      }
      super.render(e, yaw, partial, stack, buffers, light);
   }

   private void hook(GeoBone bone, PoseStack stack, @Nullable MultiBufferSource buffers, int light, int overlay) {
      int i = "thrust_r".equals(bone.name) ? 0 : "thrust_l".equals(bone.name) ? 1 : -1;
      if (i >= 0 && !this.found[i]) {
         this.point.set(bone.pivotX / 16.0F, bone.pivotY / 16.0F, bone.pivotZ / 16.0F, 1.0F);
         stack.last().pose().transform(this.point);
         this.inverse.transform(this.point);
         this.nozzles[i].set(this.point.x, this.point.y, this.point.z);
         this.found[i] = true;
      }
   }

   /** Nozzle glows (camera-facing, so they read from any side) and, on boost, real exhaust sparks once a tick. */
   private void exhaust(ChitauriInterceptor e, PoseStack stack, MultiBufferSource buffers, float speed, boolean falling, float age, float alpha, float partial) {
      Camera camera = this.entityRenderDispatcher.camera;
      Vector3f left = camera.getLeftVector();
      Vector3f up = camera.getUpVector();
      VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
      Matrix4f m = stack.last().pose();
      boolean boost = e.state() == ChitauriInterceptor.BOOST;
      float power = thrustPower(speed, e.state(), falling, age, e.getId()) * alpha;
      Track t = track(e);
      boolean emit = boost && t.fxTick != e.tickCount;
      Vec3 base = new Vec3(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()), Mth.lerp(partial, e.zo, e.getZ()));
      float yaw = e.getYRot() * Mth.DEG_TO_RAD;
      Vec3 back = new Vec3(Mth.sin(yaw), 0.0, -Mth.cos(yaw));
      for (int i = 0; i < 2; i++) {
         if (!this.found[i] || power <= 0.02F) {
            continue;
         }
         Vector3f n = this.nozzles[i];
         float flick = 0.85F + 0.15F * Mth.sin(age * 2.9F + i * 1.7F);
         GlowDraw.billboard(vc, m, n, left, up, 0.42F * power * flick, 0.2F, 70, 140, 255, (int)(140 * Math.min(1.0F, power)));
         GlowDraw.billboard(vc, m, n, left, up, 0.2F * power * flick, 0.55F, 210, 235, 255, (int)(230 * Math.min(1.0F, power)));
         if (emit) {
            ChitauriVfx.thrust(base.add(n.x, n.y, n.z), back.add(0, -0.1, 0), 1.0F + power * 0.4F);
         }
      }
      if (emit) {
         t.fxTick = e.tickCount; // sparks once a tick, not once a frame
      }
   }

   static Track track(ChitauriInterceptor e) {
      Track t = TRACKS.computeIfAbsent(e, k -> new Track());
      if (t.seenTick != e.tickCount) {
         t.seenTick = e.tickCount;
         byte s = e.state();
         if (s != t.cur) {
            t.prev = t.cur;
            t.prevStart = t.changed;
            t.cur = s;
            t.changed = e.tickCount;
         }
         float rate = Mth.wrapDegrees(e.getYRot() - e.yRotO);
         t.turn += (Mth.clamp(rate / ChitauriInterceptor.MAX_TURN, -1.0F, 1.0F) - t.turn) * 0.3F;
      }
      return t;
   }

   /** Base clip of a flight state. */
   static String clipOf(byte state) {
      return switch (state) {
         case ChitauriInterceptor.HOVER -> "hover";
         case ChitauriInterceptor.BOOST -> "boost";
         case ChitauriInterceptor.BRAKE -> "brake";
         case ChitauriInterceptor.ROLLING -> "maneuver";
         case ChitauriInterceptor.PERCH -> "perch";
         case ChitauriInterceptor.TAKEOFF -> "takeoff";
         case ChitauriInterceptor.FALLING -> "death_fall";
         default -> "cruise";
      };
   }

   private static void state(BakedGeoModel model, byte state, float since, float age, float weight) {
      Animation clip = AnimCache.animation(ChitauriAssets.INTERCEPTOR_ANIM, clipOf(state));
      if (clip == null || weight <= 0.01F) {
         return;
      }
      double sec = since / 20.0;
      double len = clip.lengthSeconds();
      double at = clip.loopType() == Animation.LoopType.LOOP ? (age / 20.0) % len : Math.min(sec, len - 0.001);
      AnimationController.overlay(clip, model, at, weight);
   }

   static void pose(BakedGeoModel model, ChitauriInterceptor e, Track t, float age, float partial) {
      model.resetBones();
      byte cur = t.cur < 0 ? e.state() : t.cur;
      float since = age - t.changed;
      float blend = ChitauriSoldierRenderer.smooth(since / 6.0F);
      if (t.prev >= 0 && blend < 1.0F) {
         state(model, t.prev, age - t.prevStart, age, 1.0F);
      }
      state(model, cur, since, age, t.prev >= 0 ? blend : 1.0F);
      if (cur == ChitauriInterceptor.BRAKE || cur == ChitauriInterceptor.TAKEOFF) {
         // after a one-shot the hover hold takes over
         Animation clip = AnimCache.animation(ChitauriAssets.INTERCEPTOR_ANIM, clipOf(cur));
         if (clip != null && since > clip.lengthSeconds() * 20.0F) {
            state(model, ChitauriInterceptor.HOVER, since, age, ChitauriSoldierRenderer.smooth((float)((since - clip.lengthSeconds() * 20.0) / 6.0)));
         }
      }
      boolean flying = cur != ChitauriInterceptor.PERCH && cur != ChitauriInterceptor.FALLING;
      if (flying) {
         float turnW = Math.abs(t.turn);
         Animation turn = AnimCache.animation(ChitauriAssets.INTERCEPTOR_ANIM, t.turn > 0 ? "turn_r" : "turn_l");
         if (turn != null && turnW > 0.05F) {
            AnimationController.overlay(turn, model, (age / 20.0) % turn.lengthSeconds(), Math.min(1.0F, turnW));
         }
         Animation accel = AnimCache.animation(ChitauriAssets.INTERCEPTOR_ANIM, "accel");
         float aw = Mth.clamp(e.clientAccel / 0.6F, 0.0F, 1.0F);
         if (accel != null && aw > 0.03F) {
            AnimationController.overlay(accel, model, 0.12, aw);
         }
      }
      ChitauriEliteRenderer.once(model, AnimCache.animation(ChitauriAssets.INTERCEPTOR_ANIM, "fire"), age - e.clientShootStart, 0.001F, 0.06F, 1.0F);
      Animation hit = AnimCache.animation(ChitauriAssets.INTERCEPTOR_ANIM, "hit");
      if (hit != null && e.hurtTime > 0) {
         AnimationController.additive(hit, model, Math.min(hit.lengthSeconds(), (10 - e.hurtTime + partial) / 20.0), 1.0F);
      }
   }

   /** Pure: hull light / glow pass: idle hum, brighter with speed and on boost, failing while it falls. */
   static float engineGlow(float speed, byte state, boolean falling, float age, int id) {
      float g = 0.7F + Mth.clamp(speed / 0.9F, 0.0F, 1.0F) * 0.25F + 0.03F * Mth.sin(age * 0.8F + id);
      if (state == ChitauriInterceptor.BOOST) {
         g += 0.15F;
      }
      if (state == ChitauriInterceptor.PERCH) {
         g *= 0.6F;
      }
      if (falling) {
         g *= ((int)(age * 2.3F + id) & 3) == 0 ? 0.2F : 0.65F;
      }
      return Mth.clamp(g, 0.0F, 1.0F);
   }

   /** Pure: nozzle glow size factor (0 = off): idles low when perched, grows with speed, peaks on boost. */
   static float thrustPower(float speed, byte state, boolean falling, float age, int id) {
      float p = switch (state) {
         case ChitauriInterceptor.PERCH -> 0.15F;
         case ChitauriInterceptor.BOOST -> 1.6F;
         case ChitauriInterceptor.TAKEOFF -> 1.2F;
         case ChitauriInterceptor.HOVER, ChitauriInterceptor.BRAKE -> 0.8F;
         default -> 0.55F + Mth.clamp(speed / 0.9F, 0.0F, 1.0F) * 0.6F;
      };
      if (falling) {
         p *= ((int)(age * 3.1F + id) % 3) == 0 ? 0.1F : 0.6F;
      }
      return p;
   }
}
