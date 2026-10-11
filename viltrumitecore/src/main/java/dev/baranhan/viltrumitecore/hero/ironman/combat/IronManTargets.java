package dev.baranhan.viltrumitecore.hero.ironman.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * One target rule for every Iron Man area hit, beam, lock and flash: alive,
 * not a spectator, not an ally (team or own pet) and, for players, allowed by
 * the server PvP and team rules. Single-target projectiles go through
 * {@code hurt}, which already checks PvP.
 */
public final class IronManTargets {
   private IronManTargets() {
   }

   public static boolean canHit(ServerPlayer attacker, Entity entity) {
      if (!(entity instanceof LivingEntity living) || entity == attacker || !living.isAlive() || entity.isSpectator()) {
         return false;
      }

      if (attacker.isAlliedTo(entity) || entity.isAlliedTo(attacker)) {
         return false;
      }

      return !(entity instanceof Player other) || attacker.canHarmPlayer(other);
   }
}
