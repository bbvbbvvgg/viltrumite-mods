package dev.baranhan.viltrumitecore.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server performance for the {@code /vperf overlay} of one player (protocol 32): {@link #SHOW} / {@link #HIDE}
 * switch the overlay, {@link #STATS} refreshes it once a second — TPS, tick time, load, loaded entities and the
 * server's work budgets of the last tick.
 */
public class PerfStatsS2CPacket {
   public static final byte STATS = 0;
   public static final byte SHOW = 1;
   public static final byte HIDE = 2;
   public static final int MAX_BUDGETS = 12;
   public final byte kind;
   public final float tps;
   public final float avgMs;
   public final float p95Ms;
   public final float maxMs;
   public final float pressure;
   public final int entities;
   public final String[] names;
   public final int[] used;
   public final int[] capacity;
   public final int[] denied;

   public PerfStatsS2CPacket(byte kind, float tps, float avgMs, float p95Ms, float maxMs, float pressure, int entities,
      String[] names, int[] used, int[] capacity, int[] denied) {
      this.kind = kind;
      this.tps = tps;
      this.avgMs = avgMs;
      this.p95Ms = p95Ms;
      this.maxMs = maxMs;
      this.pressure = pressure;
      this.entities = entities;
      this.names = names;
      this.used = used;
      this.capacity = capacity;
      this.denied = denied;
   }

   public PerfStatsS2CPacket(FriendlyByteBuf buf) {
      this.kind = buf.readByte();
      this.tps = buf.readFloat();
      this.avgMs = buf.readFloat();
      this.p95Ms = buf.readFloat();
      this.maxMs = buf.readFloat();
      this.pressure = buf.readFloat();
      this.entities = buf.readVarInt();
      int n = Math.max(0, Math.min(MAX_BUDGETS, buf.readVarInt()));
      this.names = new String[n];
      this.used = new int[n];
      this.capacity = new int[n];
      this.denied = new int[n];
      for (int i = 0; i < n; i++) {
         this.names[i] = buf.readUtf(64);
         this.used[i] = buf.readVarInt();
         this.capacity[i] = buf.readVarInt();
         this.denied[i] = buf.readVarInt();
      }
   }

   public int budgets() {
      return Math.min(MAX_BUDGETS, Math.min(this.names.length, Math.min(this.used.length, Math.min(this.capacity.length, this.denied.length))));
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeByte(this.kind);
      buf.writeFloat(this.tps);
      buf.writeFloat(this.avgMs);
      buf.writeFloat(this.p95Ms);
      buf.writeFloat(this.maxMs);
      buf.writeFloat(this.pressure);
      buf.writeVarInt(Math.max(0, this.entities));
      int n = this.budgets();
      buf.writeVarInt(n);
      for (int i = 0; i < n; i++) {
         String name = this.names[i] == null ? "" : this.names[i];
         buf.writeUtf(name.length() > 64 ? name.substring(0, 64) : name, 64);
         buf.writeVarInt(Math.max(0, this.used[i]));
         buf.writeVarInt(Math.max(0, this.capacity[i]));
         buf.writeVarInt(Math.max(0, this.denied[i]));
      }
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
         dev.baranhan.viltrumitecore.client.perf.PerfClient.onPacket(this)));
      context.setPacketHandled(true);
   }
}
