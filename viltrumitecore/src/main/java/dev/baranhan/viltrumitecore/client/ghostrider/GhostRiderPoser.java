package dev.baranhan.viltrumitecore.client.ghostrider;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import dev.baranhan.viltrumitecore.client.anim.pose.PoseRig;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderMove;
import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderRules;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleEntity;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Ghost Rider's body poses on the vanilla PlayerModel (setupAnim TAIL, GhostRiderModelMixin): seated on the Hell
 * Cycle with both fists on the grips and boots on the pegs, the chain moves (lash, throw, yank, whirl, slam, the bike
 * lash), the hellfire wave, the stare and the transformation. Rotation only, timed by the synced move clock; the outer
 * layers are copied again at the end.
 */
public final class GhostRiderPoser {
   // seat geometry (tools/assets/ghost_rider_bike.py): shoulder -> grip and hip -> peg directions, solved once
   static final float GRIP_X = -1.417F;
   static final float GRIP_Y = 0.315F;
   static final float PEG_X = -0.955F;
   static final float PEG_Y = 0.28F;

   private GhostRiderPoser() {
   }

   @Nullable
   private static HeroPublicSnapshot thirdPerson(LivingEntity entity) {
      HeroPublicSnapshot s = GhostRiderView.of(entity);
      if (s == null) {
         return null;
      }
      Minecraft mc = Minecraft.getInstance();
      boolean localFirstPerson = entity == mc.player && mc.getCameraEntity() == entity && mc.options.getCameraType().isFirstPerson();
      return localFirstPerson && !ShaderCompat.isShadowPass() ? null : s;
   }

