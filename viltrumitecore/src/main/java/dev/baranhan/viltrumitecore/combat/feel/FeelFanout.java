package dev.baranhan.viltrumitecore.combat.feel;

import java.util.HashMap;
import java.util.Map;

/**
 * One fan-out per attacker per server tick (wave1-22). An area hit calls {@code CombatFeel.hit}
 * once per struck body; the personal hit-stop is per body, but the shared shake, impact frame and
 * air ring are the same event and must reach the network once, not once per body. Pure: no
 * Minecraft classes, so the burst of an ultimate is unit-testable.
 */
public final class FeelFanout {
   /** Distinct attackers remembered before the table is dropped (it is cheap to rebuild). */
   public static final int MAX_TRACKED = 256;

   private final Map<Integer, Long> lastTick = new HashMap<>();

   /** True the first time this attacker acts in this tick, false for the rest of the tick. */
   public boolean admit(int attackerId, long gameTime) {
      Long previous = this.lastTick.get(attackerId);
      if (previous != null && previous.longValue() == gameTime) {
         return false;
      }
      if (this.lastTick.size() >= MAX_TRACKED && !this.lastTick.containsKey(attackerId)) {
         this.lastTick.clear();
      }
      this.lastTick.put(attackerId, gameTime);
      return true;
   }

   public void clear() {
      this.lastTick.clear();
   }

   public int tracked() {
      return this.lastTick.size();
   }
}
