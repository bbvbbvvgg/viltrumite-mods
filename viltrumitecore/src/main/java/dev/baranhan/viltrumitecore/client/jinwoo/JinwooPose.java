package dev.baranhan.viltrumitecore.client.jinwoo;

import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.pose.ActionClock;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooFlags;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooMove;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooRules;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Jinwoo's pose: stance weights smoothed exponentially in real time (no per-frame lerp), one action clip
 * on the server action clock (same clock for first and third person), block overlay, dagger
 * assembly/dissolve scale and the coat/hair secondary motion. Client only.
 */
public final class JinwooPose {
   /** Stance clips, in the order of the weight array. */
   static final String[] STANCES = {"idle_calm", "idle_combat", "walk", "walk_combat", "run", "crouch", "air", "swim"};
   static final int CALM = 0, COMBAT = 1, WALK = 2, WALK_COMBAT = 3, RUN = 4, CROUCH = 5, AIR = 6, SWIM = 7;
   /** Exponential stance rate (1/s): about 0.15 s to settle. */
   static final float STANCE_RATE = 14.0F;
   static final float BLOCK_RATE = 22.0F;
   /** Vanilla limb swing units per full gait cycle (2 pi / 0.6662). */
   static final double GAIT_CYCLE = 9.4315;
   /** Seconds an ended combo clip fades out over the stance. */
   static final double RELEASE = 0.12;
   /**
    * Seconds a long skill / the ult fades out (playtest: after the 5 s ult the body jumped from the
    * last ult frame into the stance in 0.12 s, which read as a snap).
    */
   static final double SKILL_RELEASE = 0.35;

   static final class State {
      final float[] w = new float[STANCES.length];
      final ActionClock clock = new ActionClock();
      long last;
      float block;
      @Nullable Animation lastClip;
      double lastSeconds;
      double lastRelease = RELEASE;
      long releaseStart = -1;
      // secondary motion (bedrock degrees)
      float tail, tailV, side, sideV, hair, hairV;
      float prevYaw = Float.NaN;
      boolean init;
      float hairOut;
   }

   private static final Map<AbstractClientPlayer, State> THIRD = new WeakHashMap<>();
   private static final Map<AbstractClientPlayer, State> FIRST = new WeakHashMap<>();

   private JinwooPose() {
   }

   public static void reset(AbstractClientPlayer player) {
      THIRD.remove(player);
      FIRST.remove(player);
   }

   /** Server-clock action ticks of the player's current move (shared by both views and the VFX). */
   public static double actionTicks(AbstractClientPlayer player, HeroPublicSnapshot s, float partial) {
      State st = THIRD.computeIfAbsent(player, p -> new State());
      return sampleTicks(st, player, s, partial);
   }

   private static double sampleTicks(State st, AbstractClientPlayer player, HeroPublicSnapshot s, float partial) {
      if (s.actionId() < 0) {
         return 0;
      }
      double client = player.tickCount + (Float.isFinite(partial) ? partial : 0);
      return st.clock.sample(s.actionId(), s.variant(), s.actionElapsed(), s.actionLength(), client);
   }

   /** Third person: poses the body model (the gear model copies it afterwards). */
   public static void thirdPerson(BakedGeoModel body, AbstractClientPlayer player, HeroPublicSnapshot s, float partial) {
      State st = THIRD.computeIfAbsent(player, p -> new State());
      long now = Util.getNanos();
      float dt = dt(st, now);
      stanceTargets(st, player, s, partial, dt, false);
      double idleSeconds = (player.tickCount + partial) / 20.0;
      double gait = player.walkAnimation.position(partial) / GAIT_CYCLE;
      blendStances(body, st, STANCES_FILE(false), idleSeconds, gait);
      overlays(body, st, player, s, partial, now, dt, false);
      secondary(body, st, player, partial, dt);
      daggers(body, s, actionTicks(player, s, partial));
   }

