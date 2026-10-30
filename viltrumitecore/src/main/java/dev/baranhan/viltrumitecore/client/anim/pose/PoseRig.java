package dev.baranhan.viltrumitecore.client.anim.pose;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Shared pose rig for hero model / first-person layers (first used by
 * Regulus, then Iron Man). Follows the Viltrumite pattern (PunchModelMixin):
 * keyframes in degrees ({@link PoseKeys}), explicit pivot positions over the
 * vanilla bases, mirroring through {@code m = leftSide ? -1 : 1}, and outer
 * layers copied after the pose. {@link #finish} keeps torso, head and arms
 * attached when a layer leans or twists the body, the same correction Punch
 * keys by hand (body z -4.2 at 23.5 deg).
 */
public final class PoseRig {
   /** Base pivots {standX, standY, standZ, crouchX, crouchY, crouchZ}, crouch pitch in deg. */
   public enum Pivot {
      HEAD(0.0F, 0.0F, 0.0F, 0.0F, 4.2F, 0.0F, 0.0F),
      BODY(0.0F, 0.0F, 0.0F, 0.0F, 3.2F, 0.0F, 28.648F),
      RIGHT_ARM(-5.0F, 2.0F, 0.0F, -5.0F, 5.2F, 0.0F, 22.918F),
      LEFT_ARM(5.0F, 2.0F, 0.0F, 5.0F, 5.2F, 0.0F, 22.918F),
      RIGHT_LEG(-1.9F, 12.0F, 0.1F, -1.9F, 12.2F, 4.0F, 0.0F),
      LEFT_LEG(1.9F, 12.0F, 0.1F, 1.9F, 12.2F, 4.0F, 0.0F);

      final float[] stand;
      final float[] crouch;
      final float crouchPitch;

      Pivot(float sx, float sy, float sz, float cx, float cy, float cz, float crouchPitch) {
         this.stand = new float[]{sx, sy, sz};
         this.crouch = new float[]{cx, cy, cz};
         this.crouchPitch = crouchPitch;
      }
   }

   private static final float TORSO_LENGTH = 12.0F;
   private static final Set<PlayerModel<?>> DIRTY = Collections.newSetFromMap(new WeakHashMap<>());
   private static final PoseRig RIG = new PoseRig();
   private static final float[] BASE = new float[6];
   private static final Quaternionf QA = new Quaternionf();
   private static final Quaternionf QB = new Quaternionf();
   private static final Vector3f EULER = new Vector3f();

   public PlayerModel<?> model;
   public float m;
   public boolean crouch;
   public ModelPart mainArm;
   public ModelPart offArm;
   public ModelPart mainLeg;
   public ModelPart offLeg;
   public Pivot mainArmPivot;
   public Pivot offArmPivot;
   public Pivot mainLegPivot;
   public Pivot offLegPivot;
   @Nullable
   private ModelPart cloak;
   private float vanillaPitch;
   private boolean slerp;

   private PoseRig() {
   }

   /** Undo pivot offsets the vanilla setupAnim never resets (head/body x,z, leg x). Call at setupAnim HEAD. */
   public static void restorePivots(PlayerModel<?> model) {
      if (!DIRTY.remove(model)) {
         return;
      }
      model.head.x = 0.0F;
      model.head.z = 0.0F;
      model.body.x = 0.0F;
      model.body.z = 0.0F;
      model.rightLeg.x = -1.9F;
      model.leftLeg.x = 1.9F;
   }

   /** Marks a model whose head/body/leg pivots a layer moved outside the rig (restored at the next HEAD). */
   public static void markDirty(PlayerModel<?> model) {
      DIRTY.add(model);
   }

   /** Per-frame reset done once, by the lowest-priority layer of a hero. */
   public static void beginFrame(PlayerModel<?> model) {
      model.head.zRot = 0.0F;
      model.body.yRot = 0.0F;
      model.body.zRot = 0.0F;
   }

   /** Rig mirrored by the entity's main arm. */
   public static PoseRig begin(PlayerModel<?> model, LivingEntity entity, @Nullable ModelPart cloak) {
      return RIG.start(model, entity.isCrouching(), entity.getMainArm() == HumanoidArm.LEFT ? -1.0F : 1.0F, cloak);
   }

   /** Rig mirrored by an explicit side (+1 right, -1 left), e.g. the book hand. */
   public static PoseRig begin(PlayerModel<?> model, LivingEntity entity, float side, @Nullable ModelPart cloak) {
      return RIG.start(model, entity.isCrouching(), side, cloak);
   }

   /** Rig with an explicit crouch flag: pose tables that do not read the entity (and their tests). */
   public static PoseRig begin(PlayerModel<?> model, boolean crouch, float side, @Nullable ModelPart cloak) {
      return RIG.start(model, crouch, side, cloak);
   }

   /** +1 for a right-side hand, -1 for left. */
   public static float handSide(Player player, InteractionHand hand) {
      HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
      return arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
   }

   /**
    * First-person arm/item transform. Rows are {@code {tick, pitch, yaw, roll,
    * x, y, z}}; x/yaw/roll are mirrored by {@code side}, positive x is outward.
    */
   public static void firstPerson(PoseStack poseStack, float[][] keys, float elapsed, float weight, float side) {
      float x = PoseKeys.sample(keys, elapsed, 3, 0.0F, true) * side * weight;
      float y = PoseKeys.sample(keys, elapsed, 4, 0.0F, true) * weight;
      float z = PoseKeys.sample(keys, elapsed, 5, 0.0F, true) * weight;
      float pitch = PoseKeys.sample(keys, elapsed, 0, 0.0F, true) * weight;
      float yaw = PoseKeys.sample(keys, elapsed, 1, 0.0F, true) * side * weight;
      float roll = PoseKeys.sample(keys, elapsed, 2, 0.0F, true) * side * weight;
      firstPersonRaw(poseStack, pitch, yaw, roll, x, y, z);
   }

   public static void firstPersonRaw(PoseStack poseStack, float pitch, float yaw, float roll, float x, float y, float z) {
      poseStack.translate(x, y, z);
      poseStack.mulPose(new Quaternionf().rotateX((float)Math.toRadians(pitch)));
      poseStack.mulPose(new Quaternionf().rotateY((float)Math.toRadians(yaw)));
      poseStack.mulPose(new Quaternionf().rotateZ((float)Math.toRadians(roll)));
   }

   private PoseRig start(PlayerModel<?> model, boolean crouch, float side, @Nullable ModelPart cloak) {
      this.model = model;
      this.m = side;
      this.crouch = crouch;
      this.slerp = false;
      boolean right = side > 0.0F;
      this.mainArm = right ? model.rightArm : model.leftArm;
      this.offArm = right ? model.leftArm : model.rightArm;
      this.mainLeg = right ? model.rightLeg : model.leftLeg;
      this.offLeg = right ? model.leftLeg : model.rightLeg;
      this.mainArmPivot = right ? Pivot.RIGHT_ARM : Pivot.LEFT_ARM;
      this.offArmPivot = right ? Pivot.LEFT_ARM : Pivot.RIGHT_ARM;
      this.mainLegPivot = right ? Pivot.RIGHT_LEG : Pivot.LEFT_LEG;
      this.offLegPivot = right ? Pivot.LEFT_LEG : Pivot.RIGHT_LEG;
      this.cloak = cloak;
      this.vanillaPitch = this.crouch ? 0.5F : 0.0F;
      this.hipLock(-1.0F);
      return this;
   }

   /**
    * The next pose calls of this rig blend rotations on the shortest arc
    * (quaternion slerp in the ZYX order of ModelPart) instead of per Euler
    * channel. Distant poses (a strike from the walk, an aim from the flight
    * pose) then never twist the limb through a third orientation. Positions
    * stay linear. Cleared by every begin.
    */
   public PoseRig slerp() {
      this.slerp = true;
      return this;
   }

   /**
    * Head keys are small additive offsets on the look direction: always the Euler path. The
    * quaternion round trip of {@link #slerp} picked the other Euler solution once the look plus
    * the key passed ±90° pitch (head flipped/twisted for a frame); {@link #finish} then limits
    * the neck against the torso.
    */
   public void head(float[][] keys, float elapsed, float weight) {
      boolean wasSlerp = this.slerp;
      this.slerp = false;
      this.pose(this.model.head, Pivot.HEAD, keys, elapsed, weight, true);
      this.slerp = wasSlerp;
   }

   /** Neck limits against the torso, radians: yaw (vanilla keeps the body within 75° of the head), pitch, roll. */
   static final float NECK_YAW = (float)Math.toRadians(80.0);
   static final float NECK_PITCH = (float)Math.toRadians(90.0);
   static final float NECK_ROLL = (float)Math.toRadians(40.0);

   /**
    * Pure: the head angle relative to the torso clamped to ±limit (wrapped first, so a
    * 350° yaw is read as -10°, not clamped to +limit); NaN collapses onto the torso.
    */
   static float neck(float head, float torso, float limit) {
      if (Float.isNaN(head) || Float.isInfinite(head)) {
         return torso;
      }
      float rel = Mth.wrapDegrees((float)Math.toDegrees(head - torso));
      float lim = (float)Math.toDegrees(limit);
      return torso + (float)Math.toRadians(Mth.clamp(rel, -lim, lim));
   }

   /**
    * Layered twists/leans of the torso plus the look made owl necks: a strike twisting the body
    * 40° while looking 60° aside, a forward lean while looking up. Only angles past a human
    * neck are corrected, the look inside the limits is untouched.
    */
   private void neckLimit() {
      limitNeck(this.model, this.vanillaPitch);
   }

   /** The same neck limits for posers outside the rig (e.g. Homelander's laser/roar layer). */
   public static void limitNeck(PlayerModel<?> model) {
      limitNeck(model, model.crouching ? 0.5F : 0.0F);
   }

   private static void limitNeck(PlayerModel<?> model, float vanillaPitch) {
      model.head.yRot = neck(model.head.yRot, model.body.yRot, NECK_YAW);
      model.head.xRot = neck(model.head.xRot, model.body.xRot - vanillaPitch, NECK_PITCH);
      model.head.zRot = neck(model.head.zRot, model.body.zRot, NECK_ROLL);
   }

   public void body(float[][] keys, float elapsed, float weight, boolean additive) {
      this.pose(this.model.body, Pivot.BODY, keys, elapsed, weight, additive);
   }

   public void mainArm(float[][] keys, float elapsed, float weight, boolean additive) {
      this.pose(this.mainArm, this.mainArmPivot, keys, elapsed, weight, additive);
   }

   public void offArm(float[][] keys, float elapsed, float weight, boolean additive) {
      this.pose(this.offArm, this.offArmPivot, keys, elapsed, weight, additive);
   }

   public void mainLeg(float[][] keys, float elapsed, float weight, boolean additive) {
      this.pose(this.mainLeg, this.mainLegPivot, keys, elapsed, weight, additive);
   }

   public void offLeg(float[][] keys, float elapsed, float weight, boolean additive) {
      this.pose(this.offLeg, this.offLegPivot, keys, elapsed, weight, additive);
   }

   /**
    * Pose one part from a timeline. Absolute rows replace the part's
    * rotation (plus the vanilla crouch pitch) and set position as
    * base pivot + offset; additive rows add to what is underneath.
    */
   public void pose(ModelPart part, Pivot pivot, float[][] keys, float elapsed, float weight, boolean additive) {
      if (weight <= 0.001F) {
         return;
      }
      float[] pivotBase = this.crouch ? pivot.crouch : pivot.stand;
      BASE[0] = (float)Math.toDegrees(part.xRot);
      BASE[1] = (float)Math.toDegrees(part.yRot);
      BASE[2] = (float)Math.toDegrees(part.zRot);
      BASE[3] = part.x;
      BASE[4] = part.y;
      BASE[5] = part.z;
      float at = PoseKeys.locate(keys, elapsed);
      int i = (int)at;
      float t = at - (float)i;
      float[] from = keys[i];
      float[] to = i + 1 < keys.length ? keys[i + 1] : from;
      float[] out = new float[6];
      if (this.slerp) {
         this.slerpPose(part, from, to, t, weight, pivot, pivotBase, additive, out);
      } else {
         for (int c = 0; c < 6; c++) {
            float a = this.endpoint(from, c, pivot, pivotBase, additive);
            float b = this.endpoint(to, c, pivot, pivotBase, additive);
            out[c] = Mth.lerp(weight, BASE[c], Mth.lerp(t, a, b));
         }
         part.xRot = (float)Math.toRadians(out[0]);
         part.yRot = (float)Math.toRadians(out[1]);
         part.zRot = (float)Math.toRadians(out[2]);
      }
      part.x = out[3];
      part.y = out[4];
      part.z = out[5];
      DIRTY.add(this.model);
   }

   /** Slerp form of {@link #pose}: the rotation is set here, positions go to {@code out[3..5]}. */
   private void slerpPose(ModelPart part, float[] from, float[] to, float t, float weight, Pivot pivot, float[] pivotBase, boolean additive, float[] out) {
      for (int c = 3; c < 6; c++) {
         float a = this.endpoint(from, c, pivot, pivotBase, additive);
         float b = this.endpoint(to, c, pivot, pivotBase, additive);
         out[c] = Mth.lerp(weight, BASE[c], Mth.lerp(t, a, b));
      }
      rotation(from, pivot, pivotBase, additive, QA);
      if (t > 0.0F && to != from) {
         rotation(to, pivot, pivotBase, additive, QB);
         QA.slerp(QB, t);
      }
      QB.rotationZYX(part.zRot, part.yRot, part.xRot);
      QB.slerp(QA, Math.min(1.0F, weight));
      QB.getEulerAnglesZYX(EULER);
      part.xRot = EULER.x;
      part.yRot = EULER.y;
      part.zRot = EULER.z;
   }

   private void rotation(float[] row, Pivot pivot, float[] pivotBase, boolean additive, Quaternionf out) {
      float x = (float)Math.toRadians(this.endpoint(row, 0, pivot, pivotBase, additive));
      float y = (float)Math.toRadians(this.endpoint(row, 1, pivot, pivotBase, additive));
      float z = (float)Math.toRadians(this.endpoint(row, 2, pivot, pivotBase, additive));
      out.rotationZYX(z, y, x);
   }

   private float endpoint(float[] row, int channel, Pivot pivot, float[] pivotBase, boolean additive) {
      if (PoseKeys.isUnder(row)) {
         return BASE[channel];
      }
      float value = row[channel + 1];
      if (channel == 1 || channel == 2 || channel == 3) {
         value *= this.m;
      }
      if (additive) {
         return BASE[channel] + value;
      }
      return switch (channel) {
         case 0 -> value + (this.crouch ? pivot.crouchPitch : 0.0F);
         case 1, 2 -> value;
         default -> pivotBase[channel - 3] + value;
      };
   }

   /** Additive tweak in degrees (breathing, sway); yaw/roll mirrored. */
   public void add(ModelPart part, float pitch, float yaw, float roll) {
      part.xRot += (float)Math.toRadians(pitch);
      part.yRot += (float)Math.toRadians(yaw * this.m);
      part.zRot += (float)Math.toRadians(roll * this.m);
   }

   /**
    * Keeps the torso attached to the hips: the body pivots at the neck, so
    * a lean of d moves the hips by 12*sin(d); shift neck, head and arms the
    * other way instead. A body twist orbits the shoulders around the spine
    * and turns the arms with it, like vanilla's swing does. The lock is
    * always derived from the total lean against vanilla, so stacked layers
    * stay exact: {@link #start} removes the previous layer's lock, finish
    * re-applies it for the final body rotation.
    */
   private void hipLock(float sign) {
      PlayerModel<?> model = this.model;
      float pitch = model.body.xRot - this.vanillaPitch;
      float yaw = model.body.yRot * sign;
      float roll = model.body.zRot;
      ModelPart[] upper = {model.head, model.body, model.rightArm, model.leftArm};
      if (Math.abs(pitch) > 1.0E-4F) {
         float dz = -TORSO_LENGTH * Mth.sin(pitch) * sign;
         float dy = TORSO_LENGTH * (1.0F - Mth.cos(pitch)) * sign;
         for (ModelPart part : upper) {
            part.z += dz;
            part.y += dy;
         }
         if (this.cloak != null) {
            this.cloak.z += dz;
            this.cloak.y += dy;
            this.cloak.xRot += pitch * sign;
         }
      }
      if (Math.abs(roll) > 1.0E-4F) {
         float dx = TORSO_LENGTH * Mth.sin(roll) * sign;
         for (ModelPart part : upper) {
            part.x += dx;
         }
      }
      if (Math.abs(yaw) > 1.0E-4F) {
         float cos = Mth.cos(yaw);
         float sin = Mth.sin(yaw);
         for (ModelPart arm : new ModelPart[]{model.rightArm, model.leftArm}) {
            float rx = arm.x - model.body.x;
            float rz = arm.z - model.body.z;
            arm.x = model.body.x + rx * cos + rz * sin;
            arm.z = model.body.z - rx * sin + rz * cos;
            arm.yRot += yaw;
         }
      }
   }

   /** Hip lock + outer layers, mandatory after every pose layer. */
   public void finish() {
      this.neckLimit();
      this.hipLock(1.0F);
      DIRTY.add(this.model);
      copyLayers(this.model);
   }

   /** Outer skin layers follow their parts (animation-system §3.2 rule 7). */
   public static void copyLayers(PlayerModel<?> model) {
      model.hat.copyFrom(model.head);
      model.jacket.copyFrom(model.body);
      model.rightSleeve.copyFrom(model.rightArm);
      model.leftSleeve.copyFrom(model.leftArm);
      model.rightPants.copyFrom(model.rightLeg);
      model.leftPants.copyFrom(model.leftLeg);
   }
}
