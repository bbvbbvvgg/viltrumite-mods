package dev.baranhan.viltrumitecore.hero.ironman.legion;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import dev.baranhan.viltrumitecore.entity.LegionSuitEntity;

/**
 * Shared seam: something outside the Iron Man code took a Legion suit over for a while (a world
 * event's machine capture). While set, it runs instead of the suit's brain and takes the suit's hits
 * (they never reach the roster, so the mark keeps its durability). The owner's recall cannot pull a
 * taken suit back; every cleanup path (death, hero change, logout, dimension change) still does, and
 * clears the override first. Server side only.
 */
public interface LegionOverride {
   /** One server tick of the taken suit (the claim check already ran: the owner is still there). */
   void tick(LegionSuitEntity suit, ServerPlayer owner);

   /** A hit on the taken suit; true when it landed. */
   boolean hurt(LegionSuitEntity suit, DamageSource source, float amount);

   /** The override was cleared or the body left the world: hand the suit back (restore what it changed). */
   void released(LegionSuitEntity suit);

   /** Allies of the suit while it is taken (no friendly fire, shared targets). */
   boolean friend(@Nullable Entity other);
}
