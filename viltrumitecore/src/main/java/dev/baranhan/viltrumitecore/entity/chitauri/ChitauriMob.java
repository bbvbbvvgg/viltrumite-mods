package dev.baranhan.viltrumitecore.entity.chitauri;

import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.entity.PartEntity;

/** Marker of every Chitauri creature (no friendly fire, shared targets). */
public interface ChitauriMob {
   static boolean is(@Nullable Entity entity) {
      Entity e = entity instanceof PartEntity<?> part ? part.getParent() : entity;
      return e instanceof ChitauriMob;
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

   /**
    * Who the Chitauri fight: survival players, city citizens, villagers, golems, and any mob
    * that is fighting a Chitauri (hero summons). Never another Chitauri.
    */
   static boolean validTarget(@Nullable LivingEntity target) {
      if (target == null || !target.isAlive() || is(target) || target.isSpectator()
         || dev.baranhan.viltrumitecore.city.citizen.CitizenCaptivity.isHeld(target)) {
         return false;
      }
      if (target instanceof Player player) {
         return !player.isCreative();
      }
      if (target instanceof CitizenEntity || target instanceof AbstractVillager || target instanceof IronGolem) {
         return true;
      }
      return target instanceof Mob mob && is(mob.getTarget());
   }

   /**
    * Civilians as auto-targets: only outside a war. While the hive wages a war it wants the city's people alive
    * (Captors take them, pylons hold them), so soldiers leave them to the snatchers instead of gunning them down.
    * A civilian who hits a Chitauri is still fought back through the hurt-by goal.
    */
   static boolean civilianTarget(@Nullable LivingEntity target) {
      if (!validTarget(target)) {
         return false;
      }
      return !(target.level() instanceof net.minecraft.server.level.ServerLevel level)
         || dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriWar.of(level) == null;
   }
}
