package dev.baranhan.viltrumitecore.hero;

import java.util.List;
import net.minecraftforge.event.TickEvent;

/**
 * Which hero systems run in which half of a server level tick (pure dispatcher
 * for {@link HeroEvents#onLevelTick}): control (freeze, pull, domes) keeps the
 * START half as before; projectiles, resource regen, level-owned Reinhard scenes and the carve queue run on END.
 */
public final class LevelTickPhases {
   public enum System {
      CONTROL,
      PROJECTILES,
      RESOURCE_REGEN,
      CARVE,
      WORLD_CUT,
      SAW_SPHERE
   }

   private static final List<System> START = List.of(System.CONTROL);
   private static final List<System> END = List.of(System.PROJECTILES, System.RESOURCE_REGEN, System.WORLD_CUT, System.SAW_SPHERE, System.CARVE);

   private LevelTickPhases() {
   }

   public static List<System> systemsFor(TickEvent.Phase phase) {
      return phase == TickEvent.Phase.START ? START : END;
   }
}
