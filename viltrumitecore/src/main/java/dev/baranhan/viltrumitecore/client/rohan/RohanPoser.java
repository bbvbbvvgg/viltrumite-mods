package dev.baranhan.viltrumitecore.client.rohan;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.rohan.RohanFlags;
import dev.baranhan.viltrumitecore.hero.rohan.RohanMove;
import dev.baranhan.viltrumitecore.hero.rohan.RohanRules;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

/**
 * Rohan's body on the vanilla PlayerModel (setupAnim TAIL, {@code RohanModelMixin}) and his first-person arms
 * ({@code FirstPersonRohanMixin}). Rotation only. Layers, all weighted so they blend:
 * <ul>
 * <li>posture: straight back, chin a little up, the right hand near the chest (a pen between the fingers);</li>
 * <li>walk: a springy, narrow arm swing;</li>
 * <li>idle: the finger warm-up (wrists bent at 90°, both forearms raised, flexing) every few seconds of standing;</li>
 * <li>moves from the synced action clock: the "Heaven's Door!" point, quick air strokes, three nib flicks, the
 * counter lean, writing on his own cheek then the backward flight, raising the manuscript, writing in a book,
 * tearing a page;</li>
 * <li>reading: one hand at the chin while a book is open.</li>
 * </ul>
 */
public final class RohanPoser {
   private static final WeakHashMap<LivingEntity, Idle> IDLE = new WeakHashMap<>();
   /** Ticks standing still before the finger warm-up may start, and its cycle / hold. */
   public static final int WARMUP_AFTER = 80, WARMUP_CYCLE = 220, WARMUP_HOLD = 60;

   private RohanPoser() {
   }

   /** Smooth 0→1→0 bump over [a, b] with ramps of {@code ramp} ticks (pure). */
   public static float window(double t, double a, double b, double ramp) {
      if (t <= a || t >= b) {
         return 0.0F;
      }
      double up = Math.min(1.0, (t - a) / ramp), down = Math.min(1.0, (b - t) / ramp);
      double w = Math.min(up, down);
      return (float)(w * w * (3.0 - 2.0 * w));
   }

   /** Finger warm-up weight from the ticks he has stood still (pure). */
   public static float warmupWeight(double stillTicks) {
      if (stillTicks < WARMUP_AFTER) {
         return 0.0F;
      }
      return window((stillTicks - WARMUP_AFTER) % WARMUP_CYCLE, 0.0, WARMUP_HOLD, 8.0);
   }

   /** Nib flick phase 0..1 inside the 6-tick throw: three flicks, one every 2 ticks (pure). */
   public static float flick(double t) {
      if (t < 0.0 || t >= RohanRules.NIBS_LENGTH) {
         return 0.0F;
      }
      double c = (t % 2.0) / 2.0;
      return (float)Math.sin(c * Math.PI);
   }

   @Nullable
   private static HeroPublicSnapshot thirdPerson(LivingEntity entity) {
      HeroPublicSnapshot s = RohanView.of(entity);
      if (s == null) {
         return null;
      }
      Minecraft client = Minecraft.getInstance();
      boolean localFirstPerson = entity == client.player && client.getCameraEntity() == entity && client.options.getCameraType().isFirstPerson();
      return localFirstPerson && !ShaderCompat.isShadowPass() ? null : s;
   }

