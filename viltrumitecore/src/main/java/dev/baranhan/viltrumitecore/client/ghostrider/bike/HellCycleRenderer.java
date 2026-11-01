package dev.baranhan.viltrumitecore.client.ghostrider.bike;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.chitauri.GlowDraw;
import dev.baranhan.viltrumitecore.client.ghostrider.GhostRiderAssets;
import dev.baranhan.viltrumitecore.client.ghostrider.HellfireDraw;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleEntity;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleRules;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
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

/**
 * The Hell Cycle (model tools/assets/ghost_rider_bike.py): frame pitched by the ground and the stoppie / wheelie lifts,
 * leaned round the tyre contact line, fork steered round its raked axis, wheels spun by the distance covered, the
 * kickstand down while parked. Every client draws the hellfire the same way: burning tyres whose flames stream back
 * with the speed, exhaust jets that grow with throttle and revs (and flash on backfires), the skull lamp's eyes, and
 * the whole machine engulfed while it forms and burns out. Client only.
 */
public class HellCycleRenderer extends EntityRenderer<HellCycleEntity> {
   /** Baked model axes: blocks, +X = the bike's right, front = -Z (JSON x negated). */
   static final float AXLE_Y = 6.0F / 16.0F;
   static final float AXLE_Z = 14.4F / 16.0F;
   static final float TYRE_R = 6.0F / 16.0F;
   static final float[][] TIPS = {{4.0F / 16.0F, 7.4F / 16.0F, 18.1F / 16.0F}, {4.4F / 16.0F, 10.4F / 16.0F, 17.7F / 16.0F}};
   static final float LAMP_X = 1.0F / 16.0F;
   static final float LAMP_Y = 17.9F / 16.0F;
   static final float LAMP_Z = -12.4F / 16.0F;
   /** The raked steering axis crosses the lamp's height here. */
   static final float LAMP_AXIS_Z = -7.0F / 16.0F;
   static final float COS_RAKE = Mth.cos(32.0F * Mth.DEG_TO_RAD);
   /** Parked lean onto the kickstand (left = positive). */
   static final float PARK_LEAN = 8.0F;
   private static final int[] WHEEL_ANGLES = {-100, -75, -50, -25, 0, 25, 50, 75, 100};
   private final Matrix4f bike = new Matrix4f();
   private final Vector3f p = new Vector3f();
   private final Vector3f q = new Vector3f();
   private final Vector3f up = new Vector3f();
   private final Vector3f dir = new Vector3f();
   private final Vector3f back = new Vector3f();

