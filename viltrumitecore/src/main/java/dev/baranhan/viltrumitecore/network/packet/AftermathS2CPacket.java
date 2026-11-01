package dev.baranhan.viltrumitecore.network.packet;

import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathRules;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathZone;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * City disaster aftermath: the ruin zones near a player. The server owns the zones; the client draws smoke,
 * dust, embers and crumbs and plays the soundscape from these snapshots and keeps their clocks running.
 * {@link #SYNC} adds or replaces zones, {@link #REMOVE} drops ids, {@link #CLEAR} forgets every zone
 * (world restore, dimension change).
 */
public class AftermathS2CPacket {
   public static final byte SYNC = 0;
   public static final byte REMOVE = 1;
   public static final byte CLEAR = 2;
   /** Zones per packet (the sync splits bigger updates). */
   public static final int MAX_ZONES = 16;
   private static final int MAX_IDS = 256;

   public final byte kind;
   public final List<AftermathZone.Snapshot> zones;
   public final int[] ids;

   public AftermathS2CPacket(byte kind, List<AftermathZone.Snapshot> zones, int[] ids) {
      this.kind = kind;
      this.zones = zones == null ? List.of() : zones;
      this.ids = ids == null ? new int[0] : ids;
   }

   public static AftermathS2CPacket clear() {
      return new AftermathS2CPacket(CLEAR, List.of(), new int[0]);
   }

   public AftermathS2CPacket(FriendlyByteBuf buf) {
      this.kind = buf.readByte();
      int n = Math.min(MAX_ZONES, Math.max(0, buf.readVarInt()));
      List<AftermathZone.Snapshot> list = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
         list.add(AftermathZone.Snapshot.read(buf));
      }

      this.zones = list;
      int m = Math.min(MAX_IDS, Math.max(0, buf.readVarInt()));
      this.ids = new int[m];
      for (int i = 0; i < m; i++) {
         this.ids[i] = buf.readVarInt();
      }
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeByte(this.kind);
      int n = Math.min(MAX_ZONES, this.zones.size());
      buf.writeVarInt(n);
      for (int i = 0; i < n; i++) {
         this.zones.get(i).write(buf);
      }

      int m = Math.min(MAX_IDS, this.ids.length);
      buf.writeVarInt(m);
      for (int i = 0; i < m; i++) {
         buf.writeVarInt(this.ids[i]);
      }
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
         dev.baranhan.viltrumitecore.client.aftermath.AftermathClient.handle(this)));
      context.setPacketHandled(true);
   }

   /** Seeds a snapshot may carry (shared bound, also checked by the tests). */
   public static int maxSeeds() {
      return AftermathRules.MAX_SEEDS;
   }
}
