package dev.baranhan.viltrumitecore.worldevent.ultron;

import dev.baranhan.viltrumitecore.entity.ultron.UltronEntities;
import net.minecraftforge.eventbus.api.IEventBus;

/** Ultron uprising bootstrap: sound holders (before the shared sound register) and the machines' entity register. */
public final class UltronSetup {
   private UltronSetup() {
   }

   public static void init(IEventBus modBus) {
      UltronSounds.init();
      UltronEntities.register(modBus);
   }
}
