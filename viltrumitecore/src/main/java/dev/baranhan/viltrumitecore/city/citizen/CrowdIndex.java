package dev.baranhan.viltrumitecore.city.citizen;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;

/**
 * Where the citizens of the city are (rebuilt every {@link #PERIOD} ticks, 8x8 column
 * buckets): who sees a neighbour run (panic spreads), who is close enough to chat, and how
 * crowded each flee destination already is (people spread out instead of all running to
 * the same corner). One pass over the citizen set; queries touch a few buckets.
 */
public final class CrowdIndex {
   public static final int PERIOD = 5;
   private static final int SHIFT = 3;
   private final Long2ObjectOpenHashMap<List<CitizenEntity>> buckets = new Long2ObjectOpenHashMap<>();
   private final Long2IntOpenHashMap claims = new Long2IntOpenHashMap();
   private final int[] byState = new int[CitizenState.values().length];

   private static long key(int bx, int bz) {
      return (long)bx << 32 | bz & 0xFFFFFFFFL;
   }

   /** Claim bucket of a destination: 4x4 columns. */
   public static long claimKey(int x, int z) {
      return key(x >> 2, z >> 2);
   }

   public void rebuild(Collection<CitizenEntity> citizens, long now) {
      for (List<CitizenEntity> l : this.buckets.values()) {
         l.clear();
      }

      this.claims.clear();
      java.util.Arrays.fill(this.byState, 0);
      for (CitizenEntity c : citizens) {
         if (c.isRemoved()) {
            continue;
         }

         long k = key(c.getBlockX() >> SHIFT, c.getBlockZ() >> SHIFT);
         List<CitizenEntity> l = this.buckets.get(k);
         if (l == null) {
            l = new ArrayList<>(8);
            this.buckets.put(k, l);
         }

         l.add(c);
         this.byState[c.citizenState().ordinal()]++;
         BlockPos t = c.fleeTarget;
         if (t != null) {
            this.claims.addTo(claimKey(t.getX(), t.getZ()), 1);
         }
      }

      if (now % 200 == 0) {
         this.buckets.values().removeIf(List::isEmpty);
      }
   }

   /** How many fleeing citizens already head for this 4x4 spot. */
   public int claims(int x, int z) {
      return this.claims.get(claimKey(x, z));
   }

   /** Citizens counted per state at the last rebuild (status, performance checks). */
   public int count(CitizenState s) {
      return this.byState[s.ordinal()];
   }

   /** The nearest other citizen within r that is panicking visibly (running, flinching, falling), or null. */
   @Nullable
   public CitizenEntity panickingNear(CitizenEntity self, double r) {
      return this.nearest(self, r, true);
   }

   /** The nearest other calm, standing citizen within r (a chat partner), or null. */
   @Nullable
   public CitizenEntity idleNear(CitizenEntity self, double r) {
      return this.nearest(self, r, false);
   }

   @Nullable
   private CitizenEntity nearest(CitizenEntity self, double r, boolean panic) {
      int bx0 = (int)Math.floor(self.getX() - r) >> SHIFT;
      int bx1 = (int)Math.floor(self.getX() + r) >> SHIFT;
      int bz0 = (int)Math.floor(self.getZ() - r) >> SHIFT;
      int bz1 = (int)Math.floor(self.getZ() + r) >> SHIFT;
      CitizenEntity best = null;
      double bestD = r * r;
      for (int bx = bx0; bx <= bx1; bx++) {
         for (int bz = bz0; bz <= bz1; bz++) {
            List<CitizenEntity> l = this.buckets.get(key(bx, bz));
            if (l == null) {
               continue;
            }

            for (CitizenEntity o : l) {
               if (o == self || o.isRemoved() || Math.abs(o.getY() - self.getY()) > 6.0) {
                  continue;
               }

               CitizenState s = o.citizenState();
               boolean match = panic ? s.contagious() && o.hasThreat() : s == CitizenState.IDLE && o.talkPartner == null;
               if (match) {
                  double d = o.distanceToSqr(self);
                  if (d < bestD) {
                     bestD = d;
                     best = o;
                  }
               }
            }
         }
      }

      return best;
   }
}
