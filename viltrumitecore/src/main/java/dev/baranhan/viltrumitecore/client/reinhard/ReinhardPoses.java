package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardFlags;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardMode;
import java.util.Locale;
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
   private static final HeroAction[] ACTIONS = HeroAction.values();

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

   /** Action overlay: clip named after the synced action (lower case), time clamped to the action length. Missing clips are skipped by the renderer. */
   @Nullable
   static Clip overlay(Input in) {
      if (in.actionId() < 0 || in.actionId() >= ACTIONS.length) {
         return null;
      }

      double seconds = (in.actionElapsed() + in.partialTick()) / 20.0;
      if (in.actionLength() > 0) {
         seconds = Math.min(seconds, in.actionLength() / 20.0);
      }

      return new Clip(ACTIONS[in.actionId()].name().toLowerCase(Locale.ROOT), seconds);
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

   /** The hand that holds Reid (right, while drawing, drawn or sheathing) draws no vanilla held item. */
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
      boolean passenger
   ) {
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
