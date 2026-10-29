package dev.baranhan.viltrumitecore.client.sinclair;

import dev.baranhan.viltrumitecore.client.anim.RealTime;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairFlags;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairMove;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairRules;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Layers Sinclair's clips (spec §3, plan step 7): real-time idle, walk/run phased by the walked
 * distance, crouch and air poses, the Prestige aim with the look pitch, reload, the card window,
 * the synced move clip (snapshot ticks), the additive shot kick, the head look, prop visibility
 * and the coat springs. The same layering drives the decoy ("running magician") and, with the
 * fp_ clips, the first-person arms.
 */
public final class SinclairPoser {
   /** Vanilla limb swing: one stride cycle per 2π/0.6662 walkAnimation units. */
   static final float STRIDE = (float) (Math.PI * 2.0 / 0.6662);
   private static final Map<Entity, Smooth> SMOOTH = new WeakHashMap<>();

   private SinclairPoser() {
   }

   /** Locomotion input, read from the entity at the render partial tick. */
   public record Motion(float walkPos, float walkSpeed, boolean sprinting, boolean crouching, boolean airborne) {
      static Motion of(LivingEntity entity, float partialTick) {
         boolean flying = entity instanceof net.minecraft.world.entity.player.Player player && player.getAbilities().flying;
         boolean air = !entity.onGround() && !entity.isPassenger() && !entity.isInWater() && !flying && !entity.isFallFlying();
         return new Motion(entity.walkAnimation.position(partialTick), Math.min(1.0F, entity.walkAnimation.speed(partialTick)), entity.isSprinting(),
            entity.isCrouching(), air);
      }
   }

   /** Third-person pose of a Sinclair player; returns the move ticks it used. */
   public static double posePlayer(BakedGeoModel model, LivingEntity player, HeroPublicSnapshot snapshot, float partialTick, float bodyYaw) {
      SinclairMove move = SinclairView.move(snapshot);
      double ticks = SinclairView.moveTicks(player, snapshot, partialTick);
      Motion motion = Motion.of(player, partialTick);
      Smooth smooth = smooth(player, motion);
      locomotion(model, motion, smooth, "idle", "walk", "run");
      int flags = snapshot.heroFlags();
      float pitch = player.getViewXRot(partialTick);
      int sinceShot = SinclairView.extra(snapshot, SinclairFlags.EXTRA_SINCE_SHOT);
      float aim = move == null ? SinclairPoseRules.aimWeight(SinclairFlags.has(flags, SinclairFlags.TRIGGER_HELD), sinceShot) : 0.0F;
      overlay(model, "aim", 0.0, aim);
      if (aim > 0.0F) {
         GeoBone arm = model.getBone("right_arm");
         if (arm != null) {
            arm.rotX -= (float) Math.toRadians(pitch) * aim;
         }
      }

      if (SinclairFlags.has(flags, SinclairFlags.RELOADING) && move == null) {
         overlay(model, "reload", (SinclairRules.RELOAD_TICKS - snapshot.cooldowns()[SinclairFlags.COOLDOWN_RELOAD] + partialTick) / 20.0, 1.0F);
      }

      if (SinclairFlags.has(flags, SinclairFlags.CARD_HELD) && move == null) {
         overlay(model, "window", RealTime.seconds(), 1.0F);
      }

      float moveWeight = 0.0F;
      if (move != null) {
         double seconds = ticks / 20.0;
         moveWeight = SinclairPoseRules.moveWeight(move, seconds, move.length() / 20.0);
         overlay(model, move.clip(), seconds, moveWeight);
      }

      if (sinceShot < 5 && aim > 0.0F) {
         additive(model, "shot", (sinceShot + partialTick) / 20.0, 1.0F);
      }

      head(model, player, partialTick, bodyYaw, move != null && move.locksCasting() ? 1.0F - 0.75F * moveWeight : 1.0F - 0.4F * moveWeight);
      props(model, flags, move, ticks);
      float kick = smooth.kick(SinclairView.extra(snapshot, SinclairFlags.EXTRA_SHOTS), snapshot.actionId());
      CoatPhysics.sinclair(model, player, bodyYaw, partialTick, motion.crouching(), kick);
      return ticks;
   }