   public HellCycleRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 0.75F;
   }

   @Override
   public ResourceLocation getTextureLocation(HellCycleEntity entity) {
      return GhostRiderAssets.BIKE_TEX;
   }

   @Override
   public void render(HellCycleEntity e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      BakedGeoModel model = AnimCache.model(GhostRiderAssets.BIKE_GEO);
      if (model == null) {
         return;
      }
      float age = e.tickCount + partial;
      float form = smooth(e.formProgress(partial));
      float burn = e.burning() ? e.burnProgress(partial) : 0.0F;
      float alpha = form * (1.0F - burn);
      float park = Mth.lerp(partial, e.parkO, e.park);
      float steer = Mth.lerp(partial, e.steerO, e.steer);
      float speed = (float)e.visualSpeed;
      float speedF = Mth.clamp(speed / (float)HellCycleRules.MAX_SPEED, 0.0F, 1.6F);
      boolean drift = e.flag(HellCycleEntity.FLAG_DRIFT);

      this.pose(e, partial, form, burn, park);
      poseBones(model, steerDegrees(steer, speedF, drift), Mth.lerp(partial, e.wheelSpinO, e.wheelSpin), park);

      stack.pushPose();
      stack.mulPoseMatrix(this.bike);
      VertexConsumer body = alpha >= 0.995F
         ? buffers.getBuffer(RenderType.entityCutoutNoCull(GhostRiderAssets.BIKE_TEX))
         : buffers.getBuffer(RenderType.entityTranslucent(GhostRiderAssets.BIKE_TEX));
      AnimRenderer.render(model, stack, null, body, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha, null);
      boolean shadowPass = ShaderCompat.isShadowPass();
      if (!shadowPass && alpha > 0.02F) {
         float g = glow(e.rpm, age, e.getId()) * alpha;
         AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(GhostRiderAssets.BIKE_GLOW)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, g, g, g, 1.0F, null);
      }
      stack.popPose();

      if (!shadowPass) {
         Camera camera = this.entityRenderDispatcher.camera;
         Vec3 cam = camera.getPosition();
         float cx = (float)(cam.x - Mth.lerp(partial, e.xo, e.getX()));
         float cy = (float)(cam.y - Mth.lerp(partial, e.yo, e.getY()));
         float cz = (float)(cam.z - Mth.lerp(partial, e.zo, e.getZ()));
         Matrix4f view = stack.last().pose();
         float heat = heat(speedF, e.dashing(), drift, form, burn);
         float throttle = Mth.clamp(e.throttle, 0.0F, 1.0F);
         float exhaust = exhaustLength(throttle, e.rpm, HellCycleClient.backfire(e, partial), age, e.getId());
         float steerYaw = steerDegrees(steer, speedF, drift) * COS_RAKE;
         VertexConsumer fire = buffers.getBuffer(HellfireDraw.type());
         this.wheelFire(fire, view, 0.0F, -AXLE_Z, steerYaw, heat, speedF, age, e.getId(), cx, cy, cz, alpha);
         this.wheelFire(fire, view, 0.0F, AXLE_Z, 0.0F, heat, speedF, age, e.getId() + 31, cx, cy, cz, alpha);
         this.exhaustFire(fire, view, exhaust, age, e.getId(), cx, cy, cz, alpha);
         if (form < 1.0F || burn > 0.0F) {
            this.engulf(fire, view, burn > 0.0F ? burn * 1.4F : 1.0F - form, age, e.getId(), cx, cy, cz);
         }
         VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
         this.glows(glow, view, camera, steerYaw, heat, exhaust, alpha, age, e.getId());
      }
      super.render(e, yaw, partial, stack, buffers, light);
   }

   /** Model transform at the entity origin: sink while forming / burning, yaw, ground pitch, lifts, lean. */
   private void pose(HellCycleEntity e, float partial, float form, float burn, float park) {
      float yaw = Mth.rotLerp(partial, e.yRotO, e.getYRot());
      float pitch = Mth.lerp(partial, e.pitchO, e.pitch);
      float lift = Mth.lerp(partial, e.liftO, e.lift);
      float lean = Mth.lerp(partial, e.leanO, e.lean) + PARK_LEAN * park;
      float sink = -0.3F * (1.0F - form) - 0.35F * burn * burn;
      Matrix4f m = this.bike.identity();
      m.translate(0.0F, sink, 0.0F);
      m.rotateY((180.0F - yaw) * Mth.DEG_TO_RAD);
      m.rotateX(pitch * Mth.DEG_TO_RAD);
      if (lift > 0.0F) {
         // stoppie: nose down round the front tyre's contact
         m.translate(0.0F, 0.0F, -AXLE_Z).rotateX(-lift * Mth.DEG_TO_RAD).translate(0.0F, 0.0F, AXLE_Z);
      } else if (lift < 0.0F) {
         // wheelie: nose up round the rear tyre's contact
         m.translate(0.0F, 0.0F, AXLE_Z).rotateX(-lift * Mth.DEG_TO_RAD).translate(0.0F, 0.0F, -AXLE_Z);
      }
      m.rotateZ(lean * Mth.DEG_TO_RAD);
   }

   static void poseBones(BakedGeoModel model, float steerDeg, float spin, float park) {
      model.resetBones();
      deg(model.getBone("fork_steer"), 0.0F, -steerDeg, 0.0F);
      deg(model.getBone("front_wheel"), spin, 0.0F, 0.0F);
      deg(model.getBone("rear_wheel"), spin, 0.0F, 0.0F);
      // the stand swings from folded along the frame (rest pose) down and out onto the ground
      deg(model.getBone("kickstand"), -90.0F * park, 0.0F, -28.0F * park);
   }

   static void deg(@Nullable GeoBone bone, float x, float y, float z) {
      if (bone != null) {
         bone.rotX += x * -Mth.DEG_TO_RAD;
         bone.rotY += y * -Mth.DEG_TO_RAD;
         bone.rotZ += z * Mth.DEG_TO_RAD;
      }
   }

   /** Pure: the drawn steering angle (deg, left positive): wide at walking pace, a few degrees at speed, countersteer in a drift. */
   static float steerDegrees(float steer, float speedF, boolean drift) {
      if (drift) {
         return -steer * 14.0F;
      }
      return steer * Mth.lerp(Mth.clamp(speedF, 0.0F, 1.0F), 26.0F, 7.0F);
   }

   /** Pure: brightness of the glow pass (fins, veins, eyes, tank flames): idle breathing, hotter with revs. */
   static float glow(float rpm, float age, int id) {
      return Mth.clamp(0.62F + 0.38F * Mth.clamp(rpm, 0.0F, 1.0F) + 0.05F * Mth.sin(age * 0.35F + id), 0.0F, 1.0F);
   }

   /** Pure: tyre fire strength (0.3 parked .. 1 at top speed, more boosting / drifting / forming). */
   static float heat(float speedF, boolean dashing, boolean drift, float form, float burn) {
      float h = 0.3F + 0.7F * Mth.clamp(speedF, 0.0F, 1.0F);
      if (dashing) {
         h += 0.45F;
      }
      if (drift) {
         h += 0.2F;
      }
      return h + (1.0F - form) * 0.6F + burn * 0.8F;
   }

   /** Pure: exhaust jet length in blocks from throttle, revs and a backfire flash (0..1). */
   static float exhaustLength(float throttle, float rpm, float backfire, float age, int id) {
      float base = 0.12F + 0.38F * throttle + 0.3F * Mth.clamp(rpm, 0.0F, 1.0F);
      return (base + 0.9F * backfire) * HellfireDraw.flicker(age * 1.6F, id);
   }

   /** Flames standing round the upper tyre, swept back by the speed (the fire does not turn with the wheel). */
   private void wheelFire(VertexConsumer vc, Matrix4f view, float ax, float az, float steerYaw, float heat, float speedF, float age, int seed,
         float cx, float cy, float cz, float alpha) {
      this.bike.transformDirection(this.back.set(0.0F, 0.0F, 1.0F));
      this.up.set(0.0F, 1.0F, 0.0F).add(this.back.x * (0.15F + 1.5F * speedF), 0.0F, this.back.z * (0.15F + 1.5F * speedF)).normalize();
      float sin = Mth.sin(steerYaw * Mth.DEG_TO_RAD);
      float cos = Mth.cos(steerYaw * Mth.DEG_TO_RAD);
      int count = heat < 0.45F ? 5 : WHEEL_ANGLES.length;
      for (int i = 0; i < count; i++) {
         int k = count == WHEEL_ANGLES.length ? i : i * 2;
         float phi = WHEEL_ANGLES[k] * Mth.DEG_TO_RAD;
         float ly = AXLE_Y + TYRE_R * Mth.cos(phi);
         float dz = -TYRE_R * Mth.sin(phi);
         // the front wheel's plane turns with the steering (approximated round the vertical through the axle)
         float lx = ax + dz * sin;
         float lz = az + dz * cos;
         this.bike.transformPosition(this.p.set(lx, ly, lz));
         float top = Mth.cos(phi);                 // 1 at the top of the tyre, lower towards the ground
         float h = (0.16F + 0.42F * heat) * (0.55F + 0.45F * top) * HellfireDraw.flicker(age, seed + k * 7);
         float w = 0.2F + 0.12F * Math.min(heat, 1.2F);
         int a = (int)(235 * alpha * Mth.clamp(0.5F + heat, 0.0F, 1.0F));
         HellfireDraw.flame(vc, view, this.p.x, this.p.y, this.p.z, this.up, cx - this.p.x, cy - this.p.y, cz - this.p.z, w, h,
            HellfireDraw.frame(age, seed + k * 3), HellfireDraw.TONGUE, 255, 255, 255, a);
      }
   }

   /** Sprite jets out of both drag pipes, pointing back and a little up. */
   private void exhaustFire(VertexConsumer vc, Matrix4f view, float length, float age, int seed, float cx, float cy, float cz, float alpha) {
      this.bike.transformDirection(this.dir.set(0.0F, 0.2F, 1.0F).normalize());
      for (int i = 0; i < TIPS.length; i++) {
         float[] t = TIPS[i];
         this.bike.transformPosition(this.p.set(t[0], t[1], t[2]));
         float l = length * (i == 0 ? 1.0F : 0.85F);
         HellfireDraw.flame(vc, view, this.p.x, this.p.y, this.p.z, this.dir, cx - this.p.x, cy - this.p.y, cz - this.p.z, 0.17F + 0.1F * l,
            0.12F + l, HellfireDraw.frame(age * 1.5F, seed + i * 5), HellfireDraw.TONGUE, 255, 255, 255, (int)(240 * alpha));
      }
   }

   /** The machine wrapped in hellfire while it forms or burns out. */
   private void engulf(VertexConsumer vc, Matrix4f view, float strength, float age, int seed, float cx, float cy, float cz) {
      float s = Mth.clamp(strength, 0.0F, 1.3F);
      if (s <= 0.02F) {
         return;
      }
      this.up.set(0.0F, 1.0F, 0.0F);
      for (int i = 0; i < 7; i++) {
         float z = -0.95F + i * 0.32F;
         for (int side = -1; side <= 1; side += 2) {
            this.bike.transformPosition(this.p.set(side * 0.1F, 0.05F, z));
            float h = (0.6F + 0.9F * s) * HellfireDraw.flicker(age, seed + i * 11 + side);
            HellfireDraw.flame(vc, view, this.p.x, this.p.y, this.p.z, this.up, cx - this.p.x, cy - this.p.y, cz - this.p.z, 0.55F + 0.3F * s, h,
               HellfireDraw.frame(age, seed + i * 5 + side), HellfireDraw.BLAZE, 255, 255, 255, (int)(230 * Math.min(1.0F, s)));
         }
      }
   }

   /** Additive glows: hot tyres, exhaust nozzles and the skull lamp's eyes. */
   private void glows(VertexConsumer vc, Matrix4f view, Camera camera, float steerYaw, float heat, float exhaust, float alpha, float age, int id) {
      Vector3f left = camera.getLeftVector();
      Vector3f camUp = camera.getUpVector();
      float pulse = 0.85F + 0.15F * Mth.sin(age * 0.7F + id);
      for (int w = -1; w <= 1; w += 2) {
         this.bike.transformPosition(this.p.set(0.0F, AXLE_Y, w * AXLE_Z));
         GlowDraw.billboard(vc, view, this.p, left, camUp, TYRE_R * 1.55F, 0.2F, 255, 110, 30, (int)(Math.min(1.0F, heat) * 90 * alpha * pulse));
      }
      for (float[] t : TIPS) {
         this.bike.transformPosition(this.p.set(t[0], t[1], t[2]));
         GlowDraw.billboard(vc, view, this.p, left, camUp, 0.12F + 0.12F * exhaust, 0.3F, 255, 150, 50, (int)(170 * alpha));
      }
      float sin = Mth.sin(steerYaw * Mth.DEG_TO_RAD);
      float cos = Mth.cos(steerYaw * Mth.DEG_TO_RAD);
      float dz = LAMP_Z - LAMP_AXIS_Z;
      for (int s = -1; s <= 1; s += 2) {
         float x = s * LAMP_X;
         // round the steering axis: x' = x cos + dz sin, z' = -x sin + dz cos (front = -Z, left = -X)
         float lx = x * cos + dz * sin;
         float lz = LAMP_AXIS_Z - x * sin + dz * cos;
         this.bike.transformPosition(this.q.set(lx, LAMP_Y, lz));
         GlowDraw.billboard(vc, view, this.q, left, camUp, 0.11F, 0.35F, 255, 190, 70, (int)(210 * alpha * pulse));
      }
   }

   static float smooth(float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }
}