   public static void poseThirdPerson(PlayerModel<?> model, LivingEntity entity) {
      HeroPublicSnapshot s = thirdPerson(entity);
      if (s == null) {
         if (RohanView.of(entity) == null) {
            IDLE.remove(entity);
         }
         return;
      }
      float partial = Minecraft.getInstance().getFrameTime();
      double t = s.actionElapsed() + partial;
      RohanMove move = RohanView.move(s);
      Idle idle = IDLE.computeIfAbsent(entity, e -> new Idle());
      double now = entity.tickCount + partial;
      double speed = entity.getDeltaMovement().horizontalDistanceSqr();
      boolean still = move == null && speed < 1.0E-4 && model.attackTime <= 0.0F && !RohanView.flag(s, RohanFlags.READING);
      if (!still) {
         idle.stillSince = now;
      }

      // Posture: straight back, chin up, right forearm near the chest with the pen.
      float posture = move == null ? 1.0F : 0.35F;
      model.head.xRot -= rad(6.0F) * posture;
      model.body.xRot -= rad(2.0F) * posture;
      if (model.attackTime <= 0.0F) {
         model.rightArm.xRot = Mth.lerp(0.35F * posture, model.rightArm.xRot, rad(-42.0F));
         model.rightArm.yRot = Mth.lerp(0.35F * posture, model.rightArm.yRot, rad(-18.0F));
      }
      // Springy walk: a narrower arm swing and a little bounce of the shoulders.
      float walk = (float)Math.min(1.0, speed * 40.0);
      if (walk > 0.0F && move == null) {
         model.leftArm.xRot *= 1.0F - 0.35F * walk;
         model.leftArm.zRot -= rad(3.0F) * walk;
         model.body.yRot += (float)Math.sin(now * 0.66) * rad(3.0F) * walk;
      }

      float warm = warmupWeight(now - idle.stillSince);
      if (warm > 0.0F) {
         float flex = (float)Math.sin(now * 0.9) * 0.5F + 0.5F;
         model.rightArm.xRot = Mth.lerp(warm, model.rightArm.xRot, rad(-78.0F - 6.0F * flex));
         model.rightArm.yRot = Mth.lerp(warm, model.rightArm.yRot, rad(-28.0F));
         model.leftArm.xRot = Mth.lerp(warm, model.leftArm.xRot, rad(-78.0F - 6.0F * (1.0F - flex)));
         model.leftArm.yRot = Mth.lerp(warm, model.leftArm.yRot, rad(28.0F));
         model.head.xRot += rad(10.0F) * warm;
      }

      if (RohanView.flag(s, RohanFlags.READING) && move == null) {
         // Reading: left hand at the chin, right hand low with the pen, head tilted toward the book.
         model.leftArm.xRot = rad(-112.0F);
         model.leftArm.yRot = rad(38.0F);
         model.leftArm.zRot = rad(-8.0F);
         model.rightArm.xRot = rad(-55.0F);
         model.rightArm.yRot = rad(-10.0F);
         model.head.xRot += rad(12.0F);
         model.head.zRot = rad(6.0F);
      } else {
         model.head.zRot = 0.0F;
      }

      if (move != null) {
         poseMove(model, move, t, now);
      }
      copy(model);
   }

