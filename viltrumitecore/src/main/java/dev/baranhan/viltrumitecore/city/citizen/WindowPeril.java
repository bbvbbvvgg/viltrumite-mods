package dev.baranhan.viltrumitecore.city.citizen;

import dev.baranhan.viltrumitecore.city.CityManager;
import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

/**
 * People falling from broken windows during heavy destruction (server; numbers in {@link WindowRules}). A person
 * upstairs in a building shaken by destruction walks to a real opening of its own floor (a broken window, a
 * blown-out wall: free at feet and head, open sky outside, a drop of {@link WindowRules#MIN_DROP}+ blocks), calls
 * for help there (the street looks up), and either steps back after a while or goes over the edge for a physical
 * reason: a shock wave reaching the window throws it out, a tremor, someone pushing past or the rubble on the sill
 * make it lose its footing (arms windmilling at the edge first). From there it is a real fall run by
 * {@link CitizenPeril}: vanilla gravity until the body touches something, the scream, perhaps a grab at the sill and
 * hanging on, witnesses. Only people already living upstairs take part (nobody appears in a window out of nothing),
 * only near players and with level, building and window cooldowns ({@link WindowRules.Gate}).
 */
public final class WindowPeril {
   static final byte NONE = 0;
   static final byte APPROACH = 1;
   static final byte AT_WINDOW = 2;
   static final byte EDGE = 3;
   private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
   private static final boolean LOG = Boolean.getBoolean("viltrumitecore.peril.log");
   private static final Logger LOGGER = LogUtils.getLogger();

   private WindowPeril() {
   }

   /** Destruction happened around a point: {@code blocks} changed (or the equivalent of a blast). */
   public static void onDestruction(Level level, double x, double z, double blocks, long now) {
      if (!level.isClientSide) {
         CitizenPeril.stats(level).destruction.add(x, z, blocks, now);
      }
   }

   /** The person is busy at a window (never trimmed or re-planned meanwhile). */
   public static boolean busy(CitizenEntity c) {
      return c.windowPhase != NONE;
   }

   /** Something else took the body (a captor, a hit): the window plan ends here. */
   static void cancel(CitizenEntity c) {
      c.windowPhase = NONE;
      c.windowAt = null;
      c.windowOut = null;
   }

   /** Once per {@link WindowRules#SCAN_PERIOD} from the city tick: who goes to a window. */
   public static void scan(CityManager city, long now) {
      if (now % WindowRules.SCAN_PERIOD != 11) {
         return;
      }
      Level level = city.level();
      CitizenPeril.Stats st = CitizenPeril.stats(level);
      if (now % 400 == 11) {
         st.destruction.prune(now);
      }
      if (st.destruction.quiet(now)) {
         return;
      }
      int busy = 0;
      for (CitizenEntity c : city.citizens()) {
         if (c.windowPhase != NONE) {
            busy++;
         }
      }
      for (CitizenEntity c : city.citizens()) {
         if (busy >= WindowRules.MAX_AT_WINDOWS) {
            return;
         }
         if (!c.occupant || c.windowPhase != NONE || !c.isAlive() || !c.onGround() || c.isInWater() || c.fallCooldown > 0 || CitizenCaptivity.isHeld(c)) {
            continue;
         }
         CitizenState s = c.citizenState();
         if (s.peril() || s == CitizenState.FALLEN || s == CitizenState.GETTING_UP) {
            continue;
         }
         float d = st.destruction.at(c.getX(), c.getZ(), now);
         if (d < WindowRules.MIN_DESTRUCTION) {
            continue;
         }
         Player p = level.getNearestPlayer(c, WindowRules.FAR);
         if (p == null) {
            continue;
         }
         float chance = WindowRules.approachChance(d, others(city, c), p.distanceTo(c), c.traits().bravery());
         if (chance <= 0.0F || c.getRandom().nextFloat() >= chance) {
            continue;
         }
         Opening o = opening(c);
         if (o == null) {
            continue;
         }
         Path path = c.getNavigation().createPath(o.at, 0);
         if (path == null || !path.canReach() || !flat(path, o.at.getY())) {
            continue;
         }
         if (!st.windows.take(now, WindowRules.building(o.at.getX(), o.at.getZ()), WindowRules.window(o.at.getX(), o.at.getY(), o.at.getZ()))) {
            continue;
         }
         c.windowAt = o.at;
         c.windowOut = o.out;
         c.windowPhase = APPROACH;
         c.windowTicks = 0;
         c.getNavigation().moveTo(path, c.runSpeed() * 0.8);
         c.setCitizenState(CitizenState.FLEEING);
         st.windowGoes++;
         busy++;
         log(c, "to-window", o.at + " drop=" + o.drop + " destruction=" + d);
      }
   }

