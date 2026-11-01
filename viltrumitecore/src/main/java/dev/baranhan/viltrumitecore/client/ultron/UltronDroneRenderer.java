package dev.baranhan.viltrumitecore.client.ultron;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.chitauri.GlowDraw;
import dev.baranhan.viltrumitecore.entity.ultron.UltronDrone;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronMove;
import java.util.List;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Recon drone: hovers on four counter-rotating rotors (blur discs around the blades), banks into turns and pitches
 * with its acceleration; the scan sweeps a red fan from the eye, the designator paints its target with a laser, the
 * zapper charges at its tip before the shot. The antenna snaps off at critical damage; dying, it tumbles out of the
 * air and breaks apart.
 */
public class UltronDroneRenderer extends UltronUnitRenderer<UltronDrone> {
   private static final String[] ROTORS = {"rotor_f_r", "rotor_f_l", "rotor_b_r", "rotor_b_l"};
   private static final float[] SPIN = {1.0F, -1.0F, -1.0F, 1.0F};

   public UltronDroneRenderer(EntityRendererProvider.Context context) {
      super(context, UltronAssets.DRONE, 0.35F, new String[]{"zap_tip", null, "iris", "antenna"}, new String[0], ROTORS,
         new float[][]{{0.0F, 1.0F, 0.0F}, {0.0F, 1.0F, 0.0F}, {0.0F, 1.0F, 0.0F}, {0.0F, 1.0F, 0.0F}}, "antenna",
         List.of("body", "arm_f_r", "arm_f_l", "arm_b_r", "arm_b_l", "antenna", "zapper"));
   }

   @Override
   protected void base(BakedGeoModel model, UltronDrone e, float partial, float age, float speed, float combat) {
      this.hover(model, e, partial, age, speed);
   }

   @Override
   protected void extraPose(BakedGeoModel model, UltronDrone e, float partial, float age) {
      this.spin(model, age, 1.25F);
   }

   @Override
   protected void deathPose(BakedGeoModel model, UltronDrone e, float partial, float age, float dying) {
      // the rotors run down as it falls
      this.spin(model, age - dying + dying * 0.35F, 1.25F);
   }

   private void spin(BakedGeoModel model, float clock, float rate) {
      for (int i = 0; i < ROTORS.length; i++) {
         GeoBone rotor = model.getBone(ROTORS[i]);
         if (rotor != null) {
            rotor.rotY += SPIN[i] * clock * rate;
         }
      }
   }

   @Override
   protected void transform(PoseStack stack, UltronDrone e, float partial, float age) {
      super.transform(stack, e, partial, age);
      float dying = e.deathTime > 0 ? e.deathTime - 1 + partial : -1.0F;
      if (dying >= 0.0F) {
         float c = 6.4F / 16.0F;
         stack.translate(0.0F, c, 0.0F);
         stack.mulPose(Axis.YP.rotationDegrees(dying * 16.0F));
         stack.mulPose(Axis.ZP.rotationDegrees(Math.min(70.0F, dying * 6.0F)));
         stack.translate(0.0F, -c, 0.0F);
      } else {
         this.attitude(stack, e, partial, 6.4F, 1.0F, 1.0F);
      }
   }

   @Override
   protected void extras(UltronDrone e, float partial, float age, float dying, PoseStack stack, VertexConsumer vc) {
      Matrix4f m = stack.last().pose();
      Vector3f a = new Vector3f();
      Vector3f b = new Vector3f();
      float run = dying >= 0.0F ? Math.max(0.0F, 1.0F - dying / 14.0F) : 1.0F;
      for (int i = 0; i < ROTORS.length; i++) {
         if (this.nozzleFound[i] && run > 0.02F) {
            basis(this.nozzleDir[i], a, b);
            Vector3f c = new Vector3f(this.nozzleDir[i]).mul(0.02F).add(this.nozzleAt[i]);
            GlowDraw.ring(vc, m, c, a, b, 0.04F, 0.24F, 0.85F, 24, 190, 190, 200, (int)(26 * run));
         }
      }
      if (dying >= 0.0F) {
         return;
      }
      Vector3f eye = this.anchor(A_HEAD);
      Vector3f tip = this.anchor(A_RIGHT);
      float g = this.glow(e, age, dying);
      if (eye != null) {
         this.orb(vc, m, eye, 0.16F + 0.04F * e.clientCombat, 0.25F, 255, 40, 30, (int)(70 * g));
      }
      float t = e.clientMoveSeconds(partial);
      if (t < 0.0F) {
         return;
      }
      UltronMove move = e.clientMove;
      if (move == UltronMove.DRONE_ZAP && tip != null) {
         float contact = 9 / 20.0F;
         if (t < contact) {
            float k = t / contact;
            float crackle = ((int)(age * 3.0F) & 1) == 0 ? 1.0F : 0.75F;
            this.orb(vc, m, tip, 0.05F + 0.16F * k * crackle, 0.4F, 255, 70, 50, (int)(110 + 120 * k));
            this.orb(vc, m, tip, 0.03F + 0.05F * k, 0.6F, 255, 230, 220, (int)(200 * k));
         } else if (t < contact + 0.15F) {
            float f = 1.0F - (t - contact) / 0.15F;
            this.orb(vc, m, tip, 0.35F * f, 0.3F, 255, 120, 90, (int)(220 * f));
         }
      } else if (move == UltronMove.DRONE_SCAN && eye != null) {
         float len = move.length / 20.0F;
         float fade = smooth(t / 0.25F) * smooth((len - t) / 0.25F);
         Vector3f fwd = facing(e, partial);
         float sweep = Mth.sin(t / len * Mth.TWO_PI * 1.5F) * 50.0F * Mth.DEG_TO_RAD;
         Vector3f dir = new Vector3f(fwd).rotateY(sweep);
         dir.y = -0.38F;
         dir.normalize();
         Vector3f end = new Vector3f(dir).mul(9.0F).add(eye);
         Vector3f cam = this.toCam(e, partial, eye);
         GlowDraw.ribbon(vc, m, eye, end, cam, 0.03F, 1.9F, 255, 40, 30, (int)(70 * fade), 0);
         GlowDraw.ribbon(vc, m, eye, end, cam, 0.015F, 0.12F, 255, 120, 100, (int)(190 * fade), 0);
      } else if (move == UltronMove.DRONE_MARK && eye != null) {
         Entity target = e.aimId() >= 0 ? e.level().getEntity(e.aimId()) : null;
         if (target != null) {
            float contact = 12 / 20.0F;
            float len = move.length / 20.0F;
            float on = t < contact ? 0.35F + 0.25F * (((int)(age * 2.0F) & 1)) : 1.0F;
            on *= smooth((len - t) / 0.2F);
            Vec3 aim = new Vec3(Mth.lerp(partial, target.xo, target.getX()), Mth.lerp(partial, target.yo, target.getY()) + target.getBbHeight() * 0.6,
               Mth.lerp(partial, target.zo, target.getZ()));
            Vector3f end = rel(e, partial, aim);
            Vector3f cam = this.toCam(e, partial, eye);
            GlowDraw.ribbon(vc, m, eye, end, cam, 0.05F, 0.05F, 255, 30, 25, (int)(80 * on), (int)(80 * on));
            GlowDraw.ribbon(vc, m, eye, end, cam, 0.016F, 0.016F, 255, 160, 140, (int)(230 * on), (int)(230 * on));
            this.orb(vc, m, end, 0.22F, 0.3F, 255, 40, 30, (int)(160 * on));
         }
      }
   }
}
