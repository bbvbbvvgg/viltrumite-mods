package dev.baranhan.viltrumitecore.client.ultron;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.entity.ultron.UltronEngineer;
import dev.baranhan.viltrumitecore.entity.ultron.UltronUnit;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronMove;
import java.util.List;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Engineer: the builder. Scuttles on four legs, settles into the welding stance at a node (the torch arm working, a
 * white-hot flame at the torch tip, the sparks come with the server's weld effect), plugs its probe in to hack, plants
 * the spike. Only the sensor head tracks. The power canister is blown off at critical damage.
 */
public class UltronEngineerRenderer extends UltronUnitRenderer<UltronEngineer> {
   public UltronEngineerRenderer(EntityRendererProvider.Context context) {
      super(context, UltronAssets.ENGINEER, 0.6F, new String[]{"torch", "probe", "head", "spike"}, new String[0], new String[0], new float[0][], "canister",
         List.of("body", "canister", "head", "welder", "claw", "hip_f_r", "hip_f_l", "hip_b_r", "hip_b_l"));
   }

   @Override
   protected void extraPose(BakedGeoModel model, UltronEngineer e, float partial, float age) {
      float hold = Mth.lerp(partial, e.clientHoldO, e.clientHold);
      Animation weld = this.clip("weld");
      if (weld != null && hold > 0.01F && e.clientMoveSeconds(partial) < 0.0F) {
         over(weld, model, loop(age / 20.0F + e.getId() * 0.1F, weld), smooth(hold));
      }
   }

   @Override
   protected void look(BakedGeoModel model, UltronEngineer e, float yaw, float pitch) {
      deg(model.getBone("head"), pitch * 0.6F, yaw * 0.7F, 0.0F);
   }

   @Override
   protected void extras(UltronEngineer e, float partial, float age, float dying, PoseStack stack, VertexConsumer vc) {
      if (dying >= 0.0F) {
         return;
      }
      Matrix4f m = stack.last().pose();
      float t = e.clientMoveSeconds(partial);
      boolean hacking = t >= 0.0F && e.clientMove == UltronMove.ENGINEER_HACK;
      Vector3f torch = this.anchor(A_RIGHT);
      if (torch != null && e.flag(UltronUnit.FLAG_BEAM) && !hacking) {
         float flick = ((int)(age * 2.0F) + e.getId()) % 5 == 0 ? 0.6F : 1.0F;
         this.orb(vc, m, torch, 0.32F * flick, 0.2F, 120, 170, 255, 110);
         this.orb(vc, m, torch, 0.12F * flick, 0.6F, 240, 245, 255, 240);
      }
      Vector3f probe = this.anchor(A_LEFT);
      if (probe != null && hacking) {
         float pulse = 0.5F + 0.5F * Mth.sin(age * 1.4F);
         this.orb(vc, m, probe, 0.18F + 0.08F * pulse, 0.35F, 255, 40, 30, (int)(120 + 100 * pulse));
      }
   }
}
