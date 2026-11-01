package dev.baranhan.viltrumitecore.worldevent.godzilla;

import dev.baranhan.viltrumitecore.entity.godzilla.GodzillaEntities;
import net.minecraftforge.eventbus.api.IEventBus;

/** Godzilla city catastrophe bootstrap: sound holders (before the shared sound register) and the entity register. */
public final class GodzillaSetup {
   private GodzillaSetup() {
   }

   public static void init(IEventBus modBus) {
      GodzillaSounds.init();
      GodzillaEntities.register(modBus);
   }
}
