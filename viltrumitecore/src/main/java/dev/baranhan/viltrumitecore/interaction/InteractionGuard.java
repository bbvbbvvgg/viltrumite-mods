package dev.baranhan.viltrumitecore.interaction;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Loop and double-damage protection of the contextual interactions. Pure (game ticks in, no Minecraft types), one
 * instance per server ({@link ContextualInteractions}).
 *
 * <ul>
 *   <li>Re-entrancy: while an interaction applies its result (shockwave hits, pushes) no other interaction may fire,
 *       so reactions never chain or loop.</li>
 *   <li>Cooldown per (interaction, entity pair): one collision triggers once.</li>
 *   <li>Aftermath per entity pair: the remaining hits of the same stroke between the two are swallowed.</li>
 * </ul>
 */
public final class InteractionGuard {
   private static final int PRUNE_AT = 512;
   private final Map<String, Long> cooldowns = new HashMap<>();
   private final Map<Long, Long> aftermath = new HashMap<>();
   private int depth;

   public boolean reentrant() {
      return this.depth > 0;
   }

   public void enter() {
      this.depth++;
   }

   public void exit() {
      this.depth = Math.max(0, this.depth - 1);
   }

   /** Order-free key of two entity ids. */
   public static long pair(int a, int b) {
      int lo = Math.min(a, b);
      int hi = Math.max(a, b);
      return (long)lo << 32 | hi & 0xFFFFFFFFL;
   }

   private static String key(String id, int a, int b) {
      return id + '|' + pair(a, b);
   }

   public boolean coolingDown(String id, int a, int b, long now) {
      Long until = this.cooldowns.get(key(id, a, b));
      return until != null && now < until;
   }

   public boolean inAftermath(int a, int b, long now) {
      Long until = this.aftermath.get(pair(a, b));
      return until != null && now < until;
   }

   /** Records a fired interaction. */
   public void fired(InteractionSpec spec, int a, int b, long now) {
      if (spec.cooldownTicks() > 0) {
         this.cooldowns.put(key(spec.id(), a, b), now + spec.cooldownTicks());
      }
      if (spec.aftermathTicks() > 0) {
         this.aftermath.merge(pair(a, b), now + spec.aftermathTicks(), Math::max);
      }
      if (this.cooldowns.size() + this.aftermath.size() > PRUNE_AT) {
         this.prune(now);
      }
   }

   /** A free-form throttle (FX of a frequent interaction): true once per {@code ticks} for the key. */
   public boolean throttle(String key, long now, int ticks) {
      Long until = this.cooldowns.get(key);
      if (until != null && now < until) {
         return false;
      }
      this.cooldowns.put(key, now + ticks);
      return true;
   }

   public void prune(long now) {
      for (Iterator<Map.Entry<String, Long>> it = this.cooldowns.entrySet().iterator(); it.hasNext(); ) {
         if (it.next().getValue() <= now) {
            it.remove();
         }
      }
      for (Iterator<Map.Entry<Long, Long>> it = this.aftermath.entrySet().iterator(); it.hasNext(); ) {
         if (it.next().getValue() <= now) {
            it.remove();
         }
      }
   }

   public int size() {
      return this.cooldowns.size() + this.aftermath.size();
   }

   public void clear() {
      this.cooldowns.clear();
      this.aftermath.clear();
      this.depth = 0;
   }
}
