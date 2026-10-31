package dev.baranhan.viltrumitecore.client.city;

import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.city.citizen.CitizenLook;
import dev.baranhan.viltrumitecore.city.citizen.CitizenPainter;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The citizen body: the vanilla player model (so the shared {@code PoseRig} poses it like
 * every hero on a vanilla body) plus citizen-only cubes that follow their bones: hair
 * volume, long hair, ponytail, bun, quiff, a nose, glasses, cap / beanie / fedora, a
 * skirt or coat tail, backpack, shoulder bag, shoes and a collar or scarf. One shared
 * model per arm width; {@link #prepare} switches the parts of each look before drawing.
 * Texture layout: {@link CitizenPainter} (128x64).
 */
public final class CitizenModel extends PlayerModel<CitizenEntity> {
   final ModelPart hairBig;
   final ModelPart crown;
   final ModelPart visor;
   final ModelPart brim;
   final ModelPart bun;
   final ModelPart longBack;
   final ModelPart ponytail;
   final ModelPart glasses;
   final ModelPart quiff;
   final ModelPart nose;
   final ModelPart skirt;
   final ModelPart backpack;
   final ModelPart bag;
   final ModelPart shoeR;
   final ModelPart shoeL;
   final ModelPart collar;
   final boolean slimArms;
   /** Set by the renderer before setupAnim: the partial tick of this frame. */
   float partialTick;

   public CitizenModel(ModelPart root, boolean slim) {
      super(root, slim);
      this.slimArms = slim;
      ModelPart h = this.head;
      this.hairBig = h.getChild("hair_big");
      this.crown = h.getChild("crown");
      this.visor = h.getChild("visor");
      this.brim = h.getChild("brim");
      this.bun = h.getChild("bun");
      this.longBack = h.getChild("long_back");
      this.ponytail = h.getChild("ponytail");
      this.glasses = h.getChild("glasses");
      this.quiff = h.getChild("quiff");
      this.nose = h.getChild("nose");
      this.skirt = this.body.getChild("skirt");
      this.backpack = this.body.getChild("backpack");
      this.bag = this.body.getChild("bag");
      this.collar = this.body.getChild("collar");
      this.shoeR = this.rightLeg.getChild("shoe");
      this.shoeL = this.leftLeg.getChild("shoe");
   }

   private static CubeListBuilder cube(int[] uv) {
      return CubeListBuilder.create().texOffs(uv[0], uv[1]);
   }

   public static LayerDefinition create(boolean slim) {
      MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, slim);
      PartDefinition root = mesh.getRoot();
      PartDefinition head = root.getChild("head");
      head.addOrReplaceChild("hair_big", cube(CitizenPainter.HAIR_BIG).addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, new CubeDeformation(1.0F)), PartPose.ZERO);
      head.addOrReplaceChild("crown", cube(CitizenPainter.CROWN).addBox(-4.0F, -8.0F, -4.0F, 8, 3, 8, new CubeDeformation(0.62F)), PartPose.ZERO);
      head.addOrReplaceChild("visor", cube(CitizenPainter.VISOR).addBox(-3.0F, -5.1F, -8.2F, 6, 1, 4, new CubeDeformation(0.0F, -0.3F, 0.0F)), PartPose.ZERO);
      head.addOrReplaceChild("brim", cube(CitizenPainter.BRIM).addBox(-6.0F, -5.4F, -6.0F, 12, 1, 12, new CubeDeformation(0.0F, -0.35F, 0.0F)), PartPose.ZERO);
      head.addOrReplaceChild("bun", cube(CitizenPainter.BUN).addBox(-1.5F, -2.5F, -1.5F, 3, 3, 3), PartPose.offset(0.0F, -8.0F, 2.8F));
      head.addOrReplaceChild("long_back", cube(CitizenPainter.LONG_BACK).addBox(-4.0F, -7.0F, 3.4F, 8, 11, 2, new CubeDeformation(0.15F, 0.0F, 0.0F)), PartPose.ZERO);
      head.addOrReplaceChild("ponytail", cube(CitizenPainter.PONYTAIL).addBox(-1.0F, 0.0F, 0.0F, 2, 6, 2), PartPose.offsetAndRotation(0.0F, -6.5F, 4.2F, 0.35F, 0.0F, 0.0F));
      head.addOrReplaceChild("glasses", cube(CitizenPainter.GLASSES).addBox(-4.0F, -5.0F, -5.0F, 8, 2, 1, new CubeDeformation(0.05F, 0.0F, -0.38F)), PartPose.ZERO);
      head.addOrReplaceChild("quiff", cube(CitizenPainter.QUIFF).addBox(-2.0F, -9.4F, -4.6F, 4, 2, 3), PartPose.ZERO);
      head.addOrReplaceChild("nose", cube(CitizenPainter.NOSE).addBox(-1.0F, -3.0F, -4.6F, 2, 1, 1, new CubeDeformation(0.0F, 0.0F, -0.2F)), PartPose.ZERO);
      PartDefinition body = root.getChild("body");
      body.addOrReplaceChild("skirt", cube(CitizenPainter.SKIRT).addBox(-4.0F, 11.0F, -2.0F, 8, 7, 4, new CubeDeformation(0.42F, 0.0F, 0.42F)), PartPose.ZERO);
      body.addOrReplaceChild("backpack", cube(CitizenPainter.BACKPACK).addBox(-3.0F, 1.0F, 2.1F, 6, 7, 3), PartPose.ZERO);
      body.addOrReplaceChild("bag", cube(CitizenPainter.BAG).addBox(0.6F, 8.0F, 2.1F, 4, 4, 2), PartPose.ZERO);
      body.addOrReplaceChild("collar", cube(CitizenPainter.COLLAR).addBox(-4.0F, -0.6F, -2.0F, 8, 2, 4, new CubeDeformation(0.45F, 0.0F, 0.45F)), PartPose.ZERO);
      root.getChild("right_leg").addOrReplaceChild("shoe", cube(CitizenPainter.SHOE_R).addBox(-2.0F, 10.0F, -3.0F, 4, 2, 5, new CubeDeformation(0.22F, 0.0F, 0.12F)), PartPose.ZERO);
      root.getChild("left_leg").addOrReplaceChild("shoe", cube(CitizenPainter.SHOE_L).addBox(-2.0F, 10.0F, -3.0F, 4, 2, 5, new CubeDeformation(0.22F, 0.0F, 0.12F)), PartPose.ZERO);
      return LayerDefinition.create(mesh, CitizenPainter.W, CitizenPainter.H);
   }

   /** Shows the parts of this look, hides the rest (the model is shared by every citizen). */
   void prepare(CitizenLook l) {
      this.setAllVisible(true);
      CitizenLook.Hair hs = l.hair();
      this.hairBig.visible = hs.voluminous();
      this.longBack.visible = hs.longBack();
      this.ponytail.visible = hs == CitizenLook.Hair.PONYTAIL;
      this.bun.visible = hs == CitizenLook.Hair.BUN || hs == CitizenLook.Hair.MAN_BUN;
      this.quiff.visible = hs == CitizenLook.Hair.QUIFF && l.hat() == CitizenLook.Hat.NONE;
      this.crown.visible = l.hat() != CitizenLook.Hat.NONE;
      this.visor.visible = l.hat() == CitizenLook.Hat.CAP;
      this.brim.visible = l.hat() == CitizenLook.Hat.FEDORA;
      this.glasses.visible = l.eyewear() != CitizenLook.Eyewear.NONE;
      this.nose.visible = true;
      this.skirt.visible = l.skirtPart();
      this.backpack.visible = l.bag() == CitizenLook.Bag.BACKPACK;
      this.bag.visible = l.bag() == CitizenLook.Bag.SHOULDER;
      this.collar.visible = l.scarf() || l.top() == CitizenLook.Top.COAT || l.top() == CitizenLook.Top.JACKET && (l.seed() & 2048) != 0;
      this.shoeR.visible = true;
      this.shoeL.visible = true;
      this.bun.y = hs == CitizenLook.Hair.MAN_BUN ? -7.0F : -8.0F;
      this.bun.z = hs == CitizenLook.Hair.MAN_BUN ? 3.6F : 2.8F;
   }

   @Override
   public void setupAnim(CitizenEntity e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
      dev.baranhan.viltrumitecore.client.anim.pose.PoseRig.restorePivots(this);
      super.setupAnim(e, limbSwing, limbAmount, age, headYaw, headPitch);
      CitizenPoser.pose(this, e, limbSwing, limbAmount, age, headPitch);
   }
}
