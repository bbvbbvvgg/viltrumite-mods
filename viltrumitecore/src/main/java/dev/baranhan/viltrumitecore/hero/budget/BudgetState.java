package dev.baranhan.viltrumitecore.hero.budget;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.effect.MobEffect;

/**
 * Server-only transient state of expansion heroes. Nothing here is saved:
 * cooldowns and a running scene simply reset on relog, which keeps old and
 * new saves untouched.
 */
final class BudgetState {
   private static final Map<UUID, BudgetState> STATES = new HashMap<>();

   /** Server tick at which each move slot is ready again. */
   final long[] readyAt = new long[4];
   /** Ticks left in the ultimate wind-up scene, 0 = none. */
   int sceneTicks;
   int sceneMove = -1;
   /** Fall damage is ignored until this server tick (after dashes and hovering). */
   long noFallUntil;
   /** Effects this hero granted, removed again on hero change. */
   final Set<MobEffect> granted = new HashSet<>();

   static BudgetState of(UUID id) {
      return STATES.computeIfAbsent(id, key -> new BudgetState());
   }

   static BudgetState peek(UUID id) {
      return STATES.get(id);
   }

   static BudgetState remove(UUID id) {
      return STATES.remove(id);
   }
}
