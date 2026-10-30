package dev.baranhan.viltrumitecore.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * A non-combatant (city crowd): heroes may still hit it, but no automatic target
 * selection (crosshair lock, "nearest enemy" resolvers, focus ESP, auto-locks) may
 * pick it. Shared seam so hero code never names the city package.
 */
public interface Bystander {
   static boolean is(Entity entity) {
      return entity instanceof Bystander;
   }

   /** Auto-target filter: a bystander is never a priority target for this player. */
   static boolean skipAutoTarget(Player player, Entity entity) {
      return entity instanceof Bystander;
   }
}
