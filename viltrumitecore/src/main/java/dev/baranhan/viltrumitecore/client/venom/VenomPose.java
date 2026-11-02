package dev.baranhan.viltrumitecore.client.venom;

import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.pose.ActionClock;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.venom.VenomFlags;
import dev.baranhan.viltrumitecore.hero.venom.VenomMove;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Venom's pose, layered like the Red Hulk's: stance weights (calm / combat / rage idles, a prowling walk, a
 * four-limbed-looking sprint, crouch, rising and falling, swim, and the wall stances: climbing, hanging, running along
 * a facade) smoothed in real time; the face layer (breathing jaw, snarl, rage maw, pain); jump / landing / hurt
 * overlays; the server-clocked move clip on top; then the procedural life: the tongue lashes on its six bones, the
 * chest and shoulders swell with the symbiote's pulse and a hit boils the surface (the reforming wobble). The same
 * code poses the boss (any LivingEntity: the caller hands in the move and its ticks). Client only.
 */
public final class VenomPose {
   static final String[] STANCES = {"idle", "idle_combat", "idle_rage", "walk", "run", "crouch", "air_up", "air_down", "swim", "wall_climb",
      "wall_hang", "wall_run"};
   static final int IDLE = 0, IDLE_COMBAT = 1, IDLE_RAGE = 2, WALK = 3, RUN = 4, CROUCH = 5, AIR_UP = 6, AIR_DOWN = 7, SWIM = 8, WALL_CLIMB = 9,
      WALL_HANG = 10, WALL_RUN = 11;
   static final String[] FACES = {"face_idle", "face_snarl", "face_rage", "face_hurt"};
   static final int F_IDLE = 0, F_SNARL = 1, F_RAGE = 2, F_HURT = 3;
   static final String[] TONGUE = {"tongue_0", "tongue_1", "tongue_2", "tongue_3", "tongue_4", "tongue_5"};
   static final float STANCE_RATE = 10.0F;
   static final float FACE_RATE = 7.0F;
   static final double GAIT_CYCLE = 9.4315;
   static final double RELEASE = 0.14;
   static final double SKILL_RELEASE = 0.3;
   static final double JUMP_SECONDS = 0.28;
   static final double LAND_SECONDS = 0.45;

   public static final class State {
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
      /** Last hurtTime seen: a new hit starts the boil. */
      int lastHurt;
      long boilStart = -1;
      float boilPower;
   }

   private static final Map<LivingEntity, State> THIRD = new WeakHashMap<>();
   private static final Map<LivingEntity, State> FIRST = new WeakHashMap<>();

   private VenomPose() {
   }

   public static void reset(LivingEntity e) {
      THIRD.remove(e);
      FIRST.remove(e);
   }

   static State third(LivingEntity e) {
      return THIRD.computeIfAbsent(e, k -> new State());
   }

   static State first(LivingEntity e) {
      return FIRST.computeIfAbsent(e, k -> new State());
   }

   /** Server-clock ticks of a player's current move (both views, the tendrils and the transformation share it). */
   public static double actionTicks(Player player, HeroPublicSnapshot s, float partial) {
      if (s.actionId() < 0) {
         return 0;
      }
      double client = player.tickCount + (Float.isFinite(partial) ? partial : 0);
      return third(player).clock.sample(s.actionId(), s.variant(), s.actionElapsed(), s.actionLength(), client);
   }

   /** Poses a body. {@code move}/{@code ticks}: the server-clocked move (null = none); {@code wall}: clinging now. */
   public static void pose(BakedGeoModel model, State st, LivingEntity e, int flags, @Nullable VenomMove move, double ticks, float partial,
      boolean wall, boolean fp) {
      long now = Util.getNanos();
      float dt = st.last == 0 ? 1.0F : Mth.clamp((now - st.last) / 1.0E9F, 0.0F, 0.25F);
      st.last = now;
      stanceTargets(st, e, flags, move, partial, dt, wall, fp, now);
      double seconds = (e.tickCount + partial) / 20.0;
      double gait = e.walkAnimation.position(partial) / GAIT_CYCLE;
      double climb = (e.getY() + 0.0) / 1.6;
      blend(model, st, fp ? VenomView.FP : VenomView.STANCES, seconds, gait, climb);
      if (!fp) {
         face(model, st, e, flags, seconds, dt);
         bodyOverlays(model, st, e, partial, now);
      }
      moveClip(model, st, move, ticks, now, fp);
      if (!fp) {
         life(model, st, e, flags, seconds, now);
      }
   }

   /** Static pose for the GUI previews: the prowling idle with the snarl, the tongue out. */
   public static void preview(BakedGeoModel body, double seconds) {
      body.resetBones();
      Animation idle = AnimCache.animation(VenomView.STANCES, "idle_combat");
      if (idle != null) {
         AnimationController.overlay(idle, body, seconds % idle.lengthSeconds(), 1.0F);
      }
      Animation face = AnimCache.animation(VenomView.FACE, "face_snarl");
      if (face != null) {
         AnimationController.overlay(face, body, seconds % face.lengthSeconds(), 1.0F);
      }
      tongue(body, seconds, 1.0F);
   }

