package dev.baranhan.viltrumitecore.worldevent.ultron.net;

import dev.baranhan.viltrumitecore.network.CoreMessages;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Senders for the Ultron packets with a per-tick FX budget (a big battle never floods clients). */
public final class UltronNet {
   /** FX packets per server tick, all kinds together; extra effects that tick are dropped. */
   public static final int FX_PER_TICK = 40;
   private static long budgetTick = Long.MIN_VALUE;
   private static int budgetUsed;

   private UltronNet() {
   }

   /** Sends to every player of the level within {@code range} of {@code at}; false when the tick budget is spent. */
   public static boolean fx(ServerLevel level, UltronFxS2CPacket packet, Vec3 at, double range) {
      long now = level.getServer().getTickCount();
      if (now != budgetTick) {
         budgetTick = now;
         budgetUsed = 0;
      }
      if (budgetUsed >= FX_PER_TICK) {
         return false;
      }
      budgetUsed++;
      fxAlways(level, packet, at, range);
      return true;
   }

   /** Important effects (node destroyed, boss transitions) skip the budget. */
   public static void fxAlways(ServerLevel level, UltronFxS2CPacket packet, Vec3 at, double range) {
      double r2 = range * range;
      for (ServerPlayer player : level.players()) {
         if (player.position().distanceToSqr(at) <= r2) {
            CoreMessages.sendToPlayer(packet, player);
         }
      }
   }
}
