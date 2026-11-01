package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Living City 3.0 infrastructure, server to client. {@link #STATE}: the cells without full power (packed
 * index and state), blown transformers, burning emergency lights, the alarm stage with its age, cause, siren
 * masts and foci; sent when the grid changes (at most twice a second) and to players entering the city. {@link #FX}:
 * one electric cue (an impact flash, a transformer blowing, a substation cascade, an arc) for players in range.
 * The server owns the grid; the client only draws and plays it. Old clients never see it (protocol bump).
 */
public class CityPowerS2CPacket {
   public static final byte STATE = 0;
   public static final byte FX = 1;

   public static final byte FX_IMPACT = 0;
   public static final byte FX_BLOW = 1;
   public static final byte FX_CASCADE = 2;
   public static final byte FX_ARC = 3;

   public static final int MAX_CELLS = 4096;
   public static final int MAX_TRANSFORMERS = 48;
   public static final int MAX_EMERGENCY = 192;
   public static final int MAX_SITES = 12;
   public static final int MAX_FOCI = 4;

   public final byte kind;
   // STATE
   public int originX;
   public int originZ;
   public long[] cells = new long[0];
   public long[] transformers = new long[0];
   public long[] emergency = new long[0];
   public byte stage;
   public int stageAge;
   public byte cause;
   public long[] sites = new long[0];
   public long[] foci = new long[0];
   public byte[] fociStage = new byte[0];
   public float darkFraction;
   // FX
   public byte fx;
   public double x;
   public double y;
   public double z;
   public float power;
   public int seed;

   private CityPowerS2CPacket(byte kind) {
      this.kind = kind;
   }

   public static CityPowerS2CPacket state(int originX, int originZ, long[] cells, long[] transformers, long[] emergency, byte stage, int stageAge,
      byte cause, long[] sites, long[] foci, byte[] fociStage, float darkFraction) {
      CityPowerS2CPacket p = new CityPowerS2CPacket(STATE);
      p.originX = originX;
      p.originZ = originZ;
      p.cells = cap(cells, MAX_CELLS);
      p.transformers = cap(transformers, MAX_TRANSFORMERS);
      p.emergency = cap(emergency, MAX_EMERGENCY);
      p.stage = stage;
      p.stageAge = stageAge;
      p.cause = cause;
      p.sites = cap(sites, MAX_SITES);
      int n = Math.min(MAX_FOCI, Math.min(foci.length, fociStage.length));
      p.foci = java.util.Arrays.copyOf(foci, n);
      p.fociStage = java.util.Arrays.copyOf(fociStage, n);
      p.darkFraction = darkFraction;
      return p;
   }

   public static CityPowerS2CPacket fx(byte fx, double x, double y, double z, float power, int seed) {
      CityPowerS2CPacket p = new CityPowerS2CPacket(FX);
      p.fx = fx;
      p.x = x;
      p.y = y;
      p.z = z;
      p.power = power;
      p.seed = seed;
      return p;
   }

   private static long[] cap(long[] a, int max) {
      return a.length <= max ? a : java.util.Arrays.copyOf(a, max);
   }

   /** Packs a cell index pair and its state (24 + 24 + 4 bits). */
   public static long packCell(int ix, int iz, int state) {
      return (long)(ix & 0xFFFFFF) << 28 | (long)(iz & 0xFFFFFF) << 4 | state & 0xF;
   }

   public static int cellX(long packed) {
      return (int)(packed >>> 28) << 8 >> 8;
   }

   public static int cellZ(long packed) {
      return ((int)(packed >>> 4) & 0xFFFFFF) << 8 >> 8;
   }

   public static int cellState(long packed) {
      return (int)(packed & 0xF);
   }

   public CityPowerS2CPacket(FriendlyByteBuf buf) {
      this.kind = buf.readByte();
      if (this.kind == STATE) {
         this.originX = buf.readInt();
         this.originZ = buf.readInt();
         this.cells = readLongs(buf, MAX_CELLS);
         this.transformers = readLongs(buf, MAX_TRANSFORMERS);
         this.emergency = readLongs(buf, MAX_EMERGENCY);
         this.stage = buf.readByte();
         this.stageAge = buf.readVarInt();
         this.cause = buf.readByte();
         this.sites = readLongs(buf, MAX_SITES);
         this.foci = readLongs(buf, MAX_FOCI);
         int n = Math.min(MAX_FOCI, Math.max(0, buf.readVarInt()));
         this.fociStage = new byte[n];
         buf.readBytes(this.fociStage);
         this.darkFraction = buf.readFloat();
      } else {
         this.fx = buf.readByte();
         this.x = buf.readDouble();
         this.y = buf.readDouble();
         this.z = buf.readDouble();
         this.power = buf.readFloat();
         this.seed = buf.readInt();
      }
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeByte(this.kind);
      if (this.kind == STATE) {
         buf.writeInt(this.originX);
         buf.writeInt(this.originZ);
         writeLongs(buf, this.cells, MAX_CELLS);
         writeLongs(buf, this.transformers, MAX_TRANSFORMERS);
         writeLongs(buf, this.emergency, MAX_EMERGENCY);
         buf.writeByte(this.stage);
         buf.writeVarInt(Math.max(0, this.stageAge));
         buf.writeByte(this.cause);
         writeLongs(buf, this.sites, MAX_SITES);
         writeLongs(buf, this.foci, MAX_FOCI);
         int n = Math.min(MAX_FOCI, this.fociStage.length);
         buf.writeVarInt(n);
         buf.writeBytes(this.fociStage, 0, n);
         buf.writeFloat(this.darkFraction);
      } else {
         buf.writeByte(this.fx);
         buf.writeDouble(this.x);
         buf.writeDouble(this.y);
         buf.writeDouble(this.z);
         buf.writeFloat(this.power);
         buf.writeInt(this.seed);
      }
   }

   private static long[] readLongs(FriendlyByteBuf buf, int max) {
      int n = buf.readVarInt();
      if (n < 0 || n > max) {
         throw new IllegalArgumentException("CityPowerS2CPacket: " + n + " entries (max " + max + ")");
      }
      long[] out = new long[n];
      for (int i = 0; i < n; i++) {
         out[i] = buf.readLong();
      }
      return out;
   }

   private static void writeLongs(FriendlyByteBuf buf, long[] a, int max) {
      int n = Math.min(max, a.length);
      buf.writeVarInt(n);
      for (int i = 0; i < n; i++) {
         buf.writeLong(a[i]);
      }
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
         dev.baranhan.viltrumitecore.client.city.power.CityPowerClient.handle(this)));
      context.setPacketHandled(true);
   }
}
