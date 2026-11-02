package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** A body caught hellfire for {@code ticks} (0 = put out): the clients draw the hellfire flames on it. */
public record HellfireS2CPacket(int entityId, int ticks) {
   public HellfireS2CPacket(FriendlyByteBuf buf) {
      this(buf.readVarInt(), buf.readVarInt());
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeVarInt(this.entityId);
      buf.writeVarInt(this.ticks);
   }

   public void handle(Supplier<NetworkEvent.Context> supplier) {
      NetworkEvent.Context context = supplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
         () -> () -> dev.baranhan.viltrumitecore.client.ghostrider.GhostRiderVfx.receiveHellfire(this.entityId, this.ticks)));
      context.setPacketHandled(true);
   }
}
