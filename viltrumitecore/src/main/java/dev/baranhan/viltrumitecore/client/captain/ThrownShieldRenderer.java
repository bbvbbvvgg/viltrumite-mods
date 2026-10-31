package dev.baranhan.viltrumitecore.client.captain;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.entity.captain.ThrownShieldEntity;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The flying shield: the same mesh as on Steve's arm, laid nearly flat (star up) and spinning fast like a
 * disc, banked into its travel direction; the kinetic glow pass when the throw was empowered. Client only.
 */
public class ThrownShieldRenderer extends EntityRenderer<ThrownShieldEntity> {
   /** Spin in degrees per tick (about 2.2 turns per second). */
   static final float SPIN = 40.0F;
   static final float BANK = 14.0F;

   public ThrownShieldRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 0.25F;
   }

   @Override
   public ResourceLocation getTextureLocation(ThrownShieldEntity entity) {
      return CaptainView.SHIELD_TEX;
   }

   @Override
   public void render(ThrownShieldEntity e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      BakedGeoModel model = AnimCache.model(CaptainView.SHIELD);
      if (model == null) {
         return;
      }
      model.resetBones();
      Vec3 d = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
      if (d.lengthSqr() < 1.0E-6) {
         d = e.getDeltaMovement();
      }
      float heading = d.horizontalDistanceSqr() > 1.0E-6 ? (float)Math.toDegrees(Math.atan2(-d.x, -d.z)) : 0.0F;
      float age = e.tickCount + partial;
      stack.pushPose();
      stack.translate(0.0, 0.25, 0.0);
      stack.mulPose(Axis.YP.rotationDegrees(heading));
      stack.mulPose(Axis.ZP.rotationDegrees(BANK));
      stack.mulPose(Axis.XP.rotationDegrees(90.0F)); // face (-Z) up
      stack.mulPose(Axis.ZP.rotationDegrees(-age * SPIN)); // spin about the face normal
      float glow = e.empowered() && !ShaderCompat.isShadowPass() ? 0.75F + 0.25F * Mth.sin(age * 0.6F) : 0.0F;
      CaptainRenderer.drawShield(stack, buffers, model, light, OverlayTexture.NO_OVERLAY, 1.0F, glow);
      stack.popPose();
      super.render(e, yaw, partial, stack, buffers, light);
   }
}
