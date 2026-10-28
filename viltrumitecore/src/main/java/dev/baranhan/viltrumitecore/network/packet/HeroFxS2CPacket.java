package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.client.render.vfx.HeroImpactFx;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * One-shot hero world FX, shared by every hero (shard spray, landing
 * shockwave, slam, blade, launch puff, flash). The server resolves gameplay
 * instantly; this packet only tells clients what to draw, which sound to
 * place and how hard to shake the camera. Send it through
 * {@link dev.baranhan.viltrumitecore.hero.fx.HeroFx}, never directly.
 */
public class HeroFxS2CPacket {
   public static final byte SHARDS = 0;
   public static final byte SHOCKWAVE = 1;
   public static final byte SLAM = 2;
   public static final byte BLADE = 3;
   public static final byte LAUNCH = 4;
   public static final byte FLASH = 5;
   /** Every entity id in {@code points} (HeroFx.encodeIds) holds still for {@code power} ticks (client HitStop). */
   public static final byte HIT_STOP = 6;
   /** A white horizontal sonic ring at the origin (client AirTrailVfx). */
   public static final byte SONIC_RING = 7;
   /** A white air trail behind each entity id in {@code points} (client AirTrailVfx). */
   public static final byte AIR_TRAIL = 8;
   /** A jagged electric spark line from the origin to {@code points[0..2]}; drawn locally (one packet, not one per spark). */
   public static final byte ELECTRIC_ARC = 9;
   /** A flat ring of electric sparks of radius {@code points[0]} around the origin; drawn locally. */
   public static final byte ELECTRIC_RING = 10;
   /** Sparks crawling around the origin, {@code power} = spark count (capped on the client). */
   public static final byte ELECTRIC_CRACKLE = 11;
   /**
    * A lightning bolt striking the origin from {@code points[0]} blocks above
    * (Thor B-m6): drawn and heard only on the client — a jagged branched
    * channel, a local flash and a local thunder clap. No LightningBolt entity
    * exists, so nothing vanilla happens (no copper scraping, no lightning rod,
    * no sculk GameEvent, no sky flash, no 10000-volume thunder). {@code power}
    * scales the brightness (0.25..2).
    */
   public static final byte ELECTRIC_BOLT = 12;

   private static final int MAX_POINTS = 64;

   public final byte kind;
   public final Vec3 origin;
   public final float power;
   public final int blockStateId;
   /** Flat xyz triples: shard end points, the blade end, {radius, 0, 0}, or entity ids (HeroFx.encodeIds). */
   public final float[] points;
   /** Sound event registry id for the pass-by whiz, or -1 for none. */
   public final int soundId;

   public HeroFxS2CPacket(byte kind, Vec3 origin, float power, int blockStateId, float[] points, int soundId) {
      this.kind = kind;
      this.origin = origin;
      this.power = power;
      this.blockStateId = blockStateId;
      this.points = points == null ? new float[0] : points;
      this.soundId = soundId;
   }

   public HeroFxS2CPacket(FriendlyByteBuf buf) {
      this.kind = buf.readByte();
      this.origin = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
      this.power = buf.readFloat();
      this.blockStateId = buf.readVarInt();
      this.soundId = buf.readVarInt() - 1;
      int count = Math.min(MAX_POINTS, Math.max(0, buf.readVarInt()));
      this.points = new float[count * 3];
      for (int i = 0; i < this.points.length; i++) {
         this.points[i] = buf.readFloat();
      }
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeByte(this.kind);
      buf.writeDouble(this.origin.x);
      buf.writeDouble(this.origin.y);
      buf.writeDouble(this.origin.z);
      buf.writeFloat(this.power);
      buf.writeVarInt(this.blockStateId);
      buf.writeVarInt(this.soundId + 1);
      int count = Math.min(MAX_POINTS, this.points.length / 3);
      buf.writeVarInt(count);
      for (int i = 0; i < count * 3; i++) {
         buf.writeFloat(this.points[i]);
      }
   }

   public void handle(Supplier<Context> supplier) {
      Context context = supplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HeroImpactFx.handle(this)));
      context.setPacketHandled(true);
   }
}
