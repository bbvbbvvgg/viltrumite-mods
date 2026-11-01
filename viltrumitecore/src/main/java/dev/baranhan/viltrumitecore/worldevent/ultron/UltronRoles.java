package dev.baranhan.viltrumitecore.worldevent.ultron;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Role coordinator (pure, unit-tested): from the doctrine and the network's needs it decides how
 * many machines of each kind take each role, then hands the roles out with as few switches as
 * possible (a unit keeps a role that is still wanted; the rest fill the gaps in id order).
 */
public final class UltronRoles {
   public record Unit(int id, UltronUnitKind kind, UltronRole role) {
   }

   /**
    * guardPosts: foundries + core standing; fortify: a fortified node needs extra guards; buildJobs: sites under
    * construction or disabled nodes to reboot; aerialThreat: flying players seen recently; antiAir: the protocol runs;
    * captureTargets: golems / suits in an uplink's reach; hostile: the phase fights on sight.
    */
   public record Needs(UltronDoctrine doctrine, int guardPosts, boolean fortify, int buildJobs, boolean aerialThreat, boolean antiAir, int captureTargets,
      boolean hostile) {
   }

   private UltronRoles() {
   }

   public static Map<Integer, UltronRole> assign(List<Unit> units, Needs needs) {
      Map<UltronUnitKind, List<Unit>> byKind = new HashMap<>();
      for (Unit u : units) {
         byKind.computeIfAbsent(u.kind, k -> new ArrayList<>()).add(u);
      }
      Map<Integer, UltronRole> out = new HashMap<>();
      for (Map.Entry<UltronUnitKind, List<Unit>> e : byKind.entrySet()) {
         List<Unit> list = e.getValue();
         list.sort(Comparator.comparingInt(Unit::id));
         fill(list, demand(e.getKey(), list.size(), needs), out);
      }
      return out;
   }

   /** Ordered role demand for {@code n} units of a kind; the counts add up to n. */
   static LinkedHashMap<UltronRole, Integer> demand(UltronUnitKind kind, int n, Needs needs) {
      LinkedHashMap<UltronRole, Integer> d = new LinkedHashMap<>();
      int left = n;
      switch (kind) {
         case ENGINEER -> {
            int capture = needs.doctrine == UltronDoctrine.ASSIMILATION && needs.captureTargets > 0 && n > 1 ? 1 : 0;
            left = put(d, UltronRole.CAPTURE, capture, left);
            put(d, UltronRole.REPAIR, left, left);
         }
         case DRONE -> {
            int intercept = needs.antiAir && needs.aerialThreat ? n / 2 : 0;
            left = put(d, UltronRole.INTERCEPT, intercept, left);
            int scouts = needs.doctrine == UltronDoctrine.HUNT ? left : Math.max(Math.min(2, left), left / 2 + left % 2);
            left = put(d, UltronRole.SCOUT, scouts, left);
            put(d, UltronRole.PATROL, left, left);
         }
         case INTERCEPTOR -> {
            if (needs.aerialThreat || needs.antiAir) {
               put(d, UltronRole.INTERCEPT, left, left);
            } else if (needs.hostile) {
               int assault = (left + 1) / 2;
               left = put(d, UltronRole.ASSAULT, assault, left);
               put(d, UltronRole.PATROL, left, left);
            } else {
               put(d, UltronRole.PATROL, left, left);
            }
         }
         case SENTRY -> {
            float guardShare = needs.doctrine == UltronDoctrine.EXPANSION || !needs.hostile ? 0.6F : 0.5F;
            int guards = Math.min(Math.round(n * guardShare), needs.guardPosts * (needs.fortify ? 2 : 1) + (needs.fortify ? 1 : 0));
            left = put(d, UltronRole.GUARD, guards, left);
            int escorts = Math.min(Math.min(2, needs.buildJobs), needs.doctrine == UltronDoctrine.EXPANSION ? left : left / 3);
            left = put(d, UltronRole.ESCORT, escorts, left);
            put(d, needs.hostile && needs.doctrine != UltronDoctrine.EXPANSION ? UltronRole.ASSAULT : UltronRole.PATROL, left, left);
         }
         case BRUTE -> {
            if (!needs.hostile || needs.doctrine == UltronDoctrine.EXPANSION && needs.guardPosts > 0) {
               int guards = Math.min(left, Math.max(1, needs.guardPosts));
               left = put(d, UltronRole.GUARD, guards, left);
            }
            put(d, needs.hostile ? UltronRole.ASSAULT : UltronRole.GUARD, left, left);
         }
      }
      return d;
   }

   private static int put(LinkedHashMap<UltronRole, Integer> d, UltronRole role, int count, int left) {
      int c = Math.max(0, Math.min(count, left));
      if (c > 0) {
         d.merge(role, c, Integer::sum);
      }
      return left - c;
   }

   private static void fill(List<Unit> units, LinkedHashMap<UltronRole, Integer> demand, Map<Integer, UltronRole> out) {
      List<Unit> remaining = new ArrayList<>(units);
      Map<UltronRole, Integer> slots = new LinkedHashMap<>(demand);
      // keep: units already in a wanted role
      for (Map.Entry<UltronRole, Integer> e : demand.entrySet()) {
         int room = e.getValue();
         for (int i = 0; i < remaining.size() && room > 0; ) {
            Unit u = remaining.get(i);
            if (u.role == e.getKey()) {
               out.put(u.id, u.role);
               remaining.remove(i);
               room--;
            } else {
               i++;
            }
         }
         slots.put(e.getKey(), room);
      }
      // fill the gaps in demand order
      for (Map.Entry<UltronRole, Integer> e : slots.entrySet()) {
         for (int k = 0; k < e.getValue() && !remaining.isEmpty(); k++) {
            out.put(remaining.remove(0).id, e.getKey());
         }
      }
      for (Unit u : remaining) {
         out.put(u.id, u.role);
      }
   }
}
