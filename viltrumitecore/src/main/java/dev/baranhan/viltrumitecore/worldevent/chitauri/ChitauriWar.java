package dev.baranhan.viltrumitecore.worldevent.chitauri;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.citizen.CitizenCaptivity;
import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.city.profile.CityProfile;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriBeacon;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriCaptor;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriChariot;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriCommander;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriEntities;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriHeavy;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriInterceptor;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriMob;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriSiege;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriSoldier;
import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import dev.baranhan.viltrumitecore.worldevent.chitauri.war.SectorPlanner;
import dev.baranhan.viltrumitecore.worldevent.chitauri.war.WarDirector;
import dev.baranhan.viltrumitecore.worldevent.chitauri.war.WarOrder;
import dev.baranhan.viltrumitecore.worldevent.chitauri.war.WarRules;
import dev.baranhan.viltrumitecore.worldevent.chitauri.war.WarSector;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * The city war of one invasion (docs/plans/2026-11-04-chitauri-evolution.md). It splits the area into sectors (Atheria
 * districts, else rings), tracks who is where from loaded chunks only, gives squads their orders (scout, drop, seize,
 * patrol, reinforce, encircle, defend, retreat and regroup), plants stronghold and containment pylons, runs the seeded
 * adaptive {@link WarDirector} every {@link WarRules#DIRECTOR_PERIOD} ticks and buys the new units with a capped budget.
 * Players hear about it through the subtitle system (hive and commander lines), never the chat. Memory only, except the
 * director's run history ({@link History}), which makes the next invasions avoid what failed before.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class ChitauriWar {
   static final String VOICE = "chitauri";
   static final String HIVE = "subtitle.viltrumitecore.speaker.chitauri_hive";
   static final String COMMANDER = "subtitle.viltrumitecore.speaker.chitauri_commander";
   static final String LINE = "subtitle.viltrumitecore.chitauri.war.";
   static final String SECTOR = "subtitle.viltrumitecore.chitauri.sector.";
   static final char SEP = '\u001F';
   private static final String[] DIRS = {"e", "se", "s", "sw", "w", "nw", "n", "ne"};

   final ChitauriInvasion.Session s;
   final List<WarSector> sectors;
   final boolean districts;
   final WarDirector.Personality personality;
   final SplittableRandom roll;
   final float[] bias;
   @Nullable
   WarDirector.Doctrine doctrine;
   int repeats;
   int planSerial;
   List<Integer> targets = List.of();
   int nextDecision = 200;
   int spent;
   int lastCall = -10000;
   int heroDamage;
   int losses;
   int kills;
   int lastLiberated;
   int lastLosses;
   int citizensSeen;
   /** Hero-samples since the last decision (one per player per sector update) and how many were airborne. */
   int airSamples;
   int airHits;
   final ArrayDeque<Integer> killTicks = new ArrayDeque<>();
   final EnumMap<WarRules.UnitKind, Set<UUID>> elites = new EnumMap<>(WarRules.UnitKind.class);
   final EnumMap<WarRules.UnitKind, Integer> bought = new EnumMap<>(WarRules.UnitKind.class);
   final Map<UUID, Integer> beacons = new LinkedHashMap<>();
   final Set<UUID> containments = new LinkedHashSet<>();
   final Map<Integer, Squad> squads = new HashMap<>();
   final Map<String, Integer> lastSaid = new HashMap<>();
   final List<String> doctrineLog = new ArrayList<>();
   int lastLine = -1000;
   int citizenCursor;
   int scoutedSaid;
   int maxHeld;

   /** One squad as the war sees it (members are soldiers that left their chariot). */
   static final class Squad {
      final int id;
      final Set<UUID> members = new LinkedHashSet<>();
      int initial;
      @Nullable
      WarOrder order;
      int serial = -1;
      int retreatUntil = -1;
      boolean retreated;
      double cx;
      double cz;
      int alive;

      Squad(int id) {
         this.id = id;
      }
   }

   ChitauriWar(ChitauriInvasion.Session s, ServerLevel level, History history) {
      this.s = s;
      List<WarSector> planned = null;
      CityManager city = CityManager.of(level);
      if (city != null && s.inCity) {
         CityProfile p = city.profile();
         List<SectorPlanner.District> ds = new ArrayList<>();
         for (CityProfile.District d : p.districts) {
            ds.add(new SectorPlanner.District(d.name(), d.minX(), d.minZ(), d.maxX(), d.maxZ()));
         }
         planned = SectorPlanner.districts(ds, s.anchor.x, s.anchor.z, s.areaRadius + 40.0);
      }
      if (planned == null) {
         planned = SectorPlanner.rings(s.anchor.x, s.anchor.z, s.areaRadius + 40.0);
      }
      this.sectors = planned;
      this.districts = planned.stream().anyMatch(w -> w.name != null);
      this.personality = WarDirector.personality(s.seed);
      this.roll = new SplittableRandom(s.seed * 31L + 7L);
      this.bias = history.bias();
      for (WarRules.UnitKind k : WarRules.UnitKind.values()) {
         this.elites.put(k, new LinkedHashSet<>());
         this.bought.put(k, 0);
      }
      ChitauriInvasion.LOG.info("Chitauri war seed={} personality={} sectors={} ({}) bias={}", s.seed, this.personality, this.sectors.size(),
         this.districts ? "districts" : "rings", java.util.Arrays.toString(this.bias));
   }

   @Nullable
   public static ChitauriWar of(ServerLevel level) {
      ChitauriInvasion.Session s = ChitauriInvasion.session();
      return s != null && s.in(level) ? s.war : null;
   }

   public boolean fighting() {
      return this.s.phase.fighting();
   }

   int budgetLeft(ServerLevel level) {
      return WarRules.budget(ChitauriCaptives.ledger(level).delivered) - this.spent;
   }

   // ------------------------------------------------------------------ tick

   void tick(ServerLevel level) {
      int clock = this.s.clock;
      if (clock % WarRules.SECTOR_PERIOD == 0) {
         this.presence(level);
      }
      if (!this.fighting()) {
         return;
      }
      if (clock % 20 == 5) {
         this.squads(level);
      }
      if (clock % 100 == 50) {
         this.vehicles(level);
      }
      if (clock >= this.nextDecision) {
         this.nextDecision = clock + WarRules.DIRECTOR_PERIOD;
         this.decide(level);
      }
      if (clock % 20 == 10) {
         this.siegeWarnings(level);
      }
   }

   // ------------------------------------------------------------------ sectors

   private void presence(ServerLevel level) {
      int n = this.sectors.size();
      int[] chit = new int[n];
      int[] heroes = new int[n];
      for (UUID id : this.s.soldiers) {
         Entity e = level.getEntity(id);
         if (e != null && !e.isPassenger()) {
            int k = SectorPlanner.locate(this.sectors, e.getX(), e.getZ());
            if (k >= 0) {
               chit[k]++;
            }
         }
      }
      for (UUID id : this.beacons.keySet()) {
         Entity e = level.getEntity(id);
         if (e != null) {
            int k = SectorPlanner.locate(this.sectors, e.getX(), e.getZ());
            if (k >= 0) {
               chit[k]++;
            }
         }
      }
      for (ServerPlayer p : level.players()) {
         if (!p.isSpectator() && p.isAlive()) {
            int k = SectorPlanner.locate(this.sectors, p.getX(), p.getZ());
            if (k >= 0) {
               heroes[k]++;
               this.airSamples++;
               if (airborne(level, p)) {
                  this.airHits++;
               }
            }
         }
      }
      // citizens: a few sectors per update, loaded chunks only (entity queries never load anything)
      for (int i = 0; i < Math.min(6, n); i++) {
         WarSector w = this.sectors.get(this.citizenCursor++ % n);
         if (!w.known()) {
            continue;
         }
         AABB box = new AABB(w.x - w.radius, w.groundY - 16, w.z - w.radius, w.x + w.radius, w.groundY + 32, w.z + w.radius);
         w.citizens = level.getEntitiesOfClass(LivingEntity.class, box, e -> (e instanceof CitizenEntity || e instanceof AbstractVillager)
            && e.isAlive() && !CitizenCaptivity.isHeld(e)).size();
      }
      int seen = 0;
      for (WarSector w : this.sectors) {
         if (!w.known()) {
            int y = ChitauriTerrain.surface(level, w.x, w.z);
            if (y != ChitauriTerrain.UNKNOWN) {
               w.groundY = y;
            }
         }
         seen += w.citizens;
         WarSector.Event ev = w.update(chit[w.id], heroes[w.id], w.citizens);
         if (ev != WarSector.Event.NONE) {
            this.onSectorEvent(level, w, ev);
         }
      }
      this.citizensSeen = seen;
   }

   private void onSectorEvent(ServerLevel level, WarSector w, WarSector.Event ev) {
      ChitauriInvasion.LOG.info("Chitauri war t={} sector {} {} -> {}", this.s.clock, w.id, label(w), ev);
      switch (ev) {
         case SCOUTED -> {
            if (this.scoutedSaid++ < 2) {
               this.say(level, "scouted", null, HeroLine.Priority.AMBIENT, 200, sectorArg(w));
            }
         }
         case CONTESTED -> this.say(level, "contested", null, HeroLine.Priority.AMBIENT, 300, sectorArg(w));
         case SEIZED -> {
            this.say(level, "seized", null, HeroLine.Priority.ABILITY, 60, sectorArg(w));
            ChitauriSounds.playWide(level, new Vec3(w.x, w.groundY + 4, w.z), ChitauriSounds.SECTOR_SEIZED, 3.0F, 1.0F, ChitauriInvasion.VIEW_RANGE);
            for (Squad q : this.squads.values()) {
               q.serial = -1; // new front line: everyone re-plans
            }
         }
         case LIBERATED -> {
            this.say(level, "liberated", null, HeroLine.Priority.ABILITY, 60, sectorArg(w));
            ChitauriSounds.playWide(level, new Vec3(w.x, w.groundY + 4, w.z), ChitauriSounds.SECTOR_LIBERATED, 3.0F, 1.0F, ChitauriInvasion.VIEW_RANGE);
            for (Squad q : this.squads.values()) {
               q.serial = -1;
            }
         }
         default -> {
         }
      }
   }

   /** The fragment argument naming a sector: the district's own name, or a translatable bearing from the portal. */
   String sectorArg(WarSector w) {
      if (w.name != null) {
         return w.name;
      }
      if (w.ring == 0) {
         return SECTOR + "centre";
      }
      double a = Math.atan2(w.z - this.s.anchor.z, w.x - this.s.anchor.x);
      int idx = Math.floorMod((int)Math.round(a / (Math.PI / 4.0)), 8);
      return SECTOR + DIRS[idx] + (w.ring >= 3 ? "_far" : "");
   }

   String label(WarSector w) {
      return w.name != null ? w.name : this.sectorArg(w).substring(SECTOR.length());
   }

   @Nullable
   WarSector sectorAt(Vec3 p) {
      int k = SectorPlanner.locate(this.sectors, p.x, p.z);
      return k >= 0 ? this.sectors.get(k) : null;
   }

   /** A ground point in a sector (its centre, or a random street point for patrols), or null while it is not loaded. */
   @Nullable
   Vec3 groundIn(ServerLevel level, WarSector w, double spread) {
      for (int i = 0; i < 4; i++) {
         double a = this.s.random.nextDouble() * Math.PI * 2.0;
         double r = spread * w.radius * Math.sqrt(this.s.random.nextDouble());
         double x = w.x + Math.cos(a) * r;
         double z = w.z + Math.sin(a) * r;
         int y = ChitauriTerrain.surface(level, x, z);
         if (y != ChitauriTerrain.UNKNOWN) {
            return new Vec3(x, y, z);
         }
      }
      return w.known() && ChitauriTerrain.loaded(level, w.x, w.z) ? new Vec3(w.x, w.groundY, w.z) : null;
   }

   // ------------------------------------------------------------------ squads

   private void squads(ServerLevel level) {
      int now = this.s.clock;
      // gather members (soldiers on foot); loose soldiers (Leviathan deploys, commands) join the nearest small squad
      for (Squad q : this.squads.values()) {
         q.alive = 0;
         q.cx = 0.0;
         q.cz = 0.0;
      }
      List<ChitauriSoldier> foot = new ArrayList<>();
      for (UUID id : this.s.soldiers) {
         if (level.getEntity(id) instanceof ChitauriSoldier so && so.isAlive() && !so.isPassenger() && !so.severed() && !(so instanceof ChitauriCaptor)) {
            foot.add(so);
         }
      }
      for (ChitauriSoldier so : foot) {
         if (so.squad < 0) {
            Squad near = null;
            double best = 14.0 * 14.0;
            for (Squad q : this.squads.values()) {
               if (q.members.size() < 5 && q.members.size() > 0) {
                  for (UUID m : q.members) {
                     Entity e = level.getEntity(m);
                     if (e != null && e.distanceToSqr(so) < best) {
                        best = e.distanceToSqr(so);
                        near = q;
                     }
                  }
               }
            }
            so.squad = near != null ? near.id : this.s.squads++;
         }
         Squad q = this.squads.computeIfAbsent(so.squad, Squad::new);
         if (q.members.add(so.getUUID())) {
            q.initial++;
         }
         q.alive++;
         q.cx += so.getX();
         q.cz += so.getZ();
      }
      List<Integer> empty = new ArrayList<>();
      for (Squad q : this.squads.values()) {
         q.members.removeIf(m -> !(level.getEntity(m) instanceof ChitauriSoldier so) || !so.isAlive());
         if (q.members.isEmpty()) {
            empty.add(q.id);
            continue;
         }
         q.cx /= Math.max(1, q.alive);
         q.cz /= Math.max(1, q.alive);
         this.plan(level, q, now);
      }
      for (int id : empty) {
         this.squads.remove(id);
      }
      this.captors(level);
   }

   private void plan(ServerLevel level, Squad q, int now) {
      Vec3 centre = new Vec3(q.cx, 0, q.cz);
      int alive = q.members.size();
      // heavy losses: break contact and fall back (once per squad life), then regroup with another squad
      if (!q.retreated && q.initial >= 2 && alive <= Math.floor(q.initial * (1.0F - WarRules.RETREAT_LOSS))) {
         Vec3 rally = this.rallyPoint(level, centre);
         if (rally != null) {
            q.retreated = true;
            q.retreatUntil = now + WarRules.REGROUP_TICKS;
            this.give(level, q, new WarOrder(WarOrder.Type.RETREAT, rally.x, rally.y, rally.z, idOf(rally), -1, now));
            WarSector w = this.sectorAt(centre);
            this.say(level, "retreat", null, HeroLine.Priority.AMBIENT, 400, w != null ? this.sectorArg(w) : SECTOR + "centre");
            return;
         }
      }
      if (q.order != null && q.order.type() == WarOrder.Type.RETREAT) {
         if (now < q.retreatUntil) {
            return;
         }
         this.regroup(level, q);
      }
      // a hero close to a squad of three or more: surround them (commanders and cunning hives do it at once)
      LivingEntity hero = this.heroNear(level, centre, 22.0);
      boolean led = this.commanderNear(level, centre, ChitauriCommander.AURA);
      if (hero != null && alive >= 3 && (led || this.personality.cunning() > 0.45F || this.doctrine == WarDirector.Doctrine.CENTRE_ASSAULT
         || this.doctrine == WarDirector.Doctrine.COMMANDER_PUSH)) {
         if (q.order == null || q.order.type() != WarOrder.Type.ENCIRCLE || q.order.target() != hero.getId() || now - q.order.issued() > 200) {
            boolean fresh = q.order == null || q.order.type() != WarOrder.Type.ENCIRCLE;
            this.give(level, q, new WarOrder(WarOrder.Type.ENCIRCLE, hero.getX(), hero.getY(), hero.getZ(), -1, hero.getId(), now));
            double base = Math.atan2(q.cz - hero.getZ(), q.cx - hero.getX());
            int i = 0;
            for (UUID m : q.members) {
               if (level.getEntity(m) instanceof ChitauriSoldier so) {
                  so.encircleBearing = base + Math.PI * 2.0 * i / alive;
                  i++;
               }
            }
            if (fresh) {
               ChitauriCommander c = this.commanderEntityNear(level, centre, ChitauriCommander.AURA);
               if (c != null) {
                  c.gesture();
                  ChitauriSounds.play(level, c.position(), ChitauriSounds.COMMANDER_ORDER, 1.6F, 1.0F);
                  this.say(level, "encircle", c, HeroLine.Priority.ABILITY, 300);
               }
            }
         }
         return;
      }
      if (q.order != null && q.serial == this.planSerial && now - q.order.issued() < 600 && q.order.type() != WarOrder.Type.ENCIRCLE) {
         return;
      }
      q.serial = this.planSerial;
      // reinforcement transfer: one squad to each hive sector under attack
      WarSector hot = null;
      double hotD = Double.MAX_VALUE;
      for (WarSector w : this.sectors) {
         if ((w.state == WarSector.State.HELD || w.state == WarSector.State.CONTESTED && w.stronghold) && w.heroes > 0 && !this.reinforced(w.id, q)) {
            double d = w.distanceSq(q.cx, q.cz);
            if (d < hotD && d > w.radius * w.radius) {
               hotD = d;
               hot = w;
            }
         }
      }
      if (hot != null && hotD < 110.0 * 110.0) {
         Vec3 p = this.groundIn(level, hot, 0.4);
         if (p != null) {
            this.give(level, q, new WarOrder(WarOrder.Type.REINFORCE, p.x, p.y, p.z, hot.id, -1, now));
            this.say(level, "reinforce", null, HeroLine.Priority.AMBIENT, 500, this.sectorArg(hot));
            return;
         }
      }
      if (this.targets.isEmpty()) {
         // no plan yet: scout outwards from the landing
         WarSector far = null;
         double best = -1.0;
         for (WarSector w : this.sectors) {
            if (w.known() && w.state == WarSector.State.UNKNOWN) {
               double d = w.distanceSq(q.cx, q.cz);
               double score = w.ring * 10.0 - Math.sqrt(d) * 0.05 + (w.id + q.id) % 3;
               if (d < 90.0 * 90.0 && score > best) {
                  best = score;
                  far = w;
               }
            }
         }
         Vec3 p = far != null ? this.groundIn(level, far, 0.3) : null;
         if (p != null) {
            this.give(level, q, new WarOrder(WarOrder.Type.SCOUT, p.x, p.y, p.z, far.id, -1, now));
         }
         return;
      }
      WarSector front = this.sectors.get(this.targets.get(Math.floorMod(q.id, this.targets.size())));
      if (front.state == WarSector.State.HELD) {
         boolean defend = this.doctrine == WarDirector.Doctrine.HOLD_GROUND || front.heroes > 0;
         Vec3 p = this.groundIn(level, front, defend ? 0.25 : 0.7);
         if (p != null) {
            this.give(level, q, new WarOrder(defend ? WarOrder.Type.DEFEND : WarOrder.Type.PATROL, p.x, p.y, p.z, front.id, -1, now));
         }
      } else {
         Vec3 p = this.groundIn(level, front, 0.2);
         if (p != null) {
            this.give(level, q, new WarOrder(WarOrder.Type.SEIZE, p.x, p.y, p.z, front.id, -1, now));
         }
      }
   }

   private boolean reinforced(int sector, Squad except) {
      for (Squad q : this.squads.values()) {
         if (q != except && q.order != null && q.order.type() == WarOrder.Type.REINFORCE && q.order.sector() == sector) {
            return true;
         }
      }
      return false;
   }

   private int idOf(Vec3 p) {
      WarSector w = this.sectorAt(p);
      return w != null ? w.id : -1;
   }

   /** Retreat target: the nearest hive-held sector or stronghold, else the landing under the portal. */
   @Nullable
   private Vec3 rallyPoint(ServerLevel level, Vec3 from) {
      WarSector best = null;
      double bestD = Double.MAX_VALUE;
      for (WarSector w : this.sectors) {
         if ((w.state == WarSector.State.HELD || w.stronghold) && w.heroes == 0) {
            double d = w.distanceSq(from.x, from.z);
            if (d < bestD && d > 12.0 * 12.0) {
               bestD = d;
               best = w;
            }
         }
      }
      if (best != null) {
         Vec3 p = this.groundIn(level, best, 0.2);
         if (p != null) {
            return p;
         }
      }
      // fall back away from the nearest hero, towards the anchor
      int y = ChitauriTerrain.surface(level, this.s.anchor.x, this.s.anchor.z);
      return y != ChitauriTerrain.UNKNOWN && from.distanceToSqr(this.s.anchor.x, 0, this.s.anchor.z) > 16.0 * 16.0 ? new Vec3(this.s.anchor.x, y, this.s.anchor.z)
         : null;
   }

   /** After a retreat the squad merges into the nearest small squad (or stays together with a fresh head count). */
   private void regroup(ServerLevel level, Squad q) {
      Squad into = null;
      double best = 40.0 * 40.0;
      for (Squad o : this.squads.values()) {
         if (o != q && o.members.size() + q.members.size() <= 6 && (o.order == null || o.order.type() != WarOrder.Type.RETREAT)) {
            double d = (o.cx - q.cx) * (o.cx - q.cx) + (o.cz - q.cz) * (o.cz - q.cz);
            if (d < best) {
               best = d;
               into = o;
            }
         }
      }
      if (into != null) {
         for (UUID m : q.members) {
            if (level.getEntity(m) instanceof ChitauriSoldier so) {
               so.squad = into.id;
               so.order = into.order;
            }
            into.members.add(m);
         }
         into.initial = into.members.size();
         into.serial = -1;
         q.members.clear();
         this.say(level, "regroup", null, HeroLine.Priority.AMBIENT, 600);
      }
      q.initial = Math.max(1, q.members.size());
      q.order = null;
      q.serial = -1;
   }

   private void give(ServerLevel level, Squad q, WarOrder o) {
      q.order = o;
      for (UUID m : q.members) {
         if (level.getEntity(m) instanceof ChitauriSoldier so) {
            so.order = o;
            if (o.type() != WarOrder.Type.ENCIRCLE) {
               so.encircleBearing = Double.NaN;
            }
         }
      }
      // a squad that reaches an open sector it was sent to seize plants the stronghold
   }

   /** Captors: hunt where the people are (ABDUCT orders), the containment run is their own goal. */
   private void captors(ServerLevel level) {
      WarSector best = null;
      for (WarSector w : this.sectors) {
         if (w.known() && w.citizens > 0 && (best == null || w.citizens - w.heroes * 3 > best.citizens - best.heroes * 3)) {
            best = w;
         }
      }
      for (UUID id : this.elites.get(WarRules.UnitKind.CAPTOR)) {
         if (level.getEntity(id) instanceof ChitauriCaptor c && !c.carrying() && !c.isPassenger()) {
            WarOrder o = c.order;
            boolean abduct = o != null && o.type() == WarOrder.Type.ABDUCT;
            if (best == null) {
               if (abduct) {
                  c.order = null; // nobody left to take: fight like a soldier
               }
               continue;
            }
            double dx = c.getX() - best.x;
            double dz = c.getZ() - best.z;
            double there = Math.max(8.0, best.radius * 0.7);
            if (dx * dx + dz * dz < there * there) {
               if (abduct) {
                  c.order = null; // among the people: the hunt goal picks, a hero next to it is fought
               }
               continue;
            }
            if (o == null || o.sector() != best.id || this.s.clock - o.issued() > 600) {
               Vec3 p = this.groundIn(level, best, 0.5);
               if (p != null) {
                  c.order = new WarOrder(WarOrder.Type.ABDUCT, p.x, p.y, p.z, best.id, -1, this.s.clock);
               }
            }
         }
      }
   }

   /** Seize: a squad member at its point plants the stronghold pylon (one per sector, capped). */
   private void seize(ServerLevel level) {
      for (Squad q : this.squads.values()) {
         WarOrder o = q.order;
         if (o == null || o.type() != WarOrder.Type.SEIZE || o.sector() < 0) {
            continue;
         }
         WarSector w = this.sectors.get(o.sector());
         if (w.stronghold || this.strongholds() >= WarRules.MAX_STRONGHOLDS || w.control < WarRules.CONTEST_CONTROL) {
            continue;
         }
         for (UUID m : q.members) {
            Entity e = level.getEntity(m);
            if (e != null && e.onGround() && e.distanceToSqr(o.x(), e.getY(), o.z()) < 7.0 * 7.0) {
               if (this.plant(level, w, e.position().add(1.5, 0, 0), ChitauriBeacon.KIND_STRONGHOLD) != null) {
                  this.say(level, "stronghold", null, HeroLine.Priority.ABILITY, 200, this.sectorArg(w));
               }
               break;
            }
         }
      }
   }

   int strongholds() {
      return this.beacons.size() - this.containments.size();
   }

   @Nullable
   ChitauriBeacon plant(ServerLevel level, WarSector w, Vec3 near, byte kind) {
      int y = ChitauriTerrain.surface(level, near.x, near.z);
      if (y == ChitauriTerrain.UNKNOWN || !ChitauriTerrain.ticking(level, near.x, y, near.z)) {
         return null;
      }
      ChitauriBeacon b = ChitauriEntities.BEACON.get().create(level);
      if (b == null) {
         return null;
      }
      b.setKind(kind);
      b.sector = w != null ? w.id : -1;
      b.moveTo(near.x, y, near.z, this.s.random.nextFloat() * 360.0F, 0.0F);
      if (!level.addFreshEntity(b)) {
         return null;
      }
      this.beacons.put(b.getUUID(), b.sector);
      if (kind == ChitauriBeacon.KIND_CONTAINMENT) {
         this.containments.add(b.getUUID());
      }
      if (w != null) {
         w.stronghold = true;
         if (kind == ChitauriBeacon.KIND_CONTAINMENT) {
            w.containment = true;
         }
      }
      ChitauriInvasion.LOG.info("Chitauri war t={} {} pylon in sector {} at {} {} {}", this.s.clock, kind == ChitauriBeacon.KIND_CONTAINMENT ? "containment"
         : "stronghold", b.sector, Math.round(near.x), y, Math.round(near.z));
      return b;
   }

   // ------------------------------------------------------------------ vehicles and heavy units

   private void vehicles(ServerLevel level) {
      this.seize(level);
      if (this.targets.isEmpty()) {
         return;
      }
      int i = 0;
      for (UUID id : this.s.chariots) {
         if (level.getEntity(id) instanceof ChitauriChariot c) {
            WarSector w = this.sectors.get(this.targets.get(i++ % this.targets.size()));
            if (c.kind() == ChitauriChariot.KIND_GUNSHIP) {
               WarSector hot = this.hottest();
               if (hot != null) {
                  w = hot;
               }
            }
            if (c.orderPoint() == null || this.s.clock % 600 == 50) {
               c.setOrderPoint(new Vec3(w.x, (w.known() ? w.groundY : this.s.anchor.y) + 2, w.z));
            }
         }
      }
      WarSector air = this.airFront(level);
      for (UUID id : this.elites.get(WarRules.UnitKind.INTERCEPTOR)) {
         if (level.getEntity(id) instanceof ChitauriInterceptor f) {
            WarSector w = air != null ? air : this.sectors.get(this.targets.get(0));
            f.setOrderPoint(new Vec3(w.x, (w.known() ? w.groundY : this.s.anchor.y) + 22, w.z));
         }
      }
      for (UUID id : this.elites.get(WarRules.UnitKind.SIEGE)) {
         if (level.getEntity(id) instanceof ChitauriSiege g) {
            WarSector w = this.hottest();
            if (w == null) {
               w = this.sectors.get(this.targets.get(0));
            }
            g.setOrderPoint(new Vec3(w.x, (w.known() ? w.groundY : this.s.anchor.y) + 8, w.z));
         }
      }
   }

   @Nullable
   private WarSector hottest() {
      WarSector best = null;
      for (WarSector w : this.sectors) {
         if (w.heroes > 0 && (best == null || w.heroes > best.heroes || w.heroes == best.heroes && w.ring < best.ring)) {
            best = w;
         }
      }
      return best;
   }

   @Nullable
   private WarSector airFront(ServerLevel level) {
      for (ServerPlayer p : level.players()) {
         if (airborne(level, p)) {
            return this.sectorAt(p.position());
         }
      }
      return null;
   }

   /** A one-shot warning to whoever a siege lancer charges at (subtitle, throttled per player). */
   private void siegeWarnings(ServerLevel level) {
      for (UUID id : this.elites.get(WarRules.UnitKind.SIEGE)) {
         if (level.getEntity(id) instanceof ChitauriSiege g && g.state() == ChitauriSiege.CHARGE && g.siegeTarget() instanceof ServerPlayer p) {
            String key = "siege_warning:" + p.getUUID();
            Integer last = this.lastSaid.get(key);
            if (last == null || this.s.clock - last > 600) {
               this.lastSaid.put(key, this.s.clock);
               HeroSpeech.announce(List.of(p), null, VOICE, LINE + "siege_warning", HIVE, List.of(LINE + "siege_warning"), HeroLine.Priority.ULTIMATE);
            }
         }
      }
   }

   // ------------------------------------------------------------------ director

   static boolean airborne(ServerLevel level, Player p) {
      if (p.isSpectator() || p.onGround()) {
         return false;
      }
      if (p.getAbilities().flying || p.isFallFlying()) {
         return true;
      }
      int y = ChitauriTerrain.surface(level, p.getX(), p.getZ());
      return y != ChitauriTerrain.UNKNOWN && p.getY() - y > 3.0;
   }

   WarDirector.Snapshot snapshot(ServerLevel level) {
      List<ServerPlayer> players = ChitauriInvasion.viewers(level, this.s);
      players.removeIf(Player::isSpectator);
      int n = Math.max(1, players.size());
      int air = 0;
      int covered = 0;
      int centre = 0;
      Set<Integer> spread = new LinkedHashSet<>();
      for (ServerPlayer p : players) {
         if (airborne(level, p)) {
            air++;
         }
         if (!level.canSeeSky(BlockPos.containing(p.getEyePosition()))) {
            covered++;
         }
         WarSector w = this.sectorAt(p.position());
         if (w != null) {
            spread.add(w.id);
            if (w.ring <= 1) {
               centre++;
            }
         }
      }
      while (!this.killTicks.isEmpty() && this.s.clock - this.killTicks.peekFirst() > 1200) {
         this.killTicks.pollFirst();
      }
      int known = 0;
      int held = 0;
      int liberated = 0;
      for (WarSector w : this.sectors) {
         if (w.known()) {
            known++;
         }
         if (w.state == WarSector.State.HELD) {
            held++;
         }
         if (w.state == WarSector.State.LIBERATED) {
            liberated++;
         }
      }
      var ledger = ChitauriCaptives.ledger(level);
      float eff = this.heroDamage / (float)Math.max(1, this.heroDamage + this.losses * 6);
      int commanders = this.elites.get(WarRules.UnitKind.COMMANDER).size();
      int phase = switch (this.s.phase) {
         case AIR_ASSAULT -> 1;
         case LEVIATHAN -> 2;
         case CLIMAX -> 3;
         default -> 0;
      };
      // heroes who spent much of the last period in the air count as fliers even if they landed for this sample
      if (this.airSamples > 0) {
         air = Math.max(air, Math.min(players.size(), (int)Math.floor(this.airHits / (float)this.airSamples * n + 0.6F)));
      }
      this.airSamples = 0;
      this.airHits = 0;
      return new WarDirector.Snapshot(players.size(), air, covered / (float)n, this.killTicks.size(), known, held, liberated, ledger.active(), ledger.freed,
         this.citizensSeen, eff, players.isEmpty() ? 0.0F : spread.size() / (float)n, centre / (float)n, this.budgetLeft(level), commanders, phase);
   }

   private void decide(ServerLevel level) {
      WarDirector.Snapshot snap = this.snapshot(level);
      // learn inside the run: a doctrine under which sectors fell or the squads bled is less likely next time
      if (this.doctrine != null) {
         int lib = snap.sectorsLiberated();
         boolean bad = lib > this.lastLiberated || this.losses - this.lastLosses > 8 && snap.efficiency() < 0.25F;
         if (bad) {
            this.bias[this.doctrine.ordinal()] = Math.min(0.6F, this.bias[this.doctrine.ordinal()] + 0.12F);
         } else {
            this.bias[this.doctrine.ordinal()] = Math.max(0.0F, this.bias[this.doctrine.ordinal()] - 0.04F);
         }
      }
      this.lastLiberated = snap.sectorsLiberated();
      this.lastLosses = this.losses;
      WarDirector.Plan plan = WarDirector.decide(this.personality, snap, this.doctrine, this.repeats, this.roll, this.bias);
      boolean changed = plan.doctrine() != this.doctrine;
      this.repeats = changed ? 1 : this.repeats + 1;
      this.doctrine = plan.doctrine();
      this.targets = WarDirector.targets(this.sectors, plan.doctrine(), plan.fronts(), this.roll);
      this.planSerial++;
      this.buy(level, plan.mix());
      StringBuilder sc = new StringBuilder();
      for (int i = 0; i < plan.scores().length; i++) {
         sc.append(i == 0 ? "" : ",").append(String.format(Locale.ROOT, "%.2f", plan.scores()[i]));
      }
      String line = String.format(Locale.ROOT, "t=%d doctrine=%s players=%d air=%d cover=%.2f kpm=%.0f held=%d lib=%d captives=%d/%d eff=%.2f budget=%d mix=%s targets=%s scores=[%s]",
         this.s.clock, plan.doctrine().key(), snap.players(), snap.airborne(), snap.coverShare(), snap.killsPerMinute(), snap.sectorsHeld(),
         snap.sectorsLiberated(), snap.captivesHeld(), snap.captivesFreed(), snap.efficiency(), snap.budgetLeft(), plan.mix(), this.targetLabels(), sc);
      ChitauriInvasion.LOG.info("Chitauri director {}", line);
      this.doctrineLog.add(plan.doctrine().key());
      if (changed) {
         this.say(level, "doctrine." + plan.doctrine().key(), null, HeroLine.Priority.ABILITY, 0);
      }
   }

   private String targetLabels() {
      List<String> out = new ArrayList<>();
      for (int id : this.targets) {
         out.add(id + ":" + this.label(this.sectors.get(id)));
      }
      return out.toString();
   }

   /** Queues the plan's reinforcements: every unit costs budget, live caps hold, nothing beyond the budget ever comes. */
   private void buy(ServerLevel level, WarDirector.Mix mix) {
      int[] want = {mix.captors(), mix.heavies(), mix.interceptors(), mix.sieges(), mix.commanders()};
      WarRules.UnitKind[] kinds = {WarRules.UnitKind.CAPTOR, WarRules.UnitKind.HEAVY, WarRules.UnitKind.INTERCEPTOR, WarRules.UnitKind.SIEGE,
         WarRules.UnitKind.COMMANDER};
      int due = this.s.clock + 20;
      for (int k = 0; k < kinds.length; k++) {
         WarRules.UnitKind kind = kinds[k];
         int live = this.elites.get(kind).size() + this.queued(kind);
         for (int i = 0; i < want[k] && live < WarRules.cap(kind) && this.budgetLeft(level) >= WarRules.cost(kind); i++, live++) {
            int sector = this.targets.isEmpty() ? -1 : this.targets.get((i + k) % this.targets.size());
            this.s.queue.add(new ChitauriInvasion.Pending(kind, due, sector));
            this.spent += WarRules.cost(kind);
            this.bought.merge(kind, 1, Integer::sum);
            due += 30 + this.s.random.nextInt(40);
         }
      }
      if (this.budgetLeft(level) <= 0 && !this.lastSaid.containsKey("budget_empty")) {
         this.say(level, "budget_empty", null, HeroLine.Priority.AMBIENT, 0);
      }
   }

   private int queued(WarRules.UnitKind kind) {
      int n = 0;
      for (ChitauriInvasion.Pending p : this.s.queue) {
         if (p.unit == kind) {
            n++;
         }
      }
      return n;
   }

   /** Drop point of a unit bought for a sector (ground centre), or null. */
   @Nullable
   Vec3 pointFor(ServerLevel level, int sector) {
      if (sector < 0 || sector >= this.sectors.size()) {
         return null;
      }
      WarSector w = this.sectors.get(sector);
      return w.known() ? new Vec3(w.x, w.groundY, w.z) : null;
   }

   /** A new unit came out of the portal (or off a carrier). */
   void track(ServerLevel level, WarRules.UnitKind kind, Entity e, int sector) {
      this.elites.get(kind).add(e.getUUID());
      WarSector w = sector >= 0 && sector < this.sectors.size() ? this.sectors.get(sector) : null;
      String arg = w != null ? this.sectorArg(w) : SECTOR + "centre";
      switch (kind) {
         case COMMANDER -> this.say(level, "commander_arrives", null, HeroLine.Priority.ABILITY, 200, arg);
         case HEAVY -> this.say(level, "heavy_arrives", null, HeroLine.Priority.AMBIENT, 400, arg);
         case INTERCEPTOR -> this.say(level, "interceptors", null, HeroLine.Priority.ABILITY, 900);
         case SIEGE -> this.say(level, "siege_arrives", null, HeroLine.Priority.ABILITY, 600);
         case CAPTOR -> this.say(level, "captors", null, HeroLine.Priority.ABILITY, 900);
      }
   }

   // ------------------------------------------------------------------ events from units

   void onKilled(ServerLevel level, Entity e, @Nullable DamageSource source) {
      for (Set<UUID> set : this.elites.values()) {
         set.remove(e.getUUID());
      }
      if (e instanceof ChitauriBeacon) {
         return;
      }
      this.losses++;
      if (source != null && source.getEntity() instanceof Player) {
         this.kills++;
         this.killTicks.addLast(this.s.clock);
      }
   }

   void onHeroHurt(float amount) {
      this.heroDamage += Math.round(amount);
   }

   void onCaptured(ServerLevel level, ChitauriCaptor captor, LivingEntity person) {
      WarSector w = this.sectorAt(person.position());
      this.say(level, "captured", null, HeroLine.Priority.ABILITY, 300, w != null ? this.sectorArg(w) : SECTOR + "centre");
   }

   void onDelivered(ServerLevel level, ChitauriBeacon beacon, LivingEntity person) {
      this.maxHeld = Math.max(this.maxHeld, ChitauriCaptives.ledger(level).active());
   }

   void onFreed(ServerLevel level, Entity person, ChitauriCaptives.Cause cause, @Nullable ServerPlayer by) {
      if (cause == ChitauriCaptives.Cause.RESCUED && by != null) {
         HeroSpeech.announce(List.of(by), null, VOICE, LINE + "freed", HIVE, List.of(LINE + "freed"), HeroLine.Priority.AMBIENT);
         this.s.heroKills.merge(by.getUUID(), 2, Integer::sum);
      }
   }

   /** The nearest containment field with room; plants one (held sector or the landing) when {@code create}. */
   @Nullable
   ChitauriBeacon containmentNear(ServerLevel level, Vec3 from, boolean create) {
      var ledger = ChitauriCaptives.ledger(level);
      ChitauriBeacon best = null;
      double bestD = Double.MAX_VALUE;
      for (UUID id : this.containments) {
         if (level.getEntity(id) instanceof ChitauriBeacon b && b.isAlive() && ledger.hasRoom(id)) {
            double d = b.distanceToSqr(from);
            if (d < bestD) {
               bestD = d;
               best = b;
            }
         }
      }
      if (best != null || !create || this.containments.size() >= WarRules.MAX_CONTAINMENTS || !this.fighting()) {
         return best;
      }
      WarSector site = null;
      double siteD = Double.MAX_VALUE;
      for (WarSector w : this.sectors) {
         if (w.known() && w.heroes == 0 && !w.containment && (w.state == WarSector.State.HELD || w.ring == 0)) {
            double d = w.distanceSq(from.x, from.z) + (w.state == WarSector.State.HELD ? 0 : 40.0 * 40.0);
            if (d < siteD) {
               siteD = d;
               site = w;
            }
         }
      }
      if (site == null) {
         return null;
      }
      Vec3 p = this.groundIn(level, site, 0.3);
      ChitauriBeacon b = p != null ? this.plant(level, site, p, ChitauriBeacon.KIND_CONTAINMENT) : null;
      if (b != null) {
         this.say(level, "containment", null, HeroLine.Priority.ABILITY, 200, this.sectorArg(site));
      }
      return b;
   }

   void pylonDown(ServerLevel level, ChitauriBeacon b, @Nullable DamageSource source, boolean loud) {
      Integer sector = this.beacons.remove(b.getUUID());
      boolean containment = this.containments.remove(b.getUUID());
      ServerPlayer by = source != null && source.getEntity() instanceof ServerPlayer p ? p : null;
      int freed = ChitauriCaptives.releaseHeldBy(level, b, ChitauriCaptives.Cause.BEACON_DOWN, by);
      if (sector != null && sector >= 0 && sector < this.sectors.size()) {
         WarSector w = this.sectors.get(sector);
         boolean other = false;
         for (int s2 : this.beacons.values()) {
            other |= s2 == sector;
         }
         if (!other) {
            WarSector.Event ev = w.strongholdLost();
            w.containment = false;
            if (ev != WarSector.Event.NONE) {
               this.onSectorEvent(level, w, ev);
            }
         }
         if (loud) {
            this.say(level, containment ? "containment_down" : "stronghold_down", null, HeroLine.Priority.ABILITY, 0,
               containment ? String.valueOf(freed) : this.sectorArg(w));
         }
      }
      if (by != null) {
         this.s.heroKills.merge(by.getUUID(), containment ? 4 : 2, Integer::sum);
      }
   }

   // ------------------------------------------------------------------ static hooks (entities)

   public static void beaconDown(ServerLevel level, ChitauriBeacon b, DamageSource source) {
      ChitauriWar war = of(level);
      if (war != null) {
         war.pylonDown(level, b, source, true);
      } else {
         ChitauriCaptives.releaseHeldBy(level, b, ChitauriCaptives.Cause.BEACON_DOWN, null);
      }
   }

   public static void beaconGone(ServerLevel level, ChitauriBeacon b) {
      ChitauriWar war = of(level);
      if (war != null) {
         war.pylonDown(level, b, null, false);
      } else {
         ChitauriCaptives.releaseHeldBy(level, b, ChitauriCaptives.Cause.ENDED, null);
      }
   }

   public static void commanderDown(ServerLevel level, ChitauriCommander c) {
      ChitauriWar war = of(level);
      if (war == null) {
         return;
      }
      war.say(level, "commander_down", null, HeroLine.Priority.ABILITY, 0);
      // the squads it led lose their nerve: everyone close falls back
      for (Squad q : war.squads.values()) {
         if ((q.cx - c.getX()) * (q.cx - c.getX()) + (q.cz - c.getZ()) * (q.cz - c.getZ()) < ChitauriCommander.AURA * ChitauriCommander.AURA * 1.5) {
            Vec3 rally = war.rallyPoint(level, new Vec3(q.cx, c.getY(), q.cz));
            if (rally != null) {
               q.retreated = true;
               q.retreatUntil = war.s.clock + WarRules.REGROUP_TICKS / 2;
               war.give(level, q, new WarOrder(WarOrder.Type.RETREAT, rally.x, rally.y, rally.z, war.idOf(rally), -1, war.s.clock));
            }
         }
      }
   }

   public static boolean mayCallReinforcements(ServerLevel level, ChitauriCommander c) {
      ChitauriWar war = of(level);
      return war != null && war.fighting() && c.callsLeft() > 0 && war.s.clock - war.lastCall > WarRules.CALL_GAP
         && war.budgetLeft(level) >= WarRules.COST_CALL && war.s.soldiers.size() + 3 <= ChitauriRules.MAX_SOLDIERS;
   }

   public static boolean callReinforcements(ServerLevel level, ChitauriCommander c) {
      ChitauriWar war = of(level);
      if (war == null || !mayCallReinforcements(level, c)) {
         return false;
      }
      war.lastCall = war.s.clock;
      war.spent += WarRules.COST_CALL;
      ChitauriInvasion.Pending p = new ChitauriInvasion.Pending(ChitauriInvasion.Kind.CARRIER, war.s.clock + 10);
      p.point = c.position();
      war.s.queue.add(p);
      WarSector w = war.sectorAt(c.position());
      war.say(level, "commander_call", c, HeroLine.Priority.ABILITY, 0, w != null ? war.sectorArg(w) : SECTOR + "centre");
      ChitauriInvasion.LOG.info("Chitauri war t={} commander call at {} {} (budget left {})", war.s.clock, Math.round(c.getX()), Math.round(c.getZ()),
         war.budgetLeft(level));
      return true;
   }

   @Nullable
   private LivingEntity heroNear(ServerLevel level, Vec3 centre, double r) {
      LivingEntity best = null;
      double bestD = r * r;
      for (ServerPlayer p : level.players()) {
         if (ChitauriMob.validTarget(p)) {
            double d = (p.getX() - centre.x) * (p.getX() - centre.x) + (p.getZ() - centre.z) * (p.getZ() - centre.z);
            if (d < bestD && !airborne(level, p)) {
               bestD = d;
               best = p;
            }
         }
      }
      return best;
   }

   private boolean commanderNear(ServerLevel level, Vec3 centre, double r) {
      return this.commanderEntityNear(level, centre, r) != null;
   }

   @Nullable
   private ChitauriCommander commanderEntityNear(ServerLevel level, Vec3 centre, double r) {
      for (UUID id : this.elites.get(WarRules.UnitKind.COMMANDER)) {
         if (level.getEntity(id) instanceof ChitauriCommander c && c.isAlive()
            && (c.getX() - centre.x) * (c.getX() - centre.x) + (c.getZ() - centre.z) * (c.getZ() - centre.z) < r * r) {
            return c;
         }
      }
      return null;
   }

   // ------------------------------------------------------------------ subtitles

   /**
    * One hive (or commander) line to every viewer. {@code gap}: ticks before the same line may come again; ambient lines
    * also keep a global gap so the war never floods the screen.
    */
   void say(ServerLevel level, String id, @Nullable Entity speaker, HeroLine.Priority priority, int gap, String... args) {
      int now = this.s.clock;
      Integer last = this.lastSaid.get(id);
      if (last != null && now - last < gap || priority == HeroLine.Priority.AMBIENT && now - this.lastLine < 60) {
         return;
      }
      this.lastSaid.put(id, now);
      this.lastLine = now;
      StringBuilder f = new StringBuilder(LINE).append(id);
      for (String a : args) {
         f.append(SEP).append(a);
      }
      HeroSpeech.announce(ChitauriInvasion.viewers(level, this.s), speaker, VOICE, LINE + id, speaker != null ? COMMANDER : HIVE, List.of(f.toString()),
         priority);
   }

   // ------------------------------------------------------------------ end

   /** The portal is closing: the pylons die with the link and every captive goes free. */
   void sever(ServerLevel level) {
      for (UUID id : List.copyOf(this.beacons.keySet())) {
         if (level.getEntity(id) instanceof ChitauriBeacon b && b.isAlive()) {
            b.setHealth(0.0F);
            b.die(level.damageSources().generic());
         }
      }
      ChitauriCaptives.releaseAll(level);
      for (WarRules.UnitKind k : new WarRules.UnitKind[]{WarRules.UnitKind.INTERCEPTOR, WarRules.UnitKind.SIEGE}) {
         for (UUID id : List.copyOf(this.elites.get(k))) {
            if (level.getEntity(id) instanceof LivingEntity e && e.isAlive()) {
               e.setHealth(0.0F);
               e.die(level.damageSources().generic());
            }
         }
      }
   }

   /** End of the run: summary line, report log, history for the next runs. */
   void finish(ServerLevel level, boolean normal) {
      ChitauriCaptives.releaseAll(level);
      var ledger = ChitauriCaptives.ledger(level);
      int seized = 0;
      int liberated = 0;
      for (WarSector w : this.sectors) {
         seized += w.seizedCount;
         liberated += w.liberatedCount;
      }
      String summary = String.format(Locale.ROOT,
         "seed=%d personality=%s doctrines=%s seized=%d liberated=%d taken=%d delivered=%d freed=%d maxHeld=%d spent=%d bought=%s losses=%d kills=%d heroDamage=%d",
         this.s.seed, this.personality, this.doctrineLog, seized, liberated, ledger.taken, ledger.delivered, ledger.freed, this.maxHeld, this.spent, this.bought,
         this.losses, this.kills, this.heroDamage);
      ChitauriInvasion.LOG.info("Chitauri war summary {}", summary);
      if (normal) {
         HeroSpeech.announce(ChitauriInvasion.viewers(level, this.s), null, VOICE, LINE + "summary", HIVE, List.of(LINE + "summary" + SEP + seized + SEP
            + liberated + SEP + ledger.freed + SEP + ledger.taken), HeroLine.Priority.ULTIMATE);
      }
      History h = History.get(level);
      h.record(this.s.seed, this.bias, this.doctrineLog.size(), seized, liberated);
      ledger.resetCounters();
   }

   List<String> status(ServerLevel level) {
      List<String> out = new ArrayList<>();
      var ledger = ChitauriCaptives.ledger(level);
      out.add(String.format(Locale.ROOT, "seed %d, doctrine %s (x%d), budget %d/%d, captives %d held / %d freed", this.s.seed,
         this.doctrine == null ? "-" : this.doctrine.key(), this.repeats, this.spent, WarRules.budget(ledger.delivered), ledger.active(), ledger.freed));
      StringBuilder el = new StringBuilder("units:");
      for (var e : this.elites.entrySet()) {
         el.append(' ').append(e.getKey().name().toLowerCase(Locale.ROOT)).append('=').append(e.getValue().size());
      }
      el.append(", squads=").append(this.squads.size()).append(", pylons=").append(this.beacons.size());
      out.add(el.toString());
      StringBuilder sec = new StringBuilder("sectors:");
      for (WarSector w : this.sectors) {
         if (w.state != WarSector.State.UNKNOWN) {
            sec.append(' ').append(this.label(w)).append('=').append(w.state.name().toLowerCase(Locale.ROOT)).append(String.format(Locale.ROOT, "(%.1f)", w.control));
         }
      }
      out.add(sec.toString());
      return out;
   }

   @SubscribeEvent
   public static void onHurt(LivingHurtEvent event) {
      if (event.getEntity() instanceof Player p && p.level() instanceof ServerLevel level && ChitauriMob.is(event.getSource().getEntity())) {
         ChitauriWar war = of(level);
         if (war != null) {
            war.onHeroHurt(event.getAmount());
         }
      }
   }

   // ------------------------------------------------------------------ history across runs

   /**
    * What the hive learned in earlier invasions on this world: a fading penalty per doctrine (the ones that lost sectors
    * weigh less next time) and a short run log. Saved with the overworld.
    */
   static final class History extends SavedData {
      static final String NAME = "viltrumitecore_chitauri_war";
      final float[] penalty = new float[WarDirector.Doctrine.values().length];
      int runs;
      final List<String> log = new ArrayList<>();

      static History get(ServerLevel level) {
         return level.getServer().overworld().getDataStorage().computeIfAbsent(History::load, History::new, NAME);
      }

      static History load(CompoundTag tag) {
         History h = new History();
         h.runs = tag.getInt("runs");
         int[] p = tag.getIntArray("penalty");
         for (int i = 0; i < Math.min(p.length, h.penalty.length); i++) {
            h.penalty[i] = p[i] / 1000.0F;
         }
         var list = tag.getList("log", net.minecraft.nbt.Tag.TAG_STRING);
         for (int i = 0; i < list.size(); i++) {
            h.log.add(list.getString(i));
         }
         return h;
      }

      @Override
      public CompoundTag save(CompoundTag tag) {
         tag.putInt("runs", this.runs);
         int[] p = new int[this.penalty.length];
         for (int i = 0; i < p.length; i++) {
            p[i] = Math.round(this.penalty[i] * 1000.0F);
         }
         tag.putIntArray("penalty", p);
         var list = new net.minecraft.nbt.ListTag();
         for (String s : this.log) {
            list.add(net.minecraft.nbt.StringTag.valueOf(s));
         }
         tag.put("log", list);
         return tag;
      }

      /** Bias for the next run: the stored penalties, faded (old lessons weigh less). */
      float[] bias() {
         float[] b = new float[this.penalty.length];
         for (int i = 0; i < b.length; i++) {
            b[i] = this.penalty[i] * 0.7F;
         }
         return b;
      }

      void record(long seed, float[] runBias, int decisions, int seized, int liberated) {
         this.runs++;
         for (int i = 0; i < this.penalty.length; i++) {
            this.penalty[i] = Math.min(0.5F, runBias[i]);
         }
         this.log.add(String.format(Locale.ROOT, "run %d seed %d decisions %d seized %d liberated %d", this.runs, seed, decisions, seized, liberated));
         while (this.log.size() > 20) {
            this.log.remove(0);
         }
         this.setDirty();
      }
   }
}
