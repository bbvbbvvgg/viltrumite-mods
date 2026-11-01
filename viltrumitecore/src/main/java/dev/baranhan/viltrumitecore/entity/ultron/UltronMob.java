package dev.baranhan.viltrumitecore.entity.ultron;

import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.entity.LegionSuitEntity;
import dev.baranhan.viltrumitecore.entity.SummonOwners;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronCapture;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.entity.PartEntity;

/** Marker of everything that fights for Ultron (machines, nodes, his body, and the machines he captured): no friendly fire, shared targets. */
public interface UltronMob {
   /** One of Ultron's own (machine, node, projectile owner chain is checked by the callers). */
   static boolean is(@Nullable Entity entity) {
      Entity e = entity instanceof PartEntity<?> part ? part.getParent() : entity;
      return e instanceof UltronMob;
   }

   /** Ultron's own, or a golem / Legion suit his uplinks control right now. */
   static boolean side(@Nullable Entity entity) {
      return is(entity) || UltronCapture.controlled(entity);
   }

   /**
    * Who the machines fight: survival players, citizens, villagers, free golems and Legion suits, the heroes'
    * summons, and any mob that is fighting a machine. Never one of Ultron's own or a machine he controls.
    */
   static boolean validTarget(@Nullable LivingEntity target) {
      if (target == null || !target.isAlive() || side(target) || target.isSpectator() || target.isInvulnerable()
         || dev.baranhan.viltrumitecore.city.citizen.CitizenCaptivity.isHeld(target)) {
         // A person another world event holds (a Chitauri captor carries them, a containment field keeps them) is
         // not a combatant: the machines leave them alone, like the Chitauri themselves (integration of #115).
         return false;
      }
      if (target instanceof Player player) {
         return !player.isCreative();
      }
      if (target instanceof CitizenEntity || target instanceof AbstractVillager || target instanceof IronGolem || target instanceof LegionSuitEntity
         || SummonOwners.isSummon(target)) {
         return true;
      }
      return target instanceof Mob mob && side(mob.getTarget());
   }

   /** A hero holds it (Viltrumite grab) or a control anchors it: its own steering stays off. */
   static boolean heldByHero(LivingEntity entity) {
      for (Player player : entity.level().players()) {
         if (player instanceof dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer core && core.getGrabbedTarget() == entity) {
            return true;
         }
      }
      return dev.baranhan.viltrumitecore.hero.HeroDamage.isAnchored(entity);
   }
}
