package dev.baranhan.viltrumitecore.client.hero;

import static org.junit.jupiter.api.Assertions.assertSame;

import dev.baranhan.viltrumitecore.hero.HeroId;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HeroHeadPreviewsTest {
   private static final HeroHeadPreviews.Preview FALLBACK = (GuiGraphics g, int x, int y, int size, float partialTick) -> {
   };

   @AfterEach
   void reset() {
      HeroHeadPreviews.clear();
   }

   @Test
   void fallbackForUnregistered() {
      assertSame(FALLBACK, HeroHeadPreviews.resolve(HeroId.HOMELANDER, FALLBACK));
      assertSame(FALLBACK, HeroHeadPreviews.resolve(null, FALLBACK));
   }

   @Test
   void registeredWins() {
      HeroHeadPreviews.Preview preview = (g, x, y, size, partialTick) -> {
      };
      HeroHeadPreviews.register(HeroId.IRON_MAN, preview);
      assertSame(preview, HeroHeadPreviews.resolve(HeroId.IRON_MAN, FALLBACK));
      assertSame(FALLBACK, HeroHeadPreviews.resolve(HeroId.REGULUS, FALLBACK));
   }
}