   /** First person: poses the arms model with the first-person clips of the same names. */
   public static void firstPerson(BakedGeoModel arms, AbstractClientPlayer player, HeroPublicSnapshot s, float partial) {
      State st = FIRST.computeIfAbsent(player, p -> new State());
      long now = Util.getNanos();
      float dt = dt(st, now);
      stanceTargets(st, player, s, partial, dt, true);
      double idleSeconds = (player.tickCount + partial) / 20.0;
      double gait = player.walkAnimation.position(partial) / GAIT_CYCLE;
      blendStances(arms, st, FP_FILE, idleSeconds, gait);
      overlays(arms, st, player, s, partial, now, dt, true);
      daggers(arms, s, actionTicks(player, s, partial));
   }

   private static final ResourceLocation FP_FILE = JinwooView.FP;

   private static ResourceLocation STANCES_FILE(boolean fp) {
      return fp ? JinwooView.FP : JinwooView.STANCES;
   }

   private static float dt(State st, long now) {
      float dt = st.last == 0 ? 1.0F : (now - st.last) / 1.0E9F;
      st.last = now;
      return Mth.clamp(dt, 0.0F, 0.25F);
   }

   private static void stanceTargets(State st, AbstractClientPlayer player, HeroPublicSnapshot s, float partial, float dt, boolean fp) {
      float[] target = new float[STANCES.length];
      boolean drawn = drawnPose(s);
      float speed = Math.min(1.0F, player.walkAnimation.speed(partial) * 1.6F);
      boolean swim = player.isVisuallySwimming() || player.isFallFlying();
      boolean air = !player.onGround() && !player.isPassenger() && !player.isInWater() && !player.getAbilities().flying;
      if (fp) {
         // First person keeps only the arm sets: hidden (calm), guard, run.
         int idle = drawn ? COMBAT : CALM;
         if (drawn && player.isSprinting() && speed > 0.2F && !air) {
            target[RUN] = 1;
         } else {
            target[idle] = 1;
         }
      } else if (swim) {
         target[SWIM] = 1;
      } else if (air) {
         target[AIR] = 1;
      } else if (player.isCrouching()) {
         target[CROUCH] = 1;
      } else {
         int idle = drawn ? COMBAT : CALM;
         int loco = player.isSprinting() ? RUN : drawn ? WALK_COMBAT : WALK;
         target[idle] = 1 - speed;
         target[loco] = speed;
      }
      float k = st.init ? 1.0F - (float)Math.exp(-STANCE_RATE * dt) : 1.0F;
      st.init = true;
      for (int i = 0; i < target.length; i++) {
         st.w[i] += (target[i] - st.w[i]) * k;
      }
   }

   /** Weighted average of the stance clips: seek the first, then overlay each with w_i / sum so far. */
   private static void blendStances(BakedGeoModel model, State st, ResourceLocation file, double idleSeconds, double gait) {
      model.resetBones();
      float sum = 0;
      for (int i = 0; i < STANCES.length; i++) {
         float w = st.w[i];
         if (w < 0.003F) {
            continue;
         }
         Animation clip = AnimCache.animation(file, STANCES[i]);
         if (clip == null) {
            continue;
         }
         double t = i == WALK || i == WALK_COMBAT || i == RUN || i == SWIM
            ? frac(gait) * clip.lengthSeconds() : idleSeconds % Math.max(0.05, clip.lengthSeconds());
         sum += w;
         AnimationController.overlay(clip, model, t, w / sum);
      }
   }