   /** The decoy: the running magician (decoy_run by walked distance), idle when stopped, hat on. */
   public static void poseDecoy(BakedGeoModel model, LivingEntity decoy, float partialTick, float bodyYaw) {
      Motion motion = Motion.of(decoy, partialTick);
      Smooth smooth = smooth(decoy, motion);
      locomotion(model, motion, smooth, "idle", "decoy_run", "decoy_run");
      head(model, decoy, partialTick, bodyYaw, 0.5F);
      props(model, 0, null, 0.0);
      CoatPhysics.sinclair(model, decoy, bodyYaw, partialTick, false, 0.0F);
   }

   /**
    * First-person arms (sinclair_arms.geo): fp_idle at real time, the synced fp_ move clip, reload,
    * card window and the shot kick. The arms follow the camera, so there is no look pitch.
    */
   public static void poseArms(BakedGeoModel arms, LivingEntity player, HeroPublicSnapshot snapshot, float partialTick) {
      SinclairMove move = SinclairView.move(snapshot);
      double ticks = SinclairView.moveTicks(player, snapshot, partialTick);
      int flags = snapshot.heroFlags();
      Animation idle = SinclairAssets.clip("fp_idle");
      if (idle != null) {
         AnimationController.seek(idle, arms, loop(idle, RealTime.seconds()));
      } else {
         arms.resetBones();
      }

      if (SinclairFlags.has(flags, SinclairFlags.RELOADING) && move == null) {
         overlay(arms, "fp_reload", (SinclairRules.RELOAD_TICKS - snapshot.cooldowns()[SinclairFlags.COOLDOWN_RELOAD] + partialTick) / 20.0, 1.0F);
      }

      if (SinclairFlags.has(flags, SinclairFlags.CARD_HELD) && move == null) {
         overlay(arms, "fp_window", RealTime.seconds(), 1.0F);
      }

      if (move != null) {
         double seconds = ticks / 20.0;
         overlay(arms, "fp_" + move.clip(), seconds, SinclairPoseRules.moveWeight(move, seconds, move.length() / 20.0));
      }

      int sinceShot = SinclairView.extra(snapshot, SinclairFlags.EXTRA_SINCE_SHOT);
      if (sinceShot < 5 && move == null) {
         additive(arms, "fp_shot", (sinceShot + partialTick) / 20.0, 1.0F);
      }

      props(arms, flags, move, ticks);
   }

   /** Hat on the head / in the hand / away, card in the palm (rules: {@link SinclairPoseRules}). */
   static void props(BakedGeoModel model, int flags, @Nullable SinclairMove move, double ticks) {
      hide(model, "hat", !SinclairPoseRules.headHat(flags, move, ticks));
      hide(model, "held_hat", !SinclairPoseRules.heldHat(flags, move, ticks));
      hide(model, "card", !SinclairPoseRules.card(flags, move, ticks));
   }

   private static void locomotion(BakedGeoModel model, Motion motion, Smooth smooth, String idleName, String walkName, String runName) {
      Animation idle = SinclairAssets.clip(idleName);
      if (idle != null) {
         AnimationController.seek(idle, model, loop(idle, RealTime.seconds()));
      } else {
         model.resetBones();
      }

      float cycle = motion.walkPos() / STRIDE;
      cycle -= (float) Math.floor(cycle);
      Animation walk = SinclairAssets.clip(walkName);
      if (walk != null && smooth.walk > 0.01F) {
         AnimationController.overlay(walk, model, cycle * walk.lengthSeconds(), smooth.walk * (1.0F - smooth.run));
      }

      Animation run = SinclairAssets.clip(runName);
      if (run != null && smooth.walk * smooth.run > 0.01F) {
         AnimationController.overlay(run, model, cycle * run.lengthSeconds(), smooth.walk * smooth.run);
      }

      overlay(model, "crouch", 0.0, smooth.crouch);
      overlay(model, "air", RealTime.seconds(), smooth.air);
   }

