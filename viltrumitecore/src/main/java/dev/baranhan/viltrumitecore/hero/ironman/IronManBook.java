package dev.baranhan.viltrumitecore.hero.ironman;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.book.BookProfile;
import dev.baranhan.viltrumitecore.hero.book.BookProfiles;
import net.minecraft.world.entity.player.Player;

/**
 * Heaven's Door vs Iron Man (docs/rohan/heavens-door-design.md §7): inside the suit or the Hulkbuster the book reads like
 * telemetry, and the plates leave a shorter window. Lives outside {@link IronManHero} (that class is at the size limit).
 */
public final class IronManBook {
   public static final float SUIT_WINDOW = 0.6F;

   private IronManBook() {
   }

   public static void init() {
      BookProfiles.registerPlayerModifier(IronManBook::modify);
   }

   static BookProfile modify(Player self, BookProfile base) {
      if (!(self instanceof HeroPlayer hp) || hp.getHeroId() != HeroId.IRON_MAN) {
         return base;
      }
      IronManState state = IronManState.of(self);
      boolean armored = state != null && (state.suit.worn() || state.hulkbuster.active());
      return armored ? base.withWindow(Math.round(base.windowTicks() * SUIT_WINDOW)).withNotes("ironman_suit") : base;
   }
}
