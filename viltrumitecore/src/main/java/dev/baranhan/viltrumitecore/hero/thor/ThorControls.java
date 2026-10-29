package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import javax.annotation.Nullable;

/**
 * Context controls: one key, a move id by state. Pure; the single resolver
 * for the server press, the client mouse claim and the panel grey-out. The
 * order of the checks is the priority of the contexts. It only routes: a
 * move does something only once a session registers it (ThorMoves).
 * Retired ids (guard, air strike, dash, teleport strike, dive slam, manual
 * lightning call, grab slam) are never returned.
 */
public final class ThorControls {
   private ThorControls() {
   }

   /** The move an input means right now, or null when it means nothing for Thor. */
   @Nullable
   public static ThorMove resolve(ThorInput input, ThorContext context) {
      return switch (input) {
         // No attacks in full flight.
         case STRIKE -> context.flying() ? null : context.mode() == ThorMode.ELECTRIC ? ThorMove.ELECTRIC_COMBO : melee(context);
         // RMB (session B): the same combo flow as LMB (the rhythm of both buttons picks the strikes), the wave / storm in the electric mode.
         case STANCE -> context.flying() ? null : context.mode() == ThorMode.ELECTRIC ? ThorMove.ELECTRIC_WAVE : melee(context);
         // MMB in full flight: only the recall (not an attack); grab, melee and throw wait for the landing / HOVER (decisions 22, 34).
         case HAMMER -> context.flying() ? context.hammerAway() ? ThorMove.RECALL : null : hammer(context);
         case POWER -> ThorMove.ELECTRIC_MODE;
         case MOBILITY -> context.hammer() == HammerState.THROWN ? ThorMove.HAMMER_PULL : null;
         // Stays routed in flight: session C's gate refuses it there with its own message (decision 48).
         case TELEPORT -> ThorMove.TELEPORT_SELECT;
         // The ultimate is an attack: none in full flight (decisions 22, 34; its own flight rule is still open in the canon).
         case ULTIMATE -> context.flying() ? null : ThorMove.WRATH;
      };
   }

   /**
    * LMB / RMB outside the electric mode: an open unarmed chain finishes
    * first (#166), then Mjölnir really in the right hand, else fists. An item
    * in the right hand keeps Mjölnir on the belt (MjolnirPlace.BELT,
    * ThorCombat.presence): those are fist strikes, never hammer combos.
    */
   private static ThorMove melee(ThorContext context) {
      return !context.fistChainOpen() && context.hammer() == HammerState.IN_HAND && context.handFree() ? ThorMove.HAMMER_COMBO : ThorMove.FIST_COMBO;
   }

   /**
    * MMB, the one decision point: grab window &gt; recall &gt; (no hammer at
    * hand: nothing) &gt; melee (enemy within 4 blocks) &gt; throw.
    */
   @Nullable
   public static ThorMove hammer(ThorContext context) {
      if (context.grabWindow()) {
         return ThorMove.GRAB;
      } else if (context.hammerAway()) {
         // Out of the hand in any world phase (thrown, returning, resting, falling, ...).
         return ThorMove.RECALL;
      } else if (beltOnly(context)) {
         // OPEN(owner): how Mjölnir gets from the belt into the hand (design §10, no DRAW key). Conservative: no
         // hammer move from the belt (no throw "from the belt", no hammer blow); the server answers "not with you".
         return null;
      }

      return context.enemyNear() ? ThorMove.HAMMER_STRIKE : ThorMove.HAMMER_THROW;
   }

   /**
    * Mjölnir neither in the hand nor away: on the belt by a technical
    * HOLSTER, or no world hammer exists yet. Session A's store never keeps
    * this state outside a technical DRAW / HOLSTER (MjolnirWorld.project).
    */
   static boolean beltOnly(ThorContext context) {
      return context.hammer() == HammerState.HOLSTERED && !context.hammerAway();
   }

   /** MMB resolved to nothing because Mjölnir is only on the belt (outside full flight): the press deserves an answer. */
   public static boolean onBelt(ThorInput input, ThorContext context) {
      return input == ThorInput.HAMMER && !context.flying() && !context.grabWindow() && beltOnly(context);
   }

   /** Drawing or throwing Mjölnir takes the right hand (ThorActions.gate refuses it while an item is there). */
   public static boolean needsHand(ThorMove move) {
      return move == ThorMove.DRAW || move == ThorMove.HAMMER_THROW;
   }

   /** The MMB recall is an overlay: it runs beside the current move and never takes the timeline (ThorActions.start). */
   public static boolean overlay(ThorMove move) {
      return move == ThorMove.RECALL;
   }

   /** Mouse claim: only a button whose resolved move is authored is taken from vanilla. */
   @Nullable
   public static HeroAction claimedMouse(MouseButton button, ThorContext context) {
      ThorInput input = ThorInput.of(button);
      if (input == null) {
         return null;
      }

      ThorMove move = resolve(input, context);
      return move != null && move.authored() ? input.action() : null;
   }

   /** Can this input start any authored move in some context (panel slot lit)? */
   public static boolean anyAuthored(ThorInput input) {
      for (ThorMove move : candidates(input)) {
         if (move.authored()) {
            return true;
         }
      }

      return false;
   }

   /** Every move an input can resolve to, in resolver order (tests, grey-out). */
   public static ThorMove[] candidates(ThorInput input) {
      return switch (input) {
         case STRIKE -> new ThorMove[]{ThorMove.ELECTRIC_COMBO, ThorMove.HAMMER_COMBO, ThorMove.FIST_COMBO};
         case STANCE -> new ThorMove[]{ThorMove.ELECTRIC_WAVE, ThorMove.HAMMER_COMBO, ThorMove.FIST_COMBO};
         case HAMMER -> new ThorMove[]{ThorMove.GRAB, ThorMove.RECALL, ThorMove.HAMMER_STRIKE, ThorMove.HAMMER_THROW};
         case POWER -> new ThorMove[]{ThorMove.ELECTRIC_MODE};
         case MOBILITY -> new ThorMove[]{ThorMove.HAMMER_PULL};
         case TELEPORT -> new ThorMove[]{ThorMove.TELEPORT_SELECT};
         case ULTIMATE -> new ThorMove[]{ThorMove.WRATH};
      };
   }
}
