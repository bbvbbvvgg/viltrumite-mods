package dev.baranhan.viltrumitecore.hero.budget;

/** One expansion ability: shared mechanic + name slug + numbers + colour. */
public record BudgetMove(BudgetMoveType type, String slug, float damage, int cooldown, int color, BudgetShape shape, BudgetBuff buff) {
   public BudgetMove {
      // Hard floor: no ability may fire more often than twice a second (server load guard).
      cooldown = Math.max(10, cooldown);
      damage = Math.max(0.0F, Math.min(60.0F, damage));
   }
}
