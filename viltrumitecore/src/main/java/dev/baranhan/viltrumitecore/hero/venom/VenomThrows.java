package dev.baranhan.viltrumitecore.hero.venom;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Bodies thrown by Venom's tendrils: the throw hurts when the body hits a wall or the ground hard (once, within two
 * seconds), and whoever it hits on the way. Server memory only.
 */
public final class VenomThrows {
   private static final Map<UUID, Flight> FLIGHTS = new ConcurrentHashMap<>();

   private static final class Flight {
      final LivingEntity thrower;
      final LivingEntity body;
      final float damage;
      final long until;
      /** Last airborne tick's fall state: vanilla resets {@code fallDistance} on landing before the END tick. */
      boolean airborne;
      float fall;
      double fallSpeed;
      /** Last tick's horizontal speed (squared): vanilla zeroes the blocked components before the END tick. */
      double horizontal;

      Flight(LivingEntity thrower, LivingEntity body, float damage, long until) {
         this.thrower = thrower;
         this.body = body;
         this.damage = damage;
         this.until = until;
      }

      LivingEntity thrower() {
         return this.thrower;
      }

      LivingEntity body() {
         return this.body;
      }

      float damage() {
         return this.damage;
      }

      long until() {
         return this.until;
      }
   }

   /** A landing after an airborne tick that fell far enough or fast enough. */
   static boolean hardLanding(boolean wasAirborne, float fall, double fallSpeed, boolean onGround) {
      return onGround && wasAirborne && (fall > 2.0F || fallSpeed > 0.6);
   }

   /** A wall impact: a horizontal collision while the body was still flying fast before it (or still is). */
   static boolean wallImpact(boolean collided, double horizontalBefore, double horizontalNow) {
      return collided && Math.max(horizontalBefore, horizontalNow) > 0.01;
   }

   private VenomThrows() {
   }

   public static void track(LivingEntity thrower, LivingEntity body, float damage) {
      FLIGHTS.put(body.getUUID(), new Flight(thrower, body, damage, body.level().getGameTime() + 40));
   }

   public static void tick(MinecraftServer server) {
      Iterator<Flight> it = FLIGHTS.values().iterator();
      while (it.hasNext()) {
         Flight f = it.next();
         LivingEntity b = f.body();
         if (!b.isAlive() || b.isRemoved() || b.level().getGameTime() > f.until() || !(b.level() instanceof ServerLevel)) {
            it.remove();
            continue;
         }
         Vec3 v = b.getDeltaMovement();
         double before = f.horizontal;
         f.horizontal = v.horizontalDistanceSqr();
         boolean ground = hardLanding(f.airborne, f.fall, f.fallSpeed, b.onGround());
         if (b.onGround()) {
            f.airborne = false;
            f.fall = 0.0F;
            f.fallSpeed = 0.0;
         } else {
            f.airborne = true;
            f.fall = Math.max(f.fall, b.fallDistance);
            f.fallSpeed = Math.max(f.fallSpeed, -v.y);
         }
         if (b.level().getGameTime() < f.until() - 36) {
            continue; // the first ticks leave the thrower's reach
         }
         boolean wall = wallImpact(b.horizontalCollision, before, v.horizontalDistanceSqr());
         Entity other = null;
         for (LivingEntity e : b.level().getEntitiesOfClass(LivingEntity.class, b.getBoundingBox().inflate(0.3),
            e -> e != b && e != f.thrower() && f.thrower() instanceof net.minecraft.server.level.ServerPlayer sp ? VenomTargets.mayHit(sp, e) : e != b && e != f.thrower())) {
            other = e;
            break;
         }
         if (wall || ground || other != null) {
            VenomTargets.strike(f.thrower(), b, f.damage());
            if (other instanceof LivingEntity hit) {
               VenomTargets.strike(f.thrower(), hit, f.damage() * 0.6F);
               VenomTargets.push(hit, v.scale(0.6));
            }
            VenomFx.send(f.thrower(), VenomFx.SLAM, b.position(), VenomFx.radius(b.position(), 1.5), 0.8F, 2, b.getId());
            VenomSounds.playAt(f.thrower(), b.position(), VenomSounds.SLAM, 0.9F, VenomSounds.jitter(b, 1.1F));
            it.remove();
         }
      }
   }

   public static void clear() {
      FLIGHTS.clear();
   }
}