   public static void poseThirdPerson(PlayerModel<?> model, LivingEntity entity) {
      HeroPublicSnapshot s = thirdPerson(entity);
      if (s == null) {
         return;
      }
      float partial = Minecraft.getInstance().getFrameTime();
      float age = entity.tickCount + partial;
      GhostRiderMove move = GhostRiderView.move(s);
      float t = GhostRiderView.elapsed(s, partial);
      boolean seated = entity.getVehicle() instanceof HellCycleEntity;
      model.head.zRot = 0.0F;
      if (seated) {
         seat(model, (HellCycleEntity)entity.getVehicle(), partial, age);
      } else if (GhostRiderView.rider(s) && move == null) {
         // the Rider's prowl: shoulders a little forward, fists loose
         model.rightArm.zRot += 0.06F;
         model.leftArm.zRot -= 0.06F;
         model.head.xRot += 0.08F;
      }
      if (move != null) {
         switch (move) {
            case LASH_1 -> lash(model.rightArm, t, 4.0F, 10.0F, true);
            case LASH_2 -> {
               // backhand from the left
               float w = GhostRiderGear.bump(t, 0.0F, 10.0F, 2.0F);
               float k = phase(t, 4.0F);
               model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, Mth.lerp(k, -2.2F, -1.1F));
               model.leftArm.yRot = Mth.lerp(w, model.leftArm.yRot, Mth.lerp(k, -0.9F, 0.7F));
               model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(k, -0.35F, 0.3F));
            }
            case LASH_3 -> {
               // both fists over the head, down hard
               float w = GhostRiderGear.bump(t, 0.0F, GhostRiderMove.LASH_3.length, 2.0F);
               float k = phase(t, GhostRiderMove.LASH_3.contact);
               float x = Mth.lerp(k, -3.0F, -0.7F);
               model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, x);
               model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, x);
               model.rightArm.yRot = Mth.lerp(w, model.rightArm.yRot, -0.25F);
               model.leftArm.yRot = Mth.lerp(w, model.leftArm.yRot, 0.25F);
               model.body.xRot = Mth.lerp(w, model.body.xRot, 0.25F * k);
            }
            case HOOK, BIKE_HOOK -> throwArm(model.rightArm, model.head, t, 3.0F, 8.0F);
            case HOOK_MISS -> throwArm(model.rightArm, model.head, t, 3.0F, 8.0F);
            case YANK -> {
               float w = GhostRiderGear.bump(t, 0.0F, GhostRiderMove.YANK.length, 1.5F);
               float k = phase(t, 6.0F);
               model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, Mth.lerp(k, -1.6F, -0.2F));
               model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, Mth.lerp(k, -1.2F, 0.1F));
               model.body.xRot = Mth.lerp(w, model.body.xRot, -0.18F * k);
            }
            case WHIRL -> {
               float w = GhostRiderGear.bump(t, 0.0F, GhostRiderRules.WHIRL_TICKS, 3.0F);
               float spin = t * 0.85F;
               model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, -2.75F + 0.15F * Mth.sin(spin));
               model.rightArm.zRot = Mth.lerp(w, model.rightArm.zRot, 0.25F * Mth.cos(spin));
               model.rightArm.yRot = Mth.lerp(w, model.rightArm.yRot, -0.3F * Mth.sin(spin));
               model.leftArm.zRot = Mth.lerp(w, model.leftArm.zRot, -0.9F);
               model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, -0.2F);
            }
            case SLAM -> {
               float w = GhostRiderGear.bump(t, 0.0F, GhostRiderRules.SLAM_TICKS, 2.0F);
               float k = phase(t, GhostRiderRules.SLAM_CONTACT);
               float x = Mth.lerp(k, -3.1F, -0.45F);
               model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, x);
               model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, x + 0.2F);
               model.body.xRot = Mth.lerp(w, model.body.xRot, 0.35F * k);
               model.head.xRot = Mth.lerp(w, model.head.xRot, 0.3F * k);
            }
            case WAVE -> {
               // gathers the fire at the chest, then both palms out
               float w = GhostRiderGear.bump(t, 0.0F, GhostRiderRules.WAVE_TICKS, 2.0F);
               float k = phase(t, GhostRiderRules.WAVE_RELEASE);
               model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, Mth.lerp(k, -0.9F, -1.62F));
               model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, Mth.lerp(k, -0.9F, -1.62F));
               model.rightArm.yRot = Mth.lerp(w, model.rightArm.yRot, Mth.lerp(k, -0.75F, -0.12F));
               model.leftArm.yRot = Mth.lerp(w, model.leftArm.yRot, Mth.lerp(k, 0.75F, 0.12F));
               model.head.xRot = Mth.lerp(w, model.head.xRot, Mth.lerp(k, 0.2F, -0.25F));
            }
            case DASH -> {
               if (!seated) {
                  float w = GhostRiderGear.bump(t, 0.0F, GhostRiderRules.DASH_TICKS, 2.0F);
                  model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, 0.7F);
                  model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, 0.7F);
                  model.body.xRot = Mth.lerp(w, model.body.xRot, 0.3F);
               }
            }
            case STARE -> {
               // a hand on the soul's collar, the skull straight at it
               float w = GhostRiderGear.bump(t, 0.0F, GhostRiderRules.STARE_TICKS, 4.0F);
               model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, -1.5F + model.head.xRot * 0.5F);
               model.rightArm.yRot = Mth.lerp(w, model.rightArm.yRot, -0.1F);
               model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, 0.1F);
               model.leftArm.zRot = Mth.lerp(w, model.leftArm.zRot, -0.15F);
               float shake = t > GhostRiderRules.STARE_VERDICT - 2 ? 0.03F * Mth.sin(age * 3.1F) : 0.0F;
               model.head.zRot = shake * w;
            }
            case TRANSFORM -> transform(model, t, age);
            case REVERT -> {
               float w = GhostRiderGear.bump(t, 0.0F, GhostRiderRules.REVERT_TICKS, 4.0F);
               model.head.xRot = Mth.lerp(w, model.head.xRot, 0.55F);
               model.body.xRot = Mth.lerp(w, model.body.xRot, 0.25F);
               model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, -0.25F);
               model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, -0.25F);
            }
            case BIKE_LASH -> lash(model.rightArm, t, 4.0F, 10.0F, true);
            case PUNCH -> punch(model, t);
            default -> {
            }
         }
      }
      copyLayers(model);
   }

   /**
    * The burning right fist: cocked back with the shoulder, thrown straight down the gaze on the contact tick, the left
    * fist guarding the jaw. The shoulders ride the twisted torso the way vanilla's attack swing moves them (setupAnim
    * resets those pivots every frame).
    */
   static void punch(PlayerModel<?> model, float t) {
      GhostRiderMove m = GhostRiderMove.PUNCH;
      float w = GhostRiderGear.bump(t, 0.0F, m.length, 1.5F);
      float k = phase(t, m.contact);
      // + = right shoulder back (vanilla attack convention): wound up, then driven through
      model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(k, 0.35F, -0.3F));
      model.rightArm.z = Mth.sin(model.body.yRot) * 5.0F;
      model.rightArm.x = -Mth.cos(model.body.yRot) * 5.0F;
      model.leftArm.z = -Mth.sin(model.body.yRot) * 5.0F;
      model.leftArm.x = Mth.cos(model.body.yRot) * 5.0F;
      model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, Mth.lerp(k, -0.45F, -1.57F + model.head.xRot));
      model.rightArm.yRot = Mth.lerp(w, model.rightArm.yRot, Mth.lerp(k, 0.25F, model.head.yRot * 0.8F - 0.12F));
      model.rightArm.zRot = Mth.lerp(w, model.rightArm.zRot, Mth.lerp(k, 0.2F, 0.0F));
      model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, -1.25F);
      model.leftArm.yRot = Mth.lerp(w, model.leftArm.yRot, 0.45F);
      model.leftArm.zRot = Mth.lerp(w, model.leftArm.zRot, 0.0F);
   }

   // ------------------------------------------------------------------------------------------------ first person

   /** On foot the chain moves also move the first-person arms (riding, HellCycleCockpit owns them). */
   @Nullable
   private static HeroPublicSnapshot firstPersonMove(LivingEntity entity) {
      HeroPublicSnapshot s = GhostRiderView.of(entity);
      return s == null || entity.getVehicle() instanceof HellCycleEntity || GhostRiderView.move(s) == null ? null : s;
   }

   /** The empty off hand shows while a move uses it (AlwaysVisibleOffhandMixin). */
   public static boolean wantsOffhand(LivingEntity entity) {
      HeroPublicSnapshot s = firstPersonMove(entity);
      return s != null && switch (GhostRiderView.move(s)) {
         case LASH_2, LASH_3, YANK, SLAM, WAVE, PUNCH, DASH, TRANSFORM -> true;
         default -> false;
      };
   }

   /**
   * First-person arm layer (FirstPersonGhostRiderMixin, before vanilla's arm transform): camera-space offsets of the
   * physical arm on {@code side} (+1 right, -1 left), sampled from the same synced move clock as the body poses, so
   * repeated passes at one timestamp agree. Small turns only: they pivot about the camera.
   */
   public static void poseFirstPerson(PoseStack stack, LivingEntity entity, float side, float partial) {
      HeroPublicSnapshot s = firstPersonMove(entity);
      if (s == null) {
         return;
      }
      GhostRiderMove move = GhostRiderView.move(s);
      float t = GhostRiderView.elapsed(s, partial);
      boolean right = side > 0.0F;
      float[] o = firstPersonOffset(move, t, right, entity.tickCount + partial);
      if (o != null) {
         PoseRig.firstPersonRaw(stack, o[3], o[4] * side, o[5] * side, o[0] * side, o[1], o[2]);
      }
   }

   /**
    * {x, y, z, pitch, yaw, roll} of one arm in camera space, or null when the move leaves it alone. Mirrored for the left
    * arm: + x = outward, + pitch = up and forward, + yaw = inward (towards the crosshair), + roll = out and up.
    */
   @Nullable
   static float[] firstPersonOffset(GhostRiderMove move, float t, boolean right, float age) {
      float w;
      float k;
      switch (move) {
         case PUNCH -> {
            w = GhostRiderGear.bump(t, 0.0F, move.length, 1.5F);
            if (!right) {
               return scale(new float[]{-0.1F, 0.12F, -0.06F, 8.0F, 6.0F, 0.0F}, w); // the guard
            }
            k = phase(t, move.contact);
            return scale(new float[]{Mth.lerp(k, 0.05F, -0.13F), Mth.lerp(k, -0.08F, 0.07F), Mth.lerp(k, 0.12F, -0.42F), Mth.lerp(k, -10.0F, 6.0F),
               Mth.lerp(k, -4.0F, 12.0F), 0.0F}, w);
         }
         case LASH_1, LASH_3 -> {
            if (!right && move == GhostRiderMove.LASH_1) {
               return null;
            }
            w = GhostRiderGear.bump(t, 0.0F, move.length, 2.0F);
            k = phase(t, move.contact);
            return scale(new float[]{Mth.lerp(k, 0.02F, -0.06F), Mth.lerp(k, 0.16F, -0.06F), Mth.lerp(k, 0.1F, -0.24F), Mth.lerp(k, 20.0F, -12.0F), 0.0F,
               0.0F}, w);
         }
         case LASH_2 -> {
            if (right) {
               return null;
            }
            w = GhostRiderGear.bump(t, 0.0F, move.length, 2.0F);
            k = phase(t, move.contact);
            return scale(new float[]{Mth.lerp(k, 0.14F, -0.3F), 0.06F, -0.12F, 4.0F, Mth.lerp(k, -10.0F, 16.0F), 0.0F}, w);
         }
         case HOOK, HOOK_MISS -> {
            if (!right) {
               return null;
            }
            w = GhostRiderGear.bump(t, 0.0F, move.length, 1.5F);
            k = phase(t, Math.max(3, move.contact));
            return scale(new float[]{Mth.lerp(k, 0.03F, -0.06F), Mth.lerp(k, -0.02F, 0.06F), Mth.lerp(k, 0.1F, -0.36F), Mth.lerp(k, -5.0F, 8.0F), 0.0F,
               0.0F}, w);
         }
         case YANK -> {
            w = GhostRiderGear.bump(t, 0.0F, move.length, 1.5F);
            k = phase(t, 6.0F);
            return scale(new float[]{-0.04F, Mth.lerp(k, 0.04F, -0.08F), Mth.lerp(k, -0.26F, 0.12F), Mth.lerp(k, 6.0F, -12.0F), 0.0F, 0.0F}, w);
         }
         case WHIRL -> {
            if (!right) {
               return null;
            }
            w = GhostRiderGear.bump(t, 0.0F, GhostRiderRules.WHIRL_TICKS, 3.0F);
            float spin = t * 0.85F;
            return scale(new float[]{0.04F * Mth.cos(spin), 0.28F + 0.03F * Mth.sin(spin), -0.1F, 30.0F, 0.0F, 0.0F}, w);
         }
         case SLAM -> {
            w = GhostRiderGear.bump(t, 0.0F, GhostRiderRules.SLAM_TICKS, 2.0F);
            k = phase(t, GhostRiderRules.SLAM_CONTACT);
            return scale(new float[]{-0.06F, Mth.lerp(k, 0.32F, -0.14F), Mth.lerp(k, 0.05F, -0.2F), Mth.lerp(k, 36.0F, -24.0F), 0.0F, 0.0F}, w);
         }
         case WAVE -> {
            w = GhostRiderGear.bump(t, 0.0F, GhostRiderRules.WAVE_TICKS, 2.0F);
            k = phase(t, GhostRiderRules.WAVE_RELEASE);
            return scale(new float[]{Mth.lerp(k, -0.14F, 0.04F), Mth.lerp(k, -0.02F, 0.06F), Mth.lerp(k, 0.06F, -0.34F), Mth.lerp(k, 0.0F, 8.0F),
               Mth.lerp(k, 8.0F, 0.0F), 0.0F}, w);
         }
         case STARE -> {
            if (!right) {
               return null;
            }
            w = GhostRiderGear.bump(t, 0.0F, GhostRiderRules.STARE_TICKS, 4.0F);
            float shake = t > GhostRiderRules.STARE_VERDICT - 2 ? 0.006F * Mth.sin(age * 3.1F) : 0.0F;
            return scale(new float[]{-0.08F, 0.08F + shake, -0.3F, 6.0F, 0.0F, 0.0F}, w);
         }
         case DASH -> {
            w = GhostRiderGear.bump(t, 0.0F, GhostRiderRules.DASH_TICKS, 2.0F);
            return scale(new float[]{0.04F, -0.12F, 0.14F, -18.0F, 0.0F, 0.0F}, w);
         }
         case TRANSFORM -> {
            float flare = GhostRiderRules.TRANSFORM_FLARE_TICK;
            float clutch = GhostRiderGear.bump(t, 0.0F, flare - 2.0F, 4.0F);
            float roar = GhostRiderGear.bump(t, flare - 3.0F, GhostRiderRules.TRANSFORM_TICKS, 3.0F);
            float tremble = 0.01F * Mth.sin(age * 4.3F) * clutch;
            return new float[]{-0.16F * clutch + 0.3F * roar, 0.32F * clutch - 0.1F * roar + tremble, 0.12F * clutch, 40.0F * clutch, 0.0F,
               22.0F * roar};
         }
         case REVERT -> {
            w = GhostRiderGear.bump(t, 0.0F, GhostRiderRules.REVERT_TICKS, 4.0F);
            return scale(new float[]{0.0F, -0.14F, 0.04F, -10.0F, 0.0F, 0.0F}, w);
         }
         default -> {
            return null;
         }
      }
   }

   private static float[] scale(float[] o, float w) {
      for (int i = 0; i < o.length; i++) {
         o[i] *= w;
      }
      return o;
   }

   /** Both fists on the grips, boots on the pegs; the head turns freely; a lean into the throttle at speed. */
   static void seat(PlayerModel<?> model, HellCycleEntity bike, float partial, float age) {
      model.rightArm.xRot = GRIP_X;
      model.rightArm.yRot = GRIP_Y;
      model.rightArm.zRot = 0.0F;
      model.leftArm.xRot = GRIP_X;
      model.leftArm.yRot = -GRIP_Y;
      model.leftArm.zRot = 0.0F;
      model.rightLeg.xRot = PEG_X;
      model.rightLeg.yRot = PEG_Y;
      model.rightLeg.zRot = 0.0F;
      model.leftLeg.xRot = PEG_X;
      model.leftLeg.yRot = -PEG_Y;
      model.leftLeg.zRot = 0.0F;
      float steer = Mth.clamp(Mth.lerp(partial, bike.steerO, bike.steer), -1.0F, 1.0F);
      // the bars turn: one fist forward, the other back
      model.rightArm.xRot -= 0.12F * steer;
      model.leftArm.xRot += 0.12F * steer;
      float vibe = 0.012F * Mth.sin(age * 2.7F);
      model.rightArm.xRot += vibe;
      model.leftArm.xRot -= vibe;
   }

   /** Overhead whip of one arm: wind up behind, crack forward at the contact, follow through. */
   static void lash(ModelPart arm, float t, float contact, float length, boolean right) {
      float w = GhostRiderGear.bump(t, 0.0F, length, 2.0F);
      float x;
      if (t < contact) {
         x = Mth.lerp(t / contact, -1.2F, -2.9F);
      } else {
         x = Mth.lerp(Math.min(1.0F, (t - contact) / 2.5F), -2.9F, -0.75F);
      }
      arm.xRot = Mth.lerp(w, arm.xRot, x);
      arm.yRot = Mth.lerp(w, arm.yRot, right ? -0.2F : 0.2F);
      arm.zRot = Mth.lerp(w, arm.zRot, right ? 0.15F : -0.15F);
   }

   /** The chain thrown: the arm snaps out straight along the gaze. */
   static void throwArm(ModelPart arm, ModelPart head, float t, float contact, float length) {
      float w = GhostRiderGear.bump(t, 0.0F, length, 1.5F);
      float x = t < contact ? Mth.lerp(t / contact, -2.4F, -1.6F) : -1.57F;
      arm.xRot = Mth.lerp(w, arm.xRot, x + head.xRot);
      arm.yRot = Mth.lerp(w, arm.yRot, head.yRot * 0.8F);
   }

   /** The transformation: doubled over clutching the skull, the flesh burns, then arms flung wide and a roar. */
   static void transform(PlayerModel<?> model, float t, float age) {
      float flare = GhostRiderRules.TRANSFORM_FLARE_TICK;
      float clutch = GhostRiderGear.bump(t, 0.0F, flare - 2.0F, 4.0F);
      float roar = GhostRiderGear.bump(t, flare - 3.0F, GhostRiderRules.TRANSFORM_TICKS, 3.0F);
      float tremble = 0.05F * Mth.sin(age * 4.3F) * clutch;
      // doubled over, hands at the head
      model.body.xRot = Mth.lerp(clutch, model.body.xRot, 0.42F);
      model.head.xRot = Mth.lerp(clutch, model.head.xRot, 0.6F) + tremble;
      model.rightArm.xRot = Mth.lerp(clutch, model.rightArm.xRot, -2.35F) + tremble;
      model.leftArm.xRot = Mth.lerp(clutch, model.leftArm.xRot, -2.35F) - tremble;
      model.rightArm.zRot = Mth.lerp(clutch, model.rightArm.zRot, -0.55F);
      model.leftArm.zRot = Mth.lerp(clutch, model.leftArm.zRot, 0.55F);
      // the flare: arms wide, chest out, skull thrown back
      model.body.xRot = Mth.lerp(roar, model.body.xRot, -0.2F);
      model.head.xRot = Mth.lerp(roar, model.head.xRot, -0.7F);
      model.rightArm.xRot = Mth.lerp(roar, model.rightArm.xRot, -0.35F);
      model.leftArm.xRot = Mth.lerp(roar, model.leftArm.xRot, -0.35F);
      model.rightArm.zRot = Mth.lerp(roar, model.rightArm.zRot, 1.15F);
      model.leftArm.zRot = Mth.lerp(roar, model.leftArm.zRot, -1.15F);
      model.rightLeg.zRot = Mth.lerp(roar, model.rightLeg.zRot, 0.12F);
      model.leftLeg.zRot = Mth.lerp(roar, model.leftLeg.zRot, -0.12F);
   }

   /** 0 before the contact tick, eased to 1 over two ticks after it. */
   static float phase(float t, float contact) {
      if (t <= contact - 2.0F) {
         return 0.0F;
      }
      float k = Math.min(1.0F, (t - (contact - 2.0F)) / 3.0F);
      return k * k * (3.0F - 2.0F * k);
   }

   static void copyLayers(PlayerModel<?> model) {
      model.hat.copyFrom(model.head);
      model.jacket.copyFrom(model.body);
      model.rightSleeve.copyFrom(model.rightArm);
      model.leftSleeve.copyFrom(model.leftArm);
      model.rightPants.copyFrom(model.rightLeg);
      model.leftPants.copyFrom(model.leftLeg);
   }
}
