package dev.baranhan.viltrumitecore.client.thor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirEntity;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirFlight;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirPhase;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirPose;
import java.util.UUID;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The world Mjölnir: the hand prop's own cubes recentred
 * (tools/assets/make_mjolnir_world.py), same size, so the catch has no
 * model jump. Stands upright at rest, head buried in the ground with a
 * crack decal around it (MjolnirPose, rework §6), sticks head first along
 * the throw in a wall, comes down head first, tumbles in flight and turns the handle to the palm over the last
 * metres of a return (№157). The runes glow.
 */
public final class MjolnirRenderer extends EntityRenderer<MjolnirEntity> {
   private static final ResourceLocation GEO = new ResourceLocation("viltrumitecore", "geo/thor/mjolnir_world.geo.json");
   private static final ResourceLocation TEXTURE = new ResourceLocation("viltrumitecore", "textures/entity/thor/mjolnir.png");
   private static final ResourceLocation GLOW = new ResourceLocation("viltrumitecore", "textures/entity/thor/mjolnir_glow.png");
   /** Tumble speed in flight (deg / tick, visual). */
   private static final float SPIN = 38.0F;
   private static final ResourceLocation CRACKS = new ResourceLocation("viltrumitecore", "textures/entity/thor/mjolnir_cracks.png");

   public MjolnirRenderer(Context context) {
      super(context);
      this.shadowRadius = 0.25F;
   }

   @Override
   public void render(MjolnirEntity hammer, float entityYaw, float partialTick, PoseStack stack, MultiBufferSource buffers, int light) {
      BakedGeoModel model = AnimCache.model(GEO);
      if (model == null) {
         return;
      }

      stack.pushPose();
      MjolnirPhase phase = hammer.phase();
      float yaw = Mth.rotLerp(partialTick, hammer.yRotO, hammer.getYRot());
      float pitch = Mth.lerp(partialTick, hammer.xRotO, hammer.getXRot());
      float age = hammer.tickCount + partialTick;
      if (hammer.planted()) {
         // Upright: head down (model +Y turned to -Y), buried MjolnirPose.BURY into the ground, handle straight up.
         MjolnirCracks.render(hammer, partialTick, stack, buffers.getBuffer(RenderType.entityTranslucent(CRACKS)));
         stack.translate(0.0F, (float)MjolnirPose.MODEL_OFFSET, 0.0F);
         stack.mulPose(Axis.YP.rotationDegrees(-yaw));
         stack.mulPose(Axis.XP.rotationDegrees(180.0F));
      } else if (phase == MjolnirPhase.RESTING) {
         // Stuck head first along the throw (entity yaw / pitch = the flight direction).
         stack.translate(0.0F, hammer.getBbHeight() * 0.5F, 0.0F);
         alongDirection(stack, yaw, pitch);
      } else if (phase == MjolnirPhase.WORLD_FALLING || phase == MjolnirPhase.FALLING) {
         // Heavy end first.
         stack.translate(0.0F, hammer.getBbHeight() * 0.5F, 0.0F);
         stack.mulPose(Axis.YP.rotationDegrees(-yaw));
         stack.mulPose(Axis.XP.rotationDegrees(180.0F + (phase == MjolnirPhase.FALLING ? Mth.sin(age * 0.6F) * 8.0F : 0.0F)));
      } else {
         stack.translate(0.0F, hammer.getBbHeight() * 0.5F, 0.0F);
         float turn = phase == MjolnirPhase.RETURNING ? handleTurn(hammer, partialTick) : 0.0F;
         if (turn > 0.0F) {
            // Handle first to the palm: the head points back along the flight.
            alongDirection(stack, yaw + 180.0F * turn, -pitch * turn + pitch * (1.0F - turn));
            stack.mulPose(Axis.XP.rotationDegrees((age * SPIN % 360.0F) * (1.0F - turn)));
         } else {
            alongDirection(stack, yaw, pitch);
            stack.mulPose(Axis.XP.rotationDegrees(age * SPIN % 360.0F));
         }
      }

      AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY,
         1.0F, 1.0F, 1.0F, 1.0F, null);
      AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
         1.0F, 1.0F, 1.0F, 1.0F, null);
      stack.popPose();
      super.render(hammer, entityYaw, partialTick, stack, buffers, light);
   }

   /** Model +Y (the head) along the direction of an entity yaw / pitch. */
   private static void alongDirection(PoseStack stack, float yaw, float pitch) {
      stack.mulPose(Axis.YP.rotationDegrees(-yaw));
      stack.mulPose(Axis.XP.rotationDegrees(pitch + 90.0F));
   }

   private static float handleTurn(MjolnirEntity hammer, float partialTick) {
      UUID owner = hammer.owner();
      Player player = owner == null ? null : hammer.level().getPlayerByUUID(owner);
      if (player == null) {
         return 0.0F;
      }

      Vec3 at = hammer.getPosition(partialTick);
      Vec3 palm = MjolnirFlight.palm(player.getEyePosition(partialTick), player.getViewYRot(partialTick));
      return MjolnirFlight.handleTurn(at.distanceTo(palm));
   }

   @Override
   public ResourceLocation getTextureLocation(MjolnirEntity hammer) {
      return TEXTURE;
   }
}
