package dev.baranhan.viltrumitecore.combat.finisher;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Side test of the finisher (wave1-22, task T6), shared by the server and the client prediction so the
 * hint can never promise a finish the server would refuse.
 * <p>
 * Vanilla {@code Entity.isAlliedTo} is a scoreboard test only ({@code LivingEntity} and {@code Player}
 * do not override it; {@code TamableAnimal.isAlliedTo} answers the other way round, pet to owner), so
 * the attacker's own pet or mount has to be asked through its owner here.
 */
public final class FinisherTargets {
   private FinisherTargets() {
   }

   /** True when the target is on the attacker's side and must never be finished. */
   public static boolean allied(Player attacker, Entity target) {
      if (attacker.isAlliedTo(target)) {
         return true;
      }
      return target instanceof OwnableEntity owned && attacker.getUUID().equals(owned.getOwnerUUID());
   }
}
