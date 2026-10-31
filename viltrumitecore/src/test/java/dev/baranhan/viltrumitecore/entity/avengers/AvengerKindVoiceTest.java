package dev.baranhan.viltrumitecore.entity.avengers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.baranhan.viltrumitecore.hero.HeroId;
import org.junit.jupiter.api.Test;

class AvengerKindVoiceTest {
   @Test
   void tonyAndThorSpeakAsTheirOwnHeroesTheRestThroughSteve() {
      assertEquals(HeroId.fromKey("ironman"), AvengerKind.IRON_MAN.voice());
      assertEquals(HeroId.fromKey("thor"), AvengerKind.THOR.voice());
      assertEquals(HeroId.CAPTAIN, AvengerKind.HULK.voice());
      assertEquals(HeroId.CAPTAIN, AvengerKind.BLACK_WIDOW.voice());
      assertEquals(HeroId.CAPTAIN, AvengerKind.HAWKEYE.voice());
   }
}
