package dev.baranhan.viltrumitecore.worldevent.chitauri.war;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Pure adaptive director of the city war. A seed gives the hive a personality (how aggressive, cunning, greedy and
 * air-minded it is); every {@link WarRules#DIRECTOR_PERIOD} ticks it reads a {@link Snapshot} of the battle and scores
 * each {@link Doctrine}. The choice is a seeded soft-max over the scores, so the same seed and the same battle give the
 * same run, while different battles (where the heroes stand, how fast they kill, what they freed) push the hive to new
 * priorities. It also decides what the reinforcement budget buys and where the fronts go.
 */
public final class WarDirector {
   /** What the hive tries to do for the next period. */
   public enum Doctrine {
      CENTRE_ASSAULT("centre"),
      OUTSKIRT_RAID("outskirts"),
      AIR_DROP("air_drop"),
      ABDUCTION("abduction"),
      MULTI_FRONT("multi_front"),
      HOLD_GROUND("hold"),
      SURPRISE_REINFORCE("surprise"),
      COMMANDER_PUSH("commander");

      private final String key;

      Doctrine(String key) {
         this.key = key;
      }

      public String key() {
         return this.key;
      }
   }

   /** Seeded traits, each 0..1. */
   public record Personality(float aggression, float cunning, float greed, float air) {
   }

   /**
    * The battle as the director sees it. Rates are per minute; shares are 0..1.
    *
    * @param heroSpread share of known sectors with a hero in them (heroes spread out → 1)
    * @param centreShare share of heroes inside the two inner rings
    * @param coverShare share of heroes standing under a roof or inside a building
    * @param efficiency how well the squads trade: hero damage dealt per loss, 0..1
    */
   public record Snapshot(int players, int airborne, float coverShare, float killsPerMinute, int sectorsKnown, int sectorsHeld, int sectorsLiberated,
      int captivesHeld, int captivesFreed, int citizensSeen, float efficiency, float heroSpread, float centreShare, int budgetLeft, int commandersAlive,
      int phase) {
   }

   /** What reinforcements to buy next (counts; the server still applies the live caps). */
   public record Mix(int captors, int heavies, int interceptors, int sieges, int commanders) {
      public int cost() {
         return this.captors * WarRules.COST_CAPTOR + this.heavies * WarRules.COST_HEAVY + this.interceptors * WarRules.COST_INTERCEPTOR
            + this.sieges * WarRules.COST_SIEGE + this.commanders * WarRules.COST_COMMANDER;
      }

      public int total() {
         return this.captors + this.heavies + this.interceptors + this.sieges + this.commanders;
      }
   }

   public record Plan(Doctrine doctrine, Mix mix, int fronts, float[] scores) {
   }

   private static final Doctrine[] ALL = Doctrine.values();

   private WarDirector() {
   }

   public static Personality personality(long seed) {
      SplittableRandom r = new SplittableRandom(seed ^ 0x5DEECE66DL);
      return new Personality(trait(r), trait(r), trait(r), trait(r));
   }

   private static float trait(SplittableRandom r) {
      // two draws averaged: most hives are mixed, a few are extreme
      return (float)((r.nextDouble() + r.nextDouble()) / 2.0);
   }

   /** Utility of every doctrine (index = ordinal). */
   public static float[] scores(Personality p, Snapshot s, Doctrine previous, int repeats) {
      float players = Math.max(1, s.players());
      float air = s.airborne() / players;
      float kills = clamp01(s.killsPerMinute() / 14.0F);
      float losing = clamp01(s.sectorsLiberated() * 0.25F + kills * 0.6F + s.captivesFreed() * 0.08F);
      float citizens = clamp01(s.citizensSeen() / 10.0F);
      float freed = s.captivesFreed() / (float)Math.max(1, s.captivesFreed() + s.captivesHeld());
      float unknown = s.sectorsKnown() <= 0 ? 1.0F : clamp01(1.0F - s.sectorsHeld() / (float)s.sectorsKnown());
      float budget = s.budgetLeft() > 0 ? 1.0F : 0.0F;
      float[] u = new float[ALL.length];
      u[Doctrine.CENTRE_ASSAULT.ordinal()] = 0.25F + 0.55F * p.aggression() + 0.6F * s.centreShare() - 0.25F * air;
      u[Doctrine.OUTSKIRT_RAID.ordinal()] = 0.2F + 0.5F * p.cunning() + 0.55F * (1.0F - s.heroSpread()) * (1.0F - s.centreShare() * 0.3F) + 0.2F * unknown;
      u[Doctrine.AIR_DROP.ordinal()] = 0.15F + 0.55F * p.air() + 0.35F * unknown + 0.3F * (1.0F - air) - 0.15F * s.phase() / 3.0F;
      u[Doctrine.ABDUCTION.ordinal()] = 0.05F + 0.6F * p.greed() + 0.6F * citizens - 0.55F * freed;
      u[Doctrine.MULTI_FRONT.ordinal()] = 0.1F + 0.45F * p.cunning() + 0.45F / players + 0.3F * (1.0F - s.heroSpread()) + 0.15F * Math.min(3, s.phase());
      u[Doctrine.HOLD_GROUND.ordinal()] = 0.05F + 0.18F * Math.min(4, s.sectorsHeld()) + 0.45F * losing * (s.sectorsHeld() > 0 ? 1.0F : 0.2F);
      u[Doctrine.SURPRISE_REINFORCE.ordinal()] = budget * (0.05F + 0.75F * losing + 0.2F * p.cunning());
      u[Doctrine.COMMANDER_PUSH.ordinal()] = (s.commandersAlive() >= WarRules.MAX_COMMANDERS ? 0.0F : 1.0F) * budget
         * (0.05F + 0.6F * (1.0F - s.efficiency()) + 0.25F * p.aggression() + 0.1F * Math.min(3, s.phase()));
      if (previous != null) {
         // keep a plan long enough to read, but never let one doctrine own the whole war
         u[previous.ordinal()] += repeats <= 1 ? 0.15F : -0.22F * (repeats - 1);
      }
      return u;
   }

   /** Picks the next plan. {@code roll} comes from the run's seeded stream (reproducible). */
   public static Plan decide(Personality p, Snapshot s, Doctrine previous, int repeats, SplittableRandom roll) {
      return decide(p, s, previous, repeats, roll, null);
   }

   /**
    * Same, with a learned penalty per doctrine (index = ordinal): doctrines under which the hive lost sectors or bled
    * squads, in this run or remembered from earlier ones, score lower.
    */
   public static Plan decide(Personality p, Snapshot s, Doctrine previous, int repeats, SplittableRandom roll, float[] bias) {
      float[] u = scores(p, s, previous, repeats);
      if (bias != null) {
         for (int i = 0; i < Math.min(u.length, bias.length); i++) {
            u[i] -= bias[i];
         }
      }
      Doctrine d = softmax(u, 0.18F, roll.nextDouble());
      return new Plan(d, mix(d, s, p), fronts(d, s), u);
   }

   static Doctrine softmax(float[] u, float temperature, double roll) {
      float max = Float.NEGATIVE_INFINITY;
      for (float v : u) {
         max = Math.max(max, v);
      }
      double[] w = new double[u.length];
      double sum = 0.0;
      for (int i = 0; i < u.length; i++) {
         w[i] = Math.exp((u[i] - max) / temperature);
         sum += w[i];
      }
      double pick = roll * sum;
      for (int i = 0; i < u.length; i++) {
         pick -= w[i];
         if (pick <= 0.0) {
            return ALL[i];
         }
      }
      return ALL[u.length - 1];
   }

   /** Reinforcements for the plan, trimmed to the budget in priority order. */
   public static Mix mix(Doctrine d, Snapshot s, Personality p) {
      int interceptors = s.airborne() > 0 ? Math.min(WarRules.MAX_INTERCEPTORS, 1 + s.airborne()) : d == Doctrine.AIR_DROP ? 1 : 0;
      int heavies = s.killsPerMinute() > 8.0F ? 2 : d == Doctrine.CENTRE_ASSAULT || d == Doctrine.HOLD_GROUND ? 1 : 0;
      int captors = d == Doctrine.ABDUCTION ? 2 + (s.citizensSeen() > 6 ? 1 : 0) : s.citizensSeen() > 3 && p.greed() > 0.4F ? 1 : 0;
      int sieges = s.coverShare() > 0.4F || d == Doctrine.HOLD_GROUND && s.phase() >= 2 || d == Doctrine.CENTRE_ASSAULT && s.phase() >= 3 ? 1 : 0;
      int commanders = d == Doctrine.COMMANDER_PUSH || d == Doctrine.MULTI_FRONT && s.players() >= 2 ? 1 : 0;
      if (d == Doctrine.SURPRISE_REINFORCE) {
         heavies = Math.max(heavies, 1);
         interceptors = Math.max(interceptors, 1);
      }
      int left = Math.max(0, s.budgetLeft());
      int[] n = {commanders, captors, interceptors, heavies, sieges};
      int[] cost = {WarRules.COST_COMMANDER, WarRules.COST_CAPTOR, WarRules.COST_INTERCEPTOR, WarRules.COST_HEAVY, WarRules.COST_SIEGE};
      // the doctrine's own unit is bought first
      int[] order = switch (d) {
         case ABDUCTION -> new int[]{1, 2, 3, 0, 4};
         case AIR_DROP -> new int[]{2, 1, 3, 0, 4};
         case COMMANDER_PUSH, MULTI_FRONT -> new int[]{0, 3, 2, 1, 4};
         case HOLD_GROUND -> new int[]{4, 3, 2, 0, 1};
         default -> new int[]{3, 2, 0, 1, 4};
      };
      int[] got = new int[5];
      for (int idx : order) {
         int take = Math.min(n[idx], left / cost[idx]);
         got[idx] = take;
         left -= take * cost[idx];
      }
      return new Mix(got[1], got[3], got[2], got[4], got[0]);
   }

   public static int fronts(Doctrine d, Snapshot s) {
      return switch (d) {
         case MULTI_FRONT -> Math.min(3, 1 + Math.max(1, s.players()));
         case OUTSKIRT_RAID, AIR_DROP -> 2;
         default -> 1;
      };
   }

   /**
    * Target sectors of a plan, best first. Unknown sectors (centre column not loaded) are never targets: the hive only
    * goes where the world is there.
    */
   public static List<Integer> targets(List<WarSector> sectors, Doctrine d, int fronts, SplittableRandom roll) {
      List<WarSector> known = new ArrayList<>();
      for (WarSector s : sectors) {
         if (s.known()) {
            known.add(s);
         }
      }
      if (known.isEmpty()) {
         return List.of();
      }
      Comparator<WarSector> by = switch (d) {
         case CENTRE_ASSAULT -> Comparator.comparingDouble(s -> s.ring - s.heroes * 2.0 - s.citizens * 0.1);
         case OUTSKIRT_RAID -> Comparator.comparingDouble(s -> -s.ring * 2.0 + s.heroes * 4.0 - s.value());
         case AIR_DROP -> Comparator.comparingDouble(s -> (s.state == WarSector.State.UNKNOWN || s.state == WarSector.State.SCOUTED ? 0 : 3) - s.ring * 0.8
            + s.heroes * 2.0);
         case ABDUCTION -> Comparator.comparingDouble(s -> -s.citizens * 1.5 + s.heroes * 3.0 + s.ring * 0.2);
         case HOLD_GROUND -> Comparator.comparingDouble(s -> (s.state == WarSector.State.HELD ? 0 : s.state == WarSector.State.CONTESTED ? 1 : 4)
            - s.heroes * 0.5);
         case SURPRISE_REINFORCE, COMMANDER_PUSH -> Comparator.comparingDouble(s -> (s.state == WarSector.State.CONTESTED ? 0 : 2) - s.heroes * 1.5 + s.ring
            * 0.1);
         case MULTI_FRONT -> Comparator.comparingDouble(s -> -s.value() + s.heroes * 0.5);
      };
      known.sort(by.thenComparingDouble(s -> s.id));
      List<Integer> out = new ArrayList<>();
      if (d == Doctrine.MULTI_FRONT || fronts > 1) {
         // fronts far apart: take the best, then the best sector at least 70 degrees away from every chosen one
         double cx = 0.0, cz = 0.0;
         for (WarSector s : known) {
            cx += s.x;
            cz += s.z;
         }
         cx /= known.size();
         cz /= known.size();
         int start = Math.min(known.size() - 1, roll.nextInt(Math.min(2, known.size())));
         out.add(known.get(start).id);
         for (WarSector s : known) {
            if (out.size() >= fronts) {
               break;
            }
            if (out.contains(s.id)) {
               continue;
            }
            boolean apart = true;
            for (int id : out) {
               WarSector o = sectors.get(id);
               double a1 = Math.atan2(s.z - cz, s.x - cx);
               double a2 = Math.atan2(o.z - cz, o.x - cx);
               double diff = Math.abs(Math.atan2(Math.sin(a1 - a2), Math.cos(a1 - a2)));
               if (diff < Math.toRadians(70.0) && Math.hypot(s.x - o.x, s.z - o.z) < WarRules.CELL * 2.2) {
                  apart = false;
               }
            }
            if (apart) {
               out.add(s.id);
            }
         }
         return out;
      }
      int pick = known.size() > 2 && roll.nextDouble() < 0.25 ? 1 : 0;
      out.add(known.get(pick).id);
      if (known.size() > 1) {
         out.add(known.get(pick == 0 ? 1 : 0).id);
      }
      return out;
   }

   static float clamp01(float v) {
      return Math.max(0.0F, Math.min(1.0F, v));
   }
}
