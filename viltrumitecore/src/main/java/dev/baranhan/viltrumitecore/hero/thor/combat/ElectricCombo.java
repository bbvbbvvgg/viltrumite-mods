package dev.baranhan.viltrumitecore.hero.thor.combat;

import dev.baranhan.viltrumitecore.hero.thor.combat.ElectricAction.Kind;
import java.util.List;
import java.util.function.Predicate;

/**
 * The six electric combos (#79, #103, #115): recognised by sequences of 2–3
 * actions over LMB / RMB / MMB results, movement direction, holds and
 * context (#50, #51). Each makes its own effect, never a charge on enemies
 * (#17, #39). The count is approved; every sequence, effect and number is
 * PLACEHOLDER / DEMO. No in-game hints (#131).
 */
public enum ElectricCombo {
   /** Step back + LMB, then forward + LMB: a long ricochet chain. */
   RICOCHET(false, step(Kind.LMB, MoveDirection.BACK), step(Kind.LMB, MoveDirection.FORWARD)),
   /** RMB on the ground, then RMB in a jump: a double thunder ring. */
   THUNDER_RING(false, step(Kind.RMB), ElectricCombo.<ElectricAction>all(a -> a.kind() == Kind.RMB && a.airborne())),
   /** LMB, then the MMB melee strike: Mjölnir conducts a cone blast. Needs Mjölnir in the hand. */
   CONDUIT_SMASH(true, step(Kind.LMB), step(Kind.MMB_STRIKE)),
   /** Hold RMB (storm), then throw Mjölnir: a lightning lance from the hand along the view. Needs Mjölnir in the hand. */
   STORM_LANCE(true, step(Kind.HOLD_RMB), step(Kind.MMB_THROW)),
   /** Recall, then LMB: a fan of discharges from the hand (the flying hammer conducts nothing, #128). */
   RECALL_FAN(false, step(Kind.MMB_RECALL), step(Kind.LMB)),
   /** Strafe + RMB, forward + LMB, MMB melee strike: judgment, the strongest. Needs Mjölnir in the hand. */
   JUDGMENT(true, ElectricCombo.<ElectricAction>all(a -> a.kind() == Kind.RMB && a.direction().sideways()), step(Kind.LMB, MoveDirection.FORWARD),
      step(Kind.MMB_STRIKE));

   private final boolean needsHammer;
   private final List<Predicate<ElectricAction>> steps;

   @SafeVarargs
   ElectricCombo(boolean needsHammer, Predicate<ElectricAction>... steps) {
      this.needsHammer = needsHammer;
      this.steps = List.of(steps);
   }

   /** Strongest combos need Mjölnir in the hand (#16, #28, #116, #148); without it they do not fire and nothing is recalled. */
   public boolean needsHammer() {
      return this.needsHammer;
   }

   public int length() {
      return this.steps.size();
   }

   /** Do the newest {@link #length()} actions (oldest first) match? */
   public boolean matches(List<ElectricAction> tail) {
      if (tail.size() != this.steps.size()) {
         return false;
      }

      for (int i = 0; i < tail.size(); i++) {
         if (!this.steps.get(i).test(tail.get(i))) {
            return false;
         }
      }

      return true;
   }

   private static Predicate<ElectricAction> step(Kind kind) {
      return action -> action.kind() == kind;
   }

   private static Predicate<ElectricAction> step(Kind kind, MoveDirection direction) {
      return action -> action.kind() == kind && action.direction() == direction;
   }

   private static <T> Predicate<T> all(Predicate<T> predicate) {
      return predicate;
   }
}
