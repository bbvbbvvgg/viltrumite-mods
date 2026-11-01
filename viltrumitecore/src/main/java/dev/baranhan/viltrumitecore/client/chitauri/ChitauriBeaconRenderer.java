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
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriBeacon;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriMob;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import java.util.List;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Field beacon: unfolds out of the drop (legs, mast and rings rising), then idles with its counter-rotating rings; it
 * shudders when hit and folds down when destroyed. The stronghold variant keeps its restraint arms and cage folded
 * away (hidden); the containment variant opens them over the holding field, and a soft energy tether runs from the
 * emitter to every captive floating in its field. Client only.
 */
public class ChitauriBeaconRenderer extends EntityRenderer<ChitauriBeacon> {
   private static final String[] CONTAINMENT_BONES = {"cage", "arm1", "arm2", "arm3", "arm4"};
   private final Matrix4f inverse = new Matrix4f();
   private final Vector4f point = new Vector4f();
   private final Vector3f emitter = new Vector3f();
   private boolean emitterFound;

   public ChitauriBeaconRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 0.7F;
   }

   @Override
   public ResourceLocation getTextureLocation(ChitauriBeacon entity) {
      return ChitauriAssets.BEACON_TEX;
   }

   @Override
   public void render(ChitauriBeacon e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      BakedGeoModel model = AnimCache.model(ChitauriAssets.BEACON_GEO);
      if (model == null) {
         return;
      }
      float age = e.tickCount + partial;
      float dying = e.deathTime > 0 ? e.deathTime - 1 + partial : -1.0F;
      pose(model, e, age, partial, dying);
      float alpha = dying < 0 ? 1.0F : 1.0F - ChitauriSoldierRenderer.smooth((dying - 14.0F) / 6.0F);
      if (alpha <= 0.01F) {
         return;
      }
      this.inverse.set(stack.last().pose()).invert();
      this.emitterFound = false;
      stack.pushPose();
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot)));
      int overlay = OverlayTexture.pack(OverlayTexture.u(0.0F), OverlayTexture.v(e.hurtTime > 0));
      RenderType type = alpha < 0.999F ? RenderType.entityTranslucent(ChitauriAssets.BEACON_TEX) : RenderType.entityCutoutNoCull(ChitauriAssets.BEACON_TEX);
      AnimRenderer.render(model, stack, buffers, buffers.getBuffer(type), light, overlay, 1.0F, 1.0F, 1.0F, alpha, this::hook);
      boolean shadowPass = ShaderCompat.isShadowPass();
      if (!shadowPass) {
         float g = glow(e.tickCount, age, e.held(), dying, e.getId()) * alpha;
         AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(ChitauriAssets.BEACON_GLOW)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, g, g, g, 1.0F, null);
      }
      stack.popPose();
      if (!shadowPass && this.emitterFound && dying < 0 && e.containment() && e.held() > 0) {
         this.tethers(e, stack, buffers, partial, age);
      }
      super.render(e, yaw, partial, stack, buffers, light);
   }

   private void hook(GeoBone bone, PoseStack stack, @Nullable MultiBufferSource buffers, int light, int overlay) {
      if (!this.emitterFound && "emitter".equals(bone.name)) {
         this.point.set(bone.pivotX / 16.0F, bone.pivotY / 16.0F, bone.pivotZ / 16.0F, 1.0F);
         stack.last().pose().transform(this.point);
         this.inverse.transform(this.point);
         this.emitter.set(this.point.x, this.point.y, this.point.z);
         this.emitterFound = true;
      }
   }

   /** Tethers to the people held in the field (the ones floating there: no gravity, not Chitauri, not players). */
   private void tethers(ChitauriBeacon e, PoseStack stack, MultiBufferSource buffers, float partial, float age) {
      double bx = Mth.lerp(partial, e.xo, e.getX());
      double by = Mth.lerp(partial, e.yo, e.getY());
      double bz = Mth.lerp(partial, e.zo, e.getZ());
      List<LivingEntity> held = e.level().getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(ChitauriBeacon.FIELD_RADIUS + 1.2, 2.0,
         ChitauriBeacon.FIELD_RADIUS + 1.2), p -> p.isAlive() && p.isNoGravity() && !(p instanceof Player) && !ChitauriMob.is(p));
      if (held.isEmpty()) {
         return;
      }
      Camera camera = this.entityRenderDispatcher.camera;
      Vec3 cam = camera.getPosition();
      VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
      Matrix4f m = stack.last().pose();
      Vector3f to = new Vector3f();
      Vector3f toCam = new Vector3f();
      int n = 0;
      for (LivingEntity p : held) {
         if (n++ >= 12) {
            break;
         }
         float px = (float)(Mth.lerp(partial, p.xo, p.getX()) - bx);
         float py = (float)(Mth.lerp(partial, p.yo, p.getY()) - by + p.getBbHeight() * 0.55);
         float pz = (float)(Mth.lerp(partial, p.zo, p.getZ()) - bz);
         to.set(px, py, pz);
         toCam.set((float)(cam.x - bx) - (this.emitter.x + px) * 0.5F, (float)(cam.y - by) - (this.emitter.y + py) * 0.5F,
            (float)(cam.z - bz) - (this.emitter.z + pz) * 0.5F);
         float flick = 0.8F + 0.2F * Mth.sin(age * 0.9F + p.getId());
         GlowDraw.ribbon(vc, m, this.emitter, to, toCam, 0.16F, 0.3F, 60, 230, 200, (int)(150 * flick), (int)(90 * flick));
         GlowDraw.ribbon(vc, m, this.emitter, to, toCam, 0.05F, 0.1F, 205, 255, 245, (int)(230 * flick), (int)(160 * flick));
      }
   }

   static void pose(BakedGeoModel model, ChitauriBeacon e, float age, float partial, float dying) {
      model.resetBones();
      Animation deploy = AnimCache.animation(ChitauriAssets.BEACON_ANIM, "deploy");
      Animation idle = AnimCache.animation(ChitauriAssets.BEACON_ANIM, "idle");
      float deployTicks = deploy == null ? ChitauriBeacon.DEPLOY_TICKS : (float)(deploy.lengthSeconds() * 20.0);
      if (idle != null) {
         float w = ChitauriSoldierRenderer.smooth((age - deployTicks + 6.0F) / 6.0F);
         AnimationController.overlay(idle, model, (age / 20.0 + e.getId() * 0.41) % idle.lengthSeconds(), Math.max(0.001F, w));
      }
      if (deploy != null && age < deployTicks) {
         AnimationController.overlay(deploy, model, age / 20.0, 1.0F - ChitauriSoldierRenderer.smooth((age - deployTicks + 6.0F) / 6.0F));
      }
      Animation hurt = AnimCache.animation(ChitauriAssets.BEACON_ANIM, "hurt");
      if (hurt != null && e.hurtTime > 0 && dying < 0) {
         AnimationController.additive(hurt, model, Math.min(hurt.lengthSeconds(), (10 - e.hurtTime + partial) / 20.0), 1.0F);
      }
      Animation down = AnimCache.animation(ChitauriAssets.BEACON_ANIM, "down");
      if (down != null && dying >= 0.0F) {
         AnimationController.overlay(down, model, Math.min(dying / 20.0, down.lengthSeconds()), ChitauriSoldierRenderer.smooth(dying / 2.0F));
      }
      if (!e.containment()) {
         for (String b : CONTAINMENT_BONES) {
            GeoBone bone = model.getBone(b);
            if (bone != null) {
               bone.hidden = true;
            }
         }
      }
   }

   /** Pure: glow: powering up through the deploy, a slow beat, brighter with captives held, dying flicker. */
   static float glow(int ticks, float age, int held, float dying, int id) {
      float g = Mth.clamp(age / ChitauriBeacon.DEPLOY_TICKS, 0.15F, 1.0F) * (0.72F + 0.12F * Mth.sin(age * 0.157F + id));
      g += Math.min(0.2F, held * 0.04F);
      if (dying >= 0.0F) {
         g *= (1.0F - ChitauriSoldierRenderer.smooth(dying / 16.0F)) * (((int)(age * 2.5F + id) & 3) == 0 ? 0.3F : 1.0F);
      }
      return Mth.clamp(g, 0.0F, 1.0F);
   }
}
