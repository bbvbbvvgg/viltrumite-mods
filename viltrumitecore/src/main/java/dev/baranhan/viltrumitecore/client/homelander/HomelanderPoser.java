package dev.baranhan.viltrumitecore.client.homelander;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.homelander.HomelanderAbilities;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

/**
 * Homelander poses (animation-system §3, §5, §6). Laser and focus are boolean
 * layers with weight managers; the roar is a 20-tick timeline driven by the
 * snapshot action fields. Rotation-only: no pivot offsets to restore.
 */
public final class HomelanderPoser {
   public static final int ROAR_POSE_TICKS = 20;
   private static final WeakHashMap<LivingEntity, Weights> THIRD = new WeakHashMap<>();
   private static final WeakHashMap<LivingEntity, Weights> FIRST = new WeakHashMap<>();

   private HomelanderPoser() {
   }

   /** Homelander snapshot or null. */
   @Nullable
   public static HeroPublicSnapshot homelander(@Nullable Entity entity) {
      if (entity instanceof HeroPlayer heroPlayer) {
         HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
         if (snapshot != null && snapshot.heroId() == HeroId.HOMELANDER) {
            return snapshot;
         }
      }

      return null;
   }

   public static boolean laserOn(HeroPublicSnapshot snapshot) {
      return snapshot.heroFlag(HomelanderAbilities.FLAG_LASER);
   }

   public static boolean focusOn(HeroPublicSnapshot snapshot) {
      return snapshot.heroFlag(HomelanderAbilities.FLAG_FOCUS);
   }

   /** Roar timeline 0..1, or -1 when no roar pose runs. */
   public static float roarTime(HeroPublicSnapshot snapshot, float partialTick) {
      if (snapshot.actionId() != HeroAction.ROAR.ordinal() || snapshot.actionLength() <= 0) {
         return -1.0F;
      }

      return Mth.clamp((snapshot.actionElapsed() + partialTick) / (float)snapshot.actionLength(), 0.0F, 1.0F);
   }

   /** Roar intensity: wind-up 0-0.2, hold to 0.7, release to 1. */
   public static float roarCurve(float t) {
      if (t < 0.0F) {
         return 0.0F;
      } else if (t < 0.2F) {
         float x = t / 0.2F;
         return x * x * (3.0F - 2.0F * x) * 1.15F;
      } else if (t < 0.3F) {
         return Mth.lerp((t - 0.2F) / 0.1F, 1.15F, 1.0F);
      } else if (t < 0.7F) {
         return 1.0F;
      } else {
         float x = (t - 0.7F) / 0.3F;
         return 1.0F - x * x * (3.0F - 2.0F * x);
      }
   }

   /** Third-person pass gate: skip the local first-person player outside the shadow pass. */
   @Nullable
   public static HeroPublicSnapshot thirdPerson(LivingEntity entity) {
      HeroPublicSnapshot snapshot = homelander(entity);
      if (snapshot == null) {
         return null;
      }

      Minecraft minecraft = Minecraft.getInstance();
      boolean localFirstPerson = entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
      return localFirstPerson && !ShaderCompat.isShadowPass() ? null : snapshot;
   }

   /** Apply the third-person layers at setupAnim TAIL. */
   public static void poseThirdPerson(PlayerModel<?> model, LivingEntity entity, @Nullable ModelPart cloak) {
      HeroPublicSnapshot snapshot = thirdPerson(entity);
      if (snapshot == null) {
         if (homelander(entity) == null) {
            // Hero changed: drop cached pose weights so a later return starts clean.
            THIRD.remove(entity);
            FIRST.remove(entity);
         }
         return;
      }

      Weights w = THIRD.computeIfAbsent(entity, e -> new Weights());
      w.advance(laserOn(snapshot), focusOn(snapshot), 12.0F);
      float roar = roarCurve(roarTime(snapshot, Minecraft.getInstance().getFrameTime()));
      if (w.laser < 0.001F && w.focus < 0.001F && roar <= 0.0F) {
         return;
      }

      // Lasers: lean in, head pushed toward the target, arms slightly back and out.
      model.body.xRot += rad(7.0F) * w.laser;
      model.head.xRot += rad(6.0F) * w.laser;
      model.rightArm.xRot += rad(12.0F) * w.laser;
      model.leftArm.xRot += rad(12.0F) * w.laser;
      model.rightArm.zRot += rad(10.0F) * w.laser;
      model.leftArm.zRot -= rad(10.0F) * w.laser;
      // Roar: chest out, head up, arms thrown back and wide.
      model.body.xRot -= rad(12.0F) * roar;
      model.head.xRot -= rad(28.0F) * roar;
      model.rightArm.xRot += rad(38.0F) * roar;
      model.leftArm.xRot += rad(38.0F) * roar;
      model.rightArm.zRot += rad(32.0F) * roar;
      model.leftArm.zRot -= rad(32.0F) * roar;
      // Roar head-up on top of looking up went past the neck (head folded into the back).
      dev.baranhan.viltrumitecore.client.anim.pose.PoseRig.limitNeck(model);
      model.hat.copyFrom(model.head);
      model.jacket.copyFrom(model.body);
      model.rightSleeve.copyFrom(model.rightArm);
      model.leftSleeve.copyFrom(model.leftArm);
      model.rightPants.copyFrom(model.rightLeg);
      model.leftPants.copyFrom(model.leftLeg);
   }

   /**
    * First-person arm transform (after pushPose in renderArmWithItem).
    * side: +1 right hand, -1 left hand. Called once per hand per frame; the
    * weights advance on the main hand only.
    */
   public static void poseFirstPerson(PoseStack poseStack, LivingEntity entity, float side, boolean advance, float partialTick) {
      HeroPublicSnapshot snapshot = homelander(entity);
      if (snapshot == null) {
         return;
      }

      Weights w = FIRST.computeIfAbsent(entity, e -> new Weights());
      if (advance) {
         w.advance(laserOn(snapshot), focusOn(snapshot), 15.0F);
      }

      float roar = roarCurve(roarTime(snapshot, partialTick));
      if (w.laser < 0.001F && w.focus < 0.001F && roar <= 0.0F) {
         return;
      }

      float time = (entity.tickCount + partialTick) * 0.9F;
      float tremble = w.laser * 0.012F * Mth.sin(time * 2.7F);
      poseStack.translate(side * (0.04F * w.laser + 0.16F * roar), -0.08F * w.laser - 0.04F * w.focus - 0.28F * roar + tremble, 0.05F * w.laser + 0.1F * roar);
      poseStack.mulPose(new Quaternionf().rotateX(rad(-8.0F * w.laser + 20.0F * roar)));
      poseStack.mulPose(new Quaternionf().rotateZ(rad(side * (6.0F * w.laser + 22.0F * roar))));
   }

   private static float rad(float degrees) {
      return (float)Math.toRadians(degrees);
   }

   /** Weight manager per entity (animation-system §6). Render thread only. */
   private static final class Weights {
      float laser;
      float focus;
      long lastNanos = System.nanoTime();

      void advance(boolean laserOn, boolean focusOn, float k) {
         long now = System.nanoTime();
         float dt = Math.min(0.1F, (now - this.lastNanos) / 1.0E9F);
         this.lastNanos = now;
         float a = 1.0F - (float)Math.exp(-k * dt);
         this.laser = Mth.lerp(a, this.laser, laserOn ? 1.0F : 0.0F);
         this.focus = Mth.lerp(a, this.focus, focusOn ? 1.0F : 0.0F);
      }
   }
}
