package dev.baranhan.viltrumitecore.hero.regulus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The wire composer: everything a remote view needs, nothing server-only. */
class RegulusSnapshotTest {

   @Test
   void idleSnapshotIsQuiet() {
      HeroPublicSnapshot s = RegulusHero.compose(new RegulusState(), -1, false, -1);
      assertEquals(HeroId.REGULUS, s.heroId());
      assertEquals(-1, s.actionId());
      assertFalse(s.actionBusy());
      assertFalse(s.lionActive());
      assertEquals(0, s.heroFlags());
      assertNull(s.actionTarget());
      assertEquals(0, s.extraCooldowns()[RegulusFlags.EXTRA_COMBO]);
   }

   @Test
   void moveLionHostAndSubjectsAreOnTheWire() {
      RegulusState state = new RegulusState();
      state.startMove(RegulusMove.REPLY);
      state.moveTick = RegulusRules.REPLY_WINDOW_START;
      state.lion = true;
      state.lionTicks = 45;
      state.strain = RegulusRules.STRAIN_WARN;
      state.lionLock = 0;
      state.comboStep = 1;
      state.subjects.add(UUID.randomUUID());
      state.subjects.add(UUID.randomUUID());
      state.hopTimer = 120;
      state.ultCooldown = 900;
      HeroPublicSnapshot s = RegulusHero.compose(state, 42, true, -1);
      assertEquals(RegulusMove.REPLY.ordinal(), s.actionId());
      assertEquals(RegulusRules.REPLY_WINDOW_START, s.actionElapsed());
      assertEquals(RegulusMove.REPLY.length(), s.actionLength());
      assertTrue(s.actionBusy());
      assertTrue(s.lionActive());
      assertEquals(42, s.controlTargetId());
      assertEquals(2, s.hearts());
      assertEquals(RegulusRules.STRAIN_WARN, s.resource());
      assertFalse(s.resourceLocked());
      int f = s.heroFlags();
      assertTrue(RegulusFlags.has(f, RegulusFlags.LION));
      assertTrue(RegulusFlags.has(f, RegulusFlags.HOST_ALIVE));
      assertTrue(RegulusFlags.has(f, RegulusFlags.REPLY_WINDOW));
      assertTrue(RegulusFlags.has(f, RegulusFlags.REPLY_ARMED));
      assertTrue(RegulusFlags.has(f, RegulusFlags.STRAIN_WARN));
      assertFalse(RegulusFlags.has(f, RegulusFlags.SEIZED));
      assertFalse(RegulusFlags.has(f, RegulusFlags.ULT));
      assertEquals(900, s.cooldowns()[RegulusFlags.COOLDOWN_ULT]);
      int[] extra = s.extraCooldowns();
      assertEquals(2, extra[RegulusFlags.EXTRA_COMBO]);
      assertEquals(2, extra[RegulusFlags.EXTRA_SUBJECTS]);
      assertEquals(120, extra[RegulusFlags.EXTRA_HOP]);
      assertEquals(45, extra[RegulusFlags.EXTRA_LION_TICKS]);
      assertEquals(state.seed & 0x7FFF, extra[RegulusFlags.EXTRA_SEED]);
   }

   @Test
   void seizureAndLockAreVisible() {
      RegulusState state = new RegulusState();
      state.startMove(RegulusMove.SEIZURE);
      state.lionLock = RegulusRules.SEIZURE_LOCK;
      HeroPublicSnapshot s = RegulusHero.compose(state, -1, false, -1);
      assertTrue(RegulusFlags.has(s.heroFlags(), RegulusFlags.SEIZED));
      assertTrue(RegulusFlags.has(s.heroFlags(), RegulusFlags.LION_LOCKED));
      assertTrue(s.resourceLocked());
      assertEquals(RegulusRules.SEIZURE_LOCK, s.extraCooldowns()[RegulusFlags.EXTRA_LION_LOCK]);
   }

   @Test
   void replyWindowClosesOutsideItsTicks() {
      RegulusState state = new RegulusState();
      state.startMove(RegulusMove.REPLY);
      state.moveTick = RegulusRules.REPLY_WINDOW_END;
      HeroPublicSnapshot s = RegulusHero.compose(state, -1, false, -1);
      assertFalse(RegulusFlags.has(s.heroFlags(), RegulusFlags.REPLY_WINDOW));
      assertTrue(RegulusFlags.has(s.heroFlags(), RegulusFlags.REPLY_ARMED));
   }

   @Test
   void extrasFitTheWire() {
      assertTrue(RegulusFlags.EXTRA_COUNT <= HeroPublicSnapshot.EXTRA_COOLDOWN_MAX, "extras " + RegulusFlags.EXTRA_COUNT);
      assertTrue(RegulusFlags.REPLY_ARMED < 31);
   }
}
