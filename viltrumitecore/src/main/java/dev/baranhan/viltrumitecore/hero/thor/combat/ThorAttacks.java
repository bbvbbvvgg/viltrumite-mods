package dev.baranhan.viltrumitecore.hero.thor.combat;

import static dev.baranhan.viltrumitecore.hero.thor.combat.ThorStrike.*;

import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorRules;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Every timed Thor attack as one data row (rework spec §3 "каждая атака — одна
 * строка данных", §4 timings; pattern: Reinhard's {@code hero/reinhard/melee/MeleeMoves}).
 * A row joins what used to live in separate places:
 * <ul>
 *   <li><b>hitbox</b> — the {@link ThorStrike} of the row (shape, reach, arc, damage, push:
 *   unchanged numbers); rows without a strike hit through their own config areas
 *   ({@link ThorCombatConfig}) or do not hit;</li>
 *   <li><b>clip</b> / <b>fpClip</b> — animation ids in {@code assets/viltrumitecore/animations/thor/thor.animation.json}
 *   (naming scheme below);</li>
 *   <li><b>trail</b> — the swing path the weapon trail draws, inside the hitbox cone;</li>
 *   <li><b>cue</b> — the sound cue the {@code ThorSounds} facade plays (beats windup / contact / tail);</li>
 *   <li><b>vfx</b> — the contact effect id;</li>
 *   <li><b>phases</b> — windup (intro), contact (main), recovery (finish) ticks. The damage lands on
 *   the first contact tick ({@code ENTER_MAIN}, tick {@code windup}); the next press is buffered and
 *   chains from the first recovery tick.</li>
 * </ul>
 *
 * <p>Clip naming scheme (one file, one name per row, first person = the same name + {@value #FP_SUFFIX}):
 * {@code draw}, {@code holster}; {@code hammer_<strike>} for HAMMER_COMBO; {@code fist_<strike>} for
 * FIST_COMBO (the {@code FIST_} prefix of the strike is dropped: FIST_UPPERCUT → {@code fist_uppercut});
 * {@code hammer_strike_<strike>} for the MMB blow (the {@code MMB_} prefix is dropped); {@code electric_mode},
 * {@code electric_discharge_right} / {@code _left}, {@code electric_wave}, {@code electric_storm}; {@code wrath}.
 * Each clip lasts exactly {@code phases.length() / 20} s and carries a {@code "timeline"} instruction
 * {@value #CONTACT_MARKER} at {@code windup / 20} s (ThorClipContractTest).
 *
 * <p>The timeline of a combo move is per step: the step starts with the move's
 * envelope ({@link ThorCombatTimings}) and is retimed to its strike's row when
 * the strike is chosen ({@code StagedTimeline.retime}). Cooldowns and damage are
 * not here and did not change with the rework.
 */
public final class ThorAttacks {
   public static final String FP_SUFFIX = "_fp";
   /** The "timeline" instruction of a clip on the server's damage tick. */
   public static final String CONTACT_MARKER = "contact";
   public static final int TICKS_PER_SECOND = 20;

   /** Ticks of windup (intro), contact (main) and recovery (finish). */
   public record Phases(int windup, int contact, int recovery) {
      public Phases {
         if (windup < 0 || contact < 0 || recovery < 0 || windup + contact + recovery <= 0) {
            throw new IllegalArgumentException("bad phases " + windup + "/" + contact + "/" + recovery);
         }
      }

      public int length() {
         return this.windup + this.contact + this.recovery;
      }

      /** The server's damage tick: the first tick of the contact phase (ENTER_MAIN). */
      public int contactTick() {
         return this.windup;
      }

      /** First tick a buffered press chains into the next step (the recovery). */
      public int chainFrom() {
         return this.windup + this.contact;
      }

      public ThorMoveDefinition definition(@Nullable MovePhase cancellableFrom, int maxSteps) {
         return new ThorMoveDefinition(this.windup, this.contact, this.recovery, cancellableFrom, maxSteps);
      }
   }

   /**
    * Weapon path in degrees relative to the view at contact: yaw + = right, pitch + = up.
    * A ring (spin) may run past ±180.
    */
   public record Trail(float yawFrom, float pitchFrom, float yawTo, float pitchTo) {
      public static final Trail NONE = new Trail(0.0F, 0.0F, 0.0F, 0.0F);

      public boolean none() {
         return this.equals(NONE);
      }
   }

   /**
    * One attack. {@code pose} is the synced combat pose that selects the row
    * (the strike's pose, the electric hand, 0 for single-row moves).
    */
   public record Attack(ThorMove move, int pose, @Nullable ThorStrike strike, String clip, String fpClip, Trail trail, String cue, String vfx,
      Phases phases) {
      public int length() {
         return this.phases.length();
      }
   }

   // Sound cue ids (ThorSounds, R5) and contact VFX ids (R4).
   public static final String CUE_DRAW = "thor.draw";
   public static final String CUE_HOLSTER = "thor.holster";
   public static final String CUE_HAMMER = "thor.hammer_swing";
   public static final String CUE_HAMMER_HEAVY = "thor.hammer_heavy";
   public static final String CUE_HAMMER_SPIN = "thor.hammer_spin";
   public static final String CUE_HAMMER_SLAM = "thor.hammer_slam";
   public static final String CUE_FIST = "thor.fist";
   public static final String CUE_FIST_HEAVY = "thor.fist_heavy";
   public static final String CUE_FIST_SLAM = "thor.fist_slam";
   public static final String CUE_STRIKE = "thor.hammer_strike";
   public static final String CUE_ELECTRIC_MODE = "thor.electric_mode";
   public static final String CUE_DISCHARGE = "thor.discharge";
   public static final String CUE_WAVE = "thor.electric_wave";
   public static final String CUE_STORM = "thor.electric_storm";
   public static final String CUE_WRATH = "thor.wrath";
   public static final String VFX_NONE = "none";
   public static final String VFX_HAMMER = "thor.hammer_hit";
   public static final String VFX_HAMMER_HEAVY = "thor.hammer_heavy_hit";
   public static final String VFX_GROUND = "thor.ground_slam";
   public static final String VFX_FIST = "thor.fist_hit";
   public static final String VFX_FIST_HEAVY = "thor.fist_heavy_hit";
   public static final String VFX_ELECTRIC_MODE = "thor.electric_mode";
   public static final String VFX_DISCHARGE = "thor.discharge";
   public static final String VFX_WAVE = "thor.electric_wave";
   public static final String VFX_STORM = "thor.electric_storm";
   public static final String VFX_WRATH = "thor.wrath_strike";

   /** All rows: technical, Mjölnir combo, fists, MMB blow, electric, ultimate. */
   public static final List<Attack> ALL = List.of(
      // --- technical draw / holster (foundation timings, ThorRules) -----------------------------------------------------------
      //  move / pose / clip                         windup                 contact               recovery
      move(ThorMove.DRAW, 0, "draw", ThorRules.DRAW_INTRO, ThorRules.DRAW_MAIN, ThorRules.DRAW_FINISH, CUE_DRAW, VFX_NONE),
      move(ThorMove.HOLSTER, 0, "holster", ThorRules.HOLSTER_INTRO, ThorRules.HOLSTER_MAIN, ThorRules.HOLSTER_FINISH, CUE_HOLSTER, VFX_NONE),
      // --- Mjölnir in the hand (HAMMER_COMBO): 24–30 ticks a strike ------------------------------------------------------------
      //  strike / clip                               wind con rec   trail: yaw, pitch from → to          cue               vfx
      strike(SWING_RIGHT, "hammer_swing_right", 10, 3, 13, trail(60, -5, -60, -10), CUE_HAMMER, VFX_HAMMER),
      strike(SWING_BACKHAND, "hammer_swing_backhand", 10, 3, 13, trail(-55, 45, 55, -30), CUE_HAMMER, VFX_HAMMER),
      strike(OVERHEAD, "hammer_overhead", 11, 3, 14, trail(0, 80, 0, -45), CUE_HAMMER_HEAVY, VFX_HAMMER),
      strike(SPIN, "hammer_spin", 12, 3, 15, trail(90, 0, -270, -5), CUE_HAMMER_SPIN, VFX_HAMMER_HEAVY),
      strike(UPPERCUT, "hammer_uppercut", 11, 3, 14, trail(10, -50, -5, 70), CUE_HAMMER_HEAVY, VFX_HAMMER),
      strike(RISING, "hammer_rising", 10, 3, 13, trail(35, -40, -30, 50), CUE_HAMMER, VFX_HAMMER),
      strike(GROUND_SLAM, "hammer_ground_slam", 12, 3, 15, trail(0, 85, 0, -70), CUE_HAMMER_SLAM, VFX_GROUND),
      strike(HEAVY_DOWN, "hammer_heavy_down", 11, 3, 14, trail(45, 60, -40, -40), CUE_HAMMER_HEAVY, VFX_HAMMER_HEAVY),
      strike(HEAVY_REVERSE, "hammer_heavy_reverse", 11, 3, 14, trail(-45, 55, 45, -40), CUE_HAMMER_HEAVY, VFX_HAMMER_HEAVY),
      strike(HEAVY_SPIN, "hammer_heavy_spin", 12, 3, 15, trail(100, 0, -260, -10), CUE_HAMMER_SPIN, VFX_HAMMER_HEAVY),
      strike(POMMEL, "hammer_pommel", 9, 3, 12, trail(0, -10, 0, 5), CUE_HAMMER, VFX_HAMMER),
      strike(DOUBLE_SWING, "hammer_double_swing", 12, 3, 15, trail(70, 5, -70, -5), CUE_HAMMER_HEAVY, VFX_HAMMER_HEAVY),
      strike(THRUST, "hammer_thrust", 11, 3, 14, trail(0, 0, 0, -5), CUE_HAMMER_HEAVY, VFX_HAMMER_HEAVY),
      strike(LUNGE, "hammer_lunge", 9, 3, 12, trail(40, 30, -40, -20), CUE_HAMMER, VFX_HAMMER),
      strike(LUNGE_FINISH, "hammer_lunge_finish", 12, 3, 15, trail(0, 75, 0, -50), CUE_HAMMER_HEAVY, VFX_HAMMER_HEAVY),
      strike(FALLING_SLAM, "hammer_falling_slam", 9, 3, 14, trail(0, 85, 0, -80), CUE_HAMMER_SLAM, VFX_GROUND),
      strike(LOW_SWEEP, "hammer_low_sweep", 9, 3, 12, trail(75, -35, -75, -40), CUE_HAMMER, VFX_HAMMER),
      strike(SWEEP_LAUNCH, "hammer_sweep_launch", 11, 3, 14, trail(0, -60, 0, 70), CUE_HAMMER_HEAVY, VFX_HAMMER),
      // --- bare fists (FIST_COMBO): 18–22 ticks a strike ----------------------------------------------------------------------
      strike(JAB, "fist_jab", 7, 2, 9, trail(-10, 0, 0, 0), CUE_FIST, VFX_FIST),
      strike(CROSS, "fist_cross", 7, 2, 9, trail(10, 0, 0, -2), CUE_FIST, VFX_FIST),
      strike(HOOK, "fist_hook", 8, 2, 10, trail(-50, 0, 30, 0), CUE_FIST, VFX_FIST),
      strike(FIST_UPPERCUT, "fist_uppercut", 9, 2, 11, trail(5, -40, 0, 40), CUE_FIST_HEAVY, VFX_FIST_HEAVY),
      strike(HAYMAKER, "fist_haymaker", 9, 2, 11, trail(55, 10, -45, -5), CUE_FIST_HEAVY, VFX_FIST_HEAVY),
      strike(HAMMER_FIST, "fist_hammer_fist", 7, 2, 9, trail(5, 60, 0, -35), CUE_FIST, VFX_FIST),
      strike(HAMMER_FIST_LEFT, "fist_hammer_fist_left", 7, 2, 9, trail(-5, 60, 0, -35), CUE_FIST, VFX_FIST),
      strike(GROUND_POUND, "fist_ground_pound", 9, 2, 11, trail(0, 80, 0, -80), CUE_FIST_SLAM, VFX_GROUND),
      strike(BODY_BLOW, "fist_body_blow", 7, 2, 9, trail(10, -20, 0, -15), CUE_FIST, VFX_FIST),
      strike(ELBOW, "fist_elbow", 7, 2, 9, trail(-40, 0, 20, 0), CUE_FIST, VFX_FIST),
      strike(PUSH_KICK, "fist_push_kick", 8, 2, 10, trail(0, -40, 0, -20), CUE_FIST_HEAVY, VFX_FIST_HEAVY),
      strike(TACKLE, "fist_tackle", 8, 2, 10, Trail.NONE, CUE_FIST_HEAVY, VFX_FIST_HEAVY),
      strike(TACKLE_HOOK, "fist_tackle_hook", 9, 2, 11, trail(50, 0, -40, 0), CUE_FIST_HEAVY, VFX_FIST_HEAVY),
      // --- MMB melee branch (HAMMER_STRIKE): ~30 ticks ------------------------------------------------------------------------
      strike(MMB_BLOW, "hammer_strike_blow", 12, 3, 15, trail(55, 30, -50, -25), CUE_STRIKE, VFX_HAMMER_HEAVY),
      strike(MMB_FINISHER, "hammer_strike_finisher", 12, 3, 15, trail(0, 85, 0, -60), CUE_STRIKE, VFX_GROUND),
      // --- electric mode (timings unchanged by the rework) --------------------------------------------------------------------
      move(ThorMove.ELECTRIC_MODE, 0, "electric_mode", 0, 4, 0, CUE_ELECTRIC_MODE, VFX_ELECTRIC_MODE),
      move(ThorMove.ELECTRIC_COMBO, 1, "electric_discharge_right", 3, 2, 7, CUE_DISCHARGE, VFX_DISCHARGE),
      move(ThorMove.ELECTRIC_COMBO, 2, "electric_discharge_left", 3, 2, 7, CUE_DISCHARGE, VFX_DISCHARGE),
      // The wave's windup is the hold threshold: RMB still down when it ends grows the wave into the storm.
      move(ThorMove.ELECTRIC_WAVE, 0, "electric_wave", 7, 3, 8, CUE_WAVE, VFX_WAVE),
      // The storm's contact is its growth (ends on release, at most this long): the clip covers the longest storm.
      move(ThorMove.ELECTRIC_STORM, 0, "electric_storm", 0, ThorCombatTimings.STORM_MAX_TICKS, 12, CUE_STORM, VFX_STORM),
      // --- ultimate: 6.4 s; Mjölnir raised to the sky, the storm builds, the strike at 3.6 s, a long tail -----------------
      move(ThorMove.WRATH, 0, "wrath", 72, 10, 46, CUE_WRATH, VFX_WRATH));

   /** The row each move's timeline starts with (combo moves are then retimed to their strike). */
   private static final Map<ThorMove, Attack> ENVELOPES = new EnumMap<>(ThorMove.class);
   private static final Map<ThorStrike, Attack> BY_STRIKE = new EnumMap<>(ThorStrike.class);
   private static final Map<ThorMove, Map<Integer, Attack>> BY_POSE = new EnumMap<>(ThorMove.class);
   private static final Map<String, Attack> BY_CLIP = new HashMap<>();

   static {
      for (Attack attack : ALL) {
         ENVELOPES.putIfAbsent(attack.move(), attack);
         if (attack.strike() != null && BY_STRIKE.put(attack.strike(), attack) != null) {
            throw new IllegalStateException("two rows for " + attack.strike());
         }

         if (BY_POSE.computeIfAbsent(attack.move(), m -> new HashMap<>()).put(attack.pose(), attack) != null) {
            throw new IllegalStateException("two rows for " + attack.move() + " pose " + attack.pose());
         }

         if (BY_CLIP.put(attack.clip(), attack) != null || BY_CLIP.put(attack.fpClip(), attack) != null) {
            throw new IllegalStateException("clip id used twice: " + attack.clip());
         }
      }
   }

   private ThorAttacks() {
   }

   private static Trail trail(float yawFrom, float pitchFrom, float yawTo, float pitchTo) {
      return new Trail(yawFrom, pitchFrom, yawTo, pitchTo);
   }

   private static Attack strike(ThorStrike strike, String clip, int windup, int contact, int recovery, Trail trail, String cue, String vfx) {
      return new Attack(strike.move(), strike.pose(), strike, clip, clip + FP_SUFFIX, trail, cue, vfx, new Phases(windup, contact, recovery));
   }

   private static Attack move(ThorMove move, int pose, String clip, int windup, int contact, int recovery, String cue, String vfx) {
      return new Attack(move, pose, null, clip, clip + FP_SUFFIX, Trail.NONE, cue, vfx, new Phases(windup, contact, recovery));
   }

   /** The row of a strike (every ThorStrike has one). */
   public static Attack of(ThorStrike strike) {
      return BY_STRIKE.get(strike);
   }

   /** The row of a move's synced combat pose; unknown poses fall back to the move's envelope; null for moves without rows. */
   @Nullable
   public static Attack of(ThorMove move, int pose) {
      Map<Integer, Attack> poses = BY_POSE.get(move);
      if (poses == null) {
         return null;
      }

      Attack attack = poses.get(pose);
      return attack != null ? attack : ENVELOPES.get(move);
   }

   /** The first row of a move: the phases its timeline starts with. */
   @Nullable
   public static Attack envelope(ThorMove move) {
      return ENVELOPES.get(move);
   }

   @Nullable
   public static Attack byClip(String clip) {
      return BY_CLIP.get(clip);
   }

   /** Every row of a move, in table order. */
   public static List<Attack> rows(ThorMove move) {
      List<Attack> rows = new ArrayList<>();
      for (Attack attack : ALL) {
         if (attack.move() == move) {
            rows.add(attack);
         }
      }

      return Collections.unmodifiableList(rows);
   }
}
