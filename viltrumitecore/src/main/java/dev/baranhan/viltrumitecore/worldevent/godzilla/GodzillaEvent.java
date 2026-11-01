package dev.baranhan.viltrumitecore.worldevent.godzilla;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.power.api.CityInfrastructure;
import dev.baranhan.viltrumitecore.city.profile.CityProfile;
import dev.baranhan.viltrumitecore.city.profile.WalkGrid;
import dev.baranhan.viltrumitecore.entity.godzilla.GodzillaEntities;
import dev.baranhan.viltrumitecore.entity.godzilla.GodzillaEntity;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriTerrain;
import dev.baranhan.viltrumitecore.worldevent.godzilla.net.GodzillaFxS2CPacket;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ГОДЗИЛЛА — ГОРОДСКАЯ КАТАСТРОФА (docs/plans/2026-11-05-godzilla-city-catastrophe.md). Manual world event, one at
 * a time, started by {@code /godzilla start}; never spawns by itself. This class holds the running session (memory
 * only: a restart ends the event), the lifecycle (start, the endings, the emergency stop, the cleanup) and the hooks
 * the kaiju's body calls. {@link GodzillaSession#tick} runs from the body's own tick. Never loads a chunk.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class GodzillaEvent {
   static final Logger LOG = LoggerFactory.getLogger("viltrumitecore/godzilla");
   /** Lang key naming the catastrophe in the other events' "already running" refusals. */
   public static final String CONFLICT_KEY = "viltrumitecore.godzilla.conflict";
   /** Ticks without a body tick before the session counts the body as lost (unloaded with its chunk). */
   static final int LOST_TICKS = 100;
   @Nullable
   private static GodzillaSession session;

   private GodzillaEvent() {
   }

   @Nullable
   public static GodzillaSession current() {
      return session;
   }

   public static boolean running(@Nullable MinecraftServer server) {
      GodzillaSession s = session;
      return s != null && server != null && s.server == server && !s.closed;
   }

   @Nullable
   static GodzillaSession in(Level level) {
      GodzillaSession s = session;
      return s != null && !level.isClientSide && !s.closed && s.in(level) ? s : null;
   }

   // ------------------------------------------------------------------ lifecycle

   public enum Start {
      STARTED,
      RUNNING,
      NO_ROOM
   }

   /** Starts the catastrophe near {@code near} (the command source): the kaiju walks in from a loaded spot. */
   public static Start start(ServerLevel level, Vec3 near, long seed) {
      if (running(level.getServer())) {
         return Start.RUNNING;
      }
      RandomSource random = RandomSource.create(seed);
      Vec3 spawn = spawnPoint(level, near, random);
      if (spawn == null) {
         return Start.NO_ROOM;
      }
      GodzillaEntity e = GodzillaEntities.GODZILLA.get().create(level);
      if (e == null) {
         return Start.NO_ROOM;
      }
      float yaw = GodzillaEntity.yawTowards(near.x - spawn.x, near.z - spawn.z);
      e.moveTo(spawn.x, spawn.y, spawn.z, yaw, 0.0F);
      e.setYBodyRot(yaw);
      e.setYHeadRot(yaw);
      GodzillaSession s = new GodzillaSession(level.getServer(), level.dimension(), e.getUUID(), near, spawn, seed);
      s.body = e;
      s.startedAt = level.getGameTime();
      session = s;
      if (!level.addFreshEntity(e)) {
         session = null;
         return Start.NO_ROOM;
      }
      s.say(level, "viltrumitecore.godzilla.arrive", ChatFormatting.DARK_AQUA);
      GodzillaNet.always(level, GodzillaFxS2CPacket.ARRIVE, spawn, GodzillaEntity.forward(yaw), 1.0F, 0, GodzillaRules.FX_FAR * 1.5);
      s.alarm(level, e, true);
      LOG.info("Godzilla catastrophe started at {} {} {} in {} (seed {}, near {} {} {})", Mth.floor(spawn.x), Mth.floor(spawn.y), Mth.floor(spawn.z),
         level.dimension().location(), seed, Mth.floor(near.x), Mth.floor(near.y), Mth.floor(near.z));
      return Start.STARTED;
   }

   /** The emergency stop: the kaiju and everything the event owns go at once; the city damage stays (/city reset). */
   public static boolean stop(MinecraftServer server) {
      GodzillaSession s = session;
      if (s == null || s.server != server) {
         return false;
      }
      ServerLevel level = s.level();
      if (level != null) {
         removeOwned(level, s);
      }
      s.close();
      session = null;
      LOG.info("Godzilla catastrophe stopped ({} blocks destroyed, {} buildings hit)", s.destruction.destroyed(), s.destruction.buildingsHit());
      return true;
   }

   /**
    * Removes every kaiju body in every level that is not the running session's (orphans of a crashed or stopped
    * session), and, without a session, the event's alarm lease and the clients' lingering effects. Returns bodies removed.
    */
   public static int cleanup(MinecraftServer server) {
      GodzillaSession s = running(server) ? session : null;
      int removed = 0;
      for (ServerLevel level : server.getAllLevels()) {
         List<Entity> stray = new ArrayList<>();
         for (Entity any : level.getAllEntities()) {
            if (any instanceof GodzillaEntity && (s == null || !any.getUUID().equals(s.bodyId))) {
               stray.add(any);
            }
         }
         for (Entity any : stray) {
            any.discard();
            removed++;
         }
         if (s == null) {
            if (CityManager.of(level) != null) {
               CityInfrastructure.clearAlarm(level, "godzilla");
            }
            GodzillaNet.always(level, GodzillaFxS2CPacket.CLEAR, Vec3.ZERO, Vec3.ZERO, 0.0F, 0, Double.MAX_VALUE / 4.0);
         }
      }
      return removed;
   }

   private static void removeOwned(ServerLevel level, GodzillaSession s) {
      Entity body = level.getEntity(s.bodyId);
      if (body != null) {
         body.discard();
      }
      s.destruction.clear();
      if (CityManager.of(level) != null) {
         CityInfrastructure.clearAlarm(level, "godzilla");
      }
      GodzillaNet.always(level, GodzillaFxS2CPacket.CLEAR, Vec3.ZERO, Vec3.ZERO, 0.0F, 0, Double.MAX_VALUE / 4.0);
   }

   /** A natural ending (death, retreat, lost): the summary line, the alarm released, the session closed. */
   static void finish(ServerLevel level, GodzillaSession s, String key) {
      if (s.closed) {
         return;
      }
      Component line = Component.translatable(key, s.destruction.destroyed(), s.destruction.buildingsHit(), s.breaths,
         Math.round(s.damageTaken)).withStyle(ChatFormatting.GOLD);
      for (ServerPlayer p : level.players()) {
         p.sendSystemMessage(line);
      }
      Entity body = level.getEntity(s.bodyId);
      if (body != null) {
         body.discard();
      }
      s.destruction.clear();
      if (CityManager.of(level) != null) {
         CityInfrastructure.clearAlarm(level, "godzilla");
      }
      s.close();
      if (session == s) {
         session = null;
      }
      LOG.info("Godzilla catastrophe ended: {} ({} blocks destroyed, {} buildings hit, {} breaths, {} damage taken, {} heroes hit)", key,
         s.destruction.destroyed(), s.destruction.buildingsHit(), s.breaths, Math.round(s.damageTaken), s.heroesHit);
   }

   // ------------------------------------------------------------------ hooks of the body

   /** Server tick of a kaiju body; false = not the session's (or the event is over): the body goes. */
   public static boolean entityTick(ServerLevel level, GodzillaEntity e) {
      GodzillaSession s = in(level);
      if (s == null || !s.bodyId.equals(e.getUUID())) {
         return false;
      }
      return s.tick(level, e);
   }

   public static void onFootfall(ServerLevel level, GodzillaEntity e, boolean left) {
      GodzillaSession s = in(level);
      if (s != null && s.bodyId.equals(e.getUUID())) {
         GodzillaActions.footfall(level, s, e, left);
      }
   }

   /** A hit landed (after the zone factor and the caps): threat, rage, stagger, the flinch and the fall. */
   public static void onHurt(ServerLevel level, GodzillaEntity e, DamageSource source, float dealt, int zone, Vec3 at) {
      GodzillaSession s = in(level);
      if (s == null || !s.bodyId.equals(e.getUUID())) {
         return;
      }
      s.damageTaken += dealt;
      s.burst += dealt;
      s.stagger += dealt;
      Entity by = source.getEntity();
      if (by != null && s.targetable(by)) {
         GodzillaSession.Threat t = s.threat(by.getUUID());
         t.value += dealt * 0.6F + 4.0F;
         t.dealt += dealt;
         t.seen = level.getGameTime();
         if (by instanceof Player p) {
            t.airborne = airborne(p);
         }
         if (s.target == null || s.targetThreat() < t.value * 0.8F) {
            s.target = by.getUUID();
            s.lostTarget = 0;
         }
         if (s.phase == GodzillaPhase.ARRIVAL || s.phase == GodzillaPhase.EXPLORE) {
            s.phase(level, e, GodzillaPhase.COMBAT);
         }
      }
      GodzillaNet.fx(level, GodzillaFxS2CPacket.HIT, at, Vec3.ZERO, dealt, zone);
      if (!e.isAlive() || s.phase == GodzillaPhase.DEATH) {
         return;
      }
      GodzillaMove move = e.move();
      boolean interruptible = move == GodzillaMove.NONE || move == GodzillaMove.ROAR || move == GodzillaMove.CLAW_L || move == GodzillaMove.CLAW_R
         || move == GodzillaMove.STOMP || move == GodzillaMove.CHARGE && dealt >= GodzillaRules.HURT_HIT * 1.5F && zone == GodzillaRules.P_HEAD;
      if (s.stagger >= GodzillaRules.STAGGER_FALL && s.cdFall <= 0 && !move.grounded() && move != GodzillaMove.FIRE) {
         s.stagger = 0.0F;
         s.breath.end(e);
         e.walkTo(null);
         s.start(e, GodzillaMove.FALL, 0);
      } else if (dealt >= GodzillaRules.HURT_HIT && s.cdHurt <= 0 && interruptible) {
         if (move == GodzillaMove.CHARGE) {
            s.breath.end(e);
         }
         e.walkTo(null);
         s.start(e, GodzillaMove.HURT, zone);
      }
   }

   public static void onDeath(ServerLevel level, GodzillaEntity e) {
      GodzillaSession s = in(level);
      if (s == null || !s.bodyId.equals(e.getUUID())) {
         return;
      }
      s.breath.end(e);
      e.walkTo(null);
      s.phase(level, e, GodzillaPhase.DEATH);
      s.start(e, GodzillaMove.DEATH, 0);
      s.say(level, "viltrumitecore.godzilla.death", ChatFormatting.GOLD);
   }

   // ------------------------------------------------------------------ ground and placement

   /** May the body step here (entity-ticking ground; never into an unloaded or frozen chunk)? */
   public static boolean canEnter(Level level, double x, double z) {
      if (!(level instanceof ServerLevel sl)) {
         return true;
      }
      BlockPos pos = BlockPos.containing(x, 64.0, z);
      return sl.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) && sl.isPositionEntityTicking(pos);
   }

   /**
    * Street height under the body: the lowest ground (sea floor in water) of five points over the footprint, the city
    * walk grid when it knows the spot. When even the lowest point is far above the body it stands in a building's
    * mass and wades through it: the height is kept. NaN when nothing is loaded there.
    */
   public static double groundAt(Level level, double x, double z, double currentY) {
      double r = 0.10 * GodzillaRules.HEIGHT;
      double best = Double.NaN;
      double[][] pts = {{0.0, 0.0}, {r, 0.0}, {-r, 0.0}, {0.0, r}, {0.0, -r}};
      for (double[] p : pts) {
         int bx = Mth.floor(x + p[0]);
         int bz = Mth.floor(z + p[1]);
         if (!level.hasChunk(bx >> 4, bz >> 4)) {
            continue;
         }
         int h = level.getHeight(Heightmap.Types.OCEAN_FLOOR, bx, bz);
         best = Double.isNaN(best) ? h : Math.min(best, h);
      }
      CityManager city = level instanceof ServerLevel sl ? CityManager.of(sl) : null;
      if (city != null) {
         int feet = city.grid().feetY(Mth.floor(x), Mth.floor(z));
         if (feet != WalkGrid.NONE && (Double.isNaN(best) || feet < best + 2)) {
            best = Double.isNaN(best) ? feet : Math.min(best, feet);
         }
      }
      if (Double.isNaN(best)) {
         return best;
      }
      if (!Double.isNaN(currentY) && currentY > level.getMinBuildHeight() && best > currentY + 6.0) {
         return currentY;
      }
      return best;
   }

   /** A loaded, low and open spot about {@link GodzillaRules#SPAWN_DISTANCE} away (the sea or a wide street first). */
   @Nullable
   static Vec3 spawnPoint(ServerLevel level, Vec3 near, RandomSource random) {
      CityManager city = CityManager.of(level);
      CityProfile arena = city != null ? city.profile() : null;
      double base = random.nextDouble() * Math.PI * 2.0;
      for (double dist : new double[]{GodzillaRules.SPAWN_DISTANCE, 120.0, 96.0, 72.0, 56.0}) {
         Vec3 best = null;
         double bestScore = -1.0E9;
         for (int i = 0; i < 24; i++) {
            double a = base + i * Math.PI * 2.0 / 24.0;
            double x = near.x + Math.cos(a) * dist;
            double z = near.z + Math.sin(a) * dist;
            if (!canEnter(level, x, z)) {
               continue;
            }
            if (arena != null && !arena.inArena(Mth.floor(x), Mth.floor(near.y), Mth.floor(z))) {
               continue;
            }
            double ground = groundAt(level, x, z, Double.NaN);
            int top = ChitauriTerrain.surface(level, x, z);
            if (Double.isNaN(ground) || top == ChitauriTerrain.UNKNOWN) {
               continue;
            }
            double score = -ground * 0.6 - Math.max(0.0, top - ground) * 0.25 + random.nextDouble() * 6.0;
            if (score > bestScore) {
               bestScore = score;
               best = new Vec3(x, ground, z);
            }
         }
         if (best != null) {
            return best;
         }
      }
      return null;
   }

   /** Flying or high above the ground (hero flight, elytra, jetpack). */
   static boolean airborne(Player p) {
      if (p.onGround() || p.isInWater() || p.isPassenger()) {
         return false;
      }
      if (p.getAbilities().flying || p.isFallFlying()) {
         return true;
      }
      int ground = ChitauriTerrain.surface(p.level(), p.getX(), p.getZ());
      return ground != ChitauriTerrain.UNKNOWN && p.getY() - ground > 3.5;
   }

   // ------------------------------------------------------------------ Forge events

   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      GodzillaSession s = session;
      if (event.phase != TickEvent.Phase.END || s == null) {
         return;
      }
      MinecraftServer server = event.getServer();
      if (s.server != server || s.closed) {
         session = null;
         return;
      }
      ServerLevel level = s.level();
      if (level == null) {
         stop(server);
         return;
      }
      // the body unloaded with its chunk (nobody near) and was not saved: the kaiju is gone
      if (level.getGameTime() - s.lastBodyTick > LOST_TICKS && level.getEntity(s.bodyId) == null) {
         GodzillaEvent.finish(level, s, "viltrumitecore.godzilla.end.lost");
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
      GodzillaSession s = session;
      if (s != null && event.getEntity() instanceof ServerPlayer p && s.barPlayers.remove(p.getUUID())) {
         s.bar.removePlayer(p);
      }
   }

   @SubscribeEvent
   public static void onLivingDeath(LivingDeathEvent event) {
      GodzillaSession s = session;
      if (s != null && event.getSource().getEntity() instanceof GodzillaEntity) {
         s.kills++;
      }
   }

   @SubscribeEvent
   public static void onServerStopping(ServerStoppingEvent event) {
      GodzillaSession s = session;
      if (s != null) {
         s.close();
         session = null;
      }
   }
}
