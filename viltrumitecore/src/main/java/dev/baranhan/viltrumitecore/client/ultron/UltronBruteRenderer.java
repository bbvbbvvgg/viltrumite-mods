package dev.baranhan.viltrumitecore.client.ultron;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.chitauri.GlowDraw;
import dev.baranhan.viltrumitecore.entity.ultron.UltronBrute;
import dev.baranhan.viltrumitecore.entity.ultron.UltronUnit;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronMove;
import java.util.List;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Brute: the heavy. A ponderous gait, the slam (both fists trail down into the ground), the swing, the missile pods,
 * the charge, and the deflector shield held up from the left forearm emitter: a hexagonal energy wall in front of the
 * body, fed by a beam from the emitter, rippling and flaring where it is hit. The left missile pod is blown off at
 * critical damage.
 */
public class UltronBruteRenderer extends UltronUnitRenderer<UltronBrute> {
   public UltronBruteRenderer(EntityRendererProvider.Context context) {
      super(context, UltronAssets.BRUTE, 1.0F, new String[]{"hand_r", "emitter_l", "head", "pod_r"}, new String[0], new String[0], new float[0][], "pod_l",
         List.of("head", "chest", "abdomen", "hips", "pod_r", "pod_l", "arm_r", "forearm_r", "arm_l", "forearm_l", "leg_r", "shin_r", "leg_l", "shin_l"));
   }

   @Override
   protected void extraPose(BakedGeoModel model, UltronBrute e, float partial, float age) {
      float hold = Mth.lerp(partial, e.clientHoldO, e.clientHold);
      Animation clip = this.clip("shield_hold");
      if (clip != null && hold > 0.01F && e.clientMoveSeconds(partial) < 0.0F) {
         over(clip, model, loop(age / 20.0F, clip), smooth(hold));
      }
   }

   @Override
   protected void extras(UltronBrute e, float partial, float age, float dying, PoseStack stack, VertexConsumer vc) {
      if (dying >= 0.0F) {
         return;
      }
      Matrix4f m = stack.last().pose();
      float t = e.clientMoveSeconds(partial);
      UltronMove move = t < 0.0F ? UltronMove.NONE : e.clientMove;
      float contact = move.firstContact() / 20.0F;
      boolean swing = move == UltronMove.BRUTE_SWING && t > contact - 0.2F && t < contact + 0.08F;
      boolean slam = move == UltronMove.BRUTE_SLAM && t > contact - 0.22F && t < contact + 0.04F;
      this.swing(e, A_RIGHT, partial, age, stack, vc, swing || slam, 3.5F, 0.32F, 255, 80, 40);
      this.swing(e, A_LEFT, partial, age, stack, vc, slam, 3.5F, 0.32F, 255, 80, 40);
      float hold = Mth.lerp(partial, e.clientHoldO, e.clientHold);
      if (hold > 0.02F || e.flag(UltronUnit.FLAG_SHIELD)) {
         this.shield(e, partial, age, m, vc, Math.max(hold, 0.15F));
      }
      if (move == UltronMove.BRUTE_CHARGE) {
         Vector3f head = this.anchor(A_HEAD);
         if (head != null) {
            this.orb(vc, m, head, 0.5F, 0.25F, 255, 50, 30, 90);
         }
      }
   }

   /** The hexagonal deflector wall in front of the body. */
   private void shield(UltronBrute e, float partial, float age, Matrix4f m, VertexConsumer vc, float k) {
      Vector3f fwd = facing(e, partial);
      Vector3f right = new Vector3f(-fwd.z, 0.0F, fwd.x);
      Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
      float h = e.getBbHeight();
      Vector3f c = new Vector3f(fwd).mul(e.getBbWidth() * 0.5F + 0.75F).add(0.0F, h * 0.52F, 0.0F);
      float r = 1.55F * smooth(k);
      float hit = e.hurtTime > 0 ? e.hurtTime / 10.0F : 0.0F;
      float shimmer = 0.85F + 0.15F * Mth.sin(age * 0.9F);
      int a = (int)((40 + 90 * hit) * shimmer * k);
      GlowDraw.disc(vc, m, c, right, up, r, 0.75F, 6, 255, 70, 40, a);
      GlowDraw.ring(vc, m, c, right, up, r * 0.9F, r, 0.6F, 6, 255, 110, 70, (int)((150 + 100 * hit) * k));
      for (int i = 1; i <= 3; i++) {
         float ri = r * (i / 3.6F) + 0.06F * Mth.sin(age * 0.5F + i);
         GlowDraw.ring(vc, m, c, right, up, ri * 0.94F, ri, 0.5F, 6, 255, 90, 60, (int)(55 * k * shimmer));
      }
      Vector3f emitter = this.anchor(A_LEFT);
      if (emitter != null) {
         Vector3f cam = this.toCam(e, partial, emitter);
         GlowDraw.ribbon(vc, m, emitter, c, cam, 0.12F, 0.5F, 255, 80, 50, (int)(140 * k), (int)(30 * k));
         this.orb(vc, m, emitter, 0.25F, 0.4F, 255, 120, 80, (int)(170 * k));
      }
   }
}
