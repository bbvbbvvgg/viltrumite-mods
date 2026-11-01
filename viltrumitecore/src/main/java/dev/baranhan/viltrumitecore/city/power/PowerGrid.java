package dev.baranhan.viltrumitecore.city.power;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.power.api.AlarmStage;
import dev.baranhan.viltrumitecore.city.power.api.InfrastructureEvent;
import dev.baranhan.viltrumitecore.city.power.api.InfrastructureIncident;
import dev.baranhan.viltrumitecore.city.power.api.OutageInfo;
import dev.baranhan.viltrumitecore.city.profile.CityProfile;
import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.CityPowerS2CPacket;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The power grid of the living city (Living City 3.0). The arena is cut into {@link PowerRules#CELL}-block cells
 * (3 x 3 chunks), grouped into substations of 3 x 3 cells. A hit on the infrastructure sends a wave of failures out
 * from the impact: near cells lose their transformer (sparks, long repair), the ring around them flickers and goes
 * dark, the edge gets a voltage dip. Worn-out substations trip and may drag their neighbours down (a cascade,
 * bounded in depth and count). Crews restore power substation by substation, sooner once the all-clear sounds.
 * <p>
 * The server owns all of it: only cells that are not fully powered are kept, every pending change sits in one
 * bounded timeline, fixture writes, scans and packets are budgeted per tick. Lights are real block states
 * (see {@link Fixtures}), switched by {@link FixtureSwitch} with a ledger, so nothing stays changed. Paused while
 * the city reset runs; the reset clears it.
 */
public final class PowerGrid {
   static final Logger LOG = LoggerFactory.getLogger("viltrumitecore/city-power");
   static final String LINE = "subtitle.viltrumitecore.city.";
   static final String VOICE = "city";
   static final String SPEAKER = "subtitle.viltrumitecore.speaker.city_defense";
   static final char SEP = '\u001F';
   /** Players within this distance of the arena get the state and the alarm lines. */
   static final int AUDIENCE_MARGIN = 256;
   static final double FX_RANGE = 420.0;

   public enum State {
      POWERED,
      SURGE,
      FAILING,
      DARK,
      DAMAGED,
      RESTORING;

      boolean dark() {
         return this == DARK || this == DAMAGED;
      }
   }

   static final class Cell {
      final long key;
      final int ix;
      final int iz;
      State state = State.POWERED;
      State after = State.DARK;
      long since;
      long restoreAt;
      double damage;
      long seed;
      long incident = -1;
      @Nullable
      BlockPos transformer;
      long[] emergency = new long[0];
      boolean shownLit = true;

      Cell(long key) {
         this.key = key;
         this.ix = PowerRules.keyX(key);
         this.iz = PowerRules.keyZ(key);
      }
   }

   static final class Station {
      final long key;
      double health = 1.0;
      boolean tripped;
      long restoreAt;
      long lastHit;
      int depth;

      Station(long key) {
         this.key = key;
      }
   }

   private static final int HIT_SURGE = 0, HIT_DARK = 1, HIT_DAMAGED = 2, TRIP = 3, RESTORE = 4;

   private record Action(long at, long seq, int kind, long key, double value, long incident, int depth, double x, double z) {
   }

   final CityManager city;
   final ServerLevel level;
   final int originX;
   final int originZ;
   final int cols;
   final int rows;
   private final String[] districtOf;
   private final Map<String, Integer> districtCells = new HashMap<>();
   private final Map<String, Integer> districtDark = new HashMap<>();
   private final Long2ObjectOpenHashMap<Cell> cells = new Long2ObjectOpenHashMap<>();
   private final Long2ObjectOpenHashMap<Station> stations = new Long2ObjectOpenHashMap<>();
   private final LongLinkedOpenHashSet animating = new LongLinkedOpenHashSet();
   private final PriorityQueue<Action> timeline = new PriorityQueue<>((a, b) -> a.at != b.at ? Long.compare(a.at, b.at) : Long.compare(a.seq, b.seq));
   private final Long2LongOpenHashMap tripsByIncident = new Long2LongOpenHashMap();
   private final Long2ObjectOpenHashMap<InfrastructureIncident> incidents = new Long2ObjectOpenHashMap<>();
   private final ArrayDeque<InfrastructureIncident> recent = new ArrayDeque<>();
   private final Long2LongOpenHashMap lastBurst = new Long2LongOpenHashMap();
   private final Map<String, Long> lastDistrictLine = new HashMap<>();
   private final Set<UUID> synced = new HashSet<>();
   final FixtureIndex index;
   final FixtureSwitch fixtures;
   private final CityAlarm alarm;
   private long nextIncident = 1;
   private long seq;
   private boolean dirty;
   private long lastSync = -1000;
   private long lastLine = -1000;
   private int darkCount;
   private long droppedActions;

   public PowerGrid(CityManager city) {
      this.city = city;
      this.level = city.level();
      CityProfile p = city.profile();
      this.originX = Math.floorDiv(p.minX, 16) * 16;
      this.originZ = Math.floorDiv(p.minZ, 16) * 16;
      this.cols = Math.max(1, Math.floorDiv(p.maxX - this.originX, PowerRules.CELL) + 1);
      this.rows = Math.max(1, Math.floorDiv(p.maxZ - this.originZ, PowerRules.CELL) + 1);
      this.districtOf = new String[this.cols * this.rows];
      for (int ix = 0; ix < this.cols; ix++) {
         for (int iz = 0; iz < this.rows; iz++) {
            double cx = this.originX + (ix + 0.5) * PowerRules.CELL, cz = this.originZ + (iz + 0.5) * PowerRules.CELL;
            CityProfile.District d = p.districtAt(cx, cz);
            String name = d == null ? p.name : d.name();
            this.districtOf[ix * this.rows + iz] = name;
            this.districtCells.merge(name, 1, Integer::sum);
         }
      }
      this.index = new FixtureIndex(this.level, p.minY, p.maxY);
      this.fixtures = new FixtureSwitch(this, this.level, this.index, this.level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
         .resolve(CityProfile.FOLDER));
      this.alarm = new CityAlarm(this);
      this.fixtures.load();
      if (this.fixtures.size() > 0) {
         LOG.info("City power: {} fixtures were left dark by the last session, relighting them as their chunks load", this.fixtures.size());
         this.relightLedger();
      }
   }

   // ------------------------------------------------------------------ queries

   public CityAlarm alarm() {
      return this.alarm;
   }

   long now() {
      return this.level.getGameTime();
   }

   void markDirty() {
      this.dirty = true;
   }

   boolean paused() {
      return this.city.restoreQueue().running();
   }

   public boolean inGrid(int ix, int iz) {
      return ix >= 0 && iz >= 0 && ix < this.cols && iz < this.rows;
   }

   public boolean powered(int x, int z) {
      Cell c = this.cells.get(PowerRules.key(PowerRules.cellIndex(x, this.originX), PowerRules.cellIndex(z, this.originZ)));
      return c == null || !(c.state.dark() || c.state == State.FAILING);
   }

   public float darkFraction() {
      return (float)this.darkCount / (this.cols * this.rows);
   }

   public int cellCount() {
      return this.cols * this.rows;
   }

   public List<InfrastructureIncident> recentIncidents() {
      return List.copyOf(this.recent);
   }

   String district(int ix, int iz) {
      return this.inGrid(ix, iz) ? this.districtOf[ix * this.rows + iz] : this.city.profile().name;
   }

   public List<OutageInfo> outages() {
      Map<String, long[]> acc = new HashMap<>();
      for (Cell c : this.cells.values()) {
         if (!c.state.dark()) {
            continue;
         }
         long[] a = acc.computeIfAbsent(this.district(c.ix, c.iz), k -> new long[4]);
         a[0]++;
         a[1] += c.state == State.DAMAGED ? 1 : 0;
         a[2] += this.originX + (c.ix + 0.5) * PowerRules.CELL;
         a[3] += this.originZ + (c.iz + 0.5) * PowerRules.CELL;
      }
      List<OutageInfo> out = new ArrayList<>();
      for (Map.Entry<String, long[]> e : acc.entrySet()) {
         long[] a = e.getValue();
         int x = (int)(a[2] / a[0]), z = (int)(a[3] / a[0]);
         out.add(new OutageInfo(e.getKey(), this.districtCells.getOrDefault(e.getKey(), 0), (int)a[0], (int)a[1], new BlockPos(x, this.surfaceY(x, z), z)));
      }
      out.sort((a, b) -> Integer.compare(b.darkCells(), a.darkCells()));
      return out;
   }

   /** The nine chunks of a cell (cells are chunk aligned). */
   long[] chunksOf(long cellKey) {
      int cx0 = (this.originX >> 4) + PowerRules.keyX(cellKey) * 3, cz0 = (this.originZ >> 4) + PowerRules.keyZ(cellKey) * 3;
      long[] out = new long[9];
      int i = 0;
      for (int dx = 0; dx < 3; dx++) {
         for (int dz = 0; dz < 3; dz++) {
            out[i++] = ChunkPos.asLong(cx0 + dx, cz0 + dz);
         }
      }
      return out;
   }

   long cellOfChunk(int cx, int cz) {
      return PowerRules.key(Math.floorDiv(cx - (this.originX >> 4), 3), Math.floorDiv(cz - (this.originZ >> 4), 3));
   }

   int surfaceY(int x, int z) {
      if (this.level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null) {
         return this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
      }
      int feet = this.city.grid().feetY(x, z);
      return feet != dev.baranhan.viltrumitecore.city.profile.WalkGrid.NONE ? feet : this.city.profile().minY + 64;
   }

   Vec3 stationSite(long stationKey) {
      int x = this.originX + (PowerRules.keyX(stationKey) * PowerRules.STATION * PowerRules.CELL) + PowerRules.STATION * PowerRules.CELL / 2;
      int z = this.originZ + (PowerRules.keyZ(stationKey) * PowerRules.STATION * PowerRules.CELL) + PowerRules.STATION * PowerRules.CELL / 2;
      return new Vec3(x + 0.5, this.surfaceY(x, z) + 1.5, z + 0.5);
   }

   private Cell cell(long key) {
      Cell c = this.cells.get(key);
      if (c == null) {
         c = new Cell(key);
         this.cells.put(key, c);
      }
      return c;
   }

   private Station station(long cellKey) {
      long sk = PowerRules.key(PowerRules.stationIndex(PowerRules.keyX(cellKey)), PowerRules.stationIndex(PowerRules.keyZ(cellKey)));
      Station s = this.stations.get(sk);
      if (s == null) {
         s = new Station(sk);
         this.stations.put(sk, s);
      }
      return s;
   }

   private long[] cellsOfStation(long stationKey) {
      LongArrayList out = new LongArrayList(9);
      int sx = PowerRules.keyX(stationKey) * PowerRules.STATION, sz = PowerRules.keyZ(stationKey) * PowerRules.STATION;
      for (int dx = 0; dx < PowerRules.STATION; dx++) {
         for (int dz = 0; dz < PowerRules.STATION; dz++) {
            if (this.inGrid(sx + dx, sz + dz)) {
               out.add(PowerRules.key(sx + dx, sz + dz));
            }
         }
      }
      return out.toLongArray();
   }

   private int darkCellsOf(long stationKey) {
      int n = 0;
      for (long k : this.cellsOfStation(stationKey)) {
         Cell c = this.cells.get(k);
         if (c != null && (c.state.dark() || c.state == State.FAILING)) {
            n++;
         }
      }
      return n;
   }

   // ------------------------------------------------------------------ damage

   /** See {@link dev.baranhan.viltrumitecore.city.power.api.CityInfrastructure#damage}. */
   @Nullable
   public InfrastructureIncident damage(double x, double y, double z, float radius, float severity, String cause, String source) {
      if (this.paused() || !(severity >= 0.05F)) {
         return null;
      }
      float sev = Math.min(1.0F, severity);
      int ix = PowerRules.cellIndex((int)Math.floor(x), this.originX), iz = PowerRules.cellIndex((int)Math.floor(z), this.originZ);
      if (!this.inGrid(ix, iz)) {
         return null;
      }
      long now = this.now();
      InfrastructureIncident incident = new InfrastructureIncident(this.nextIncident++, this.level.dimension(), x, y, z, cause, source, radius, sev, now);
      this.recent.addFirst(incident);
      while (this.recent.size() > 16) {
         this.recent.removeLast();
      }
      this.incidents.put(incident.id(), incident);
      double reach = PowerRules.reach(radius, sev);
      int rc = Math.min(PowerRules.MAX_CELL_REACH, (int)Math.ceil(reach / PowerRules.CELL));
      int touched = 0;
      List<BlockPos> blown = new ArrayList<>();
      Map<Long, Double> wear = new HashMap<>();
      for (int dx = -rc; dx <= rc; dx++) {
         for (int dz = -rc; dz <= rc; dz++) {
            int cx = ix + dx, cz = iz + dz;
            if (!this.inGrid(cx, cz)) {
               continue;
            }
            double d = PowerRules.distanceToCell(x, z, cx, cz, this.originX, this.originZ);
            double hit = sev * PowerRules.falloff(d, reach);
            PowerRules.Effect eff = PowerRules.effect(hit);
            if (eff == PowerRules.Effect.NONE) {
               continue;
            }
            long key = PowerRules.key(cx, cz);
            long seed = PowerRules.mix(incident.id() * 0x9E3779B97F4A7C15L + key);
            int kind = eff == PowerRules.Effect.DAMAGED ? HIT_DAMAGED : eff == PowerRules.Effect.DARK ? HIT_DARK : HIT_SURGE;
            this.schedule(now + PowerRules.waveDelay(d, seed), kind, key, hit, incident.id(), 0, x, z);
            touched++;
            if (eff == PowerRules.Effect.DAMAGED) {
               blown.add(this.transformerSite(cx, cz, x, z, seed));
            }
            long sk = this.station(key).key;
            wear.merge(sk, PowerRules.stationWear(eff == PowerRules.Effect.SURGE ? hit * 0.3 : hit), Double::sum);
         }
      }
      for (Map.Entry<Long, Double> e : wear.entrySet()) {
         Station st = this.stations.get((long)e.getKey());
         st.health = Math.max(0.0, st.health - e.getValue());
         st.lastHit = now;
         if (!st.tripped && PowerRules.stationTrips(st.health, this.darkCellsOf(st.key), 9)) {
            Vec3 site = this.stationSite(st.key);
            this.schedule(now + 20 + PowerRules.waveDelay(Math.hypot(site.x - x, site.z - z), st.key), TRIP, st.key, sev, incident.id(), 0, x, z);
         }
      }
      if (sev >= 0.25F) {
         this.fx(CityPowerS2CPacket.FX_IMPACT, x, y, z, sev, (int)PowerRules.mix(incident.id()));
      }
      Vec3 at = new Vec3(x, y, z);
      if (sev >= 0.45F) {
         this.alarm.lease("incident:" + cause, AlarmStage.ACTIVE, at, (float)reach * 3, 1200);
      } else if (sev >= 0.3F && !"destruction".equals(cause)) {
         this.alarm.lease("incident:" + cause, AlarmStage.WARNING, at, (float)reach * 3, 600);
      }
      if ("meteor".equals(cause)) {
         int strikes = 0;
         for (InfrastructureIncident r : this.recent) {
            if ("meteor".equals(r.cause()) && now - r.gameTime() < 1200) {
               strikes++;
            }
         }
         if (strikes >= 4) {
            this.alarm.lease("meteor_storm", AlarmStage.CRITICAL, at, (float)reach * 4, 900);
         }
      }
      MinecraftForge.EVENT_BUS.post(new InfrastructureEvent.Damaged(this.level, incident, touched, blown));
      return incident;
   }

   /** A burst of destroyed blocks in one chunk and tick (from the city's journal hook). */
   public void onBurst(double x, double y, double z, int blocks) {
      double sev = PowerRules.burstSeverity(blocks);
      if (sev <= 0.0 || this.paused()) {
         return;
      }
      int ix = PowerRules.cellIndex((int)Math.floor(x), this.originX), iz = PowerRules.cellIndex((int)Math.floor(z), this.originZ);
      long key = PowerRules.key(ix, iz);
      long now = this.now();
      long last = this.lastBurst.getOrDefault(key, Long.MIN_VALUE / 2);
      if (now - last < (blocks >= 30 ? 20 : 60)) {
         return; // one chunk smashed every tick by the same fight is one incident, not twenty
      }
      this.lastBurst.put(key, now);
      String cause = PowerTriggers.burstCause(this.level, x, z);
      this.damage(x, y, z, (float)PowerRules.burstRadius(blocks), (float)sev, cause, cause + ":burst");
   }

   private BlockPos transformerSite(int cx, int cz, double x, double z, long seed) {
      double x0 = this.originX + (double)cx * PowerRules.CELL, z0 = this.originZ + (double)cz * PowerRules.CELL;
      double px = Math.max(x0 + 4, Math.min(x0 + PowerRules.CELL - 4, x + (PowerRules.unit(seed) - 0.5) * 18));
      double pz = Math.max(z0 + 4, Math.min(z0 + PowerRules.CELL - 4, z + (PowerRules.unit(seed + 7) - 0.5) * 18));
      int bx = (int)Math.floor(px), bz = (int)Math.floor(pz);
      return new BlockPos(bx, this.surfaceY(bx, bz), bz);
   }

   private void schedule(long at, int kind, long key, double value, long incident, int depth, double x, double z) {
      if (this.timeline.size() >= PowerRules.MAX_TIMELINE) {
         this.droppedActions++;
         return;
      }
      this.timeline.add(new Action(at, this.seq++, kind, key, value, incident, depth, x, z));
   }

   // ------------------------------------------------------------------ tick

   public void tick(long now) {
      if (this.paused()) {
         this.alarm.tick(now);
         this.sync(now);
         return;
      }
      int budget = PowerRules.ACTIONS_PER_TICK;
      while (budget-- > 0 && !this.timeline.isEmpty() && this.timeline.peek().at <= now) {
         this.run(this.timeline.poll(), now);
      }
      this.animate(now);
      if (now % 20 == 0) {
         this.restoreDue(now);
         PowerTriggers.poll(this, now);
      }
      this.alarm.tick(now);
      this.index.tick();
      this.fixtures.tick();
      this.sync(now);
      if (now % 1200 == 0) {
         this.prune(now);
      }
   }

   private void run(Action a, long now) {
      switch (a.kind) {
         case HIT_SURGE -> {
            Cell c = this.cell(a.key);
            if (c.state == State.POWERED) {
               this.enter(c, State.SURGE, now);
               c.seed = PowerRules.mix(a.key + now);
            } else if (c.state == State.SURGE) {
               c.since = now;
            }
            this.forgetIfIdle(c);
         }
         case HIT_DARK, HIT_DAMAGED -> {
            Cell c = this.cell(a.key);
            boolean damaged = a.kind == HIT_DAMAGED;
            c.damage = Math.max(c.damage, a.value);
            c.incident = a.incident;
            PowerRules.Effect eff = damaged ? PowerRules.Effect.DAMAGED : PowerRules.Effect.DARK;
            c.restoreAt = Math.max(c.restoreAt, now + PowerRules.restoreDelay(eff, a.value, PowerRules.mix(a.key ^ a.incident)));
            if (damaged && c.transformer == null) {
               c.transformer = this.transformerSite(c.ix, c.iz, a.x, a.z, PowerRules.mix(a.incident * 0x9E3779B97F4A7C15L + a.key));
               BlockPos t = c.transformer;
               this.fx(CityPowerS2CPacket.FX_BLOW, t.getX() + 0.5, t.getY() + 0.8, t.getZ() + 0.5, (float)a.value, (int)PowerRules.mix(a.key + now));
            }
            switch (c.state) {
               case POWERED, SURGE, RESTORING -> {
                  c.after = damaged ? State.DAMAGED : State.DARK;
                  c.seed = PowerRules.mix(a.key * 31 + now);
                  this.enter(c, State.FAILING, now);
               }
               case FAILING -> {
                  if (damaged) {
                     c.after = State.DAMAGED;
                  }
               }
               case DARK -> {
                  if (damaged) {
                     c.state = State.DAMAGED;
                     this.dirty = true;
                  }
               }
               default -> {
               }
            }
         }
         case TRIP -> this.trip(a, now);
         case RESTORE -> {
            Cell c = this.cells.get(a.key);
            if (c != null && c.state.dark() && !this.station(a.key).tripped) {
               c.transformer = null;
               c.seed = PowerRules.mix(a.key * 17 + now);
               this.enter(c, State.RESTORING, now);
            }
         }
         default -> {
         }
      }
   }

   private void trip(Action a, long now) {
      Station st = this.stations.get(a.key);
      if (st == null) {
         st = new Station(a.key);
         this.stations.put(a.key, st);
      }
      if (st.tripped) {
         return;
      }
      long trips = this.tripsByIncident.get(a.incident);
      if (trips >= PowerRules.MAX_TRIPS_PER_INCIDENT) {
         return;
      }
      this.tripsByIncident.put(a.incident, trips + 1);
      st.tripped = true;
      st.depth = a.depth;
      st.lastHit = now;
      st.restoreAt = now + PowerRules.stationRestoreDelay(a.depth, PowerRules.mix(a.key + now));
      Vec3 site = this.stationSite(a.key);
      this.fx(CityPowerS2CPacket.FX_CASCADE, site.x, site.y, site.z, (float)Math.max(0.4, a.value), (int)PowerRules.mix(a.key ^ now));
      long[] ks = this.cellsOfStation(a.key);
      for (int i = 0; i < ks.length; i++) {
         long k = ks[(int)Math.floorMod(PowerRules.mix(a.key + i) + i, (long)ks.length)];
         this.schedule(now + 4 + i * 5L, HIT_DARK, k, 0.42, a.incident, a.depth, site.x, site.z);
      }
      // the load moves onto the neighbours: they may give way too
      int sx = PowerRules.keyX(a.key), sz = PowerRules.keyZ(a.key);
      int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
      InfrastructureIncident inc = this.incidents.get(a.incident);
      double sev = inc == null ? a.value : inc.severity();
      for (int[] d : around) {
         int nx = sx + d[0], nz = sz + d[1];
         if (!this.inGrid(nx * PowerRules.STATION, nz * PowerRules.STATION)) {
            continue;
         }
         long nk = PowerRules.key(nx, nz);
         long seed = PowerRules.mix(nk * 131 + a.incident * 7 + a.depth);
         Station n = this.stations.get(nk);
         double weak = n == null ? 0.0 : (1.0 - n.health) * 0.5;
         double load = PowerRules.cascadeLoad(sev, a.depth + 1) + weak;
         if ((n == null || !n.tripped) && PowerRules.cascades(load, a.depth + 1, seed)) {
            this.schedule(now + PowerRules.cascadeDelay(seed), TRIP, nk, load, a.incident, a.depth + 1, site.x, site.z);
         }
      }
      String district = this.district(sx * PowerRules.STATION + 1, sz * PowerRules.STATION + 1);
      BlockPos at = BlockPos.containing(site);
      MinecraftForge.EVENT_BUS.post(new InfrastructureEvent.Outage(this.level,
         new OutageInfo(district, this.districtCells.getOrDefault(district, 0), this.districtDark.getOrDefault(district, 0), 0, at), a.depth > 0, a.incident));
      if (a.depth >= 1) {
         this.alarm.lease("cascade", AlarmStage.CRITICAL, site, 600.0F, 900);
         if (now - this.lastLine > 200) {
            this.say("cascade" + SEP + district, true);
         }
      }
      this.dirty = true;
   }

   private void enter(Cell c, State s, long now) {
      State old = c.state;
      c.state = s;
      c.since = now;
      if (s == State.SURGE || s == State.FAILING || s == State.RESTORING) {
         this.animating.add(c.key);
      }
      boolean wasDark = old.dark();
      boolean isDark = s.dark();
      if (wasDark != isDark) {
         this.countDark(c, isDark ? 1 : -1, now);
      }
      this.dirty = true;
   }

   /** Steps the flickering cells and hands the wanted light state to the fixture switch. */
   private void animate(long now) {
      if (this.animating.isEmpty()) {
         return;
      }
      LongArrayList finished = new LongArrayList();
      for (long key : this.animating) {
         Cell c = this.cells.get(key);
         if (c == null) {
            finished.add(key);
            continue;
         }
         int age = (int)(now - c.since);
         boolean lit;
         switch (c.state) {
            case SURGE -> {
               lit = PowerRules.surgeLit(age, c.seed);
               if (age >= PowerRules.SURGE_TICKS) {
                  c.state = State.POWERED;
                  this.dirty = true;
                  finished.add(key);
               }
            }
            case FAILING -> {
               lit = PowerRules.flickerLit(age, c.seed, false);
               if (age >= PowerRules.FLICKER_TICKS) {
                  this.enter(c, c.after, now);
                  finished.add(key);
                  this.onDark(c, now);
               }
            }
            case RESTORING -> {
               lit = PowerRules.flickerLit(age, c.seed, true);
               if (age >= PowerRules.FLICKER_TICKS) {
                  c.state = State.POWERED;
                  c.damage = 0;
                  c.restoreAt = 0;
                  c.emergency = new long[0];
                  this.dirty = true;
                  finished.add(key);
               }
            }
            default -> {
               lit = !c.state.dark();
               finished.add(key);
            }
         }
         if (lit != c.shownLit) {
            c.shownLit = lit;
            this.fixtures.want(key, lit);
         }
      }
      for (int i = 0; i < finished.size(); i++) {
         long key = finished.getLong(i);
         this.animating.remove(key);
         Cell c = this.cells.get(key);
         if (c != null) {
            this.forgetIfIdle(c);
         }
      }
   }

   private void forgetIfIdle(Cell c) {
      if (c.state == State.POWERED && c.shownLit && !this.animating.contains(c.key)) {
         this.cells.remove(c.key);
      }
   }

   private void onDark(Cell c, long now) {
      c.emergency = this.fixtures.emergencyLights(c.key, 3);
      // most of a substation dark: it trips (and may take its neighbours with it)
      Station st = this.station(c.key);
      if (!st.tripped && PowerRules.stationTrips(st.health, this.darkCellsOf(st.key), this.cellsOfStation(st.key).length)) {
         this.schedule(now + 30, TRIP, st.key, Math.max(0.35, c.damage), c.incident, 0, this.originX + (c.ix + 0.5) * PowerRules.CELL,
            this.originZ + (c.iz + 0.5) * PowerRules.CELL);
      }
   }

   private void countDark(Cell c, int delta, long now) {
      this.darkCount = Math.max(0, this.darkCount + delta);
      String d = this.district(c.ix, c.iz);
      int before = this.districtDark.getOrDefault(d, 0);
      int after = Math.max(0, before + delta);
      this.districtDark.put(d, after);
      int x = this.originX + c.ix * PowerRules.CELL + PowerRules.CELL / 2, z = this.originZ + c.iz * PowerRules.CELL + PowerRules.CELL / 2;
      OutageInfo info = new OutageInfo(d, this.districtCells.getOrDefault(d, 0), after, 0, new BlockPos(x, this.surfaceY(x, z), z));
      if (before == 0 && after > 0) {
         MinecraftForge.EVENT_BUS.post(new InfrastructureEvent.Outage(this.level, info, false, c.incident));
         this.districtLine("blackout", d, now);
      } else if (before > 0 && after == 0) {
         MinecraftForge.EVENT_BUS.post(new InfrastructureEvent.Restored(this.level, info));
         this.districtLine("restored", d, now);
      }
   }

   private void districtLine(String what, String district, long now) {
      String k = what + ":" + district;
      Long last = this.lastDistrictLine.get(k);
      if (last != null && now - last < PowerRules.DISTRICT_LINE_COOLDOWN || now - this.lastLine < 100) {
         return;
      }
      this.lastDistrictLine.put(k, now);
      this.say(what + SEP + district, false);
   }

   // ------------------------------------------------------------------ restoration

   private void restoreDue(long now) {
      boolean emergency = this.alarm.stage().emergency();
      for (Station st : this.stations.values()) {
         if (st.tripped && now >= st.restoreAt) {
            if (now - st.lastHit < 400 || emergency && st.depth == 0 && now - st.lastHit < 1200) {
               st.restoreAt = now + 200; // still under fire: the crews wait
               continue;
            }
            st.tripped = false;
            st.health = Math.max(st.health, 0.85);
            this.restoreStation(st.key, now, 10);
         } else if (!st.tripped && now - st.lastHit > 600 && st.health < 1.0) {
            st.health = Math.min(1.0, st.health + 0.02);
         }
      }
      this.stations.values().removeIf(st -> !st.tripped && st.health >= 1.0);
      int started = 0;
      for (Cell c : this.cells.values()) {
         if (started >= 6) {
            break;
         }
         if (c.state.dark() && c.restoreAt > 0 && now >= c.restoreAt) {
            Station st = this.station(c.key);
            if (st.tripped) {
               continue;
            }
            if (now - st.lastHit < 300) {
               c.restoreAt = now + 200;
               continue;
            }
            c.restoreAt = Long.MAX_VALUE; // scheduled
            this.schedule(now + started * (long)PowerRules.RESTORE_STAGGER, RESTORE, c.key, 0, c.incident, 0, 0, 0);
            started++;
         }
      }
   }

   private void restoreStation(long stationKey, long now, long delay) {
      long[] ks = this.cellsOfStation(stationKey);
      int k = 0;
      for (long key : ks) {
         Cell c = this.cells.get(key);
         if (c != null && c.state == State.DARK) {
            // tripped cells come back with their substation; a blown transformer keeps its own repair time
            c.restoreAt = Long.MAX_VALUE;
            this.schedule(now + delay + k++ * (long)PowerRules.RESTORE_STAGGER, RESTORE, key, 0, c.incident, 0, 0, 0);
         }
      }
   }

   void onAlarmChanged(AlarmStage prev, AlarmStage next) {
      if (next == AlarmStage.ALL_CLEAR) {
         // the all-clear: the crews go out everywhere, substation by substation
         long now = this.now();
         List<Station> tripped = new ArrayList<>();
         for (Station st : this.stations.values()) {
            if (st.tripped) {
               tripped.add(st);
            }
         }
         tripped.sort((a, b) -> Long.compare(a.restoreAt, b.restoreAt));
         int i = 0;
         for (Station st : tripped) {
            st.restoreAt = Math.min(st.restoreAt, now + 80 + i++ * 120L);
            st.lastHit = Math.min(st.lastHit, now - 1200);
            st.depth = Math.max(st.depth, 1);
         }
         int j = 0;
         for (Cell c : this.cells.values()) {
            if (c.state.dark() && c.restoreAt != Long.MAX_VALUE) {
               long base = c.state == State.DAMAGED ? 600 : 160;
               c.restoreAt = Math.min(c.restoreAt, now + base + j++ * 24L);
            }
         }
      }
   }

   /** Operator: power back everywhere now, substation by substation. */
   public int restoreAll() {
      long now = this.now();
      int n = 0;
      for (Station st : this.stations.values()) {
         st.tripped = false;
         st.health = 1.0;
         st.lastHit = now - 2000;
      }
      int k = 0;
      for (Cell c : this.cells.values()) {
         if (c.state.dark() || c.state == State.FAILING) {
            if (c.state == State.FAILING) {
               this.enter(c, c.after, now);
            }
            c.restoreAt = Long.MAX_VALUE;
            this.schedule(now + 5 + k++ * 6L, RESTORE, c.key, 0, c.incident, 0, 0, 0);
            n++;
         }
      }
      this.timeline.removeIf(a -> a.kind != RESTORE);
      this.relightLedger();
      return n;
   }

   /** Every chunk the ledger still has dark fixtures in: light them again where their cell has power. */
   private void relightLedger() {
      for (long ck : this.fixtures.ledgerChunks()) {
         long cell = this.cellOfChunk(ChunkPos.getX(ck), ChunkPos.getZ(ck));
         Cell c = this.cells.get(cell);
         if (c == null || !c.state.dark()) {
            this.fixtures.want(cell, true);
         }
      }
   }

   /** Operator: trip the substation around a point (with its cascade). */
   public void tripAt(double x, double z, float severity) {
      int ix = PowerRules.cellIndex((int)Math.floor(x), this.originX), iz = PowerRules.cellIndex((int)Math.floor(z), this.originZ);
      if (!this.inGrid(ix, iz) || this.paused()) {
         return;
      }
      long now = this.now();
      InfrastructureIncident inc = new InfrastructureIncident(this.nextIncident++, this.level.dimension(), x, this.surfaceY((int)x, (int)z), z, "command",
         "command:cascade", 0.0F, severity, now);
      this.incidents.put(inc.id(), inc);
      this.recent.addFirst(inc);
      while (this.recent.size() > 16) {
         this.recent.removeLast();
      }
      this.schedule(now, TRIP, this.station(PowerRules.key(ix, iz)).key, severity, inc.id(), 0, x, z);
   }

   // ------------------------------------------------------------------ world events

   /** A chunk of the city loaded: rescan it if its cell is dark, relight it if the ledger has it and power is back. */
   public void onChunkLoad(int cx, int cz) {
      long ck = ChunkPos.asLong(cx, cz);
      long cell = this.cellOfChunk(cx, cz);
      if (!this.inGrid(PowerRules.keyX(cell), PowerRules.keyZ(cell))) {
         return;
      }
      Cell c = this.cells.get(cell);
      boolean dark = c != null && (c.state.dark() || !c.shownLit);
      if (dark) {
         this.index.forget(ck);
         this.index.request(ck);
         this.fixtures.want(cell, false);
      } else if (this.fixtures.hasChunk(ck)) {
         this.fixtures.want(cell, true);
      } else if (this.index.scanned(ck)) {
         this.index.forget(ck);
      }
   }

   /** /city reset: the journal puts every original back (the fixtures were journaled); the grid starts fresh. */
   public void onCityReset() {
      this.timeline.clear();
      this.cells.clear();
      this.stations.clear();
      this.animating.clear();
      this.districtDark.clear();
      this.darkCount = 0;
      this.index.clear();
      this.fixtures.clear();
      this.fixtures.save(true);
      this.alarm.clear();
      this.dirty = true;
   }

   public void save() {
      this.fixtures.save(false);
   }

   public void shutdown() {
      this.fixtures.save(true);
   }

   private void prune(long now) {
      this.lastBurst.long2LongEntrySet().removeIf(e -> now - e.getLongValue() > 1200);
      this.incidents.values().removeIf(i -> now - i.gameTime() > 12_000);
      this.tripsByIncident.keySet().removeIf(id -> !this.incidents.containsKey(id));
   }

   // ------------------------------------------------------------------ clients

   List<ServerPlayer> audience() {
      CityProfile p = this.city.profile();
      List<ServerPlayer> out = new ArrayList<>();
      for (ServerPlayer sp : this.level.players()) {
         if (sp.getX() > p.minX - AUDIENCE_MARGIN && sp.getX() < p.maxX + AUDIENCE_MARGIN && sp.getZ() > p.minZ - AUDIENCE_MARGIN
            && sp.getZ() < p.maxZ + AUDIENCE_MARGIN) {
            out.add(sp);
         }
      }
      return out;
   }

   void say(String fragment, boolean urgent) {
      this.lastLine = this.now();
      int cut = fragment.indexOf(SEP);
      String id = cut < 0 ? fragment : fragment.substring(0, cut);
      HeroSpeech.announce(this.audience(), null, VOICE, LINE + id, SPEAKER, List.of(LINE + fragment),
         urgent ? HeroLine.Priority.ULTIMATE : HeroLine.Priority.AMBIENT);
   }

   private void fx(byte kind, double x, double y, double z, float power, int seed) {
      CityPowerS2CPacket p = CityPowerS2CPacket.fx(kind, x, y, z, power, seed);
      double r2 = FX_RANGE * FX_RANGE;
      for (ServerPlayer sp : this.level.players()) {
         if (sp.distanceToSqr(x, y, z) < r2) {
            CoreMessages.sendToPlayer(p, sp);
         }
      }
   }

   private void sync(long now) {
      List<ServerPlayer> audience = null;
      if (now % 20 == 0) {
         audience = this.audience();
         Set<UUID> present = new HashSet<>();
         List<ServerPlayer> fresh = new ArrayList<>();
         for (ServerPlayer sp : audience) {
            present.add(sp.getUUID());
            if (!this.synced.contains(sp.getUUID())) {
               fresh.add(sp);
            }
         }
         this.synced.retainAll(present);
         if (!fresh.isEmpty() && !(this.dirty && now - this.lastSync >= 10)) {
            CityPowerS2CPacket p = this.statePacket(now);
            for (ServerPlayer sp : fresh) {
               CoreMessages.sendToPlayer(p, sp);
               this.synced.add(sp.getUUID());
            }
         }
      }
      if (this.dirty && now - this.lastSync >= 10 || now - this.lastSync >= 600) {
         this.dirty = false;
         this.lastSync = now;
         if (audience == null) {
            audience = this.audience();
         }
         if (audience.isEmpty()) {
            return;
         }
         CityPowerS2CPacket p = this.statePacket(now);
         for (ServerPlayer sp : audience) {
            CoreMessages.sendToPlayer(p, sp);
            this.synced.add(sp.getUUID());
         }
      }
   }

   private CityPowerS2CPacket statePacket(long now) {
      LongArrayList packed = new LongArrayList();
      LongArrayList transformers = new LongArrayList();
      LongArrayList emergency = new LongArrayList();
      for (Cell c : this.cells.values()) {
         if (c.state == State.POWERED) {
            continue;
         }
         packed.add(CityPowerS2CPacket.packCell(c.ix, c.iz, c.state.ordinal()));
         if (c.state == State.DAMAGED && c.transformer != null && transformers.size() < CityPowerS2CPacket.MAX_TRANSFORMERS) {
            transformers.add(c.transformer.asLong());
         }
         if (c.state.dark()) {
            for (long e : c.emergency) {
               if (emergency.size() < CityPowerS2CPacket.MAX_EMERGENCY) {
                  emergency.add(e);
               }
            }
         }
      }
      List<BlockPos> sites = this.alarm.stage() == AlarmStage.NONE ? List.of() : this.alarm.sites();
      long[] s = new long[sites.size()];
      for (int i = 0; i < s.length; i++) {
         s[i] = sites.get(i).asLong();
      }
      List<CityAlarm.Lease> foci = this.alarm.foci();
      long[] f = new long[foci.size()];
      byte[] fs = new byte[foci.size()];
      for (int i = 0; i < f.length; i++) {
         f[i] = BlockPos.containing(foci.get(i).at()).asLong();
         fs[i] = (byte)foci.get(i).stage().ordinal();
      }
      String cause = CityAlarm.cause(this.alarm.reason());
      byte causeId = (byte)("meteor".equals(cause) ? 1 : "chitauri".equals(cause) ? 2 : 0);
      return CityPowerS2CPacket.state(this.originX, this.originZ, packed.toLongArray(), transformers.toLongArray(), emergency.toLongArray(),
         (byte)this.alarm.stage().ordinal(), (int)Math.min(Integer.MAX_VALUE, now - this.alarm.stageSince()), causeId, s, f, fs, this.darkFraction());
   }

   // ------------------------------------------------------------------ status

   /** Counters for /city power status: cells by state, tripped substations, ledger, scans, queue. */
   public Map<String, Long> stats() {
      Map<String, Long> m = new java.util.LinkedHashMap<>();
      for (State s : State.values()) {
         m.put(s.name().toLowerCase(java.util.Locale.ROOT), 0L);
      }
      for (Cell c : this.cells.values()) {
         m.merge(c.state.name().toLowerCase(java.util.Locale.ROOT), 1L, Long::sum);
      }
      m.put("cells", (long)this.cellCount());
      m.put("stations_tripped", this.stations.values().stream().filter(st -> st.tripped).count());
      m.put("stations_worn", (long)this.stations.size());
      m.put("timeline", (long)this.timeline.size());
      m.put("timeline_dropped", this.droppedActions);
      m.put("ledger", (long)this.fixtures.size());
      m.put("sweeps", (long)this.fixtures.sweeps());
      m.put("writes", this.fixtures.writes());
      m.put("scanned_chunks", (long)this.index.chunkCount());
      m.put("fixtures_indexed", this.index.fixtureCount());
      m.put("scan_queue", (long)this.index.pending());
      return m;
   }
}
