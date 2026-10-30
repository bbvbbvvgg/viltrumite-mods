package dev.baranhan.viltrumitecore.client.dio;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.dio.DioRules;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

/**
 * DIO's own body poses on the vanilla PlayerModel (setupAnim TAIL) and the
 * first-person arm. Timed layers read the synced action clock; the posture
 * layer (leaning back, chin up) and the idle "JoJo pose" (hand at the face)
 * fade with weights. Rotation only: no pivot offsets to restore.
 */
public final class DioPoser {
   private static final WeakHashMap<LivingEntity, Idle> IDLE = new WeakHashMap<>();
   /** Ticks standing still before the JoJo pose may come, and its cycle. */
   static final int POSE_AFTER = 100, POSE_CYCLE = 260, POSE_HOLD = 70;

   private DioPoser() {
   }

   @Nullable
   private static HeroPublicSnapshot thirdPerson(LivingEntity entity) {
      HeroPublicSnapshot snapshot = DioView.of(entity);
      if (snapshot == null) {
         return null;
      }

      Minecraft client = Minecraft.getInstance();
      boolean localFirstPerson = entity == client.player && client.getCameraEntity() == entity && client.options.getCameraType().isFirstPerson();
      return localFirstPerson && !ShaderCompat.isShadowPass() ? null : snapshot;
   }

   /** Smooth 0→1→0 bump over [a, b] with ramps of {@code ramp} ticks. */
   static float window(double t, double a, double b, double ramp) {
      if (t <= a || t >= b) {
         return 0.0F;
      }

      double up = Math.min(1.0, (t - a) / ramp), down = Math.min(1.0, (b - t) / ramp);
      double w = Math.min(up, down);
      return (float)(w * w * (3.0 - 2.0 * w));
   }

   /** JoJo pose weight from the ticks DIO has stood still (pure). */
   static float jojoWeight(double stillTicks) {
      if (stillTicks < POSE_AFTER) {
         return 0.0F;
      }

      double c = (stillTicks - POSE_AFTER) % POSE_CYCLE;
      return window(c, 0.0, POSE_HOLD, 10.0);
   }

