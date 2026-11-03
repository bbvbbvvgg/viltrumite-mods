package dev.baranhan.viltrumitecore.client.venom;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.venom.VenomFlags;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/**
 * The symbiote's coat on the vanilla player model: spreading over Venom's human during the first transformation
 * stages (and receding at the end of the reverse), and over a ridden host (engulf spreads it, the end strips it; in
 * control it pulses and grows extra limbs: tendrils off the shoulder blades). The coat is a 64x64 skin frame
 * (coat_0..16, the hands and feet first, the head last) drawn over the player's own skin. Client only.
 */
public class VenomCoatLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
   public VenomCoatLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
      super(parent);
   }

   @Override
   public void render(PoseStack stack, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
      float partial, float age, float netHeadYaw, float headPitch) {
      if (player.isInvisible()) {
         return;
      }
      float coat = VenomPossessionClient.coatOf(player, partial);
      boolean host = coat > 0;
      if (!host) {
         HeroPublicSnapshot s = VenomView.of(player);
         if (s == null || VenomView.flag(s, VenomFlags.POSSESSING)) {
            return;
         }
         VenomView.Look look = VenomView.look(VenomView.flag(s, VenomFlags.FORM), VenomView.move(s), VenomPose.actionTicks(player, s, partial));
         if (look.geo()) {
            return;
         }
         coat = look.coat();
      }
      int frame = VenomView.coatFrame(coat);
      if (frame <= 0) {
         return;
      }
      PlayerModel<AbstractClientPlayer> model = this.getParentModel();
      model.renderToBuffer(stack, buffers.getBuffer(RenderType.entityCutoutNoCull(VenomView.coat(frame))), light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
      if (ShaderCompat.isShadowPass()) {
         return;
      }
      float p = VenomView.pulse(age / 20.0, host, player.hurtTime) * coat;
      model.renderToBuffer(stack, buffers.getBuffer(RenderType.eyes(VenomView.rl("textures/entity/venom/venom_skin_pulse.png"))), LightTexture.FULL_BRIGHT,
         OverlayTexture.NO_OVERLAY, p * 0.5F, p * 0.55F, p, 1.0F);
      if (host && coat > 0.6F) {
         // extra limbs: four tendrils off the shoulder blades (body part space: y down, +z = back)
         stack.pushPose();
         model.body.translateAndRotate(stack);
         var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(VenomView.TENDRIL));
         double seconds = age / 20.0;
         float len = 0.85F * (coat - 0.6F) / 0.4F;
         for (int i = 0; i < 4; i++) {
            float side = i % 2 == 0 ? 1 : -1;
            float row = i / 2;
            Vector3f root = new Vector3f(side * 0.12F, 0.12F + 0.12F * row, 0.14F);
            Vector3f dir = new Vector3f(side * 0.6F, -0.5F + 0.4F * row, 0.7F).normalize();
            Vector3f sideV = new Vector3f(dir).cross(0, 1, 0).normalize();
            VenomTendrils.draw(stack.last(), vc, VenomTendrils.wave(root, dir, sideV, len * (1 - 0.15F * row), seconds, i * 1.9F, 0.22F, 8), 0.045F, 0.015F,
               light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
         }
         stack.popPose();
      }
   }
}
