package dev.baranhan.viltrumitecore.hero.thor.scene;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Bodies thrown out of a grab (far throw, barrage finisher): flown by real
 * physics and watched until they hit a surface (damage scaled by speed,
 * allowed destruction around the impact) or another body on the way.
 * Server main thread.
 */
final class ThrownBodies {
   private static final class Flight {
      final UUID thor;
      final UUID body;
      final ServerLevel level;
      final float damage;
      final double speed;
      final Set<Integer> struck = new HashSet<>();
      Vec3 last;
      int ticks;

      Flight(ServerPlayer thor, LivingEntity body, float damage, double speed) {
         this.thor = thor.getUUID();
         this.body = body.getUUID();
         this.level = thor.serverLevel();
         this.damage = damage;
         this.speed = speed;
         this.last = body.position();
      }
   }

   private static final List<Flight> FLIGHTS = new ArrayList<>();

   private ThrownBodies() {
   }

   static void launch(ServerPlayer thor, LivingEntity body, Vec3 velocity, float damage) {
      if (!HeroRegistry.allowsImpulse(body)) {
         SceneFx.impact(thor, body, body.position(), damage, 0);
         return;
      }

      FLIGHTS.removeIf(f -> f.body.equals(body.getUUID()));
      SceneFx.move(body, velocity);
      FLIGHTS.add(new Flight(thor, body, damage, velocity.length()));
      SceneFx.trail(thor, body);
   }

   static void tick(MinecraftServer server) {
      FLIGHTS.removeIf(flight -> step(server, flight));
   }

   /** True when the flight is over. */
   private static boolean step(MinecraftServer server, Flight flight) {
      Entity entity = flight.level.getEntity(flight.body);
      ServerPlayer thor = server.getPlayerList().getPlayer(flight.thor);
      if (!(entity instanceof LivingEntity body) || !body.isAlive() || thor == null || ++flight.ticks > ThorSceneConfig.throwFlightTicks) {
         return true;
      }

      Vec3 now = body.position();
      double moved = now.distanceTo(flight.last);
      flight.last = now;
      AABB box = body.getBoundingBox().inflate(0.4);
      for (Entity other : flight.level.getEntities(body, box, e -> e instanceof LivingEntity && e != thor && e.isAlive())) {
         if (flight.struck.add(other.getId())) {
            SceneFx.bodyHit(thor, (LivingEntity)other, body.getDeltaMovement());
         }
      }

      boolean stopped = flight.ticks > 2 && moved < flight.speed * 0.3;
      if (body.horizontalCollision || body.verticalCollision && flight.ticks > 2 || stopped) {
         double share = Math.max(0.4, Math.min(1.0, Math.max(moved, body.getDeltaMovement().length()) / Math.max(0.1, flight.speed)));
         SceneFx.impact(thor, body, now, (float)(flight.damage * share), ThorSceneConfig.slamCraterRadius);
         return true;
      }

      return false;
   }

   static void clear() {
      FLIGHTS.clear();
   }
}
