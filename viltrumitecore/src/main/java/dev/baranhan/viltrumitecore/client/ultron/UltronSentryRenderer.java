package dev.baranhan.viltrumitecore.client.ultron;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.entity.ultron.UltronSentry;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronMove;
import java.util.List;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Sentry: the line machine. Walks and runs on its gait, squares up into the combat stance, raises the left forearm into
 * the deflector guard when the adaptation protocol is on, strikes in three variants (trails on the striking hands),
 * charges the palm blast in the raised right hand. The left arm tears off at critical damage.
 */
public class UltronSentryRenderer extends UltronUnitRenderer<UltronSentry> {
   public UltronSentryRenderer(EntityRendererProvider.Context context) {
      super(context, UltronAssets.SENTRY, 0.5F, new String[]{"muzzle_r", "muzzle_l", "head", null}, new String[0], new String[0], new float[0][],
         "arm_l", List.of("head", "chest", "abdomen", "hips", "arm_r", "forearm_r", "arm_l", "forearm_l", "leg_r", "shin_r", "leg_l", "shin_l"));
   }

   @Override
   protected void extraPose(BakedGeoModel model, UltronSentry e, float partial, float age) {
      float hold = Mth.lerp(partial, e.clientHoldO, e.clientHold);
      Animation guard = this.clip("guard");
      if (guard != null && hold > 0.01F && e.clientMoveSeconds(partial) < 0.0F) {
         over(guard, model, loop(age / 20.0F, guard), smooth(hold));
      }
   }

   @Override
   protected void extras(UltronSentry e, float partial, float age, float dying, PoseStack stack, VertexConsumer vc) {
      if (dying >= 0.0F) {
         return;
      }
      Matrix4f m = stack.last().pose();
      float t = e.clientMoveSeconds(partial);
      UltronMove move = t < 0.0F ? UltronMove.NONE : e.clientMove;
      boolean strike = move == UltronMove.SENTRY_STRIKE_A || move == UltronMove.SENTRY_STRIKE_B || move == UltronMove.SENTRY_STRIKE_C;
      float contact = strike ? move.firstContact() / 20.0F : 0.0F;
      boolean fast = strike && t > contact - 0.18F && t < contact + 0.06F;
      this.swing(e, A_RIGHT, partial, age, stack, vc, fast, 3.0F, 0.16F, 255, 70, 45);
      if (!e.clientPartLost) {
         this.swing(e, A_LEFT, partial, age, stack, vc, fast, 3.0F, 0.16F, 255, 70, 45);
      }
      Vector3f palm = this.anchor(A_RIGHT);
      if (move == UltronMove.SENTRY_BLAST && palm != null) {
         float hit = move.firstContact() / 20.0F;
         if (t >= 0.2F && t < hit) {
            float k = (t - 0.2F) / (hit - 0.2F);
            float tremble = 0.9F + 0.1F * Mth.sin(age * 4.1F);
            this.orb(vc, m, palm, (0.12F + 0.3F * k) * tremble, 0.3F, 255, 60, 35, (int)(90 + 130 * k));
            this.orb(vc, m, palm, 0.05F + 0.1F * k, 0.6F, 255, 225, 200, (int)(220 * k));
         } else if (t >= hit && t < hit + 0.2F) {
            float f = 1.0F - (t - hit) / 0.2F;
            this.orb(vc, m, palm, 0.7F * f, 0.25F, 255, 110, 70, (int)(230 * f));
         }
      }
   }
}
