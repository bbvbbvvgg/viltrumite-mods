package dev.baranhan.viltrumitecore.hero.reinhard.ability;

import dev.baranhan.viltrumitecore.hero.Annihilation;
import dev.baranhan.viltrumitecore.hero.CarveJobs;
import dev.baranhan.viltrumitecore.hero.reinhard.*;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.*;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.WorldCutS2CPacket;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

/** Detached, level-owned cast. Its clock and victims do not depend on a living caster. */
public final class WorldCut implements AbilityHandler {
   /** Runtime boundary: a level adapter supplies carve progress, candidates and the strike. */
   public interface Environment {
      void blast();
      default void progress(long elapsed, boolean finished) { }
      default boolean pendingWork() { return false; }
      int carveFront();
      Collection<UUID> targets(int through);
      void strike(UUID target);
   }
   private final long startTime;
   private final Environment environment;
   private final Set<UUID> hit = new HashSet<>();
   private boolean blasted;
   private boolean finished;
   private boolean waveComplete;
   private boolean dropped;
   public WorldCut() { this(0, null); }
   private WorldCut(long startTime, Environment environment) {
      this.startTime = startTime; this.environment = environment;
   }
   public static WorldCut timed(long startTime, Environment environment) {
      return new WorldCut(startTime, Objects.requireNonNull(environment));
   }
   public static int visualFront(double elapsed) {
      return (int)Math.max(0, Math.min(ReinhardRules.CUT_LENGTH,
         (elapsed - ReinhardRules.WORLD_CUT_BLAST) * ReinhardRules.CUT_LENGTH / ReinhardRules.WORLD_CUT_WAVE_TICKS));
   }
   public static int carveFront(@javax.annotation.Nullable WorldCutCarve carve) { return carve == null ? 0 : carve.frontAlong(); }
   public boolean blasted() { return blasted; }
   public boolean finished() { return finished; }
   /** The owning level went away (unload, server stop) before the cast could end: it never blasts again. */
   public void drop() { dropped = true; }
   public boolean dropped() { return dropped; }
   public boolean advance(long gameTime) {
      long elapsed = gameTime - startTime;
      if (elapsed < ReinhardRules.WORLD_CUT_BLAST) return false;
      if (!blasted) { environment.blast(); blasted = true; }
      int front = Math.min(ReinhardRules.CUT_LENGTH, Math.max(visualFront(elapsed), environment.carveFront()));
      if(!waveComplete) {
         for (UUID target : environment.targets(front)) if (hit.add(target)) environment.strike(target);
         waveComplete=elapsed >= ReinhardRules.WORLD_CUT_BLAST + ReinhardRules.WORLD_CUT_WAVE_TICKS;
      }
      // Queue ownership extends admission protection, NOT the damaging wave's lifetime.
      finished=waveComplete && !environment.pendingWork();
      if (finished || elapsed % 10 == 0) environment.progress(elapsed,finished);
      return finished;
   }
   public static void start(ReinhardState state) { start(state, WorldCutSweep.FROM_RIGHT); }
   /** The action phase is the {@link WorldCutSweep}: every viewer plays the clip of the same shoulder. */
   public static void start(ReinhardState state, int sweep) {
      state.cooldowns[ReinhardAbility.WORLD_CUT.ordinal()] = ReinhardRules.WORLD_CUT_COOLDOWN;
      state.action.start(ReinhardAbility.WORLD_CUT, ReinhardRules.WORLD_CUT_BLAST + 20, -1, WorldCutSweep.ofPhase(sweep));
   }
   @Override public boolean tryStart(ServerPlayer player, ReinhardState state) {
      if (!state.reid.drawn()) return false;
      ServerLevel level = player.serverLevel();
      if (WorldCutManager.get(level).size()>=2) return false;
      WorldCutShape shape = new WorldCutShape(player.position(), Vec3.directionFromRotation(0, player.getYRot()), level.getGameTime());
      int[] surface = new int[ReinhardRules.CUT_LENGTH];
      for (int a=0; a<surface.length; a++) {
         BlockPos p = BlockPos.containing(shape.axis(a + .5));
         surface[a] = level.getWorldBorder().isWithinBounds(p) && level.isLoaded(p)
            ? level.getHeight(Heightmap.Types.WORLD_SURFACE, p.getX(), p.getZ()) - 1
            : (int)Math.floor(shape.origin().y) - 1;
      }
      Live world = new Live(level, UUID.randomUUID(), player.getUUID(), shape, surface, level.getGameTime());
      start(state, WorldCutSweep.of(shape.seed()));
      state.cutAnchor=player.position();
      state.worldCut = timed(world.startTime, world);
      WorldCutManager.get(level).submit(state.worldCut);
      world.send(WorldCutS2CPacket.START);
      return true;
   }
   /** Shortest horizontal distance to the finite 200-block axis, including its endpoints. */
   public static double distanceToLine(WorldCutShape shape, Vec3 point) {
      Vec3 from=shape.axis(0), delta=point.subtract(from);
      double a=Math.max(0,Math.min(ReinhardRules.CUT_LENGTH,delta.x*shape.dirXZ().x+delta.z*shape.dirXZ().z));
      Vec3 near=from.add(shape.dirXZ().scale(a));
      return Math.hypot(point.x-near.x,point.z-near.z);
   }
   private static final class Live implements Environment {
      final ServerLevel level; final UUID castId, casterId; final WorldCutShape shape;
      final int[] surface; final long startTime; WorldCutCarve carve;
      final long telemetryStart=System.nanoTime();
      final Map<UUID, Entity> candidates = new HashMap<>();
      Live(ServerLevel level, UUID castId, UUID casterId, WorldCutShape shape, int[] surface, long startTime) {
         this.level=level; this.castId=castId; this.casterId=casterId; this.shape=shape; this.surface=surface; this.startTime=startTime;
      }
      void send(int phase) {
         WorldCutS2CPacket packet=new WorldCutS2CPacket(castId,casterId,shape,surface,startTime,phase);
         for (ServerPlayer viewer:level.players())
            if(distanceToLine(shape,viewer.position()) <= ReinhardRules.WORLD_CUT_VIEW_RANGE) CoreMessages.sendToPlayer(packet,viewer);
      }
      public void blast() {
         send(WorldCutS2CPacket.BLAST);
         carve=new WorldCutCarve(shape,surface,level.getMinBuildHeight(),level.getMaxBuildHeight(),level.getWorldBorder()::isWithinBounds);
         CarveJobs.submit(level,carve);
      }
      public void progress(long elapsed, boolean finished) { if(finished)CutTelemetry.done("ground",castId,level,elapsed,telemetryStart,carve); send(finished ? WorldCutS2CPacket.DONE : WorldCutS2CPacket.WORK); }
      public boolean pendingWork() { return carve!=null && CarveJobs.pending(level,carve); }
      public int carveFront() { return WorldCut.carveFront(carve); }
      public Collection<UUID> targets(int through) {
         candidates.clear();
         if(through <= 0) return List.of();
         AABB bounds=shape.hitBox(0,surface[0]);
         for(int a=1;a<through;a++) bounds=bounds.minmax(shape.hitBox(a,surface[a]));
         for(Entity e:level.getEntities((Entity)null,bounds,e -> e instanceof LivingEntity || e instanceof PartEntity<?>)) {
            Entity resolved=e instanceof PartEntity<?> part ? part.getParent() : e;
            if(resolved.getUUID().equals(casterId)) continue;
            for(int a=0;a<through;a++) if(shape.intersects(e.getBoundingBox(),a,surface[a])) {
               candidates.put(resolved.getUUID(),resolved); break;
            }
         }
         return candidates.keySet();
      }
      public void strike(UUID id) {
         ServerPlayer caster=level.getServer().getPlayerList().getPlayer(casterId);
         // No cross-dimension attribution. A logout or death does not cancel this cast.
         if(caster != null && (caster.serverLevel()!=level || !caster.isAlive())) caster=null;
         Annihilation.strike(caster,candidates.get(id));
      }
   }
}
