package dev.baranhan.viltrumitecore.worldevent.chitauri;

import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriEntities;
import net.minecraftforge.eventbus.api.IEventBus;

/** Chitauri invasion bootstrap: sound holders (before the shared sound register) and the entity register. */
public final class ChitauriSetup {
   private ChitauriSetup() {
   }

   public static void init(IEventBus modBus) {
      ChitauriSounds.init();
      ChitauriEntities.register(modBus);
   }
}
