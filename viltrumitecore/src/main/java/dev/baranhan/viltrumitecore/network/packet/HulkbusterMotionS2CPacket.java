package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** The server refused a predicted Hulkbuster movement op: the client drops its prediction (the position was already snapped back). */
public class HulkbusterMotionS2CPacket {
   private final int op;

   public HulkbusterMotionS2CPacket(int op) {
      this.op = op;
   }

   public HulkbusterMotionS2CPacket(FriendlyByteBuf buf) {
      this.op = buf.readByte();
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeByte(this.op);
   }

   public void handle(Supplier<NetworkEvent.Context> context) {
      int op = this.op;
      context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
         () -> () -> dev.baranhan.viltrumitecore.client.ironman.hulkbuster.HulkbusterMotionClient.refused(op)));
      context.get().setPacketHandled(true);
   }
}
