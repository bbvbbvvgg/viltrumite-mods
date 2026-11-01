package dev.baranhan.viltrumitecore.worldevent.meteor;

import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.MeteorRainFxS2CPacket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * The red apocalyptic sky of a running meteor rain (Meteor Rain 2.0): every player in the rain's dimension
 * within {@link #RANGE} blocks of a rain owner gets the sky (the owner always does). Purely a view of
 * {@link MeteorRain}'s sessions: it never starts, stops or times anything. While active the cue is resent
 * every {@link #REFRESH_TICKS}, so a client that recreated its level (respawn, dimension return) gets it
 * back; the client eases the colour in and out.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class MeteorRainSky {
   public static final double RANGE = 192.0;
   static final int CHECK_TICKS = 10;
   static final int REFRESH_TICKS = 100;
   private static final Map<UUID, ResourceKey<Level>> SHOWN = new HashMap<>();
   private static final Map<UUID, Long> SENT_AT = new HashMap<>();

   private MeteorRainSky() {
   }

   /** Pure: is a viewer at {@code viewer} in range of a rain owner at {@code owner} (same dimension). */
   public static boolean inRange(Vec3 owner, Vec3 viewer) {
      double dx = owner.x - viewer.x;
      double dz = owner.z - viewer.z;
      return dx * dx + dz * dz <= RANGE * RANGE;
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }

      MinecraftServer server = event.getServer();
      long tick = server.getTickCount();
      if (tick % CHECK_TICKS != 0L || MeteorRain.sessions().isEmpty() && SHOWN.isEmpty()) {
         return;
      }

      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         boolean active = covered(server, p);
         ResourceKey<Level> shown = SHOWN.get(p.getUUID());
         ResourceKey<Level> here = p.level().dimension();
         if (active) {
            Long sent = SENT_AT.get(p.getUUID());
            if (!here.equals(shown) || sent == null || tick - sent >= REFRESH_TICKS) {
               CoreMessages.sendToPlayer(new MeteorRainFxS2CPacket(MeteorRainFxS2CPacket.SKY, p.position(), 1.0F, null), p);
               SHOWN.put(p.getUUID(), here);
               SENT_AT.put(p.getUUID(), tick);
            }
         } else if (shown != null) {
            CoreMessages.sendToPlayer(new MeteorRainFxS2CPacket(MeteorRainFxS2CPacket.SKY, p.position(), 0.0F, null), p);
            SHOWN.remove(p.getUUID());
            SENT_AT.remove(p.getUUID());
         }
      }
   }

   private static boolean covered(MinecraftServer server, ServerPlayer viewer) {
      for (MeteorRain.Session s : MeteorRain.sessions()) {
         if (!s.dimension().equals(viewer.level().dimension())) {
            continue;
         }

         if (s.owner().equals(viewer.getUUID())) {
            return true;
         }

         ServerPlayer owner = server.getPlayerList().getPlayer(s.owner());
         if (owner != null && owner.level() == viewer.level() && inRange(owner.position(), viewer.position())) {
            return true;
         }
      }

      return false;
   }

   @SubscribeEvent
   public static void onLogout(PlayerLoggedOutEvent event) {
      SHOWN.remove(event.getEntity().getUUID());
      SENT_AT.remove(event.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void onServerStopping(ServerStoppingEvent event) {
      SHOWN.clear();
      SENT_AT.clear();
   }
}
