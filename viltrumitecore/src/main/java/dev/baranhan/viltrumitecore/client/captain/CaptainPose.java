package dev.baranhan.viltrumitecore.client.captain;

import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.pose.ActionClock;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.captain.CaptainFlags;
import dev.baranhan.viltrumitecore.hero.captain.CaptainMove;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Steve's pose: stance weights smoothed exponentially in real time, one action clip on the server action clock
 * (same clock for first and third person), the guard overlay (shield block, or crossed forearms while the
 * shield flies) and the shield visibility. Client only.
 */
public final class CaptainPose {
   static final String[] STANCES = {"idle_calm", "idle_combat", "walk", "walk_combat", "run", "crouch", "air", "swim"};
   static final int CALM = 0, COMBAT = 1, WALK = 2, WALK_COMBAT = 3, RUN = 4, CROUCH = 5, AIR = 6, SWIM = 7;
   static final float STANCE_RATE = 12.0F;
   static final float BLOCK_RATE = 24.0F;
   /** Vanilla limb swing units per full gait cycle (2 pi / 0.6662). */
   static final double GAIT_CYCLE = 9.4315;
   static final double RELEASE = 0.12;
   static final double SKILL_RELEASE = 0.3;

   static final class State {
      final float[] w = new float[STANCES.length];
      final ActionClock clock = new ActionClock();
      long last;
      float block;
      @Nullable Animation lastClip;
      double lastSeconds;
      double lastRelease = RELEASE;
      long releaseStart = -1;
      boolean init;
   }

   private static final Map<AbstractClientPlayer, State> THIRD = new WeakHashMap<>();
   private static final Map<AbstractClientPlayer, State> FIRST = new WeakHashMap<>();

   private CaptainPose() {
   }

   public static void reset(AbstractClientPlayer player) {
      THIRD.remove(player);
      FIRST.remove(player);
   }

   /** Server-clock ticks of the current move (shared by both views and the VFX). */
   public static double actionTicks(AbstractClientPlayer player, HeroPublicSnapshot s, float partial) {
      return sampleTicks(THIRD.computeIfAbsent(player, p -> new State()), player, s, partial);
   }

   private static double sampleTicks(State st, AbstractClientPlayer player, HeroPublicSnapshot s, float partial) {
      if (s.actionId() < 0) {
         return 0;
      }
      double client = player.tickCount + (Float.isFinite(partial) ? partial : 0);
      return st.clock.sample(s.actionId(), s.variant(), s.actionElapsed(), s.actionLength(), client);
   }

   public static void thirdPerson(BakedGeoModel body, AbstractClientPlayer player, HeroPublicSnapshot s, float partial) {
      State st = THIRD.computeIfAbsent(player, p -> new State());
      pose(body, st, player, s, partial, false);
   }

   public static void firstPerson(BakedGeoModel arms, AbstractClientPlayer player, HeroPublicSnapshot s, float partial) {
      State st = FIRST.computeIfAbsent(player, p -> new State());
      pose(arms, st, player, s, partial, true);
   }

   private static void pose(BakedGeoModel model, State st, AbstractClientPlayer player, HeroPublicSnapshot s, float partial, boolean fp) {
      long now = Util.getNanos();
      float dt = st.last == 0 ? 1.0F : Mth.clamp((now - st.last) / 1.0E9F, 0.0F, 0.25F);
      st.last = now;
      stanceTargets(st, player, s, partial, dt, fp);
      double idleSeconds = (player.tickCount + partial) / 20.0;
      double gait = player.walkAnimation.position(partial) / GAIT_CYCLE;
      blend(model, st, fp ? CaptainView.FP : CaptainView.STANCES, idleSeconds, gait);
      overlays(model, st, player, s, partial, now, dt, fp);
      shield(model, s, sampleTicks(st, player, s, partial));
   }

