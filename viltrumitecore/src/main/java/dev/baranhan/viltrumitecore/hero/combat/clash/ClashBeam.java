package dev.baranhan.viltrumitecore.hero.combat.clash;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Public beam contract (wave 1, session 23). Any continuous beam (a hero ability, a boss attack, an NPC) that
 * should be able to collide head-on with another beam reports itself every server tick while it is on:
 *
 * <pre>{@code
 * ClashBeams.report(ClashBeam.of("homelander:eye_lasers", player, eye, look, 64.0, 1.0F));
 * }</pre>
 *
 * <p>No registration lifecycle: an entry that is not reported for {@link ClashRules#BEAM_EXPIRY_TICKS} ticks
 * disappears on its own. While {@link ClashBeams#struggling} is true the source should stop its own damage along
 * the ray and draw the beam to {@link ClashBeams#clashPoint}; while {@link ClashBeams#beamLocked} is true (the
 * owner just lost) the source should cut the beam. The id is for logs, lang and FX only, never for rules (A4).
 */
public interface ClashBeam {
   /** Stable id, e.g. {@code homelander:eye_lasers}. */
   String beamId();

   LivingEntity owner();

   /** Muzzle position this tick (server side). */
   Vec3 origin();

   /** Beam direction this tick; need not be normalised. */
   Vec3 direction();

   /** Beam length in blocks. */
   double range();

   /** Push strength; 1.0 is Homelander's eye lasers. Bosses may go above 1. */
   default float power() {
      return 1.0F;
   }

   /** Pressure an owner that cannot mash adds on its own (bosses, NPC). Players mash instead. */
   default double autoPressure() {
      return this.owner() instanceof Player ? 0.0 : ClashRules.AUTO_PRESSURE;
   }

   /** Called once when this beam enters a struggle. */
   default void onStruggleStart(LivingEntity opponent) {
   }

   /** Called once when the struggle ends, from this beam owner's point of view. */
   default void onStruggleEnd(LivingEntity opponent, ClashOutcome outcome) {
   }

   /** A plain value beam for sources that need no callbacks. */
   static ClashBeam of(String beamId, LivingEntity owner, Vec3 origin, Vec3 direction, double range, float power) {
      return new Simple(beamId, owner, origin, direction, range, power);
   }

   record Simple(String beamId, LivingEntity owner, Vec3 origin, Vec3 direction, double range, float power) implements ClashBeam {
   }
}
