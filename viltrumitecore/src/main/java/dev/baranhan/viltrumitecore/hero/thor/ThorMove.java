package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedMove;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveDefinition;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Thor move ids played by the shared StagedTimeline. The enum carries only
 * identity (priority tier, pairing, retired); timing comes from the per-move
 * registration in {@link ThorMoves}. An unregistered move has zero length and
 * can never start. The wire id is the ordinal: append only, never reorder.
 * Names are internal ids, not approved ability names.
 */
public enum ThorMove implements StagedMove {
   /** Technical draw / holster timings (wire ids kept); no input reaches them. */
   DRAW(ThorRules.PRIORITY_UTILITY, false, false),
   HOLSTER(ThorRules.PRIORITY_UTILITY, false, false),
   /** LMB combos (hammer in hand / bare fists). */
   HAMMER_COMBO(ThorRules.PRIORITY_ATTACK, false, false),
   FIST_COMBO(ThorRules.PRIORITY_ATTACK, false, false),
   /** Retired context (no guard was approved). */
   GUARD(ThorRules.PRIORITY_UTILITY, false, true),
   /** MMB fallback branch: throw (holding MMB charges it). */
   HAMMER_THROW(ThorRules.PRIORITY_HEAVY, false, false),
   /** MMB while Mjölnir is away. */
   RECALL(ThorRules.PRIORITY_UTILITY, false, false),
   /** R: toggle the electric mode. */
   ELECTRIC_MODE(ThorRules.PRIORITY_UTILITY, false, false),
   /** LMB in the electric mode. */
   ELECTRIC_COMBO(ThorRules.PRIORITY_ATTACK, false, false),
   /** Retired as a manual R move (sky lightning fires on its own after a successful electric combo). */
   LIGHTNING_CALL(ThorRules.PRIORITY_HEAVY, false, true),
   /** Retired: no attacks in full flight. */
   AIR_STRIKE(ThorRules.PRIORITY_ATTACK, false, true),
   /** Retired: not approved. */
   LIGHTNING_DASH(ThorRules.PRIORITY_HEAVY, false, true),
   TELEPORT_STRIKE(ThorRules.PRIORITY_HEAVY, false, true),
   DIVE_SLAM(ThorRules.PRIORITY_HEAVY, false, true),
   /** MMB while the grab window is open. */
   GRAB(ThorRules.PRIORITY_GRAB, true, false),
   /** Retired: LMB while grabbing was not approved. */
   GRAB_SLAM(ThorRules.PRIORITY_GRAB, true, true),
   /** Started by a LETHAL hammer-in-hand hit on a player / hero, never by a key. */
   FATALITY(ThorRules.PRIORITY_FATALITY, true, false),
   /** Ultimate key (internal id; the name is not approved). */
   WRATH(ThorRules.PRIORITY_ULTIMATE, false, false),
   // --- appended by the bridge ---
   /** MMB with any enemy within ThorRules.MELEE_ENEMY_RADIUS: the melee callback. */
   HAMMER_STRIKE(ThorRules.PRIORITY_ATTACK, false, false),
   /** Y: pull Thor to Mjölnir flying after a throw. */
   HAMMER_PULL(ThorRules.PRIORITY_HEAVY, false, false),
   /** V: choose the teleport point (top-down camera). */
   TELEPORT_SELECT(ThorRules.PRIORITY_UTILITY, false, false),
   // --- appended by session B (combat) ---
   /** RMB in the electric mode: circular electric wave on a tap (a held RMB grows into ELECTRIC_STORM). */
   ELECTRIC_WAVE(ThorRules.PRIORITY_ATTACK, false, false),
   /** Held RMB in the electric mode: the expanding storm around Thor, ends on release. */
   ELECTRIC_STORM(ThorRules.PRIORITY_HEAVY, false, false),
   // --- appended by the Thor animation pass (owner playtest 2026-10-28) ---
   /**
    * RMB on Mjölnir resting on the ground: Thor bends to it, closes both hands on the handle and heaves it up.
    * The hammer changes place on the contact tick (ENTER_MAIN), never on the click.
    */
   PICKUP(ThorRules.PRIORITY_UTILITY, false, false);

   private static final ThorMove[] VALUES = values();

   private final int priority;
   private final boolean paired;
   private final boolean retired;

   ThorMove(int priority, boolean paired, boolean retired) {
      this.priority = priority;
      this.paired = paired;
      this.retired = retired;
   }

   /** Kept only for wire / save compatibility: no input, HUD or registration. */
   public boolean retired() {
      return this.retired;
   }

   /** Registered in ThorMoves: may start. */
   public boolean authored() {
      return ThorMoves.authored(this);
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
      ThorMoveDefinition definition = ThorMoves.definition(this);
      return definition == null ? 0 : definition.ticks(phase);
   }

   @Override
   @Nullable
   public MovePhase cancellableFrom() {
      ThorMoveDefinition definition = ThorMoves.definition(this);
      return definition == null ? null : definition.cancellableFrom();
   }

   @Override
   public boolean paired() {
      return this.paired;
   }

   @Override
   public int maxSteps() {
      ThorMoveDefinition definition = ThorMoves.definition(this);
      return definition == null ? 1 : definition.maxSteps();
   }

   @Nullable
   public static ThorMove byWireId(int wireId) {
      return wireId >= 0 && wireId < VALUES.length ? VALUES[wireId] : null;
   }
}