   /** One tick of a person with a window plan; true while it owns the body (the brain is skipped). */
   static boolean tick(CitizenEntity c, CityManager city, long now) {
      if (c.windowPhase == NONE) {
         return false;
      }
      if (c.windowAt == null || c.windowOut == null || c.citizenState().peril() && c.windowPhase != EDGE || CitizenCaptivity.isHeld(c)) {
         cancel(c); // the floor went, a captor took it: the peril (or the holder) runs the body now
         return false;
      }
      CitizenPeril.Stats st = CitizenPeril.stats(c.level());
      BlockPos b = c.windowAt;
      Direction out = c.windowOut;
      switch (c.windowPhase) {
         case APPROACH -> {
            c.windowTicks++;
            double dx = b.getX() + 0.5 - c.getX(), dz = b.getZ() + 0.5 - c.getZ();
            double d2 = dx * dx + dz * dz;
            boolean done = c.getNavigation().isDone();
            if (d2 < 0.2 || done && d2 < 1.0) {
               arrive(c, city, now);
            } else if (done || c.windowTicks > WindowRules.APPROACH_MAX) {
               stepBack(c, st, "approach-failed");
            }
         }
         case AT_WINDOW -> {
            c.getNavigation().stop();
            face(c, out, 30.0F);
            if (city.dangerMap().blastAt(c.getX(), c.getY(), c.getZ(), now) > WindowRules.BLAST_THROW) {
               over(c, city, now, WindowRules.Cause.BLAST);
               return true;
            }
            if (--c.windowTicks <= 0) {
               stepBack(c, st, "stepped-back");
               return true;
            }
            if ((c.tickCount + c.getId()) % 20 == 0) {
               if (c.getRandom().nextFloat() < 0.3F) {
                  CitizenPeril.voice(c, CitizenVoices.Kind.HELP, 1.5F);
               }
               WindowRules.Cause cause = WindowRules.edge(st.destruction.at(c.getX(), c.getZ(), now), others(city, c), c.traits().bravery(),
                  c.getRandom().nextFloat());
               if (cause != null) {
                  edge(c, cause);
               }
            }
         }
         case EDGE -> {
            c.getNavigation().stop();
            face(c, out, 45.0F);
            if (city.dangerMap().blastAt(c.getX(), c.getY(), c.getZ(), now) > WindowRules.BLAST_THROW) {
               c.windowCause = WindowRules.Cause.BLAST;
               c.windowTicks = 0;
            }
            if (--c.windowTicks <= 0) {
               over(c, city, now, c.windowCause == null ? WindowRules.Cause.SLIP : c.windowCause);
            }
         }
         default -> cancel(c);
      }
      return true;
   }

   private static void arrive(CitizenEntity c, CityManager city, long now) {
      c.getNavigation().stop();
      c.windowPhase = AT_WINDOW;
      c.windowTicks = WindowRules.windowTicks(c.getRandom().nextFloat());
      c.setCitizenState(CitizenState.ALERT);
      face(c, c.windowOut, 30.0F);
      CitizenPeril.Stats st = CitizenPeril.stats(c.level());
      st.windowCalls++;
      CitizenPeril.voice(c, CitizenVoices.Kind.HELP, 1.5F);
      // the street hears the call and looks up (a curiosity, not a danger)
      city.dangerMap().add(c.getX(), c.getY(), c.getZ(), 0.0F, 28.0F, now, 140);
      log(c, "at-window", c.windowAt);
   }

   /** Balance lost at the edge: the body leans over the sill, arms windmilling (the teeter pose). */
   private static void edge(CitizenEntity c, WindowRules.Cause cause) {
      BlockPos b = c.windowAt;
      Direction out = c.windowOut;
      double x = b.getX() + 0.5 + out.getStepX() * 0.3, z = b.getZ() + 0.5 + out.getStepZ() * 0.3;
      if (c.level().noCollision(c, c.getType().getAABB(x, c.getY(), z).deflate(0.02))) {
         c.setPos(x, c.getY(), z);
      }
      c.windowPhase = EDGE;
      c.windowCause = cause;
      c.windowTicks = WindowRules.edgeTicks(c.getRandom().nextFloat());
      c.setCitizenState(CitizenState.TEETER);
      c.teeterTicks = PerilRules.TEETER_MAX;
      CitizenPeril.voice(c, CitizenVoices.Kind.SCREAM, 1.0F);
      log(c, "edge", cause);
   }

