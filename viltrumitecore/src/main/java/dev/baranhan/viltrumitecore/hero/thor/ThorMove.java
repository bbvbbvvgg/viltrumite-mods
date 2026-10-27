package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedMove;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Every Thor move of the roadmap, played by the shared StagedTimeline. Only
 * moves of shipped stages are authored (non-zero phase ticks); the others
 * carry their stage, priority tier and pairing so the controls and docs can
 * name them, and they can never start (zero length, refused before the
 * timeline). The wire id is the ordinal: append only, never reorder.
 */
public enum ThorMove implements StagedMove {
   // Stage 1 (shipped): handling Mjölnir.
   DRAW(1, ThorRules.PRIORITY_UTILITY, false, ThorRules.DRAW_INTRO, ThorRules.DRAW_MAIN, ThorRules.DRAW_FINISH, MovePhase.FINISH, 1),
   HOLSTER(1, ThorRules.PRIORITY_UTILITY, false, ThorRules.HOLSTER_INTRO, ThorRules.HOLSTER_MAIN, ThorRules.HOLSTER_FINISH, MovePhase.FINISH, 1),
   // Stage 2: melee and guard.
   HAMMER_COMBO(2, ThorRules.PRIORITY_ATTACK, false),
   FIST_COMBO(2, ThorRules.PRIORITY_ATTACK, false),
   GUARD(2, ThorRules.PRIORITY_UTILITY, false),
   // Stage 3: throw / recall and the electric state.
   HAMMER_THROW(3, ThorRules.PRIORITY_HEAVY, false),
   RECALL(3, ThorRules.PRIORITY_UTILITY, false),
   ELECTRIC_MODE(3, ThorRules.PRIORITY_UTILITY, false),
   ELECTRIC_COMBO(3, ThorRules.PRIORITY_ATTACK, false),
   LIGHTNING_CALL(3, ThorRules.PRIORITY_HEAVY, false),
   // Stage 4: flight and mobility.
   AIR_STRIKE(4, ThorRules.PRIORITY_ATTACK, false),
   LIGHTNING_DASH(4, ThorRules.PRIORITY_HEAVY, false),
   TELEPORT_STRIKE(4, ThorRules.PRIORITY_HEAVY, false),
   DIVE_SLAM(4, ThorRules.PRIORITY_HEAVY, false),
   // Stage 5: grabs (two-character scenes).
   GRAB(5, ThorRules.PRIORITY_GRAB, true),
   GRAB_SLAM(5, ThorRules.PRIORITY_GRAB, true),
   // Stage 6: fatalities.
   FATALITY(6, ThorRules.PRIORITY_FATALITY, true),
   // Stage 7: the ultimate.
   WRATH(7, ThorRules.PRIORITY_ULTIMATE, false);

   private static final ThorMove[] VALUES = values();

   private final int stage;
   private final int priority;
   private final boolean paired;
   private final int intro;
   private final int main;
   private final int finish;
   @Nullable
   private final MovePhase cancellableFrom;
   private final int maxSteps;

   /** Not authored yet: zero ticks in every phase, so it can never start. */
   ThorMove(int stage, int priority, boolean paired) {
      this(stage, priority, paired, 0, 0, 0, null, 1);
   }

   ThorMove(int stage, int priority, boolean paired, int intro, int main, int finish, @Nullable MovePhase cancellableFrom, int maxSteps) {
      this.stage = stage;
      this.priority = priority;
      this.paired = paired;
      this.intro = intro;
      this.main = main;
      this.finish = finish;
      this.cancellableFrom = cancellableFrom;
      this.maxSteps = maxSteps;
   }

   /** Development stage that ships this move (docs/plans roadmap). */
   public int stage() {
      return this.stage;
   }

   /** Authored and part of a shipped stage: may start. */
   public boolean shipped() {
      return this.stage <= ThorRules.SHIPPED_STAGE && this.length() > 0;
   }

   public String translationKey() {
      return "hero.viltrumitecore.thor.move." + this.name().toLowerCase(Locale.ROOT);
   }

   @Override
   public int wireId() {
      return this.ordinal();
   }

   @Override
   public int priority() {
      return this.priority;
   }

   @Override
   public int ticks(MovePhase phase) {
      return switch (phase) {
         case INTRO -> this.intro;
         case MAIN -> this.main;
         case FINISH -> this.finish;
      };
   }

   @Override
   @Nullable
   public MovePhase cancellableFrom() {
      return this.cancellableFrom;
   }

   @Override
   public boolean paired() {
      return this.paired;
   }

   @Override
   public int maxSteps() {
      return this.maxSteps;
   }

   @Nullable
   public static ThorMove byWireId(int wireId) {
      return wireId >= 0 && wireId < VALUES.length ? VALUES[wireId] : null;
   }
}