   private static void overlays(BakedGeoModel model, State st, AbstractClientPlayer player, HeroPublicSnapshot s, float partial, long now, float dt, boolean fp) {
      ResourceLocation stanceFile = fp ? JinwooView.FP : JinwooView.COMBAT;
      boolean blocking = JinwooFlags.has(s.heroFlags(), JinwooFlags.BLOCKING);
      float kb = 1.0F - (float)Math.exp(-BLOCK_RATE * dt);
      st.block += ((blocking ? 1 : 0) - st.block) * kb;
      if (st.block > 0.01F) {
         Animation block = AnimCache.animation(stanceFile, "block");
         if (block != null) {
            AnimationController.overlay(block, model, ((player.tickCount + partial) / 20.0) % block.lengthSeconds(), st.block);
         }
      }

      JinwooMove move = JinwooView.move(s);
      if (move != null) {
         Animation clip = AnimCache.animation(fp ? JinwooView.FP : JinwooView.fileOf(move), move.clip());
         if (clip != null) {
            double ticks = sampleTicks(st, player, s, partial);
            double seconds = Math.min(ticks / 20.0, clip.lengthSeconds());
            boolean quick = move.isCombo() || move == JinwooMove.RIPOSTE;
            float w = quick ? 1.0F : (float)smooth(Math.min(1.0, ticks / 2.0));
            AnimationController.overlay(clip, model, seconds, w);
            st.lastClip = clip;
            st.lastSeconds = seconds;
            st.lastRelease = releaseSeconds(quick);
            st.releaseStart = -1;
         }
      } else if (st.lastClip != null) {
         if (st.releaseStart < 0) {
            st.releaseStart = now;
         }
         double since = (now - st.releaseStart) / 1.0E9;
         if (since >= st.lastRelease) {
            st.lastClip = null;
         } else {
            AnimationController.overlay(st.lastClip, model, st.lastSeconds, (float)smooth(1.0 - since / st.lastRelease));
         }
      }
   }

   /** Pure: fade-out of an ended clip — short after a combo strike, longer after a skill or the ult. */
   static double releaseSeconds(boolean quick) {
      return quick ? RELEASE : SKILL_RELEASE;
   }

   /** Coat tails and hair: damped springs on forward speed, fall speed and turn rate (degrees). */
   private static void secondary(BakedGeoModel model, State st, AbstractClientPlayer player, float partial, float dt) {
      float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
      float yawRate = Float.isNaN(st.prevYaw) || dt <= 0 ? 0 : Mth.wrapDegrees(bodyYaw - st.prevYaw) / Math.max(dt, 1.0E-3F);
      st.prevYaw = bodyYaw;
      Vec3 v = player.getDeltaMovement();
      double yawRad = Math.toRadians(bodyYaw);
      double forward = -v.x * Math.sin(yawRad) + v.z * Math.cos(yawRad);
      float fwd = (float)Math.max(0, forward) * 20.0F; // blocks per second
      float fall = (float)v.y * 20.0F;
      float tailTarget = Mth.clamp(fwd * 9.0F + Math.max(0, -fall) * 3.0F - Math.max(0, fall) * 2.0F, -10.0F, 75.0F);
      float sideTarget = Mth.clamp(-yawRate * 0.06F, -25.0F, 25.0F);
      float hairTarget = Mth.clamp(fwd * 3.5F + Math.max(0, -fall) * 1.5F, 0.0F, 30.0F);
      // semi-implicit spring: stiffness 90, damping 12 (slight overshoot)
      float[] a = spring(st.tail, st.tailV, tailTarget, dt, 90, 12);
      st.tail = a[0];
      st.tailV = a[1];
      a = spring(st.side, st.sideV, sideTarget, dt, 70, 10);
      st.side = a[0];
      st.sideV = a[1];
      a = spring(st.hair, st.hairV, hairTarget, dt, 120, 14);
      st.hair = a[0];
      st.hairV = a[1];
      float sway = (float)Math.sin((player.tickCount + partial) * 0.11) * 2.0F;
      addDeg(model.getBone("coat_tail_right"), st.tail + sway, 0, st.side);
      addDeg(model.getBone("coat_tail_left"), st.tail - sway, 0, st.side);
      addDeg(model.getBone("coat_tail_right_lower"), st.tail * 0.45F + st.tailV * 0.02F, 0, st.side * 0.5F);
      addDeg(model.getBone("coat_tail_left_lower"), st.tail * 0.45F + st.tailV * 0.02F, 0, st.side * 0.5F);
      st.hairOut = st.hair;
   }

