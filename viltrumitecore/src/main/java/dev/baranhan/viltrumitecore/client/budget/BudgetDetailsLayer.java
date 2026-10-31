package dev.baranhan.viltrumitecore.client.budget;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.budget.BudgetHeroSpec;
import dev.baranhan.viltrumitecore.hero.budget.BudgetHeroes;
import java.util.Map;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Primitive cube details of the expansion heroes (hair spikes, ears, capes,
 * claws...), drawn as tinted white boxes glued to the vanilla head, body or
 * right arm. Detail tokens come from BudgetLook ("spiky:f4f4f4").
 */
public class BudgetDetailsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
   private enum Anchor { HEAD, BODY, RIGHT_ARM }

   private record Detail(ModelPart part, Anchor anchor) {
   }

   private static Map<String, Detail> details;

   public BudgetDetailsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
      super(parent);
   }

   private static Map<String, Detail> details() {
      if (details == null) {
         MeshDefinition mesh = new MeshDefinition();
         PartDefinition root = mesh.getRoot();
         root.addOrReplaceChild("spiky", CubeListBuilder.create()
            .addBox(-3.5F, -11.0F, -3.0F, 2.0F, 3.0F, 2.0F)
            .addBox(-1.0F, -12.0F, -2.0F, 2.0F, 4.0F, 2.0F)
            .addBox(1.5F, -11.0F, -3.0F, 2.0F, 3.0F, 2.0F)
            .addBox(-3.0F, -10.5F, 1.0F, 2.0F, 2.5F, 2.0F)
            .addBox(1.0F, -10.5F, 1.0F, 2.0F, 2.5F, 2.0F)
            .addBox(-4.25F, -8.75F, -4.5F, 8.5F, 1.5F, 1.0F), PartPose.ZERO);
         root.addOrReplaceChild("tall_spiky", CubeListBuilder.create()
            .addBox(-3.0F, -13.0F, -2.5F, 6.0F, 5.0F, 5.0F)
            .addBox(-1.0F, -15.0F, -1.5F, 2.0F, 3.0F, 2.0F)
            .addBox(-4.25F, -8.75F, -4.5F, 8.5F, 1.0F, 1.0F), PartPose.ZERO);
         root.addOrReplaceChild("bun", CubeListBuilder.create().addBox(-1.5F, -8.5F, 4.0F, 3.0F, 3.0F, 2.0F), PartPose.ZERO);
         root.addOrReplaceChild("long_hair", CubeListBuilder.create().addBox(-4.0F, -8.0F, 4.0F, 8.0F, 13.0F, 1.0F), PartPose.ZERO);
         root.addOrReplaceChild("ears", CubeListBuilder.create()
            .addBox(-3.5F, -10.5F, -1.0F, 1.5F, 3.0F, 1.5F)
            .addBox(2.0F, -10.5F, -1.0F, 1.5F, 3.0F, 1.5F), PartPose.ZERO);
         root.addOrReplaceChild("ogre_ears", CubeListBuilder.create()
            .addBox(-6.5F, -6.0F, -0.75F, 2.5F, 1.5F, 1.5F)
            .addBox(4.0F, -6.0F, -0.75F, 2.5F, 1.5F, 1.5F), PartPose.ZERO);
         root.addOrReplaceChild("bolts", CubeListBuilder.create()
            .addBox(-5.0F, -6.0F, -1.0F, 1.0F, 3.0F, 1.0F)
            .addBox(4.0F, -6.0F, -1.0F, 1.0F, 3.0F, 1.0F)
            .addBox(-5.5F, -7.0F, -1.0F, 1.0F, 1.0F, 1.0F)
            .addBox(4.5F, -7.0F, -1.0F, 1.0F, 1.0F, 1.0F), PartPose.ZERO);
         root.addOrReplaceChild("glasses", CubeListBuilder.create().addBox(-3.5F, -4.5F, -4.6F, 7.0F, 1.0F, 0.5F), PartPose.ZERO);
         root.addOrReplaceChild("headband", CubeListBuilder.create().addBox(-4.3F, -7.2F, -4.3F, 8.6F, 1.6F, 8.6F), PartPose.ZERO);
         root.addOrReplaceChild("straw_hat", CubeListBuilder.create()
            .addBox(-6.5F, -8.4F, -6.5F, 13.0F, 0.6F, 13.0F)
            .addBox(-4.4F, -11.0F, -4.4F, 8.8F, 2.8F, 8.8F), PartPose.ZERO);
         root.addOrReplaceChild("cape", CubeListBuilder.create().addBox(-5.0F, 0.0F, 2.2F, 10.0F, 18.0F, 0.6F), PartPose.rotation(0.12F, 0.0F, 0.0F));
         root.addOrReplaceChild("katana_back", CubeListBuilder.create().addBox(-0.5F, -3.0F, 2.3F, 1.0F, 15.0F, 0.8F), PartPose.rotation(0.0F, 0.0F, 0.6F));
         root.addOrReplaceChild("sword_hip", CubeListBuilder.create().addBox(4.1F, 9.0F, -3.0F, 0.8F, 0.8F, 9.0F), PartPose.ZERO);
         root.addOrReplaceChild("claws", CubeListBuilder.create()
            .addBox(-2.6F, 10.0F, -1.6F, 0.4F, 4.5F, 0.4F)
            .addBox(-1.4F, 10.0F, -1.6F, 0.4F, 4.5F, 0.4F)
            .addBox(-0.2F, 10.0F, -1.6F, 0.4F, 4.5F, 0.4F), PartPose.ZERO);
         root.addOrReplaceChild("ring", CubeListBuilder.create().addBox(-3.4F, 8.5F, -1.0F, 1.0F, 1.0F, 2.0F), PartPose.ZERO);
         // MULTIVERSE EXPANSION PHASE II details: same cheap white cubes.
         root.addOrReplaceChild("big_sword", CubeListBuilder.create()
            .addBox(-1.5F, -6.0F, 2.4F, 3.0F, 22.0F, 0.8F)
            .addBox(-2.5F, 15.0F, 2.4F, 5.0F, 1.0F, 1.0F), PartPose.rotation(0.0F, 0.0F, 0.5F));
         root.addOrReplaceChild("horns", CubeListBuilder.create()
            .addBox(-3.5F, -11.0F, -2.0F, 1.5F, 3.0F, 1.5F)
            .addBox(2.0F, -11.0F, -2.0F, 1.5F, 3.0F, 1.5F), PartPose.ZERO);
         root.addOrReplaceChild("chainsaw", CubeListBuilder.create()
            .addBox(-1.0F, -12.0F, -5.0F, 2.0F, 6.0F, 2.0F)
            .addBox(-0.5F, -14.0F, -5.0F, 1.0F, 2.0F, 1.0F), PartPose.ZERO);
         root.addOrReplaceChild("wide_hat", CubeListBuilder.create()
            .addBox(-7.0F, -8.4F, -7.0F, 14.0F, 0.6F, 14.0F)
            .addBox(-4.4F, -11.5F, -4.4F, 8.8F, 3.2F, 8.8F), PartPose.ZERO);
         ModelPart baked = LayerDefinition.create(mesh, 16, 16).bakeRoot();
         Map<String, Detail> map = new java.util.HashMap<>();
         for (String name : new String[]{"spiky", "tall_spiky", "bun", "long_hair", "ears", "ogre_ears", "bolts", "glasses", "headband", "straw_hat", "horns", "chainsaw", "wide_hat"}) {
            map.put(name, new Detail(baked.getChild(name), Anchor.HEAD));
         }
         for (String name : new String[]{"cape", "katana_back", "sword_hip", "big_sword"}) {
            map.put(name, new Detail(baked.getChild(name), Anchor.BODY));
         }
         for (String name : new String[]{"claws", "ring"}) {
            map.put(name, new Detail(baked.getChild(name), Anchor.RIGHT_ARM));
         }
         details = map;
      }

      return details;
   }

   @Override
   public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
      float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
      if (!(player instanceof HeroPlayer heroPlayer) || player.isInvisible()) {
         return;
      }

      BudgetHeroSpec spec = BudgetHeroes.spec(heroPlayer.getHeroId());
      if (spec == null || spec.look().details().length == 0) {
         return;
      }

      renderDetails(pose, buffers, light, this.getParentModel(), spec);
   }

   /** Shared with the multiverse NPC renderer: the hero's cubic details on any humanoid model. */
   public static void renderDetails(PoseStack pose, MultiBufferSource buffers, int light, net.minecraft.client.model.HumanoidModel<?> model, BudgetHeroSpec spec) {
      VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(BudgetSkins.white()));
      for (String token : spec.look().details()) {
         int split = token.indexOf(':');
         String name = split < 0 ? token : token.substring(0, split);
         int color = split < 0 ? 0x202020 : BudgetSkins.parse(token.substring(split + 1));
         Detail detail = details().get(name);
         if (detail == null) {
            continue;
         }

         pose.pushPose();
         ModelPart anchor = switch (detail.anchor()) {
            case HEAD -> model.head;
            case BODY -> model.body;
            case RIGHT_ARM -> model.rightArm;
         };
         anchor.translateAndRotate(pose);
         detail.part().render(pose, consumer, light, OverlayTexture.NO_OVERLAY,
            (color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F, 1.0F);
         pose.popPose();
      }
   }
}
