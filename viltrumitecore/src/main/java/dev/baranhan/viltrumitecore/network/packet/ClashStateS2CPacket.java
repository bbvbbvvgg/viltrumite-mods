package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * One beam struggle as seen by a watcher (wave 1, session 23): both muzzles, the clash point as a fraction from A
 * to B, the struggle age, surge/grace flags and mash counts for the HUD, and the beam ids for colours. Sent every
 * tick while it runs and once more with the final status. Gameplay never trusts the client copy.
 */
public class ClashStateS2CPacket {
   public static final int RUNNING = 0;
   public static final int A_WINS = 1;
   public static final int B_WINS = 2;
   public static final int DRAW = 3;
   public static final int CANCELLED = 4;
   public static final int FLAG_SURGE_A = 1;
   public static final int FLAG_SURGE_B = 2;
   public static final int FLAG_GRACE = 4;
   public static final int MAX_BEAM_ID = 64;
   public final int id;
   public final int status;
   public final int entityA;
   public final int entityB;
   public final Vec3 originA;
   public final Vec3 originB;
   public final float point;
   public final int age;
   public final int flags;
   public final int pulsesA;
   public final int pulsesB;
   public final String beamA;
   public final String beamB;

   public ClashStateS2CPacket(int id, int status, int entityA, int entityB, Vec3 originA, Vec3 originB, float point, int age, int flags, int pulsesA,
      int pulsesB, String beamA, String beamB) {
      this.id = id;
      this.status = status;
      this.entityA = entityA;
      this.entityB = entityB;
      this.originA = originA;
      this.originB = originB;
      this.point = point;
      this.age = age;
      this.flags = flags;
      this.pulsesA = Math.max(0, Math.min(127, pulsesA));
      this.pulsesB = Math.max(0, Math.min(127, pulsesB));
      this.beamA = clip(beamA);
      this.beamB = clip(beamB);
   }

   public ClashStateS2CPacket(FriendlyByteBuf buf) {
      this.id = buf.readVarInt();
      this.status = buf.readByte();
      this.entityA = buf.readVarInt();
      this.entityB = buf.readVarInt();
      this.originA = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
      this.originB = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
      this.point = buf.readFloat();
      this.age = buf.readVarInt();
      this.flags = buf.readByte();
      this.pulsesA = buf.readByte();
      this.pulsesB = buf.readByte();
      this.beamA = buf.readUtf(MAX_BEAM_ID);
      this.beamB = buf.readUtf(MAX_BEAM_ID);
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeVarInt(this.id);
      buf.writeByte(this.status);
      buf.writeVarInt(this.entityA);
      buf.writeVarInt(this.entityB);
      buf.writeDouble(this.originA.x);
      buf.writeDouble(this.originA.y);
      buf.writeDouble(this.originA.z);
      buf.writeDouble(this.originB.x);
      buf.writeDouble(this.originB.y);
      buf.writeDouble(this.originB.z);
      buf.writeFloat(this.point);
      buf.writeVarInt(this.age);
      buf.writeByte(this.flags);
      buf.writeByte(this.pulsesA);
      buf.writeByte(this.pulsesB);
      buf.writeUtf(this.beamA, MAX_BEAM_ID);
      buf.writeUtf(this.beamB, MAX_BEAM_ID);
   }

   public boolean running() {
      return this.status == RUNNING;
   }

   public boolean flag(int flag) {
      return (this.flags & flag) != 0;
   }

   /** The clash point in the world for this snapshot. */
   public Vec3 pointAt() {
      return this.originA.add(this.originB.subtract(this.originA).scale(this.point));
   }

   private static String clip(String id) {
      if (id == null) {
         return "";
      }

      return id.length() > MAX_BEAM_ID ? id.substring(0, MAX_BEAM_ID) : id;
   }

   public void handle(Supplier<Context> supplier) {
      Context context = supplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.baranhan.viltrumitecore.client.clash.ClashClient.handle(this)));
      context.setPacketHandled(true);
   }
}