   static float[] spring(float x, float v, float target, float dt, float k, float d) {
      int steps = Math.max(1, (int)Math.ceil(dt / 0.01F));
      float h = dt / steps;
      for (int i = 0; i < steps; i++) {
         v += ((target - x) * k - v * d) * h;
         x += v * h;
      }
      return new float[]{x, v};
   }

   /** Hair lift of the last third-person pose (applied to the gear model's hair bones). */
   public static void hair(BakedGeoModel gear, AbstractClientPlayer player) {
      State st = THIRD.get(player);
      if (st == null) {
         return;
      }
      addDeg(gear.getBone("hair"), st.hairOut * 0.4F, 0, 0);
      addDeg(gear.getBone("hair_fringe"), -st.hairOut * 0.5F, 0, 0);
   }

   /** Daggers: hidden while sheathed; they assemble from shadow on a draw and dissolve on a sheath (scale). */
   static void daggers(BakedGeoModel model, HeroPublicSnapshot s, double ticks) {
      float scale = daggerScale(s, ticks);
      for (String name : new String[]{"right_dagger", "left_dagger"}) {
         GeoBone bone = model.getBone(name);
         if (bone == null) {
            continue;
         }
         bone.hidden = scale <= 0.02F;
         bone.scaleX *= scale;
         bone.scaleY *= scale;
         bone.scaleZ *= scale;
      }
   }

   /** Pure: dagger scale 0..1 from the drawn flag and the draw/sheath clock. */
   public static float daggerScale(HeroPublicSnapshot s, double ticks) {
      JinwooMove move = JinwooView.move(s);
      if (move == JinwooMove.DRAW) {
         return (float)smooth(Mth.clamp((ticks - 3.0) / (JinwooRules.DRAW_TICKS - 5.0), 0.0, 1.0));
      }
      if (move == JinwooMove.SHEATH) {
         return (float)smooth(1.0 - Mth.clamp((ticks - 1.0) / (JinwooRules.SHEATH_TICKS - 3.0), 0.0, 1.0));
      }
      return drawnPose(s) ? 1.0F : 0.0F;
   }

   /** The combat stance while the daggers are out or a combat move runs. */
   public static boolean drawnPose(HeroPublicSnapshot s) {
      if (JinwooFlags.has(s.heroFlags(), JinwooFlags.DAGGERS) || JinwooFlags.has(s.heroFlags(), JinwooFlags.DRAWING)) {
         return true;
      }
      JinwooMove move = JinwooView.move(s);
      return move != null && move != JinwooMove.SHEATH && move.isCombo();
   }

   /** Copies the posed transforms by bone name (body -> gear: one skeleton, two meshes). */
   public static void copyPose(BakedGeoModel from, BakedGeoModel to) {
      to.resetBones();
      for (GeoBone bone : to.allBones()) {
         GeoBone src = from.getBone(bone.name);
         if (src == null) {
            continue;
         }
         bone.rotX = src.rotX;
         bone.rotY = src.rotY;
         bone.rotZ = src.rotZ;
         bone.posX = src.posX;
         bone.posY = src.posY;
         bone.posZ = src.posZ;
         bone.scaleX = src.scaleX;
         bone.scaleY = src.scaleY;
         bone.scaleZ = src.scaleZ;
         bone.hidden = src.hidden;
      }
   }

   /** Adds a rotation given in Bedrock degrees (x and y are mirrored in the baked model). */
   public static void addDeg(@Nullable GeoBone bone, float x, float y, float z) {
      if (bone == null) {
         return;
      }
      bone.rotX += (float)Math.toRadians(-x);
      bone.rotY += (float)Math.toRadians(-y);
      bone.rotZ += (float)Math.toRadians(z);
   }

   static double frac(double v) {
      return v - Math.floor(v);
   }

   static double smooth(double t) {
      t = Math.max(0, Math.min(1, t));
      return t * t * (3 - 2 * t);
   }
}
