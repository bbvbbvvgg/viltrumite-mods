package dev.baranhan.viltrumitecore.hero.sinclair;

import dev.baranhan.viltrumitecore.hero.ActionLocks;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.SinclairEntities;
import net.minecraftforge.eventbus.api.IEventBus;

/** Mod-constructor hook of Sinclair (spec §11): sound holders, the entity register and the rabbit action lock. */
public final class SinclairSetup {
   private SinclairSetup() {
   }

   public static void init(IEventBus modBus) {
      SinclairSounds.init();
      SinclairEntities.register(modBus);
      ActionLocks.register(HexMarks::locks);
   }
}
