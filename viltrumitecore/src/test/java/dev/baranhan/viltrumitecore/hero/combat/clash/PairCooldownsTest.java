package dev.baranhan.viltrumitecore.hero.combat.clash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pair cooldowns are symmetric, expire and are pruned. */
class PairCooldownsTest {
   private static final UUID A = new UUID(0L, 1L);
   private static final UUID B = new UUID(0L, 2L);
   private static final UUID C = new UUID(0L, 3L);

   @Test
   void cooldownIsSymmetricAndPerPair() {
      PairCooldowns cooldowns = new PairCooldowns();
      cooldowns.start(A, B, 100L, 50);
      assertFalse(cooldowns.ready(B, A, 120L));
      assertTrue(cooldowns.ready(A, C, 120L));
      assertTrue(cooldowns.ready(A, B, 150L));
   }

   @Test
   void finishedCooldownsArePruned() {
      PairCooldowns cooldowns = new PairCooldowns();
      cooldowns.start(A, B, 0L, 10);
      cooldowns.start(A, C, 0L, 100);
      cooldowns.prune(10L);
      assertEquals(1, cooldowns.size());
      cooldowns.clear();
      assertEquals(0, cooldowns.size());
   }
}
