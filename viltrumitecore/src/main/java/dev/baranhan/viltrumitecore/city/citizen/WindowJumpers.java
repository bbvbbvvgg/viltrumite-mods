package dev.baranhan.viltrumitecore.city.citizen;

import com.mojang.logging.LogUtils;
import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.world.collapse.CollapseInfo;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

/**
 * The background picture of a city in trouble: when a building near a player is blown up, smashed, collapsing or on
 * fire, now and then a person shows up in one of its windows facing that player, panics there for a moment (arms
 * waving, a call for help, smoke pouring out when it burns), breaks the glass and goes out: thrown by the blast, slips
 * off the sill or jumps away from the flames (still burning). The fall itself is the usual {@link CitizenPeril} one:
 * flailing, the scream, a grab at the sill now and then, witnesses. Numbers in {@link WindowRules} (jumper section).
 * <p>
 * Unlike {@link WindowPeril} (people already living upstairs who walk to an opening) nothing has to line up here: the
 * window is found by looking from the player's eyes at the damaged building, so the person is always in plain view.
 * Collapses also put one or two people on the falling section so they go down with the debris.
 */
public final class WindowJumpers {
   /** The person stands in (or behind) the window, panicking, before going out. */
   static final byte SHOW = 4;
   private static final boolean LOG = Boolean.getBoolean("viltrumitecore.peril.log");
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Map<ResourceKey<Level>, Book> BOOKS = new HashMap<>();

   private WindowJumpers() {
   }

   /** One damaged or burning place: where, what, how much, how many people went out of its windows. */
   static final class Incident {
      double x, y, z;
      final boolean fire;
      double power;
      double radius;
      long born, until, next;
      int spawned, misses;
      /** Windows people already went out of: the next one comes out of another window. */
      final List<BlockPos> used = new ArrayList<>();

      Incident(double x, double y, double z, boolean fire, double radius, long now) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.fire = fire;
         this.radius = radius;
         this.born = now;
      }