   private static void poseMove(PlayerModel<?> model, RohanMove move, double t, double now) {
      float look = model.head.xRot;
      switch (move) {
         case DOOR -> {
            // "Heaven's Door!": the right index finger points at the body, the left hand flat at the hip.
            float w = window(t, -1.0, RohanRules.DOOR_LENGTH + 3.0, 3.0);
            model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, rad(-90.0F) + look);
            model.rightArm.yRot = Mth.lerp(w, model.rightArm.yRot, rad(-6.0F));
            model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, rad(10.0F));
            model.leftArm.zRot = Mth.lerp(w, model.leftArm.zRot, rad(-22.0F));
            model.body.yRot += rad(-12.0F) * w;
         }
         case SKETCH, WRITE -> {
            // Fast strokes "without guidelines": the forearm darts in short arcs.
            float w = window(t, -1.0, move.length + 2.0, 3.0);
            float sx = (float)Math.sin(now * 2.1), sy = (float)Math.cos(now * 3.3);
            model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, rad(-80.0F + 14.0F * sy) + look * 0.5F);
            model.rightArm.yRot = Mth.lerp(w, model.rightArm.yRot, rad(-10.0F + 22.0F * sx));
            model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, rad(-48.0F));
            model.leftArm.yRot = Mth.lerp(w, model.leftArm.yRot, rad(30.0F));
            model.head.xRot += rad(move == RohanMove.WRITE ? 16.0F : 4.0F) * w;
         }
         case NIBS -> {
            float w = window(t, -1.0, RohanRules.NIBS_LENGTH + 2.0, 2.0);
            float f = flick(t);
            model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, rad(-150.0F + 70.0F * f) + look * 0.5F);
            model.rightArm.yRot = Mth.lerp(w, model.rightArm.yRot, rad(-20.0F + 30.0F * f));
            model.body.yRot += rad(-14.0F + 18.0F * f) * w;
         }
         case COUNTER -> {
            // He leans away, the page already in his left hand: faster than the punch.
            float w = window(t, -1.0, RohanRules.COUNTER_WINDOW + 4.0, 2.0);
            model.body.xRot -= rad(8.0F) * w;
            model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, rad(-100.0F));
            model.leftArm.yRot = Mth.lerp(w, model.leftArm.yRot, rad(20.0F));
            model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, rad(-30.0F));
            model.rightArm.zRot = Mth.lerp(w, model.rightArm.zRot, rad(30.0F));
            model.head.xRot -= rad(8.0F) * w;
         }
         case SELF_EDIT -> {
            float write = window(t, -1.0, RohanRules.SELF_EDIT_WRITE + 1.0, 2.0);
            model.rightArm.xRot = Mth.lerp(write, model.rightArm.xRot, rad(-128.0F));
            model.rightArm.yRot = Mth.lerp(write, model.rightArm.yRot, rad(-40.0F));
            model.head.zRot = rad(-12.0F) * write;
            float fly = window(t, RohanRules.SELF_EDIT_WRITE - 1.0, move.length + 4.0, 2.0);
            // Thrown backwards at 70 km/h: arms forward, legs trailing ahead of the body.
            model.body.xRot += rad(-14.0F) * fly;
            model.rightArm.xRot = Mth.lerp(fly, model.rightArm.xRot, rad(-70.0F));
            model.leftArm.xRot = Mth.lerp(fly, model.leftArm.xRot, rad(-70.0F));
            model.rightArm.zRot = Mth.lerp(fly, model.rightArm.zRot, rad(24.0F));
            model.leftArm.zRot = Mth.lerp(fly, model.leftArm.zRot, rad(-24.0F));
            model.rightLeg.xRot = Mth.lerp(fly, model.rightLeg.xRot, rad(-36.0F));
            model.leftLeg.xRot = Mth.lerp(fly, model.leftLeg.xRot, rad(-20.0F));
         }
         case MANUSCRIPT -> {
            // Both hands raise the original page high for everyone to see.
            float w = window(t, -1.0, RohanRules.MANUSCRIPT_SHOW + 6.0, 5.0);
            model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, rad(-150.0F));
            model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, rad(-150.0F));
            model.rightArm.zRot = Mth.lerp(w, model.rightArm.zRot, rad(-10.0F));
            model.leftArm.zRot = Mth.lerp(w, model.leftArm.zRot, rad(10.0F));
            model.head.xRot -= rad(14.0F) * w;
            model.body.xRot -= rad(5.0F) * w;
         }
         case RIP -> {
            float w = window(t, -1.0, 12.0, 2.0);
            float sweep = (float)Mth.clamp(t / 6.0, 0.0, 1.0);
            model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, rad(-90.0F + 20.0F * sweep));
            model.rightArm.yRot = Mth.lerp(w, model.rightArm.yRot, rad(-10.0F - 60.0F * sweep));
         }
      }
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
      HeroPublicSnapshot s = RohanView.of(entity);
      if (s == null) {
         return;
      }
      RohanMove move = RohanView.move(s);
      double t = s.actionElapsed() + partialTick;
      double now = entity.tickCount + partialTick;
      float x = 0.0F, y = 0.0F, z = 0.0F, pitch = 0.0F, roll = 0.0F;
      if (move == RohanMove.DOOR && side > 0.0F) {
         float w = window(t, -1.0, RohanRules.DOOR_LENGTH + 3.0, 3.0);
         x -= 0.18F * w;
         y += 0.12F * w;
         z -= 0.25F * w;
         pitch += 8.0F * w;
      } else if (move == RohanMove.SKETCH || move == RohanMove.WRITE) {
         float w = window(t, -1.0, move.length + 2.0, 3.0);
         if (side > 0.0F) {
            x += (float)Math.sin(now * 2.1) * 0.08F * w - 0.12F * w;
            y += (float)Math.cos(now * 3.3) * 0.05F * w + 0.08F * w;
            z -= 0.12F * w;
            roll += 10.0F * w;
         }
      } else if (move == RohanMove.NIBS && side > 0.0F) {
         float f = flick(t), w = window(t, -1.0, RohanRules.NIBS_LENGTH + 2.0, 2.0);
         y += (0.18F - 0.2F * f) * w;
         z -= 0.2F * f * w;
         pitch += (20.0F - 40.0F * f) * w;
      } else if (move == RohanMove.MANUSCRIPT) {
         float w = window(t, -1.0, RohanRules.MANUSCRIPT_SHOW + 6.0, 5.0);
         y += 0.32F * w;
         x -= side * 0.12F * w;
         pitch += 20.0F * w;
      } else if (move == RohanMove.SELF_EDIT && side > 0.0F) {
         float w = window(t, -1.0, RohanRules.SELF_EDIT_WRITE + 1.0, 2.0);
         x -= 0.3F * w;
         y += 0.3F * w;
         roll -= 30.0F * w;
      } else if (move == RohanMove.COUNTER && side < 0.0F) {
         float w = window(t, -1.0, RohanRules.COUNTER_WINDOW + 4.0, 2.0);
         y += 0.2F * w;
         z -= 0.15F * w;
      } else if (move == RohanMove.RIP && side > 0.0F) {
         float sweep = (float)Mth.clamp(t / 6.0, 0.0, 1.0), w = window(t, -1.0, 12.0, 2.0);
         x += 0.3F * sweep * w;
         roll += 25.0F * sweep * w;
      } else if (move == null && RohanView.flag(s, RohanFlags.READING) && side > 0.0F) {
         y -= 0.12F;
         pitch -= 10.0F;
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
