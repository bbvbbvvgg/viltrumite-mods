package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Saw Sphere end: {@code interrupted} stops saws, barrier, sound and camera at once (no finale). */
public record SawSphereEndS2CPacket(int casterId, boolean interrupted) {
   public SawSphereEndS2CPacket(FriendlyByteBuf b) {
      this(b.readVarInt(), b.readBoolean());
   }

   public void toBytes(FriendlyByteBuf b) {
      b.writeVarInt(this.casterId);
      b.writeBoolean(this.interrupted);
   }

   public void handle(Supplier<NetworkEvent.Context> c) {
      c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.baranhan.viltrumitecore.client.reinhard.saw.SawSphereClient.end(this)));
      c.get().setPacketHandled(true);
   }
}
