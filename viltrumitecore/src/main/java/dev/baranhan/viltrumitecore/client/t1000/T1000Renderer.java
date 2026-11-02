package dev.baranhan.viltrumitecore.client.t1000;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.client.city.CitizenModel;
import dev.baranhan.viltrumitecore.client.city.CitizenRenderer;
import dev.baranhan.viltrumitecore.t1000.T1000Entity;
import dev.baranhan.viltrumitecore.t1000.T1000Form;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * The T-1000: a citizen renderer (same model, same painted look, same proportions — the disguise is the crowd)
 * with the liquid-metal body as a layer on the posed model. The vanilla death tip is suppressed: its death is the
 * metal's own throes and melt. The shadow spreads with the puddle.
 */
public final class T1000Renderer extends CitizenRenderer {
   public T1000Renderer(EntityRendererProvider.Context ctx) {
      super(ctx, new T1000Model(CitizenModel.create(false).bakeRoot(), false), new T1000Model(CitizenModel.create(true).bakeRoot(), true));
      this.addLayer(new MetalLayer(this));
   }

   @Override
   public void render(CitizenEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
      if (e instanceof T1000Entity t) {
         T1000Form form = t.form();
         float melt = form.melt(t.formAge(partialTick));
         this.shadowRadius = 0.5F + 0.35F * melt;
         if (form == T1000Form.DYING) {
            this.shadowRadius *= 1.0F - T1000Shapes.smooth((t.formAge(partialTick) - 60.0F) / 20.0F);
         }
      }
      super.render(e, yaw, partialTick, pose, buffers, light);
   }

   @Override
   protected void setupRotations(CitizenEntity e, PoseStack pose, float bob, float bodyYaw, float partialTick) {
      int deathTime = e.deathTime;
      e.deathTime = 0;
      try {
         super.setupRotations(e, pose, bob, bodyYaw, partialTick);
      } finally {
         e.deathTime = deathTime;
      }
   }

   /** The metal body on the posed citizen model. */
   static final class MetalLayer extends RenderLayer<CitizenEntity, CitizenModel> {
      MetalLayer(RenderLayerParent<CitizenEntity, CitizenModel> parent) {
         super(parent);
      }

      @Override
      public void render(PoseStack pose, MultiBufferSource buffers, int light, CitizenEntity e, float limbSwing, float limbAmount, float partialTick,
         float age, float headYaw, float headPitch) {
         if (!(e instanceof T1000Entity t) || e.isInvisible()) {
            return;
         }
         CitizenModel model = this.getParentModel();
         T1000Body.Frame f = T1000Body.frame(t, partialTick, model.slimArms());
         if (T1000Body.invisible(f)) {
            return;
         }
         T1000Body.render(f, model, pose, buffers, light);
      }
   }
}
