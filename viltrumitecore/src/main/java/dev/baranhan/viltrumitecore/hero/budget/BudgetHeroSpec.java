package dev.baranhan.viltrumitecore.hero.budget;

import dev.baranhan.viltrumitecore.hero.HeroId;

/** Everything one expansion hero is: id, look, 2-4 moves, optional ultimate scene. */
public record BudgetHeroSpec(HeroId id, BudgetLook look, BudgetMove[] moves, BudgetScene scene) {
   public BudgetHeroSpec {
      if (moves.length < 2 || moves.length > 4) {
         throw new IllegalArgumentException(id + " needs 2-4 moves, has " + moves.length);
      }
   }

   public String key() {
      return this.id.key();
   }

   public String abilityId(BudgetMove move) {
      return "budget:" + this.key() + "_" + move.slug();
   }

   public String abilityLangKey(BudgetMove move) {
      return "ability.viltrumitecore.budget_" + this.key() + "_" + move.slug();
   }

   public String nameKey() {
      return "hero.viltrumitecore." + this.key();
   }

   /** Index of the move bound to this action, -1 when the hero has none. */
   public int indexOf(dev.baranhan.viltrumitecore.hero.HeroAction action) {
      for (int i = 0; i < this.moves.length; i++) {
         if (this.moves[i].type().action() == action) {
            return i;
         }
      }

      return -1;
   }

   public boolean hasScene() {
      return this.scene != BudgetScene.NONE;
   }
}
