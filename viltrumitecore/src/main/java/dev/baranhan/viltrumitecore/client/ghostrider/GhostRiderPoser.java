package dev.baranhan.viltrumitecore.client.ghostrider;

import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
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
               float w = GhostRiderGear.bump(t, 0.0F, 14.0F, 2.0F);
               float k = phase(t, 7.0F);
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
            default -> {
            }
         }
      }
      copyLayers(model);
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
