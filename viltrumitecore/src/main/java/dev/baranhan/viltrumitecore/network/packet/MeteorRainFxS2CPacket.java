package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Meteor Rain 2.0 client cues. The server owns the rain and the city fire; this packet only says what to
 * draw and hear: the red sky on/off, the summary of burning zones (smoke columns, glow, fire roar), a
 * burning brand thrown between buildings, a far impact for players outside the meteor's tracking range,
 * and a fire flare. Old clients never see it (protocol bump).
 */
public class MeteorRainFxS2CPacket {
   /** {@code power} 1 = a rain is active around this player, 0 = it ended. */
   public static final byte SKY = 0;
   /** {@code data} = zones as (x, y, z, intensity 0..1, radius) groups; an empty list clears them. */
   public static final byte FIRE_ZONES = 1;
   /** A burning brand from {@code origin} to (data[0..2]), flight ticks in data[3]. */
   public static final byte BRAND = 2;
   /** An impact at {@code origin} heard from far away (no meteor entity on this client). */
   public static final byte FAR_IMPACT = 3;
   /** A fire flare at {@code origin}: a spot caught strongly or a window burst, {@code power} 0..1. */
   public static final byte FLARE = 4;

   public static final int ZONE_STRIDE = 5;
   public static final int MAX_ZONES = 64;
   private static final int MAX_DATA = MAX_ZONES * ZONE_STRIDE;

   public final byte kind;
   public final Vec3 origin;
   public final float power;
   public final float[] data;

   public MeteorRainFxS2CPacket(byte kind, Vec3 origin, float power, float[] data) {
      this.kind = kind;
      this.origin = origin == null ? Vec3.ZERO : origin;
      this.power = power;
      this.data = data == null ? new float[0] : data;
   }

   public MeteorRainFxS2CPacket(FriendlyByteBuf buf) {
      this.kind = buf.readByte();
      this.origin = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
      this.power = buf.readFloat();
      int count = Math.min(MAX_DATA, Math.max(0, buf.readVarInt()));
      this.data = new float[count];
      for (int i = 0; i < count; i++) {
         this.data[i] = buf.readFloat();
      }
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeByte(this.kind);
      buf.writeDouble(this.origin.x);
      buf.writeDouble(this.origin.y);
      buf.writeDouble(this.origin.z);
      buf.writeFloat(this.power);
      int count = Math.min(MAX_DATA, this.data.length);
      buf.writeVarInt(count);
      for (int i = 0; i < count; i++) {
         buf.writeFloat(this.data[i]);
      }
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
         dev.baranhan.viltrumitecore.client.meteor.MeteorRainClient.handle(this)));
      context.setPacketHandled(true);
   }
}
