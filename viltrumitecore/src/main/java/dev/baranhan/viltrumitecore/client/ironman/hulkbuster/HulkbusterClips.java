package dev.baranhan.viltrumitecore.client.ironman.hulkbuster;

import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkMove;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkStrikes;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterMotion;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterRules;
import javax.annotation.Nullable;

/**
 * Which Mark 48 clips play and where (rework spec §3, pure; the third-person body and the first-person arms
 * read the same answer on the same clock). Three layers over the idle loop: the gait (walk / run / ram by the
 * vanilla walk distance, so the feet stay on the stride), the motion state (dash, jet, hover, dive, landing,
 * stagger, fall) and the move (one clip per step or per phase of the split moves, HulkMove.clip). Clip names
 * and the clip time of a move come from HulkMove only, so the server phases and the clips cannot drift.
 */
public final class HulkbusterClips {
   public static final String IDLE = "idle";
   public static final String WALK = "walk";
   public static final String RUN = "run";
   public static final String RAM = "ram";
   public static final String FALL = "fall";
   /** Vanilla walk-animation units per walk cycle (two strides of ~1.45 blocks at ~4 units per block). */
   public static final double STRIDE_UNITS = 11.6;
   public static final double WALK_SECONDS = 1.6;
   public static final double RUN_SECONDS = 1.2;
   public static final double RAM_SECONDS = 0.8;
   /** Ticks a motion clip takes to fade in (a jump from the gait into a dash or a landing). */
   static final double MOTION_FADE = 3.0;

   /**
    * @param moveTicks   rendered ticks since the current step began (ActionClock.sample of actionElapsed)
    * @param motionTicks rendered ticks since the motion state began
    * @param walkSpeed   vanilla walkAnimation speed (0..~1)
    * @param walkPos     vanilla walkAnimation position
    */
   public record Input(@Nullable HulkMove move, MovePhase phase, int step, double moveTicks, HulkbusterMotion.State motion, double motionTicks,
      int direction, double wallSeconds, float walkSpeed, double walkPos, boolean sprinting) {
   }

   /** One clip at one time with a weight; seconds are already wrapped for loops. */
   public record Layer(String clip, double seconds, float weight) {
   }

   /** Idle base, then the gait over it, the motion clip over that, the move clip on top (null = layer off). */
   public record Pose(Layer idle, @Nullable Layer gait, @Nullable Layer motion, @Nullable Layer move) {
   }

   private HulkbusterClips() {
   }

   public static Pose select(Input in) {
      Layer idle = new Layer(IDLE, wrap(in.wallSeconds(), 5.0), 1.0F);
      return new Pose(idle, gait(in), motion(in), move(in));
   }

   @Nullable
   static Layer gait(Input in) {
      if (in.motion() == HulkbusterMotion.State.RAM) {
         return new Layer(RAM, wrap(in.walkPos() / STRIDE_UNITS * RAM_SECONDS, RAM_SECONDS), 1.0F);
      }

      float weight = Math.min(1.0F, in.walkSpeed() * 1.6F);
      if (weight <= 0.02F) {
         return null;
      }

      boolean run = in.sprinting();
      double length = run ? RUN_SECONDS : WALK_SECONDS;
      return new Layer(run ? RUN : WALK, wrap(in.walkPos() / STRIDE_UNITS * length, length), weight);
   }

