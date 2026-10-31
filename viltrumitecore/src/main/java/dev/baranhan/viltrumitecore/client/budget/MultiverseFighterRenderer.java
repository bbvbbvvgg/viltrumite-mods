package dev.baranhan.viltrumitecore.client.budget;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.hero.budget.BudgetHeroSpec;
import dev.baranhan.viltrumitecore.hero.budget.multiverse.MultiverseFighter;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Multiverse NPCs: the vanilla player model, the runtime budget skin of their
 * hero and the same cubic details as the playable hero, scaled up for bosses.
 */
public class MultiverseFighterRenderer extends HumanoidMobRenderer<MultiverseFighter, PlayerModel<MultiverseFighter>> {
   public MultiverseFighterRenderer(EntityRendererProvider.Context context) {
      super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
      this.addLayer(new Details(this));
   }

   @Override
   public ResourceLocation getTextureLocation(MultiverseFighter fighter) {
      BudgetHeroSpec spec = fighter.spec();
      return spec == null ? BudgetSkins.white() : BudgetSkins.skin(spec);
   }

   @Override
   protected void scale(MultiverseFighter fighter, PoseStack pose, float partialTick) {
      float scale = fighter.getScale();
      pose.scale(scale, scale, scale);
   }

   static final class Details extends RenderLayer<MultiverseFighter, PlayerModel<MultiverseFighter>> {
      Details(RenderLayerParent<MultiverseFighter, PlayerModel<MultiverseFighter>> parent) {
         super(parent);
      }

      @Override
      public void render(PoseStack pose, MultiBufferSource buffers, int light, MultiverseFighter fighter, float limbSwing, float limbSwingAmount,
         float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
         BudgetHeroSpec spec = fighter.spec();
         if (spec != null && !fighter.isInvisible() && spec.look().details().length > 0) {
            BudgetDetailsLayer.renderDetails(pose, buffers, light, this.getParentModel(), spec);
         }
      }
   }
}
