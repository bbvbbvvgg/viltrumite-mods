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
       * The first hero choice is a wish now (Od Lagna's Wish, first-choice mode). RaceSelectionScreen stays
       * in the code as the old fallback; it is no longer opened in normal play.
       */
      public static void openScreen() {
         dev.baranhan.viltrumitecore.client.wish.WishScreens.open(true);
      }
   }
}
