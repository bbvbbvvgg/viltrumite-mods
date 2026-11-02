package dev.baranhan.viltrumitecore.world.collapse;

import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support loss (#135): after any structural block removal (mining, commands, fire, explosions, hero powers, the
 * end of a collapse) the structure around it is checked with a {@link SupportProbe}. A structure that just lost
 * its last connection to the ground comes down as a {@link SupportChainPlan}: one collapse when it fits, a chain
 * of stages (lowest first) when it is a skyscraper top. Limits: one probe per level at a time with a time budget
 * per tick, {@link CollapseRules#SUPPORT_MAX_NODES}, {@link CollapseRules#SUPPORT_MIN_BLOCKS}; a structure that
 * was already floating before the removal (a sky platform) stays where it is. When collapses are off (config or
 * {@code mobGriefing=false}) nearby players are told why the severed structure keeps hanging.
 */
public final class SupportWatch {
   private static final Logger LOGGER = LoggerFactory.getLogger("viltrumitecore/collapse");
   private static final Map<ResourceKey<Level>, State> STATES = new HashMap<>();
   private static final Map<UUID, Long> NOTIFIED = new HashMap<>();
   // QA metrics (/collapse status)
   static int probes, grounded, floating, undecided, alreadyFloating, tooSmall, chainsStarted, stagesStarted, blocked;
   static long lastProbeNanos, maxProbeNanos;
   static int lastNodes, maxNodes;
   static String lastResult = "-";

   private SupportWatch() {
   }

   /** Config switch: support loss feeds probes (default on). */
   public static boolean enabled() {
      return ViltrumiteCoreConfig.INSTANCE.collapseSupportLoss;
   }

   private record Request(long[] removed, int cutY, int x, int y, int z, @Nullable CollapseCause cause, boolean continuation) {
   }

   private static final class Job {
      final Request request;
      final SupportProbe probe;
      final boolean before;
      final long started;
      long nanos;
      int ticks;
      @Nullable
      long[] floatingBlocks;
      int floatingStructure;

      Job(Request request, SupportProbe probe, boolean before, long started) {
         this.request = request;
         this.probe = probe;
         this.before = before;
         this.started = started;
      }
   }

   /** One severed structure coming down stage by stage. */
   static final class Chain {
      final SupportChainPlan plan;
      final CollapseCause cause;
      final long seed;
      final float fallX, fallZ;
      final AABB box;
      @Nullable
      CollapseDirector.Active current;

      Chain(SupportChainPlan plan, CollapseCause cause, long seed, float fallX, float fallZ) {
         this.plan = plan;
         this.cause = cause;
         this.seed = seed;
         this.fallX = fallX;
         this.fallZ = fallZ;
         this.box = new AABB(plan.minX, plan.minY, plan.minZ, plan.maxX + 1, plan.maxY + 1, plan.maxZ + 1);
      }
   }

   private static final class State {
      @Nullable
      Job job;
      final ArrayDeque<Request> pending = new ArrayDeque<>();
      final List<Chain> chains = new ArrayList<>();
   }

   private static State state(ServerLevel level) {
      return STATES.computeIfAbsent(level.dimension(), k -> new State());
   }

   static boolean allowed(ServerLevel level) {
      return CollapseDirector.enabled() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
   }

   /** A finished collapse removed its section: whatever hung on it is checked next (no "was it standing" test). */
   static void followUp(ServerLevel level, long[] removed, int cutY, CollapseCause cause) {
      if (!enabled() || removed.length == 0) {
         return;
      }
      State st = state(level);
      if (st.pending.size() >= 16) {
         st.pending.pollFirst();
      }
      long c = removed[removed.length / 2];
      st.pending.addLast(new Request(removed, cutY, BlockKey.x(c), BlockKey.y(c), BlockKey.z(c), cause, true));
   }

   /** True inside a running chain (no other collapse starts there). */
   static boolean covers(ServerLevel level, double x, double y, double z) {
      State st = STATES.get(level.dimension());
      if (st == null) {
         return false;
      }
      for (Chain c : st.chains) {
         if (c.box.inflate(4.0).contains(x + 0.5, y + 0.5, z + 0.5)) {
            return true;
         }
      }
      return false;
   }

   public static int chainCount() {
      int n = 0;
      for (State st : STATES.values()) {
         n += st.chains.size();
      }
      return n;
   }

   static void abort(@Nullable ServerLevel level) {
      if (level == null) {
         STATES.clear();
         NOTIFIED.clear();
      } else {
         STATES.remove(level.dimension());
      }
   }

   /** Server level tick (after the damage-ledger collapses). */
   static void tick(ServerLevel level, long now) {
      State st = STATES.get(level.dimension());
      SupportLedger ledger = StructuralDamage.support(level);
      if (st == null && ledger.pending() == 0) {
         return;
      }
      if (st == null) {
         st = state(level);
      }
      if (st.job == null) {
         Request r = st.pending.pollFirst();
         if (r == null) {
            r = settledRequest(level, ledger, now);
         }
         if (r != null) {
            st.job = new Job(r, probe(level, r, null, false), false, now);
            probes++;
         }
      }
      if (st.job != null) {
         Job job = st.job;
         long t0 = System.nanoTime();
         SupportProbe.Status s;
         do {
            s = job.probe.step(4096);
         } while (s == SupportProbe.Status.RUNNING && System.nanoTime() - t0 < CollapseRules.SUPPORT_TICK_NANOS);
         job.nanos += System.nanoTime() - t0;
         job.ticks++;
         if (s != SupportProbe.Status.RUNNING) {
            st.job = null;
            finish(level, st, job, now);
         }
      }
      Iterator<Chain> it = st.chains.iterator();
      while (it.hasNext()) {
         Chain c = it.next();
         if (c.current != null && !c.current.removalDone()) {
            continue;
         }
         if (!allowed(level)) {
            it.remove(); // switched off half-way: the rest stays (the journal still restores what fell)
            continue;
         }
         CollapseAnalyzer.Plan plan = c.plan.next(StructuralDamage.volume(level), CollapseRules.chainStageBlocks(CollapseDirector.maxBlocks()), c.cause,
            c.seed + c.plan.stages(), c.fallX, c.fallZ);
         if (plan == null || plan.blocks().length == 0) {
            it.remove();
            if (CollapseDirector.LOG) {
               LOGGER.info("support chain at {} {} {} done: {} stages, {} blocks", (int)c.box.getCenter().x, c.plan.minY, (int)c.box.getCenter().z,
                  c.plan.stages(), c.plan.total());
            }
            continue;
         }
         int creak = c.plan.stages() == 1 ? 0 : CollapseRules.CHAIN_CREAK_TICKS;
         c.current = CollapseDirector.startStage(level, plan, c.cause, c.seed * 31L + c.plan.stages(), creak, c);
         stagesStarted++;
         if (c.current == null) {
            it.remove(); // cancelled by a listener (protected zone)
         }
      }
      if (st.job == null && st.pending.isEmpty() && st.chains.isEmpty() && ledger.pending() == 0) {
         STATES.remove(level.dimension());
      }
   }

   @Nullable
   private static Request settledRequest(ServerLevel level, SupportLedger ledger, long now) {
      long cell = ledger.settled(now);
      if (cell == Long.MIN_VALUE) {
         return null;
      }
      int cx = (BlockKey.x(cell) << CollapseRules.CELL_SHIFT) + 4, cy = (BlockKey.y(cell) << CollapseRules.CELL_SHIFT) + 4, cz = (BlockKey.z(cell) << CollapseRules.CELL_SHIFT) + 4;
      if (CollapseDirector.insideActive(level, cx, cy, cz) || covers(level, cx, cy, cz)) {
         return null; // a running collapse or chain is bringing this structure down (its follow-up checks the rest)
      }
      LongArrayList near = ledger.around(cx, cy, cz, CollapseRules.SUPPORT_CLUSTER_RADIUS, CollapseRules.SUPPORT_CLUSTER_BAND);
      CollapseAnalyzer.Volume v = StructuralDamage.volume(level);
      LongArrayList air = new LongArrayList();
      int cutY = Integer.MAX_VALUE;
      for (int i = 0; i < near.size(); i++) {
         long k = near.getLong(i);
         if (v.at(BlockKey.x(k), BlockKey.y(k), BlockKey.z(k)) == CollapseAnalyzer.AIR) {
            air.add(k);
            cutY = Math.min(cutY, BlockKey.y(k));
         }
      }
      if (air.isEmpty()) {
         return null; // rebuilt
      }
      return new Request(air.toLongArray(), cutY, cx, cy, cz, null, false);
   }

   private static SupportProbe probe(ServerLevel level, Request r, @Nullable LongOpenHashSet assumed, boolean single) {
      return new SupportProbe(StructuralDamage.volume(level), r.removed, assumed, single, r.cutY, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1,
         CollapseRules.SUPPORT_MAX_NODES, CollapseRules.SUPPORT_SEARCH_RADIUS, r.x, r.z);
   }

   private static void finish(ServerLevel level, State st, Job job, long now) {
      SupportProbe p = job.probe;
      lastProbeNanos = job.nanos;
      maxProbeNanos = Math.max(maxProbeNanos, job.nanos);
      lastNodes = p.nodes();
      maxNodes = Math.max(maxNodes, p.nodes());
      Request r = job.request;
      String where = r.x + " " + r.y + " " + r.z;
      if (job.before) {
         if (p.status() == SupportProbe.Status.GROUNDED) {
            start(level, st, job.floatingBlocks, job.floatingStructure, r, now);
         } else {
            alreadyFloating++;
            result(String.format("%s: was not standing before (%s), stays", where, p.reason()), job);
         }
         return;
      }
      switch (p.status()) {
         case GROUNDED -> {
            grounded++;
            result(String.format("%s: supported (%d removed, cut y %d, %d searches)", where, r.removed.length, r.cutY, p.groundedSearches()), job);
         }
         case UNDECIDED -> {
            undecided++;
            result(String.format("%s: undecided, %s", where, p.reason()), job);
         }
         case FLOATING -> {
            floating++;
            long[] blocks = p.floatingBlocks();
            if (blocks.length < CollapseRules.SUPPORT_MIN_BLOCKS || p.floatingNatural() > p.floatingStructure()) {
               tooSmall++;
               result(String.format("%s: %d loose blocks (%d natural), too small or terrain", where, blocks.length, p.floatingNatural()), job);
               return;
            }
            if (r.continuation) {
               start(level, st, blocks, p.floatingStructure(), r, now);
               return;
            }
            // was it standing before? the removed blocks count as solid again
            LongOpenHashSet assumed = new LongOpenHashSet(r.removed);
            Request seeds = new Request(origins(blocks, r.removed), r.cutY, r.x, r.y, r.z, r.cause, false);
            Job before = new Job(r, probe(level, seeds, assumed, true), true, job.started);
            before.nanos = job.nanos;
            before.ticks = job.ticks;
            before.floatingBlocks = blocks;
            before.floatingStructure = p.floatingStructure();
            st.job = before;
         }
         default -> {
         }
      }
   }

   /** Removed positions next to a floating block: the "before" search starts there. */
   private static long[] origins(long[] floatingBlocks, long[] removed) {
      LongOpenHashSet in = new LongOpenHashSet(floatingBlocks);
      LongArrayList out = new LongArrayList();
      for (long k : removed) {
         int x = BlockKey.x(k), y = BlockKey.y(k), z = BlockKey.z(k);
         if (in.contains(BlockKey.of(x, y + 1, z)) || in.contains(BlockKey.of(x, y - 1, z)) || in.contains(BlockKey.of(x + 1, y, z))
            || in.contains(BlockKey.of(x - 1, y, z)) || in.contains(BlockKey.of(x, y, z + 1)) || in.contains(BlockKey.of(x, y, z - 1))) {
            out.add(k);
         }
      }
      return out.toLongArray();
   }

   private static void start(ServerLevel level, State st, long[] blocks, int structure, Request r, long now) {
      String where = r.x + " " + r.y + " " + r.z;
      if (!allowed(level)) {
         blocked++;
         result(String.format("%s: %d blocks lost support, collapses are off", where, blocks.length), null);
         notifyBlocked(level, r);
         return;
      }
      long seed = now * 0x9E3779B97F4A7C15L ^ BlockKey.of(r.x, r.y, r.z) ^ level.random.nextLong();
      SupportChainPlan plan = new SupportChainPlan(blocks, seed);
      // fall toward the side that lost its support: removed-block centroid against the structure centroid
      double hx = 0, hz = 0;
      for (long k : r.removed) {
         hx += BlockKey.x(k) + 0.5;
         hz += BlockKey.z(k) + 0.5;
      }
      hx /= Math.max(1, r.removed.length);
      hz /= Math.max(1, r.removed.length);
      float fx = (float)(hx - plan.centerX), fz = (float)(hz - plan.centerZ);
      double len = Math.sqrt(fx * fx + fz * fz);
      if (len < 0.75) {
         double a = (seed >>> 11) * 0x1.0p-53 * Math.PI * 2.0;
         fx = (float)Math.cos(a);
         fz = (float)Math.sin(a);
      } else {
         fx /= (float)len;
         fz /= (float)len;
      }
      CollapseCause cause = r.cause != null ? r.cause : CollapseDirector.causeNear(level, r.x, r.y, r.z, CollapseCause.SUPPORT);
      Chain chain = new Chain(plan, cause, seed, fx, fz);
      st.chains.add(chain);
      chainsStarted++;
      result(String.format("%s: %d blocks lost support (%d structure), chain started, cause %s", where, blocks.length, structure, cause.id()), null);
   }

   private static void result(String text, @Nullable Job job) {
      lastResult = job == null ? text : String.format("%s [%d nodes, %.2f ms over %d ticks]", text, job.probe.nodes(), job.nanos / 1.0E6, job.ticks);
      if (CollapseDirector.LOG) {
         LOGGER.info("support probe {}", lastResult);
      }
   }

   private static void notifyBlocked(ServerLevel level, Request r) {
      String key = CollapseDirector.enabled() ? "message.viltrumitecore.collapse.support_blocked.gamerule" : "message.viltrumitecore.collapse.support_blocked.config";
      long now = level.getGameTime();
      for (ServerPlayer p : level.players()) {
         if (p.distanceToSqr(r.x + 0.5, r.y + 0.5, r.z + 0.5) > 96.0 * 96.0) {
            continue;
         }
         Long last = NOTIFIED.get(p.getUUID());
         if (last != null && now - last < 600L && now >= last) {
            continue;
         }
         NOTIFIED.put(p.getUUID(), now);
         p.displayClientMessage(Component.translatable(key), true);
      }
   }

   static String status() {
      return String.format(
         "support: %s, probes %d (grounded %d, floating %d, undecided %d, already floating %d, too small %d, blocked %d), chains %d running / %d started, %d stages; probe %.2f ms (max %.2f), %d nodes (max %d); last: %s",
         enabled() ? "on" : "off", probes, grounded, floating, undecided, alreadyFloating, tooSmall, blocked, chainCount(), chainsStarted, stagesStarted,
         lastProbeNanos / 1.0E6, maxProbeNanos / 1.0E6, lastNodes, maxNodes, lastResult);
   }

   static void resetMetrics() {
      maxProbeNanos = 0;
      maxNodes = 0;
   }

   /** {@code /collapse support <pos>}: is the structure at {@code pos} standing on anything below {@code pos}? */
   static SupportProbe dryRun(ServerLevel level, int x, int y, int z) {
      SupportProbe p = new SupportProbe(StructuralDamage.volume(level), new long[]{BlockKey.of(x, y, z)}, null, true, y, level.getMinBuildHeight(),
         level.getMaxBuildHeight() - 1, CollapseRules.SUPPORT_MAX_NODES, CollapseRules.SUPPORT_SEARCH_RADIUS, x, z);
      p.run();
      return p;
   }
}
