package dev.baranhan.viltrumitecore.client.regulus;

import dev.baranhan.viltrumitecore.client.anim.RealTime;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.sinclair.Spring;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusMove;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Layers Regulus' clips. Locomotion is phased by the real horizontal distance the drawn body
 * covers (one cycle per stride, the clips' IK feet move back exactly as fast), so planted feet do
 * not slide; the pelvis turns into the walking direction (backwards walks run the cycle in
 * reverse) and, standing, the legs lag the body yaw and catch up with a two-step turn. Moves are
 * overlaid from the synced snapshot clock; while he walks during an upper-body move the legs keep
 * walking. Coat and hair ride springs. The fp_ clips drive the first-person arms.
 */
public final class RegulusPoser {
   /** Blocks per gait cycle; equal to make_regulus_clips.py WALK/RUN/SNEAK_STRIDE. */
   static final float WALK_STRIDE = 1.25F;
   static final float RUN_STRIDE = 1.9F;
   static final float SNEAK_STRIDE = 0.9F;
   /** Leg yaw lag (degrees) that starts a turn step while standing; the hard twist limit. */
   static final float TURN_START = 45.0F;
   static final float TURN_LIMIT = 75.0F;
   static final float TURN_TICKS = 10.0F;
   static final float WALK_YAW_LIMIT = 60.0F;
   static final Set<String> LEGS = Set.of("hips", "right_thigh", "right_shin", "right_foot", "right_toe", "left_thigh", "left_shin", "left_foot",
      "left_toe", "coat_front_r", "coat_front_l", "coat_back", "coat_back_lower");
   private static final Map<Entity, State> STATES = new WeakHashMap<>();

   private RegulusPoser() {
   }

   /** Pure: blend weight of a timed move at {@code ticks} of {@code length}: eased in and out. */
   static float moveWeight(RegulusMove move, double ticks) {
      double in = move == RegulusMove.ULT ? 6.0 : move.isTouch() ? 1.5 : 3.0;
      double out = move == RegulusMove.ULT ? 6.0 : 4.0;
      double w = Math.min(1.0, (ticks + 1.0) / in) * Math.min(1.0, (move.length() - ticks) / out);
      return (float) Mth.clamp(w, 0.0, 1.0);
   }

   /** Pure: does the walk keep the legs during this move (upper-body moves) or does the clip own them. */
   static boolean legsWalk(@Nullable RegulusMove move) {
      return move != RegulusMove.PROCESSION && move != RegulusMove.ULT;
   }

   /** Pure: stride (blocks per cycle) for the current blend of walk, run and sneak. */
   static float stride(float run, float crouch) {
      float s = WALK_STRIDE + (RUN_STRIDE - WALK_STRIDE) * run;
      return s + (SNEAK_STRIDE - s) * crouch;
   }

   /**
    * Pure: pelvis yaw (degrees, + = towards the model's right) and cycle direction (+1 forward, -1
    * backwards) for a horizontal step (forward, sideways-right components in blocks).
    */
   static float[] walkYaw(double forward, double right) {
      double len = Math.sqrt(forward * forward + right * right);
      if (len < 1.0E-5) {
         return new float[]{0.0F, 1.0F};
      }

      float dir = forward < -0.25 * len ? -1.0F : 1.0F;
      double yaw = Math.toDegrees(Math.atan2(right * dir, forward * dir));
      return new float[]{(float) Mth.clamp(yaw, -WALK_YAW_LIMIT, WALK_YAW_LIMIT), dir};
   }

   /** Third-person pose of a Regulus player; returns the move ticks it used. */
   public static double posePlayer(BakedGeoModel model, LivingEntity player, HeroPublicSnapshot snapshot, float partialTick, float bodyYaw) {
      RegulusMove move = RegulusView.move(snapshot);
      double ticks = RegulusView.moveTicks(player, snapshot, partialTick);
      State s = STATES.computeIfAbsent(player, e -> new State());
      s.update(player, partialTick, bodyYaw);
      locomotion(model, s);
      float moveWeight = 0.0F;
      if (move != null) {
         moveWeight = moveWeight(move, ticks);
         overlay(model, move.clip(), ticks / 20.0, moveWeight);
         if (legsWalk(move) && s.walk > 0.02F) {
            walkLegs(model, s, s.walk * moveWeight);
         }
      }

      if (s.turning(player, partialTick) && (move == null || legsWalk(move))) {
         Animation turn = RegulusAssets.clip(s.turnLeft ? "turn_l" : "turn_r");
         if (turn != null) {
            overlayBones(turn, model, s.turnTicks(player, partialTick) / 20.0, 1.0F - s.walk, LEGS);
         }
      }

      double sinceLand = player.tickCount + partialTick - s.landAt;
      if (sinceLand >= 0.0 && sinceLand < 8.0) {
         additive(model, "land", sinceLand / 20.0, s.landPower);
      }

      float hipsYaw = Mth.lerp(s.walk, s.lagYaw(), s.walkYaw);
      if (move == RegulusMove.PROCESSION || move == RegulusMove.ULT) {
         hipsYaw *= 1.0F - moveWeight;
      }

      rotY(model, "hips", hipsYaw);
      rotY(model, "torso", -hipsYaw);
      float look = move == null ? 1.0F : move.locksCasting() ? 1.0F - 0.75F * moveWeight : 1.0F - 0.45F * moveWeight;
      head(model, player, partialTick, bodyYaw, look);
      springs(model, s, player, partialTick);
      return ticks;
   }

   /** First-person arms (regulus_arms.geo): fp_idle in real time and the synced fp_ move clip. */
   public static void poseArms(BakedGeoModel arms, LivingEntity player, HeroPublicSnapshot snapshot, float partialTick) {
      RegulusMove move = RegulusView.move(snapshot);
      double ticks = RegulusView.moveTicks(player, snapshot, partialTick);
      Animation idle = RegulusAssets.clip("fp_idle");
      if (idle != null) {
         AnimationController.seek(idle, arms, loop(idle, RealTime.seconds()));
      } else {
         arms.resetBones();
      }

      if (move != null) {
         overlay(arms, "fp_" + move.clip(), ticks / 20.0, moveWeight(move, ticks));
      }
   }

   /** Idle pose for GUI previews (portrait, head preview). */
   static void poseIdle(BakedGeoModel model) {
      Animation idle = RegulusAssets.clip("idle");
      if (idle != null) {
         AnimationController.seek(idle, model, loop(idle, RealTime.seconds()));
      } else {
         model.resetBones();
      }
   }

   private static void locomotion(BakedGeoModel model, State s) {
      poseIdle(model);
      double cycle = s.phase - Math.floor(s.phase);
      Animation walk = RegulusAssets.clip("walk");
      Animation run = RegulusAssets.clip("run");
      Animation sneak = RegulusAssets.clip("crouch_walk");
      if (s.walk > 0.01F) {
         float plain = s.walk * (1.0F - s.crouch);
         if (walk != null && plain * (1.0F - s.run) > 0.01F) {
            AnimationController.overlay(walk, model, cycle * walk.lengthSeconds(), plain * (1.0F - s.run));
         }

         if (run != null && plain * s.run > 0.01F) {
            AnimationController.overlay(run, model, cycle * run.lengthSeconds(), plain * s.run);
         }
      }

      overlay(model, "crouch", 0.0, s.crouch * (1.0F - s.walk));
      if (sneak != null && s.walk * s.crouch > 0.01F) {
         AnimationController.overlay(sneak, model, cycle * sneak.lengthSeconds(), s.walk * s.crouch);
      }

      overlay(model, "air", RealTime.seconds(), s.air);
   }

   /** Re-applies the locomotion legs over a move clip (the upper body keeps the move). */
   private static void walkLegs(BakedGeoModel model, State s, float weight) {
      double cycle = s.phase - Math.floor(s.phase);
      String name = s.crouch > 0.5F ? "crouch_walk" : s.run > 0.5F ? "run" : "walk";
      Animation clip = RegulusAssets.clip(name);
      if (clip != null) {
         overlayBones(clip, model, cycle * clip.lengthSeconds(), weight * (1.0F - s.air), LEGS);
      }
   }

   private static void head(BakedGeoModel model, LivingEntity entity, float partialTick, float bodyYaw, float weight) {
      GeoBone head = model.getBone("head");
      GeoBone neck = model.getBone("neck");
      if (head == null || weight <= 0.0F) {
         return;
      }

      float yaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot) - bodyYaw), -75.0F, 75.0F);
      float pitch = entity.getViewXRot(partialTick);
      // the neck takes a third: the look turns the whole collar line, not only the skull
      head.rotY += (float) Math.toRadians(-yaw * 0.67F) * weight;
      head.rotX += (float) Math.toRadians(-pitch * 0.7F) * weight;
      if (neck != null) {
         neck.rotY += (float) Math.toRadians(-yaw * 0.33F) * weight;
         neck.rotX += (float) Math.toRadians(-pitch * 0.3F) * weight;
      }
   }

   private static void springs(BakedGeoModel model, State s, LivingEntity player, float partialTick) {
      float dt = s.dt;
      float speed = Math.min(0.5F, s.speed);
      float fall = (float) Mth.clamp(-(player.getY() - player.yo) * 40.0, -30.0, 30.0);
      float back = s.coatBack.step(speed * 70.0F + fall * 0.6F, dt);
      float front = s.coatFront.step(-speed * 18.0F + fall * 0.3F, dt);
      float hair = s.hair.step(speed * 30.0F + fall * 0.4F, dt);
      addX(model, "coat_back", back);
      addX(model, "coat_back_lower", back * 0.45F);
      addX(model, "coat_front_r", front);
      addX(model, "coat_front_l", front);
      addX(model, "hair_back", hair);
   }

   static void overlay(BakedGeoModel model, String name, double seconds, float weight) {
      if (weight <= 0.0F) {
         return;
      }

      Animation clip = RegulusAssets.clip(name);
      if (clip != null) {
         AnimationController.overlay(clip, model, loop(clip, seconds), weight);
      }
   }

   static void additive(BakedGeoModel model, String name, double seconds, float weight) {
      Animation clip = RegulusAssets.clip(name);
      if (clip != null && weight > 0.0F) {
         AnimationController.additive(clip, model, Math.min(seconds, clip.lengthSeconds()), weight);
      }
   }

   /** Overlay restricted to a bone set (blend from the current pose by weight). */
   static void overlayBones(Animation clip, BakedGeoModel model, double seconds, float weight, Set<String> bones) {
      float w = Mth.clamp(weight, 0.0F, 1.0F);
      if (w <= 0.0F) {
         return;
      }

      double t = loop(clip, seconds);
      for (Animation.BoneAnimation ba : clip.boneAnimations().values()) {
         if (!bones.contains(ba.boneName())) {
            continue;
         }

         GeoBone bone = model.getBone(ba.boneName());
         if (bone == null) {
            continue;
         }

         bone.rotX = Mth.lerp(w, bone.rotX, bone.initRotX + ba.rotX().sample(t));
         bone.rotY = Mth.lerp(w, bone.rotY, bone.initRotY + ba.rotY().sample(t));
         bone.rotZ = Mth.lerp(w, bone.rotZ, bone.initRotZ + ba.rotZ().sample(t));
         bone.posX = Mth.lerp(w, bone.posX, ba.posX().sample(t));
         bone.posY = Mth.lerp(w, bone.posY, ba.posY().sample(t));
         bone.posZ = Mth.lerp(w, bone.posZ, ba.posZ().sample(t));
      }
   }

   /** Loop clips wrap, others hold their last frame. */
   static double loop(Animation clip, double seconds) {
      if (!Double.isFinite(seconds) || seconds <= 0.0 || clip.lengthSeconds() <= 0.0) {
         return 0.0;
      }

      return clip.loopType() == Animation.LoopType.LOOP ? seconds % clip.lengthSeconds() : Math.min(seconds, clip.lengthSeconds());
   }

   /** Adds a Bedrock-degree yaw (+ = towards the model's right) to a bone. */
   private static void rotY(BakedGeoModel model, String name, float degrees) {
      GeoBone bone = model.getBone(name);
      if (bone != null && degrees != 0.0F) {
         bone.rotY += (float) Math.toRadians(-degrees);
      }
   }

   private static void addX(BakedGeoModel model, String name, float degrees) {
      GeoBone bone = model.getBone(name);
      if (bone != null) {
         bone.rotX += (float) Math.toRadians(-degrees);
      }
   }

   public static void forget(Entity entity) {
      STATES.remove(entity);
      RegulusView.forget(entity);
   }

   public static void clear() {
      STATES.clear();
   }

   /** Per-entity locomotion tracker: distance phase, eased weights, leg yaw lag, landing. */
   static final class State {
      double phase;
      double lastX;
      double lastZ;
      double lastTime = Double.NaN;
      float speed;
      float walkYaw;
      float walk;
      float run;
      float crouch;
      float air;
      float dt;
      long lastNanos = -1L;
      float legYaw = Float.NaN;
      float bodyYaw;
      double turnStart = -100.0;
      float turnFrom;
      float turnTo;
      boolean turnLeft;
      boolean wasAir;
      int airTicks;
      int lastAirTick = -1;
      double landAt = -100.0;
      float landPower;
      final Spring coatBack = new Spring(90.0F, 9.0F, 50.0F);
      final Spring coatFront = new Spring(110.0F, 10.0F, 30.0F);
      final Spring hair = new Spring(140.0F, 11.0F, 35.0F);

      void update(LivingEntity e, float partialTick, float bodyYaw) {
         long now = Util.getNanos();
         this.dt = this.lastNanos < 0L ? 0.0F : Math.min(0.25F, (now - this.lastNanos) / 1.0E9F);
         this.lastNanos = now;
         this.bodyYaw = bodyYaw;
         double time = e.tickCount + partialTick;
         Vec3 at = e.getPosition(partialTick);
         if (Double.isNaN(this.lastTime) || time < this.lastTime || time - this.lastTime > 40.0) {
            this.lastTime = time;
            this.lastX = at.x;
            this.lastZ = at.z;
            this.legYaw = bodyYaw;
         } else if (time > this.lastTime) {
            double dx = at.x - this.lastX;
            double dz = at.z - this.lastZ;
            double len = Math.sqrt(dx * dx + dz * dz);
            double dTicks = time - this.lastTime;
            this.lastTime = time;
            this.lastX = at.x;
            this.lastZ = at.z;
            if (len < 3.0 * dTicks) { // teleports and rubber-banding do not walk
               double yaw = Math.toRadians(bodyYaw);
               double fx = -Math.sin(yaw);
               double fz = Math.cos(yaw);
               double forward = dx * fx + dz * fz;
               double right = dx * -fz + dz * fx;
               float[] wy = walkYaw(forward, right);
               float k = 1.0F - (float) Math.exp(-dTicks * 0.35);
               this.walkYaw += (wy[0] - this.walkYaw) * k;
               this.speed += ((float) (len / dTicks) - this.speed) * Math.min(1.0F, (float) dTicks * 0.5F);
               boolean grounded = e.onGround() || e.isInWater() || e.isPassenger();
               if (grounded || this.air < 0.5F) {
                  this.phase += wy[1] * len / stride(this.run, this.crouch);
               }
            }
         }

         if (this.dt > 0.0F) {
            boolean flying = e instanceof Player p && p.getAbilities().flying;
            boolean airborne = !e.onGround() && !e.isPassenger() && !e.isInWater() && !flying && !e.isFallFlying();
            float k = Math.min(1.0F, this.dt * 10.0F);
            this.walk += (Mth.clamp(this.speed / 0.05F, 0.0F, 1.0F) - this.walk) * k;
            float runTarget = e.isCrouching() ? 0.0F : Mth.clamp((this.speed - 0.235F) / 0.05F, 0.0F, 1.0F);
            this.run += (runTarget - this.run) * k;
            this.crouch += ((e.isCrouching() ? 1.0F : 0.0F) - this.crouch) * Math.min(1.0F, this.dt * 14.0F);
            this.air += ((airborne ? 1.0F : 0.0F) - this.air) * Math.min(1.0F, this.dt * 8.0F);
            if (e.tickCount != this.lastAirTick) {
               this.lastAirTick = e.tickCount;
               if (airborne) {
                  this.airTicks++;
               } else {
                  if (this.wasAir && this.airTicks > 4) {
                     this.landAt = e.tickCount + partialTick;
                     this.landPower = Mth.clamp(this.airTicks / 20.0F, 0.3F, 1.0F);
                  }

                  this.airTicks = 0;
               }

               this.wasAir = airborne;
            }
         }

         this.updateLegYaw(e, partialTick, bodyYaw);
      }

      private void updateLegYaw(LivingEntity e, float partialTick, float bodyYaw) {
         if (Float.isNaN(this.legYaw) || this.walk > 0.3F || this.air > 0.5F) {
            this.legYaw = bodyYaw;
            this.turnStart = -100.0;
            return;
         }

         double now = e.tickCount + partialTick;
         if (this.turning(e, partialTick)) {
            float u = (float) Mth.clamp((now - this.turnStart) / TURN_TICKS, 0.0, 1.0);
            float eased = u * u * (3.0F - 2.0F * u);
            this.legYaw = this.turnFrom + Mth.wrapDegrees(this.turnTo - this.turnFrom) * eased;
            return;
         }

         float lag = Mth.wrapDegrees(this.legYaw - bodyYaw);
         if (Math.abs(lag) > TURN_LIMIT) {
            this.legYaw = bodyYaw + Math.copySign(TURN_LIMIT, lag);
            lag = Math.copySign(TURN_LIMIT, lag);
         }

         if (Math.abs(lag) > TURN_START) {
            this.turnStart = now;
            this.turnFrom = this.legYaw;
            this.turnTo = bodyYaw;
            this.turnLeft = lag > 0.0F;
         }
      }

      boolean turning(LivingEntity e, float partialTick) {
         double t = e.tickCount + partialTick - this.turnStart;
         return t >= 0.0 && t < TURN_TICKS;
      }

      double turnTicks(LivingEntity e, float partialTick) {
         return e.tickCount + partialTick - this.turnStart;
      }

      /** Leg yaw relative to the body (+ = legs towards the model's right). */
      float lagYaw() {
         return Float.isNaN(this.legYaw) ? 0.0F : Mth.wrapDegrees(this.legYaw - this.bodyYaw);
      }
   }
}
