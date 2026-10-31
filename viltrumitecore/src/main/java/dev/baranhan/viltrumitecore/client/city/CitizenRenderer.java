package dev.baranhan.viltrumitecore.client.city;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.city.citizen.CitizenLook;
import dev.baranhan.viltrumitecore.city.citizen.CitizenState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * City pedestrians: {@link CitizenModel} (vanilla player body plus hair, hats, skirts, bags,
 * shoes) with a painted texture per look ({@link CitizenSkins}), body proportions from the
 * look, poses from {@link CitizenPoser}. Knocked-down citizens tip over backwards or forwards
 * (a seed bit) and get back up over the GETTING_UP state.
 */
public class CitizenRenderer extends MobRenderer<CitizenEntity, CitizenModel> {
   /** Ticks the fall takes, and the get-up (matches the server's GETTING_UP length). */
   static final float FALL_TICKS = 6.0F;
   static final float RISE_TICKS = 18.0F;
   private final CitizenModel wide;
   private final CitizenModel slim;

   public CitizenRenderer(EntityRendererProvider.Context ctx) {
      super(ctx, new CitizenModel(CitizenModel.create(false).bakeRoot(), false), 0.5F);
      this.wide = this.model;
      this.slim = new CitizenModel(CitizenModel.create(true).bakeRoot(), true);
   }

   @Override
   public void render(CitizenEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
      CitizenLook look = e.look();
      this.model = look.slim() ? this.slim : this.wide;
      CitizenModel m = this.model;
      m.prepare(look);
      m.partialTick = partialTick;
      m.crouching = e.citizenState() == CitizenState.COVER;
      m.leftArmPose = HumanoidModel.ArmPose.EMPTY;
      m.rightArmPose = HumanoidModel.ArmPose.EMPTY;
      super.render(e, yaw, partialTick, pose, buffers, light);
   }

   @Override
   protected void scale(CitizenEntity e, PoseStack pose, float partialTick) {
      CitizenLook look = e.look();
      float h = look.height() * 0.9375F;
      float w = look.width() * 0.9375F;
      pose.scale(w, h, w);
   }

   @Override
   protected void setupRotations(CitizenEntity e, PoseStack pose, float bob, float bodyYaw, float partialTick) {
      super.setupRotations(e, pose, bob, bodyYaw, partialTick);
      float tip = tip(e, partialTick);
      if (tip > 0.001F) {
         // the pivot at the feet: lying on the back (face up) or on the front, lifted a touch
         boolean back = (e.skin() >>> 29 & 1) == 0;
         pose.translate(0.0F, 0.15F * tip, 0.0F);
         pose.mulPose(Axis.XP.rotationDegrees((back ? -90.0F : 90.0F) * tip));
      }
   }

   /** 0 standing .. 1 lying: eased over the fall, back to 0 over the get-up. */
   static float tip(CitizenEntity e, float partialTick) {
      CitizenState s = e.citizenState();
      if (s != CitizenState.FALLEN && s != CitizenState.GETTING_UP) {
         return 0.0F;
      }
      float age = e.tickCount + partialTick;
      CitizenPoser.Anim a = CitizenPoser.anim(e);
      float since = a.state == s ? age - a.since : 0.0F;
      float t = s == CitizenState.FALLEN ? Mth.clamp(since / FALL_TICKS, 0.0F, 1.0F) : 1.0F - Mth.clamp(since / RISE_TICKS, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   @Override
   public ResourceLocation getTextureLocation(CitizenEntity e) {
      return CitizenSkins.texture(e.look());
   }
}
