package dev.baranhan.viltrumitecore.worldevent.ultron.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * One Ultron world effect for nearby clients (impacts, blasts, shields, node boot / break, welding,
 * hacking, fabrication, glitch pulses, Ultron Prime's beams and shockwaves). Server authoritative: the
 * client only draws. {@code d} is a direction or a second point (kind-owned), {@code extra} a seed, a
 * kind ordinal or an entity id.
 */
public record UltronFxS2CPacket(int kind, double x, double y, double z, float dx, float dy, float dz, float power, int extra) {
   public static final int BOLT_IMPACT = 0;
   public static final int MISSILE_BLAST = 1;
   public static final int DEFLECT = 2;
   public static final int UNIT_BREAKUP = 3;
   public static final int MUZZLE = 4;
   public static final int SLAM_WAVE = 5;
   public static final int ZAP = 6;
   public static final int SCAN = 7;
   public static final int MARK = 8;
   public static final int WELD = 9;
   public static final int HACK = 10;
   public static final int NODE_BOOT = 11;
   public static final int NODE_SHIELD_HIT = 12;
   public static final int NODE_SHIELD_BREAK = 13;
   public static final int NODE_DESTROYED = 14;
   public static final int NODE_DISABLED = 15;
   public static final int FABRICATE = 16;
   public static final int GLITCH = 17;
   public static final int PRIME_BEAM = 18;
   public static final int PRIME_IMPACT = 19;
   public static final int PRIME_SHOCKWAVE = 20;
   public static final int PRIME_TRANSITION = 21;
   public static final int PRIME_ASSEMBLE = 22;
   public static final int PRIME_DEATH = 23;
   public static final int CAPTURE = 24;
   public static final int RELEASE = 25;
   public static final int DASH_TRAIL = 26;
   public static final int UPLOAD = 27;
   public static final int CHARGE = 28;
   public static final int JET = 29;
   public static final int NODE_HIT = 30;
   public static final int SHUTDOWN = 31;
   /** Highest kind id (tests check the client handles every one). */
   public static final int LAST = SHUTDOWN;

   public UltronFxS2CPacket(int kind, Vec3 at, Vec3 d, float power, int extra) {
      this(kind, at.x, at.y, at.z, (float)d.x, (float)d.y, (float)d.z, power, extra);
   }

   public UltronFxS2CPacket(FriendlyByteBuf b) {
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
      ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.baranhan.viltrumitecore.client.ultron.UltronVfx.accept(this)));
      ctx.get().setPacketHandled(true);
   }
}
