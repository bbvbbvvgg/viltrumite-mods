package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.client.reinhard.DashBehindClient;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * The server's answer to every {@link ReinhardDashBehindC2SPacket}: Dash Behind's
 * cooldown in ticks right after the press (0 = refused for a reason other than the
 * cooldown). {@code DashBehindClient} sets its local predictor cooldown to it, so a
 * refused prediction does not block the next one.
 */
public class ReinhardDashBehindAnswerS2CPacket {
   private final int cooldown;

   public ReinhardDashBehindAnswerS2CPacket(int cooldown) {
      this.cooldown = Math.max(0, cooldown);
   }

   public ReinhardDashBehindAnswerS2CPacket(FriendlyByteBuf buffer) {
      this.cooldown = Math.max(0, buffer.readVarInt());
   }

   public void toBytes(FriendlyByteBuf buffer) {
      buffer.writeVarInt(this.cooldown);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DashBehindClient.onAnswer(this.cooldown)));
      context.setPacketHandled(true);
   }
}
