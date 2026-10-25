package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

/** HeroDefinition.onRespawn: a generic hook that other heroes leave as a no-op. */
class HeroRespawnHookTest {
   @Test
   void defaultIsNoOp() throws NoSuchMethodException {
      assertTrue(HeroDefinition.class.getMethod("onRespawn", ServerPlayer.class).isDefault());
      HeroRegistry.registerDefaults();
      for (HeroId id : HeroId.values()) {
         if (id == HeroId.REINHARD) {
            continue;
         }
         HeroDefinition hero = HeroRegistry.get(id);
         assertDoesNotThrow(() -> hero.onRespawn(null), id.key());
      }
   }
}
