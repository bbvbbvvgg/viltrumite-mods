package dev.baranhan.viltrumitecore.worldevent.chitauri.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * One Chitauri world effect for nearby clients (impacts, explosions, breakups, Leviathan hits and
 * crashes, portal strikes). Server authoritative: the client only draws. {@code d} is a direction
 * or a second point (kind-owned), {@code extra} a seed or an entity id.
 */
public record ChitauriFxS2CPacket(int kind, double x, double y, double z, float dx, float dy, float dz, float power, int extra) {
   public static final int BOLT_IMPACT = 0, CANNON_IMPACT = 1, EXPLOSION = 2, CHARIOT_BREAKUP = 3, LEVIATHAN_HIT = 4, LEVIATHAN_ROAR = 5,
      LEVIATHAN_CRASH = 6, LEVIATHAN_SMASH = 7, DROP_LANDING = 8, PORTAL_STRIKE = 9, SEVER = 10, MUZZLE = 11, SEGMENT_LAND = 12, VANISH = 13,
      // city war (feat/chitauri-evolution): appended, ids stay contiguous
      TETHER = 14, SLAM = 15, SIEGE_CHARGE = 16, SIEGE_IMPACT = 17, BEACON_DEPLOY = 18, BEACON_DOWN = 19, SIGNAL = 20, RELEASE = 21,
      ARMOR_BREAK = 22, THRUST = 23;

   public ChitauriFxS2CPacket(int kind, Vec3 at, Vec3 d, float power, int extra) {
      this(kind, at.x, at.y, at.z, (float)d.x, (float)d.y, (float)d.z, power, extra);
   }

   public ChitauriFxS2CPacket(FriendlyByteBuf b) {
      this(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readInt());
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
      buf.writeInt(this.extra);
   }

   public Vec3 at() {
      return new Vec3(this.x, this.y, this.z);
   }

   public Vec3 d() {
      return new Vec3(this.dx, this.dy, this.dz);
   }

   public void handle(Supplier<NetworkEvent.Context> ctx) {
      ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
         dev.baranhan.viltrumitecore.client.chitauri.ChitauriVfx.accept(this)));
      ctx.get().setPacketHandled(true);
   }
}
