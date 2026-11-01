package dev.baranhan.viltrumitecore.client.godzilla;

import dev.baranhan.viltrumitecore.worldevent.godzilla.net.GodzillaFxS2CPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Client side of the kaiju event: one-shot effects from {@link GodzillaFxS2CPacket}. */
@OnlyIn(Dist.CLIENT)
public final class GodzillaClient {
   private GodzillaClient() {
   }

   public static void accept(GodzillaFxS2CPacket packet) {
   }
}
