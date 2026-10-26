package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.melee.MeleeMoves;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Clip names of Reinhard's action overlays (stage 3+), written by
 * {@code tools/assets/make_reinhard_clips.py} into {@code reinhard.animation.json}
 * ({@code ReinhardClipsTest} checks both agree), and the pure choice of the clip
 * for a synced action (ability, phase, elapsed ticks). No Minecraft calls.
 */
public final class ReinhardClips {
   public static final String LIGHT_PUNCH = "light_punch";
   /** Ordinary fists outside Knight: right jab, left jab, right hook (MeleeMoves.FIST). */
   public static final String LIGHT_PUNCH_LEFT = "light_punch_left";
   public static final String LIGHT_HOOK = "light_hook";
   /** Sword Drawn state: standing stance (body/legs), its own walk and run, both under the two-hand guard. */
   public static final String DRAWN_STANCE = "drawn_stance";
   public static final String DRAWN_WALK = "drawn_walk";
   public static final String DRAWN_RUN = "drawn_run";
   /** Ground/air transitions: the tuck of a Cloud Step and the landing absorption. */
   public static final String AIR_STEP = "air_step";
   public static final String LAND = "land";
   public static final String STEP_DASH = "step_dash";
   public static final String CATCH_READY = "catch_ready";
   public static final String CATCH = "catch";
   public static final String COMBO_1 = "combo_1";
   public static final String COMBO_2 = "combo_2";
   public static final String COMBO_3 = "combo_3";
   public static final String KICK_TOSS = "kick_toss";
   public static final String KICK = "kick";
   public static final String GRAB_HOLD = "grab_hold";
   public static final String THROW = "throw";
   public static final String DASH_BEHIND = "dash_behind";
   public static final String STOMP = "stomp";
   public static final String REFLECT_READY = "reflect_ready";
   public static final String REFLECT = "reflect";
   /** Stage 4: Reid's 6 s draw and 1 s sheathe (driven by the Reid phase, not the action timeline). */
   public static final String REID_DRAW = "reid_draw";
   public static final String REID_SHEATHE = "reid_sheathe";
   /** Lethal Chop (the kit chop, synced chop ticks): the sweep of each hand. */
   public static final String CHOP_LEFT = "chop_left";
   public static final String CHOP_RIGHT = "chop_right";
   /** Reid in the right hand: loop over the Holy Sword stance. */
   public static final String IDLE_HOLY_DRAWN = "idle_holy_drawn";
   /** Sheath Strike: the sword, still in its sheath, swung in a flat arc. */
   public static final String SHEATH_STRIKE = "sheath_strike";
   /** Saw Sphere: 13 s airborne spin, a swing every 4 ticks (one disc each) and the big final swing. */
   public static final String SAW_SPHERE = "saw_sphere";
   /** Normal World Cut from the right shoulder (right to left); its mirror comes from the left shoulder. */
   public static final String WORLD_CUT = "world_cut";
   public static final String WORLD_CUT_FROM_LEFT = "world_cut_from_left";
   public static final String WORLD_ERASURE = "world_erasure";
   /** Phase 1 of a window (catch / reflect) and of the grab = the snap / the throw. */
   public static final int PHASE_SNAP = dev.baranhan.viltrumitecore.hero.reinhard.ActionTimeline.PHASE_SNAP;

   private ReinhardClips() {
   }

   /** Clip of the normal World Cut for a {@link dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutSweep}. */
   public static String worldCut(int sweep) {
      return dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutSweep.ofPhase(sweep)
         == dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutSweep.FROM_LEFT ? WORLD_CUT_FROM_LEFT : WORLD_CUT;
   }

   /** Every clip of an ability (each one must exist in the animation file); empty for later stages. */
   public static List<String> names(ReinhardAbility ability) {
      return switch (ability) {
         case REID_STRIKE -> Arrays.stream(MeleeMoves.REID).map(MeleeMoves.Move::clip).toList();
         case LIGHT_PUNCH -> Arrays.stream(MeleeMoves.FIST).map(MeleeMoves.Move::clip).toList();
         case STEP_DASH -> List.of(STEP_DASH);
         case CATCH -> List.of(CATCH_READY, CATCH);
         case FIST_COMBO -> List.of(COMBO_1, COMBO_2, COMBO_3);
         case SKY_KICK -> List.of(KICK_TOSS, KICK);
         case GRAB_THROW -> List.of(GRAB_HOLD, THROW);
         case DASH_BEHIND -> List.of(DASH_BEHIND);
         case STOMP -> List.of(STOMP);
         case REFLECT -> List.of(REFLECT_READY, REFLECT);
         case REID -> List.of(REID_DRAW, REID_SHEATHE, IDLE_HOLY_DRAWN);
         case SHEATH_STRIKE -> List.of(SHEATH_STRIKE);
         case SAW_SPHERE -> List.of(SAW_SPHERE);
         case WORLD_CUT -> List.of(WORLD_CUT, WORLD_CUT_FROM_LEFT);
         case DIAGONAL_WORLD_CUT -> List.of("diagonal_world_cut");
         case WORLD_ERASURE -> List.of(WORLD_ERASURE);
         default -> List.of();
      };
   }

   /** Overlay clip and its time for the synced action; null = no overlay. {@code ticks} = elapsed + partial tick. */
   @Nullable
   public static ReinhardPoses.Clip clip(ReinhardAbility ability, int phase, double ticks) {
      if (ability == null) return null;
      double seconds = ticks / 20.0;
      return switch (ability) {
         // The synced phase is the move index: the clip of that row, on the strike's own clock.
         case REID_STRIKE -> new ReinhardPoses.Clip(MeleeMoves.reid(phase).clip(), seconds);
         case LIGHT_PUNCH -> new ReinhardPoses.Clip(MeleeMoves.fist(phase).clip(), seconds);
         case CATCH -> new ReinhardPoses.Clip(phase == PHASE_SNAP && ticks < ReinhardRules.CATCH_SNAP_TICKS ? CATCH : CATCH_READY, seconds);
         case REFLECT -> new ReinhardPoses.Clip(phase == PHASE_SNAP && ticks < ReinhardRules.REFLECT_SNAP_TICKS ? REFLECT : REFLECT_READY, seconds);
         case GRAB_THROW -> new ReinhardPoses.Clip(phase == PHASE_SNAP ? THROW : GRAB_HOLD, seconds);
         case FIST_COMBO -> new ReinhardPoses.Clip(phase <= 0 ? COMBO_1 : phase == 1 ? COMBO_2 : COMBO_3, seconds);
         // The kick clip starts at the contact tick; before it the toss plays.
         case SKY_KICK -> ticks < ReinhardRules.KICK_TOSS_TICKS ? new ReinhardPoses.Clip(KICK_TOSS, seconds)
            : new ReinhardPoses.Clip(KICK, (ticks - ReinhardRules.KICK_TOSS_TICKS) / 20.0);
         // The synced phase is the WorldCutSweep: the shoulder the cut comes down from.
         case WORLD_CUT -> new ReinhardPoses.Clip(worldCut(phase), seconds);
         default -> {
            List<String> names = names(ability);
            yield names.isEmpty() ? null : new ReinhardPoses.Clip(names.get(0), seconds);
         }
      };
   }
}
