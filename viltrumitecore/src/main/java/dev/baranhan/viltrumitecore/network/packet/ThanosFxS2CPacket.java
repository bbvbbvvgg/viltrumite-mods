package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * One Thanos event effect for nearby clients (VFX, cues, local slow/disorientation of the
 * receiving player). Server authoritative: the client draws and applies only what this says.
 * {@code a}/{@code b} are entity ids (Thanos, target), {@code extra} a kind-owned integer
 * (ticks, seed, move id, count).
 */
public record ThanosFxS2CPacket(int kind, int a, int b, double x, double y, double z, float dx, float dy, float dz, float power, int extra) {
   public static final int TELEGRAPH = 0, MELEE_HIT = 1, SLAM = 2, POWER_PUNCH = 3, POWER_WAVE = 4, FISSURES = 5, SPACE_PORTAL = 6,
      SPACE_STEP = 7, SPACE_PULL = 8, SPACE_WARD = 9, SPACE_REDIRECT = 10, REALITY_ZONE = 11, REALITY_MIRAGE = 12, MIRAGE_POP = 13,
      TIME_DOME = 14, TIME_REWIND = 15, MIND_GAZE_CHARGE = 16, MIND_GAZE_HIT = 17, MIND_RESIST = 18, MIND_PULSE = 19, MIND_FALSE_TARGETS = 20,
      SOUL_TETHER = 21, SOUL_TETHER_BREAK = 22, SOUL_DRAIN = 23, SOUL_NOVA = 24, ARRIVAL = 25, STUN = 26, REALITY_SHARD = 27, CLEAR = 28;

   public ThanosFxS2CPacket(FriendlyByteBuf b) {
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
         dev.baranhan.viltrumitecore.client.thanos.ThanosFx.accept(this)));
      ctx.get().setPacketHandled(true);
   }
}
