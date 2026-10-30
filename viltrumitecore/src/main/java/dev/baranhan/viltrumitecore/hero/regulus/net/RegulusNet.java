package dev.baranhan.viltrumitecore.hero.regulus.net;

import dev.baranhan.viltrumitecore.network.CoreMessages;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Server → client Regulus effects: everyone within {@link #RANGE} of the point (the caster included). */
public final class RegulusNet {
   public static final double RANGE = 96.0;

   private RegulusNet() {
   }

   public static void fx(ServerLevel level, int kind, Entity source, int target, Vec3 at, Vec3 dir, float power, int extra) {
      RegulusFxS2CPacket packet = new RegulusFxS2CPacket(kind, source == null ? -1 : source.getId(), target, at.x, at.y, at.z, (float) dir.x, (float) dir.y,
         (float) dir.z, power, extra);
      double r2 = RANGE * RANGE;
      for (ServerPlayer player : level.players()) {
         if (player.distanceToSqr(at) <= r2 || player == source) {
            CoreMessages.sendToPlayer(packet, player);
         }
      }
   }

   public static void fx(ServerLevel level, int kind, Entity source, int target, Vec3 at) {
      fx(level, kind, source, target, at, Vec3.ZERO, 0.0F, 0);
   }
}
