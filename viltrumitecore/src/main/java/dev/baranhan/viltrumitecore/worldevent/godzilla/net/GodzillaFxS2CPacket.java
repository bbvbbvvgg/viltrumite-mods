package dev.baranhan.viltrumitecore.worldevent.godzilla.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * One Godzilla world effect for nearby clients: destruction dust, shock waves, claw and tail impacts, the fall,
 * hot beam craters, hits on the kaiju, the roar wave, the arrival, the retreat, the death and the clear. The walk,
 * the steps, the plate glow, the charge and the beam itself are drawn from the synced entity state, not from
 * packets. {@code d} is a direction (kind-owned), {@code extra} a count, a lifetime or a seed. The client decides
 * near / far detail by its own distance.
 */
public record GodzillaFxS2CPacket(int kind, double x, double y, double z, float dx, float dy, float dz, float power, int extra) {
   public static final int SMASH = 0;
   public static final int STOMP = 1;
   public static final int TAIL_HIT = 2;
   public static final int CLAW_HIT = 3;
   public static final int FALL_IMPACT = 4;
   public static final int HOT = 5;
   public static final int BEAM_BLAST = 6;
   public static final int ROAR_WAVE = 7;
   public static final int DEATH = 8;
   public static final int RETREAT = 9;
   public static final int ARRIVE = 10;
   public static final int CLEAR = 11;
   public static final int HIT = 12;
   public static final int RISE = 13;
   public static final int STEP = 14;
   /** Highest kind id (tests check the client handles every one). */
   public static final int LAST = STEP;

   public GodzillaFxS2CPacket(int kind, Vec3 at, Vec3 d, float power, int extra) {
      this(kind, at.x, at.y, at.z, (float)d.x, (float)d.y, (float)d.z, power, extra);
   }

   public GodzillaFxS2CPacket(FriendlyByteBuf b) {
      this(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readVarInt());
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeVarInt(this.kind);
      buf.writeDouble(this.x);
      buf.writeDouble(this.y);
      buf.writeDouble(this.z);
      buf.writeFloat(this.dx);
      buf.writeFloat(this.dy);
      buf.writeFloat(this.dz);
      buf.writeFloat(this.power);
      buf.writeVarInt(this.extra);
   }

   public Vec3 at() {
      return new Vec3(this.x, this.y, this.z);
   }

   public Vec3 d() {
      return new Vec3(this.dx, this.dy, this.dz);
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.baranhan.viltrumitecore.client.godzilla.GodzillaClient.accept(this)));
      ctx.get().setPacketHandled(true);
   }
}
