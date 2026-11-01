package dev.baranhan.viltrumitecore.worldevent.ultron;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.profile.CityProfile;
import dev.baranhan.viltrumitecore.entity.SummonOwners;
import dev.baranhan.viltrumitecore.entity.ultron.UltronDrone;
import dev.baranhan.viltrumitecore.entity.ultron.UltronEngineer;
import dev.baranhan.viltrumitecore.entity.ultron.UltronMob;
import dev.baranhan.viltrumitecore.entity.ultron.UltronNodeEntity;
import dev.baranhan.viltrumitecore.entity.ultron.UltronPrime;
import dev.baranhan.viltrumitecore.entity.ultron.UltronUnit;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriTerrain;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronNet;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ULTRON — ВОССТАНИЕ МАШИН (docs/plans/2026-11-01-ultron-uprising.md). Manual world event: one at a
 * time, started by {@code /ultron start}. This class holds the running session, the lifecycle (start,
 * restore after a restart, the endings, the emergency stop) and the hooks the machines, the nodes and
 * Ultron Prime call; {@link UltronDirector} runs the tick. Never loads a chunk.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class UltronUprising {
   static final Logger LOG = LoggerFactory.getLogger("viltrumitecore/ultron");
   @Nullable
   /** Lang key naming the uprising in the other events' "already running" refusals. */
   public static final String CONFLICT_KEY = "viltrumitecore.ultron.conflict";
   private static UltronSession session;

   private UltronUprising() {
   }

   @Nullable
   public static UltronSession current() {
      return session;
   }

   public static boolean running(@Nullable MinecraftServer server) {
      UltronSession s = session;
      return s != null && server != null && s.server == server;
   }

   @Nullable
   static UltronSession in(Level level) {
      UltronSession s = session;
      return s != null && !level.isClientSide && s.in(level) ? s : null;
   }

   // ------------------------------------------------------------------ hooks for the machines

   /** Do the machines fight on sight here? Outside an event (a test spawn) always. */
   public static boolean hostile(Level level) {
      UltronSession s = in(level);
      return s == null || s.outcome == 0 && s.phase.hostile();
   }

   /**
    * Past the event's leash ring (area radius × {@link UltronDirector#LEASH} around the anchor): machines do not
    * chase or pick targets out there, so a fleeing (or knocked-away) player does not drag them across the world.
    * False without an uprising in this level.
    */
   public static boolean beyondLeash(Level level, double x, double z) {
      UltronSession s = in(level);
      if (s == null) {
         return false;
      }
      return UltronRules.outside(s.anchor.x, s.anchor.z, s.radius * UltronDirector.LEASH, x, z);
   }

   /** A machine saw a player (real line of sight): the network knows where he is. */
   public static void sighted(LivingEntity seer, Player player) {
      UltronSession s = in(seer.level());
      if (s != null && s.outcome == 0) {
         s.intel.saw(player.getUUID(), player.getX(), player.getY(), player.getZ(), s.clock, airborne(player));
      }
   }

   /** A machine was hit: threat and, inside the network's coverage, what the hit teaches Ultron. */
   public static void unitHurt(UltronUnit unit, DamageSource source, float dealt, boolean ranged) {
      UltronSession s = in(unit.level());
      if (s == null || s.outcome != 0) {
         return;
      }
      Player player = playerBehind(source.getEntity());
      if (player == null) {
         return;
      }
      s.intel.hurtBy(player.getUUID(), dealt);
      if (unit.sees(player)) {
         s.intel.saw(player.getUUID(), player.getX(), player.getY(), player.getZ(), s.clock, airborne(player));
      }
      if (!s.network.covered(unit.getX(), unit.getY(), unit.getZ())) {
         return; // out of the network's telemetry: nothing learned
      }
      if (airborne(player)) {
         s.adaptation.observe(UltronAdaptation.Tactic.AERIAL, dealt * 0.5F);
      }
      s.adaptation.observe(ranged ? UltronAdaptation.Tactic.RANGED : UltronAdaptation.Tactic.MELEE, dealt * 0.4F);
   }

   /** A machine was destroyed: losses per sector, the killer's threat, drone salvage (reveals a node). */
   public static void unitKilled(UltronUnit unit, DamageSource source) {
      if (unit instanceof UltronPrime prime) {
         bossKilled(prime, source);
         return;
      }
      UltronSession s = in(unit.level());
      if (s == null || !(unit.level() instanceof ServerLevel level)) {
         return;
      }
      s.units.remove(unit.getUUID());
      if (s.outcome != 0) {
         return;
      }
      UltronUnitKind kind = unit.kind();
      s.killed[kind.ordinal()]++;
      int sector = UltronIntel.sector(s.anchor.x, s.anchor.z, unit.getX(), unit.getZ());
      if (s.intel.focus() == sector) {
         s.adaptation.observe(UltronAdaptation.Tactic.SECTOR_FOCUS, 3.0F);
      }
      s.intel.lost(sector);
      s.recentLosses += 1.0F;
      Player player = playerBehind(source.getEntity());
      if (player != null) {
         s.intel.killedBy(player.getUUID(), switch (kind) {
            case DRONE -> 2.0F;
            case SENTRY -> 4.0F;
            case BRUTE -> 10.0F;
            case INTERCEPTOR -> 5.0F;
            case ENGINEER -> 3.0F;
         });
         if (kind == UltronUnitKind.DRONE) {
            UltronNetwork.Node n = s.network.nearestUnknown(unit.getX(), unit.getY(), unit.getZ(), UltronRules.SALVAGE_RANGE);
            if (n != null) {
               UltronDirector.discover(level, s, n, player instanceof ServerPlayer sp ? sp : null);
            }
         }
      }
      if (s.recentLosses >= 8.0F) {
         UltronVoice.say(level, s, "machines_lost", false);
      }
      s.dirty = true;
   }

   /** Damage factor for a machine near an active bulwark. */
   public static float bulwarkFactor(LivingEntity e) {
      UltronSession s = in(e.level());
      if (s == null) {
         return 1.0F;
      }
      double r2 = UltronRules.BULWARK_AURA * UltronRules.BULWARK_AURA;
      for (UltronNetwork.Node n : s.network.all()) {
         if (n.kind == UltronNodeKind.BULWARK && n.active() && n.distanceSq(e.getX(), e.getY(), e.getZ()) <= r2) {
            return 1.0F - UltronRules.BULWARK_REDUCTION;
         }
      }
      return 1.0F;
   }

   /** Machine damage scale: mild group scaling (players in the event's range). 1 outside an event. */
   public static float unitDamageScale(Level level) {
      UltronSession s = in(level);
      if (s == null || !(level instanceof ServerLevel server)) {
         return 1.0F;
      }
      int players = UltronDirector.fighters(server, s).size();
      return 1.0F + 0.05F * (Mth.clamp(players, 1, 6) - 1);
   }

   /** Engineers: a node by id (null outside an event). */
   @Nullable
   public static UltronNetwork.Node node(Level level, int id) {
      UltronSession s = in(level);
      return s == null ? null : s.network.get(id);
   }

   /** Engineer welding at a node this tick (build, reboot, repair). False when there is nothing left to do. */
   public static boolean weld(UltronEngineer engineer, int nodeId) {
      UltronSession s = in(engineer.level());
      if (s == null || s.outcome != 0 || !(engineer.level() instanceof ServerLevel level)) {
         return false;
      }
      UltronNetwork.Node n = s.network.get(nodeId);
      return n != null && UltronDirector.weld(level, s, n, engineer);
   }

   /** Engineer plants a new node on its planned site (contact of the plant move). */
   public static boolean plant(UltronEngineer engineer, int nodeId) {
      UltronSession s = in(engineer.level());
      if (s == null || s.outcome != 0 || !(engineer.level() instanceof ServerLevel level)) {
         return false;
      }
      UltronNetwork.Node n = s.network.get(nodeId);
      return n != null && UltronDirector.plant(level, s, n);
   }

   /** Engineer hack on a golem / Legion suit (contact of the hack move). */
   public static boolean engineerHack(UltronEngineer engineer, LivingEntity target) {
      UltronSession s = in(engineer.level());
      if (s == null || s.outcome != 0 || !(engineer.level() instanceof ServerLevel level)) {
         return false;
      }
      UltronNetwork.Node holder = s.capture.holder(s, target.position());
      return holder != null && s.capture.take(level, s, target, holder.id, engineer.position().add(0.0, 0.8, 0.0));
   }

   /** Can an engineer take this over (a free golem or suit within a controller's hold)? */
   public static boolean capturable(Level level, LivingEntity target) {
      UltronSession s = in(level);
      return s != null && s.capture.capturable(target) && s.capture.roomFor(target) && s.capture.holder(s, target.position()) != null;
   }

   /** Drone scan sweep: players within the sweep become known (no line of sight needed: it is a sensor sweep). */
   public static void scan(UltronDrone drone, double range) {
      UltronSession s = in(drone.level());
      if (s == null) {
         return;
      }
      for (Player p : drone.level().players()) {
         if (!p.isSpectator() && !p.isCreative() && p.distanceToSqr(drone) <= range * range) {
            s.intel.saw(p.getUUID(), p.getX(), p.getY(), p.getZ(), s.clock, airborne(p));
         }
      }
   }

   /** Drone designator: the target glows, its threat rises, nearby assault machines go for it. */
   public static void mark(UltronDrone drone, LivingEntity target) {
      target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, false, false, true), drone);
      UltronSession s = in(drone.level());
      if (s == null) {
         return;
      }
      if (target instanceof Player p) {
         s.intel.saw(p.getUUID(), p.getX(), p.getY(), p.getZ(), s.clock, airborne(p));
         s.intel.hurtBy(p.getUUID(), 5.0F);
      }
      for (UltronUnit u : s.units.values()) {
         if (u != drone && u.isAlive() && u.distanceToSqr(target) < 40.0 * 40.0 && u.getTarget() == null) {
            u.hunt = target.getUUID();
            u.goal = target.position();
         }
      }
   }

   // ------------------------------------------------------------------ hooks for the nodes

   /** A hit on a node body: shield, then hull, capped (UltronNetwork.damage). */
   public static boolean damageNode(UltronNodeEntity body, DamageSource source, float amount) {
      UltronSession s = in(body.level());
      if (s == null || s.outcome != 0 || !(body.level() instanceof ServerLevel level)) {
         return false;
      }
      UltronNetwork.Node n = s.network.get(body.nodeId());
      if (n == null || !n.standing()) {
         return false;
      }
      if (UltronMob.side(source.getEntity()) || UltronMob.side(source.getDirectEntity())) {
         return false;
      }
      if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         amount = n.hull + n.shield + 1.0F; // /kill: the node goes (operator)
         n.secondDamage = 0.0F;
         n.hull = Math.min(n.hull, UltronRules.nodeHitCap(n.kind));
         n.shield = 0.0F;
      }
      return UltronDirector.damageNode(level, s, n, body, source, amount);
   }

   /** Sneak + use at a node core: one step of a hack (the interaction repeats while the button is held). */
   public static boolean hackNode(Player player, UltronNodeEntity body) {
      UltronSession s = in(body.level());
      if (s == null || s.outcome != 0 || !(body.level() instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) {
         return false;
      }
      UltronNetwork.Node n = s.network.get(body.nodeId());
      return n != null && UltronDirector.hack(level, s, n, body, sp);
   }

   // ------------------------------------------------------------------ hooks for Ultron Prime

   /** What the live nodes give him now (none outside an event). */
   public static UltronBossRules.Tethers tethers(Level level) {
      UltronSession s = in(level);
      return s == null ? UltronBossRules.Tethers.NONE : UltronDirector.tethers(s);
   }

   /** The boss's fighting style of this scenario. */
   public static UltronScenario.BossStyle bossStyle(Level level) {
      UltronSession s = in(level);
      return s == null ? UltronScenario.BossStyle.TACTICIAN : s.scenario.boss();
   }

   /** Heroes counted for his health (players in range). */
   public static int heroes(Level level) {
      UltronSession s = in(level);
      if (s == null || !(level instanceof ServerLevel server)) {
         return 1;
      }
      return Math.max(1, UltronDirector.fighters(server, s).size());
   }

   /** He changed stage (aerial, overdrive): lines, cues, saved. */
   public static void bossStage(UltronPrime prime, UltronBossRules.Stage stage) {
      UltronSession s = in(prime.level());
      if (s == null || !(prime.level() instanceof ServerLevel level)) {
         return;
      }
      s.bossStage = stage.ordinal();
      s.saveDirty = true;
      s.dirty = true;
      switch (stage) {
         case TACTICAL -> {
            UltronVoice.say(level, s, "prime_assembled", true);
            UltronDirector.startUpload(level, s);
         }
         case AERIAL -> UltronVoice.say(level, s, "prime_aerial", true);
         case OVERDRIVE -> UltronVoice.say(level, s, "prime_overdrive", true);
         default -> {
         }
      }
   }

   /** He calls help from the live foundries (summon move contact). */
   public static int bossSummon(UltronPrime prime) {
      UltronSession s = in(prime.level());
      if (s == null || !(prime.level() instanceof ServerLevel level)) {
         return 0;
      }
      UltronVoice.say(level, s, "prime_summon", false);
      return UltronDirector.summon(level, s, prime);
   }

   /** A player's hit mostly hit the tethers: he mocks it (the line keeps its own cooldown). */
   public static void bossTethered(UltronPrime prime) {
      UltronSession s = in(prime.level());
      if (s != null && prime.level() instanceof ServerLevel level) {
         UltronVoice.say(level, s, "prime_tether", false);
      }
   }

   /** He staggered under a burst of damage: a dry remark (ambient, never over a beat). */
   public static void bossStaggered(UltronPrime prime) {
      UltronSession s = in(prime.level());
      if (s != null && prime.level() instanceof ServerLevel level) {
         UltronVoice.say(level, s, "prime_hurt", false);
      }
   }

   /** He drains the nearest live node for health (absorb move contact): the node takes the cost. */
   public static float bossAbsorb(UltronPrime prime) {
      UltronSession s = in(prime.level());
      if (s == null || !(prime.level() instanceof ServerLevel level)) {
         return 0.0F;
      }
      return UltronDirector.absorb(level, s, prime);
   }

   /** The live nodes that tether him now (positions for the beams), all of them in the finale. */
   public static java.util.List<UltronNetwork.Node> tetherNodes(Level level) {
      UltronSession s = in(level);
      if (s == null) {
         return java.util.List.of();
      }
      java.util.List<UltronNetwork.Node> out = new java.util.ArrayList<>();
      for (UltronNetwork.Node n : s.network.all()) {
         if (n.active()) {
            out.add(n);
         }
      }
      return out;
   }

   static void bossKilled(UltronPrime prime, DamageSource source) {
      UltronSession s = in(prime.level());
      if (s == null || !(prime.level() instanceof ServerLevel level) || s.outcome != 0) {
         return;
      }
      LOG.info("Ultron Prime destroyed: victory (seed {})", s.seed);
      UltronDirector.end(level, s, true);
   }

   /** Health share he keeps over a restart (the director saves it). */
   public static void bossHealth(UltronPrime prime, float share) {
      UltronSession s = in(prime.level());
      if (s != null) {
         s.bossShare = Mth.clamp(share, 0.0F, 1.0F);
      }
   }

   // ------------------------------------------------------------------ lifecycle

   /** Starts an uprising around {@code at} with this seed. False when one runs. */
   public static boolean start(ServerLevel level, Vec3 at, long seed) {
      if (session != null) {
         return false;
      }
      Vec3 centre = at;
      double radius = UltronRules.AREA_RADIUS;
      boolean inCity = false;
      CityManager city = CityManager.of(level);
      if (city != null) {
         CityProfile p = city.profile();
         if (p.inArena(Mth.floor(at.x), Mth.clamp(Mth.floor(at.y), p.minY, p.maxY), Mth.floor(at.z))) {
            inCity = true;
            double halfX = (p.maxX - p.minX) / 2.0;
            double halfZ = (p.maxZ - p.minZ) / 2.0;
            radius = Mth.clamp(Math.min(halfX, halfZ) * 0.85, UltronRules.MIN_AREA_RADIUS, UltronRules.MAX_AREA_RADIUS);
            if (halfX <= UltronRules.MAX_AREA_RADIUS && halfZ <= UltronRules.MAX_AREA_RADIUS) {
               centre = new Vec3(p.minX + halfX, at.y, p.minZ + halfZ);
            }
         }
      }
      int ground = ChitauriTerrain.surface(level, centre.x, centre.z);
      centre = new Vec3(centre.x, ground == ChitauriTerrain.UNKNOWN ? at.y : ground, centre.z);
      UltronSession s = new UltronSession(level.getServer(), level.dimension(), seed, centre, radius, inCity);
      for (UltronScenario.Site site : s.scenario.sites()) {
         double x = centre.x + Math.cos(site.angle()) * site.radius() * radius;
         double z = centre.z + Math.sin(site.angle()) * site.radius() * radius;
         s.network.add(site.kind(), site.index(), x, centre.y, z);
      }
      UltronNetwork.Node core = s.network.core();
      if (core != null) {
         core.state = UltronNodeState.BUILDING;
         core.build = 0.0F;
         core.hull = UltronRules.hull(core.kind) * 0.4F;
         core.shield = 0.0F;
      }
      s.network.relink();
      s.network.repower();
      session = s;
      LOG.info("Ultron uprising started at {} {} {} in {} (seed {}, variant {}, layout {}, radius {}, city {})", Mth.floor(centre.x), Mth.floor(centre.y),
         Mth.floor(centre.z), level.dimension().location(), seed, s.scenario.variant().key(), s.scenario.layout(), Math.round(radius), inCity);
      UltronDirector.begin(level, s);
      UltronSavedData.get(level.getServer()).put(s.save());
      return true;
   }

   /** Emergency stop: everything goes at once (no cinematic), every machine he took is handed back. */
   public static boolean stop(MinecraftServer server) {
      UltronSession s = session;
      if (s == null || s.server != server) {
         return false;
      }
      ServerLevel level = server.getLevel(s.dimension);
      if (level != null) {
         UltronDirector.cleanup(level, s);
      }
      close(s);
      LOG.info("Ultron uprising stopped");
      return true;
   }

   /** Jumps to a phase (testing, staging). */
   public static boolean jump(MinecraftServer server, UltronPhase phase) {
      UltronSession s = session;
      if (s == null || s.server != server || phase == UltronPhase.ENDED || s.outcome != 0) {
         return false;
      }
      ServerLevel level = server.getLevel(s.dimension);
      if (level == null) {
         return false;
      }
      UltronDirector.stage(level, s, phase);
      UltronDirector.enter(level, s, phase);
      return true;
   }

   /** Ends with a victory or a defeat (operator), through the normal ending sequence. */
   public static boolean finish(MinecraftServer server, boolean victory) {
      UltronSession s = session;
      if (s == null || s.server != server || s.outcome != 0) {
         return false;
      }
      ServerLevel level = server.getLevel(s.dimension);
      if (level == null) {
         return false;
      }
      UltronDirector.end(level, s, victory);
      return true;
   }

   /** Called by the director once the clean-up ran: the session is gone. */
   static void close(UltronSession s) {
      s.bar.removeAllPlayers();
      if (s.bossBar != null) {
         s.bossBar.removeAllPlayers();
      }
      s.queue.clear();
      s.units.clear();
      s.hacks.clear();
      s.phase = UltronPhase.ENDED;
      if (session == s) {
         session = null;
      }
      UltronSavedData.get(s.server).put(null);
   }

   // ------------------------------------------------------------------ helpers

   /** The player behind a hit: himself, his summon's owner, or a projectile's owner chain. */
   @Nullable
   static Player playerBehind(@Nullable Entity attacker) {
      if (attacker instanceof Player p) {
         return p.isCreative() || p.isSpectator() ? null : p;
      }
      return SummonOwners.ownerOf(attacker);
   }

   /** Flying or high above the ground (jetpack, hero flight, elytra). */
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
      UltronSession s = session;
      if (event.phase != TickEvent.Phase.END || s == null) {
         return;
      }
      MinecraftServer server = event.getServer();
      if (s.server != server) {
         session = null;
         return;
      }
      UltronDirector.tick(server, s);
   }

   @SubscribeEvent
   public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         UltronHud.sendStateTo(player);
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
      UltronSession s = session;
      if (s != null) {
         s.hacks.remove(event.getEntity().getUUID());
      }
   }

   @SubscribeEvent
   public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         UltronHud.sendStateTo(player);
      }
   }

   @SubscribeEvent
   public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         UltronHud.sendStateTo(player);
      }
   }

   /** A hacker hit loses progress. */
   @SubscribeEvent(priority = EventPriority.LOW)
   public static void onHurt(LivingHurtEvent event) {
      UltronSession s = session;
      if (s == null || s.hacks.isEmpty() || !(event.getEntity() instanceof ServerPlayer player)) {
         return;
      }
      UltronSession.Hack h = s.hacks.get(player.getUUID());
      if (h != null && event.getAmount() > 0.5F) {
         h.progress = Math.max(0, h.progress - UltronRules.HACK_HIT_LOSS);
      }
   }

   /** A captive golem beaten down is freed instead of dying. */
   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onDeath(LivingDeathEvent event) {
      UltronSession s = session;
      if (s == null || !(event.getEntity() instanceof IronGolem golem) || !(golem.level() instanceof ServerLevel level) || !s.in(level)) {
         return;
      }
      if (s.capture.saveGolem(level, s, golem)) {
         event.setCanceled(true);
      }
   }

   /** A captive golem never turns on the machines (its own target goals would pick them as monsters). */
   @SubscribeEvent
   public static void onTarget(LivingChangeTargetEvent event) {
      UltronSession s = session;
      if (s == null || s.capture.captives.isEmpty() || !(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) {
         return;
      }
      if (s.capture.captives.containsKey(mob.getUUID()) && UltronMob.side(event.getNewTarget())) {
         event.setNewTarget(null);
      }
   }

   @SubscribeEvent
   public static void onServerStarted(ServerStartedEvent event) {
      MinecraftServer server = event.getServer();
      UltronSavedData data = UltronSavedData.get(server);
      if (data.tag() == null || session != null) {
         return;
      }
      UltronSession s = UltronSession.load(server, data.tag());
      if (s == null || s.outcome != 0) {
         data.put(null);
         return;
      }
      session = s;
      LOG.info("Ultron uprising restored (seed {}, phase {}, {} nodes standing, reserve {})", s.seed, s.phase.key(), s.network.standing(),
         java.util.Arrays.toString(s.reserve));
   }

   @SubscribeEvent
   public static void onServerStopping(ServerStoppingEvent event) {
      UltronSession s = session;
      if (s == null) {
         return;
      }
      ServerLevel level = event.getServer().getLevel(s.dimension);
      if (level != null) {
         s.capture.releaseAll(level, s);
         UltronSavedData.get(event.getServer()).put(s.save());
      }
      s.bar.removeAllPlayers();
      if (s.bossBar != null) {
         s.bossBar.removeAllPlayers();
      }
      session = null;
   }

   /** Players in the event's range hear this (wide one-shot). */
   static void cue(ServerLevel level, UltronSession s, net.minecraftforge.registries.RegistryObject<net.minecraft.sounds.SoundEvent> sound, float volume,
      float pitch) {
      for (ServerPlayer p : UltronDirector.viewers(level, s)) {
         p.playNotifySound(sound.get(), SoundSource.HOSTILE, volume, pitch);
      }
   }

   static void fx(ServerLevel level, int kind, Vec3 at, Vec3 d, float power, int extra, boolean always) {
      UltronFxS2CPacket packet = new UltronFxS2CPacket(kind, at, d, power, extra);
      if (always) {
         UltronNet.fxAlways(level, packet, at, 160.0);
      } else {
         UltronNet.fx(level, packet, at, 96.0);
      }
   }
}
