package dev.baranhan.viltrumitecore.hero.budget.multiverse;

import dev.baranhan.viltrumitecore.hero.budget.BudgetHeroSpec;
import dev.baranhan.viltrumitecore.hero.budget.BudgetHeroes;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Runtime glue between the boss table and the hero roster. */
public final class MultiverseBosses {
   private MultiverseBosses() {
   }

   public static BudgetHeroSpec hero(MultiverseBoss boss) {
      for (BudgetHeroSpec spec : BudgetHeroes.roster()) {
         if (spec.key().equals(boss.heroKey())) {
            return spec;
         }
      }

      throw new IllegalStateException("Boss " + boss.id() + " uses unknown hero " + boss.heroKey());
   }

   /** {@code /multiverse boss <id>}: a single boss outside of any event (session 0). */
   @Nullable
   public static MultiverseFighter summon(ServerLevel level, Vec3 at, MultiverseBoss boss) {
      return MultiverseFighter.spawn(level, at, hero(boss), 0, boss, 1.0F, 1.0F, 1.0F);
   }
}
