package dev.baranhan.viltrumitecore.client.t1000;

import dev.baranhan.viltrumitecore.client.city.CitizenModel;
import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.t1000.T1000Entity;
import dev.baranhan.viltrumitecore.t1000.T1000Form;
import net.minecraft.client.model.geom.ModelPart;

/**
 * The citizen body of the disguise: the very same {@link CitizenModel} (so it is pixel-identical to the crowd),
 * posed on top by {@link T1000Poser}. Once the chrome has covered the skin the painted parts hide and the
 * {@link T1000Body} mesh is the body; the clothes' extra parts (hair, hats, bags, shoes) are drawn into the metal
 * by shrinking them as the reveal spreads and growing them back on the conceal.
 */
public final class T1000Model extends CitizenModel {
   /** Skin visibility of the current frame (read by the metal layer for its blend). */
   float skin = 1.0F;

   public T1000Model(ModelPart root, boolean slim) {
      super(root, slim);
   }

   @Override
   public void setupAnim(CitizenEntity e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
      super.setupAnim(e, limbSwing, limbAmount, age, headYaw, headPitch);
      if (!(e instanceof T1000Entity t)) {
         return;
      }
      T1000Poser.pose(this, t, limbSwing, limbAmount, age, this.partialTick);
      T1000Form form = t.form();
      float metal = form.metal(t.formAge(this.partialTick));
      this.skin = skinVisibility(form, metal);
      boolean skinParts = this.skin > 0.0F;
      this.head.visible = skinParts;
      this.hat.visible = skinParts;
      this.body.visible = skinParts;
      this.jacket.visible = skinParts;
      this.rightArm.visible = skinParts;
      this.leftArm.visible = skinParts;
      this.rightSleeve.visible = skinParts;
      this.leftSleeve.visible = skinParts;
      this.rightLeg.visible = skinParts;
      this.leftLeg.visible = skinParts;
      this.rightPants.visible = skinParts;
      this.leftPants.visible = skinParts;
      float extra = extraScale(metal);
      for (ModelPart p : this.extras()) {
         p.xScale = extra;
         p.yScale = extra;
         p.zScale = extra;
      }
   }

   /**
    * 1 while the painted skin is drawn under the spreading chrome, 0 once the metal covers it all. Pure (tests).
    * The metal mesh sits a hair outside the skin and fades in by coverage, so the two never fight.
    */
   static float skinVisibility(T1000Form form, float metal) {
      if (form == T1000Form.HUMAN) {
         return 1.0F;
      }
      return metal >= 0.999F ? 0.0F : 1.0F;
   }

   /** Scale of the clothes' extra parts: they melt into the body over the first two thirds of the reveal. Pure. */
   static float extraScale(float metal) {
      float t = T1000Shapes.clamp01(metal / 0.66F);
      return 1.0F - t * t * (3.0F - 2.0F * t) * 0.999F;
   }
}
