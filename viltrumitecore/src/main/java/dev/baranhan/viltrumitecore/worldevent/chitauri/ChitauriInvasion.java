package dev.baranhan.viltrumitecore.worldevent.chitauri;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.profile.CityProfile;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriBolt;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriChariot;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriEntities;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriLeviathan;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriMob;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriSoldier;
import dev.baranhan.viltrumitecore.hero.control.TimeFreeze;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriNet;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriPortalS2CPacket;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Chitauri invasion (Avengers, 2012) as one manual world event: {@code /chitauri start}
 * opens a portal {@link ChitauriRules#PORTAL_HEIGHT} blocks above the operator, the sky darkens,
 * a beam strikes up, the portal grows, lightning plays on its rim, the first chariots and the
 * first Leviathan come through, then the phases run (ground, air, Leviathan, climax) until the
 * closing severs the hive and the portal collapses.
 * <p>
 * Server authoritative and memory only: one invasion per server, nothing is saved (every
 * Chitauri entity is no-save; a restart ends the invasion, the destruction stays in the world
 * and in the city journal). Never loads chunks: spawns go only into entity-ticking chunks, and
 * the creatures turn back at unloaded ones. Caps in {@link ChitauriRules}. Pauses while time is
 * stopped; ends itself when nobody is in the dimension for {@link ChitauriRules#EMPTY_LEVEL_TICKS}.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class ChitauriInvasion {
   static final Logger LOG = LoggerFactory.getLogger("viltrumitecore/chitauri");
   /** Players this far from the anchor (horizontally) see the phase bar and titles. */
   public static final double VIEW_RANGE = 240.0;
   private static final double LEVIATHAN_BAR_RANGE = 110.0;

   @Nullable
   private static Session session;

   private ChitauriInvasion() {
   }

   enum Kind {
      GUNSHIP,
      CARRIER,
      LEVIATHAN
   }

   static final class Pending {
      final Kind kind;
      final int born;
      int due;

      Pending(Kind kind, int due) {
         this.kind = kind;
         this.due = due;
         this.born = due;
      }
   }

   public static final class Session {
      final MinecraftServer server;
      final ResourceKey<Level> dimension;
      final Vec3 anchor;
      final Vec3 portal;
      final float tiltYaw;
      final long seed;
      final Vec3 areaCentre;
      final double areaRadius;
      final boolean inCity;
      final RandomSource random;
      ChitauriPhase phase = ChitauriPhase.OPENING;
      int phaseTick;
      int clock;
      int waveIndex;
      int nextWaveIn = -1;
      int quietTicks;
      int emptyTicks;
      int nextStrike = 40;
      int squads;
      int leviathansSent;
      int killedSoldiers;
      int killedChariots;
      int killedLeviathans;
      final List<Pending> queue = new ArrayList<>();
      final Set<UUID> soldiers = new LinkedHashSet<>();
      final Set<UUID> chariots = new LinkedHashSet<>();
      final Set<UUID> leviathans = new LinkedHashSet<>();
      final Map<UUID, Integer> heroKills = new HashMap<>();
      final ServerBossEvent bar;
      final Map<UUID, ServerBossEvent> leviathanBars = new HashMap<>();

      Session(MinecraftServer server, ResourceKey<Level> dimension, Vec3 anchor, Vec3 portal, float tiltYaw, long seed, Vec3 areaCentre, double areaRadius,
         boolean inCity) {
         this.server = server;
         this.dimension = dimension;
         this.anchor = anchor;
         this.portal = portal;
         this.tiltYaw = tiltYaw;
         this.seed = seed;
         this.areaCentre = areaCentre;
         this.areaRadius = areaRadius;
         this.inCity = inCity;
         this.random = RandomSource.create(seed);
         this.bar = new ServerBossEvent(Component.translatable("viltrumitecore.chitauri.bar", phaseName(ChitauriPhase.OPENING)),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
         this.bar.setDarkenScreen(false);
         this.bar.setCreateWorldFog(false);
      }

      public ChitauriPhase phase() {
         return this.phase;
      }

      public int phaseTick() {
         return this.phaseTick;
      }

      public int clock() {
         return this.clock;
      }

      public ResourceKey<Level> dimension() {
         return this.dimension;
      }

      public Vec3 anchor() {
         return this.anchor;
      }

      public Vec3 portal() {
         return this.portal;
      }

      public boolean inCity() {
         return this.inCity;
      }

      public int soldiers() {
         return this.soldiers.size();
      }

      public int chariots() {
         return this.chariots.size();
      }

      public int leviathans() {
         return this.leviathans.size();
      }

      public int pending() {
         return this.queue.size();
      }

      public int leviathansSent() {
         return this.leviathansSent;
      }

      public int killedSoldiers() {
         return this.killedSoldiers;
      }

      public int killedChariots() {
         return this.killedChariots;
      }

      public int killedLeviathans() {
         return this.killedLeviathans;
      }

      public int waveIndex() {
         return this.waveIndex;
      }

      boolean in(Level level) {
         return !level.isClientSide && level.getServer() == this.server && level.dimension().equals(this.dimension);
      }

      Vec3 normal() {
         double[] n = ChitauriRules.portalNormal(this.tiltYaw);
         return new Vec3(n[0], n[1], n[2]);
      }
   }

   // ------------------------------------------------------------------ queries (entities, commands, other events)

   public static boolean running(@Nullable MinecraftServer server) {
      Session s = session;
      return s != null && server != null && s.server == server && s.phase != ChitauriPhase.ENDED;
   }

   @Nullable
   public static Session session() {
      return session;
   }

   /** The invasion's ground anchor in this level, or null (no invasion here: summoned creatures use their own position). */
   @Nullable
   public static Vec3 anchor(Level level) {
      Session s = session;
      return s != null && s.in(level) ? s.anchor : null;
   }

   public static Vec3 areaCentre(Level level, Vec3 fallback) {
      Session s = session;
      return s != null && s.in(level) ? s.areaCentre : fallback;
   }

   public static double areaRadius(Level level) {
      Session s = session;
      return s != null && s.in(level) ? s.areaRadius : 64.0;
   }

   /** How many more soldiers may come now (Leviathan deploys ask this). 0 outside the fighting phases. */
   public static int soldierRoom(Level level) {
      Session s = session;
      if (s == null || !s.in(level) || ChitauriRules.quietPortal(s.phase, s.phaseTick) && s.phase != ChitauriPhase.OPENING
         || s.phase == ChitauriPhase.CLOSING || s.phase == ChitauriPhase.ENDED) {
         return 0;
      }
      return Math.max(0, ChitauriRules.MAX_SOLDIERS - s.soldiers.size());
   }

   /** Spawns one soldier (entity-ticking chunk only); counted by the invasion when one runs here. */
   @Nullable
   public static ChitauriSoldier spawnSoldier(ServerLevel level, Vec3 at, byte role, int squad) {
      if (!ChitauriTerrain.ticking(level, at.x, at.y, at.z)) {
         return null;
      }
      ChitauriSoldier soldier = ChitauriEntities.SOLDIER.get().create(level);
      if (soldier == null) {
         return null;
      }
      soldier.setRole(role);
      soldier.squad = squad;
      soldier.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360.0F, 0.0F);
      soldier.yHeadRot = soldier.getYRot();
      soldier.yBodyRot = soldier.getYRot();
      if (!level.addFreshEntity(soldier)) {
         return null;
      }
      Session s = session;
      if (s != null && s.in(level)) {
         s.soldiers.add(soldier.getUUID());
      }
      return soldier;
   }

   /** Every Chitauri death reports here (kill counters, Leviathan bars, caps). */
   public static void onKilled(Entity entity, @Nullable DamageSource source) {
      Session s = session;
      if (s == null || !s.in(entity.level())) {
         return;
      }
      boolean severed = s.phase == ChitauriPhase.CLOSING || s.phase == ChitauriPhase.ENDED;
      UUID id = entity.getUUID();
      boolean counted = false;
      if (entity instanceof ChitauriSoldier) {
         counted = s.soldiers.remove(id);
         if (counted && !severed) {
            s.killedSoldiers++;
         }
      } else if (entity instanceof ChitauriChariot) {
         counted = s.chariots.remove(id);
         if (counted && !severed) {
            s.killedChariots++;
         }
      } else if (entity instanceof ChitauriLeviathan) {
         counted = s.leviathans.remove(id);
         ServerBossEvent bar = s.leviathanBars.remove(id);
         if (bar != null) {
            bar.removeAllPlayers();
         }
         if (counted && !severed) {
            s.killedLeviathans++;
            if (entity.level() instanceof ServerLevel level) {
               Component msg = Component.translatable("viltrumitecore.chitauri.chat.leviathan_down").withStyle(ChatFormatting.AQUA);
               for (ServerPlayer p : viewers(level, s)) {
                  p.displayClientMessage(msg, true);
               }
            }
         }
      }
      if (counted && !severed && source != null && source.getEntity() instanceof ServerPlayer player) {
         s.heroKills.merge(player.getUUID(), entity instanceof ChitauriLeviathan ? 10 : entity instanceof ChitauriChariot ? 3 : 1, Integer::sum);
      }
   }

   // ------------------------------------------------------------------ control

   /** Opens the portal above {@code at}. False when an invasion already runs on this server. */
   public static boolean start(ServerLevel level, Vec3 at) {
      if (session != null) {
         return false;
      }
      RandomSource rnd = level.getRandom();
      long seed = rnd.nextLong();
      int surfaceTop = ChitauriTerrain.surfaceMax(level, at.x, at.z, 24);
      double py = Math.max(at.y + ChitauriRules.PORTAL_HEIGHT, (surfaceTop == ChitauriTerrain.UNKNOWN ? at.y : surfaceTop) + 36.0);
      py = Math.min(py, level.getMaxBuildHeight() - 12.0);
      Vec3 portal = new Vec3(at.x, py, at.z);
      Vec3 areaCentre = at;
      double areaRadius = 80.0;
      boolean inCity = false;
      CityManager city = CityManager.of(level);
      if (city != null) {
         CityProfile p = city.profile();
         if (p.inArena(Mth.floor(at.x), Mth.clamp(Mth.floor(at.y), p.minY, p.maxY), Mth.floor(at.z))) {
            inCity = true;
            double halfX = (p.maxX - p.minX) / 2.0;
            double halfZ = (p.maxZ - p.minZ) / 2.0;
            areaRadius = Mth.clamp(Math.min(halfX, halfZ), 64.0, 140.0);
            if (halfX <= 140.0 && halfZ <= 140.0) {
               areaCentre = new Vec3(p.minX + halfX, at.y, p.minZ + halfZ);
            }
         }
      }
      double dx = areaCentre.x - at.x;
      double dz = areaCentre.z - at.z;
      float tiltYaw = dx * dx + dz * dz > 64.0 ? (float)(Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG) : rnd.nextFloat() * 360.0F;
      Session s = new Session(level.getServer(), level.dimension(), at, portal, tiltYaw, seed, areaCentre, areaRadius, inCity);
      session = s;
      ChitauriDestruction.reset();
      LOG.info("Chitauri invasion opened over {} {} {} in {} (portal y {}, area r {}, city {})", Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z),
         level.dimension().location(), Mth.floor(py), Math.round(areaRadius), inCity);
      announce(level, s, ChitauriPhase.OPENING);
      ChitauriSounds.playWide(level, portal, ChitauriSounds.PORTAL_THUNDER, 7.0F, 0.6F, VIEW_RANGE);
      sendPortal(level, s);
      updateBars(level, s);
      return true;
   }

   /** Normal end: skips to the closing (the hive severs, the portal collapses). */
   public static boolean end(MinecraftServer server) {
      Session s = session;
      if (s == null || s.server != server || s.phase == ChitauriPhase.CLOSING || s.phase == ChitauriPhase.ENDED) {
         return false;
      }
      ServerLevel level = server.getLevel(s.dimension);
      if (level == null) {
         stop(server);
         return true;
      }
      enter(level, s, ChitauriPhase.CLOSING);
      return true;
   }

   /** Emergency stop: removes every Chitauri at once, closes the portal without a cinematic. */
   public static boolean stop(MinecraftServer server) {
      Session s = session;
      if (s == null || s.server != server) {
         return false;
      }
      ServerLevel level = server.getLevel(s.dimension);
      if (level != null) {
         int removed = purge(level);
         LOG.info("Chitauri invasion stopped (emergency), {} entities removed", removed);
         ChitauriNet.portal(level, ChitauriPortalS2CPacket.inactive());
      }
      close(s);
      return true;
   }

   /** Forces the next wave now (fighting phases; the opening jumps to the ground assault). */
   public static boolean nextWave(MinecraftServer server) {
      Session s = session;
      if (s == null || s.server != server) {
         return false;
      }
      ServerLevel level = server.getLevel(s.dimension);
      if (level == null) {
         return false;
      }
      if (s.phase == ChitauriPhase.OPENING) {
         enter(level, s, ChitauriPhase.GROUND_ASSAULT);
         return true;
      }
      if (!s.phase.fighting()) {
         return false;
      }
      ChitauriRules.Wave w = ChitauriRules.wave(s.phase, s.waveIndex);
      if (w == null) {
         w = ChitauriRules.wave(s.phase, Math.max(0, s.waveIndex - 1));
      }
      if (w == null) {
         return false;
      }
      sendWave(level, s, w);
      return true;
   }

   /** Jumps to a phase (testing, staging); false when the phase is not reachable. */
   public static boolean jump(MinecraftServer server, ChitauriPhase phase) {
      Session s = session;
      if (s == null || s.server != server || phase == ChitauriPhase.ENDED) {
         return false;
      }
      ServerLevel level = server.getLevel(s.dimension);
      if (level == null) {
         return false;
      }
      enter(level, s, phase);
      return true;
   }

   private static int purge(ServerLevel level) {
      List<Entity> doomed = new ArrayList<>();
      for (Entity e : level.getAllEntities()) {
         if (e instanceof ChitauriMob || e instanceof ChitauriBolt) {
            doomed.add(e);
         }
      }
      for (Entity e : doomed) {
         e.ejectPassengers();
      }
      for (Entity e : doomed) {
         e.discard();
      }
      return doomed.size();
   }

   private static void close(Session s) {
      s.bar.removeAllPlayers();
      for (ServerBossEvent bar : s.leviathanBars.values()) {
         bar.removeAllPlayers();
      }
      s.leviathanBars.clear();
      s.queue.clear();
      s.phase = ChitauriPhase.ENDED;
      if (session == s) {
         session = null;
      }
   }

   // ------------------------------------------------------------------ tick

   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      Session s = session;
      if (event.phase != TickEvent.Phase.END || s == null) {
         return;
      }
      MinecraftServer server = event.getServer();
      if (s.server != server) {
         session = null;
         return;
      }
      ServerLevel level = server.getLevel(s.dimension);
      if (level == null) {
         close(s);
         return;
      }
      if (level.players().isEmpty()) {
         // nobody to defend the city: the invasion holds still, then gives up (nothing runs unobserved)
         if (++s.emptyTicks > ChitauriRules.EMPTY_LEVEL_TICKS) {
            LOG.info("Chitauri invasion ended: nobody left in {}", s.dimension.location());
            stop(server);
         }
         return;
      }
      s.emptyTicks = 0;
      if (TimeFreeze.worldStopped(level)) {
         return; // stopped time stops the invasion's clock too
      }
      s.clock++;
      s.phaseTick++;
      if (s.clock % 10 == 0) {
         prune(level, s);
      }
      switch (s.phase) {
         case OPENING -> tickOpening(level, s);
         case GROUND_ASSAULT, AIR_ASSAULT, LEVIATHAN, CLIMAX -> tickFighting(level, s);
         case CLOSING -> tickClosing(level, s);
         default -> {
         }
      }
      if (session != s) {
         return;
      }
      tickStorm(level, s);
      drain(level, s);
      if (s.phase != ChitauriPhase.ENDED && s.phase != ChitauriPhase.CLOSING && s.phaseTick >= ChitauriRules.length(s.phase)) {
         enter(level, s, s.phase.next());
      } else if (s.phase == ChitauriPhase.CLOSING && s.phaseTick >= ChitauriRules.length(s.phase)) {
         finish(level, s);
         return;
      }
      if (session != s) {
         return;
      }
      if (s.clock % 10 == 0) {
         updateBars(level, s);
      }
      if (s.clock % 40 == 0) {
         sendPortal(level, s);
      }
      if (s.clock % 20 == 0) {
         markDanger(level, s);
      }
   }

   private static void tickOpening(ServerLevel level, Session s) {
      int t = s.phaseTick;
      if (t >= ChitauriRules.BEAT_BEAM && t % 40 == 0) {
         // the sky tears open: people in the street stop and look up (curiosity, nobody runs yet)
         ChitauriDestruction.spectacle(level, s.portal, 60);
      }
      if (t == ChitauriRules.BEAT_BEAM) {
         // the beam strikes up from the anchor and tears the sky open
         ChitauriSounds.playWide(level, s.portal, ChitauriSounds.PORTAL_OPEN, 9.0F, 1.0F, VIEW_RANGE + 60.0);
         ChitauriNet.fxAlways(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.PORTAL_STRIKE, s.anchor, s.portal.subtract(s.anchor), 2.0F, 2), s.anchor,
            VIEW_RANGE + 60.0);
         sendPortal(level, s);
      } else if (t == ChitauriRules.BEAT_PORTAL_FULL) {
         ChitauriSounds.playWide(level, s.portal, ChitauriSounds.PORTAL_THUNDER, 8.0F, 0.8F, VIEW_RANGE + 60.0);
         sendPortal(level, s);
      } else if (t == ChitauriRules.BEAT_FIRST_CHARIOTS) {
         for (int i = 0; i < 3; i++) {
            s.queue.add(new Pending(Kind.GUNSHIP, s.clock + i * 11));
         }
         message(level, s, "viltrumitecore.chitauri.chat.first_chariots", ChatFormatting.DARK_AQUA);
      } else if (t == ChitauriRules.BEAT_FIRST_LEVIATHAN) {
         s.queue.add(new Pending(Kind.LEVIATHAN, s.clock));
      }
   }

   private static void tickFighting(ServerLevel level, Session s) {
      if (s.nextWaveIn > 0 && --s.nextWaveIn == 0) {
         ChitauriRules.Wave w = ChitauriRules.wave(s.phase, s.waveIndex);
         if (w != null) {
            sendWave(level, s, w);
         }
      }
      // a phase with no waves left ends early once the heroes cleared the sky
      if (s.phase != ChitauriPhase.CLIMAX && ChitauriRules.wave(s.phase, s.waveIndex) == null && s.queue.isEmpty()
         && s.soldiers.isEmpty() && s.chariots.isEmpty() && s.leviathans.isEmpty()) {
         if (++s.quietTicks > 100) {
            enter(level, s, s.phase.next());
         }
      } else {
         s.quietTicks = 0;
      }
   }

   private static void tickClosing(ServerLevel level, Session s) {
      int t = s.phaseTick;
      if (t == 2) {
         ChitauriSounds.playWide(level, s.portal, ChitauriSounds.PORTAL_CLOSE, 9.0F, 1.0F, VIEW_RANGE + 60.0);
      }
      if (t == ChitauriRules.BEAT_SEVER) {
         // the hive is cut off: every Chitauri drops at once
         ChitauriSounds.playWide(level, s.portal, ChitauriSounds.SEVER, 9.0F, 1.0F, VIEW_RANGE + 60.0);
         ChitauriNet.fxAlways(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.SEVER, s.portal, s.normal(), 1.0F, 0), s.anchor, VIEW_RANGE + 60.0);
         s.queue.clear();
         for (UUID id : new ArrayList<>(s.soldiers)) {
            if (level.getEntity(id) instanceof ChitauriSoldier soldier) {
               soldier.sever(1 + s.random.nextInt(40));
            }
         }
         for (UUID id : new ArrayList<>(s.chariots)) {
            if (level.getEntity(id) instanceof ChitauriChariot chariot && chariot.isAlive()) {
               chariot.setHealth(0.0F);
               chariot.die(level.damageSources().generic());
            }
         }
         for (UUID id : new ArrayList<>(s.leviathans)) {
            if (level.getEntity(id) instanceof ChitauriLeviathan leviathan) {
               leviathan.sever();
            }
         }
      }
   }

   /** Lightning plays on the portal rim (dense while it opens, rarer in the fight), now and then a bolt to the rooftops. */
   private static void tickStorm(ServerLevel level, Session s) {
      if (s.phase == ChitauriPhase.ENDED) {
         return;
      }
      float open = ChitauriRules.portalOpen(s.phase, s.phaseTick);
      if (open <= 0.05F || --s.nextStrike > 0) {
         return;
      }
      boolean storm = s.phase == ChitauriPhase.OPENING && s.phaseTick >= ChitauriRules.BEAT_STORM - 60;
      s.nextStrike = storm ? 6 + s.random.nextInt(14) : s.phase == ChitauriPhase.CLOSING ? 8 + s.random.nextInt(10) : 30 + s.random.nextInt(70);
      double r = ChitauriRules.PORTAL_RADIUS * open;
      Vec3[] basis = basis(s);
      double a = s.random.nextDouble() * Math.PI * 2.0;
      Vec3 rim = s.portal.add(basis[0].scale(Math.cos(a) * r)).add(basis[1].scale(Math.sin(a) * r));
      if (s.phase != ChitauriPhase.CLOSING && s.random.nextInt(storm ? 3 : 4) == 0) {
         // down to a rooftop near the anchor (visual and sound only)
         double gx = s.anchor.x + (s.random.nextDouble() - 0.5) * 60.0;
         double gz = s.anchor.z + (s.random.nextDouble() - 0.5) * 60.0;
         int gy = ChitauriTerrain.surface(level, gx, gz);
         if (gy != ChitauriTerrain.UNKNOWN) {
            Vec3 ground = new Vec3(gx, gy, gz);
            float h = (float)Math.min(HeroFx.BOLT_MAX_HEIGHT, s.portal.y - gy);
            HeroFx.electricBolt(level, ground, h, 1.2F, ChitauriSounds.PORTAL_THUNDER.get());
            return;
         }
      }
      double b = a + (s.random.nextDouble() - 0.5) * 2.4;
      Vec3 rim2 = s.portal.add(basis[0].scale(Math.cos(b) * r * 0.9)).add(basis[1].scale(Math.sin(b) * r * 0.9));
      ChitauriNet.fx(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.PORTAL_STRIKE, rim, rim2.subtract(rim), 1.0F, 0), s.portal, VIEW_RANGE + 60.0);
      if (s.random.nextInt(3) == 0) {
         ChitauriSounds.playWide(level, rim, ChitauriSounds.PORTAL_THUNDER, 5.0F, 0.9F + s.random.nextFloat() * 0.3F, VIEW_RANGE);
      }
   }

   private static Vec3[] basis(Session s) {
      Vec3 n = s.normal();
      Vec3 u = n.cross(new Vec3(0, 0, 1));
      if (u.lengthSqr() < 1.0E-4) {
         u = n.cross(new Vec3(1, 0, 0));
      }
      u = u.normalize();
      Vec3 v = n.cross(u).normalize();
      return new Vec3[]{u, v, n};
   }

   private static void enter(ServerLevel level, Session s, ChitauriPhase next) {
      if (next == ChitauriPhase.ENDED) {
         finish(level, s);
         return;
      }
      s.phase = next;
      s.phaseTick = 0;
      s.waveIndex = 0;
      s.quietTicks = 0;
      s.nextWaveIn = next.fighting() ? 40 : -1;
      if (next.fighting() || next == ChitauriPhase.CLOSING) {
         s.nextStrike = Math.min(s.nextStrike, 20);
      }
      announce(level, s, next);
      sendPortal(level, s);
      updateBars(level, s);
   }

   private static void finish(ServerLevel level, Session s) {
      Component stats = Component.translatable("viltrumitecore.chitauri.chat.ended", s.killedSoldiers, s.killedChariots, s.killedLeviathans,
         ChitauriDestruction.destroyed()).withStyle(ChatFormatting.AQUA);
      UUID best = null;
      int bestScore = 0;
      for (Map.Entry<UUID, Integer> e : s.heroKills.entrySet()) {
         if (e.getValue() > bestScore) {
            best = e.getKey();
            bestScore = e.getValue();
         }
      }
      ServerPlayer top = best != null ? s.server.getPlayerList().getPlayer(best) : null;
      for (ServerPlayer p : viewers(level, s)) {
         p.sendSystemMessage(stats);
         if (top != null) {
            p.sendSystemMessage(Component.translatable("viltrumitecore.chitauri.chat.top", top.getDisplayName(), bestScore).withStyle(ChatFormatting.GOLD));
         }
         title(p, Component.translatable("viltrumitecore.chitauri.title.ended").withStyle(ChatFormatting.AQUA),
            Component.translatable("viltrumitecore.chitauri.subtitle.ended").withStyle(ChatFormatting.GRAY));
      }
      LOG.info("Chitauri invasion over: {} soldiers, {} chariots, {} Leviathans down, {} blocks destroyed", s.killedSoldiers, s.killedChariots,
         s.killedLeviathans, ChitauriDestruction.destroyed());
      ChitauriNet.portal(level, ChitauriPortalS2CPacket.inactive());
      close(s);
   }

   // ------------------------------------------------------------------ spawning

   private static void sendWave(ServerLevel level, Session s, ChitauriRules.Wave w) {
      int t = s.clock + 4;
      int n = Math.max(w.carriers(), w.gunships());
      for (int i = 0; i < n; i++) {
         if (i < w.carriers()) {
            s.queue.add(new Pending(Kind.CARRIER, t));
            t += 9 + s.random.nextInt(12);
         }
         if (i < w.gunships()) {
            s.queue.add(new Pending(Kind.GUNSHIP, t));
            t += 9 + s.random.nextInt(12);
         }
      }
      for (int i = 0; i < w.leviathans(); i++) {
         s.queue.add(new Pending(Kind.LEVIATHAN, s.clock + 60 + i * 200));
      }
      s.waveIndex++;
      s.nextWaveIn = ChitauriRules.wave(s.phase, s.waveIndex) != null ? w.pause() : -1;
      if (s.phase == ChitauriPhase.CLIMAX) {
         s.nextWaveIn = w.pause();
      }
      Component msg = Component.translatable("viltrumitecore.chitauri.chat.wave", s.waveIndex, phaseName(s.phase)).withStyle(ChatFormatting.DARK_AQUA);
      for (ServerPlayer p : viewers(level, s)) {
         p.displayClientMessage(msg, true);
      }
   }

   private static void drain(ServerLevel level, Session s) {
      if (s.queue.isEmpty() || ChitauriRules.quietPortal(s.phase, s.phaseTick) && s.phase != ChitauriPhase.OPENING) {
         return;
      }
      int spawned = 0;
      Iterator<Pending> it = s.queue.iterator();
      while (it.hasNext() && spawned < 2) {
         Pending p = it.next();
         if (p.due > s.clock) {
            continue;
         }
         boolean ok = switch (p.kind) {
            case GUNSHIP -> spawnChariot(level, s, ChitauriChariot.KIND_GUNSHIP);
            case CARRIER -> spawnChariot(level, s, ChitauriChariot.KIND_CARRIER);
            case LEVIATHAN -> spawnLeviathan(level, s);
         };
         if (ok) {
            it.remove();
            spawned++;
         } else if (s.clock - p.born > 1200 || p.kind == Kind.LEVIATHAN && s.leviathansSent >= ChitauriRules.MAX_LEVIATHANS_TOTAL) {
            it.remove(); // the caps stayed full for a minute: this unit never comes
         } else {
            p.due = s.clock + 30;
         }
      }
   }

   /** A point on the portal disc (fraction of the open radius) and its outward direction. */
   private static Vec3[] portalPoint(Session s, double spread) {
      Vec3[] basis = basis(s);
      double a = s.random.nextDouble() * Math.PI * 2.0;
      double r = ChitauriRules.PORTAL_RADIUS * spread * Math.sqrt(s.random.nextDouble());
      Vec3 radial = basis[0].scale(Math.cos(a)).add(basis[1].scale(Math.sin(a)));
      Vec3 at = s.portal.add(radial.scale(r)).add(basis[2].scale(1.2));
      Vec3 flat = new Vec3(radial.x, 0, radial.z);
      if (flat.lengthSqr() < 1.0E-4) {
         flat = new Vec3(Math.cos(a), 0, Math.sin(a));
      }
      return new Vec3[]{at, flat.normalize()};
   }

   private static boolean spawnChariot(ServerLevel level, Session s, byte kind) {
      int riders = kind == ChitauriChariot.KIND_CARRIER ? 3 : 2;
      if (s.chariots.size() >= ChitauriRules.MAX_CHARIOTS || s.soldiers.size() + riders > ChitauriRules.MAX_SOLDIERS) {
         return false;
      }
      Vec3[] p = portalPoint(s, 0.7);
      Vec3 at = p[0];
      if (!ChitauriTerrain.ticking(level, at.x, at.y, at.z)) {
         return false;
      }
      ChitauriChariot chariot = ChitauriEntities.CHARIOT.get().create(level);
      if (chariot == null) {
         return false;
      }
      chariot.setKind(kind);
      chariot.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
      // out of the portal: down along its normal and fanning outwards, then levelling off
      Vec3 dir = s.normal().scale(0.55).add(p[1].scale(0.9)).add(0, -0.1, 0);
      chariot.launch(dir);
      if (!level.addFreshEntity(chariot)) {
         return false;
      }
      s.chariots.add(chariot.getUUID());
      int squad = s.squads++;
      for (int seat = 0; seat < riders; seat++) {
         byte role = seat == 0 ? ChitauriSoldier.ROLE_PILOT : kind == ChitauriChariot.KIND_GUNSHIP ? ChitauriSoldier.ROLE_GUNNER : ChitauriSoldier.ROLE_TROOPER;
         ChitauriSoldier soldier = spawnSoldier(level, at, role, role == ChitauriSoldier.ROLE_TROOPER ? squad : -1);
         if (soldier == null) {
            continue;
         }
         soldier.squadIndex = seat - 1;
         soldier.setSeat(seat);
         soldier.startRiding(chariot, true);
      }
      if (s.random.nextInt(2) == 0) {
         ChitauriSounds.playWide(level, at, ChitauriSounds.CHARIOT_FLYBY, 4.0F, 0.85F + s.random.nextFloat() * 0.3F, 128.0);
      }
      return true;
   }

   private static boolean spawnLeviathan(ServerLevel level, Session s) {
      if (s.leviathansSent >= ChitauriRules.MAX_LEVIATHANS_TOTAL || s.leviathans.size() >= ChitauriRules.MAX_LEVIATHANS) {
         return false;
      }
      Vec3 centre = s.portal.add(s.normal().scale(1.0));
      if (!ChitauriTerrain.ticking(level, centre.x, centre.y, centre.z)) {
         return false;
      }
      // out over the city: towards the area centre (or a random bearing), diving out of the portal at ~35 degrees
      Vec3 flat = new Vec3(s.areaCentre.x - s.anchor.x, 0, s.areaCentre.z - s.anchor.z);
      if (flat.lengthSqr() < 64.0) {
         double a = s.random.nextDouble() * Math.PI * 2.0;
         flat = new Vec3(Math.cos(a), 0, Math.sin(a));
      }
      double jitter = (s.random.nextDouble() - 0.5) * 1.2;
      flat = flat.normalize().yRot((float)jitter);
      Vec3 dir = flat.scale(0.82).add(0, -0.57, 0).normalize();
      ChitauriLeviathan leviathan = ChitauriLeviathan.emerge(level, centre, dir);
      if (leviathan == null || leviathan.isRemoved()) {
         return false;
      }
      s.leviathans.add(leviathan.getUUID());
      s.leviathansSent++;
      ServerBossEvent bar = new ServerBossEvent(Component.translatable("viltrumitecore.chitauri.bar.leviathan"), BossEvent.BossBarColor.PURPLE,
         BossEvent.BossBarOverlay.NOTCHED_6);
      bar.setDarkenScreen(true);
      s.leviathanBars.put(leviathan.getUUID(), bar);
      ChitauriSounds.playWide(level, centre, ChitauriSounds.LEVIATHAN_ROAR, 9.0F, 0.75F, VIEW_RANGE + 60.0);
      level.broadcastEntityEvent(leviathan, ChitauriLeviathan.EVENT_ROAR);
      for (ServerPlayer p : viewers(level, s)) {
         title(p, Component.translatable("viltrumitecore.chitauri.title.leviathan_arrives").withStyle(ChatFormatting.DARK_PURPLE),
            Component.translatable("viltrumitecore.chitauri.subtitle.leviathan_arrives").withStyle(ChatFormatting.GRAY));
      }
      return true;
   }

   /** Forget dead or unloaded creatures (no-save entities vanish with their chunk). */
   private static void prune(ServerLevel level, Session s) {
      pruneSet(level, s.soldiers);
      pruneSet(level, s.chariots);
      Iterator<UUID> it = s.leviathans.iterator();
      while (it.hasNext()) {
         UUID id = it.next();
         Entity e = level.getEntity(id);
         if (e == null || e.isRemoved() || !e.isAlive()) {
            it.remove();
            ServerBossEvent bar = s.leviathanBars.remove(id);
            if (bar != null) {
               bar.removeAllPlayers();
            }
         }
      }
   }

   private static void pruneSet(ServerLevel level, Set<UUID> set) {
      set.removeIf(id -> {
         Entity e = level.getEntity(id);
         return e == null || e.isRemoved() || !e.isAlive();
      });
   }

   /** Citizens flee from the fighting: danger zones around every live creature. */
   private static void markDanger(ServerLevel level, Session s) {
      if (CityManager.of(level) == null) {
         return;
      }
      for (UUID id : s.soldiers) {
         Entity e = level.getEntity(id);
         if (e != null && !e.isPassenger()) {
            ChitauriDestruction.danger(level, e.position(), 9.0F, 60);
         }
      }
      for (UUID id : s.leviathans) {
         if (level.getEntity(id) instanceof ChitauriLeviathan l) {
            Vec3 c = l.centre();
            int ground = ChitauriTerrain.surface(level, c.x, c.z);
            if (ground != ChitauriTerrain.UNKNOWN && c.y - ground < 30.0) {
               ChitauriDestruction.danger(level, new Vec3(c.x, ground, c.z), 22.0F, 60);
            }
         }
      }
   }

   // ------------------------------------------------------------------ players

   static List<ServerPlayer> viewers(ServerLevel level, Session s) {
      List<ServerPlayer> out = new ArrayList<>();
      double r2 = VIEW_RANGE * VIEW_RANGE;
      for (ServerPlayer p : level.players()) {
         double dx = p.getX() - s.anchor.x;
         double dz = p.getZ() - s.anchor.z;
         if (dx * dx + dz * dz <= r2) {
            out.add(p);
         }
      }
      return out;
   }

   static Component phaseName(ChitauriPhase phase) {
      return Component.translatable("viltrumitecore.chitauri.phase." + phase.key());
   }

   private static void title(ServerPlayer p, Component title, Component subtitle) {
      p.connection.send(new ClientboundSetTitlesAnimationPacket(12, 70, 24));
      p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
      p.connection.send(new ClientboundSetTitleTextPacket(title));
   }

   private static void announce(ServerLevel level, Session s, ChitauriPhase phase) {
      Component t = Component.translatable("viltrumitecore.chitauri.title." + phase.key()).withStyle(ChatFormatting.AQUA);
      Component sub = Component.translatable("viltrumitecore.chitauri.subtitle." + phase.key()).withStyle(ChatFormatting.GRAY);
      Component chat = Component.translatable("viltrumitecore.chitauri.chat." + phase.key()).withStyle(ChatFormatting.DARK_AQUA);
      for (ServerPlayer p : viewers(level, s)) {
         title(p, t, sub);
         p.sendSystemMessage(chat);
      }
      ChitauriSounds.playWide(level, s.anchor.add(0, 20, 0), ChitauriSounds.PHASE_HORN, 6.0F, phase == ChitauriPhase.CLOSING ? 0.8F : 1.0F, VIEW_RANGE);
   }

   private static void message(ServerLevel level, Session s, String key, ChatFormatting color) {
      Component msg = Component.translatable(key).withStyle(color);
      for (ServerPlayer p : viewers(level, s)) {
         p.sendSystemMessage(msg);
      }
   }

   private static BossEvent.BossBarColor color(ChitauriPhase phase) {
      return switch (phase) {
         case OPENING -> BossEvent.BossBarColor.BLUE;
         case GROUND_ASSAULT -> BossEvent.BossBarColor.GREEN;
         case AIR_ASSAULT -> BossEvent.BossBarColor.YELLOW;
         case LEVIATHAN -> BossEvent.BossBarColor.PURPLE;
         case CLIMAX -> BossEvent.BossBarColor.RED;
         default -> BossEvent.BossBarColor.WHITE;
      };
   }

   private static void updateBars(ServerLevel level, Session s) {
      List<ServerPlayer> viewers = viewers(level, s);
      int length = Math.max(1, ChitauriRules.length(s.phase));
      s.bar.setName(Component.translatable("viltrumitecore.chitauri.bar", phaseName(s.phase)));
      s.bar.setColor(color(s.phase));
      s.bar.setProgress(Mth.clamp(1.0F - (float)s.phaseTick / length, 0.0F, 1.0F));
      sync(s.bar, viewers);
      for (Map.Entry<UUID, ServerBossEvent> e : s.leviathanBars.entrySet()) {
         Entity entity = level.getEntity(e.getKey());
         if (!(entity instanceof ChitauriLeviathan l)) {
            continue;
         }
         e.getValue().setProgress(Mth.clamp(l.getHealth() / l.getMaxHealth(), 0.0F, 1.0F));
         List<ServerPlayer> near = new ArrayList<>();
         Vec3 c = l.centre();
         for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(c) <= LEVIATHAN_BAR_RANGE * LEVIATHAN_BAR_RANGE) {
               near.add(p);
            }
         }
         sync(e.getValue(), near);
      }
   }

   private static void sync(ServerBossEvent bar, List<ServerPlayer> want) {
      Set<ServerPlayer> keep = new HashSet<>(want);
      for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
         if (!keep.contains(p)) {
            bar.removePlayer(p);
         }
      }
      for (ServerPlayer p : want) {
         if (!bar.getPlayers().contains(p)) {
            bar.addPlayer(p);
         }
      }
   }

   static ChitauriPortalS2CPacket portalPacket(Session s) {
      return new ChitauriPortalS2CPacket(true, s.portal.x, s.portal.y, s.portal.z, ChitauriRules.PORTAL_RADIUS, s.tiltYaw, s.anchor.x, s.anchor.y,
         s.anchor.z, s.phase.ordinal(), s.phaseTick, ChitauriRules.length(s.phase), s.seed);
   }

   private static void sendPortal(ServerLevel level, Session s) {
      ChitauriNet.portal(level, portalPacket(s));
   }

   private static void sendPortalTo(ServerPlayer player) {
      Session s = session;
      if (s != null && s.in(player.level()) && s.phase != ChitauriPhase.ENDED) {
         CoreMessages.sendToPlayer(portalPacket(s), player);
      } else {
         CoreMessages.sendToPlayer(ChitauriPortalS2CPacket.inactive(), player);
      }
   }

   @SubscribeEvent
   public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         sendPortalTo(player);
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
      Session s = session;
      if (s != null && event.getEntity() instanceof ServerPlayer player) {
         s.bar.removePlayer(player);
         for (ServerBossEvent bar : s.leviathanBars.values()) {
            bar.removePlayer(player);
         }
      }
   }

   @SubscribeEvent
   public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         sendPortalTo(player);
      }
   }

   @SubscribeEvent
   public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         sendPortalTo(player);
      }
   }

   @SubscribeEvent
   public static void onLevelUnload(LevelEvent.Unload event) {
      Session s = session;
      if (s != null && event.getLevel() instanceof ServerLevel level && s.in(level)) {
         close(s);
      }
   }

   @SubscribeEvent
   public static void onServerStopping(ServerStoppingEvent event) {
      Session s = session;
      if (s != null) {
         close(s);
      }
      session = null;
   }
}
