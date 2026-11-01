package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Compact structural-collapse cue (protocol 26). The server owns every block change and every phase tick;
 * this packet only tells clients what to draw and hear. START carries the section as a bitset over its
 * bounding box (about 3 KB at the 1600-block limit), so every client builds its own falling fragments from
 * the blocks it still sees. Later kinds carry only the id and the phase.
 */
public class CityCollapseS2CPacket {
   /** Phase 1 (impact) begins; carries the section and the timeline. */
   public static final byte START = 0;
   /** Phase 3: the section is removed now; fragments fall. */
   public static final byte COLLAPSE = 1;
   /** The section hits the ground: slam, dust along the street, debris. */
   public static final byte IMPACT = 2;
   /** Phase 4 is over (or aborted): drop the scene. */
   public static final byte END = 3;

   public static final int MAX_BITS_LONGS = 2048;

   public final byte kind;
   public final long id;
   public final Vec3 center;
   // START only
   public final int severity, variant;
   public final long seed;
   public final float fallX, fallZ;
   public final int minX, minY, minZ, sizeX, sizeY, sizeZ;
   public final long[] bits;
   public final int creakTicks, collapseAt, fallTicks, groundY;
   public final boolean far;

   private CityCollapseS2CPacket(byte kind, long id, Vec3 center, int severity, int variant, long seed, float fallX, float fallZ, int minX, int minY, int minZ,
      int sizeX, int sizeY, int sizeZ, long[] bits, int creakTicks, int collapseAt, int fallTicks, int groundY, boolean far) {
      this.kind = kind;
      this.id = id;
      this.center = center;
      this.severity = severity;
      this.variant = variant;
      this.seed = seed;
      this.fallX = fallX;
      this.fallZ = fallZ;
      this.minX = minX;
      this.minY = minY;
      this.minZ = minZ;
      this.sizeX = sizeX;
      this.sizeY = sizeY;
      this.sizeZ = sizeZ;
      this.bits = bits;
      this.creakTicks = creakTicks;
      this.collapseAt = collapseAt;
      this.fallTicks = fallTicks;
      this.groundY = groundY;
      this.far = far;
   }

   public static CityCollapseS2CPacket start(long id, Vec3 center, int severity, int variant, long seed, float fallX, float fallZ, int minX, int minY,
      int minZ, int sizeX, int sizeY, int sizeZ, long[] bits, int creakTicks, int collapseAt, int fallTicks, int groundY, boolean far) {
      return new CityCollapseS2CPacket(START, id, center, severity, variant, seed, fallX, fallZ, minX, minY, minZ, sizeX, sizeY, sizeZ, bits, creakTicks,
         collapseAt, fallTicks, groundY, far);
   }

   /** COLLAPSE / IMPACT / END. {@code severity} is repeated so a late or far client can still scale its cue. */
   public static CityCollapseS2CPacket phase(byte kind, long id, Vec3 center, int severity, int groundY, boolean far) {
      return new CityCollapseS2CPacket(kind, id, center, severity, 0, 0L, 0.0F, 0.0F, 0, 0, 0, 0, 0, 0, new long[0], 0, 0, 0, groundY, far);
   }

   public CityCollapseS2CPacket(FriendlyByteBuf buf) {
      this.kind = buf.readByte();
      this.id = buf.readVarLong();
      this.center = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
      this.severity = buf.readByte();
      this.groundY = buf.readVarInt();
      this.far = buf.readBoolean();
      if (this.kind == START) {
         this.variant = buf.readByte();
         this.seed = buf.readLong();
         this.fallX = buf.readFloat();
         this.fallZ = buf.readFloat();
         this.minX = buf.readVarInt();
         this.minY = buf.readVarInt();
         this.minZ = buf.readVarInt();
         this.sizeX = buf.readUnsignedByte();
         this.sizeY = buf.readUnsignedByte();
         this.sizeZ = buf.readUnsignedByte();
         int n = Math.min(MAX_BITS_LONGS, Math.max(0, buf.readVarInt()));
         this.bits = new long[n];
         for (int i = 0; i < n; i++) {
            this.bits[i] = buf.readLong();
         }
         this.creakTicks = buf.readVarInt();
         this.collapseAt = buf.readVarInt();
         this.fallTicks = buf.readVarInt();
      } else {
         this.variant = 0;
         this.seed = 0L;
         this.fallX = 0.0F;
         this.fallZ = 0.0F;
         this.minX = this.minY = this.minZ = 0;
         this.sizeX = this.sizeY = this.sizeZ = 0;
         this.bits = new long[0];
         this.creakTicks = this.collapseAt = this.fallTicks = 0;
      }
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeByte(this.kind);
      buf.writeVarLong(this.id);
      buf.writeDouble(this.center.x);
      buf.writeDouble(this.center.y);
      buf.writeDouble(this.center.z);
      buf.writeByte(this.severity);
      buf.writeVarInt(this.groundY);
      buf.writeBoolean(this.far);
      if (this.kind == START) {
         buf.writeByte(this.variant);
         buf.writeLong(this.seed);
         buf.writeFloat(this.fallX);
         buf.writeFloat(this.fallZ);
         buf.writeVarInt(this.minX);
         buf.writeVarInt(this.minY);
         buf.writeVarInt(this.minZ);
         buf.writeByte(this.sizeX);
         buf.writeByte(this.sizeY);
         buf.writeByte(this.sizeZ);
         int n = Math.min(MAX_BITS_LONGS, this.bits.length);
         buf.writeVarInt(n);
         for (int i = 0; i < n; i++) {
            buf.writeLong(this.bits[i]);
         }
         buf.writeVarInt(this.creakTicks);
         buf.writeVarInt(this.collapseAt);
         buf.writeVarInt(this.fallTicks);
      }
   }

   /** Index of a box cell in {@link #bits} (x fastest, then z, then y). */
   public static int bitIndex(int dx, int dy, int dz, int sizeX, int sizeZ) {
      return (dy * sizeZ + dz) * sizeX + dx;
   }

   public boolean has(int dx, int dy, int dz) {
      int i = bitIndex(dx, dy, dz, this.sizeX, this.sizeZ);
      int w = i >>> 6;
      return w < this.bits.length && (this.bits[w] >>> (i & 63) & 1L) != 0L;
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
         dev.baranhan.viltrumitecore.client.collapse.CollapseClient.handle(this)));
      context.setPacketHandled(true);
   }
}
