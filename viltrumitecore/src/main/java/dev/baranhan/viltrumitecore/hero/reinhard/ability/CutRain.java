package dev.baranhan.viltrumitecore.hero.reinhard.ability;

import dev.baranhan.viltrumitecore.hero.Annihilation;
import dev.baranhan.viltrumitecore.hero.CarveJobs;
import dev.baranhan.viltrumitecore.hero.HeroDestruction;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.reinhard.ActionTimeline;
import dev.baranhan.viltrumitecore.hero.reinhard.DestructionLevel;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardTargets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

/**
 * Rain of Cuts (spec §7, Holy Sword slot 4, Reid drawn only): {@link ReinhardRules#CUT_COUNT}
 * cuts fanned evenly over ±{@link ReinhardRules#CUT_CONE_DEG}/2° of yaw (small pitch jitter,
 * deterministic from the cast tick), one per tick from {@link ReinhardRules#CUT_FIRST_TICK}.
 * Each cut is a {@link ReinhardRules#CUT_RANGE}-block segment from the chest: every target whose
 * box it crosses takes the Reid strike ({@link Annihilation#strike}); in Holy Sword (massive
 * destruction) a 1 × 3 groove along it goes to the {@link CarveJobs} queue (no drops,
 * mobGriefing / unbreakable honoured by {@link HeroDestruction#clearBlock}). Pure parts tested.
 */
public final class CutRain implements AbilityHandler {
   public static final float PITCH_JITTER_DEG = 3.0F;
   /** Groove sampling step along the cut (blocks). */
   private static final double STEP = 0.25;
   private static final float[] SOUND_PITCHES = {0.8F, 1.0F, 1.2F};

   /** The 12 cut directions (unit), left to right over the cone. */
   public static List<Vec3> directions(float yaw, float pitch, long seed) {
      int n = ReinhardRules.CUT_COUNT;
      double half = ReinhardRules.CUT_CONE_DEG / 2.0;
      List<Vec3> out = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
         double yawOff = n == 1 ? 0.0 : -half + ReinhardRules.CUT_CONE_DEG * (double) i / (n - 1);
         double jitter = (new Random(seed * 31L + i).nextDouble() * 2.0 - 1.0) * PITCH_JITTER_DEG;
         float p = (float) Math.max(-90.0, Math.min(90.0, pitch + jitter));
         out.add(Vec3.directionFromRotation(p, (float) (yaw + yawOff)).normalize());
      }

      return out;
   }

   /** The segment crosses (or starts inside) the box. */
   public static boolean segmentHits(AABB box, Vec3 from, Vec3 to) {
      return box.contains(from) || box.clip(from, to).isPresent();
   }

   /** Blocks of the 1-wide × 3-tall groove along the segment, near to far. */
   public static Set<BlockPos> groove(Vec3 from, Vec3 to) {
      Set<BlockPos> out = new LinkedHashSet<>();
      double length = from.distanceTo(to);
      int steps = (int) Math.ceil(length / STEP);
      int half = ReinhardRules.CUT_GROOVE_HEIGHT / 2;
      for (int s = 0; s <= steps; s++) {
         Vec3 p = steps == 0 ? from : from.lerp(to, (double) s / steps);
         BlockPos center = BlockPos.containing(p);
         for (int dy = -half; dy <= half; dy++) {
            out.add(center.above(dy));
         }
      }

      return out;
   }

   /** Index of the cut that lands this tick, or -1. */
   public static int cutAt(ActionTimeline action) {
      if (!action.is(ReinhardAbility.CUT_RAIN)) {
         return -1;
      }

      int i = action.elapsed() - ReinhardRules.CUT_FIRST_TICK;
      return i >= 0 && i < ReinhardRules.CUT_COUNT ? i : -1;
   }

   public static void start(ReinhardState state) {
      state.cooldowns[ReinhardAbility.CUT_RAIN.ordinal()] = ReinhardRules.CUT_COOLDOWN;
      state.action.start(ReinhardAbility.CUT_RAIN, ReinhardRules.CUT_TICKS, -1);
   }

   @Override
   public boolean tryStart(ServerPlayer player, ReinhardState state) {
      start(state);
      return true;
   }

   @Override
   public void tick(ServerPlayer player, ReinhardState state) {
      int cut = cutAt(state.action);
      if (cut >= 0) {
         cut(player, state, cut);
      }
   }

   private static void cut(ServerPlayer player, ReinhardState state, int index) {
      ServerLevel level = player.serverLevel();
      long seed = level.getGameTime() - state.action.elapsed();
      Vec3 dir = directions(player.getYRot(), player.getXRot(), seed).get(index);
      Vec3 from = player.position().add(0.0, player.getBbHeight() * 0.7, 0.0);
      Vec3 to = from.add(dir.scale(ReinhardRules.CUT_RANGE));
      for (Entity e : level.getEntities(player, new AABB(from, to).inflate(1.0),
         e -> (e instanceof LivingEntity || e instanceof PartEntity<?>) && segmentHits(e.getBoundingBox(), from, to)
            && ReinhardTargets.canHit(player, e instanceof PartEntity<?> part ? part.getParent() : e))) {
         Annihilation.strike(player, e);
      }

      if (DestructionLevel.of(state.mode).mayBreak()) {
         CarveJobs.submit(level, new Groove(new ArrayList<>(groove(from, to))));
      }

      HeroFx.blade(player, from, to, null);
      if (index % 4 == 0) {
         ReinhardSounds.playAt(player, from, ReinhardSounds.CUT_RAIN.get(), 1.2F, SOUND_PITCHES[(index / 4) % SOUND_PITCHES.length]);
      }
   }

   /** One cut's groove, cleared near to far under the carve budget. */
   static final class Groove implements CarveJobs.Job {
      private final List<BlockPos> blocks;
      private int next;

      Groove(List<BlockPos> blocks) {
         this.blocks = blocks;
      }

      @Override
      public boolean step(@Nullable ServerLevel level, int budget) {
         int end = Math.min(this.blocks.size(), this.next + budget);
         for (; this.next < end; this.next++) {
            if (level != null) {
               HeroDestruction.clearBlock(level, this.blocks.get(this.next));
            }
         }

         return this.next >= this.blocks.size();
      }

      @Override
      public int priority() {
         return 0;
      }

      @Override
      public int remainingWork() {
         return this.blocks.size() - this.next;
      }
   }
}
