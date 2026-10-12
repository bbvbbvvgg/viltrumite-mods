package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.client.hero.AirJumpClient;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Air jump state (HeroAirJump): {@code step} = an accepted step of another
 * player (observers draw the ring); otherwise the server's counter for the
 * local player after a refused press.
 */
public class AirJumpSyncS2CPacket {
   private final int entityId;
   private final int used;
   private final boolean step;

   public AirJumpSyncS2CPacket(int entityId, int used, boolean step) {
      this.entityId = entityId;
      this.used = used;
      this.step = step;
   }

   public AirJumpSyncS2CPacket(FriendlyByteBuf buffer) {
      this.entityId = buffer.readVarInt();
      this.used = buffer.readByte();
      this.step = buffer.readBoolean();
   }

   public void toBytes(FriendlyByteBuf buffer) {
      buffer.writeVarInt(this.entityId);
      buffer.writeByte(this.used);
      buffer.writeBoolean(this.step);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AirJumpClient.onSync(this.entityId, this.used, this.step)));
      context.setPacketHandled(true);
   }
}
