package dev.baranhan.viltrumitecore.hero.thor.combat;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Pure recognizer of the six electric combos: keeps the last actions (at
 * most 3, consecutive ones at most {@code gapTicks} apart) and reports the
 * longest combo the newest action completes. A recognised combo consumes
 * its actions.
 */
public final class ElectricRecognizer {
   public static final int MAX_ACTIONS = 3;

   /** A combo completed by the newest action; {@code fires} false when it needs Mjölnir and it is not in the hand. */
   public record Result(ElectricCombo combo, List<ElectricAction> actions, boolean fires) {
   }

   private final List<ElectricAction> history = new ArrayList<>();

   /** Adds an action; returns the combo it completes, or null. */
   @Nullable
   public Result push(ElectricAction action, int gapTicks, boolean hammerInHandNow) {
      if (!this.history.isEmpty() && action.tick() - this.history.get(this.history.size() - 1).tick() > gapTicks) {
         this.history.clear();
      }

      this.history.add(action);
      while (this.history.size() > MAX_ACTIONS) {
         this.history.remove(0);
      }

      ElectricCombo best = null;
      for (ElectricCombo combo : ElectricCombo.values()) {
         int n = combo.length();
         if (n <= this.history.size() && (best == null || n > best.length())
            && combo.matches(this.history.subList(this.history.size() - n, this.history.size()))) {
            best = combo;
         }
      }

      if (best == null) {
         return null;
      }

      List<ElectricAction> used = List.copyOf(this.history.subList(this.history.size() - best.length(), this.history.size()));
      this.history.clear();
      // A thrown hammer was in the hand for the throw itself; otherwise the hand counts now.
      boolean hammer = hammerInHandNow || action.kind() == ElectricAction.Kind.MMB_THROW && action.hammerInHand();
      return new Result(best, used, !best.needsHammer() || hammer);
   }

   public List<ElectricAction> history() {
      return List.copyOf(this.history);
   }

   public void clear() {
      this.history.clear();
   }
}
