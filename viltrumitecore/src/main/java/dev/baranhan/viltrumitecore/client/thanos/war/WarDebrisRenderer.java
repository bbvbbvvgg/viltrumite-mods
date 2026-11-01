package dev.baranhan.viltrumitecore.client.thanos.war;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.entity.thanos.WarDebrisEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ebony Maw's telekinetic debris: the torn block itself, scaled to the chunk's size, tumbling by its mode
 * (orbit and hold turn slowly, a thrown chunk tumbles fast, a falling one rolls, the barrier wall barely
 * drifts). The spin axis comes from the entity id, so every chunk turns its own way.
 */
public class WarDebrisRenderer extends EntityRenderer<WarDebrisEntity> {
   private final BlockRenderDispatcher blocks;

   public WarDebrisRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.blocks = context.getBlockRenderDispatcher();
      this.shadowRadius = 0.4F;
      this.shadowStrength = 0.5F;
   }

   @Override
   public ResourceLocation getTextureLocation(WarDebrisEntity entity) {
      return TextureAtlas.LOCATION_BLOCKS;
   }

   /** Degrees per tick of the tumble for a mode. */
   static float spin(byte mode) {
      return switch (mode) {
         case WarDebrisEntity.FLY -> 22.0F;
         case WarDebrisEntity.FALL -> 9.0F;
         case WarDebrisEntity.BARRIER -> 0.8F;
         case WarDebrisEntity.HOLD -> 2.5F;
         default -> 4.0F;
      };
   }

   @Override
   public void render(WarDebrisEntity e, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
      BlockState state = e.block();
      if (state == null || state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
         state = Blocks.COBBLESTONE.defaultBlockState();
      }

      float size = Mth.clamp(e.size(), 0.3F, 4.0F);
      float age = e.tickCount + partial;
      int id = e.getId();
      float ax = ((id * 37) % 100) / 100.0F - 0.5F;
      float az = ((id * 61) % 100) / 100.0F - 0.5F;
      float angle = age * spin(e.mode()) + (id % 360);
      stack.pushPose();
      stack.translate(0.0, size * 0.5, 0.0);
      stack.mulPose(Axis.YP.rotationDegrees(angle * 0.7F + id * 13));
      stack.mulPose(Axis.XP.rotationDegrees(angle * (0.6F + ax)));
      stack.mulPose(Axis.ZP.rotationDegrees(angle * az));
      stack.scale(size, size, size);
      stack.translate(-0.5, -0.5, -0.5);
      this.blocks.renderSingleBlock(state, stack, buffers, light, OverlayTexture.NO_OVERLAY);
      stack.popPose();
      super.render(e, yaw, partial, stack, buffers, light);
   }

   @Override
   public boolean shouldShowName(WarDebrisEntity entity) {
      return false;
   }
}
