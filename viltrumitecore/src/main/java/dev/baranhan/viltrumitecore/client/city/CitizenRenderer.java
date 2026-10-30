package dev.baranhan.viltrumitecore.client.city;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** City pedestrians: the vanilla player model (wide or slim arms) with a generated civilian skin. */
public class CitizenRenderer extends HumanoidMobRenderer<CitizenEntity, PlayerModel<CitizenEntity>> {
   private final PlayerModel<CitizenEntity> wide;
   private final PlayerModel<CitizenEntity> slim;

   public CitizenRenderer(EntityRendererProvider.Context ctx) {
      super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
      this.wide = this.model;
      this.slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
   }

   @Override
   public void render(CitizenEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
      this.model = e.slim() ? this.slim : this.wide;
      PlayerModel<CitizenEntity> m = this.model;
      m.setAllVisible(true);
      m.crouching = false;
      // fleeing citizens run with their arms up a little (vanilla "cross bow" pose would be wrong)
      m.leftArmPose = HumanoidModel.ArmPose.EMPTY;
      m.rightArmPose = HumanoidModel.ArmPose.EMPTY;
      super.render(e, yaw, partialTick, pose, buffers, light);
   }

   @Override
   public ResourceLocation getTextureLocation(CitizenEntity e) {
      return CitizenSkins.texture(e.skin());
   }
}