   public static boolean combat(int flags, @Nullable VenomMove move) {
      return VenomFlags.has(flags, VenomFlags.IN_COMBAT) || VenomFlags.has(flags, VenomFlags.HOLDING) || move != null;
   }

   private static void stanceTargets(State st, LivingEntity e, int flags, @Nullable VenomMove move, float partial, float dt, boolean wall, boolean fp,
      long now) {
      float[] target = new float[STANCES.length];
      boolean rage = VenomFlags.has(flags, VenomFlags.RAGE);
      boolean combat = combat(flags, move);
      float speed = Math.min(1.0F, e.walkAnimation.speed(partial) * 1.6F);
      boolean flying = e instanceof Player p && p.getAbilities().flying;
      boolean swim = e.isVisuallySwimming() || e.isFallFlying();
      boolean air = !e.onGround() && !e.isPassenger() && !e.isInWater() && !flying && !wall;
      double vy = e.getDeltaMovement().y;
      if (air && !st.wasAir && vy > 0.05) {
         st.jumpStart = now;
      }
      if (!air && st.wasAir && st.init && !wall) {
         st.landStart = now;
         st.landWeight = (float)Mth.clamp(-st.fallSpeed / 0.9, 0.35, 1.0);
      }
      if (air) {
         st.fallSpeed = vy;
      }
      st.wasAir = air;
      int idle = rage ? IDLE_RAGE : combat ? IDLE_COMBAT : IDLE;
      double horizontal = e.getDeltaMovement().horizontalDistance();
      if (fp) {
         target[e.isSprinting() && speed > 0.2F && !air ? RUN : combat || rage ? IDLE_COMBAT : IDLE] = 1;
      } else if (wall) {
         if (Math.abs(vy) > 0.06) {
            target[WALL_CLIMB] = 1;
         } else if (horizontal > 0.12) {
            target[WALL_RUN] = 1;
         } else {
            target[WALL_HANG] = 1;
         }
      } else if (swim) {
         target[SWIM] = 1;
      } else if (air) {
         float down = (float)Mth.clamp(0.5 - vy * 2.5, 0.0, 1.0);
         target[AIR_UP] = 1 - down;
         target[AIR_DOWN] = down;
      } else if (e.isCrouching()) {
         target[CROUCH] = 1;
      } else {
         target[idle] = 1 - speed;
         target[e.isSprinting() ? RUN : WALK] = speed;
      }
      float k = st.init ? 1.0F - (float)Math.exp(-STANCE_RATE * dt) : 1.0F;
      st.init = true;
      for (int i = 0; i < target.length; i++) {
         st.w[i] += (target[i] - st.w[i]) * k;
      }
   }

