package dev.baranhan.viltrumitecore.client.reinhard;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.pose.FirstPersonArm;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** First-person Reid rig: both camera arms hold ONE blade. Pure view-space math (base =
 * the RenderHandEvent stack), so tests replay the exact production chain:
 * [kit chop] · hero shoulder offset · vanilla renderPlayerArm · canonical ModelPart ·
 * PlayerGeoLayer conversion · geo bones. The support hand is a positional constraint on
 * that chain (ReinhardGrip), never a second weapon frame. */
final class ReinhardFirstPersonRig {
   /** Support palm on the pommel end of the hilt (native px), main palm by the guard. */
   static final float POMMEL_PX = -8.5F, GUARD_PX = 0;

   private ReinhardFirstPersonRig() {}

   /** ItemInHandRenderer.renderPlayerArm (1.20.1) up to the arm draw, as one matrix. */
   static Matrix4f vanilla(float side, float equip, float swing) {
      PoseStack s = new PoseStack();
      float f1 = Mth.sqrt(swing);
      float f2 = -0.3F * Mth.sin(f1 * (float)Math.PI);
      float f3 = 0.4F * Mth.sin(f1 * ((float)Math.PI * 2F));
      float f4 = -0.4F * Mth.sin(swing * (float)Math.PI);
      s.translate(side * (f2 + 0.64000005F), f3 + -0.6F + equip * -0.6F, f4 + -0.71999997F);
      s.mulPose(Axis.YP.rotationDegrees(side * 45.0F));
      float f5 = Mth.sin(swing * swing * (float)Math.PI);
      float f6 = Mth.sin(f1 * (float)Math.PI);
      s.mulPose(Axis.YP.rotationDegrees(side * f6 * 70.0F));
      s.mulPose(Axis.ZP.rotationDegrees(side * f5 * -20.0F));
      s.translate(side * -1.0F, 3.6F, 3.5F);
      s.mulPose(Axis.ZP.rotationDegrees(side * 120.0F));
      s.mulPose(Axis.XP.rotationDegrees(200.0F));
      s.mulPose(Axis.YP.rotationDegrees(side * -135.0F));
      s.translate(side * 5.6F, 0.0F, 0.0F);
      return new Matrix4f(s.last().pose());
   }

   /** The arm ModelPart as renderHand leaves it (setupAnim at age 0, EMPTY pose, xRot 0).
    * RenderArmEvent fires BEFORE that setupAnim, so the shared PlayerModel still carries the
    * last third-person pose of whatever player was drawn: never read it, set it. */
   static void rest(ModelPart arm, boolean left) {
      arm.x = left ? 5.0F : -5.0F;
      arm.y = 2.0F;
      arm.z = 0.0F;
      arm.xRot = 0.0F;
      arm.yRot = 0.0F;
      arm.zRot = left ? -0.1F : 0.1F;
   }

   /** {@link #rest} as a matrix (ModelPart.translateAndRotate). */
   static Matrix4f restFrame(boolean left) {
      return new Matrix4f().translation((left ? 5.0F : -5.0F) / 16.0F, 2.0F / 16.0F, 0.0F)
         .rotateZ(left ? -0.1F : 0.1F);
   }

   /** PlayerGeoLayer.partFrame after translateAndRotate: ModelPart pivot frame to geo model space. */
   static Matrix4f geo(boolean left) {
      return new Matrix4f().translation((left ? -5.0F : 5.0F) / 16.0F, 22.0F / 16.0F, 0.0F).scale(-1, -1, 1);
   }

   /** FirstPersonArm.apply of one offset (mirrored for the left arm). */
   static Matrix4f shoulder(ReinhardFirstPersonPose.Offset o, boolean left) {
      PoseStack s = new PoseStack();
      new FirstPersonArm().reset().blend(new float[]{o.pitch(), o.yaw(), o.roll(), o.x(), o.y(), o.z()}, 1)
         .apply(s, left ? -1 : 1);
      return new Matrix4f(s.last().pose());
   }

   /** ReinhardChopFirstPerson.apply as a matrix; null pose = no chop on this arm. */
   static Matrix4f chop(float[] pose, boolean left) {
      if (pose == null) return new Matrix4f();
      PoseStack s = new PoseStack();
      ReinhardChopFirstPerson.apply(s, pose, left);
      return new Matrix4f(s.last().pose());
   }

   /** Geo model space of one camera arm relative to the hand base. */
   static Matrix4f arm(ReinhardFirstPersonPose.Offset offset, boolean left, float equip, float swing, float[] chop) {
      return chop(chop, left).mul(shoulder(offset, left)).mul(vanilla(left ? -1 : 1, equip, swing))
         .mul(restFrame(left)).mul(geo(left));
   }

   /** The blade frame held by the right camera palm; {@code guard} (0..1) slides the
    * palm from the handle centre to the guard end so the support palm has the pommel. */
   static Matrix4f blade(Matrix4f rightGeo, BakedGeoModel arms, float guard) {
      return new Matrix4f(rightGeo).mul(ReinhardGrip.cameraHand(arms)).translate(0, ReinhardGrip.HANDLE.y * guard, 0);
   }

   /** Shoulder (pivot) of the support arm in its geo space. */
   static final Vector3f SUPPORT_ROOT = new Vector3f(-5 / 16F, 22 / 16F, 0);
   /** Two-handed guard: the support shoulder sits SUPPORT_REACH blocks from the pommel along
    * SUPPORT_DIR (view space; turned with the rig by a World Cut), low, left and behind the hilt,
    * so the arm reaches the pommel with a bent elbow and its root stays below the frame. */
   static final float SUPPORT_REACH = .5F;
   static final Vector3f SUPPORT_DIR = new Vector3f(-.6F, -.65F, .45F).normalize();

   /** View translation that carries the free support arm {@code leftFree} (its geo space in the
    * hand base, exactly as drawn) to the hilt of {@code blade}; {@code cut} turns SUPPORT_DIR. */
   static Vector3f supportShift(Matrix4f blade, Matrix4f cut, Matrix4f leftFree) {
      Vector3f pommel = blade.transformPosition(new Vector3f(0, POMMEL_PX / 16, 0));
      Vector3f root = pommel.add(cut.transformDirection(new Vector3f(SUPPORT_DIR)).mul(SUPPORT_REACH));
      return root.sub(leftFree.transformPosition(new Vector3f(SUPPORT_ROOT)));
   }

   /** Support-hand target on the blade, in the left arm's geo space. */
   static Matrix4f pommel(Matrix4f blade, Matrix4f leftGeo) {
      return new Matrix4f(leftGeo).invert().mul(ReinhardSword.gripAt(blade, POMMEL_PX));
   }

   /** Solve the left camera arm onto the pommel with weight {@code weight}; returns the
    * residual (px). The free pose (weight 0) is exactly the arm the left pass draws. */
   static float support(BakedGeoModel arms, Matrix4f blade, Matrix4f leftGeo, float weight) {
      if (weight <= 0) return 0;
      Matrix4f mount = pommel(blade, leftGeo);
      ReinhardGrip.paired(arms, mount, weight, true);
      return ReinhardGrip.point(arms, true).distance(mount.transformPosition(new Vector3f(ReinhardGrip.HANDLE))) * 16;
   }
}