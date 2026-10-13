package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardFlags;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardMode;
import javax.annotation.Nullable;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Pure pose selection for Reinhard's model: stance by mode, walk blend, action
 * overlay, body state and render passes. No Minecraft client calls (unit tested).
 */
public final class ReinhardPoses {
   /** reinhard_walk.json referenceAmount: the clip's swing equals vanilla's at this limb swing amount. */
   public static final float WALK_REFERENCE_AMOUNT = 0.55F;
   /**
    * Clip seconds per unit of vanilla walk position: the 1 s clip is vanilla's limb swing
    * (cos(position * 0.6662)), so one swing period 2 pi / 0.6662 = one clip length.
    */
   public static final double WALK_SECONDS_PER_BLOCK = 1.0 * 0.6662 / (2.0 * Math.PI);
   private static final ReinhardAbility[] ABILITIES = ReinhardAbility.values();

   private ReinhardPoses() {
   }

   public static Pose pose(Input in) {
      float walk = Math.max(0.0F, Math.min(1.0F, in.limbSwingAmount() / WALK_REFERENCE_AMOUNT));
      return new Pose(stance(in.mode()), in.realSeconds(), walk, in.walkDistance() * WALK_SECONDS_PER_BLOCK, overlay(in), body(in.vanillaPose(), in.passenger()),
         0.0F);
   }

   /** Stance clip of a mode (all six bones). */
   public static String stance(ReinhardMode mode) {
      return switch (mode) {
         case RESTRAINED -> "idle_restrained";
         case KNIGHT -> "idle_knight";
         case HOLY_SWORD -> "idle_holy";
      };
   }

   /**
    * Action overlay: the synced action is a {@link ReinhardAbility} ordinal (−1 = none), its phase is
    * {@code variant}; {@link ReinhardClips} picks the clip. Time = elapsed + partial tick, clamped to the
    * action length. Missing clips are skipped by the renderer.
    */
   @Nullable
   static Clip overlay(Input in) {
      if (in.actionId() < 0 || in.actionId() >= ABILITIES.length) {
         return null;
      }

      double ticks = in.actionElapsed() + in.partialTick() - in.heldTicks();
      if (in.actionLength() > 0) {
         ticks = Math.min(ticks, in.actionLength());
      }

      return ReinhardClips.clip(ABILITIES[in.actionId()], in.actionPhase(), Math.max(0.0, ticks));
   }

   /** Clip seconds per walk-position unit of the 1 s swim stroke: vanilla's stroke repeats every 26 units (HumanoidModel). */
   public static final double SWIM_SECONDS_PER_UNIT = 1.0 / 26.0;

   /** Kit chop length (PlayerEntityCoreMixin counts 20 → 0). */
   public static final int CHOP_TICKS = 20;

   /** Stage 4, Lethal Chop: the chop overlay from the synced chop ticks (20 → 0) and side; null when no chop runs. */
   @Nullable
   public static Clip chopClip(int chopTicks, boolean left, float partialTick) {
      if (chopTicks <= 0) {
         return null;
      }

      double seconds = Math.max(0.0, Math.min(1.0, (CHOP_TICKS - (chopTicks - partialTick)) / (double)CHOP_TICKS));
      return new Clip(left ? ReinhardClips.CHOP_LEFT : ReinhardClips.CHOP_RIGHT, seconds);
   }

   /** Stage 4: Reid's draw / sheathe / drawn-idle overlay at {@code ticks} into the phase; null while sheathed. */
   @Nullable
   public static Clip reidClip(ReidPhase phase, double ticks) {
      return switch (phase) {
         case DRAWING -> new Clip(ReinhardClips.REID_DRAW, Math.max(0.0, ticks) / 20.0);
         case SHEATHING -> new Clip(ReinhardClips.REID_SHEATHE, Math.max(0.0, ticks) / 20.0);
         case DRAWN -> new Clip(ReinhardClips.IDLE_HOLY_DRAWN, Math.max(0.0, ticks) / 20.0);
         default -> null;
      };
   }

   /**
    * How far the blade has left the sheath along its axis (0 in, 1 out): eased over the 6 s draw,
    * reversed over the 1 s sheathe. Drawn = 1 (stage 4 Task 5 moves it to the hand).
    */
   public static float bladeOut(ReidPhase phase, double ticks) {
      return switch (phase) {
         case SHEATHED -> 0.0F;
         case DRAWN -> 1.0F;
         case DRAWING -> smooth(ticks / dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules.REID_DRAW_TICKS);
         case SHEATHING -> 1.0F - smooth(ticks / dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules.REID_SHEATHE_TICKS);
      };
   }

   private static float smooth(double x) {
      double k = Math.max(0.0, Math.min(1.0, x));
      return (float)(k * k * (3.0 - 2.0 * k));
   }

   static BodyState body(net.minecraft.world.entity.Pose pose, boolean passenger) {
      if (passenger) {
         return BodyState.SIT;
      }

      return switch (pose) {
         case CROUCHING -> BodyState.CROUCH;
         case SLEEPING -> BodyState.SLEEP;
         case SWIMMING -> BodyState.SWIM;
         case FALL_FLYING -> BodyState.FALL_FLY;
         default -> BodyState.STAND;
      };
   }

