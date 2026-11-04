package dev.baranhan.viltrumitecore.city.life;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.city.life.PhoneRoutine.Kind;
import dev.baranhan.viltrumitecore.city.life.PhoneRoutine.Moment;
import dev.baranhan.viltrumitecore.city.life.PhoneRoutine.Phase;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** #151: the phone timeline is shared (pure, deterministic), rare per person, out of step and in the right order. */
class PhoneRoutineTest {
   @Test
   void sameSeedAndTimeGiveTheSameMomentEverywhere() {
      Random rnd = new Random(5);
      for (int i = 0; i < 500; i++) {
         int seed = rnd.nextInt();
         long time = rnd.nextInt(10_000_000);
         assertEquals(PhoneRoutine.at(seed, time, 0.5F), PhoneRoutine.at(seed, time, 0.5F));
      }
   }

   @Test
   void onlyAFewPeopleAreOnThePhoneAtOnceAndNotInStep() {
      Random rnd = new Random(11);
      int active = 0;
      int n = 4000;
      Set<Phase> phases = EnumSet.noneOf(Phase.class);
      Set<Integer> starts = new HashSet<>();
      for (int i = 0; i < n; i++) {
         int seed = rnd.nextInt();
         Moment m = PhoneRoutine.at(seed, 123_456L, 0.0F);
         if (m.active()) {
            active++;
            phases.add(m.phase());
            starts.add((int)m.elapsed());
         }
         if (!PhoneRoutine.owner(seed)) {
            assertFalse(m.active(), "no phone, no phone moment");
         }
      }
      float share = active / (float)n;
      assertTrue(share > 0.03F && share < 0.13F, "share on the phone " + share);
      assertTrue(phases.size() >= 5, "people are in different phases " + phases);
      assertTrue(starts.size() > active / 4, "nobody starts in step");
   }

   @Test
   void everyPlanFitsItsCycleAndPhasesRunInOrder() {
      Random rnd = new Random(17);
      int calls = 0;
      int reads = 0;
      for (int i = 0; i < 300; i++) {
         int seed = rnd.nextInt();
         long cycle = rnd.nextInt(100_000);
         PhoneRoutine.Plan p = PhoneRoutine.plan(seed, cycle);
         if (p.kind() == Kind.NONE) {
            continue;
         }
         assertTrue(p.start() >= 10 && p.start() + p.length() <= PhoneRoutine.CYCLE, "plan inside its cycle");
         calls += p.kind() == Kind.CALL ? 1 : 0;
         reads += p.kind() == Kind.READ ? 1 : 0;
      }
      assertTrue(calls > 5 && reads > 5, "both reading and calls");

      // walk whole cycles tick by tick: the phases follow each other, each from t 0 to its length
      for (int seed = 0, found = 0; found < 40 && seed < 100_000; seed++) {
         long t0 = 9_000L;
         List<Phase> seq = new ArrayList<>();
         Moment prev = Moment.NONE;
         for (long t = t0; t < t0 + 2L * PhoneRoutine.CYCLE; t++) {
            Moment m = PhoneRoutine.at(seed, t, 0.0F);
            if (m.active()) {
               assertTrue(m.t() >= 0 && m.t() < m.len());
               if (prev.active() && prev.phase() == m.phase()) {
                  assertEquals(prev.t() + 1.0F, m.t(), 1.0E-4F);
               } else {
                  if (t > t0) {
                     assertEquals(0.0F, m.t(), 1.0E-4F, "a phase starts at its beginning");
                  }
                  seq.add(m.phase());
               }
            }
            prev = m;
         }
         if (seq.isEmpty()) {
            continue;
         }
         found++;
         for (int i = 0; i < seq.size(); i++) {
            Phase ph = seq.get(i);
            if (ph == Phase.DRAW && i + 1 < seq.size()) {
               Phase next = seq.get(i + 1);
               assertTrue(next == Phase.LOOK || next == Phase.DIAL);
               List<Phase> want = next == Phase.LOOK ? List.of(Phase.DRAW, Phase.LOOK, Phase.STOW)
                  : List.of(Phase.DRAW, Phase.DIAL, Phase.TO_EAR, Phase.TALK, Phase.FROM_EAR, Phase.HANG_UP, Phase.STOW);
               if (i + want.size() <= seq.size()) {
                  assertEquals(want, seq.subList(i, i + want.size()));
               }
            }
         }
      }
   }

   @Test
   void thePhoneIsInTheHandOnlyBetweenPocketAndPocket() {
      assertFalse(new Moment(Phase.DRAW, PhoneRoutine.IN_HAND - 1, PhoneRoutine.DRAW, Kind.READ, 0, 1, 0).inHand());
      assertTrue(new Moment(Phase.DRAW, PhoneRoutine.IN_HAND, PhoneRoutine.DRAW, Kind.READ, 0, 1, 0).inHand());
      assertTrue(new Moment(Phase.TALK, 50, 200, Kind.CALL, 0, 1, 0).inHand());
      assertTrue(new Moment(Phase.STOW, PhoneRoutine.OUT_OF_HAND - 1, PhoneRoutine.STOW, Kind.READ, 0, 1, 0).inHand());
      assertFalse(new Moment(Phase.STOW, PhoneRoutine.OUT_OF_HAND, PhoneRoutine.STOW, Kind.READ, 0, 1, 0).inHand());
      assertFalse(new Moment(Phase.TALK, 50, 200, Kind.CALL, 0, 1, 0).screenOn(), "dark at the ear");
      assertTrue(new Moment(Phase.LOOK, 50, 200, Kind.READ, 0, 1, 0).screenOn());
      assertTrue(new Moment(Phase.TALK, 5, 200, Kind.CALL, 2, 1, 0).holdsStill());
      assertFalse(new Moment(Phase.TALK, 5, 200, Kind.CALL, 3, 1, 0).holdsStill(), "half the callers walk on");
      assertFalse(new Moment(Phase.LOOK, 5, 200, Kind.READ, 2, 1, 0).holdsStill(), "readers walk on");
   }

   @Test
   void aMomentThatBeganBeforeThePersonCalmedIsSkipped() {
      Moment m = new Moment(Phase.TALK, 40, 200, Kind.CALL, 0, 1, 90);
      assertSame(Moment.NONE, PhoneRoutine.calm(m, 30), "the alarm put the phone away: never back out mid-call");
      assertSame(m, PhoneRoutine.calm(m, 200));
      assertSame(Moment.NONE, PhoneRoutine.calm(Moment.NONE, 1000));
   }
}