      boolean used(BlockPos p) {
         for (BlockPos u : this.used) {
            if (Math.abs(u.getX() - p.getX()) <= 1 && Math.abs(u.getZ() - p.getZ()) <= 1 && Math.abs(u.getY() - p.getY()) <= 2) {
               return true;
            }
         }
         return false;
      }
   }

   static final class Book {
      final List<Incident> incidents = new ArrayList<>();
      long lastStart = -1_000_000L; // never Long.MIN_VALUE: now - lastStart would overflow
      int started, missed;
   }

   static Book book(Level level) {
      return BOOKS.computeIfAbsent(level.dimension(), k -> new Book());
   }

   static void forget(ResourceKey<Level> dimension) {
      BOOKS.remove(dimension);
   }

   static void forgetAll() {
      BOOKS.clear();
   }

   // ------------------------------------------------------------------ sources

   /** A shock wave (an explosion, a hero's slam) of this radius. */
   public static void onBlast(Level level, double x, double y, double z, float radius, long now) {
      destruction(level, x, y, z, WindowRules.blastPower(radius), Math.max(8.0, radius + 6.0), now);
   }

   /** Many blocks changed in one chunk within one tick (somebody is smashing a building). */
   public static void onBurst(Level level, double x, double y, double z, double blocks, long now) {
      destruction(level, x, y, z, blocks, 10.0, now);
   }

   /** A fire block appeared (spreading flames, a lit building). */
   public static void onFire(Level level, BlockPos pos, long now) {
      if (level.isClientSide) {
         return;
      }
      Book b = book(level);
      Incident i = near(b, pos.getX() + 0.5, pos.getZ() + 0.5, true);
      if (i == null) {
         if (b.incidents.size() >= WindowRules.MAX_INCIDENTS) {
            return;
         }
         i = new Incident(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, true, WindowRules.FIRE_RADIUS, now);
         i.next = now + WindowRules.FIRE_FIRST;
         b.incidents.add(i);
         log("fire", i, "");
      } else if (pos.getY() > i.y) {
         i.y = pos.getY(); // the flames climb: the windows above them are the ones to look at
      }
      i.power += 1.0;
      i.until = now + WindowRules.FIRE_LIFE;
   }

   /** A collapse starts: people on the falling section near its edge, more from the windows of the building. */
   public static void onCollapse(ServerLevel level, CityManager city, CollapseInfo info, long now) {
      destruction(level, info.center().getX() + 0.5, info.center().getY(), info.center().getZ() + 0.5, info.affectedBlocks().size() * 2.0 + 40.0,
         info.radius() + 8.0, now);
      riders(level, city, info, now);
   }

   private static void destruction(Level level, double x, double y, double z, double power, double radius, long now) {
      if (level.isClientSide || power <= 0.0) {
         return;
      }
      Book b = book(level);
      Incident i = near(b, x, z, false);
      if (i == null) {
         if (b.incidents.size() >= WindowRules.MAX_INCIDENTS) {
            return;
         }
         i = new Incident(x, y, z, false, radius, now);
         i.next = now + WindowRules.BLAST_FIRST_MIN + level.random.nextInt(WindowRules.BLAST_FIRST_SPREAD);
         b.incidents.add(i);
         log("destruction", i, String.format(java.util.Locale.ROOT, "power=%.0f", power));
      } else {
         i.radius = Math.max(i.radius, radius);
      }
      i.power += power;
      i.until = Math.max(i.until, now + WindowRules.DAMAGED_LIFE);
   }

   @Nullable
   private static Incident near(Book b, double x, double z, boolean fire) {
      for (Incident i : b.incidents) {
         double dx = i.x - x, dz = i.z - z;
         if (i.fire == fire && dx * dx + dz * dz < WindowRules.MERGE_R * WindowRules.MERGE_R) {
            return i;
         }
      }
      return null;
   }

   // ------------------------------------------------------------------ tick

   /** From the city tick: who appears in a window now. */
   public static void tick(CityManager city, long now) {
      if (now % WindowRules.JUMPER_PERIOD != 3) {
         return;
      }
      ServerLevel level = city.level();
      Book b = BOOKS.get(level.dimension());
      if (b == null || b.incidents.isEmpty()) {
         return;
      }
      b.incidents.removeIf(i -> now > i.until || i.spawned >= WindowRules.quota(i.power, i.fire) && now > i.next);
      if (now - b.lastStart < WindowRules.LEVEL_JUMP_GAP) {
         return;
      }
      int busy = 0;
      for (CitizenEntity c : city.citizens()) {
         if (c.windowPhase == SHOW) {
            busy++;
         }
      }
      if (busy >= WindowRules.MAX_SHOWING) {
         return;
      }
      for (Incident i : b.incidents) {
         if (now < i.next || i.spawned >= WindowRules.quota(i.power, i.fire)) {
            continue;
         }
         Player p = level.getNearestPlayer(i.x, i.y, i.z, WindowRules.JUMPER_FAR, (java.util.function.Predicate<net.minecraft.world.entity.Entity>)null);
         if (p == null) {
            i.next = now + 20;
            continue;
         }
         if (!WindowRules.onScreen(p.getViewVector(1.0F), p.getEyePosition(), new Vec3(i.x, i.y + 4.0, i.z), WindowRules.PLACE_VIEW)) {
            i.next = now + 10; // the player looks elsewhere: wait for the building to be in view (no miss)
            continue;
         }
         Window w = find(level, p, i);
         if (w == null) {
            i.next = now + 10;
            b.missed++;
            if (++i.misses > WindowRules.MAX_MISSES) {
               i.spawned = Integer.MAX_VALUE / 2; // no window of this building faces anybody: let it go
               log("no-window", i, "");
            }
            continue;
         }
         if (start(level, city, i, w, now)) {
            i.spawned++;
            i.used.add(w.at);
            i.misses = 0;
            i.next = now + WindowRules.gap(i.fire, level.random.nextFloat());
            b.lastStart = now;
            b.started++;
            return; // one per period for the whole level
         }
         i.next = now + 10;
      }
   }

   /** A window facing a player: where the glass (or the opening) is, the way out, and where the person stands. */
   record Window(BlockPos at, Direction out, BlockPos stand, int drop) {
   }

   /**
    * Looks from the player's eyes at a few random points of the damaged building; where the sight line hits a facade
    * (a pane, a wall, or the inside of a room through an already broken window) a window around it is looked for.
    */
   @Nullable
   static Window find(ServerLevel level, Player p, Incident i) {
      Vec3 eye = p.getEyePosition();
      Vec3 look = p.getViewVector(1.0F);
      for (int k = 0; k < WindowRules.SIGHT_TRIES; k++) {
         double a = level.random.nextDouble() * Math.PI * 2.0;
         double r = level.random.nextDouble() * i.radius;
         double ty = i.fire ? i.y + level.random.nextInt(8) - 1 : i.y + level.random.nextInt(16) - 4;
         Vec3 t = new Vec3(i.x + Math.cos(a) * r, ty, i.z + Math.sin(a) * r);
         double d = eye.distanceTo(t);
         if (d < WindowRules.JUMPER_NEAR || d > WindowRules.JUMPER_FAR || !WindowRules.onScreen(look, eye, t, WindowRules.WINDOW_VIEW)) {
            continue;
         }
         // a little past the point, so a sight line through an open window still hits the room behind it
         Vec3 end = eye.add(t.subtract(eye).scale((d + 6.0) / d));
         BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
         if (hit.getType() != HitResult.Type.BLOCK || !hit.getDirection().getAxis().isHorizontal()) {
            continue;
         }
         double hx = hit.getLocation().x - i.x, hz = hit.getLocation().z - i.z;
         if (hx * hx + hz * hz > (i.radius + 4.0) * (i.radius + 4.0)) {
            continue; // the sight line stopped at another building in front of the damaged one
         }
         Window w = around(level, hit.getBlockPos(), hit.getDirection(), i);
         if (w != null && eye.distanceTo(Vec3.atCenterOf(w.at)) >= WindowRules.JUMPER_NEAR && WindowRules.onScreen(look, eye, Vec3.atCenterOf(w.at), WindowRules.WINDOW_VIEW)) {
            return w;
         }
      }
      return null;
   }

   /** The nearest window to a hit block of a facade facing {@code out}: in its plane or a few blocks closer to the eye. */
   @Nullable
   static Window around(Level level, BlockPos hit, Direction out, Incident i) {
      Direction side = out.getClockWise();
      for (int k = 0; k <= 5; k++) {
         for (int ring = 0; ring <= 3; ring++) {
            for (int du = -ring; du <= ring; du++) {
               for (int dv = -ring; dv <= ring; dv++) {
                  if (Math.max(Math.abs(du), Math.abs(dv)) != ring) {
                     continue;
                  }
                  BlockPos at = hit.relative(out, k).relative(side, du).above(dv);
                  if (i.fire && Math.abs(at.getY() - i.y) > WindowRules.FIRE_BAND) {
                     continue;
                  }
                  if (i.used(at)) {
                     continue;
                  }
                  Window w = window(level, at, out);
                  if (w != null) {
                     return w;
                  }
               }
            }
         }
      }
      return null;
   }

   /**
    * A window at {@code at} opening to {@code out}: glass or air at feet and head, something to stand on, a wall or
    * more glass beside it, a ceiling above (a floor, not a roof), open air with a real drop outside, and a room
    * behind it (or the person stands in the opening itself). Nobody else there already.
    */
   @Nullable
   static Window window(Level level, BlockPos at, Direction out) {
      if (!level.isLoaded(at) || !level.isLoaded(at.relative(out)) || !level.isLoaded(at.relative(out.getOpposite()))) {
         return null;
      }
      if (!passable(level, at) || !passable(level, at.above()) || free(level, at.below())) {
         return null;
      }
      BlockPos o = at.relative(out);
      if (!free(level, o) || !free(level, o.above()) || !WindowPeril.outside(level, o)) {
         return null;
      }
      double fy = floorY(level, at);
      if (Double.isNaN(fy)) {
         return null;
      }
      for (int y = at.getY() + 2, top = bodyTop(fy); y <= top; y++) {
         // a raised sill (a wall block, a slab) puts the head into the third row: it has to be glass or open too
         BlockPos h = new BlockPos(at.getX(), y, at.getZ());
         if (!passable(level, h) || !free(level, h.relative(out))) {
            return null;
         }
      }
      Direction side = out.getClockWise();
      if (free(level, at.relative(side)) && free(level, at.relative(side.getOpposite())) && free(level, at.above().relative(side))
         && free(level, at.above().relative(side.getOpposite()))) {
         return null; // no wall or glass beside it: an open floor edge, a balcony, not a window
      }
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      if (!WindowPeril.roofed(level, at, m)) {
         return null;
      }
      int drop = WindowPeril.drop(level, o, m);
      if (drop < WindowRules.JUMP_MIN_DROP) {
         return null;
      }
      BlockPos in = at.relative(out.getOpposite());
      BlockPos stand;
      if (free(level, in) && free(level, in.above()) && standable(level, in)) {
         stand = in;
      } else if (free(level, in.below()) && free(level, in) && standable(level, in.below())) {
         stand = in.below(); // the room floor one below the sill
      } else if (free(level, at) && free(level, at.above())) {
         stand = at; // already open: standing in it
      } else {
         return null;
      }
      if (!level.getEntitiesOfClass(CitizenEntity.class, new net.minecraft.world.phys.AABB(at).inflate(2.0)).isEmpty()) {
         return null;
      }
      return new Window(at.immutable(), out, stand.immutable(), drop);
   }

   private static boolean start(ServerLevel level, CityManager city, Incident i, Window w, long now) {
      CitizenEntity c = ViltrumiteEntities.CITIZEN.get().create(level);
      if (c == null) {
         return false;
      }
      double fy = floorY(level, w.stand);
      if (Double.isNaN(fy)) {
         return false;
      }
      float yaw = yaw(w.out);
      c.moveTo(w.stand.getX() + 0.5, fy + 0.01, w.stand.getZ() + 0.5, yaw, 0.0F);
      if (!level.noCollision(c, c.getBoundingBox().deflate(0.05))) {
         return false;
      }
      c.setSkin(level.random.nextInt());
      c.occupant = true;
      c.finalizeSpawn(level, level.getCurrentDifficultyAt(c.blockPosition()), MobSpawnType.EVENT, null, null);
      level.addFreshEntity(c);
      dev.baranhan.viltrumitecore.worldevent.aftermath.CityAftermath.onCitizenSpawn(level, c);
      c.setYHeadRot(yaw);
      c.yBodyRot = yaw;
      c.windowAt = w.at;
      c.windowOut = w.out;
      c.windowPhase = SHOW;
      c.windowFire = i.fire;
      boolean thrown = !i.fire && now - i.born < WindowRules.BLAST_THROWN_WINDOW && i.spawned == 0 && level.random.nextFloat() < 0.5F;
      c.windowCause = i.fire ? WindowRules.Cause.JUMP : thrown ? WindowRules.Cause.BLAST : level.random.nextBoolean() ? WindowRules.Cause.SLIP : WindowRules.Cause.TREMOR;
      c.windowTicks = WindowRules.showTicks(i.fire, thrown, level.random.nextFloat());
      c.perilUntil = now + PerilRules.PERIL_MEMORY;
      c.getNavigation().stop();
      c.setCitizenState(CitizenState.TEETER); // arms waving for help in the window
      c.teeterTicks = Integer.MAX_VALUE / 4;
      CitizenPeril.voice(c, CitizenVoices.Kind.HELP, 0.0F);
      // the street hears it and looks up
      city.dangerMap().add(c.getX(), c.getY(), c.getZ(), 0.0F, 28.0F, now, 140);
      log("start", i, w.at + " out=" + w.out + " drop=" + w.drop + " stand=" + w.stand + " cause=" + c.windowCause + " show=" + c.windowTicks);
      return true;
   }

   /** One tick of a person in a window ({@link #SHOW}); the window plan of {@link WindowPeril} takes over at the edge. */
   static void show(CitizenEntity c, CityManager city, long now) {
      c.getNavigation().stop();
      Direction out = c.windowOut;
      BlockPos at = c.windowAt;
      float yaw = yaw(out);
      c.setYRot(yaw);
      c.setYHeadRot(yaw);
      c.yBodyRot = yaw;
      c.setXRot(15.0F);
      ServerLevel level = (ServerLevel)c.level();
      if (c.windowFire && (c.tickCount & 3) == 0) {
         // smoke pours out of the window above the person
         level.sendParticles(ParticleTypes.LARGE_SMOKE, at.getX() + 0.5 + out.getStepX() * 0.7, at.getY() + 1.6, at.getZ() + 0.5 + out.getStepZ() * 0.7, 2, 0.25,
            0.2, 0.25, 0.02);
         if (c.getRandom().nextInt(3) == 0) {
            level.sendParticles(ParticleTypes.FLAME, at.getX() + 0.5 - out.getStepX() * 0.6, at.getY() + 0.4, at.getZ() + 0.5 - out.getStepZ() * 0.6, 1, 0.3, 0.2,
               0.3, 0.01);
         }
      }
      if ((c.tickCount + c.getId()) % 16 == 0 && c.getRandom().nextFloat() < 0.5F) {
         CitizenPeril.voice(c, c.getRandom().nextBoolean() ? CitizenVoices.Kind.HELP : CitizenVoices.Kind.SCREAM, 1.0F);
      }
      if (city.dangerMap().blastAt(c.getX(), c.getY(), c.getZ(), now) > WindowRules.BLAST_THROW) {
         c.windowCause = WindowRules.Cause.BLAST;
         c.windowTicks = 0;
      }
      if (--c.windowTicks > 0) {
         return;
      }
      // out through the glass
      double fy = floorY(level, at);
      int top = Double.isNaN(fy) ? at.getY() + 1 : Math.max(at.getY() + 1, bodyTop(fy));
      for (int y = at.getY(); y <= top; y++) {
         shatter(level, new BlockPos(at.getX(), y, at.getZ()), c);
      }
      if (!Double.isNaN(fy)) {
         double x = at.getX() + 0.5, z = at.getZ() + 0.5;
         if (level.noCollision(c, c.getType().getAABB(x, fy + 0.01, z).deflate(0.02))) {
            c.setPos(x, fy + 0.01, z);
         }
      }
      if (c.windowCause == WindowRules.Cause.BLAST || c.windowCause == WindowRules.Cause.JUMP) {
         WindowPeril.over(c, city, now, c.windowCause);
      } else {
         WindowPeril.edge(c, c.windowCause);
      }
   }

   /** Glass at a window breaks (the sound and the shards of a broken pane); journaled like any block, so a reset restores it. */
   private static void shatter(ServerLevel level, BlockPos p, CitizenEntity by) {
      if (glass(level.getBlockState(p))) {
         level.destroyBlock(p, false, by);
      }
   }

   // ------------------------------------------------------------------ collapse riders

   /** One or two people standing on the falling section close to its open edge, so they go down with it. */
   private static void riders(ServerLevel level, CityManager city, CollapseInfo info, long now) {
      Player p = level.getNearestPlayer(info.center().getX() + 0.5, info.center().getY(), info.center().getZ() + 0.5, WindowRules.JUMPER_FAR, (java.util.function.Predicate<net.minecraft.world.entity.Entity>)null);
      if (p == null || info.affectedBlocks().isEmpty()) {
         return;
      }
      LongOpenHashSet planned = new LongOpenHashSet(info.affectedBlocks().size());
      for (BlockPos b : info.affectedBlocks()) {
         planned.add(b.asLong());
      }
      List<BlockPos> blocks = info.affectedBlocks();
      int want = WindowRules.riders(blocks.size(), level.random.nextFloat());
      int made = 0;
      Vec3 eye = p.getEyePosition();
      for (int t = 0; t < 160 && made < want; t++) {
         BlockPos b = blocks.get(level.random.nextInt(blocks.size()));
         BlockPos feet = b.above();
         if (planned.contains(feet.asLong()) || !free(level, feet) || !free(level, feet.above()) || !standable(level, feet)) {
            continue;
         }
         Direction edge = null;
         for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = feet.relative(d);
            if (free(level, n) && free(level, n.above()) && WindowPeril.outside(level, n)) {
               edge = d;
               break;
            }
         }
         Vec3 at = new Vec3(feet.getX() + 0.5, feet.getY() + 1.2, feet.getZ() + 0.5);
         if (edge == null || eye.distanceTo(at) < WindowRules.JUMPER_NEAR || !level.getEntitiesOfClass(CitizenEntity.class, new net.minecraft.world.phys.AABB(feet).inflate(2.0)).isEmpty()) {
            continue;
         }
         BlockHitResult hit = level.clip(new ClipContext(eye, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
         if (hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceTo(at) > 1.2) {
            continue; // hidden from the player: no use
         }
         CitizenEntity c = ViltrumiteEntities.CITIZEN.get().create(level);
         if (c == null) {
            return;
         }
         double fy = floorY(level, feet);
         float yaw = yaw(edge);
         c.moveTo(feet.getX() + 0.5 - edge.getStepX() * 0.2, fy + 0.01, feet.getZ() + 0.5 - edge.getStepZ() * 0.2, yaw, 20.0F);
         if (!level.noCollision(c, c.getBoundingBox().deflate(0.05))) {
            continue;
         }
         c.setSkin(level.random.nextInt());
         c.occupant = true;
         c.finalizeSpawn(level, level.getCurrentDifficultyAt(c.blockPosition()), MobSpawnType.EVENT, null, null);
         level.addFreshEntity(c);
         dev.baranhan.viltrumitecore.worldevent.aftermath.CityAftermath.onCitizenSpawn(level, c);
         c.setYHeadRot(yaw);
         c.yBodyRot = yaw;
         c.perilUntil = now + PerilRules.PERIL_MEMORY;
         c.teeterTicks = PerilRules.TEETER_MAX;
         c.getNavigation().stop();
         c.setCitizenState(CitizenState.TEETER);
         CitizenPeril.voice(c, CitizenVoices.Kind.SCREAM, 0.0F);
         made++;
         book(level).started++;
         if (LOG) {
            LOGGER.info("[jumper] rider at {} edge={}", feet, edge);
         }
      }
   }

   // ------------------------------------------------------------------ blocks

   static boolean glass(BlockState s) {
      return s.is(net.minecraftforge.common.Tags.Blocks.GLASS) || s.is(net.minecraftforge.common.Tags.Blocks.GLASS_PANES)
         || s.getBlock() instanceof net.minecraft.world.level.block.AbstractGlassBlock || s.getBlock() instanceof net.minecraft.world.level.block.StainedGlassPaneBlock;
   }

   private static boolean free(Level level, BlockPos p) {
      BlockState s = level.getBlockState(p);
      return s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty();
   }

   /** Air or glass (the person breaks it on the way out). */
   private static boolean passable(Level level, BlockPos p) {
      return free(level, p) || glass(level.getBlockState(p));
   }

   private static boolean standable(Level level, BlockPos feet) {
      return !Double.isNaN(floorY(level, feet));
   }

   /** Standing height on the feet block (slab, carpet or the block below), NaN when there is no floor. */
   static double floorY(Level level, BlockPos feet) {
      BlockState own = level.getBlockState(feet);
      if (!glass(own)) {
         VoxelShape s = own.getCollisionShape(level, feet);
         if (!s.isEmpty()) {
            double top = s.max(Direction.Axis.Y);
            return top <= 0.5 ? feet.getY() + top : Double.NaN;
         }
      }
      BlockPos below = feet.below();
      VoxelShape under = level.getBlockState(below).getCollisionShape(level, below);
      return under.isEmpty() || under.max(Direction.Axis.Y) < 0.5 ? Double.NaN : below.getY() + under.max(Direction.Axis.Y);
   }

   /** The highest block row a standing body (1.95 tall) reaches from a floor at {@code fy}. */
   static int bodyTop(double fy) {
      return (int)Math.floor(fy + 1.95);
   }

   private static float yaw(Direction out) {
      return (float)Math.toDegrees(Math.atan2(out.getStepZ(), out.getStepX())) - 90.0F;
   }

   /** For /city status: incidents, people started, misses. */
   public static int[] counts(Level level) {
      Book b = BOOKS.get(level.dimension());
      return b == null ? new int[3] : new int[]{b.incidents.size(), b.started, b.missed};
   }

   private static void log(String what, Incident i, String extra) {
      if (LOG) {
         LOGGER.info("[jumper] {} {} at {} {} {} power={} spawned={} {}", what, i.fire ? "fire" : "destruction", Math.round(i.x), Math.round(i.y), Math.round(i.z),
            Math.round(i.power), i.spawned, extra);
      }
   }
}
