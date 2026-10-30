package dev.baranhan.viltrumitecore.hero.regulus.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * One Regulus world event for nearby clients (VFX, sound timing, cameras). Server authoritative:
 * the client only draws what this packet and the synced snapshot say happened. {@code a}/{@code b}
 * are entity ids (source, target), {@code extra} a kind-owned integer (seed, block state id,
 * flags, grain index).
 */
public record RegulusFxS2CPacket(int kind, int a, int b, double x, double y, double z, float dx, float dy, float dz, float power, int extra) {
   public static final int LION_ON = 0, LION_OFF = 1, RECOIL = 2, PROJECTILE_STOP = 3, SEIZURE = 4, TOUCH_HIT = 5, TOUCH_MISS = 6,
      RUPTURE = 7, SAND_SHOT = 8, GRAIN_STOP = 9, GRAIN_HIT = 10, SEVER_SHOT = 11, SEVER_CUT = 12, TRAMPLE_IMPACT = 13, BLOCK_STOP = 14,
      REPLY_BLOCK = 15, REPLY_STRIKE = 16, KING_BIND = 17, KING_HOP = 18, KING_RELEASE = 19, ULT_START = 20, ULT_SCOOP = 21,
      ULT_WAVE = 22, ULT_FLICK = 23, ULT_END = 24, REFUSE = 25, LAND = 26, TRAMPLE_HIT = 27;

   public RegulusFxS2CPacket(FriendlyByteBuf b) {
      this(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(), b.readFloat(),
         b.readFloat(), b.readVarInt());
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeVarInt(this.kind);
      buf.writeVarInt(this.a);
      buf.writeVarInt(this.b);
      buf.writeDouble(this.x);
      buf.writeDouble(this.y);
      buf.writeDouble(this.z);
      buf.writeFloat(this.dx);
      buf.writeFloat(this.dy);
      buf.writeFloat(this.dz);
      buf.writeFloat(this.power);
      buf.writeVarInt(this.extra);
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
         dev.baranhan.viltrumitecore.client.regulus.RegulusFx.accept(this)));
      ctx.get().setPacketHandled(true);
   }
}
