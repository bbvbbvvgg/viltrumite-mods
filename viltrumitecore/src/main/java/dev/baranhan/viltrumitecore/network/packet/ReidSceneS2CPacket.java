package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * The Reid draw scene (Reinhard spec §7.3) for every player within 96 blocks:
 * START (sent at the press and every 20 t for late joiners) and END (final or
 * abort). The client times the scene from {@code startGameTime}, so a relog
 * mid-draw shows the right moment.
 */
public class ReidSceneS2CPacket {
   public static final int START = 0;
   public static final int END = 1;
   private final int casterId;
   private final BlockPos origin;
   private final long startGameTime;
   private final int phase;

   public ReidSceneS2CPacket(int casterId, BlockPos origin, long startGameTime, int phase) {
      this.casterId = casterId;
      this.origin = origin;
      this.startGameTime = startGameTime;
      this.phase = phase;
   }

   public ReidSceneS2CPacket(FriendlyByteBuf buffer) {
      this.casterId = buffer.readVarInt();
      this.origin = buffer.readBlockPos();
      this.startGameTime = buffer.readLong();
      this.phase = buffer.readByte();
   }

   public void toBytes(FriendlyByteBuf buffer) {
      buffer.writeVarInt(this.casterId);
      buffer.writeBlockPos(this.origin);
      buffer.writeLong(this.startGameTime);
      buffer.writeByte(this.phase);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
         dev.baranhan.viltrumitecore.client.reinhard.reid.ReidDrawScene.onPacket(this.casterId, this.origin, this.startGameTime, this.phase)));
      context.setPacketHandled(true);
   }
}
