package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.client.gui.RaceScreens;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

/** Opens the hero choice screen on the client ({@link RaceScreens#open}). */
public class OpenRaceScreenS2CPacket {
   public OpenRaceScreenS2CPacket() {
   }

   public OpenRaceScreenS2CPacket(FriendlyByteBuf buf) {
   }

   public void encode(FriendlyByteBuf buf) {
   }

   public void handle(Supplier<Context> supplier) {
      Context context = supplier.get();
      // Sent only on purpose (/viltrumite choose, a refused choice): never on login, heroes come from the wish.
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> RaceScreens::open));
      context.setPacketHandled(true);
   }
}
