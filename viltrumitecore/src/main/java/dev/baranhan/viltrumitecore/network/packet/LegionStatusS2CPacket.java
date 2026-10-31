package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionStatus;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** The owner's Legion HUD state and the JARVIS events of this update. */
public class LegionStatusS2CPacket {
   private final LegionStatus status;

   public LegionStatusS2CPacket(LegionStatus status) {
      this.status = status;
   }

   public LegionStatusS2CPacket(FriendlyByteBuf buffer) {
      this.status = LegionStatus.read(buffer);
   }

   public static LegionStatusS2CPacket of(dev.baranhan.viltrumitecore.hero.ironman.legion.LegionSquad squad) {
      return new LegionStatusS2CPacket(LegionStatus.of(squad));
   }

   public void toBytes(FriendlyByteBuf buffer) {
      this.status.write(buffer);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
         dev.baranhan.viltrumitecore.client.ironman.legion.ClientLegion.status(this.status)));
      context.setPacketHandled(true);
   }
}
