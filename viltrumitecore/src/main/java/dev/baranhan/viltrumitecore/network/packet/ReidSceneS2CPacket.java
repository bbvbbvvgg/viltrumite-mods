package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * The Reid draw scene (Reinhard spec §7.3) for every player within 96 blocks:
 * START (sent at the press and every 20 t for late joiners) and END (final or
 * abort). The client times the scene from {@code startGameTime}, so a relog
 * mid-draw shows the right moment. {@code center} is Reinhard's exact position
 * at the press (REINHARD OVERHAUL item 15: the beam stands on him, never on the
 * centre of his block); clients that track him follow his interpolated position.
 */
public class ReidSceneS2CPacket {
   public static final int START = 0;
   public static final int END = 1;
   private final int casterId;
   private final Vec3 center;
   private final long startGameTime;
   private final int phase;

   public ReidSceneS2CPacket(int casterId, Vec3 center, long startGameTime, int phase) {
      this.casterId = casterId;
      this.center = center;
      this.startGameTime = startGameTime;
      this.phase = phase;
   }

   public ReidSceneS2CPacket(FriendlyByteBuf buffer) {
      this.casterId = buffer.readVarInt();
      this.center = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
      this.startGameTime = buffer.readLong();
      this.phase = buffer.readByte();
   }

   public void toBytes(FriendlyByteBuf buffer) {
      buffer.writeVarInt(this.casterId);
      buffer.writeDouble(this.center.x);
      buffer.writeDouble(this.center.y);
      buffer.writeDouble(this.center.z);
      buffer.writeLong(this.startGameTime);
      buffer.writeByte(this.phase);
   }

   public int casterId() {
      return this.casterId;
   }

   public Vec3 center() {
      return this.center;
   }

   public long startGameTime() {
      return this.startGameTime;
   }

   public int phase() {
      return this.phase;
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
         dev.baranhan.viltrumitecore.client.reinhard.reid.ReidDrawScene.onPacket(this.casterId, this.center, this.startGameTime, this.phase)));
      context.setPacketHandled(true);
   }
}