   /** PlayerRenderer.setupRotations swim pitch (degrees): lerp(swimAmount, 0, inWater ? -90 - xRot : -90). */
   public static float swimPitch(float swimAmount, boolean inWater, float xRot) {
      float target = inWater ? -90.0F - xRot : -90.0F;
      return swimAmount * target;
   }

   /** PlayerRenderer.setupRotations elytra pitch (degrees): clamp(t^2 / 100, 0, 1) * (-90 - xRot); none in a riptide spin. */
   public static float elytraPitch(float fallFlyTicks, float xRot, boolean autoSpin) {
      if (autoSpin) {
         return 0.0F;
      }

      float k = Math.max(0.0F, Math.min(1.0F, fallFlyTicks * fallFlyTicks / 100.0F));
      return k * (-90.0F - xRot);
   }

   /** LivingEntityRenderer.sleepDirectionToRotation (degrees). */
   public static float sleepYaw(net.minecraft.core.Direction bed) {
      return switch (bed) {
         case SOUTH -> 90.0F;
         case NORTH -> 270.0F;
         case EAST -> 180.0F;
         default -> 0.0F;
      };
   }

   /**
    * Render passes. The vanilla body is always cancelled. Visible: everything (a glowing body outlines
    * through the outline buffer source). Invisible but visible to the viewer (spectator, team): translucent
    * body only. Invisible and glowing: only the outline of the model, like vanilla's RenderType.outline.
    */
   public static Passes passes(boolean invisible, boolean visibleToViewer, boolean glowing) {
      if (!invisible) {
         return new Passes(true, false, false, true);
      }

      if (visibleToViewer) {
         return new Passes(true, true, false, false);
      }

      return new Passes(false, false, glowing, false);
   }

   /**
    * First person: Knight keeps the model's left arm behind the back (hidden); Restrained and
    * Holy Sword show both arms at vanilla's rest position (the hilt reach is third person only).
    */
   public static boolean firstPersonArmVisible(ReinhardMode mode, HumanoidArm arm, int flags) {
      return !(mode == ReinhardMode.KNIGHT && arm == HumanoidArm.LEFT);
   }

   /**
    * AlwaysVisibleOffhandMixin term: draw the empty off hand when its arm is visible, never in Knight.
    * Stage 3 adds the action clips that use the off hand. {@code mode} null = not Reinhard.
    */
   public static boolean wantsOffhand(@Nullable ReinhardMode mode, HumanoidArm offArm, int flags) {
      return mode != null && mode != ReinhardMode.KNIGHT && firstPersonArmVisible(mode, offArm, flags);
   }

   /** The hand that holds Reid (right, while drawing, drawn or sheathing) draws no vanilla held item. */
   public static HumanoidArm physicalArm(HumanoidArm main, net.minecraft.world.InteractionHand hand) {
      return hand == net.minecraft.world.InteractionHand.MAIN_HAND ? main : main.getOpposite();
   }

   /** Two-handed cinematic actions hide slot items without altering inventory. */
   public static boolean drawsHeldItem(HumanoidArm arm, ReidPhase phase, @Nullable ReinhardAbility action) {
      return action != ReinhardAbility.WORLD_CUT && drawsHeldItem(arm, phase);
   }

   public static boolean drawsHeldItem(HumanoidArm arm, ReidPhase phase) {
      return arm != HumanoidArm.RIGHT || phase == ReidPhase.SHEATHED;
   }

   public static ReidPhase reidPhase(int flags) {
      if (ReinhardFlags.has(flags, ReinhardFlags.REID_DRAWING)) {
         return ReidPhase.DRAWING;
      }

      if (ReinhardFlags.has(flags, ReinhardFlags.REID_SHEATHING)) {
         return ReidPhase.SHEATHING;
      }

      return ReinhardFlags.has(flags, ReinhardFlags.REID_DRAWN) ? ReidPhase.DRAWN : ReidPhase.SHEATHED;
   }

   public record Input(
      ReinhardMode mode,
      int flags,
      int actionId,
      int actionElapsed,
      int actionLength,
      float partialTick,
      float limbSwingAmount,
      float walkDistance,
      double realSeconds,
      net.minecraft.world.entity.Pose vanillaPose,
      boolean passenger,
      int actionPhase,
      double heldTicks
   ) {
      /** No action phase, no hit-stop (stage 1b/2 callers). */
      public Input(ReinhardMode mode, int flags, int actionId, int actionElapsed, int actionLength, float partialTick, float limbSwingAmount,
         float walkDistance, double realSeconds, net.minecraft.world.entity.Pose vanillaPose, boolean passenger) {
         this(mode, flags, actionId, actionElapsed, actionLength, partialTick, limbSwingAmount, walkDistance, realSeconds, vanillaPose, passenger, 0, 0.0);
      }
   }

   public record Pose(String stance, double stanceSeconds, float walk, double walkSeconds, @Nullable Clip overlay, BodyState body, float aura) {
   }

   public record Clip(String animation, double seconds) {
   }

   public record Passes(boolean body, boolean translucent, boolean outline, boolean extras) {
   }

   public enum BodyState {
      STAND,
      CROUCH,
      SIT,
      SLEEP,
      SWIM,
      FALL_FLY;
   }

   public enum ReidPhase {
      SHEATHED,
      DRAWING,
      DRAWN,
      SHEATHING;
   }
}
