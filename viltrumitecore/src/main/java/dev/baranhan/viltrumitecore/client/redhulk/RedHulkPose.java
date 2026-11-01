package dev.baranhan.viltrumitecore.client.redhulk;

import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.pose.ActionClock;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.redhulk.RedHulkFlags;
import dev.baranhan.viltrumitecore.hero.redhulk.RedHulkMove;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Red Hulk's pose, layered: stance weights (calm / rage / combat idles, heavy walk, rage walk, run, crouch, rising
 * and falling air poses, swim) smoothed exponentially in real time; the face layer (calm, snarl, fury, pant, a roar
 * flash when hurt) chosen by heat and state; short body overlays (jump take-off, landing, hurt flinch) on their own
 * timers; then the server-clocked move clip on top (same clock in both views). A finished move releases over
 * {@link #RELEASE} / {@link #SKILL_RELEASE} seconds instead of snapping back. Client only.
 */
public final class RedHulkPose {
   static final String[] STANCES = {"idle", "idle_rage", "idle_combat", "walk", "walk_rage", "run", "crouch", "air_up", "air_down", "swim"};
   static final int IDLE = 0, IDLE_RAGE = 1, IDLE_COMBAT = 2, WALK = 3, WALK_RAGE = 4, RUN = 5, CROUCH = 6, AIR_UP = 7, AIR_DOWN = 8, SWIM = 9;
   static final String[] FACES = {"face_calm", "face_snarl", "face_fury", "face_pant", "face_roar"};
   static final int CALM = 0, SNARL = 1, FURY = 2, PANT = 3, ROAR = 4;
   static final float STANCE_RATE = 9.0F;
   static final float FACE_RATE = 6.0F;
   /** Vanilla limb swing units per full gait cycle (2 pi / 0.6662); the heavy walk takes one cycle per two steps. */
   static final double GAIT_CYCLE = 9.4315;
   static final double RELEASE = 0.16;
   static final double SKILL_RELEASE = 0.34;
   static final double JUMP_SECONDS = 0.3;
   static final double LAND_SECONDS = 0.5;

   static final class State {
      final float[] w = new float[STANCES.length];
      final float[] face = new float[FACES.length];
      final ActionClock clock = new ActionClock();
      long last;
      boolean init;
      @Nullable Animation lastClip;
      double lastSeconds;
      double lastRelease = RELEASE;
      long releaseStart = -1;
      boolean wasAir;
      long jumpStart = -1;
      long landStart = -1;
      float landWeight;
      double fallSpeed;
   }

   private static final Map<AbstractClientPlayer, State> THIRD = new WeakHashMap<>();
   private static final Map<AbstractClientPlayer, State> FIRST = new WeakHashMap<>();

   private RedHulkPose() {
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
      pose(body, THIRD.computeIfAbsent(player, p -> new State()), player, s, partial, false);
   }

   public static void firstPerson(BakedGeoModel arms, AbstractClientPlayer player, HeroPublicSnapshot s, float partial) {
      pose(arms, FIRST.computeIfAbsent(player, p -> new State()), player, s, partial, true);
   }

   /** Static pose for the GUI previews (inventory, race screen): the calm idle with a snarl. */
   public static void preview(BakedGeoModel body, double seconds) {
      body.resetBones();
      Animation idle = AnimCache.animation(RedHulkView.STANCES, "idle");
      if (idle != null) {
         AnimationController.overlay(idle, body, seconds % idle.lengthSeconds(), 1.0F);
      }
      Animation face = AnimCache.animation(RedHulkView.FACE, "face_snarl");
      if (face != null) {
         AnimationController.overlay(face, body, seconds % face.lengthSeconds(), 1.0F);
      }
   }

   private static void pose(BakedGeoModel model, State st, AbstractClientPlayer player, HeroPublicSnapshot s, float partial, boolean fp) {
      long now = Util.getNanos();
      float dt = st.last == 0 ? 1.0F : Mth.clamp((now - st.last) / 1.0E9F, 0.0F, 0.25F);
      st.last = now;
      stanceTargets(st, player, s, partial, dt, fp, now);
      double idleSeconds = (player.tickCount + partial) / 20.0;
      double gait = player.walkAnimation.position(partial) / GAIT_CYCLE;
      blend(model, st, fp ? RedHulkView.FP : RedHulkView.STANCES, idleSeconds, gait, fp);
      if (!fp) {
         face(model, st, player, s, idleSeconds, dt);
         bodyOverlays(model, st, player, partial, now);
      }
      move(model, st, player, s, partial, now, fp);
   }

   /** Fighting stance while in combat, holding a body or running a move. */
   public static boolean combatPose(HeroPublicSnapshot s) {
      return RedHulkView.flag(s, RedHulkFlags.IN_COMBAT) || RedHulkView.flag(s, RedHulkFlags.HOLDING) || s.actionId() >= 0;
   }

   private static void stanceTargets(State st, AbstractClientPlayer player, HeroPublicSnapshot s, float partial, float dt, boolean fp, long now) {
      float[] target = new float[STANCES.length];
      boolean combat = combatPose(s);
      boolean rage = RedHulkView.rage(s);
      float speed = Math.min(1.0F, player.walkAnimation.speed(partial) * 1.6F);
      boolean swim = player.isVisuallySwimming() || player.isFallFlying();
      boolean air = !player.onGround() && !player.isPassenger() && !player.isInWater() && !player.getAbilities().flying;
      double vy = player.getDeltaMovement().y;
      if (air && !st.wasAir && vy > 0.05) {
         st.jumpStart = now;
      }
      if (!air && st.wasAir && st.init) {
         st.landStart = now;
         st.landWeight = (float)Mth.clamp(-st.fallSpeed / 0.9, 0.35, 1.0);
      }
      if (air) {
         st.fallSpeed = vy;
      }
      st.wasAir = air;
      int idle = rage ? IDLE_RAGE : combat ? IDLE_COMBAT : IDLE;
      if (fp) {
         if (player.isSprinting() && speed > 0.2F && !air) {
            target[RUN] = 1;
         } else {
            target[combat || rage ? IDLE_COMBAT : IDLE] = 1;
         }
      } else if (swim) {
         target[SWIM] = 1;
      } else if (air) {
         float down = (float)Mth.clamp(0.5 - vy * 2.5, 0.0, 1.0);
         target[AIR_UP] = 1 - down;
         target[AIR_DOWN] = down;
      } else if (player.isCrouching()) {
         target[CROUCH] = 1;
      } else {
         int loco = player.isSprinting() ? RUN : rage ? WALK_RAGE : WALK;
         target[idle] = 1 - speed;
         target[loco] = speed;
      }
      float k = st.init ? 1.0F - (float)Math.exp(-STANCE_RATE * dt) : 1.0F;
      st.init = true;
      for (int i = 0; i < target.length; i++) {
         st.w[i] += (target[i] - st.w[i]) * k;
      }
   }

   private static void blend(BakedGeoModel model, State st, ResourceLocation file, double idleSeconds, double gait, boolean fp) {
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
         boolean cyclic = i == WALK || i == WALK_RAGE || i == RUN || i == SWIM;
         double t = cyclic ? frac(gait) * clip.lengthSeconds() : idleSeconds % Math.max(0.05, clip.lengthSeconds());
         sum += w;
         AnimationController.overlay(clip, model, t, w / sum);
      }
   }

   /** Pure: the face the heat and state call for (index into {@link #FACES}). */
   static int faceFor(int tier, boolean fury, boolean overheat, boolean combat, boolean hurt) {
      if (hurt) {
         return ROAR;
      }
      if (overheat) {
         return PANT;
      }
      if (fury || tier >= 3) {
         return FURY;
      }
      return tier >= 1 || combat ? SNARL : CALM;
   }

   private static void face(BakedGeoModel model, State st, AbstractClientPlayer player, HeroPublicSnapshot s, double seconds, float dt) {
      int want = faceFor(RedHulkView.tier(s), RedHulkView.furious(s), RedHulkView.overheated(s), combatPose(s), player.hurtTime > 6);
      float k = 1.0F - (float)Math.exp(-(want == ROAR ? FACE_RATE * 3 : FACE_RATE) * dt);
      float sum = 0;
      for (int i = 0; i < FACES.length; i++) {
         st.face[i] += ((i == want ? 1 : 0) - st.face[i]) * k;
         float w = st.face[i];
         if (w < 0.01F) {
            continue;
         }
         Animation clip = AnimCache.animation(RedHulkView.FACE, FACES[i]);
         if (clip == null) {
            continue;
         }
         sum += w;
         AnimationController.overlay(clip, model, seconds % Math.max(0.05, clip.lengthSeconds()), w / sum);
      }
   }

   /** Jump take-off and landing compression (weighted by the fall speed) on real-time timers; the hurt flinch on hurtTime. */
   private static void bodyOverlays(BakedGeoModel model, State st, AbstractClientPlayer player, float partial, long now) {
      if (st.jumpStart >= 0) {
         double t = (now - st.jumpStart) / 1.0E9;
         Animation jump = AnimCache.animation(RedHulkView.STANCES, "jump");
         if (jump == null || t > JUMP_SECONDS) {
            st.jumpStart = -1;
         } else {
            AnimationController.overlay(jump, model, Math.min(t, jump.lengthSeconds()), (float)smooth(1.0 - t / JUMP_SECONDS) * 0.9F);
         }
      }
      if (st.landStart >= 0) {
         double t = (now - st.landStart) / 1.0E9;
         Animation land = AnimCache.animation(RedHulkView.STANCES, "land");
         if (land == null || t > LAND_SECONDS) {
            st.landStart = -1;
         } else {
            AnimationController.overlay(land, model, Math.min(t, land.lengthSeconds()), (float)smooth(1.0 - t / LAND_SECONDS) * st.landWeight);
         }
      }
      if (player.hurtTime > 0) {
         Animation hurt = AnimCache.animation(RedHulkView.STANCES, "hurt");
         if (hurt != null) {
            double t = (10 - player.hurtTime + partial) / 20.0;
            AnimationController.overlay(hurt, model, Math.min(t, hurt.lengthSeconds()), (float)smooth(player.hurtTime / 10.0) * 0.8F);
         }
      }
   }

   private static void move(BakedGeoModel model, State st, AbstractClientPlayer player, HeroPublicSnapshot s, float partial, long now, boolean fp) {
      RedHulkMove move = RedHulkView.move(s);
      if (move != null) {
         Animation clip = AnimCache.animation(fp ? RedHulkView.FP : RedHulkView.fileOf(move), move.clip());
         if (clip != null) {
            double ticks = sampleTicks(st, player, s, partial);
            double seconds = move.loops() ? (ticks / 20.0) % clip.lengthSeconds() : Math.min(ticks / 20.0, clip.lengthSeconds());
            boolean quick = move.isCombo() || move == RedHulkMove.GRAB || move == RedHulkMove.STAGGER || move == RedHulkMove.LANDING;
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
