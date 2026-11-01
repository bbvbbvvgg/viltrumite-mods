package dev.baranhan.viltrumitecore.worldevent.ultron;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * One seeded variant of the uprising (pure, reproducible: the same seed gives the same plan on any
 * machine). It decides much more than enemy counts: the variant (story and priorities), the node
 * layout and the order of the node kinds, the reserve sites for relocation, the unit composition,
 * the doctrine weights, the assault routes, the attack style, Ultron's boss style and the finale
 * rule (where he assembles, how long the upload takes, how many protocols he may run).
 * Site positions are polar fractions of the area radius around the area centre; the director maps
 * them onto the ground (or onto city districts).
 */
public record UltronScenario(long seed, Variant variant, Layout layout, List<Site> sites, float[] composition, float[] doctrineWeights,
   double[] routes, AttackStyle style, BossStyle boss, FinaleRule finale, int uploadTicks, float timeScale, int maxProtocols) {

   public enum Variant {
      QUIET_INFECTION("quiet_infection"),
      FACTORY_WAR("factory_war"),
      HUNTER("hunter"),
      SINGULARITY("singularity");

      private final String key;

      Variant(String key) {
         this.key = key;
      }

      public String key() {
         return this.key;
      }
   }

   public enum Layout {
      RING,
      CHAIN,
      CLUSTER,
      SCATTER
   }

   /** How assaults are shaped: many small groups, two heavy columns, hunter squads on intel, or a rotation of all three. */
   public enum AttackStyle {
      INFILTRATION,
      COLUMNS,
      PURSUIT,
      ADAPTIVE
   }

   /** Ultron's own fighting character in the finale. */
   public enum BossStyle {
      TACTICIAN,
      JUGGERNAUT,
      DUELIST,
      ASCENDANT
   }

   /** Where Ultron assembles for the finale. */
   public enum FinaleRule {
      /** At the strongest uplink (the core when none is left). */
      UPLINK,
      /** At the foundry with the most hull (the core when none is left). */
      FOUNDRY,
      /** Near the most dangerous player the machines have seen. */
      HUNT,
      /** At the core, or the strongest node standing. */
      CORE
   }

   /** wave 0: the core (stands from the start), 1: raised during the spread, 2: reserve for relocation. */
   public record Site(int index, UltronNodeKind kind, double angle, double radius, int wave) {
   }

   public static final int MAX_SITES = 10;

   public float composition(UltronUnitKind kind) {
      return this.composition[kind.ordinal()];
   }

   public float doctrineWeight(UltronDoctrine doctrine) {
      return this.doctrineWeights[doctrine.ordinal()];
   }

   /** Sites raised during the spread (wave 1). */
   public int spreadTarget() {
      int n = 0;
      for (Site s : this.sites) {
         if (s.wave == 1) {
            n++;
         }
      }
      return n;
   }

   /** Pure: the scenario of a seed. */
   public static UltronScenario of(long seed) {
      Random r = new Random(seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L);
      Variant variant = Variant.values()[Math.floorMod(r.nextInt(), Variant.values().length)];
      Layout layout = pickLayout(variant, r);
      List<UltronNodeKind> plan = new ArrayList<>(plan(variant));
      Collections.shuffle(plan, r);
      double a0 = r.nextDouble() * Math.PI * 2.0;
      List<double[]> spots = spots(layout, plan.size() + 1 + 2, a0, r);
      List<Site> sites = new ArrayList<>();
      sites.add(new Site(0, UltronNodeKind.CORE, spots.get(0)[0], spots.get(0)[1], 0));
      for (int i = 0; i < plan.size(); i++) {
         double[] p = spots.get(i + 1);
         sites.add(new Site(i + 1, plan.get(i), p[0], p[1], 1));
      }
      UltronNodeKind[] reserve = {UltronNodeKind.FOUNDRY, reserveKind(variant)};
      for (int i = 0; i < reserve.length; i++) {
         double[] p = spots.get(plan.size() + 1 + i);
         sites.add(new Site(sites.size(), reserve[i], p[0], p[1], 2));
      }
      float[] composition = jitter(baseComposition(variant), r, 0.15F);
      float[] doctrines = jitter(baseDoctrines(variant), r, 0.2F);
      int routeCount = variant == Variant.FACTORY_WAR ? 2 : 2 + r.nextInt(3);
      double[] routes = new double[routeCount];
      for (int i = 0; i < routeCount; i++) {
         routes[i] = wrap(a0 + Math.PI + i * (Math.PI * 2.0 / routeCount) + (r.nextDouble() - 0.5) * 0.6);
      }
      AttackStyle style = r.nextFloat() < 0.25F ? AttackStyle.values()[r.nextInt(AttackStyle.values().length)] : defaultStyle(variant);
      BossStyle boss = defaultBoss(variant);
      FinaleRule finale = switch (variant) {
         case QUIET_INFECTION -> FinaleRule.UPLINK;
         case FACTORY_WAR -> FinaleRule.FOUNDRY;
         case HUNTER -> FinaleRule.HUNT;
         case SINGULARITY -> FinaleRule.CORE;
      };
      int uploadSeconds = switch (variant) {
         case QUIET_INFECTION -> 260;
         case FACTORY_WAR -> 300;
         case HUNTER -> 240;
         case SINGULARITY -> 210;
      } + r.nextInt(41) - 20;
      float timeScale = 0.9F + r.nextFloat() * 0.2F;
      int maxProtocols = variant == Variant.SINGULARITY ? 4 : 3;
      return new UltronScenario(seed, variant, layout, List.copyOf(sites), composition, doctrines, routes, style, boss, finale, uploadSeconds * UltronRules.TPS,
         timeScale, maxProtocols);
   }

   static List<UltronNodeKind> plan(Variant v) {
      return switch (v) {
         case QUIET_INFECTION -> List.of(UltronNodeKind.UPLINK, UltronNodeKind.UPLINK, UltronNodeKind.UPLINK, UltronNodeKind.SENSOR, UltronNodeKind.REPAIR,
            UltronNodeKind.FOUNDRY, UltronNodeKind.BULWARK);
         case FACTORY_WAR -> List.of(UltronNodeKind.FOUNDRY, UltronNodeKind.FOUNDRY, UltronNodeKind.FOUNDRY, UltronNodeKind.BULWARK, UltronNodeKind.REPAIR,
            UltronNodeKind.SENSOR, UltronNodeKind.UPLINK);
         case HUNTER -> List.of(UltronNodeKind.SENSOR, UltronNodeKind.SENSOR, UltronNodeKind.FOUNDRY, UltronNodeKind.UPLINK, UltronNodeKind.REPAIR,
            UltronNodeKind.BULWARK);
         case SINGULARITY -> List.of(UltronNodeKind.UPLINK, UltronNodeKind.UPLINK, UltronNodeKind.SENSOR, UltronNodeKind.SENSOR, UltronNodeKind.FOUNDRY,
            UltronNodeKind.BULWARK, UltronNodeKind.REPAIR);
      };
   }

   private static UltronNodeKind reserveKind(Variant v) {
      return switch (v) {
         case QUIET_INFECTION -> UltronNodeKind.UPLINK;
         case FACTORY_WAR -> UltronNodeKind.FOUNDRY;
         case HUNTER -> UltronNodeKind.SENSOR;
         case SINGULARITY -> UltronNodeKind.BULWARK;
      };
   }

   private static Layout pickLayout(Variant v, Random r) {
      // weights RING, CHAIN, CLUSTER, SCATTER per variant
      int[] w = switch (v) {
         case QUIET_INFECTION -> new int[]{2, 2, 1, 4};
         case FACTORY_WAR -> new int[]{2, 1, 4, 1};
         case HUNTER -> new int[]{3, 3, 1, 2};
         case SINGULARITY -> new int[]{3, 2, 2, 2};
      };
      int total = 0;
      for (int x : w) {
         total += x;
      }
      int roll = r.nextInt(total);
      for (int i = 0; i < w.length; i++) {
         roll -= w[i];
         if (roll < 0) {
            return Layout.values()[i];
         }
      }
      return Layout.RING;
   }

   private static float[] baseComposition(Variant v) {
      // DRONE, SENTRY, BRUTE, INTERCEPTOR, ENGINEER
      return switch (v) {
         case QUIET_INFECTION -> new float[]{3.0F, 4.0F, 1.0F, 1.0F, 3.0F};
         case FACTORY_WAR -> new float[]{2.0F, 5.0F, 3.0F, 1.0F, 2.0F};
         case HUNTER -> new float[]{4.0F, 3.0F, 1.0F, 3.0F, 1.5F};
         case SINGULARITY -> new float[]{3.0F, 4.0F, 2.0F, 2.0F, 2.0F};
      };
   }

   private static float[] baseDoctrines(Variant v) {
      // EXPANSION, SIEGE, HUNT, ASSIMILATION
      return switch (v) {
         case QUIET_INFECTION -> new float[]{3.0F, 1.0F, 1.0F, 4.0F};
         case FACTORY_WAR -> new float[]{2.0F, 4.0F, 1.0F, 1.0F};
         case HUNTER -> new float[]{1.0F, 1.5F, 5.0F, 1.0F};
         case SINGULARITY -> new float[]{2.0F, 2.0F, 2.0F, 2.0F};
      };
   }

   private static AttackStyle defaultStyle(Variant v) {
      return switch (v) {
         case QUIET_INFECTION -> AttackStyle.INFILTRATION;
         case FACTORY_WAR -> AttackStyle.COLUMNS;
         case HUNTER -> AttackStyle.PURSUIT;
         case SINGULARITY -> AttackStyle.ADAPTIVE;
      };
   }

   private static BossStyle defaultBoss(Variant v) {
      return switch (v) {
         case QUIET_INFECTION -> BossStyle.TACTICIAN;
         case FACTORY_WAR -> BossStyle.JUGGERNAUT;
         case HUNTER -> BossStyle.DUELIST;
         case SINGULARITY -> BossStyle.ASCENDANT;
      };
   }

   private static float[] jitter(float[] base, Random r, float amount) {
      float[] out = new float[base.length];
      for (int i = 0; i < base.length; i++) {
         out[i] = base[i] * (1.0F - amount + r.nextFloat() * 2.0F * amount);
      }
      return out;
   }

   /** Polar spots (angle, radius fraction) for the layout: the first is the core. Spots keep a gap (the best of many tries where the layout is random). */
   static List<double[]> spots(Layout layout, int count, double a0, Random r) {
      List<double[]> out = new ArrayList<>();
      switch (layout) {
         case RING -> {
            out.add(new double[]{wrap(a0 + r.nextDouble() * Math.PI * 2.0), 0.08 + r.nextDouble() * 0.12});
            int ring = count - 1;
            for (int i = 0; i < ring; i++) {
               double a = a0 + i * (Math.PI * 2.0 / ring) + (r.nextDouble() - 0.5) * 0.35;
               double rad = 0.58 + r.nextDouble() * 0.3;
               out.add(new double[]{wrap(a), rad});
            }
         }
         case CHAIN -> {
            // a curved line across the area through the centre; the core sits near the middle
            double wave = 0.18 + r.nextDouble() * 0.12;
            for (int i = 0; i < count; i++) {
               int k = i == 0 ? (count - 1) / 2 : i <= (count - 1) / 2 ? i - 1 : i;
               double t = -0.88 + 1.76 * k / Math.max(1, count - 1);
               double side = Math.sin(t * Math.PI * 1.3) * wave;
               double x = t;
               double z = side;
               double ca = Math.cos(a0);
               double sa = Math.sin(a0);
               double wx = x * ca - z * sa;
               double wz = x * sa + z * ca;
               out.add(new double[]{wrap(Math.atan2(wz, wx)), Math.min(0.92, Math.sqrt(wx * wx + wz * wz))});
            }
         }
         case CLUSTER -> {
            double ca = a0;
            double cr = 0.42 + r.nextDouble() * 0.12;
            double cx = Math.cos(ca) * cr;
            double cz = Math.sin(ca) * cr;
            out.add(polar(cx * 0.8, cz * 0.8));
            for (int i = 1; i < count; i++) {
               double[] best = null;
               double bestGap = -1.0;
               for (int tries = 0; tries < 60 && bestGap < 0.24; tries++) {
                  double a = r.nextDouble() * Math.PI * 2.0;
                  double d = 0.2 + r.nextDouble() * 0.4;
                  double[] p = polar(cx + Math.cos(a) * d, cz + Math.sin(a) * d);
                  double gap = gap(out, p);
                  if (p[1] <= 0.95 && gap > bestGap) {
                     bestGap = gap;
                     best = p;
                  }
               }
               out.add(best != null ? best : polar(cx * 0.5, cz * 0.5));
            }
         }
         case SCATTER -> {
            out.add(new double[]{wrap(r.nextDouble() * Math.PI * 2.0), r.nextDouble() * 0.25});
            for (int i = 1; i < count; i++) {
               double[] best = null;
               double bestGap = -1.0;
               for (int tries = 0; tries < 80 && bestGap < 0.3; tries++) {
                  double[] p = {r.nextDouble() * Math.PI * 2.0, 0.3 + r.nextDouble() * 0.62};
                  double gap = gap(out, p);
                  if (gap > bestGap) {
                     bestGap = gap;
                     best = p;
                  }
               }
               out.add(best);
            }
         }
      }
      return out;
   }

   private static double[] polar(double x, double z) {
      return new double[]{wrap(Math.atan2(z, x)), Math.sqrt(x * x + z * z)};
   }

   /** Smallest distance from {@code p} to the spots placed so far. */
   private static double gap(List<double[]> placed, double[] p) {
      double best = Double.MAX_VALUE;
      for (double[] q : placed) {
         best = Math.min(best, distance(p, q));
      }
      return best;
   }

   /** Distance between two polar spots in area-radius units. */
   public static double distance(double[] a, double[] b) {
      double ax = Math.cos(a[0]) * a[1];
      double az = Math.sin(a[0]) * a[1];
      double bx = Math.cos(b[0]) * b[1];
      double bz = Math.sin(b[0]) * b[1];
      return Math.hypot(ax - bx, az - bz);
   }

   static double wrap(double a) {
      double t = a % (Math.PI * 2.0);
      return t < 0 ? t + Math.PI * 2.0 : t;
   }
}
