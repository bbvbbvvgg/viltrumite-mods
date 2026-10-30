package dev.baranhan.viltrumitecore.client.captain;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.entity.avengers.AvengerProjectileEntity;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Avengers projectiles: Hawkeye's arrows (plain / explosive / shock), Iron Man's micro-missiles and the thrown
 * Mjolnir. Meshes point along -Z and are turned into the travel direction; the hammer tumbles end over end.
 * Trails: missile smoke + flame, shock sparks, hammer static. Client only.
 */
public class AvengerProjectileRenderer extends EntityRenderer<AvengerProjectileEntity> {
   private static final String[] KEYS = {"arrow", "arrow_explosive", "arrow_shock", "mini_missile", "mjolnir"};
   private static final ResourceLocation[] GEO = new ResourceLocation[KEYS.length];
   private static final ResourceLocation[] TEX = new ResourceLocation[KEYS.length];
   private static final ResourceLocation[] GLOW = new ResourceLocation[KEYS.length];

   static {
      for (int i = 0; i < KEYS.length; i++) {
         GEO[i] = CaptainView.rl("geo/avengers/" + KEYS[i] + ".geo.json");
         TEX[i] = CaptainView.rl("textures/entity/avengers/" + KEYS[i] + ".png");
         GLOW[i] = CaptainView.rl("textures/entity/avengers/" + KEYS[i] + "_glow.png");
      }
   }

   private int lastTrail = -1;

   public AvengerProjectileRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 0.0F;
   }

   static int index(int type) {
      return Mth.clamp(type, 0, KEYS.length - 1);
   }

   @Override
   public ResourceLocation getTextureLocation(AvengerProjectileEntity entity) {
      return TEX[index(entity.type())];
   }

   @Override
   public void render(AvengerProjectileEntity e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      int i = index(e.type());
      BakedGeoModel model = AnimCache.model(GEO[i]);
      if (model == null) {
         return;
      }
      model.resetBones();
      Vec3 d = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
      if (d.lengthSqr() < 1.0E-6) {
         d = e.getDeltaMovement();
      }
      if (d.lengthSqr() < 1.0E-6) {
         d = new Vec3(0, 0, 1);
      }
      d = d.normalize();
      float heading = (float)Math.toDegrees(Math.atan2(-d.x, -d.z));
      float pitch = (float)Math.toDegrees(Math.asin(Mth.clamp(d.y, -1.0, 1.0)));
      float age = e.tickCount + partial;
      stack.pushPose();
      stack.translate(0.0, e.getBbHeight() * 0.5, 0.0);
      stack.mulPose(Axis.YP.rotationDegrees(heading));
      stack.mulPose(Axis.XP.rotationDegrees(pitch));
      if (e.type() == AvengerProjectileEntity.HAMMER) {
         stack.mulPose(Axis.XP.rotationDegrees(-age * 45.0F)); // end over end
         stack.scale(1.2F, 1.2F, 1.2F);
      } else if (e.type() == AvengerProjectileEntity.MISSILE) {
         stack.mulPose(Axis.ZP.rotationDegrees(age * 30.0F)); // fin roll
      }
      AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.entityCutoutNoCull(TEX[i])), light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1, null);
      if (!ShaderCompat.isShadowPass() && e.type() != AvengerProjectileEntity.ARROW) {
         AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(GLOW[i])), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
            1, 1, 1, 1.0F, null);
      }
      stack.popPose();
      if (e.tickCount != this.lastTrail) {
         this.lastTrail = e.tickCount;
         trail(e, d);
      }
      super.render(e, yaw, partial, stack, buffers, light);
   }

   private static void trail(AvengerProjectileEntity e, Vec3 dir) {
      var level = e.level();
      Vec3 p = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(dir.scale(0.3));
      switch (e.type()) {
         case AvengerProjectileEntity.MISSILE -> {
            level.addParticle(ParticleTypes.SMOKE, p.x, p.y, p.z, 0, 0.01, 0);
            level.addParticle(ParticleTypes.SMALL_FLAME, p.x, p.y, p.z, -dir.x * 0.05, -dir.y * 0.05, -dir.z * 0.05);
         }
         case AvengerProjectileEntity.ARROW_SHOCK -> level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 0, 0, 0);
         case AvengerProjectileEntity.ARROW_EXPLOSIVE -> level.addParticle(ParticleTypes.SMOKE, p.x, p.y, p.z, 0, 0, 0);
         case AvengerProjectileEntity.HAMMER -> {
            if (e.tickCount % 2 == 0) {
               level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 0, 0, 0);
            }
         }
         default -> level.addParticle(ParticleTypes.CRIT, p.x, p.y, p.z, 0, 0, 0);
      }
   }
}
