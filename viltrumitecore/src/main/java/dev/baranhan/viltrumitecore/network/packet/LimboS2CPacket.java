package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Private limbo overlay resume/end, independent of world game time. */
public record LimboS2CPacket(boolean active, int ticksLeft) {
   public LimboS2CPacket(FriendlyByteBuf b) {this(b.readBoolean(), b.readVarInt());}
   public void toBytes(FriendlyByteBuf b) {b.writeBoolean(active); b.writeVarInt(ticksLeft);}
   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
         dev.baranhan.viltrumitecore.client.reinhard.ReinhardClient.receiveLimbo(this)));
      ctx.get().setPacketHandled(true);
   }
}
