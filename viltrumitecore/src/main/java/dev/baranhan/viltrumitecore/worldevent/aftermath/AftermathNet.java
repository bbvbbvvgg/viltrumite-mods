package dev.baranhan.viltrumitecore.worldevent.aftermath;

import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.AftermathS2CPacket;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Sends each player the ruin zones near them: every 2 s (at once after a change) a diff against what that
 * client already has, so a quiet city costs nothing on the wire. Zones farther than {@link #RANGE} blocks
 * (to their edge) are not sent; leaving the range removes them on the client, as do world restores and
 * dimension changes.
 */
final class AftermathNet {
   static final double RANGE = 224.0;
   private static final Map<UUID, View> VIEWS = new HashMap<>();
   private static final Set<ResourceKey<Level>> URGENT = new HashSet<>();

   private AftermathNet() {
   }

   private static final class View {
      ResourceKey<Level> dimension;
      final Int2IntOpenHashMap known = new Int2IntOpenHashMap();
   }

   static void urgent(ServerLevel level) {
      URGENT.add(level.dimension());
   }

   static void tick(ServerLevel level, AftermathData data, long now) {
      if (now % 10 != 0) {
         return;
      }

      boolean urgent = URGENT.remove(level.dimension());
      double scale = CityAftermath.scale();
      for (ServerPlayer p : level.players()) {
         View v = VIEWS.computeIfAbsent(p.getUUID(), u -> new View());
         if (v.dimension != level.dimension()) {
            CoreMessages.sendToPlayer(AftermathS2CPacket.clear(), p);

            v.dimension = level.dimension();
            v.known.clear();
            urgent = true;
         }

         if (!urgent && (now / 10 + p.getId()) % 4 != 0) {
            continue;
         }

         sync(p, v, data, now, scale);
      }
   }

   private static void sync(ServerPlayer p, View v, AftermathData data, long now, double scale) {
      List<AftermathZone.Snapshot> changed = null;
      IntArrayList live = new IntArrayList();
      for (AftermathZone zn : data.zones) {
         double reach = RANGE + zn.radius;
         if (zn.distanceSq(p.getX(), p.getY(), p.getZ()) > reach * reach || zn.stage(now, scale) == AftermathStage.GONE) {
            continue;
         }

         live.add(zn.id);
         if (!v.known.containsKey(zn.id) || v.known.get(zn.id) != zn.version) {
            if (changed == null) {
               changed = new ArrayList<>();
            }

            changed.add(zn.snapshot(now, scale));
            v.known.put(zn.id, zn.version);
         }
      }

      if (v.known.size() > live.size()) {
         IntArrayList gone = new IntArrayList();
         for (int id : v.known.keySet()) {
            if (!live.contains(id)) {
               gone.add(id);
            }
         }

         for (int i = 0; i < gone.size(); i++) {
            v.known.remove(gone.getInt(i));
         }

         if (!gone.isEmpty()) {
            CoreMessages.sendToPlayer(new AftermathS2CPacket(AftermathS2CPacket.REMOVE, List.of(), gone.toIntArray()), p);
         }
      }

      if (changed != null) {
         for (int i = 0; i < changed.size(); i += AftermathS2CPacket.MAX_ZONES) {
            List<AftermathZone.Snapshot> part = changed.subList(i, Math.min(changed.size(), i + AftermathS2CPacket.MAX_ZONES));
            CoreMessages.sendToPlayer(new AftermathS2CPacket(AftermathS2CPacket.SYNC, new ArrayList<>(part), new int[0]), p);
         }
      }
   }

   /** Every client in the level forgets its zones (they are gone). */
   static void clear(ServerLevel level) {
      for (ServerPlayer p : level.players()) {
         View v = VIEWS.get(p.getUUID());
         if (v != null) {
            v.known.clear();
         }

         CoreMessages.sendToPlayer(AftermathS2CPacket.clear(), p);
      }
   }

   /** Respawn, dimension change: the client may have a new level, so it gets everything again. */
   static void reset(ServerPlayer p) {
      View v = VIEWS.get(p.getUUID());
      if (v != null) {
         v.dimension = null;
         v.known.clear();
      }
   }

   static void logout(UUID player) {
      VIEWS.remove(player);
   }

   static void serverStopped() {
      VIEWS.clear();
      URGENT.clear();
   }

   /** Zones this player's client has now (QA). */
   static int known(ServerPlayer p) {
      View v = VIEWS.get(p.getUUID());
      return v == null ? 0 : v.known.size();
   }
}
