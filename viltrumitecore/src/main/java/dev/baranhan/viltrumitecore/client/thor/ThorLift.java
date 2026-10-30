package dev.baranhan.viltrumitecore.client.thor;

import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoPose;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.thor.MjolnirPlace;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Thor rework R1 / R3: both palms on a planted Mjölnir's handle (MjolnirAnchors), the same rig in
 * third and first person. Who: anyone lifting (MjolnirLift's synced lifter ids), and Thor crouching
 * by his own planted hammer (a ready-to-pull hold; R2's pull-out clip starts from it).
 * <p>The lifter squares up to the handle: the body (visual only, the hitbox does not move) turns to
 * face it and slides to the body's grip distance, both by the blend weight. Thor's body squats
 * (lift_squat) and solves each arm with {@link GeoPose#reach}; a vanilla body bends and aims its
 * one-piece arms ({@link #poseVanilla}). First person draws the very same arms in view space.
 * <p>Model space here is BakedGeoModel's internal frame (blocks): the render frame after
 * setupRotations' {@code Ry(180 - yaw)}, before any scale.
 */
public final class ThorLift {
   /** Thor fists on the handle (px below the handle top): two 4.75 px wide fists on a 7.85 px handle touch. */
   public static final float THOR_UPPER_PX = 1.0F;
   public static final float THOR_LOWER_PX = 5.6F;
   /** Horizontal distance (blocks) from the body root to the handle when gripping. */
   public static final double THOR_DISTANCE = dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirTuning.PICKUP_STAND;
   /** Blend arc of the fists (model blocks): out and forward, peak at a third of the weight. */
   static final float ARC_OUT = 9.0F / 16.0F;
   static final float ARC_FORWARD = 10.0F / 16.0F;
   /**
    * Thor lift elbow pole, body space per right arm (x out, y up, z back; mirrored for the left):
    * mostly forward of the bent-over chest, so the elbows bend out over the hammer, not into the belly.
    * Searched together with lift_squat against ThorBodyGeometryTest's mesh scan.
    */
   static final Vector3f ELBOW_POLE = new Vector3f(-0.9F, 0.25F, -6.37F);
   public static final double VANILLA_DISTANCE = 0.16;
   /** Thor's ready hold: how close (horizontally, blocks) a crouching Thor must be to his planted hammer. */
   public static final double THOR_HOLD_RADIUS = 2.0;
   /** Blend in / out time (seconds). */
   public static final float BLEND_SECONDS = 0.3F;
   /** Vanilla body scale and model offset (PlayerRenderer.scale, LivingEntityRenderer translate). */
   static final float VANILLA_SCALE = 0.9375F;
   static final float VANILLA_LIFT = 1.501F;
   /** Vanilla arm grip point (px, arm part frame): the hand where ItemInHandLayer holds items. */
   static final Vector3f VANILLA_RIGHT_GRIP = new Vector3f(-1.0F, 9.0F, 0.0F);
   static final Vector3f VANILLA_LEFT_GRIP = new Vector3f(1.0F, 9.0F, 0.0F);
   /** Vanilla lifter bend: the body leans, legs straddle the hammer. */
   static final float VANILLA_BODY_LEAN = 0.5F;

   private static final Map<Player, Blend> BLENDS = new WeakHashMap<>();

   /** Per lifter: weight ramp and the last grip (kept for the fade-out after the hammer goes). */
   private static final class Blend {
      float weight;
      long lastNanos = Long.MIN_VALUE;
      MjolnirAnchors.Grip grip;
      int startTick;
      boolean active;
      /** The grip belongs to Thor's PICKUP (kept through the fade-out, while the hammer rises in his hand). */
      boolean pickup;
   }

   /**
    * One frame of the lift: the grip, its weight, the visual root (feet, world) and facing yaw.
    * {@code seconds} = time since the hold began (the lift_squat loop clock).
    */
   public record Frame(MjolnirAnchors.Grip grip, float weight, Vec3 root, float yaw, double seconds, boolean thorBody, boolean pickup) {
      public Frame(MjolnirAnchors.Grip grip, float weight, Vec3 root, float yaw, double seconds, boolean thorBody) {
         this(grip, weight, root, yaw, seconds, thorBody, false);
      }
   }

   private ThorLift() {
   }

   /** The planted hammer this player is holding right now, or null. */
   @Nullable
   static MjolnirAnchors.Grip activeGrip(Player player, float partialTick) {
      MjolnirAnchors.Grip lifting = MjolnirAnchors.lifting(player, partialTick);
      if (lifting != null) {
         return lifting;
      }

      HeroPublicSnapshot snapshot = ThorView.of(player);
      if (snapshot != null && pickupReach(snapshot)) {
         // PICKUP's bend (owner playtest: no instant pick-up): both hands go onto the handle before the hammer moves.
         MjolnirAnchors.Grip nearest = MjolnirAnchors.nearest(player, partialTick);
         if (nearest != null) {
            return nearest;
         }
      }

      if (snapshot == null || !player.isCrouching() || ThorView.place(snapshot, player) == MjolnirPlace.HAND) {
         return null;
      }

      MjolnirAnchors.Grip nearest = MjolnirAnchors.nearest(player, partialTick);
      if (nearest == null) {
         return null;
      }

      Vec3 feet = player.getPosition(partialTick);
      double dx = nearest.rest().x - feet.x, dz = nearest.rest().z - feet.z;
      return dx * dx + dz * dz <= THOR_HOLD_RADIUS * THOR_HOLD_RADIUS && Math.abs(nearest.rest().y - feet.y) < 1.0 ? nearest : null;
   }

   /** Thor is in PICKUP's bend (before the contact tick the hammer still rests where it stood). */
   static boolean pickupReach(HeroPublicSnapshot snapshot) {
      return ThorView.move(snapshot) == ThorMove.PICKUP && ThorView.phase(snapshot) == MovePhase.INTRO;
   }

   /** This frame's lift (weight ramps by real time), or null when the player is not holding a hammer. */
   @Nullable
   public static Frame frame(Player player, float partialTick, long nanos, boolean thorBody) {
      MjolnirAnchors.Grip grip = activeGrip(player, partialTick);
      Blend blend = BLENDS.computeIfAbsent(player, p -> new Blend());
      float dt = blend.lastNanos == Long.MIN_VALUE ? 0.0F : Math.max(0.0F, Math.min(0.25F, (nanos - blend.lastNanos) / 1.0E9F));
      blend.lastNanos = nanos;
      if (grip != null) {
         if (!blend.active) {
            blend.startTick = player.tickCount;
         }

         blend.active = true;
         blend.grip = grip;
         HeroPublicSnapshot snapshot = ThorView.of(player);
         blend.pickup = snapshot != null && pickupReach(snapshot);
      } else {
         blend.active = false;
      }

      blend.weight = ramp(blend.weight, blend.active, dt);
      if (blend.weight <= 0.0F || blend.grip == null) {
         blend.grip = null;
         return null;
      }

      Vec3 feet = player.getPosition(partialTick);
      float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
      float w = smooth(blend.weight);
      Vec3 handle = blend.grip.handleTop();
      float face = faceYaw(feet, handle, bodyYaw);
      Vec3 root = root(feet, handle, thorBody ? THOR_DISTANCE : VANILLA_DISTANCE);
      Vec3 visual = feet.add(root.subtract(feet).scale(w));
      float yaw = bodyYaw + Mth.wrapDegrees(face - bodyYaw) * w;
      double seconds = (player.tickCount - blend.startTick + partialTick) / 20.0;
      return new Frame(blend.grip, w, visual, yaw, seconds, thorBody, blend.pickup);
   }

   /** Pure: the weight one step on. */
   static float ramp(float weight, boolean active, float dt) {
      float step = dt / BLEND_SECONDS;
      return Math.max(0.0F, Math.min(1.0F, active ? weight + step : weight - step));
   }

   static float smooth(float t) {
      return t * t * (3.0F - 2.0F * t);
   }

   /** Pure: entity yaw (degrees) that faces {@code to} from {@code from}; {@code fallback} when on top. */
   public static float faceYaw(Vec3 from, Vec3 to, float fallback) {
      double dx = to.x - from.x, dz = to.z - from.z;
      if (dx * dx + dz * dz < 1.0E-8) {
         return fallback;
      }

      return (float)Math.toDegrees(Math.atan2(-dx, dz));
   }

   /** Pure: feet position {@code distance} (horizontal) short of the handle, on the line from the feet. */
   public static Vec3 root(Vec3 feet, Vec3 handle, double distance) {
      double dx = handle.x - feet.x, dz = handle.z - feet.z;
      double flat = Math.sqrt(dx * dx + dz * dz);
      if (flat < 1.0E-6) {
         return feet;
      }

      double k = (flat - distance) / flat;
      return new Vec3(feet.x + dx * k, feet.y, feet.z + dz * k);
   }

   /** Pure: world point to geo model space for a body at {@code root} facing {@code yaw} (setupRotations' Ry(180 - yaw)). */
   public static Vector3f toModel(Vec3 world, Vec3 root, float yaw) {
      Vector3f v = new Vector3f((float)(world.x - root.x), (float)(world.y - root.y), (float)(world.z - root.z));
      return new Quaternionf().rotationY((float)Math.toRadians(180.0F - yaw)).conjugate().transform(v);
   }

   /** Pure: world point to vanilla PlayerModel part space (px, y down) for a body at {@code root} facing {@code yaw}. */
   public static Vector3f toVanillaModel(Vec3 world, Vec3 root, float yaw) {
      Vector3f e = toModel(world, root, yaw);
      return new Vector3f(-e.x / VANILLA_SCALE * 16.0F, (-e.y / VANILLA_SCALE + VANILLA_LIFT) * 16.0F, e.z / VANILLA_SCALE * 16.0F);
   }

   /** Thor's palm targets (world): the right fist under the handle top, the left one below it. */
   public static Vec3[] thorPalms(MjolnirAnchors.Grip grip) {
      return new Vec3[]{grip.handleTop().subtract(grip.up().scale(THOR_UPPER_PX / 16.0)),
         grip.handleTop().subtract(grip.up().scale(THOR_LOWER_PX / 16.0))};
   }

   /**
    * The whole Thor-body lift over the current pose (third and first person): the lift_squat clip
    * at {@code seconds}, then both fists solved onto the handle. {@code root} is the model origin
    * in the world (the renderer's lift root, crouch offset included), {@code yaw} its facing.
    */
   public static void poseThor(BakedGeoModel body, MjolnirAnchors.Grip grip, Vec3 root, float yaw, double seconds, float weight) {
      poseThor(body, grip, root, yaw, seconds, weight, false);
   }

   /**
    * {@code pickup}: Thor's own PICKUP. The right fist takes the hammer the way it will carry it, head down (the
    * held hammer hangs on the grip's -Z), so nothing turns on the contact tick when the hammer changes into the hand
    * and the fade-out lifts it with the fist.
    */
   public static void poseThor(BakedGeoModel body, MjolnirAnchors.Grip grip, Vec3 root, float yaw, double seconds, float weight,
      boolean pickup) {
      ThorBodyPose.liftClip(body, seconds, weight);
      Vec3[] palms = thorPalms(grip);
      Vector3f up = new Vector3f(toModel(root.add(grip.up()), root, yaw));
      solveArm(body, ThorAssets.RIGHT_ARM, ThorAssets.RIGHT_FOREARM, ThorAssets.RIGHT_HAND, ThorAssets.MJOLNIR_GRIP, toModel(palms[0], root, yaw),
         pickup ? new Vector3f(up).negate() : up, weight, 1.0F);
      solveArm(body, ThorAssets.LEFT_ARM, ThorAssets.LEFT_FOREARM, ThorAssets.LEFT_HAND, ThorAssets.LEFT_GRIP, toModel(palms[1], root, yaw), up,
         weight, -1.0F);
   }

   /**
    * Thor body: both arms onto the handle by {@code weight}. Targets are model space (right fist,
    * left fist), {@code up} the handle axis in model space. The fists turn so the handle runs
    * along their width (index to pinky), thumbs up; elbows bend out and back.
    */
   public static void solveThor(BakedGeoModel model, Vector3f right, Vector3f left, Vector3f up, float weight) {
      solveArm(model, ThorAssets.RIGHT_ARM, ThorAssets.RIGHT_FOREARM, ThorAssets.RIGHT_HAND, ThorAssets.MJOLNIR_GRIP, right, up, weight, 1.0F);
      solveArm(model, ThorAssets.LEFT_ARM, ThorAssets.LEFT_FOREARM, ThorAssets.LEFT_HAND, ThorAssets.LEFT_GRIP, left, up, weight, -1.0F);
   }

   private static void solveArm(BakedGeoModel model, String arm, String forearm, String hand, String tip, Vector3f target, Vector3f up,
      float weight, float side) {
      GeoBone a = model.getBone(arm), f = model.getBone(forearm), h = model.getBone(hand), t = model.getBone(tip);
      if (a == null || f == null || h == null || t == null || weight <= 0.0F) {
         return;
      }

      Vector3f shoulder = GeoPose.point(a);
      Vector3f fk = GeoPose.point(t);
      Quaternionf fkHand = GeoPose.modelRotation(h);
      Vector3f goal = new Vector3f(fk).lerp(target, weight);
      // Early in the blend (peak at 1/3) the fists swing out and forward around the thighs instead of cutting through them.
      float arc = 6.75F * weight * (1.0F - weight) * (1.0F - weight);
      goal.add(ARC_OUT * side * arc, 0.0F, -ARC_FORWARD * arc);
      Quaternionf grip = gripRotation(shoulder, target, up, GeoPose.modelRotation(model.getBone(ThorAssets.BODY_BONE)));
      Quaternionf rotation = new Quaternionf(fkHand).slerp(grip, weight);
      // Elbow side: out (internal +x is the right side) and back (+z), a little up.
      Quaternionf body = GeoPose.modelRotation(model.getBone(ThorAssets.BODY_BONE));
      Vector3f pole = new Vector3f(shoulder).add(body.transform(new Vector3f(ELBOW_POLE.x * side, ELBOW_POLE.y, ELBOW_POLE.z)));
      GeoPose.reach(a, f, h, t, goal, pole, rotation);
   }

   /** Pure: fist rotation for a vertical grip: forearm line toward the handle, width along the handle, thumb up. */
   static Quaternionf gripRotation(Vector3f shoulder, Vector3f target, Vector3f up, Quaternionf body) {
      Vector3f rod = new Vector3f(up).normalize();
      Vector3f approach = new Vector3f(target).sub(shoulder);
      approach.sub(new Vector3f(rod).mul(approach.dot(rod)));
      if (approach.lengthSquared() < 1.0E-8F) {
         approach = body.transform(new Vector3f(0.0F, 0.0F, -1.0F));
         approach.sub(new Vector3f(rod).mul(approach.dot(rod)));
      }

      approach.normalize();
      Vector3f y = new Vector3f(approach).negate();
      Vector3f z = new Vector3f(rod).negate();
      Vector3f x = new Vector3f(y).cross(z).normalize();
      return new Quaternionf().setFromNormalized(new Matrix3f(x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z));
   }

   /**
    * Vanilla body (any player model): a bend with the legs straddling the hammer and both arms
    * aimed at R3's palm anchors. Targets in part space (px, {@link #toVanillaModel}). Writes the
    * parts like setupAnim would (called at its tail); weight blends from the current pose.
    */
   public static void poseVanilla(PlayerModel<?> model, Vector3f upper, Vector3f lower, float weight, float tremble) {
      float w = weight;
      lerp(model.body, VANILLA_BODY_LEAN, 0.0F, 0.0F, w);
      model.body.y = Mth.lerp(w, model.body.y, 3.2F);
      model.head.y = Mth.lerp(w, model.head.y, 4.2F);
      model.head.xRot = Mth.lerp(w, model.head.xRot, 0.1F);
      model.head.yRot = Mth.lerp(w, model.head.yRot, 0.0F);
      model.rightArm.y = Mth.lerp(w, model.rightArm.y, 5.2F);
      model.leftArm.y = Mth.lerp(w, model.leftArm.y, 5.2F);
      model.rightLeg.z = Mth.lerp(w, model.rightLeg.z, 4.0F);
      model.leftLeg.z = Mth.lerp(w, model.leftLeg.z, 4.0F);
      model.rightLeg.y = Mth.lerp(w, model.rightLeg.y, 12.2F);
      model.leftLeg.y = Mth.lerp(w, model.leftLeg.y, 12.2F);
      lerp(model.rightLeg, -0.12F, 0.0F, 0.32F, w);
      lerp(model.leftLeg, 0.12F, 0.0F, -0.32F, w);
      aim(model.rightArm, VANILLA_RIGHT_GRIP, upper, w, tremble);
      aim(model.leftArm, VANILLA_LEFT_GRIP, lower, w, -tremble);
   }

   private static void lerp(ModelPart part, float x, float y, float z, float w) {
      part.xRot = Mth.lerp(w, part.xRot, x);
      part.yRot = Mth.lerp(w, part.yRot, y);
      part.zRot = Mth.lerp(w, part.zRot, z);
   }

   /** Pure: turns a one-piece arm so its grip point lies on the line to the target (radians, ModelPart order). */
   static Vector3f aimRotation(Vector3f pivot, Vector3f grip, Vector3f target) {
      Vector3f to = new Vector3f(target).sub(pivot);
      if (to.lengthSquared() < 1.0E-8F) {
         return new Vector3f();
      }

      Quaternionf q = new Quaternionf().rotationTo(new Vector3f(grip).normalize(), to.normalize());
      return GeoPose.euler(q);
   }

   private static void aim(ModelPart arm, Vector3f grip, Vector3f target, float w, float tremble) {
      Vector3f e = aimRotation(new Vector3f(arm.x, arm.y, arm.z), grip, target);
      arm.xRot = Mth.lerp(w, arm.xRot, e.x + tremble);
      arm.yRot = Mth.lerp(w, arm.yRot, e.y);
      arm.zRot = Mth.lerp(w, arm.zRot, e.z);
   }

   /** Vanilla arm reach (px, model scale): pivot to grip. */
   static float vanillaReach() {
      return VANILLA_RIGHT_GRIP.length();
   }
}