   @Nullable
   static Layer motion(Input in) {
      double t = Math.max(0.0, in.motionTicks());
      float fade = (float)Math.min(1.0, (t + 1.0) / MOTION_FADE);
      return switch (in.motion()) {
         case DASH_WINDUP -> new Layer(dashClip(in.direction()), t / 20.0, fade);
         // DASH continues the same clip after the wind-up: no fade.
         case DASH -> new Layer(dashClip(in.direction()), (HulkbusterRules.DASH_WINDUP + t) / 20.0, 1.0F);
         case JET_CHARGE -> new Layer("jet_charge", Math.min(t, HulkbusterRules.JET_CHARGE_MAX) / 20.0, fade);
         case JET_RISE -> new Layer("jet_rise", t / 20.0, fade);
         case HOVER -> new Layer("hover", wrap(t / 20.0, 1.0), fade);
         case DIVE -> new Layer("dive", t / 20.0, fade);
         case LANDING -> new Layer("land", t / 20.0, 1.0F);
         case STAGGER -> new Layer("stagger", t / 20.0, fade);
         case FALL -> new Layer(FALL, wrap(t / 20.0, 1.0), fade);
         default -> null;
      };
   }

   @Nullable
   static Layer move(Input in) {
      HulkMove move = in.move();
      if (move == null) {
         return null;
      }

      int phaseElapsed = (int)Math.floor(Math.max(0.0, in.moveTicks() - StagedTimeline.phaseStart(move, in.phase(), in.step())));
      double fraction = in.moveTicks() - Math.floor(in.moveTicks());
      String clip = move.clip(in.step(), in.phase());
      double ticks = move.clipTicks(in.step(), in.phase(), phaseElapsed) + fraction;
      if (HulkMove.loops(clip)) {
         return new Layer(clip, wrap(ticks / 20.0, loopSeconds(clip)), 1.0F);
      }

      int length = move.clipLength(in.step(), in.phase());
      return new Layer(clip, Math.min(ticks, length) / 20.0, 1.0F);
   }

   public static String dashClip(int direction) {
      return switch (direction & 3) {
         case 1 -> "dash_left";
         case 2 -> "dash_back";
         case 3 -> "dash_right";
         default -> "dash_forward";
      };
   }

   /** Authored length of the loop clips (mark48.animation.json, HulkbusterClipsTest). */
   static double loopSeconds(String clip) {
      return switch (clip) {
         case "hero_fall" -> 0.6;
         case "grab_hold" -> 2.0;
         default -> 1.0;
      };
   }

   static double wrap(double seconds, double length) {
      if (length <= 0.0) {
         return 0.0;
      }

      double t = seconds % length;
      return t < 0.0 ? t + length : t;
   }

   /**
    * First-person thrust of the striking arm, model px along the forearm (HulkStrikes.fpPush): drawn back
    * through the wind-up, the full push on the contact, eased out over the recovery. 0 outside strikes.
    *
    * @param right true for the right camera arm
    */
   public static float firstPersonPush(@Nullable HulkMove move, MovePhase phase, int step, double moveTicks, boolean right) {
      HulkStrikes.Strike strike = move == null ? null : HulkStrikes.of(move, step);
      if (strike == null) {
         return 0.0F;
      }

      boolean arm = switch (strike.arm()) {
         case RIGHT -> right;
         case LEFT -> !right;
         case BOTH -> true;
      };
      if (!arm) {
         return 0.0F;
      }

      int intro = move.ticks(MovePhase.INTRO, step);
      int finish = move.ticks(MovePhase.FINISH, step);
      double t = Math.max(0.0, moveTicks - StagedTimeline.phaseStart(move, phase, step));
      float push = strike.fpPush();
      if (phase == MovePhase.INTRO) {
         double f = Math.min(1.0, t / Math.max(1, intro));
         // Wind-up: the arm draws back (ease-out), then snaps forward over the last fifth (ease-in into the contact).
         if (f < 0.8) {
            return (float)(-0.35 * push * Math.sin(f / 0.8 * Math.PI / 2.0));
         }

         double k = (f - 0.8) / 0.2;
         return (float)(-0.35 * push + 1.35 * push * k * k * k);
      }

      if (phase == MovePhase.MAIN) {
         return push;
      }

      double f = Math.min(1.0, t / Math.max(1, finish));
      double out = 1.0 - Math.pow(1.0 - f, 3.0);
      return (float)(push * (1.0 - out));
   }
}
