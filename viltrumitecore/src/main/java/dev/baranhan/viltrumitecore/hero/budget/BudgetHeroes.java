package dev.baranhan.viltrumitecore.hero.budget;

import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/** Lookup and registration of the expansion roster (both sides). */
public final class BudgetHeroes {
   private static final Map<HeroId, BudgetHeroSpec> SPECS = new EnumMap<>(HeroId.class);

   static {
      for (BudgetHeroSpec spec : BudgetRoster.ALL) {
         if (SPECS.put(spec.id(), spec) != null) {
            throw new IllegalStateException("Duplicate expansion hero " + spec.id());
         }
      }
   }

   private BudgetHeroes() {
   }

   /** HeroRegistry hook: one shared chassis instance per roster entry. */
   public static void registerAll(Consumer<HeroDefinition> register) {
      for (BudgetHeroSpec spec : BudgetRoster.ALL) {
         register.accept(new BudgetHero(spec));
      }
   }

   @Nullable
   public static BudgetHeroSpec spec(@Nullable HeroId id) {
      return id == null ? null : SPECS.get(id);
   }

   public static boolean isExpansion(@Nullable HeroId id) {
      return spec(id) != null;
   }

   /** Roster in menu order. */
   public static List<BudgetHeroSpec> roster() {
      return List.of(BudgetRoster.ALL);
   }

   public static int count() {
      return BudgetRoster.ALL.length;
   }
}