   private static void head(BakedGeoModel model, LivingEntity entity, float partialTick, float bodyYaw, float weight) {
      GeoBone head = model.getBone("head");
      if (head == null || weight <= 0.0F) {
         return;
      }

      float yaw = Mth.wrapDegrees(Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot) - bodyYaw);
      float pitch = entity.getViewXRot(partialTick);
      head.rotY += (float) Math.toRadians(-Mth.clamp(yaw, -75.0F, 75.0F)) * weight;
      head.rotX += (float) Math.toRadians(-pitch) * weight;
   }

   static void overlay(BakedGeoModel model, String name, double seconds, float weight) {
      if (weight <= 0.0F) {
         return;
      }

      Animation clip = SinclairAssets.clip(name);
      if (clip != null) {
         AnimationController.overlay(clip, model, loop(clip, seconds), weight);
      }
   }

   /** Resets the model and poses it at one clip time (props shared by several draws in a frame). */
   static void overlayFresh(BakedGeoModel model, String name, double seconds) {
      model.resetBones();
      Animation clip = SinclairAssets.clip(name);
      if (clip != null) {
         AnimationController.overlay(clip, model, loop(clip, seconds));
      }
   }

   static void additive(BakedGeoModel model, String name, double seconds, float weight) {
      Animation clip = SinclairAssets.clip(name);
      if (clip != null) {
         AnimationController.additive(clip, model, Math.min(seconds, clip.lengthSeconds()), weight);
      }
   }

   /** Loop clips wrap, others hold their last frame. */
   static double loop(Animation clip, double seconds) {
      if (!Double.isFinite(seconds) || seconds <= 0.0 || clip.lengthSeconds() <= 0.0) {
         return 0.0;
      }

      return clip.loopType() == Animation.LoopType.LOOP ? seconds % clip.lengthSeconds() : Math.min(seconds, clip.lengthSeconds());
   }

   static void hide(BakedGeoModel model, String name, boolean hidden) {
      GeoBone bone = model.getBone(name);
      if (bone != null) {
         bone.hidden = hidden;
         if (hidden) {
            for (GeoBone child : bone.children) {
               child.hidden = true;
            }
         }
      }
   }

   private static Smooth smooth(Entity entity, Motion motion) {
      Smooth s = SMOOTH.computeIfAbsent(entity, e -> new Smooth());
      s.update(motion, Util.getNanos());
      return s;
   }

   public static void forget(Entity entity) {
      SMOOTH.remove(entity);
      CoatPhysics.forget(entity);
      SinclairView.forget(entity);
   }

   /** Per-entity eased layer weights (real time) and gesture edges for the coat kick. */
   static final class Smooth {
      float walk;
      float run;
      float crouch;
      float air;
      long last = -1L;
      int shots = -1;
      int action = -2;

      void update(Motion m, long now) {
         float dt = this.last < 0L ? 1.0F : Math.min(1.0F, (now - this.last) / 1.0E9F);
         this.last = now;
         if (dt <= 0.0F) {
            return;
         }

         float k = Math.min(1.0F, dt * 10.0F);
         this.walk += (Mth.clamp(m.walkSpeed() * 1.6F, 0.0F, 1.0F) - this.walk) * k;
         this.run += ((m.sprinting() ? 1.0F : 0.0F) - this.run) * k;
         this.crouch += ((m.crouching() ? 1.0F : 0.0F) - this.crouch) * Math.min(1.0F, dt * 14.0F);
         this.air += ((m.airborne() ? 1.0F : 0.0F) - this.air) * Math.min(1.0F, dt * 8.0F);
      }

      /** Coat kick (degrees per second) on a new shot or a new move. */
      float kick(int shots, int action) {
         float kick = 0.0F;
         if (this.shots >= 0 && shots != this.shots) {
            kick += 90.0F;
         }

         if (this.action != -2 && action != this.action && action >= 0) {
            kick += 140.0F;
         }

         this.shots = shots;
         this.action = action;
         return kick;
      }
   }
}
