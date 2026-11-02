package dev.baranhan.viltrumitecore.worldevent.godzilla;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.profile.CityProfile;
import dev.baranhan.viltrumitecore.entity.godzilla.GodzillaEntity;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriTerrain;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * What the kaiju does next when no move runs: fight the target (zone in the body frame → move, else close in and
 * face it), or explore (walk between waypoints through the tallest blocks it can find near the players, look at the
 * heroes, roar, ram a tall building, burn a skyscraper now and then), or walk away when the event is over.
 */
final class GodzillaBrain {
   private GodzillaBrain() {
   }

   static void decide(ServerLevel level, GodzillaSession s, GodzillaEntity e) {
      if (s.phase == GodzillaPhase.DEATH) {
         e.walkTo(null);
         return;
      }
      if (s.phase == GodzillaPhase.RETREAT) {
         retreat(level, s, e);
         return;
      }
      // a phase change roar (arrival, rage, finale)
      if (s.cdRoar <= 0 && (s.phase == GodzillaPhase.ARRIVAL && s.phaseTick > 30 || s.phase.enraged() && s.phaseTick < 60)) {
         e.walkTo(null);
         s.start(e, GodzillaMove.ROAR, 0);
         return;
      }
      LivingEntity target = s.passive ? null : s.target(level);
      if (target != null && (s.phase.fighting() || s.phase == GodzillaPhase.EXPLORE)) {
         fight(level, s, e, target);
         return;
      }
      explore(level, s, e);
   }

   /** Body-frame offset of a point: left, up from the ground under the body, forward (blocks). */
   static double[] frame(GodzillaEntity e, Vec3 p) {
      Vec3 fw = GodzillaEntity.forward(e.getYRot());
      Vec3 lf = GodzillaEntity.left(e.getYRot());
      double dx = p.x - e.getX();
      double dz = p.z - e.getZ();
      return new double[]{dx * lf.x + dz * lf.z, p.y - e.getY(), dx * fw.x + dz * fw.z};
   }

   private static void fight(ServerLevel level, GodzillaSession s, GodzillaEntity e, LivingEntity target) {
      Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
      double[] f = frame(e, at);
      GodzillaRules.Zone zone = GodzillaRules.classify(f[0], f[1], f[2]);
      e.lookAt(at, false);
      e.faceTowards(at);
      GodzillaMove move = GodzillaRules.choose(zone, s.cooldowns(), s.rage(), s.targetThreat(), s.healthShare());
      if (move == GodzillaMove.CHARGE && !GodzillaBreath.worthIt(e, at)) {
         move = GodzillaMove.NONE;
      }
      if (move != GodzillaMove.NONE) {
         e.walkTo(null);
         if (move == GodzillaMove.CHARGE) {
            s.breath.begin(s, e, target, null);
         }
         int arg = move == GodzillaMove.TAIL ? (f[0] >= 0.0 ? 1 : -1) : move == GodzillaMove.STOMP ? (f[0] >= 0.0 ? 1 : -1) : 0;
         s.start(e, move, arg);
         return;
      }
      // close in: ground heroes to arm reach, fliers to under them, never closer than the feet
      double flat = Math.sqrt(f[0] * f[0] + f[2] * f[2]);
      double want = zone == GodzillaRules.Zone.AIR_NEAR || zone == GodzillaRules.Zone.AIR_FAR ? 0.25 * GodzillaRules.HEIGHT : 0.32 * GodzillaRules.HEIGHT;
      if (flat > want + 4.0) {
         Vec3 dir = new Vec3(at.x - e.getX(), 0.0, at.z - e.getZ()).normalize();
         e.walkTo(new Vec3(at.x - dir.x * want, e.getY(), at.z - dir.z * want));
      } else {
         e.walkTo(null);
         // a building between it and the hero: ram it
         if (s.cdSlam <= 0 && tallAhead(level, e) > 0.22F * GodzillaRules.HEIGHT) {
            s.start(e, GodzillaMove.SLAM, 0);
         }
      }
   }

