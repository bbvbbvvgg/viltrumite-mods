package dev.baranhan.viltrumitecore.hero.thor.combat;

import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEventType;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvents;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorLifecycle;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveHandler;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves;
import dev.baranhan.viltrumitecore.hero.thor.dev.ThorDevCommands;
import java.util.EnumMap;
import java.util.Map;

/**
 * Session B's one install point on the foundation bridge: registers its
 * moves in ThorMoves (owner {@link #OWNER}), subscribes to the bridge
 * events it needs and adds {@code /thor debug combat}. It never installs a
 * HammerAccess, never registers an MMB resolver and never touches retired ids.
 * Called from common setup on both sides (the client reads the same
 * registrations for claims and clips).
 */
public final class ThorCombatModule {
   public static final String OWNER = "B";

   private static boolean installed;

   private ThorCombatModule() {
   }

   /** The moves B authors and their handlers. */
   public static Map<ThorMove, ThorMoveHandler> handlers() {
      Map<ThorMove, ThorMoveHandler> handlers = new EnumMap<>(ThorMove.class);
      handlers.put(ThorMove.HAMMER_COMBO, MeleeMoves.COMBO);
      handlers.put(ThorMove.FIST_COMBO, MeleeMoves.COMBO);
      handlers.put(ThorMove.HAMMER_STRIKE, MeleeMoves.STRIKE);
      handlers.put(ThorMove.ELECTRIC_MODE, ElectricMoves.MODE);
      handlers.put(ThorMove.ELECTRIC_COMBO, ElectricMoves.DISCHARGE);
      handlers.put(ThorMove.ELECTRIC_WAVE, ElectricMoves.WAVE);
      handlers.put(ThorMove.ELECTRIC_STORM, ElectricMoves.STORM);
      handlers.put(ThorMove.WRATH, WrathMove.HANDLER);
      return handlers;
   }

   /** Idempotent. */
   public static synchronized void install() {
      if (installed) {
         return;
      }

      for (Map.Entry<ThorMove, ThorMoveHandler> entry : handlers().entrySet()) {
         ThorMoves.register(OWNER, entry.getKey(), ThorCombatTimings.of(entry.getKey()), entry.getValue());
      }

      ThorEvents.subscribe(OWNER, ThorEventType.COMBO_END, ElectricCombos::onComboEnd);
      ThorEvents.subscribe(OWNER, ThorEventType.THROW, ElectricCombos::onHammerEvent);
      ThorEvents.subscribe(OWNER, ThorEventType.RECALL, ElectricCombos::onHammerEvent);
      ThorEvents.subscribe(OWNER, ThorEventType.CATCH, ThorCombatRuntime::onCatch);
      ThorLifecycle.subscribe(OWNER, ThorCombatRuntime::onLifecycleStop);
      ThorDevCommands.registerChild(OWNER, "debug combat", ThorCombatCommands::build);
      installed = true;
   }

   public static synchronized boolean installed() {
      return installed;
   }

   /** Tests only (with ThorMoves / ThorEvents / ThorDevCommands resetForTests). */
   public static synchronized void resetForTests() {
      installed = false;
      ThorCombatState.clearAll();
   }
}
