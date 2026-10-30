package dev.baranhan.viltrumitecore.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.baranhan.viltrumitecore.hero.HeroId;
import org.junit.jupiter.api.Test;

class HeroCommandsTest {
   @Test
   void heroFilterUsesSaveKeysCaseInsensitively() {
      for (HeroId id : HeroId.values()) {
         assertEquals(id, HeroCommands.parseHero(id.key()));
         assertEquals(id, HeroCommands.parseHero(id.key().toUpperCase(java.util.Locale.ROOT)));
      }
      assertNull(HeroCommands.parseHero("batman"));
   }
}