   private static void explore(ServerLevel level, GodzillaSession s, GodzillaEntity e) {
      s.waypointAge++;
      Vec3 here = e.position();
      boolean reached = s.waypoint != null && horizontal(here, s.waypoint) < GodzillaRules.WAYPOINT_REACHED;
      if (reached && s.phase != GodzillaPhase.ARRIVAL) {
         // a survey pause: it stops among the ruins and looks over the city before the next leg
         s.restUntil = s.clock + GodzillaRules.SURVEY_MIN + s.random.nextInt(GodzillaRules.SURVEY_SPREAD);
      }
      if (s.waypoint == null || s.waypointAge > 1800 || reached || s.stuck > 100) {
         s.waypoint = pickWaypoint(level, s, e);
         s.waypointAge = 0;
         s.stuck = 0;
      }
      ServerPlayer watcher = nearestPlayer(level, e, GodzillaRules.FX_NEAR);
      // set pieces while someone watches: a roar, a rammed building, a burnt skyscraper
      if (watcher != null && s.phase != GodzillaPhase.ARRIVAL && !s.passive) {
         if (s.cdSlam <= 0 && s.random.nextInt(40) == 0 && tallAhead(level, e) > 0.25F * GodzillaRules.HEIGHT) {
            e.walkTo(null);
            s.start(e, GodzillaMove.SLAM, 0);
            return;
         }
         if (s.cdExploreBreath <= 0 && s.cdBreath <= 0 && s.random.nextInt(60) == 0) {
            Vec3 tower = GodzillaBreath.towerAhead(level, e, s.random);
            if (tower != null) {
               e.walkTo(null);
               s.breath.begin(s, e, null, tower);
               s.start(e, GodzillaMove.CHARGE, 0);
               s.cdExploreBreath = 2400 + s.random.nextInt(1200);
               return;
            }
         }
         if (s.cdRoar <= 0 && s.random.nextInt(200) == 0) {
            e.walkTo(null);
            s.start(e, GodzillaMove.ROAR, 0);
            return;
         }
      }
      if (s.waypoint == null || s.clock < s.restUntil) {
         e.walkTo(null);
         e.faceTowards(null);
         if (watcher != null && (s.clock / 90 & 1) == 0) {
            e.lookAt(watcher.getEyePosition(), false);
         } else {
            // the head pans slowly over the skyline
            float pan = Mth.sin(s.clock * 0.025F) * 50.0F;
            Vec3 fw = GodzillaEntity.forward(e.getYRot() + pan);
            e.lookAt(here.add(fw.scale(90.0)).add(0.0, GodzillaRules.HEIGHT * 0.5, 0.0), false);
         }
         return;
      }
      e.walkTo(s.waypoint);
      e.faceTowards(null);
      // look at the heroes now and then, else ahead and a little down at the streets
      if (watcher != null && (s.cdLook <= 0 || s.cdLook > 60)) {
         if (s.cdLook <= 0) {
            s.cdLook = 160 + s.random.nextInt(160);
         }
         e.lookAt(watcher.getEyePosition(), false);
      } else {
         Vec3 fw = GodzillaEntity.forward(e.getYRot());
         e.lookAt(here.add(fw.scale(80.0)).add(0.0, GodzillaRules.HEIGHT * 0.45, 0.0), false);
      }
      // walking against something it cannot cross (unloaded ground)
      if (!e.flag(GodzillaEntity.FLAG_WALKING)) {
         s.stuck++;
      } else {
         s.stuck = Math.max(0, s.stuck - 1);
      }
   }

