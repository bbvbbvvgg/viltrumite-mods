package dev.baranhan.viltrumitecore.world.collapse;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import dev.baranhan.viltrumitecore.hero.HeroDestruction;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.CityCollapseS2CPacket;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server side of structural collapses: turns settled ledger damage into analysed plans and runs each accepted
 * plan through its four phases (impact, structural failure, collapse, aftermath). All block writes go through
 * {@link HeroDestruction} rules (mobGriefing, unbreakable blocks), loaded chunks only, inside
 * {@link StructuralDamage#suppressed}, so the city journal records them for restoration and they never feed
 * another collapse. Limits: active collapses, blocks per collapse, blocks per tick, analysis box, region
 * cooldown. Memory only: a restart forgets running collapses (blocks already changed stay journaled).
 */
public final class CollapseDirector {
   private static final Logger LOGGER = LoggerFactory.getLogger("viltrumitecore/collapse");
   /** Debug/QA: log every analysis and phase with its cost ({@code -Dviltrumitecore.collapse.log=true}). */
   public static final boolean LOG = Boolean.getBoolean("viltrumitecore.collapse.log");

   private static final List<Active> ACTIVE = new ArrayList<>();
   private static final List<Region> RECENT = new ArrayList<>();
   private static long nextId = 1L + (System.nanoTime() & 0xFFFFL) * 1000L;
   private static int budgetLeft;
   private static long budgetTick = Long.MIN_VALUE;
   // QA metrics
   static long lastAnalysisNanos, maxAnalysisNanos, lastTickNanos, maxTickNanos;
   static int analyses, accepted, rejected, started;
   static String lastReason = "-";

   private record Region(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim, double x, double y, double z, long until) {
   }

   private CollapseDirector() {
   }

   // ------------------------------------------------------------------ limits

   static int maxActive() {
      return Mth.clamp(ViltrumiteCoreConfig.INSTANCE.collapseMaxActive, 0, 8);
   }

   static int maxBlocks() {
      return Mth.clamp(ViltrumiteCoreConfig.INSTANCE.collapseMaxBlocks, CollapseRules.MIN_SECTION, 4000);
   }

   static int blocksPerTick() {
      return Mth.clamp(ViltrumiteCoreConfig.INSTANCE.collapseBlocksPerTick, 64, 4000);
   }

   public static boolean enabled() {
      return ViltrumiteCoreConfig.INSTANCE.collapseEnabled;
   }

   public static int activeCount() {
      return ACTIVE.size();
   }

   // ------------------------------------------------------------------ tick

   /** Server level tick (END). */
   public static void tick(ServerLevel level) {
      long t0 = System.nanoTime();
      long now = level.getGameTime();
      DamageLedger ledger = StructuralDamage.ledger(level);
      if (now % 100L == 0L) {
         ledger.prune(now);
         RECENT.removeIf(r -> r.until < now);
      }
      if (enabled() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
         // at capacity the settled damage waits in the ledger (it expires with the damage window)
         List<DamageLedger.Candidate> found = countIn(level) < maxActive() && ACTIVE.size() < maxActive() ? ledger.settled(now, 1) : List.of();
         if (!found.isEmpty()) {
            DamageLedger.Candidate c = found.get(0);
            if (!cooling(level, c.x(), c.y(), c.z())) {
               tryStart(level, c.x(), c.y(), c.z(), c.score(), null, false);
            }
         }
      }
      Iterator<Active> it = ACTIVE.iterator();
      while (it.hasNext()) {
         Active a = it.next();
         if (a.level != level) {
            continue;
         }
         if (a.step(now)) {
            it.remove();
         }
      }
      long dt = System.nanoTime() - t0;
      lastTickNanos = dt;
      maxTickNanos = Math.max(maxTickNanos, dt);
   }

   private static int countIn(ServerLevel level) {
      int n = 0;
      for (Active a : ACTIVE) {
         if (a.level == level) {
            n++;
         }
      }
      return n;
   }

   static boolean cooling(ServerLevel level, double x, double y, double z) {
      for (Region r : RECENT) {
         if (r.dim == level.dimension() && (r.x - x) * (r.x - x) + (r.z - z) * (r.z - z) <= CollapseRules.REGION_RADIUS * CollapseRules.REGION_RADIUS
            && Math.abs(r.y - y) <= 24.0) {
            return true;
         }
      }
      for (Active a : ACTIVE) {
         if (a.level == level && a.box.inflate(4.0).contains(x + 0.5, y + 0.5, z + 0.5)) {
            return true;
         }
      }
      return false;
   }

   /** Runs the analysis at a point; starts the collapse unless {@code dryRun}. Returns the plan (or rejection). */
   public static CollapseAnalyzer.Plan tryStart(ServerLevel level, int x, int y, int z, float score, @Nullable CollapseCause hint, boolean dryRun) {
      long t0 = System.nanoTime();
      DamageLedger ledger = StructuralDamage.ledger(level);
      LongArrayList near = ledger.around(x, y, z, CollapseRules.MAX_HALF, 24, CollapseRules.MAX_UP);
      CollapseCause cause = hint != null ? hint : dominantCause(level, ledger, x, y, z);
      long seed = level.getGameTime() * 0x9E3779B97F4A7C15L ^ BlockKey.of(x, y, z) ^ level.random.nextLong();
      CollapseAnalyzer.Request req = new CollapseAnalyzer.Request(x, y, z, near.toLongArray(), score, cause, seed, maxBlocks(), level.getMinBuildHeight(),
         level.getMaxBuildHeight() - 1);
      CollapseAnalyzer.Plan plan = CollapseAnalyzer.analyze(StructuralDamage.volume(level), req);
      long dt = System.nanoTime() - t0;
      lastAnalysisNanos = dt;
      maxAnalysisNanos = Math.max(maxAnalysisNanos, dt);
      analyses++;
      lastReason = plan.reason();
      if (LOG) {
         LOGGER.info("collapse analysis at {} {} {}: {} ({} damaged, {} cells, {} blocks, {} ms)", x, y, z, plan.reason(), plan.damaged(), plan.cells(),
            plan.accepted() ? plan.blocks().length : 0, String.format("%.2f", dt / 1.0E6));
      }
      if (!plan.accepted()) {
         rejected++;
         return plan;
      }
      accepted++;
      if (!dryRun) {
         start(level, plan, cause, seed, ledger);
      }
      return plan;
   }

   /**
    * The cause with the most damage weight around the spot names the collapse (found in game: a hero strike and a
    * Leviathan cut in the same tick used to share the level-wide cause). Damage without a recorded cause falls back
    * to the strongest cause of the last {@link #CAUSE_MEMORY} ticks.
    */
   private static CollapseCause dominantCause(ServerLevel level, DamageLedger ledger, int x, int y, int z) {
      CollapseCause local = ledger.dominantCause(x, y, z, CollapseRules.MAX_HALF, 24, CollapseRules.MAX_UP);
      if (local != null) {
         return local;
      }
      Noted n = lastCause.get(level.dimension());
      return n != null && level.getGameTime() - n.tick <= CAUSE_MEMORY ? n.cause : CollapseCause.HERO;
   }

   private record Noted(CollapseCause cause, long tick) {
   }

   static final int CAUSE_MEMORY = 400;
   private static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Noted> lastCause = new java.util.HashMap<>();

   /** Remembers the strongest recent cause per level; a weaker one replaces it once it is old. */
   static void noteCause(ServerLevel level, CollapseCause cause) {
      long now = level.getGameTime();
      Noted prev = lastCause.get(level.dimension());
      if (prev == null || cause.weight() >= prev.cause.weight() || now - prev.tick > CAUSE_MEMORY / 2) {
         if (prev == null || prev.cause != cause || prev.tick != now) {
            lastCause.put(level.dimension(), new Noted(cause, now));
         }
      }
   }

   private static void start(ServerLevel level, CollapseAnalyzer.Plan plan, CollapseCause cause, long seed, DamageLedger ledger) {
      long id = nextId++;
      List<BlockPos> planned = new ArrayList<>(plan.blocks().length);
      for (long k : plan.blocks()) {
         planned.add(BlockPos.of(k));
      }
      int cx = (plan.minX() + plan.maxX()) / 2, cy = (plan.minY() + plan.maxY()) / 2, cz = (plan.minZ() + plan.maxZ()) / 2;
      BlockPos center = new BlockPos(cx, cy, cz);
      int height = plan.maxY() - plan.minY() + 1;
      int fall = CollapseRules.impactTicks(plan.variant(), plan.minY() - plan.groundY() - 1, height);
      double spread = Math.min(8.0, 1.5 + height * 0.35) * (plan.variant() == CollapseVariant.PANCAKE ? 0.4 : 1.0);
      double lx0 = Math.min(plan.minX(), plan.minX() + plan.fallX() * spread) - 1.5, lx1 = Math.max(plan.maxX() + 1, plan.maxX() + 1 + plan.fallX() * spread) + 1.5;
      double lz0 = Math.min(plan.minZ(), plan.minZ() + plan.fallZ() * spread) - 1.5, lz1 = Math.max(plan.maxZ() + 1, plan.maxZ() + 1 + plan.fallZ() * spread) + 1.5;
      AABB landing = new AABB(lx0, plan.groundY() - 1.0, lz0, lx1, plan.groundY() + 5.0, lz1);
      float radius = (float)Math.max(plan.radius() + spread, 0.5 * Math.hypot(lx1 - lx0, lz1 - lz0)) + 2.0F;
      CollapseInfo info = new CollapseInfo(id, level.dimension(), center, radius, plan.severity(), cause, planned, Map.of(), level.getGameTime(), plan.variant(),
         landing, plan.truncated());
      ledger.consume(plan.minX() - 4, plan.minY() - 30, plan.minZ() - 4, plan.maxX() + 4, plan.maxY() + 4, plan.maxZ() + 4);
      RECENT.add(new Region(level.dimension(), cx, cy, cz, level.getGameTime() + CollapseRules.REGION_COOLDOWN));
      if (MinecraftForge.EVENT_BUS.post(new CityCollapseEvent.Started(level, info))) {
         if (LOG) {
            LOGGER.info("collapse {} cancelled by a listener", id);
         }
         return;
      }
      started++;
      Active a = new Active(level, plan, info, seed, fall);
      ACTIVE.add(a);
      a.sendStart();
      CityManager city = CityManager.of(level);
      if (city != null) {
         city.danger(cx + 0.5, plan.groundY() + 1.0, cz + 0.5, radius + 10.0F, 460);
         city.blast(cx + 0.5, plan.groundY() + 1.0, cz + 0.5, Math.min(24.0F, radius));
      }
      if (LOG) {
         LOGGER.info("collapse {} started: {} {} {} blocks, cause {}, rule {}, creak {} t, fall {} t, radius {}", id, plan.variant(), plan.severity(),
            plan.blocks().length, cause.id(), plan.rule(), a.creak, fall, String.format("%.1f", radius));
      }
   }

   /** Aborts every collapse of a level (unload) or all (server stop): Finished(aborted) is posted for each. */
   public static void abortAll(@Nullable ServerLevel level) {
      Iterator<Active> it = ACTIVE.iterator();
      while (it.hasNext()) {
         Active a = it.next();
         if (level == null || a.level == level) {
            a.finish(true);
            it.remove();
         }
      }
      if (level == null) {
         RECENT.clear();
         lastCause.clear();
      }
   }

   private static int takeBudget(ServerLevel level, int want) {
      long now = level.getServer().getTickCount();
      if (now != budgetTick) {
         budgetTick = now;
         budgetLeft = blocksPerTick();
      }
      int got = Math.min(want, budgetLeft);
      budgetLeft -= got;
      return got;
   }

   static String status() {
      return String.format("collapses: %d active (max %d), started %d, analyses %d (accepted %d, rejected %d, last: %s); analysis %.2f ms (max %.2f), tick %.2f ms (max %.2f); limits %d blocks/collapse, %d blocks/tick",
         ACTIVE.size(), maxActive(), started, analyses, accepted, rejected, lastReason, lastAnalysisNanos / 1.0E6, maxAnalysisNanos / 1.0E6,
         lastTickNanos / 1.0E6, maxTickNanos / 1.0E6, maxBlocks(), blocksPerTick());
   }

   static List<String> describeActive() {
      List<String> out = new ArrayList<>();
      for (Active a : ACTIVE) {
         out.add(String.format("#%d %s %s %s at %s: age %d, removed %d/%d", a.info.eventId(), a.info.variant(), a.info.severity(), a.info.cause().id(),
            a.info.center().toShortString(), a.age, a.removed.size(), a.plan.blocks().length));
      }
      return out;
   }

   static void resetMetrics() {
      maxAnalysisNanos = 0;
      maxTickNanos = 0;
   }

   // ------------------------------------------------------------------ one collapse

   private static final class Active {
      final ServerLevel level;
      final CollapseAnalyzer.Plan plan;
      final CollapseInfo info;
      final long seed;
      final int creak, collapseAt, fallTicks, impactAt, endAt;
      final AABB box;
      final Vec3 center;
      final long[] shed;
      final LinkedHashMap<BlockPos, BlockState> removed = new LinkedHashMap<>();
      final LinkedHashMap<BlockPos, BlockState> changed = new LinkedHashMap<>();
      int age;
      int shedDone;
      int mainCursor;
      boolean collapsedPosted;
      boolean impactDone;

      Active(ServerLevel level, CollapseAnalyzer.Plan plan, CollapseInfo info, long seed, int fallTicks) {
         this.level = level;
         this.plan = plan;
         this.info = info;
         this.seed = seed;
         this.creak = Math.max(4, CollapseRules.creakTicks(plan.variant(), plan.severity(), seed));
         this.collapseAt = CollapseRules.IMPACT_TICKS + this.creak;
         this.fallTicks = fallTicks;
         this.impactAt = this.collapseAt + fallTicks;
         this.endAt = this.impactAt + CollapseRules.SETTLE_PHASE_TICKS;
         this.box = new AABB(plan.minX(), plan.minY(), plan.minZ(), plan.maxX() + 1, plan.maxY() + 1, plan.maxZ() + 1);
         this.center = this.box.getCenter();
         this.shed = pickShed(level, plan, seed, CollapseRules.shedCount(plan.blocks().length, this.creak));
      }

      /** Exposed section blocks (an air neighbour outside the section), seeded order, for phase 2 shedding. */
      private static long[] pickShed(ServerLevel level, CollapseAnalyzer.Plan plan, long seed, int count) {
         it.unimi.dsi.fastutil.longs.LongOpenHashSet in = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(plan.blocks());
         LongArrayList exposed = new LongArrayList();
         BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
         for (long k : plan.blocks()) {
            int x = BlockKey.x(k), y = BlockKey.y(k), z = BlockKey.z(k);
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
               if (d == net.minecraft.core.Direction.UP) {
                  continue;
               }
               long n = BlockKey.of(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
               p.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
               if (!in.contains(n) && level.isLoaded(p) && level.getBlockState(p).isAir()) {
                  exposed.add(k);
                  break;
               }
            }
         }
         long[] arr = exposed.toLongArray();
         // seeded shuffle, then keep the first `count`
         for (int i = arr.length - 1; i > 0; i--) {
            int j = (int)(CollapseAnalyzer.hash(seed, i, 7, arr.length) % (i + 1));
            long t = arr[i];
            arr[i] = arr[j];
            arr[j] = t;
         }
         return java.util.Arrays.copyOf(arr, Math.min(count, arr.length));
      }

      void sendStart() {
         int sx = this.plan.maxX() - this.plan.minX() + 1, sy = this.plan.maxY() - this.plan.minY() + 1, sz = this.plan.maxZ() - this.plan.minZ() + 1;
         long[] bits = new long[(sx * sy * sz + 63) >>> 6];
         for (long k : this.plan.blocks()) {
            int i = CityCollapseS2CPacket.bitIndex(BlockKey.x(k) - this.plan.minX(), BlockKey.y(k) - this.plan.minY(), BlockKey.z(k) - this.plan.minZ(), sx, sz);
            bits[i >>> 6] |= 1L << (i & 63);
         }
         for (ServerPlayer player : this.level.players()) {
            double d = player.position().distanceTo(this.center);
            if (d <= CollapseRules.PACKET_RANGE) {
               CoreMessages.sendToPlayer(CityCollapseS2CPacket.start(this.info.eventId(), this.center, this.plan.severity().ordinal(), this.plan.variant().ordinal(),
                  this.seed, this.plan.fallX(), this.plan.fallZ(), this.plan.minX(), this.plan.minY(), this.plan.minZ(), sx, sy, sz, bits, this.creak,
                  this.collapseAt, this.fallTicks, this.plan.groundY(), d > CollapseRules.NEAR_RANGE), player);
            }
         }
      }

      void sendPhase(byte kind) {
         for (ServerPlayer player : this.level.players()) {
            double d = player.position().distanceTo(this.center);
            if (d <= CollapseRules.PACKET_RANGE) {
               CoreMessages.sendToPlayer(CityCollapseS2CPacket.phase(kind, this.info.eventId(), this.center, this.plan.severity().ordinal(), this.plan.groundY(),
                  d > CollapseRules.NEAR_RANGE), player);
            }
         }
      }

      /** One tick; true when finished. */
      boolean step(long now) {
         if (!this.level.isLoaded(BlockPos.containing(this.center))) {
            // the area unloaded under us: stop here, keep what changed
            this.finish(true);
            return true;
         }
         this.age++;
         if (this.age >= CollapseRules.IMPACT_TICKS && this.age < this.collapseAt) {
            this.shedStep();
         }
         if (this.age >= this.collapseAt && this.mainCursor < this.plan.blocks().length) {
            this.mainStep();
         }
         if (!this.collapsedPosted && this.mainCursor >= this.plan.blocks().length) {
            this.collapsedPosted = true;
            MinecraftForge.EVENT_BUS.post(new CityCollapseEvent.Collapsed(this.level, this.info.with(new ArrayList<>(this.removed.keySet()), this.removed,
               this.level.getGameTime())));
         }
         if (!this.impactDone && this.age >= this.impactAt && this.collapsedPosted) {
            this.impactDone = true;
            this.impact();
         }
         if (this.age >= this.endAt && this.impactDone) {
            this.finish(false);
            return true;
         }
         return false;
      }

      private void shedStep() {
         int span = Math.max(1, this.collapseAt - CollapseRules.IMPACT_TICKS);
         int due = Math.min(this.shed.length, (int)Math.ceil(this.shed.length * (double)(this.age - CollapseRules.IMPACT_TICKS + 1) / span));
         while (this.shedDone < due) {
            BlockPos pos = BlockPos.of(this.shed[this.shedDone++]);
            this.remove(pos);
         }
      }

      private void mainStep() {
         int n = takeBudget(this.level, this.plan.blocks().length - this.mainCursor);
         if (this.mainCursor == 0 && n > 0) {
            // the cue goes out in the same tick as the first batch, ahead of the block updates
            this.sendPhase(CityCollapseS2CPacket.COLLAPSE);
            CityManager city = CityManager.of(this.level);
            if (city != null) {
               city.danger(this.center.x, this.plan.groundY() + 1.0, this.center.z, this.info.radius() + 8.0F, 400);
            }
         }
         for (int i = 0; i < n; i++) {
            this.remove(BlockPos.of(this.plan.blocks()[this.mainCursor++]));
         }
      }

      private boolean remove(BlockPos pos) {
         if (this.removed.containsKey(pos) || !this.level.isLoaded(pos)) {
            return false;
         }
         BlockState state = this.level.getBlockState(pos);
         if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false; // already gone or flooded since the analysis
         }
         boolean[] ok = new boolean[1];
         StructuralDamage.suppressed(() -> ok[0] = HeroDestruction.clearBlock(this.level, pos));
         if (ok[0]) {
            this.removed.put(pos, state);
            this.changed.put(pos, state);
         }
         return ok[0];
      }

      private void impact() {
         AABB landing = this.info.landing();
         // impact damage: everything under the falling section and in the landing zone
         DamageSource source = new DamageSource(this.level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.FALLING_BLOCK));
         AABB hurt = landing.minmax(this.box.inflate(0.5));
         Vec3 lc = landing.getCenter();
         double reach = Math.max(2.0, 0.5 * Math.hypot(landing.getXsize(), landing.getZsize()));
         for (LivingEntity e : this.level.getEntitiesOfClass(LivingEntity.class, hurt, x -> x.isAlive() && !x.isSpectator())) {
            double share = Math.hypot(e.getX() - lc.x, e.getZ() - lc.z) / reach;
            if (e.hurt(source, CollapseRules.impactDamage(this.plan.severity(), share)) && HeroRegistry.allowsImpulse(e)) {
               Vec3 away = new Vec3(e.getX() - lc.x, 0.0, e.getZ() - lc.z);
               if (away.lengthSqr() > 1.0E-4) {
                  away = away.normalize();
                  e.knockback(0.5 + 0.6 * this.plan.severity().strength(), -away.x, -away.z);
               }
            }
         }
         StructuralDamage.suppressed(() -> {
            this.secondary(landing);
            this.rubble(landing);
         });
         this.sendPhase(CityCollapseS2CPacket.IMPACT);
         CityManager city = CityManager.of(this.level);
         if (city != null) {
            city.blast(lc.x, this.plan.groundY() + 1.0, lc.z, Math.min(28.0F, (float)reach + 6.0F));
            city.danger(lc.x, this.plan.groundY() + 1.0, lc.z, this.info.radius() + 6.0F, 600);
         }
         MinecraftForge.EVENT_BUS.post(new CityCollapseEvent.Impact(this.level, this.info.with(new ArrayList<>(this.changed.keySet()), this.changed,
            this.level.getGameTime())));
      }

      /** Glass and leaves at street level in the landing zone break (quietly; the client draws the shards). */
      private void secondary(AABB landing) {
         int done = 0;
         int y0 = this.plan.groundY(), y1 = this.plan.groundY() + 3;
         BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
         for (int x = Mth.floor(landing.minX); x <= Mth.floor(landing.maxX) && done < CollapseRules.SECONDARY_MAX; x++) {
            for (int z = Mth.floor(landing.minZ); z <= Mth.floor(landing.maxZ) && done < CollapseRules.SECONDARY_MAX; z++) {
               for (int y = y0; y <= y1 && done < CollapseRules.SECONDARY_MAX; y++) {
                  p.set(x, y, z);
                  if (!this.level.isLoaded(p) || this.box.contains(x + 0.5, y + 0.5, z + 0.5)) {
                     continue;
                  }
                  BlockState s = this.level.getBlockState(p);
                  if ((s.getSoundType() == SoundType.GLASS || s.is(net.minecraft.tags.BlockTags.LEAVES)) && HeroDestruction.clearBlock(this.level, p.immutable())) {
                     this.changed.put(p.immutable(), s);
                     done++;
                  }
               }
            }
         }
      }

      /** A few section blocks come to rest as rubble on the street (simple full blocks only). */
      private void rubble(AABB landing) {
         int want = CollapseRules.rubbleCount(this.removed.size());
         if (want <= 0) {
            return;
         }
         List<BlockState> palette = new ArrayList<>();
         for (BlockState s : this.removed.values()) {
            if (!s.hasBlockEntity() && !(s.getBlock() instanceof FallingBlock) && s.getSoundType() != SoundType.GLASS && s.isCollisionShapeFullBlock(this.level, BlockPos.ZERO)
               && s.getFluidState().isEmpty()) {
               palette.add(s);
               if (palette.size() >= 64) {
                  break;
               }
            }
         }
         if (palette.isEmpty()) {
            return;
         }
         int x0 = Mth.floor(landing.minX), z0 = Mth.floor(landing.minZ);
         int w = Math.max(1, Mth.floor(landing.maxX) - x0 + 1), d = Math.max(1, Mth.floor(landing.maxZ) - z0 + 1);
         int placed = 0;
         BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
         for (int tries = 0; tries < want * 4 && placed < want; tries++) {
            long h = CollapseAnalyzer.hash(this.seed, tries, 31, want);
            int x = x0 + (int)(h % w), z = z0 + (int)((h >>> 20) % d);
            p.set(x, this.plan.groundY(), z);
            if (!this.level.isLoaded(p)) {
               continue;
            }
            int top = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(top - (this.plan.groundY() + 1)) > 3) {
               continue; // not the street the section landed on (a roof, a pit)
            }
            p.set(x, top, z);
            if (!this.level.getBlockState(p).isAir() || this.box.contains(x + 0.5, top + 0.5, z + 0.5)) {
               continue;
            }
            BlockState s = palette.get((int)((h >>> 40) % palette.size()));
            BlockState old = this.level.getBlockState(p);
            if (this.level.setBlock(p.immutable(), s, Block.UPDATE_ALL)) {
               this.changed.put(p.immutable(), old);
               placed++;
            }
         }
      }

      void finish(boolean aborted) {
         this.sendPhase(CityCollapseS2CPacket.END);
         MinecraftForge.EVENT_BUS.post(new CityCollapseEvent.Finished(this.level, this.info.with(new ArrayList<>(this.changed.keySet()), this.changed,
            this.level.getGameTime()), aborted));
         if (LOG) {
            LOGGER.info("collapse {} finished{}: {} removed, {} changed", this.info.eventId(), aborted ? " (aborted)" : "", this.removed.size(), this.changed.size());
         }
      }
   }
}
