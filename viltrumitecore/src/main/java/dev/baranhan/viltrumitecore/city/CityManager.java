package dev.baranhan.viltrumitecore.city;

import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.city.danger.DangerMap;
import dev.baranhan.viltrumitecore.city.profile.CityProfile;
import dev.baranhan.viltrumitecore.city.profile.WalkGrid;
import dev.baranhan.viltrumitecore.city.restore.ChangeJournal;
import dev.baranhan.viltrumitecore.city.restore.RestoreQueue;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The living city of the running world (at most one): its prepared profile and
 * pedestrian grid, the danger map, the change journal with the restore queue, the
 * runtime switch and the citizen population. Created when the profile's dimension loads
 * in a world that ships {@code viltrumite_city/profile.json}; absent everywhere else.
 */
public final class CityManager {
   static final Logger LOG = LoggerFactory.getLogger("viltrumitecore/city");
   private static volatile CityManager active;
   private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "viltrumite-city-io");
      t.setDaemon(true);
      return t;
   });

   public static final double MAX_DENSITY = 2.5;
   public static final int ABSOLUTE_MAX = 300;

   final MinecraftServer server;
   final ServerLevel level;
   final Path folder;
   final CityProfile profile;
   final WalkGrid grid;
   final DangerMap danger = new DangerMap();
   final ChangeJournal journal;
   final RestoreQueue restore;
   final CityPopulation population;
   final Set<CitizenEntity> citizens = new LinkedHashSet<>();
   boolean enabled;
   double density;
   private final Long2ObjectOpenHashMap<double[]> bursts = new Long2ObjectOpenHashMap<>();
   private long lastSave;
   private long saveRequestedAt = -1;
   private int resumeIn = -1;

   private CityManager(MinecraftServer server, ServerLevel level, Path folder, CityProfile profile, ChangeJournal journal) {
      this.server = server;
      this.level = level;
      this.folder = folder;
      this.profile = profile;
      this.grid = new WalkGrid(folder.resolve("walk"));
      this.journal = journal;
      this.density = profile.defaultDensity;
      this.restore = new RestoreQueue(journal, this::restoreReport);
      this.population = new CityPopulation(this);
      this.loadState();
   }

   // ------------------------------------------------------------------ lookup

   @Nullable
   public static CityManager active() {
      return active;
   }

   /** The city of this level, or null (client levels, other dimensions, worlds without a city). */
   @Nullable
   public static CityManager of(Level level) {
      CityManager m = active;
      return m != null && m.level == level ? m : null;
   }

   public static void track(CitizenEntity c, boolean add) {
      CityManager m = active;
      if (m == null || m.level != c.level()) {
         return;
      }

      if (add) {
         m.citizens.add(c);
      } else {
         m.citizens.remove(c);
      }
   }

   public CityProfile profile() {
      return this.profile;
   }

   public WalkGrid grid() {
      return this.grid;
   }

   public DangerMap dangerMap() {
      return this.danger;
   }

   public ChangeJournal journal() {
      return this.journal;
   }

   public RestoreQueue restoreQueue() {
      return this.restore;
   }

   public ServerLevel level() {
      return this.level;
   }

   public long now() {
      return this.level.getGameTime();
   }

   public boolean enabled() {
      return this.enabled;
   }

   public double density() {
      return this.density;
   }

   public List<CitizenEntity> citizens() {
      return Collections.unmodifiableList(new ArrayList<>(this.citizens));
   }

   public int citizenCount() {
      return this.citizens.size();
   }

   public void danger(double x, double y, double z, float radius, int lifetime) {
      this.danger.add(x, y, z, radius, this.now(), lifetime);
   }

   // ------------------------------------------------------------------ lifecycle

   static void onLevelLoad(ServerLevel level) {
      MinecraftServer server = level.getServer();
      Path folder = server.getWorldPath(LevelResource.ROOT).resolve(CityProfile.FOLDER);
      CityProfile profile;
      try {
         profile = CityProfile.load(server.getWorldPath(LevelResource.ROOT));
      } catch (IOException e) {
         LOG.error("City profile unreadable, the living city is off: {}", e.getMessage());
         return;
      }

      if (profile == null) {
         return;
      }

      ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(profile.dimension));
      if (!level.dimension().equals(dim)) {
         return;
      }

      ChangeJournal journal;
      try {
         journal = ChangeJournal.read(folder.resolve(ChangeJournal.FILE), level.holderLookup(Registries.BLOCK));
      } catch (IOException | RuntimeException e) {
         LOG.error("City change journal unreadable ({}); keeping it aside and starting a new one", e.toString());
         try {
            Files.move(folder.resolve(ChangeJournal.FILE), folder.resolve(ChangeJournal.FILE + ".broken-" + System.currentTimeMillis()));
         } catch (IOException ignored) {
         }

         journal = new ChangeJournal();
      }

      CityManager m = new CityManager(server, level, folder, profile, journal);
      active = m;
      LOG.info("Living city '{}' ({}) ready: {} districts, {} walk cells, journal {} blocks in {} chunks{}", profile.name, profile.mapVersion,
         profile.districts.size(), profile.walkCells, journal.size(), journal.chunkCount(), journal.restoring ? ", reset will resume" : "");
      if (journal.restoring && !journal.isEmpty()) {
         m.resumeIn = 100;
      }
   }

   static void onLevelUnload(ServerLevel level) {
      CityManager m = active;
      if (m != null && m.level == level) {
         m.shutdown();
      }
   }

   static void onServerStopping() {
      CityManager m = active;
      if (m != null) {
         m.shutdown();
      }
   }

   private void shutdown() {
      if (this.restore.running()) {
         this.restore.abort();
         this.journal.restoring = true; // resume after the next start
      }

      this.saveJournal(true);
      this.saveState();
      this.citizens.clear();
      active = null;
   }

   // ------------------------------------------------------------------ journal hook

   /** Called from the LevelChunk.setBlockState hook before the change is applied. */
   public static void onSetBlock(Level level, LevelChunk chunk, BlockPos pos, BlockState next) {
      CityManager m = active;
      if (m == null || m.level != level || ChangeJournal.suppressed || !m.server.isSameThread()) {
         return;
      }

      if (!m.profile.inArena(pos.getX(), pos.getY(), pos.getZ())) {
         return;
      }

      BlockState old = chunk.getBlockState(pos);
      if (old == next) {
         return;
      }

      boolean sameBlock = old.getBlock() == next.getBlock();
      if (sameBlock && ambientOnly(old, next)) {
         // daylight detectors, redstone power, leaf distance, crop growth...: the map living on its own
         return;
      }

      if (!sameBlock) {
         // only real block replacements count as destruction for the danger heuristic
         long ck = ChangeJournal.chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
         double[] b = m.bursts.get(ck);
         if (b == null) {
            b = new double[4];
            m.bursts.put(ck, b);
         }

         b[0] += pos.getX();
         b[1] += pos.getY();
         b[2] += pos.getZ();
         b[3]++;
      }

      if (m.journal.contains(pos)) {
         return;
      }

      CompoundTag be = null;
      if (old.hasBlockEntity()) {
         BlockEntity e = chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
         if (e != null) {
            try {
               be = e.saveWithFullMetadata();
            } catch (RuntimeException ex) {
               be = null;
            }
         }
      }

      m.journal.record(pos.immutable(), old, be);
   }

   /** Properties that change by themselves (time of day, redstone, growth, leaf decay distance). */
   private static final Set<String> AMBIENT = Set.of("power", "powered", "lit", "distance", "moisture", "age", "triggered",
      "enabled", "signal_fire", "snowy", "stage", "bites", "level", "honey_level", "eggs", "hatch", "berries", "pickles",
      "sculk_sensor_phase", "bloom", "shrieking", "can_summon", "note", "instrument", "tilt", "has_record", "has_book");

   /** True when every property that differs between the two states of one block is an ambient one. */
   static boolean ambientOnly(BlockState old, BlockState next) {
      for (Map.Entry<Property<?>, Comparable<?>> e : old.getValues().entrySet()) {
         if (!e.getValue().equals(next.getValue(e.getKey())) && !AMBIENT.contains(e.getKey().getName())) {
            return false;
         }
      }

      return true;
   }

   // ------------------------------------------------------------------ tick

   void tick() {
      long now = this.now();
      if (!this.bursts.isEmpty()) {
         for (double[] b : this.bursts.values()) {
            if (b[3] >= 6) {
               // many blocks changed in one chunk within one tick: destruction is happening there
               this.danger.add(b[0] / b[3], b[1] / b[3], b[2] / b[3], DangerMap.EXPLOSION_RADIUS, now, DangerMap.DEFAULT_LIFETIME);
            }
         }

         this.bursts.clear();
      }

      if (now % 20 == 0) {
         this.danger.prune(now);
      }

      if (this.resumeIn > 0 && --this.resumeIn == 0) {
         this.broadcastOps(Component.translatable("viltrumitecore.city.reset.resumed", this.journal.size()).withStyle(ChatFormatting.YELLOW));
         this.restore.start(now);
      }

      this.restore.tick(this.level, now);
      this.population.tick(now);
      long interval = this.journal.size() > 500_000 ? 6000 : 1200;
      if (this.journal.dirty() && (now - this.lastSave > interval || this.saveRequestedAt >= 0 && now - this.saveRequestedAt > 20)) {
         this.saveJournal(false);
      }
   }

   /** A journaled chunk is unloading (its blocks go to disk): persist the journal soon. */
   void onChunkUnload(int cx, int cz) {
      if (this.journal.dirty() && this.journal.hasChunk(ChangeJournal.chunkKey(cx, cz)) && this.saveRequestedAt < 0) {
         this.saveRequestedAt = this.now();
      }
   }

   void saveJournal(boolean sync) {
      this.lastSave = this.now();
      this.saveRequestedAt = -1;
      CompoundTag snap = this.journal.save();
      Path file = this.folder.resolve(ChangeJournal.FILE);
      Runnable w = () -> {
         try {
            ChangeJournal.write(snap, file);
         } catch (IOException e) {
            LOG.error("City journal save failed: {}", e.toString());
         }
      };
      if (sync) {
         try {
            // single-threaded executor: earlier queued saves finish first
            IO.submit(w).get(60, TimeUnit.SECONDS);
         } catch (Exception e) {
            LOG.error("City journal save did not finish: {}", e.toString());
         }
      } else {
         IO.submit(w);
      }
   }

   // ------------------------------------------------------------------ state

   private Path statePath() {
      return this.folder.resolve("state.json");
   }

   /** Only the density survives a restart; the city always starts disabled. */
   private void loadState() {
      Path f = this.statePath();
      if (!Files.isRegularFile(f)) {
         return;
      }

      try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
         com.google.gson.JsonObject o = com.google.gson.JsonParser.parseReader(r).getAsJsonObject();
         if (o.has("density")) {
            this.density = clampDensity(o.get("density").getAsDouble());
         }
      } catch (IOException | RuntimeException e) {
         LOG.warn("City state unreadable: {}", e.toString());
      }
   }

   void saveState() {
      com.google.gson.JsonObject o = new com.google.gson.JsonObject();
      o.addProperty("density", this.density);
      try {
         Files.createDirectories(this.folder);
         try (Writer w = Files.newBufferedWriter(this.statePath(), StandardCharsets.UTF_8)) {
            w.write(o.toString());
         }
      } catch (IOException e) {
         LOG.warn("City state save failed: {}", e.toString());
      }
   }

   public static double clampDensity(double d) {
      return Double.isNaN(d) ? 1.0 : Math.max(0.0, Math.min(MAX_DENSITY, d));
   }

   void setEnabled(boolean on) {
      this.enabled = on;
      if (!on) {
         this.population.clearAll();
      }
   }

   void setDensity(double d) {
      this.density = clampDensity(d);
      this.saveState();
   }

   // ------------------------------------------------------------------ reset

   void startReset() {
      this.population.clearAll();
      this.restore.start(this.now());
      this.saveJournal(false);
   }

   private void restoreReport(String msg) {
      String[] p = msg.split(":");
      if (p.length >= 6 && p[0].equals("done")) {
         this.broadcastOps(Component.translatable("viltrumitecore.city.reset.done", p[1], p[2], p[3], p[4]).withStyle(ChatFormatting.GREEN));
         if (!"0".equals(p[5])) {
            this.broadcastOps(Component.translatable("viltrumitecore.city.reset.leftover", p[5]).withStyle(ChatFormatting.YELLOW));
         }
      }

      this.saveJournal(false);
   }

   void broadcastOps(Component c) {
      for (var p : this.server.getPlayerList().getPlayers()) {
         if (p.hasPermissions(2)) {
            p.sendSystemMessage(c);
         }
      }

      LOG.info(c.getString());
   }
}
