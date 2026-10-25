package dev.baranhan.viltrumitecore.hero;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Server rules a client must mirror for its local input claims. The server
 * answers from its own settings; the client from the values synced at login
 * ({@code ServerRulesS2CPacket}). Until they arrive the client assumes the
 * stricter rule, so it never claims an input the server would refuse.
 */
public final class ServerRules {
   private static volatile boolean clientPvp;

   private ServerRules() {
   }

   /** May players hurt each other on this server (server.properties {@code pvp}). */
   public static boolean pvp(Player player) {
      if (player instanceof ServerPlayer serverPlayer) {
         return serverPlayer.server.isPvpAllowed();
      }

      return clientPvp;
   }

   /** Client: the values synced by the server at login. */
   public static void receive(boolean pvp) {
      clientPvp = pvp;
   }

   /** Client: log out; the next server sends its own rules. */
   public static void reset() {
      clientPvp = false;
   }
}
