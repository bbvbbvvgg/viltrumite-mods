package dev.baranhan.viltrumitecore.hero.ironman.legion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.phys.Vec3;

/**
 * Squad decisions of the coordinator (design §5), pure: who takes which
 * target in which role, who breaks off to defend Tony, who falls back, and
 * whether a heavy weapon is worth it.
 */
public final class LegionTactics {
   private LegionTactics() {
   }

   /** What a suit does this round. */
   public enum Stance {
      /** No target: formation slot for the order. */
      GUARD,
      /** Fighting its target. */
      ENGAGE,
      /** Damaged: fights from double range and higher up. */
      RETREAT,
      /** Critically damaged: goes back to Veronica. */
      RETURN
   }

   /** A suit as the coordinator sees it. */
   public record Suit(int slot, LegionProfile profile, Vec3 pos, float fraction, boolean available) {
   }

   /** A scored, eligible threat. */
   public record Target(int id, double score, float effectiveHealth, Vec3 pos, double ownerDistance, boolean onOwner, boolean flying) {
   }

   /** The coordinator's decision for one suit. */
   public record Assignment(int slot, int targetId, LegionRole role, Stance stance, boolean interrupt) {
      public boolean hasTarget() {
         return this.targetId >= 0;
      }
   }

   public static Stance health(float fraction) {
      if (fraction < LegionRules.RETURN_FRACTION) {
         return Stance.RETURN;
      }

      return fraction < LegionRules.RETREAT_FRACTION ? Stance.RETREAT : Stance.ENGAGE;
   }

   /**
    * One round of assignment. Targets need not be sorted. Every available suit
    * gets one entry, in the order of {@code suits}.
    */
   public static List<Assignment> assign(List<Suit> suits, List<Target> targets) {
      List<Target> sorted = new ArrayList<>(targets);
      sorted.sort(Comparator.comparingDouble((Target t) -> -t.score()));
      Map<Integer, Float> planned = new HashMap<>();
      Map<Integer, Assignment> out = new HashMap<>();
      List<Suit> free = new ArrayList<>();
      for (Suit suit : suits) {
         if (!suit.available()) {
            continue;
         }

         if (health(suit.fraction()) == Stance.RETURN) {
            out.put(suit.slot(), new Assignment(suit.slot(), -1, suit.profile().role(), Stance.RETURN, false));
         } else {
            free.add(suit);
         }
      }

      // 1. Somebody is on Tony: the nearest healthy suit breaks off (a guardian close by is preferred).
      for (Target target : sorted) {
         if (!target.onOwner() || free.isEmpty()) {
            continue;
         }

         Suit nearest = null;
         double best = Double.MAX_VALUE;
         for (Suit suit : free) {
            double d = suit.pos().distanceTo(target.pos());
            if (suit.profile().role() == LegionRole.GUARDIAN) {
               d *= 0.66;
            }

            if (health(suit.fraction()) == Stance.RETREAT) {
               d *= 2.0;
            }

            if (d < best) {
               best = d;
               nearest = suit;
            }
         }

         if (nearest != null) {
            free.remove(nearest);
            planned.merge(target.id(), nearest.profile().dps(), Float::sum);
            out.put(nearest.slot(), new Assignment(nearest.slot(), target.id(), nearest.profile().role(), health(nearest.fraction()), true));
         }

         break;
      }

      // 2. The rest spread over the board: no more planned damage on a target than it can take.
      free.sort(Comparator.comparingInt((Suit s) -> s.profile().role() == LegionRole.GUARDIAN ? 1 : 0));
      for (Suit suit : free) {
         Target choice = null;
         double best = Double.NEGATIVE_INFINITY;
         for (Target target : sorted) {
            if (suit.profile().role() == LegionRole.GUARDIAN && target.ownerDistance() > suit.profile().leash() + 4.0 && !target.onOwner()) {
               continue;
            }

            float already = planned.getOrDefault(target.id(), 0.0F);
            boolean saturated = already >= target.effectiveHealth() * LegionRules.FOCUS_CAP;
            double value = target.score() - suit.pos().distanceTo(target.pos()) * 1.5 - (saturated ? 500.0 : 0.0);
            if (target.flying() && suit.profile().role() == LegionRole.OVERWATCH) {
               value += 15.0;
            }

            if (value > best) {
               best = value;
               choice = target;
            }
         }

         if (choice == null) {
            out.put(suit.slot(), new Assignment(suit.slot(), -1, suit.profile().role(), Stance.GUARD, false));
         } else {
            planned.merge(choice.id(), suit.profile().dps(), Float::sum);
            out.put(suit.slot(), new Assignment(suit.slot(), choice.id(), suit.profile().role(), health(suit.fraction()), false));
         }
      }

      spreadRoles(suits, out);
      List<Assignment> result = new ArrayList<>();
      for (Suit suit : suits) {
         Assignment a = out.get(suit.slot());
         if (a != null) {
            result.add(a);
         }
      }

      return result;
   }

   /** Suits on the same target take different roles: front, flank, top (guardians stay guardians). */
   private static void spreadRoles(List<Suit> suits, Map<Integer, Assignment> out) {
      Map<Integer, Set<LegionRole>> taken = new HashMap<>();
      for (Suit suit : suits) {
         Assignment a = out.get(suit.slot());
         if (a == null || !a.hasTarget() || a.role() == LegionRole.GUARDIAN) {
            continue;
         }

         Set<LegionRole> roles = taken.computeIfAbsent(a.targetId(), k -> EnumSet.noneOf(LegionRole.class));
         LegionRole role = a.role();
         if (roles.contains(role)) {
            for (LegionRole next : new LegionRole[]{LegionRole.VANGUARD, LegionRole.FLANKER, LegionRole.OVERWATCH}) {
               if (!roles.contains(next)) {
                  role = next;
                  break;
               }
            }
         }

         roles.add(role);
         if (role != a.role()) {
            out.put(suit.slot(), new Assignment(a.slot(), a.targetId(), role, a.stance(), a.interrupt()));
         }
      }
   }

   /**
    * Heavy weapon check: not when a normal shot already finishes the target,
    * and no splash or beam next to Tony or an ally.
    */
   public static boolean heavyAllowed(LegionWeapon weapon, float targetEffectiveHealth, float normalShot, double allyToTarget) {
      if (!weapon.heavy()) {
         return true;
      }

      return targetEffectiveHealth > normalShot * LegionRules.OVERKILL_MARGIN && allyToTarget > LegionRules.SPLASH_SAFE_RADIUS;
   }

   /** Missile volley check: splash safety plus the overkill rule. */
   public static boolean missilesAllowed(float targetEffectiveHealth, float normalShot, double allyToTarget) {
      return targetEffectiveHealth > normalShot * LegionRules.OVERKILL_MARGIN && allyToTarget > LegionRules.SPLASH_SAFE_RADIUS;
   }
}