   /** Over the edge with the push of its cause; from here the peril sees a real fall (or a grab at the sill). */
   private static void over(CitizenEntity c, CityManager city, long now, WindowRules.Cause cause) {
      BlockPos b = c.windowAt;
      Direction out = c.windowOut;
      cancel(c);
      double x = b.getX() + 0.5 + out.getStepX() * 0.45, z = b.getZ() + 0.5 + out.getStepZ() * 0.45;
      if (c.level().noCollision(c, c.getType().getAABB(x, c.getY(), z).deflate(0.02))) {
         c.setPos(x, c.getY(), z);
      }
      double speed = cause.out * (0.9 + c.getRandom().nextDouble() * 0.2);
      c.setDeltaMovement(out.getStepX() * speed, cause.up, out.getStepZ() * speed);
      c.hurtMarked = true;
      c.fallChecked = false;
      c.perilUntil = now + PerilRules.PERIL_MEMORY;
      if (c.citizenState() != CitizenState.TEETER) {
         c.setCitizenState(CitizenState.TEETER);
      }
      c.teeterTicks = c.stateTicks + 40; // the next ticks see the body leave the floor (CitizenPeril.teeter)
      c.setThreat(c.getX(), c.getY(), c.getZ(), 10.0F);
      CitizenPeril.stats(c.level()).windowFalls++;
      log(c, "over", cause);
   }

   private static void stepBack(CitizenEntity c, CitizenPeril.Stats st, String why) {
      cancel(c);
      c.getNavigation().stop();
      c.idleTicks = 100 + c.getRandom().nextInt(100);
      c.setCitizenState(CitizenState.ALERT);
      st.windowBacks++;
      log(c, why, "");
   }

   private static void face(CitizenEntity c, Direction out, float down) {
      float yaw = (float)Math.toDegrees(Math.atan2(out.getStepZ(), out.getStepX())) - 90.0F;
      c.setYRot(yaw);
      c.setYHeadRot(yaw);
      c.yBodyRot = yaw;
      c.setXRot(down);
   }

   /** Other people on the same floor close by (at most 6 counted). */
   private static int others(CityManager city, CitizenEntity c) {
      int n = 0;
      for (CitizenEntity o : c.level().getEntitiesOfClass(CitizenEntity.class, c.getBoundingBox().inflate(8.0, 1.0, 8.0))) {
         if (o != c && o.isAlive() && ++n >= 6) {
            break;
         }
      }
      return n;
   }

   record Opening(BlockPos at, Direction out, int drop) {
   }

   /**
    * The nearest opening of the person's floor: a block free at feet and head, under a roof, on a full floor,
    * whose neighbour on one side is free, open to the sky and above a drop of {@link WindowRules#MIN_DROP}+.
    */
   @Nullable
   static Opening opening(CitizenEntity c) {
      Level level = c.level();
      BlockPos feet = c.blockPosition();
      int y = feet.getY();
      Opening best = null;
      int bestD = Integer.MAX_VALUE;
      int r = WindowRules.SEARCH_R;
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int dx = -r; dx <= r; dx++) {
         for (int dz = -r; dz <= r; dz++) {
            int d = dx * dx + dz * dz;
            if (d > r * r || d >= bestD) {
               continue;
            }
            BlockPos b = new BlockPos(feet.getX() + dx, y, feet.getZ() + dz);
            if (!level.isLoaded(b) || !free(level, b) || !free(level, b.above()) || level.canSeeSky(b) || !fullTop(level, b.below())) {
               continue;
            }
            for (Direction side : SIDES) {
               BlockPos e = b.relative(side);
               if (!level.isLoaded(e) || !free(level, e) || !free(level, e.above()) || !level.canSeeSky(e)) {
                  continue;
               }
               int drop = drop(level, e, m);
               if (drop >= WindowRules.MIN_DROP) {
                  best = new Opening(b, side, drop);
                  bestD = d;
                  break;
               }
            }
         }
      }
      return best;
   }

   /** Free blocks under {@code e} down to the first one to land on (solid or a fluid); 0 when unknown. */
   static int drop(Level level, BlockPos e, BlockPos.MutableBlockPos m) {
      for (int i = 1; i <= WindowRules.MAX_DROP; i++) {
         m.set(e.getX(), e.getY() - i, e.getZ());
         if (m.getY() < level.getMinBuildHeight()) {
            return i - 1;
         }
         if (!level.isLoaded(m)) {
            return 0;
         }
         if (!free(level, m)) {
            return i - 1;
         }
      }
      return WindowRules.MAX_DROP;
   }

   private static boolean free(Level level, BlockPos p) {
      BlockState s = level.getBlockState(p);
      return s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty();
   }

   private static boolean fullTop(Level level, BlockPos p) {
      VoxelShape s = level.getBlockState(p).getCollisionShape(level, p);
      return !s.isEmpty() && s.max(Direction.Axis.Y) >= 0.99;
   }

   /** Every node of the path on one height: no stairs, no step off an edge on the way. */
   private static boolean flat(Path p, int y) {
      for (int i = 0; i < p.getNodeCount(); i++) {
         if (p.getNode(i).y != y) {
            return false;
         }
      }
      return true;
   }

   private static void log(CitizenEntity c, String what, Object extra) {
      if (LOG) {
         LOGGER.info("[window] {} #{} at {} {} {} {}", what, c.getId(), Math.round(c.getX()), Math.round(c.getY()), Math.round(c.getZ()), extra);
      }
   }
}
