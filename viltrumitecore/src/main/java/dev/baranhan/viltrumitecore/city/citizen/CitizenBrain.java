package dev.baranhan.viltrumitecore.city.citizen;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.danger.DangerMap;
import dev.baranhan.viltrumitecore.city.profile.WalkGrid;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The citizen's danger perception and state machine (server, Living City 2.0). Every
 * {@link #PERIOD} ticks (staggered by entity id) a citizen reads the danger map and the
 * crowd around it and moves between calm, alert, startled, fleeing, cover and recovery;
 * shock waves are checked every other tick so people fall with the blast, not after it.
 * The goals ({@link CitizenGoals}) then carry the state out: run, hide, watch, stroll.
 */
final class CitizenBrain {
   static final int PERIOD = 5;
   /** Distance at which people notice a runner, a scream or a fall near them. */
   static final double CONTAGION_RANGE = 10.0;
   /** Seconds of memory: after a danger people avoid its place for a minute while strolling. */
   static final int MEMORY = 1200;
   /** Flee legs before a cornered citizen gives up running and cowers where it is. */
   static final int MAX_LEGS = 7;

   private CitizenBrain() {
   }

   static void tick(CitizenEntity c, CityManager city) {
      long now = city.now();
      c.stateTicks++;
      if ((c.tickCount & 1) == 0 && c.citizenState() != CitizenState.FALLEN && c.citizenState() != CitizenState.GETTING_UP && c.fallCooldown <= 0) {
         float blast = city.dangerMap().blastAt(c.getX(), c.getY(), c.getZ(), now);
         if (blast > 0.25F + c.traits().bravery() * 0.25F && c.onGround()) {
            knockDown(c, city, blast);
            return;
         }
      }

      if (c.fallCooldown > 0) {
         c.fallCooldown--;
      }

      if ((c.tickCount + c.getId()) % PERIOD != 0) {
         return;
      }

      think(c, city, now);
   }

   private static void knockDown(CitizenEntity c, CityManager city, float strength) {
      DangerMap.Zone z = city.dangerMap().noticed(c.getX(), c.getY(), c.getZ(), city.now());
      if (z != null) {
         c.setThreat(z.x, z.y, z.z, Math.max(z.radius, 10.0F));
      } else if (!c.hasThreat()) {
         c.setThreat(c.getX(), c.getY(), c.getZ(), 12.0F);
      }

      c.getNavigation().stop();
      double dx = c.getX() - c.threatX;
      double dz = c.getZ() - c.threatZ;
      double len = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
      double push = 0.25 + strength * 0.45;
      c.setDeltaMovement(dx / len * push, 0.18 + strength * 0.2, dz / len * push);
      c.hurtMarked = true;
      // fall away from the blast: the body turns its back to it
      c.setYRot((float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
      c.yBodyRot = c.getYRot();
      c.fallTicks = 28 + c.getRandom().nextInt(20) + Math.round(strength * 16.0F);
      c.fallCooldown = 120;
      c.setCitizenState(CitizenState.FALLEN);
   }

   private static void think(CitizenEntity c, CityManager city, long now) {
      CitizenState s = c.citizenState();
      if (s == CitizenState.FALLEN || s == CitizenState.GETTING_UP) {
         return; // the panic goal runs the fall and the getting up on its own clock
      }

      CitizenTraits t = c.traits();
      DangerMap map = city.dangerMap();
      DangerMap.Zone z = map.noticed(c.getX(), c.getY(), c.getZ(), now);
      boolean danger = z != null && !z.alertOnly();
      double dist = z == null ? Double.MAX_VALUE : Math.sqrt(z.distanceSq(c.getX(), c.getY(), c.getZ()));
      boolean inside = danger && dist < z.radius + t.margin();
      if (c.hurtTicks > 0) {
         inside = true;
         if (z == null || !danger) {
            z = null;
         }
      }

      CitizenEntity runner = null;
      if (!inside && (s.calm() || s == CitizenState.ALERT)) {
         runner = city.crowd().panickingNear(c, CONTAGION_RANGE);
      }

      if (danger) {
         c.setThreat(z.x, z.y, z.z, z.radius);
         c.calmTicks = 0;
         c.remember(z.x, z.z, now);
      }

      switch (s) {
         case STARTLED -> {
            int wait = inside && z != null && dist < z.radius * 0.5 ? c.traits().reaction() / 2 : c.traits().reaction();
            if (c.stateTicks >= wait) {
               c.legs = 0;
               c.setCitizenState(CitizenState.FLEEING);
            }
         }
         case FLEEING, COVER -> {
            if (inside && s == CitizenState.COVER) {
               // the danger came to the hiding place: run again
               c.legs = 0;
               c.setCitizenState(CitizenState.FLEEING);
            } else if (!danger && c.hurtTicks <= 0) {
               c.calmTicks += PERIOD;
               if (c.calmTicks >= t.calmDelay()) {
                  c.recoverTicks = 300 + c.getRandom().nextInt(300);
                  c.setCitizenState(CitizenState.RECOVERING);
               }
            }
         }
         case ALERT -> {
            if (inside) {
               c.setCitizenState(CitizenState.STARTLED);
            } else if (runner != null && c.getRandom().nextFloat() < t.contagion()) {
               adopt(c, runner, now);
               c.setCitizenState(CitizenState.STARTLED);
            } else if (z == null && runner == null) {
               if (c.stateTicks > 30) {
                  c.setCitizenState(c.recoverTicks > 0 ? CitizenState.RECOVERING : CitizenState.IDLE);
               }
            } else if (danger && c.stateTicks > 60 + Math.round(t.bravery() * 140.0F)) {
               // a fight nearby that is not coming closer: leave anyway, before it does
               c.legs = 0;
               c.setCitizenState(CitizenState.FLEEING);
            } else if (!danger && z != null && c.stateTicks > 40 + Math.round(t.curiosity() * 80.0F)) {
               c.setCitizenState(c.recoverTicks > 0 ? CitizenState.RECOVERING : CitizenState.IDLE);
            }
         }
         default -> {
            // calm: IDLE, WALKING, TALKING, RECOVERING
            if (inside) {
               c.setCitizenState(CitizenState.STARTLED);
            } else if (runner != null) {
               if (c.getRandom().nextFloat() < t.contagion()) {
                  adopt(c, runner, now);
                  c.setCitizenState(CitizenState.STARTLED);
               } else {
                  c.setThreat(runner.threatX, runner.threatY, runner.threatZ, runner.threatRadius);
                  c.setCitizenState(CitizenState.ALERT);
               }
            } else if (z != null && (danger || c.getRandom().nextFloat() < 0.35F + t.curiosity() * 0.6F)) {
               if (!danger) {
                  c.setThreat(z.x, z.y, z.z, 0.0F); // something to look at, not to run from
               }

               c.setCitizenState(CitizenState.ALERT);
            } else if (s == CitizenState.RECOVERING && c.recoverTicks <= 0) {
               c.setCitizenState(CitizenState.IDLE);
               c.clearThreat();
            }
         }
      }
   }

   /** Take over the danger a running neighbour is running from (or the runner itself, seen from here). */
   private static void adopt(CitizenEntity c, CitizenEntity runner, long now) {
      if (runner.hasThreat()) {
         c.setThreat(runner.threatX, runner.threatY, runner.threatZ, Math.max(runner.threatRadius, 8.0F));
      } else {
         c.setThreat(runner.getX(), runner.getY(), runner.getZ(), 8.0F);
      }

      c.remember(c.threatX, c.threatZ, now);
   }

   // ------------------------------------------------------------------ flee destinations

   /**
    * A path away from the citizen's threat: candidate sidewalk cells around the away
    * direction, scored by distance from the danger, cover (no line of sight to it), other
    * live dangers on the way and how many people already run there; only the best cells
    * are path-searched, and a path that leads past the danger is refused.
    */
   @Nullable
   static Path fleePath(CitizenEntity c, CityManager city, long now) {
      WalkGrid grid = city.grid();
      DangerMap map = city.dangerMap();
      double dx = c.getX() - c.threatX;
      double dz = c.getZ() - c.threatZ;
      double len = Math.sqrt(dx * dx + dz * dz);
      double base = len < 0.5 ? c.getRandom().nextDouble() * Math.PI * 2.0 : Math.atan2(dz, dx);
      double need = Math.max(14.0, c.threatRadius + 14.0 - len) + c.legs * 4.0;
      int n = 9;
      long[] cells = new long[n];
      double[] score = new double[n];
      int found = 0;
      for (int i = 0; i < n; i++) {
         double spread = i < 4 ? 0.9 : i < 7 ? 1.9 : 2.8;
         double a = base + (c.getRandom().nextDouble() - 0.5) * spread;
         double d = need + c.getRandom().nextDouble() * 14.0;
         int tx = (int)Math.floor(c.getX() + Math.cos(a) * d);
         int tz = (int)Math.floor(c.getZ() + Math.sin(a) * d);
         long cell = grid.nearestCell(tx, tz, 4, null);
         if (cell == Long.MIN_VALUE) {
            continue;
         }

         int x = WalkGrid.unpackX(cell);
         int z = WalkGrid.unpackZ(cell);
         int y = grid.feetY(x, z);
         if (!c.level().hasChunk(x >> 4, z >> 4) || Math.abs(y - c.getY()) > 10 || map.dangersNear(x + 0.5, y, z + 0.5, now, 2.0) > 0) {
            continue;
         }

         double ex = x + 0.5 - c.threatX;
         double ez = z + 0.5 - c.threatZ;
         double away = Math.sqrt(ex * ex + ez * ez);
         if (away < len + 4.0) {
            continue; // not further from the danger than here: no use running there
         }

         double sc = away - city.crowd().claims(x, z) * 5.0 - Math.abs(y - c.getY()) * 0.5;
         if (covered(c, x, y, z)) {
            sc += 10.0;
         }

         cells[found] = cell;
         score[found] = sc;
         found++;
      }

      Path fallback = null;
      for (int tries = 0; tries < 3 && found > 0; tries++) {
         int bi = 0;
         for (int i = 1; i < found; i++) {
            if (score[i] > score[bi]) {
               bi = i;
            }
         }

         long cell = cells[bi];
         cells[bi] = cells[found - 1];
         score[bi] = score[found - 1];
         found--;
         int x = WalkGrid.unpackX(cell);
         int z = WalkGrid.unpackZ(cell);
         Path p = c.getNavigation().createPath(new BlockPos(x, grid.feetY(x, z), z), 0);
         if (p == null || p.getNodeCount() < 2 || passesDanger(c, p, len)) {
            continue;
         }

         if (p.canReach()) {
            return p;
         }

         if (fallback == null) {
            fallback = p;
         }
      }

      return fallback;
   }

   /** True when the path runs closer to the threat than the citizen already is (and into its sphere). */
   static boolean passesDanger(CitizenEntity c, Path p, double startDist) {
      double r = Math.max(4.0, Math.min(c.threatRadius, startDist - 2.0));
      for (int i = 1; i < p.getNodeCount(); i++) {
         Node node = p.getNode(i);
         double ex = node.x + 0.5 - c.threatX;
         double ez = node.z + 0.5 - c.threatZ;
         double d = Math.sqrt(ex * ex + ez * ez);
         if (d < startDist - 3.0 && d < r) {
            return true;
         }
      }

      return false;
   }

   /** A wall, car or corner between this cell and the threat (line of sight blocked at head height). */
   static boolean covered(CitizenEntity c, int x, int y, int z) {
      Vec3 from = new Vec3(x + 0.5, y + 1.5, z + 0.5);
      Vec3 to = new Vec3(c.threatX, c.threatY + 1.0, c.threatZ);
      if (from.distanceToSqr(to) > 96.0 * 96.0) {
         return true;
      }

      BlockHitResult hit = c.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
      return hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceToSqr(to) > 4.0;
   }
}
