package dev.baranhan.viltrumitecore.hero;

import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.entity.PartEntity;

/**
 * One shared "may this hero's attack hit that entity" rule (AGENTS §7: no
 * per-hero copies). Both sides (the client claim asks it too; the server PvP
 * rule comes from ServerPlayer.canHarmPlayer). Today it backs Thor (MMB
 * resolver enemy, session B strikes and area hits, session A's thrown and
 * returning Mjölnir); Iron Man and Reinhard keep their own filters for now
 * (IronManTargets, ReinhardTargets) and may move to it later.
 *
 * <p>Excluded: the attacker, dead / non-attackable / spectator entities,
 * armor stands, creative or spectator players, allies in either direction
 * (teams; a tamed pet is allied to its owner), players the attacker may not
 * harm (server PvP, friendly fire), the attacker's own vehicle, passengers
 * and fellow passengers, and any {@link OwnableEntity} the attacker owns
 * (pets, a tamed horse). A {@link PartEntity} counts as its parent.
 *
 * <p>OPEN(owner): whether villagers, iron golems and passive animals are
 * valid targets (Thor F-M12 / B-M6 / A-M7). Conservative choice: unchanged,
 * they can still be hit.
 */
public final class HeroTargets {
   private HeroTargets() {
   }

   public static boolean canHit(Player attacker, @Nullable Entity entity) {
      Entity target = entity instanceof PartEntity<?> part ? part.getParent() : entity;
      if (!(target instanceof LivingEntity living) || target == attacker || !living.isAlive() || !living.isAttackable() || living.isSpectator()
         || living instanceof ArmorStand) {
         return false;
      }

      if (attacker.isAlliedTo(target) || target.isAlliedTo(attacker)) {
         return false;
      }

      if (target instanceof OwnableEntity ownable && attacker.getUUID().equals(ownable.getOwnerUUID())) {
         return false;
      }

      if (rides(attacker, target)) {
         return false;
      }

      return !(target instanceof Player other) || !other.isCreative() && attacker.canHarmPlayer(other);
   }

   /** The attacker's own vehicle, its passengers, or a fellow passenger of the same vehicle. */
   static boolean rides(Entity attacker, Entity target) {
      return attacker.getVehicle() == target || target.getVehicle() == attacker || attacker.isPassengerOfSameVehicle(target);
   }
}
