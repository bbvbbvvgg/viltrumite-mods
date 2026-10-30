package dev.baranhan.viltrumitecore.hero.dio.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** One DIO impact/event effect for nearby clients (pixel VFX, camera, timing). */
public record DioFxS2CPacket(int kind, int sourceId, double x, double y, double z, float dx, float dy, float dz, float power, int extra) {
   public static final int STAND_HIT = 0, STAND_MISS = 1, BARRAGE_HIT = 2, FINISHER = 3, KNIFE_HIT = 4, LEDGER_BURST = 5,
      DRAIN_PULSE = 6, ROLLER_SLAM = 7, ROLLER_PUNCH = 8, ROLLER_EXPLODE = 9, GUARD_BLOCK = 10, DEFLECT = 11, FROZEN_HIT = 12,
      SUMMON = 13, KNIFE_STOP = 14, ROLLER_LEAP = 15, ROLLER_LAND = 16;

   public DioFxS2CPacket(FriendlyByteBuf b) {
      this(b.readVarInt(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readVarInt());
   }

   public void toBytes(FriendlyByteBuf b) {
      b.writeVarInt(this.kind);
      b.writeVarInt(this.sourceId);
      b.writeDouble(this.x);
      b.writeDouble(this.y);
      b.writeDouble(this.z);
      b.writeFloat(this.dx);
      b.writeFloat(this.dy);
      b.writeFloat(this.dz);
      b.writeFloat(this.power);
      b.writeVarInt(this.extra);
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
         dev.baranhan.viltrumitecore.client.dio.DioFxClient.accept(this)));
      ctx.get().setPacketHandled(true);
   }
}
