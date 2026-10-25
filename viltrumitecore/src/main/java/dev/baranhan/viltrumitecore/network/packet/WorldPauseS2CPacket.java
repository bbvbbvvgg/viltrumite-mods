package dev.baranhan.viltrumitecore.network.packet;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** All-dimension pause identity and a local-input watchdog. Owner is never input-blocked. */
public record WorldPauseS2CPacket(boolean active,UUID pauseId,UUID owner,int ticksLeft) {
   public WorldPauseS2CPacket(FriendlyByteBuf b) {this(b.readBoolean(),b.readUUID(),b.readUUID(),b.readVarInt());}
   public void toBytes(FriendlyByteBuf b) {b.writeBoolean(active); b.writeUUID(pauseId); b.writeUUID(owner); b.writeVarInt(ticksLeft);}
   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->
         dev.baranhan.viltrumitecore.client.hero.WorldPauseClient.accept(this)));
      ctx.get().setPacketHandled(true);
   }
}
