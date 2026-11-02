package dev.baranhan.viltrumitecore.worldevent.godzilla;

import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.worldevent.godzilla.net.GodzillaFxS2CPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Senders of the Godzilla effect packets with a per-tick budget ({@link GodzillaRules#FX_PER_TICK}): a heavy
 * rampage never floods the clients. Players up to {@link GodzillaRules#FX_FAR} get them; the client draws a
 * simplified version beyond {@link GodzillaRules#FX_NEAR}.
 */
public final class GodzillaNet {
   private static long budgetTick = Long.MIN_VALUE;
   private static int budgetUsed;

   private GodzillaNet() {
   }

   /** Budgeted effect; false when this tick's budget is spent. */
   public static boolean fx(ServerLevel level, int kind, Vec3 at, Vec3 d, float power, int extra) {
      long now = level.getServer().getTickCount();
      if (now != budgetTick) {
         budgetTick = now;
         budgetUsed = 0;
      }
      if (budgetUsed >= GodzillaRules.FX_PER_TICK) {
         return false;
      }
      budgetUsed++;
      always(level, kind, at, d, power, extra, GodzillaRules.FX_FAR);
      return true;
   }

   /** Effects that must arrive (arrival, death, retreat, clear). */
   public static void always(ServerLevel level, int kind, Vec3 at, Vec3 d, float power, int extra, double range) {
      GodzillaFxS2CPacket packet = new GodzillaFxS2CPacket(kind, at, d, power, extra);
      double r2 = range * range;
      for (ServerPlayer player : level.players()) {
         if (player.position().distanceToSqr(at) <= r2) {
            CoreMessages.sendToPlayer(packet, player);
         }
      }
   }

   /** Dust and debris where a smash removed blocks. */
   static void smash(ServerLevel level, Vec3 at, float radius, int blocks, String kind) {
      fx(level, GodzillaFxS2CPacket.SMASH, at, Vec3.ZERO, radius, blocks);
   }
}