   public static void poseThirdPerson(PlayerModel<?> model, LivingEntity entity) {
      HeroPublicSnapshot snapshot = thirdPerson(entity);
      if (snapshot == null) {
         if (DioView.of(entity) == null) {
            IDLE.remove(entity);
         }

         return;
      }

      float partial = Minecraft.getInstance().getFrameTime();
      double t = snapshot.actionElapsed() + partial;
      HeroAction action = DioView.action(snapshot);
      Idle idle = IDLE.computeIfAbsent(entity, e -> new Idle());
      double now = entity.tickCount + partial;
      boolean still = action == null && entity.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4 && model.attackTime <= 0.0F;
      if (!still) {
         idle.stillSince = now;
      }

      // Posture: leaning back, chin up, arms loose and a little out.
      float posture = action == null ? 1.0F : 0.4F;
      model.body.xRot -= rad(4.0F) * posture;
      model.head.xRot -= rad(8.0F) * posture;
      model.rightArm.zRot += rad(4.0F) * posture;
      model.leftArm.zRot -= rad(4.0F) * posture;

      float jojo = jojoWeight(now - idle.stillSince);
      if (jojo > 0.0F) {
         model.rightArm.xRot = Mth.lerp(jojo, model.rightArm.xRot, rad(-128.0F));
         model.rightArm.yRot = Mth.lerp(jojo, model.rightArm.yRot, rad(-38.0F));
         model.rightArm.zRot = Mth.lerp(jojo, model.rightArm.zRot, rad(12.0F));
         model.leftArm.xRot = Mth.lerp(jojo, model.leftArm.xRot, rad(-20.0F));
         model.leftArm.zRot = Mth.lerp(jojo, model.leftArm.zRot, rad(-28.0F));
         model.body.yRot += rad(14.0F) * jojo;
         model.head.zRot += rad(10.0F) * jojo;
         model.head.xRot -= rad(6.0F) * jojo;
         model.rightLeg.zRot += rad(5.0F) * jojo;
      }

      if (DioView.standPhase(snapshot) == DioRules.STAND_SUMMONING) {
         // A flick of the hand forward as The World comes out.
         float g = window(DioView.standElapsed(snapshot) + partial, 0.0, DioRules.SUMMON_TICKS, 4.0);
         model.rightArm.xRot = Mth.lerp(g, model.rightArm.xRot, rad(-84.0F) + model.head.xRot);
         model.rightArm.yRot = Mth.lerp(g, model.rightArm.yRot, rad(-12.0F));
      }

      if (action == HeroAction.KNIFE_THROW) {
         float wind = window(t, -1.0, DioRules.KNIFE_WINDUP + 1.0, 4.0);
         float throwW = window(t, DioRules.KNIFE_WINDUP - 1.0, DioRules.KNIFE_LENGTH, 3.0);
         // Wind up crossed at the chest, then both hands fan out forward.
         model.rightArm.xRot = Mth.lerp(wind, model.rightArm.xRot, rad(-100.0F));
         model.rightArm.yRot = Mth.lerp(wind, model.rightArm.yRot, rad(50.0F));
         model.leftArm.xRot = Mth.lerp(wind, model.leftArm.xRot, rad(-100.0F));
         model.leftArm.yRot = Mth.lerp(wind, model.leftArm.yRot, rad(-50.0F));
         model.rightArm.xRot = Mth.lerp(throwW, model.rightArm.xRot, rad(-90.0F) + model.head.xRot);
         model.rightArm.yRot = Mth.lerp(throwW, model.rightArm.yRot, rad(-30.0F));
         model.leftArm.xRot = Mth.lerp(throwW, model.leftArm.xRot, rad(-90.0F) + model.head.xRot);
         model.leftArm.yRot = Mth.lerp(throwW, model.leftArm.yRot, rad(30.0F));
         model.body.yRot += rad(-10.0F) * wind + rad(8.0F) * throwW;
      } else if (action == HeroAction.TIME_STOP) {
         // ZA WARUDO: arms thrown wide, chest out, head back.
         float w = window(t, 0.0, DioRules.TIME_STOP_CAST + 6.0, 5.0);
         model.rightArm.zRot = Mth.lerp(w, model.rightArm.zRot, rad(78.0F));
         model.leftArm.zRot = Mth.lerp(w, model.leftArm.zRot, rad(-78.0F));
         model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, rad(-20.0F));
         model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, rad(-20.0F));
         model.body.xRot -= rad(8.0F) * w;
         model.head.xRot -= rad(16.0F) * w;
      } else if (action == HeroAction.VAMPIRE_DRAIN) {
         // The grip: right hand at the victim's throat, fingers in.
         float w = window(t, -1.0, DioRules.DRAIN_LENGTH + 1.0, 4.0);
         model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, rad(-88.0F) + model.head.xRot);
         model.rightArm.yRot = Mth.lerp(w, model.rightArm.yRot, model.head.yRot - rad(6.0F));
         model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, rad(-14.0F));
         model.leftArm.zRot = Mth.lerp(w, model.leftArm.zRot, rad(-18.0F));
         model.body.xRot += rad(10.0F) * w;
         float pulse = (float)Math.exp(-((t % DioRules.DRAIN_PULSE) / 3.0));
         model.rightArm.xRot += rad(5.0F) * pulse * w;
      } else if (action == HeroAction.ROAD_ROLLER) {
         roller(model, t);
      }

      copy(model);
   }

   private static void roller(PlayerModel<?> model, double t) {
      // Anticipation: knees bend, torso pitches forward, arms swing back.
      float crouch = window(t, DioRules.ROLLER_CROUCH - 2.0, DioRules.ROLLER_LEAP + 1.0, 4.0);
      model.body.xRot += rad(24.0F) * crouch;
      model.rightLeg.xRot = Mth.lerp(crouch, model.rightLeg.xRot, rad(-42.0F));
      model.leftLeg.xRot = Mth.lerp(crouch, model.leftLeg.xRot, rad(-30.0F));
      model.rightArm.xRot = Mth.lerp(crouch, model.rightArm.xRot, rad(38.0F));
      model.leftArm.xRot = Mth.lerp(crouch, model.leftArm.xRot, rad(38.0F));
      model.rightArm.zRot = Mth.lerp(crouch, model.rightArm.zRot, rad(14.0F));
      model.leftArm.zRot = Mth.lerp(crouch, model.leftArm.zRot, rad(-14.0F));
      model.head.xRot -= rad(14.0F) * crouch;
      // Rise: a straight, stretched launch silhouette, one fist leading up.
      float rise = window(t, DioRules.ROLLER_LEAP - 1.0, DioRules.ROLLER_APEX - 6.0, 4.0);
      model.rightLeg.xRot = Mth.lerp(rise, model.rightLeg.xRot, rad(12.0F));
      model.leftLeg.xRot = Mth.lerp(rise, model.leftLeg.xRot, rad(4.0F));
      model.rightArm.xRot = Mth.lerp(rise, model.rightArm.xRot, rad(-172.0F));
      model.rightArm.zRot = Mth.lerp(rise, model.rightArm.zRot, rad(-6.0F));
      model.leftArm.xRot = Mth.lerp(rise, model.leftArm.xRot, rad(20.0F));
      model.leftArm.zRot = Mth.lerp(rise, model.leftArm.zRot, rad(-24.0F));
      model.head.xRot -= rad(20.0F) * rise;
      // Top of the arc and the fall: knees tucked, arms up to bring the roller down.
      float leap = window(t, DioRules.ROLLER_APEX - 8.0, DioRules.ROLLER_SLAM + 1.0, 5.0);
      model.rightLeg.xRot = Mth.lerp(leap, model.rightLeg.xRot, rad(-70.0F));
      model.leftLeg.xRot = Mth.lerp(leap, model.leftLeg.xRot, rad(-35.0F));
      model.rightArm.xRot = Mth.lerp(leap, model.rightArm.xRot, rad(-160.0F));
      model.leftArm.xRot = Mth.lerp(leap, model.leftArm.xRot, rad(-160.0F));
      // On the roller: wide crouch, both fists hammer down in turn.
      float ride = window(t, DioRules.ROLLER_SLAM, DioRules.ROLLER_BARRAGE_END + 2.0, 3.0);
      double cycle = ((t - DioRules.ROLLER_SLAM) % DioRules.ROLLER_PULSE) / DioRules.ROLLER_PULSE;
      float hitR = (float)Math.sin(cycle * Math.PI * 2.0) * 0.5F + 0.5F;
      model.body.xRot += rad(28.0F) * ride;
      model.rightLeg.xRot = Mth.lerp(ride, model.rightLeg.xRot, rad(-24.0F));
      model.leftLeg.xRot = Mth.lerp(ride, model.leftLeg.xRot, rad(18.0F));
      model.rightLeg.zRot += rad(10.0F) * ride;
      model.leftLeg.zRot -= rad(10.0F) * ride;
      model.rightArm.xRot = Mth.lerp(ride, model.rightArm.xRot, rad(-40.0F - 60.0F * hitR));
      model.leftArm.xRot = Mth.lerp(ride, model.leftArm.xRot, rad(-40.0F - 60.0F * (1.0F - hitR)));
      // WRYYY: arms high and wide, head thrown back.
      float wry = window(t, DioRules.ROLLER_BARRAGE_END, DioRules.ROLLER_RESUME + 3.0, 3.0);
      model.rightArm.xRot = Mth.lerp(wry, model.rightArm.xRot, rad(-150.0F));
      model.leftArm.xRot = Mth.lerp(wry, model.leftArm.xRot, rad(-150.0F));
      model.rightArm.zRot = Mth.lerp(wry, model.rightArm.zRot, rad(40.0F));
      model.leftArm.zRot = Mth.lerp(wry, model.leftArm.zRot, rad(-40.0F));
      model.head.xRot -= rad(30.0F) * wry;
      model.body.xRot -= rad(12.0F) * wry;
   }

   private static void copy(PlayerModel<?> model) {
      model.hat.copyFrom(model.head);
      model.jacket.copyFrom(model.body);
      model.rightSleeve.copyFrom(model.rightArm);
      model.leftSleeve.copyFrom(model.leftArm);
      model.rightPants.copyFrom(model.rightLeg);
      model.leftPants.copyFrom(model.leftLeg);
   }

   /** First-person arm (after pushPose in renderArmWithItem); side +1 right, -1 left. */
   public static void poseFirstPerson(PoseStack stack, LivingEntity entity, float side, float partialTick) {
      HeroPublicSnapshot snapshot = DioView.of(entity);
      if (snapshot == null) {
         return;
      }

      HeroAction action = DioView.action(snapshot);
      double t = snapshot.actionElapsed() + partialTick;
      float x = 0.0F, y = 0.0F, z = 0.0F, pitch = 0.0F, roll = 0.0F;
      if (action == HeroAction.KNIFE_THROW) {
         float wind = window(t, -1.0, DioRules.KNIFE_WINDUP + 1.0, 4.0);
         float throwW = window(t, DioRules.KNIFE_WINDUP - 1.0, DioRules.KNIFE_LENGTH, 3.0);
         x += -side * 0.22F * wind + side * 0.12F * throwW;
         y += 0.12F * wind;
         z += 0.12F * wind - 0.28F * throwW;
         pitch += 25.0F * wind - 10.0F * throwW;
         roll += side * 30.0F * wind;
      } else if (action == HeroAction.TIME_STOP) {
         float w = window(t, 0.0, DioRules.TIME_STOP_CAST + 6.0, 5.0);
         x += side * 0.35F * w;
         y += 0.05F * w;
         roll += side * -35.0F * w;
      } else if (action == HeroAction.VAMPIRE_DRAIN && side > 0.0F) {
         float w = window(t, -1.0, DioRules.DRAIN_LENGTH + 1.0, 4.0);
         float pulse = (float)Math.exp(-((t % DioRules.DRAIN_PULSE) / 3.0));
         x -= 0.12F * w;
         y += 0.16F * w;
         z -= (0.3F + 0.04F * pulse) * w;
         pitch += 8.0F * w;
      } else if (action == HeroAction.ROAD_ROLLER) {
         float ride = window(t, DioRules.ROLLER_SLAM, DioRules.ROLLER_BARRAGE_END + 2.0, 3.0);
         double cycle = ((t - DioRules.ROLLER_SLAM) % DioRules.ROLLER_PULSE) / DioRules.ROLLER_PULSE;
         float hit = (float)Math.sin(cycle * Math.PI * 2.0 + (side > 0 ? 0.0 : Math.PI)) * 0.5F + 0.5F;
         y -= 0.25F * hit * ride;
         z -= 0.18F * hit * ride;
         pitch -= 30.0F * hit * ride;
         float wry = window(t, DioRules.ROLLER_BARRAGE_END, DioRules.ROLLER_RESUME + 3.0, 3.0);
         y += 0.3F * wry;
         x += side * 0.2F * wry;
         roll += side * -20.0F * wry;
      }

      if (DioView.standPhase(snapshot) == DioRules.STAND_SUMMONING && side > 0.0F) {
         float g = window(DioView.standElapsed(snapshot) + partialTick, 0.0, DioRules.SUMMON_TICKS, 4.0);
         y += 0.1F * g;
         z -= 0.2F * g;
         pitch += 12.0F * g;
      }

      if (x == 0.0F && y == 0.0F && z == 0.0F && pitch == 0.0F && roll == 0.0F) {
         return;
      }

      stack.translate(x, y, z);
      stack.mulPose(new Quaternionf().rotateX(rad(pitch)));
      stack.mulPose(new Quaternionf().rotateZ(rad(roll)));
   }

   private static float rad(float degrees) {
      return (float)Math.toRadians(degrees);
   }

   private static final class Idle {
      double stillSince = 0.0;
   }
}
