package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import dev.baranhan.viltrumitecore.hero.combat.InterruptReason;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedMove;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Every Hulkbuster Mark 48 move on the shared {@link dev.baranhan.viltrumitecore.hero.combat.StagedTimeline}
 * (rework spec §6–7, Thor rules §1.3.2): phase lengths per combo step, priority (1 utility … 6 ultimate),
 * the cancel window and hyper armour. One row per move; the clip of step n is {@link #clip(int)} and lasts
 * exactly {@link #length(int)} ticks (HulkbusterClipsTest). Wire ids are stable: never reuse or renumber.
 * Numbers are the spec §12 starting values (tuned after the owner's playtest).
 */
public enum HulkMove implements StagedMove {
   /** LMB: right hook 24 t, left straight 26 t, double hammer from above 36 t (hyper armour in its MAIN). */
   COMBO(1, 2, MovePhase.FINISH, false, new int[][]{{11, 3, 10}, {12, 3, 11}, {18, 4, 14}}, "combo_1", "combo_2", "combo_3"),
   /** Held LMB: the elbow thruster winds up (released early by the button), then the jet-driven punch. */
   CHARGED(2, 3, null, false, new int[][]{{HulkbusterRules.CHARGE_HOLD_MAX, 6, 18}}, "charged"),
   /** RMB: the left arm shoots out (catch window in MAIN). A miss plays GRAB_MISS. */
   GRAB_REACH(3, 3, null, false, new int[][]{{10, 6, 0}}, "grab_reach"),
   GRAB_MISS(4, 3, null, false, new int[][]{{0, 0, 24}}, "grab_miss"),
   /** The victim in the left fist, lifted (INTRO) and held (MAIN). Its length is the 6 s hold cap. */
   GRAB_HOLD(5, 4, MovePhase.MAIN, true, new int[][]{{8, HulkbusterRules.GRAB_HOLD_MAX - 8, 0}}, "grab_hold"),
   /** LMB in the hold: the jackhammer into the face, four escalating steps (18 → 15 t). */
   GRAB_PUNCH(6, 4, null, true, new int[][]{{10, 2, 6}, {9, 2, 6}, {9, 2, 5}, {8, 2, 5}}, "grab_punch_1", "grab_punch_2", "grab_punch_3", "grab_punch_4"),
   /** Fifth punch: wind-up behind the head, the piston fires, the victim flies. */
   GRAB_FINISH(7, 5, null, true, new int[][]{{26, 2, 12}}, "grab_finish"),
   /** RMB in the hold: throw along the look. */
   GRAB_THROW(8, 4, null, true, new int[][]{{12, 2, 14}}, "grab_throw"),
   /** RMB + S in the hold, or a wall right ahead: slam into the ground / the wall. */
   GRAB_SLAM(9, 4, null, true, new int[][]{{14, 2, 16}}, "grab_slam"),
   /** F held: crossed forearms (MAIN holds until the release skips to FINISH). */
   BLOCK(10, 2, MovePhase.FINISH, false, new int[][]{{4, HulkbusterRules.BLOCK_HOLD_MAX, 6}}, "block"),
   /** Perfect block answer: elbow counter with a push. */
   COUNTER(11, 4, null, false, new int[][]{{6, 2, 12}}, "counter"),
   /** Slot 1: both palms forward, 1.5 s charge, the double beam, recoil. */
   HEAVY_REPULSORS(12, 3, null, false, new int[][]{{30, 16, 14}}, "heavy_repulsors"),
   /** Slot 2: both fists into the ground at 1.2 s, a crack runs forward. */
   SEISMIC_RIFT(13, 3, null, false, new int[][]{{24, 4, 22}}, "seismic_rift"),
   /** Slot 3: mark the spot for Veronica's cage (the cage itself is a world effect). */
   VERONICA_CAGE(14, 2, MovePhase.FINISH, false, new int[][]{{8, 4, 12}}, "veronica_cage"),
   /** Slot 4: the broken right arm drops, a new one docks (vulnerable). */
   SPARE_ARM(15, 2, null, false, new int[][]{{20, 20, 10}}, "spare_arm"),
   /** Ultimate: jet ram. INTRO thrusters open, MAIN dash + candle + dive (contact at its end), FINISH the long tail. */
   JET_RAM(16, 6, null, false, new int[][]{{30, 80, 50}}, "jet_ram"),
   /** LMB out of a forward dash: the punch carried by the dash. */
   DASH_STRIKE(17, 3, MovePhase.FINISH, false, new int[][]{{6, 3, 15}}, "dash_strike"),
   /** End of the air suit-up (spec §4.4): the fall (INTRO, ends on the ground), fist and knee into the ground, the slow rise. */
   HERO_LANDING(18, 6, null, false, new int[][]{{40, 2, 26}}, "hero_landing");

   private static final HulkMove[] VALUES = values();
   /** Snapshot actionId of a Hulkbuster move = OFFSET + wireId (never collides with HeroAction ordinals). */
   public static final int WIRE_OFFSET = 1000;

   private final int wireId;
   private final int priority;
   @Nullable
   private final MovePhase cancellableFrom;
   private final boolean paired;
   private final int[][] steps;
   private final String[] clips;

   HulkMove(int wireId, int priority, @Nullable MovePhase cancellableFrom, boolean paired, int[][] steps, String... clips) {
      this.wireId = wireId;
      this.priority = priority;
      this.cancellableFrom = cancellableFrom;
      this.paired = paired;
      this.steps = steps;
      this.clips = clips;
   }

   @Override
   public int wireId() {
      return this.wireId;
   }

   @Override
   public int priority() {
      return this.priority;
   }

   @Override
   public int ticks(MovePhase phase) {
      return this.ticks(phase, 0);
   }

   @Override
   public int ticks(MovePhase phase, int step) {
      int[] row = this.steps[Math.max(0, Math.min(this.steps.length - 1, step))];
      return row[phase.ordinal()];
   }

   @Nullable
   @Override
   public MovePhase cancellableFrom() {
      return this.cancellableFrom;
   }

   @Override
   public boolean paired() {
      return this.paired;
   }

   @Override
   public int maxSteps() {
      return this.steps.length;
   }

   /**
    * Hyper armour (spec §6.1, §7, §4): the double hammer's MAIN, the finisher's wind-up and contact,
    * the whole ultimate except its tail. Hits never stop these; the owner's lower-priority input does not either.
    */
   @Override
   public boolean resists(InterruptReason reason, MovePhase phase) {
      if (reason == InterruptReason.LIFECYCLE) {
         return false;
      }

      return switch (this) {
         case JET_RAM -> phase != MovePhase.FINISH;
         case HERO_LANDING -> true;
         case GRAB_FINISH -> phase != MovePhase.FINISH && reason != InterruptReason.CONTROL;
         default -> false;
      };
   }

   /** Hyper armour of combo step {@code step} (only the third strike). */
   public boolean resistsHit(MovePhase phase, int step) {
      if (this == COMBO) {
         return step == 2 && phase == MovePhase.MAIN;
      }

      return this.resists(InterruptReason.HIT, phase);
   }

   /**
    * Clip of one phase. Most moves play one clip over the whole step; the moves whose INTRO or MAIN has no
    * fixed length (a hold, a fall, a charge released by the button) split into a loop and a fixed tail.
    */
   public String clip(int step, MovePhase phase) {
      return switch (this) {
         case CHARGED -> phase == MovePhase.INTRO ? "charged_windup" : "charged_release";
         case HERO_LANDING -> phase == MovePhase.INTRO ? "hero_fall" : "hero_landing";
         case BLOCK -> phase == MovePhase.INTRO ? "block_raise" : phase == MovePhase.MAIN ? "block_hold" : "block_lower";
         case GRAB_HOLD -> phase == MovePhase.INTRO ? "grab_lift" : "grab_hold";
         default -> this.clip(step);
      };
   }

   /** Ticks into {@link #clip(int, MovePhase)} at a phase and the ticks spent in it. */
   public int clipTicks(int step, MovePhase phase, int phaseElapsed) {
      return switch (this) {
         case CHARGED, HERO_LANDING -> phase == MovePhase.INTRO ? phaseElapsed
            : phase == MovePhase.MAIN ? phaseElapsed : this.ticks(MovePhase.MAIN, step) + phaseElapsed;
         case BLOCK, GRAB_HOLD -> phaseElapsed;
         default -> dev.baranhan.viltrumitecore.hero.combat.StagedTimeline.phaseStart(this, phase, step) + phaseElapsed;
      };
   }

   /** The split clips that loop (hold / fall); every other clip holds its last frame. */
   public static boolean loops(String clip) {
      return clip.equals("hero_fall") || clip.equals("block_hold") || clip.equals("grab_hold");
   }

   /** Ticks a clip must last (HulkbusterClipsTest): the step, or the fixed segment of a split move; 0 = a loop. */
   public int clipLength(int step, MovePhase phase) {
      String clip = this.clip(step, phase);
      if (loops(clip)) {
         return 0;
      }

      return switch (this) {
         case CHARGED, HERO_LANDING -> phase == MovePhase.INTRO ? this.ticks(MovePhase.INTRO, step)
            : this.ticks(MovePhase.MAIN, step) + this.ticks(MovePhase.FINISH, step);
         case BLOCK, GRAB_HOLD -> this.ticks(phase, step);
         default -> this.length(step);
      };
   }

   /** Clip name of combo step {@code step} (one clip per step, same length as the step). */
   public String clip(int step) {
      return this.clips[Math.max(0, Math.min(this.clips.length - 1, step))];
   }

   public int wire() {
      return WIRE_OFFSET + this.wireId;
   }

   public String translationKey() {
      return "hud.viltrumitecore.ironman.hulkbuster.move." + this.name().toLowerCase(Locale.ROOT);
   }

   /** Move of a snapshot actionId, null when it is no Hulkbuster move. */
   @Nullable
   public static HulkMove byWire(int actionId) {
      int id = actionId - WIRE_OFFSET;
      for (HulkMove move : VALUES) {
         if (move.wireId == id) {
            return move;
         }
      }

      return null;
   }

   /** First tick of MAIN (the authoritative contact of a strike). */
   public int contactTick(int step) {
      return this.ticks(MovePhase.INTRO, step);
   }
}
