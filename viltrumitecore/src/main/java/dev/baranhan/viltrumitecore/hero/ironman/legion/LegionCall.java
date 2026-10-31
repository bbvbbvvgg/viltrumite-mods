package dev.baranhan.viltrumitecore.hero.ironman.legion;

import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkRoster;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Server check of a Legion call (design §3): the client only shows the menu. */
public final class LegionCall {
   private LegionCall() {
   }

   /**
    * The marks that may really fly: known ids, no duplicates, callable right
    * now, and no more than the free places of the squad. Keeps the player's
    * order (it decides the arrival slots).
    */
   public static List<MarkId> validate(int[] requested, Predicate<MarkId> callable, int alreadyFlying) {
      int free = Math.max(0, LegionRules.MAX_SUITS - Math.max(0, alreadyFlying));
      List<MarkId> out = new ArrayList<>(free);
      if (requested == null) {
         return out;
      }

      for (int id : requested) {
         if (out.size() >= free) {
            break;
         }

         MarkId mark = MarkId.byId(id);
         if (mark != null && !out.contains(mark) && callable.test(mark)) {
            out.add(mark);
         }
      }

      return out;
   }

   public static List<MarkId> validate(int[] requested, MarkRoster roster) {
      return validate(requested, roster::legionCallable, roster.legion().size());
   }

   /** Pre-selection of the menu: the healthiest callable marks, up to the squad size. */
   public static List<MarkId> suggest(MarkRoster roster) {
      List<MarkId> callable = new ArrayList<>();
      for (MarkId id : MarkId.values()) {
         if (roster.legionCallable(id)) {
            callable.add(id);
         }
      }

      callable.sort(Comparator.comparingDouble((MarkId id) -> -LegionRules.fraction(roster.durability(id), roster.maxDurability(id))));
      int free = Math.max(0, LegionRules.MAX_SUITS - roster.legion().size());
      return callable.size() > free ? new ArrayList<>(callable.subList(0, free)) : callable;
   }
}
