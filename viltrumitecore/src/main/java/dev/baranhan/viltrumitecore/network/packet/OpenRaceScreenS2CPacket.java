package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent.Context;

public class OpenRaceScreenS2CPacket {
   public OpenRaceScreenS2CPacket() {
   }

   public OpenRaceScreenS2CPacket(FriendlyByteBuf buf) {
   }

   public void encode(FriendlyByteBuf buf) {
   }

   public void handle(Supplier<Context> supplier) {
      Context context = supplier.get();
      context.enqueueWork(() -> {
         if (context.getDirection().getReceptionSide().isClient()) {
            OpenRaceScreenS2CPacket.ClientHandler.openScreen();
         }
      });
      context.setPacketHandled(true);
   }

   private static class ClientHandler {
      /**
       * No automatic hero choice screen any more: a hero is chosen only with Od Lagna's Wish (the item, RMB).
       * The packet stays registered so the protocol does not change; RaceSelectionScreen stays as old code.
       */
      public static void openScreen() {
      }
   }
}
