package dev.baranhan.viltrumitecore.hero.regulus;

import net.minecraftforge.eventbus.api.IEventBus;

/** Mod-constructor hook of Regulus Reborn: the sound holders (registered on the shared sound register). */
public final class RegulusSetup {
   private RegulusSetup() {
   }

   public static void init(IEventBus modBus) {
      RegulusSounds.init();
   }
}
