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

   private record Flight(LivingEntity thrower, LivingEntity body, float damage, long until) {
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
         if (b.level().getGameTime() < f.until() - 36) {
            continue; // the first ticks leave the thrower's reach
         }
         Vec3 v = b.getDeltaMovement();
         boolean wall = b.horizontalCollision && v.horizontalDistanceSqr() > 0.01;
         boolean ground = b.onGround() && b.fallDistance > 2.0F;
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
