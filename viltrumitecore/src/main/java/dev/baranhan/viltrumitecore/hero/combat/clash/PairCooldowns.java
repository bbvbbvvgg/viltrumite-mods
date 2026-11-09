package dev.baranhan.viltrumitecore.hero.combat.clash;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pure, symmetric per-pair cooldown: the same two fighters cannot start another clash too soon. */
public final class PairCooldowns {
   private record Pair(UUID low, UUID high) {
      static Pair of(UUID a, UUID b) {
         return a.compareTo(b) <= 0 ? new Pair(a, b) : new Pair(b, a);
      }
   }

   private final Map<Pair, Long> until = new HashMap<>();

   public boolean ready(UUID a, UUID b, long now) {
      Long end = this.until.get(Pair.of(a, b));
      return end == null || now >= end;
   }

   public void start(UUID a, UUID b, long now, int ticks) {
      this.until.put(Pair.of(a, b), now + ticks);
   }

   /** Forgets finished cooldowns so the map never grows past live pairs. */
   public void prune(long now) {
      this.until.values().removeIf(end -> now >= end);
   }

   public int size() {
      return this.until.size();
   }

   public void clear() {
      this.until.clear();
   }
}
