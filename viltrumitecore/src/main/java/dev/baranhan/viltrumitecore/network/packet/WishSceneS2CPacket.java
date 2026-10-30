package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.hero.wish.Wish;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * One beat of a wish ritual for the wisher and everyone tracking them: START (also the catch-up for a
 * late tracker, with the elapsed ticks), SWAP (the hero just changed), END, ABORT. Client: WishSceneClient.
 */
public record WishSceneS2CPacket(int phase, int entityId, String wishId, int elapsed) {
   public static final int START = 0;
   public static final int SWAP = 1;
   public static final int END = 2;
   public static final int ABORT = 3;

   public WishSceneS2CPacket(FriendlyByteBuf buffer) {
      this(buffer.readByte(), buffer.readVarInt(), buffer.readUtf(Wish.MAX_ID), buffer.readVarInt());
   }

   public void toBytes(FriendlyByteBuf buffer) {
      buffer.writeByte(this.phase);
      buffer.writeVarInt(this.entityId);
      buffer.writeUtf(this.wishId, Wish.MAX_ID);
      buffer.writeVarInt(Math.max(0, this.elapsed));
   }

   public void handle(Supplier<NetworkEvent.Context> context) {
      context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.baranhan.viltrumitecore.client.wish.WishSceneClient.handle(this)));
      context.get().setPacketHandled(true);
   }
}
