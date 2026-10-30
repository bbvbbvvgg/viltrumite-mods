package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.hero.wish.Wish;
import dev.baranhan.viltrumitecore.hero.wish.WishRitual;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** «исполнить желание»: only the wish id. The server validates everything (WishRitual.request). */
public class WishChoiceC2SPacket {
   private final String wishId;

   public WishChoiceC2SPacket(String wishId) {
      this.wishId = wishId;
   }

   public WishChoiceC2SPacket(FriendlyByteBuf buffer) {
      this.wishId = buffer.readUtf(Wish.MAX_ID);
   }

   public void toBytes(FriendlyByteBuf buffer) {
      buffer.writeUtf(this.wishId, Wish.MAX_ID);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> {
         ServerPlayer player = context.getSender();
         if (player != null) {
            WishRitual.request(player, this.wishId);
         }
      });
      context.setPacketHandled(true);
   }
}
