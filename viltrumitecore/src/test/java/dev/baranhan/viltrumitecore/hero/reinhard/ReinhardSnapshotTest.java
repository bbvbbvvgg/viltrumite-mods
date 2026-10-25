package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import org.junit.jupiter.api.Test;

class ReinhardSnapshotTest {
   @Test void actualComposerSupportsEveryAbilityAndReservedSlotsInEveryMode() {
      for (ReinhardMode mode : ReinhardMode.values()) {
         ReinhardState state = new ReinhardState();
         state.mode = mode;
         state.modeCooldown = 771;
         for (ReinhardAbility ability : ReinhardAbility.values()) state.cooldowns[ability.ordinal()] = 100 + ability.ordinal();
         state.action.start(ReinhardAbility.DIAGONAL_WORLD_CUT, 90, 42, 3);
         state.action.tick();
         int sceneFlags = (1 << ReinhardFlags.WORLD_CUT_ACTIVE) | (1 << ReinhardFlags.WORLD_ERASURE_ACTIVE);
         HeroPublicSnapshot snapshot = ReinhardHero.composeSnapshot(state, 1201, sceneFlags);
         HeroPublicSnapshot decoded = HeroPublicSnapshot.decode(snapshot.encode());
         assertEquals(snapshot, decoded);
         for (ReinhardAbility ability : ReinhardAbility.values()) {
            assertEquals(100 + ability.ordinal(), decoded.extraCooldown(ability.snapshotIndex()), ability.name());
         }
         assertEquals(771, decoded.extraCooldown(ReinhardFlags.EXTRA_MODE_CD));
         assertEquals(61, decoded.extraCooldown(ReinhardFlags.EXTRA_PHOENIX));
         assertEquals(3, decoded.variant());
         assertEquals(42, decoded.controlTargetId());
         assertEquals(1, decoded.actionElapsed());
         assertTrue(decoded.actionBusy());
         assertEquals(sceneFlags, decoded.heroFlags() & sceneFlags);
      }
   }

   @Test void freshlyLoadedStateProducesSnapshotWithoutCrashing() {
      HeroPublicSnapshot snapshot = ReinhardHero.composeSnapshot(new ReinhardState(), 0, 0);
      assertEquals(snapshot, HeroPublicSnapshot.decode(snapshot.encode()));
      assertEquals(0, snapshot.extraCooldown(ReinhardAbility.DIAGONAL_WORLD_CUT.snapshotIndex()));
      assertFalse(snapshot.actionBusy());
   }
}
