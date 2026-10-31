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
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriSoldier;
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
import org.joml.Vector4f;

/**
 * Chitauri soldier: idle → walk → run by the smoothed ground speed (gait phase from the entity, so the feet do not
 * slide), falling and landing after a drop, upper-body aim / shot / melee overlays, an additive hurt flinch, the death
 * or the hive-severed collapse (both keep the root), and the mounted poses (pilot at the controls, gunner holding on
 * and firing). The head and chest follow the look; the rifle's energy cell glows, brightens while charging, and a
 * charge orb grows at the muzzle. The rendered muzzle is handed to the entity for the shot flash. Client only.
 */
public class ChitauriSoldierRenderer extends EntityRenderer<ChitauriSoldier> {
   public static final double GLOW_LOD = 96.0;
   private final Matrix4f inverse = new Matrix4f();
   private final Vector4f point = new Vector4f();
   private final Vector3f muzzle = new Vector3f();
   private boolean muzzleFound;

   public ChitauriSoldierRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 0.45F;
   }

   @Override
   public ResourceLocation getTextureLocation(ChitauriSoldier entity) {
      return ChitauriAssets.SOLDIER_TEX;
   }

   @Override
   public void render(ChitauriSoldier e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      BakedGeoModel model = AnimCache.model(ChitauriAssets.SOLDIER_GEO);
      if (model == null) {
         return;
      }
      // riders of a chariot still inside the portal tube stay hidden with it
      ChitauriPortalClient.Plane portal = ChitauriPortalClient.plane();
      if (portal != null && e.getVehicle() != null && !portal.inFront(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()) + 1.0,
            Mth.lerp(partial, e.zo, e.getZ()), 1.6)) {
         return;
      }
      float age = e.tickCount + partial;
      float dying = e.deathTime > 0 ? e.deathTime - 1 + partial : -1.0F;
      pose(model, e, partial, age, dying);
      float alpha = dying < 0 ? 1.0F : 1.0F - smooth((dying - (ChitauriSoldier.DEATH_TICKS - 14)) / 12.0F);
      if (alpha <= 0.01F) {
         return;
      }
      this.inverse.set(stack.last().pose()).invert();
      this.muzzleFound = false;
      stack.pushPose();
      float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
      int overlay = OverlayTexture.pack(OverlayTexture.u(0.0F), OverlayTexture.v(e.hurtTime > 0));
      RenderType type = alpha < 0.999F ? RenderType.entityTranslucent(ChitauriAssets.SOLDIER_TEX) : RenderType.entityCutoutNoCull(ChitauriAssets.SOLDIER_TEX);
      AnimRenderer.render(model, stack, buffers, buffers.getBuffer(type), light, overlay, 1.0F, 1.0F, 1.0F, alpha, this::hook);
      boolean shadowPass = ShaderCompat.isShadowPass();
      double dist = Math.sqrt(this.entityRenderDispatcher.distanceToSqr(e));
      if (!shadowPass && dist <= GLOW_LOD) {
         float g = glow(e, age, dying) * alpha;
         if (g > 0.01F) {
            AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(ChitauriAssets.SOLDIER_GLOW)), LightTexture.FULL_BRIGHT,
               OverlayTexture.NO_OVERLAY, g, g, g, 1.0F, null);
         }
      }
      stack.popPose();
      if (this.muzzleFound) {
         double x = Mth.lerp(partial, e.xo, e.getX());
         double y = Mth.lerp(partial, e.yo, e.getY());
         double z = Mth.lerp(partial, e.zo, e.getZ());
         if (!shadowPass) {
            e.clientMuzzle = new Vec3(x + this.muzzle.x, y + this.muzzle.y, z + this.muzzle.z);
            e.clientMuzzleTick = e.tickCount;
         }
         float charge = charge(e, age);
         if (!shadowPass && charge > 0.0F && dying < 0) {
            this.chargeOrb(stack, buffers, charge, age);
         }
      }
      super.render(e, yaw, partial, stack, buffers, light);
   }

   private void hook(GeoBone bone, PoseStack stack, @Nullable MultiBufferSource buffers, int light, int overlay) {
      if (!this.muzzleFound && "muzzle".equals(bone.name)) {
         this.point.set(bone.pivotX / 16.0F, bone.pivotY / 16.0F, bone.pivotZ / 16.0F, 1.0F);
         stack.last().pose().transform(this.point);
         this.inverse.transform(this.point);
         this.muzzle.set(this.point.x, this.point.y, this.point.z);
         this.muzzleFound = true;
      }
   }

   /** Energy gathering at the muzzle while the rifle charges: a pulsing core and a wide soft halo. */
   private void chargeOrb(PoseStack stack, MultiBufferSource buffers, float charge, float age) {
      Camera camera = this.entityRenderDispatcher.camera;
      Vector3f left = camera.getLeftVector();
      Vector3f up = camera.getUpVector();
      VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
      Matrix4f m = stack.last().pose();
      float pulse = 0.85F + 0.15F * Mth.sin(age * 1.7F);
      float core = (0.035F + 0.07F * charge) * pulse;
      GlowDraw.billboard(vc, m, this.muzzle, left, up, core * 4.2F, 0.15F, 70, 140, 255, (int)(110 * charge));
      GlowDraw.billboard(vc, m, this.muzzle, left, up, core * 1.6F, 0.45F, 150, 205, 255, (int)(200 * charge));
      GlowDraw.billboard(vc, m, this.muzzle, left, up, core, 0.7F, 235, 245, 255, (int)(255 * charge));
   }

   /** Pure: rifle charge 0..1 (grows over a second while the flag is up, gone after the shot). */
   static float charge(ChitauriSoldier e, float age) {
      if (!e.charging()) {
         return 0.0F;
      }
      float since = age - e.clientChargeStart;
      if (age - e.clientShootStart < 3.0F) {
         return 0.0F;
      }
      return Mth.clamp(since / 20.0F, 0.15F, 1.0F);
   }

   /** Pure: glow intensity: a slow pulse, brighter while charging and on the shot, dying out with the soldier. */
   static float glow(ChitauriSoldier e, float age, float dying) {
      float g = 0.78F + 0.1F * Mth.sin(age * 0.13F + e.getId());
      if (e.charging()) {
         g += 0.2F * (0.5F + 0.5F * Mth.sin(age * 0.9F));
      }
      float sinceShot = age - e.clientShootStart;
      if (sinceShot >= 0.0F && sinceShot < 4.0F) {
         g += 0.35F * (1.0F - sinceShot / 4.0F);
      }
      if (dying >= 0.0F) {
         float out = e.severed() ? 14.0F : 34.0F;
         float flicker = ((int)(age * 1.7F + e.getId()) & 3) == 0 ? 0.35F : 1.0F;
         g *= (1.0F - smooth(dying / out)) * flicker;
      }
      return Mth.clamp(g, 0.0F, 1.0F);
   }

   /** Clip pose for this frame (see the class comment for the layering). */
   static void pose(BakedGeoModel model, ChitauriSoldier e, float partial, float age, float dying) {
      ResourceLocation anim = ChitauriAssets.SOLDIER_ANIM;
      ResourceLocation states = ChitauriAssets.SOLDIER_STATES;
      model.resetBones();
      Animation idle = AnimCache.animation(anim, "idle");
      if (idle != null) {
         AnimationController.overlay(idle, model, (age / 20.0 + e.getId() * 0.31) % idle.lengthSeconds(), 1.0F);
      }
      if (dying >= 0.0F) {
         Animation clip = AnimCache.animation(states, e.severed() ? "sever" : "death");
         if (clip != null) {
            AnimationController.overlay(clip, model, Math.min(dying / 20.0, clip.lengthSeconds()), smooth(dying / 3.0F));
         }
         return;
      }
      boolean mounted = e.mounted();
      boolean pilot = mounted && e.role() == ChitauriSoldier.ROLE_PILOT;
      if (mounted) {
         Animation clip = AnimCache.animation(states, pilot ? "mounted_pilot" : "mounted");
         if (clip != null) {
            AnimationController.overlay(clip, model, (age / 20.0 + e.getId() * 0.17) % clip.lengthSeconds(), 1.0F);
         }
      } else {
         float move = Mth.lerp(partial, e.clientMoveO, e.clientMove);
         double gait = Mth.lerp(partial, e.clientGaitO, e.clientGait);
         double phase = gait - Math.floor(gait);
         float walkW = ChitauriSoldier.walkWeight(move);
         float runW = ChitauriSoldier.runWeight(move);
         Animation walk = AnimCache.animation(anim, "walk");
         Animation run = AnimCache.animation(anim, "run");
         if (walk != null && walkW > 0.01F) {
            AnimationController.overlay(walk, model, phase * walk.lengthSeconds(), walkW);
         }
         if (run != null && walkW * runW > 0.01F) {
            AnimationController.overlay(run, model, phase * run.lengthSeconds(), walkW * runW);
         }
         float air = Mth.clamp((e.clientAirTicks + partial - 3.0F) / 5.0F, 0.0F, 1.0F);
         Animation fall = AnimCache.animation(states, "fall");
         if (fall != null && air > 0.01F) {
            AnimationController.overlay(fall, model, (age / 20.0) % fall.lengthSeconds(), air);
         }
         Animation land = AnimCache.animation(states, "land");
         float sinceLand = age - e.clientLandStart;
         if (land != null && sinceLand >= 0.0F && sinceLand < land.lengthSeconds() * 20.0F) {
            double sec = sinceLand / 20.0;
            AnimationController.overlay(land, model, sec, smooth((float)((land.lengthSeconds() - sec) / 0.15)));
         }
      }
      // upper body: aim hold, the shot, the rifle-butt strike
      float sinceMelee = age - e.clientMeleeStart;
      Animation melee = AnimCache.animation(anim, "melee");
      boolean meleeOn = !mounted && melee != null && sinceMelee >= 0.0F && sinceMelee < melee.lengthSeconds() * 20.0F;
      float aim = Mth.lerp(partial, e.clientAimO, e.clientAim);
      if (!pilot) {
         Animation hold = mounted ? AnimCache.animation(states, "mounted_shoot") : AnimCache.animation(anim, "aim");
         if (hold != null && aim > 0.01F && !meleeOn) {
            double sec = mounted ? 0.02 : (age / 20.0) % hold.lengthSeconds();
            AnimationController.overlay(hold, model, sec, aim);
         }
         Animation shot = mounted ? hold : AnimCache.animation(anim, "shoot");
         float sinceShot = age - e.clientShootStart;
         if (shot != null && sinceShot >= 0.0F && sinceShot < shot.lengthSeconds() * 20.0F) {
            double sec = sinceShot / 20.0;
            AnimationController.overlay(shot, model, sec, smooth((float)((shot.lengthSeconds() - sec) / 0.08)) * Math.max(aim, 0.6F));
         }
      }
      if (meleeOn) {
         double sec = sinceMelee / 20.0;
         AnimationController.overlay(melee, model, sec, smooth((float)(sec / 0.05)) * smooth((float)((melee.lengthSeconds() - sec) / 0.1)));
      }
      Animation hurt = AnimCache.animation(anim, "hurt");
      if (hurt != null && e.hurtTime > 0) {
         AnimationController.additive(hurt, model, Math.min(hurt.lengthSeconds(), (10 - e.hurtTime + partial) / 20.0), 1.0F);
      }
      // look: the head turns, the chest carries the rifle with the aim pitch
      float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
      float headYaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - bodyYaw), -55.0F, 55.0F);
      float pitch = Mth.clamp(Mth.lerp(partial, e.xRotO, e.getXRot()), -45.0F, 45.0F);
      float carry = pilot ? 0.0F : aim * 0.55F;
      deg(model.getBone("torso"), 0.0F, headYaw * 0.15F, 0.0F);
      deg(model.getBone("chest"), pitch * carry, headYaw * 0.2F, 0.0F);
      deg(model.getBone("head"), pitch * (1.0F - carry) * 0.8F, headYaw * 0.65F, 0.0F);
   }

   /** Adds Blockbench-convention degrees (the clip convention) to a bone. */
   static void deg(@Nullable GeoBone bone, float x, float y, float z) {
      if (bone != null) {
         bone.rotX += (float)Math.toRadians(-x);
         bone.rotY += (float)Math.toRadians(-y);
         bone.rotZ += (float)Math.toRadians(z);
      }
   }

   static float smooth(float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }
}
