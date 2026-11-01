package dev.baranhan.viltrumitecore.hero.ghostrider.bike;

import dev.baranhan.viltrumitecore.entity.Bystander;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderFx;
import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderRules;
import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderSounds;
import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderTargets;
import dev.baranhan.viltrumitecore.hero.ghostrider.Hellfire;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What the ridden Hell Cycle does to bodies in its way (server, once per tick from the accepted motion):
 * <ul>
 *    <li>Hell Dash rams every legal non-bystander target once per dash: {@link GhostRiderRules#RAM_DAMAGE}, hellfire,
 *    thrown up and ahead;</li>
 *    <li>at speed ({@link GhostRiderRules#HIT_SPEED}) hostile monsters are hit (4-7 by speed) and knocked aside;</li>
 *    <li>everybody else - city folk, villagers, animals, players without PvP - is only nudged out of the way, unhurt.
 *    Normal riding never breaks a block.</li>
 * </ul>
 */
public final class HellCycleContacts {
   private static final Map<HellCycleEntity, Map<Integer, Long>> LAST = new WeakHashMap<>();

   private HellCycleContacts() {
   }

   public static void tick(HellCycleEntity bike, ServerPlayer rider, Vec3 from, Vec3 to, double speed) {
      if (speed < 0.03 && !bike.dashing()) {
         return;
      }
      long now = bike.level().getGameTime();
      Map<Integer, Long> last = LAST.computeIfAbsent(bike, k -> new HashMap<>());
      if (now % 100 == 0) {
         last.values().removeIf(t -> now - t > 200);
      }
      double rad = Math.toRadians(bike.getYRot());
      Vec3 heading = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
      AABB box = bike.getBoundingBox().inflate(0.25, -0.1, 0.25).expandTowards(from.subtract(to)).expandTowards(heading.scale(0.6));
      boolean dash = bike.dashing();
      for (LivingEntity e : bike.level().getEntitiesOfClass(LivingEntity.class, box,
         e -> e != rider && e.isAlive() && !e.isSpectator() && e.getVehicle() != bike && !(e.isPassenger() && e.getVehicle() == rider))) {
         Long previous = last.get(e.getId());
         long since = previous == null ? Long.MAX_VALUE : now - previous;
         Vec3 side = side(bike, heading, e);
         if (dash && GhostRiderTargets.mayAutoHit(rider, e)) {
            if (since < HellCycleRules.BOOST_TICKS) {
               continue;
            }
            last.put(e.getId(), now);
            if (GhostRiderTargets.strike(rider, e, GhostRiderRules.RAM_DAMAGE)) {
               Hellfire.ignite(rider, e, GhostRiderRules.RAM_BURN_TICKS);
               GhostRiderTargets.push(e, heading.scale(0.9).add(side.scale(0.35)).add(0, 0.55, 0));
               GhostRiderFx.send(rider, GhostRiderFx.RAM, GhostRiderTargets.center(e), rider.getEyePosition(), 1.0F, null, 2, e.getId());
               GhostRiderSounds.playAt(rider, GhostRiderTargets.center(e), GhostRiderSounds.RAM, 1.0F, GhostRiderSounds.jitter(rider, 1.0F));
            }
         } else if (speed >= GhostRiderRules.HIT_SPEED && e instanceof Enemy && !Bystander.is(e) && GhostRiderTargets.mayHit(rider, e)) {
            if (since < GhostRiderRules.HIT_REPEAT_TICKS) {
               continue;
            }
            last.put(e.getId(), now);
            if (GhostRiderTargets.strike(rider, e, GhostRiderRules.bikeHitDamage(speed))) {
               GhostRiderTargets.push(e, side.scale(0.7).add(heading.scale(speed * 0.6)).add(0, 0.35, 0));
               GhostRiderSounds.playAt(rider, GhostRiderTargets.center(e), GhostRiderSounds.CHAIN_HIT, 0.8F, GhostRiderSounds.jitter(rider, 0.7F));
            }
         } else {
            if (since < GhostRiderRules.NUDGE_REPEAT_TICKS) {
               continue;
            }
            last.put(e.getId(), now);
            nudge(e, side, heading, speed);
         }
      }
   }

   /** Out of the way, unhurt: sideways off the bike's line, a little hop, no damage. */
   static void nudge(LivingEntity e, Vec3 side, Vec3 heading, double speed) {
      if (!HeroRegistry.allowsExternalControl(e, ControlKind.IMPULSE) || HeroDamage.isAnchored(e)) {
         return;
      }
      double push = GhostRiderRules.NUDGE_PUSH + Math.min(0.3, speed * 0.3);
      Vec3 v = side.scale(push).add(heading.scale(Math.min(0.25, speed * 0.3))).add(0, 0.12, 0);
      e.setDeltaMovement(e.getDeltaMovement().scale(0.2).add(v));
      e.hurtMarked = true;
      e.resetFallDistance();
   }

   /** Unit vector from the bike's centre line to the body, perpendicular to the heading. */
   static Vec3 side(HellCycleEntity bike, Vec3 heading, LivingEntity e) {
      Vec3 d = e.position().subtract(bike.position());
      Vec3 perp = new Vec3(-heading.z, 0, heading.x);
      double s = d.x * perp.x + d.z * perp.z;
      return s >= 0.0 ? perp : perp.scale(-1.0);
   }
}