   private static void retreat(ServerLevel level, GodzillaSession s, GodzillaEntity e) {
      Vec3 here = e.position();
      Vec3 away = new Vec3(s.home.x - here.x, 0.0, s.home.z - here.z);
      if (away.lengthSqr() < 64.0 * 64.0) {
         // home reached (or it started there): straight away from the nearest hero
         ServerPlayer p = nearestPlayer(level, e, 600.0);
         away = p == null ? GodzillaEntity.forward(e.getYRot()) : new Vec3(here.x - p.getX(), 0.0, here.z - p.getZ());
      }
      away = away.lengthSqr() < 1.0E-4 ? GodzillaEntity.forward(e.getYRot()) : away.normalize();
      Vec3 to = here.add(away.scale(120.0));
      e.walkTo(to);
      e.lookAt(to.add(0.0, GodzillaRules.HEIGHT * 0.6, 0.0), false);
      e.faceTowards(null);
   }

   /** Next waypoint: near the heroes (they should see it), ahead rather than behind, through the tallest blocks. */
   @Nullable
   static Vec3 pickWaypoint(ServerLevel level, GodzillaSession s, GodzillaEntity e) {
      ServerPlayer p = nearestPlayer(level, e, GodzillaRules.LOSE_RANGE * 2.0);
      Vec3 focus = p != null ? p.position() : s.anchor;
      CityManager city = CityManager.of(level);
      CityProfile arena = city != null ? city.profile() : null;
      Vec3 best = null;
      double bestScore = -1.0E9;
      float yaw = e.getYRot();
      for (int i = 0; i < 16; i++) {
         double a = s.random.nextDouble() * Math.PI * 2.0;
         double d = 40.0 + s.random.nextDouble() * (GodzillaRules.WANDER_RADIUS - 40.0);
         double x = focus.x + Math.cos(a) * d;
         double z = focus.z + Math.sin(a) * d;
         int ground = ChitauriTerrain.surface(level, x, z);
         if (ground == ChitauriTerrain.UNKNOWN || !GodzillaEvent.canEnter(level, x, z)) {
            continue;
         }
         if (arena != null && !arena.inArena(Mth.floor(x), Mth.floor(e.getY()), Mth.floor(z))) {
            continue;
         }
         double tall = Math.max(0.0, ground - e.getY());
         double dist = Math.sqrt((x - e.getX()) * (x - e.getX()) + (z - e.getZ()) * (z - e.getZ()));
         float turn = Math.abs(GodzillaRules.wrap(GodzillaEntity.yawTowards(x - e.getX(), z - e.getZ()) - yaw));
         double score = Math.min(tall, 80.0) * GodzillaRules.TOWER_PULL - turn * 0.35 - Math.abs(dist - 90.0) * 0.15 + s.random.nextDouble() * 12.0;
         if (dist < GodzillaRules.WAYPOINT_REACHED * 1.5) {
            continue;
         }
         if (score > bestScore) {
            bestScore = score;
            best = new Vec3(x, e.getY(), z);
         }
      }
      return best;
   }

   /** Height of the blocks in front of the chest above the ground (0 when nothing stands there). */
   static float tallAhead(ServerLevel level, GodzillaEntity e) {
      float best = 0.0F;
      for (double fw = 0.22; fw <= 0.40; fw += 0.09) {
         Vec3 p = e.bodyPoint(0.0, 0.0, fw);
         int s = ChitauriTerrain.surface(level, p.x, p.z);
         if (s != ChitauriTerrain.UNKNOWN) {
            best = Math.max(best, (float)(s - e.getY()));
         }
      }
      return best;
   }

   @Nullable
   static ServerPlayer nearestPlayer(ServerLevel level, GodzillaEntity e, double range) {
      ServerPlayer best = null;
      double bd = range * range;
      for (ServerPlayer p : level.players()) {
         if (p.isSpectator()) {
            continue;
         }
         double d = p.distanceToSqr(e);
         if (d < bd) {
            bd = d;
            best = p;
         }
      }
      return best;
   }

   static double horizontal(Vec3 a, Vec3 b) {
      double dx = a.x - b.x;
      double dz = a.z - b.z;
      return Math.sqrt(dx * dx + dz * dz);
   }
}
