package dev.baranhan.viltrumitecore.worldevent.ultron;

import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.worldevent.ultron.UltronAdaptation.Change;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronAdaptation.Protocol;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronAdaptation.Reason;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronAdaptation.Tactic;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UltronAdaptationTest {
   private static List<Change> seconds(UltronAdaptation a, int n, boolean canActivate) {
      List<Change> all = new ArrayList<>();
      for (int i = 0; i < n; i++) {
         all.addAll(a.second(canActivate));
      }
      return all;
   }

   @Test
   void noSensorsNoLearning() {
      UltronAdaptation a = new UltronAdaptation(3);
      a.setLearning(UltronAdaptation.learningFor(0));
      assertTrue(a.frozen());
      a.observe(Tactic.AERIAL, 500.0F);
      assertEquals(0.0F, a.pressure(Tactic.AERIAL));
      assertTrue(a.second(true).isEmpty());
      assertEquals(0.6F, UltronAdaptation.learningFor(1));
      assertEquals(1.0F, UltronAdaptation.learningFor(2));
      assertEquals(1.2F, UltronAdaptation.learningFor(5));
   }

   @Test
   void protocolNeedsThresholdAndTheAdaptationPhase() {
      UltronAdaptation a = new UltronAdaptation(3);
      a.observe(Tactic.AERIAL, Protocol.ANTI_AIR.threshold * 0.8F);
      assertTrue(a.second(true).isEmpty(), "below the threshold");
      a.observe(Tactic.AERIAL, Protocol.ANTI_AIR.threshold);
      assertTrue(a.second(false).isEmpty(), "before the adaptation phase nothing activates");
      List<Change> changes = a.second(true);
      assertEquals(1, changes.size());
      assertEquals(new Change(Protocol.ANTI_AIR, true, Reason.ACTIVATED), changes.get(0));
      assertTrue(a.active(Protocol.ANTI_AIR));
      assertEquals(1, a.activations());
      assertEquals(Protocol.ANTI_AIR.duration, a.ticksLeft(Protocol.ANTI_AIR));
   }

   @Test
   void learningScalesPressure() {
      UltronAdaptation slow = new UltronAdaptation(3);
      slow.setLearning(UltronAdaptation.learningFor(1));
      slow.observe(Tactic.RANGED, 10.0F);
      assertEquals(6.0F, slow.pressure(Tactic.RANGED), 1.0E-4);
   }

   @Test
   void pressureHalvesOverItsHalfLife() {
      UltronAdaptation a = new UltronAdaptation(3);
      a.observe(Tactic.MELEE, 100.0F);
      a.observe(Tactic.FACTORY_LOSS, 100.0F);
      seconds(a, (int)UltronAdaptation.EVENT_HALF_LIFE_S, false);
      assertEquals(50.0F, a.pressure(Tactic.FACTORY_LOSS), 0.5F);
      seconds(a, (int)(UltronAdaptation.HALF_LIFE_S - UltronAdaptation.EVENT_HALF_LIFE_S), false);
      assertEquals(50.0F, a.pressure(Tactic.MELEE), 0.5F);
   }

   @Test
   void atMostMaxActiveAtOnce() {
      UltronAdaptation a = new UltronAdaptation(2);
      a.observe(Tactic.AERIAL, 100.0F);
      a.observe(Tactic.RANGED, 100.0F);
      a.observe(Tactic.SECTOR_FOCUS, 100.0F);
      a.observe(Tactic.FACTORY_LOSS, 100.0F);
      List<Change> changes = a.second(true);
      assertEquals(2, changes.size());
      assertEquals(2, a.activeCount());
      // the strongest ratios win: factory loss (100/10) and sector focus (100/24)
      assertTrue(a.active(Protocol.RELOCATION));
      assertTrue(a.active(Protocol.REDISTRIBUTION));
      assertEquals(0.0F, a.pressure(Tactic.FACTORY_LOSS), "an event trigger is spent when it fires");
   }

   @Test
   void lapsesThenCoolsDown() {
      UltronAdaptation a = new UltronAdaptation(3);
      a.observe(Tactic.FACTORY_LOSS, 20.0F);
      a.second(true);
      assertTrue(a.active(Protocol.RELOCATION));
      List<Change> changes = seconds(a, Protocol.RELOCATION.duration / UltronRules.TPS, true);
      assertTrue(changes.contains(new Change(Protocol.RELOCATION, false, Reason.LAPSED)));
      assertFalse(a.active(Protocol.RELOCATION));
      assertTrue(a.cooldown(Protocol.RELOCATION) > 0);
      a.observe(Tactic.FACTORY_LOSS, 50.0F);
      assertTrue(a.second(true).isEmpty(), "cooling down");
      seconds(a, UltronAdaptation.COOLDOWN / UltronRules.TPS, true);
      assertTrue(a.active(Protocol.RELOCATION) || a.pressure(Tactic.FACTORY_LOSS) < Protocol.RELOCATION.threshold);
   }

   @Test
   void sustainedPressureRenews() {
      UltronAdaptation a = new UltronAdaptation(3);
      a.observe(Tactic.AERIAL, 40.0F);
      a.second(true);
      assertTrue(a.active(Protocol.ANTI_AIR));
      List<Change> changes = new ArrayList<>();
      for (int s = 0; s < Protocol.ANTI_AIR.duration / UltronRules.TPS; s++) {
         a.observe(Tactic.AERIAL, 2.0F);
         changes.addAll(a.second(true));
      }
      assertTrue(changes.contains(new Change(Protocol.ANTI_AIR, true, Reason.RENEWED)), changes.toString());
      assertTrue(a.active(Protocol.ANTI_AIR));
      assertEquals(2, a.level(Protocol.ANTI_AIR));
   }

   @Test
   void meleeCountersTheDeflector() {
      UltronAdaptation a = new UltronAdaptation(3);
      a.observe(Tactic.RANGED, 40.0F);
      a.second(true);
      assertTrue(a.active(Protocol.DEFLECTOR));
      List<Change> changes = new ArrayList<>();
      for (int s = 0; s < 120 && a.active(Protocol.DEFLECTOR); s++) {
         a.observe(Tactic.RANGED, 1.0F);
         a.observe(Tactic.MELEE, 4.0F);
         changes.addAll(a.second(true));
      }
      assertTrue(changes.contains(new Change(Protocol.DEFLECTOR, false, Reason.COUNTERED)), changes.toString());
      assertTrue(a.cooldown(Protocol.DEFLECTOR) > 0);
   }

   @Test
   void stoppingTheTacticCountersAntiAir() {
      UltronAdaptation a = new UltronAdaptation(3);
      a.observe(Tactic.AERIAL, 40.0F);
      a.second(true);
      List<Change> changes = seconds(a, 100, true);
      assertTrue(changes.contains(new Change(Protocol.ANTI_AIR, false, Reason.COUNTERED)), changes.toString());
      assertFalse(a.active(Protocol.ANTI_AIR));
   }

   @Test
   void eventProtocolsAreNotCountered() {
      UltronAdaptation a = new UltronAdaptation(3);
      a.observe(Tactic.DEFENSE_HELD, 20.0F);
      a.second(true);
      assertTrue(a.active(Protocol.FORTIFY));
      List<Change> changes = seconds(a, UltronAdaptation.COUNTER_HOLD + 10, true);
      assertTrue(changes.isEmpty(), changes.toString());
      assertTrue(a.active(Protocol.FORTIFY));
   }

   @Test
   void completeEndsTheProtocol() {
      UltronAdaptation a = new UltronAdaptation(3);
      a.observe(Tactic.FACTORY_LOSS, 20.0F);
      a.second(true);
      assertEquals(new Change(Protocol.RELOCATION, false, Reason.LAPSED), a.complete(Protocol.RELOCATION));
      assertNull(a.complete(Protocol.RELOCATION));
      assertEquals(UltronAdaptation.COOLDOWN, a.cooldown(Protocol.RELOCATION));
   }

   @Test
   void restoreBringsBackSavedState() {
      UltronAdaptation a = new UltronAdaptation(3);
      a.restore(Protocol.DEFLECTOR, 400, 0, 2);
      a.setPressure(Tactic.RANGED, 50.0F);
      a.setActivations(3);
      assertTrue(a.active(Protocol.DEFLECTOR));
      assertEquals(2, a.level(Protocol.DEFLECTOR));
      assertEquals(3, a.activations());
      assertEquals(50.0F, a.pressure(Tactic.RANGED));
   }
}
