package dev.baranhan.viltrumitecore.worldevent.ultron;

import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.worldevent.ultron.UltronRoles.Needs;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronRoles.Unit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UltronRolesTest {
   private static List<Unit> army(int drones, int sentries, int brutes, int interceptors, int engineers) {
      List<Unit> out = new ArrayList<>();
      int id = 0;
      int[] counts = {drones, sentries, brutes, interceptors, engineers};
      for (UltronUnitKind kind : UltronUnitKind.values()) {
         for (int i = 0; i < counts[kind.ordinal()]; i++) {
            out.add(new Unit(id++, kind, UltronRole.PATROL));
         }
      }
      return out;
   }

   private static List<Unit> apply(List<Unit> units, Map<Integer, UltronRole> roles) {
      List<Unit> out = new ArrayList<>();
      for (Unit u : units) {
         out.add(new Unit(u.id(), u.kind(), roles.get(u.id())));
      }
      return out;
   }

   private static Map<UltronRole, Integer> count(List<Unit> units, UltronUnitKind kind) {
      Map<UltronRole, Integer> c = new EnumMap<>(UltronRole.class);
      for (Unit u : units) {
         if (kind == null || u.kind() == kind) {
            c.merge(u.role(), 1, Integer::sum);
         }
      }
      return c;
   }

   private static final Needs SIEGE = new Needs(UltronDoctrine.SIEGE, 2, false, 1, false, false, 0, true);

   @Test
   void everyUnitGetsARole() {
      List<Unit> units = army(4, 8, 3, 3, 3);
      Map<Integer, UltronRole> roles = UltronRoles.assign(units, SIEGE);
      assertEquals(units.size(), roles.size());
      for (Unit u : units) {
         assertNotNull(roles.get(u.id()));
      }
   }

   @Test
   void sameNeedsNoSwitches() {
      List<Unit> units = apply(army(4, 8, 3, 3, 3), UltronRoles.assign(army(4, 8, 3, 3, 3), SIEGE));
      Map<Integer, UltronRole> again = UltronRoles.assign(units, SIEGE);
      for (Unit u : units) {
         assertEquals(u.role(), again.get(u.id()), "unit " + u.id() + " kept its role");
      }
   }

   @Test
   void fewSwitchesWhenNeedsShift() {
      List<Unit> units = apply(army(4, 8, 3, 3, 3), UltronRoles.assign(army(4, 8, 3, 3, 3), SIEGE));
      Needs fortify = new Needs(UltronDoctrine.SIEGE, 2, true, 1, false, false, 0, true);
      Map<Integer, UltronRole> next = UltronRoles.assign(units, fortify);
      int switched = 0;
      for (Unit u : units) {
         if (u.role() != next.get(u.id())) {
            switched++;
         }
      }
      int guardsBefore = count(units, UltronUnitKind.SENTRY).getOrDefault(UltronRole.GUARD, 0);
      int guardsAfter = count(apply(units, next), UltronUnitKind.SENTRY).getOrDefault(UltronRole.GUARD, 0);
      assertTrue(guardsAfter > guardsBefore, "a fortified node pulls extra guards");
      assertEquals(guardsAfter - guardsBefore, switched, "only the units that change jobs switch");
   }

   @Test
   void peacefulPhaseNeverAssaults() {
      Needs calm = new Needs(UltronDoctrine.EXPANSION, 1, false, 2, false, false, 0, false);
      Map<UltronRole, Integer> c = count(apply(army(4, 6, 2, 2, 2), UltronRoles.assign(army(4, 6, 2, 2, 2), calm)), null);
      assertFalse(c.containsKey(UltronRole.ASSAULT), c.toString());
      assertTrue(c.getOrDefault(UltronRole.SCOUT, 0) >= 2, "drones scout");
      assertTrue(c.getOrDefault(UltronRole.ESCORT, 0) >= 1, "sentries escort the builders");
      assertEquals(2, c.getOrDefault(UltronRole.REPAIR, 0), "engineers build and repair");
   }

   @Test
   void antiAirSendsFlyersAfterFlyers() {
      Needs air = new Needs(UltronDoctrine.SIEGE, 2, false, 0, true, true, 0, true);
      List<Unit> units = apply(army(4, 4, 1, 3, 1), UltronRoles.assign(army(4, 4, 1, 3, 1), air));
      assertEquals(3, count(units, UltronUnitKind.INTERCEPTOR).getOrDefault(UltronRole.INTERCEPT, 0));
      assertEquals(2, count(units, UltronUnitKind.DRONE).getOrDefault(UltronRole.INTERCEPT, 0));
   }

   @Test
   void assimilationSendsAnEngineerToCapture() {
      Needs capture = new Needs(UltronDoctrine.ASSIMILATION, 2, false, 0, false, false, 2, true);
      List<Unit> units = apply(army(2, 4, 1, 1, 3), UltronRoles.assign(army(2, 4, 1, 1, 3), capture));
      assertEquals(1, count(units, UltronUnitKind.ENGINEER).getOrDefault(UltronRole.CAPTURE, 0));
      assertEquals(2, count(units, UltronUnitKind.ENGINEER).getOrDefault(UltronRole.REPAIR, 0));
   }

   @Test
   void huntSendsEveryDroneScouting() {
      Needs hunt = new Needs(UltronDoctrine.HUNT, 2, false, 0, false, false, 0, true);
      List<Unit> units = apply(army(5, 2, 0, 0, 0), UltronRoles.assign(army(5, 2, 0, 0, 0), hunt));
      assertEquals(5, count(units, UltronUnitKind.DRONE).getOrDefault(UltronRole.SCOUT, 0));
   }
}
