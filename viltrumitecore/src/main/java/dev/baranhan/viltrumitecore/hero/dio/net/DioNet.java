package dev.baranhan.viltrumitecore.hero.dio.net;

import dev.baranhan.viltrumitecore.network.CoreMessages;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Send helpers for DIO's two client packets. */
public final class DioNet {
   private DioNet() {
   }

   public static void fx(Entity source, int kind, Vec3 at, Vec3 dir, float power, int extra) {
      CoreMessages.sendToTrackingAndSelf(new DioFxS2CPacket(kind, source.getId(), at.x, at.y, at.z, (float)dir.x, (float)dir.y, (float)dir.z, power, extra), source);
   }

   public static void fx(Entity source, int kind, Vec3 at) {
      fx(source, kind, at, Vec3.ZERO, 1.0F, 0);
   }

   /** Every player of the level within {@code range} of {@code at}. */
   public static void near(ServerLevel level, Vec3 at, double range, Object message) {
      double r2 = range * range;
      for (ServerPlayer player : level.players()) {
         if (player.position().distanceToSqr(at) <= r2) {
            CoreMessages.sendToPlayer(message, player);
         }
      }
   }
}
