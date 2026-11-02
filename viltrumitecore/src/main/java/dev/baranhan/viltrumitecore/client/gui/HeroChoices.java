package dev.baranhan.viltrumitecore.client.gui;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.wish.OdLagnaWishes;
import dev.baranhan.viltrumitecore.hero.wish.Wish;
import java.util.ArrayList;
import java.util.List;

/**
 * The heroes the choice screen offers (pure, unit-tested). They come from Od Lagna's wish registry, so a hero
 * that can be wished for can also be chosen on the screen: a new hero needs no edit here. The plain human is
 * the screen's own «remain human» button and is not listed.
 */
public final class HeroChoices {
   public static final String LABEL_PREFIX = "gui.viltrumitecore.race_selection.become_";

   private HeroChoices() {
   }

   /** Heroes in wish order, each once; unknown hero keys are skipped. */
   public static List<HeroId> heroes() {
      OdLagnaWishes.registerDefaults();
      List<HeroId> heroes = new ArrayList<>();
      for (Wish wish : OdLagnaWishes.all()) {
         HeroId hero = HeroId.fromKey(wish.heroKey());
         if (hero != null && hero != HeroId.HUMAN && !heroes.contains(hero)) {
            heroes.add(hero);
         }
      }
      return heroes;
   }

   /** Lang key of the hero's button, e.g. {@code gui.viltrumitecore.race_selection.become_red_hulk}. */
   public static String labelKey(HeroId hero) {
      return LABEL_PREFIX + hero.key();
   }
}