   private static void blend(BakedGeoModel model, State st, ResourceLocation file, double seconds, double gait, double climb) {
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
         double t;
         if (i == WALK || i == RUN || i == SWIM || i == WALL_RUN) {
            t = frac(gait) * clip.lengthSeconds();
         } else if (i == WALL_CLIMB) {
            t = frac(climb) * clip.lengthSeconds();
         } else {
            t = seconds % Math.max(0.05, clip.lengthSeconds());
         }
         sum += w;
         AnimationController.overlay(clip, model, t, w / sum);
      }
   }

   /** Pure: the face the state calls for (index into {@link #FACES}). */
   static int faceFor(boolean rage, boolean combat, boolean hurt) {
      if (hurt) {
         return F_HURT;
      }
      if (rage) {
         return F_RAGE;
      }
      return combat ? F_SNARL : F_IDLE;
   }

   private static void face(BakedGeoModel model, State st, LivingEntity e, int flags, double seconds, float dt) {
      int want = faceFor(VenomFlags.has(flags, VenomFlags.RAGE), combat(flags, null), e.hurtTime > 6);
      float k = 1.0F - (float)Math.exp(-(want == F_HURT ? FACE_RATE * 3 : FACE_RATE) * dt);
      float sum = 0;
      for (int i = 0; i < FACES.length; i++) {
         st.face[i] += ((i == want ? 1 : 0) - st.face[i]) * k;
         float w = st.face[i];
         if (w < 0.01F) {
            continue;
         }
         Animation clip = AnimCache.animation(VenomView.FACE, FACES[i]);
         if (clip == null) {
            continue;
         }
         sum += w;
         AnimationController.overlay(clip, model, seconds % Math.max(0.05, clip.lengthSeconds()), w / sum);
      }
   }

   private static void bodyOverlays(BakedGeoModel model, State st, LivingEntity e, float partial, long now) {
      if (st.jumpStart >= 0) {
         double t = (now - st.jumpStart) / 1.0E9;
         Animation jump = AnimCache.animation(VenomView.STANCES, "jump");
         if (jump == null || t > JUMP_SECONDS) {
            st.jumpStart = -1;
         } else {
            AnimationController.overlay(jump, model, Math.min(t, jump.lengthSeconds()), (float)smooth(1.0 - t / JUMP_SECONDS) * 0.9F);
         }
      }
      if (st.landStart >= 0) {
         double t = (now - st.landStart) / 1.0E9;
         Animation land = AnimCache.animation(VenomView.STANCES, "land");
         if (land == null || t > LAND_SECONDS) {
            st.landStart = -1;
         } else {
            AnimationController.overlay(land, model, Math.min(t, land.lengthSeconds()), (float)smooth(1.0 - t / LAND_SECONDS) * st.landWeight);
         }
      }
      if (e.hurtTime > 0) {
         Animation hurt = AnimCache.animation(VenomView.STANCES, "hurt");
         if (hurt != null) {
            double t = (10 - e.hurtTime + partial) / 20.0;
            AnimationController.overlay(hurt, model, Math.min(t, hurt.lengthSeconds()), (float)smooth(e.hurtTime / 10.0) * 0.8F);
         }
      }
   }

   /** The move's clip: first person from the arms file, otherwise from whichever move file holds it. */
   @Nullable
   static Animation moveAnimation(VenomMove move, boolean fp) {
      if (fp) {
         return AnimCache.animation(VenomView.FP, move.clip());
      }
      for (ResourceLocation file : VenomView.MOVE_FILES) {
         Animation clip = AnimCache.animation(file, move.clip());
         if (clip != null) {
            return clip;
         }
      }
      return null;
   }

   private static void moveClip(BakedGeoModel model, State st, @Nullable VenomMove move, double ticks, long now, boolean fp) {
      Animation clip = move == null ? null : moveAnimation(move, fp);
      if (clip != null) {
         double seconds = move.loops() ? (ticks / 20.0) % clip.lengthSeconds() : Math.min(ticks / 20.0, clip.lengthSeconds());
         boolean quick = move.isClaw() || move.isLash() || move == VenomMove.GRAB || move == VenomMove.TENDRIL_SHOT || move == VenomMove.REFORM;
         float w = quick ? 1.0F : (float)smooth(Math.min(1.0, ticks / 2.0));
         AnimationController.overlay(clip, model, seconds, w);
         st.lastClip = clip;
         st.lastSeconds = seconds;
         st.lastRelease = quick ? RELEASE : SKILL_RELEASE;
         st.releaseStart = -1;
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

   /**
    * The procedural layer: the tongue (wilder with the jaw open), the breathing swell of chest and shoulders with the
    * pulse, and the boil after a hit: the struck body bulges and ripples, then the symbiote pulls itself back together.
    */
   private static void life(BakedGeoModel model, State st, LivingEntity e, int flags, double seconds, long now) {
      boolean rage = VenomFlags.has(flags, VenomFlags.RAGE);
      GeoBone jaw = model.getBone("jaw");
      float open = jaw == null ? 0 : Mth.clamp(Math.abs(jaw.rotX - jaw.initRotX) / 0.5F, 0.0F, 1.0F); // the rest pose is already a grin
      tongue(model, seconds, 0.35F + 0.65F * open);
      float swell = VenomView.swell(seconds, rage);
      scale(model.getBone("chest"), 1 + swell * 1.2F, 1 + swell * 0.5F, 1 + swell * 1.6F);
      scale(model.getBone("left_arm"), 1 + swell, 1, 1 + swell);
      scale(model.getBone("right_arm"), 1 + swell, 1, 1 + swell);
      if (e.hurtTime > st.lastHurt && e.hurtTime >= 8) {
         st.boilStart = now;
         st.boilPower = 1.0F;
      }
      st.lastHurt = e.hurtTime;
      if (st.boilStart >= 0) {
         double t = (now - st.boilStart) / 1.0E9;
         if (t > 0.7) {
            st.boilStart = -1;
         } else {
            float a = (float)((1.0 - t / 0.7) * st.boilPower);
            float wob = (float)Math.sin(t * 38.0) * 0.09F * a;
            scale(model.getBone("torso"), 1 + wob, 1 - wob * 0.6F, 1 - wob);
            scale(model.getBone("head"), 1 - wob * 0.7F, 1 + wob * 0.5F, 1 + wob * 0.8F);
            scale(model.getBone("left_forearm"), 1 + wob * 1.2F, 1, 1 - wob);
            scale(model.getBone("right_forearm"), 1 - wob, 1, 1 + wob * 1.2F);
         }
      }
   }

   /** The tongue's lash: a travelling wave down the six bones (amplitude 0-1). */
   static void tongue(BakedGeoModel model, double seconds, float amp) {
      for (int i = 1; i < TONGUE.length; i++) {
         GeoBone b = model.getBone(TONGUE[i]);
         if (b == null) {
            continue;
         }
         double phase = seconds * 5.2 - i * 0.85;
         b.rotY += (float)Math.toRadians(Math.sin(phase) * (6 + 5 * i) * amp);
         b.rotX += (float)Math.toRadians((Math.sin(phase * 0.7 + 1.3) * (4 + 3 * i) - 2 * i) * amp); // rotX > 0 lifts the tip: a slight droop
      }
   }

   static void scale(@Nullable GeoBone bone, float x, float y, float z) {
      if (bone != null) {
         bone.scaleX *= x;
         bone.scaleY *= y;
         bone.scaleZ *= z;
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
