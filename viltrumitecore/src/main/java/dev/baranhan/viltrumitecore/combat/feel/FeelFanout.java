package dev.baranhan.viltrumitecore.combat.feel;

import java.util.HashMap;
import java.util.Map;

/**
 * One fan-out of the shared effects per attacker per server tick, kept to the heaviest hit of the
 * tick (wave1-22). An area hit calls {@code CombatFeel.hit} once per struck body; the personal
 * hit-stop is per body, but the shared shake, impact frame and air ring are the same event and must
 * reach the network once. A later, heavier hit in the same tick is still sent, so an ultimate that
 * chips with a light wave and then lands a crushing blow keeps its impact frame and ring; a lighter
 * hit after a heavier one is swallowed. Pure: no Minecraft classes, so the burst of an ultimate is
 * unit-testable.
 */
public final class FeelFanout {
   /** Distinct attackers remembered before the table is dropped (it is cheap to rebuild). */
   public static final int MAX_TRACKED = 256;

   private final Map<Integer, Long> lastTick = new HashMap<>();
   private final Map<Integer, Integer> lastSeverity = new HashMap<>();

   /**
    * True when this hit should fan out: the first hit of the tick for this attacker, or a heavier
    * hit than the one already sent this tick. {@code severity} is a monotonic weight rank
    * ({@link HitWeight#ordinal()}).
    */
   public boolean admit(int attackerId, long gameTime, int severity) {
      Long tick = this.lastTick.get(attackerId);
      if (tick != null && tick.longValue() == gameTime) {
         Integer sent = this.lastSeverity.get(attackerId);
         if (sent != null && severity <= sent.intValue()) {
            return false;
         }
      }
      if (this.lastTick.size() >= MAX_TRACKED && !this.lastTick.containsKey(attackerId)) {
         this.lastTick.clear();
         this.lastSeverity.clear();
      }
      this.lastTick.put(attackerId, gameTime);
      this.lastSeverity.put(attackerId, severity);
      return true;
   }

   public void clear() {
      this.lastTick.clear();
      this.lastSeverity.clear();
   }

   public int tracked() {
      return this.lastTick.size();
   }
}
