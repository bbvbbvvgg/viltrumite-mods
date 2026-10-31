package dev.baranhan.viltrumitecore.city;

import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.city.danger.DangerMap;
import dev.baranhan.viltrumitecore.city.danger.ThreatRules;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Universal danger perception of the living city (Living City 2.0). Twice a second it looks
 * at what moves around the players in the city and turns <i>kinds</i> of things into
 * danger zones that follow them: hostile creatures (anything implementing vanilla
 * {@link Enemy}: Chitauri soldiers, chariots, leviathans, Thanos, monsters), mobs that are
 * fighting right now (legion suits, summons), flying projectiles (bolts, arrows, missiles,
 * meteors, thrown weapons), lightning, and players flying fast over the street (a
 * curiosity only). Hits, explosions, block destruction and hero combat noise still come in
 * through {@link CityEvents}; nothing here knows a particular hero or event.
 */
final class ThreatScan {
   static final int PERIOD = 10;
   /** How far around each player the scan looks (blocks, horizontal / vertical). */
   static final double RANGE = 88.0;
   static final double RANGE_Y = 96.0;
   /** Zones of moving sources live a little longer than one scan period, so they never blink. */
   static final int TRACK_LIFE = PERIOD * 3;
   /** Without players in the city the crowd anchors the scan: one box per this spacing, at most a few. */
   static final double ANCHOR_SPACING = 64.0;
   static final int MAX_ANCHORS = 6;
   private final CityManager city;
   private final Map<UUID, Vec3> lastPlayerPos = new HashMap<>();
   private final IntOpenHashSet seen = new IntOpenHashSet();
   private final IntOpenHashSet struck = new IntOpenHashSet();
   int lastFound;

   ThreatScan(CityManager city) {
      this.city = city;
   }

   void tick(long now) {
      if (now % PERIOD != 3) {
         return;
      }

      ServerLevel level = this.city.level;
      this.seen.clear();
      int found = 0;
      boolean anyPlayer = false;
      for (ServerPlayer p : level.players()) {
         if (p.isSpectator() || !this.city.profile.inArena(p.getBlockX(), level.getMinBuildHeight(), p.getBlockZ())) {
            continue;
         }

         anyPlayer = true;
         this.flyby(p, now);
         found += this.scan(level, p.getBoundingBox().inflate(RANGE, RANGE_Y, RANGE), now);
      }

      if (!anyPlayer && !this.city.citizens.isEmpty()) {
         // nobody in the city (dedicated server, /city populate from the console): look around the crowd itself
         List<Vec3> anchors = new ArrayList<>();
         for (CitizenEntity c : this.city.citizens) {
            Vec3 at = c.position();
            boolean covered = false;
            for (Vec3 a : anchors) {
               if (a.distanceToSqr(at) < ANCHOR_SPACING * ANCHOR_SPACING) {
                  covered = true;
                  break;
               }
            }

            if (!covered) {
               anchors.add(at);
               found += this.scan(level, new AABB(at, at).inflate(RANGE, RANGE_Y, RANGE), now);
               if (anchors.size() >= MAX_ANCHORS) {
                  break;
               }
            }
         }
      }

      this.lastFound = found;
      if (now % 200 == 3) {
         this.lastPlayerPos.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
         this.struck.clear();
      }
   }

   private int scan(ServerLevel level, AABB box, long now) {
      int found = 0;
      for (Entity e : level.getEntities((Entity)null, box, ThreatScan::candidate)) {
         if (this.seen.add(e.getId()) && this.classify(level, e, now)) {
            found++;
         }
      }

      return found;
   }

   private static boolean candidate(Entity e) {
      if (!e.isAlive() || e instanceof CitizenEntity || e instanceof net.minecraft.world.entity.player.Player) {
         return false;
      }

      return e instanceof Enemy || e instanceof Projectile || e instanceof LightningBolt || e instanceof Mob m && m.getTarget() != null;
   }

   /** Height of the entity above the ground (or roof) under it; 0 when the column is not loaded. */
   private static double altitude(ServerLevel level, Entity e) {
      int x = e.getBlockX();
      int z = e.getBlockZ();
      if (!level.hasChunk(x >> 4, z >> 4)) {
         return 0.0;
      }

      return Math.max(0.0, e.getY() - level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
   }

   private boolean classify(ServerLevel level, Entity e, long now) {
      DangerMap map = this.city.danger;
      if (e instanceof LightningBolt) {
         if (this.struck.add(e.getId())) {
            float[] r = ThreatRules.lightning();
            map.add(e.getX(), e.getY(), e.getZ(), r[ThreatRules.RADIUS], r[ThreatRules.NOTICE], now, 120);
            map.blast(e.getX(), e.getY(), e.getZ(), 8.0F, now);
         }

         return true;
      }

      if (e instanceof Projectile p) {
         if (p instanceof FishingHook) {
            return false;
         }

         Vec3 v = p.getDeltaMovement();
         double speed = v.length();
         float[] r = ThreatRules.projectile(speed, p.getBbWidth());
         if (r == null) {
            return false;
         }

         Vec3 at = p.position().add(v.scale(ThreatRules.projectileLead(speed)));
         map.track(e.getId(), at.x, at.y, at.z, r[ThreatRules.RADIUS], r[ThreatRules.NOTICE], now, PERIOD + 6);
         return true;
      }

      if (e instanceof LivingEntity living && living.isDeadOrDying()) {
         return false;
      }

      double alt = altitude(level, e);
      float[] r;
      double y = e.getY();
      if (e instanceof Enemy) {
         r = ThreatRules.hostile(e.getBbWidth(), e.getBbHeight(), alt);
         if (Math.max(e.getBbWidth(), e.getBbHeight() * 0.5F) >= ThreatRules.GIANT) {
            y -= alt * 0.85; // a giant above the street: the street under it is where people are
         }
      } else if (e instanceof Mob m && m.getTarget() != null && m.getTarget().isAlive() && m.distanceToSqr(m.getTarget()) < 24.0 * 24.0) {
         r = ThreatRules.fighter(e.getBbWidth(), e.getBbHeight());
      } else {
         return false;
      }

      map.track(e.getId(), e.getX(), y, e.getZ(), r[ThreatRules.RADIUS], r[ThreatRules.NOTICE], now, TRACK_LIFE);
      return true;
   }

   /** Players are not threats, but a hero racing low over the street is a sight (and a gust). */
   private void flyby(ServerPlayer p, long now) {
      Vec3 pos = p.position();
      Vec3 last = this.lastPlayerPos.put(p.getUUID(), pos);
      if (last == null || p.onGround()) {
         return;
      }

      double speed = pos.distanceTo(last) / PERIOD;
      if (speed > 12.0) {
         return; // teleport, not flight
      }

      float[] r = ThreatRules.flyby(speed, altitude(this.city.level, p));
      if (r != null) {
         this.city.danger.track(-2 - p.getId(), pos.x, pos.y, pos.z, r[ThreatRules.RADIUS], r[ThreatRules.NOTICE], now, PERIOD + 4);
      }
   }
}