   /** Fighting stance while in combat, guarding, the shield flies or a move runs. */
   public static boolean combatPose(HeroPublicSnapshot s) {
      return CaptainView.flag(s, CaptainFlags.IN_COMBAT) || CaptainView.flag(s, CaptainFlags.GUARDING) || !CaptainView.shieldInHand(s)
         || s.actionId() >= 0;
   }

   private static void stanceTargets(State st, AbstractClientPlayer player, HeroPublicSnapshot s, float partial, float dt, boolean fp) {
      float[] target = new float[STANCES.length];
      boolean combat = combatPose(s);
      float speed = Math.min(1.0F, player.walkAnimation.speed(partial) * 1.6F);
      boolean swim = player.isVisuallySwimming() || player.isFallFlying();
      boolean air = !player.onGround() && !player.isPassenger() && !player.isInWater() && !player.getAbilities().flying;
      if (fp) {
         int idle = combat ? COMBAT : CALM;
         if (player.isSprinting() && speed > 0.2F && !air) {
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
         int idle = combat ? COMBAT : CALM;
         int loco = player.isSprinting() ? RUN : combat ? WALK_COMBAT : WALK;
         target[idle] = 1 - speed;
         target[loco] = speed;
      }
      float k = st.init ? 1.0F - (float)Math.exp(-STANCE_RATE * dt) : 1.0F;
      st.init = true;
      for (int i = 0; i < target.length; i++) {
         st.w[i] += (target[i] - st.w[i]) * k;
      }
   }

   private static void blend(BakedGeoModel model, State st, ResourceLocation file, double idleSeconds, double gait) {
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
      ResourceLocation guardFile = fp ? CaptainView.FP : CaptainView.STANCES;
      boolean guarding = CaptainView.flag(s, CaptainFlags.GUARDING) && s.actionId() < 0;
      float kb = 1.0F - (float)Math.exp(-BLOCK_RATE * dt);
      st.block += ((guarding ? 1 : 0) - st.block) * kb;
      if (st.block > 0.01F) {
         Animation block = AnimCache.animation(guardFile, CaptainView.shieldInHand(s) ? "block" : "block_arms");
         if (block != null) {
            AnimationController.overlay(block, model, ((player.tickCount + partial) / 20.0) % block.lengthSeconds(), (float)smooth(st.block));
         }
      }
      CaptainMove move = CaptainView.move(s);
      if (move != null) {
         Animation clip = AnimCache.animation(fp ? CaptainView.FP : CaptainView.fileOf(move), move.clip());
         if (clip != null) {
            double ticks = sampleTicks(st, player, s, partial);
            double seconds = CaptainView.loops(move) ? (ticks / 20.0) % clip.lengthSeconds() : Math.min(ticks / 20.0, clip.lengthSeconds());
            boolean quick = move.isCombo() || move == CaptainMove.PARRY || move == CaptainMove.CATCH;
            float w = quick ? 1.0F : (float)smooth(Math.min(1.0, ticks / 2.0));
            AnimationController.overlay(clip, model, seconds, w);
            st.lastClip = clip;
            st.lastSeconds = seconds;
            st.lastRelease = quick ? RELEASE : SKILL_RELEASE;
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

   /** The shield bone is empty (the shield model is drawn on it); only the hidden flag matters to the renderer. */
   public static void shield(BakedGeoModel model, HeroPublicSnapshot s, double ticks) {
      GeoBone bone = model.getBone("left_shield");
      if (bone != null) {
         bone.hidden = !shieldVisible(s, ticks);
      }
   }

   /** Pure: the shield is on the arm unless thrown; during the throw it stays until the release tick. */
   public static boolean shieldVisible(HeroPublicSnapshot s, double ticks) {
      CaptainMove move = CaptainView.move(s);
      if (move == CaptainMove.THROW && ticks < dev.baranhan.viltrumitecore.hero.captain.CaptainRules.THROW_RELEASE_TICK) {
         return true;
      }
      return CaptainView.shieldInHand(s);
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
