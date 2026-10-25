package dev.baranhan.viltrumitecore.hero.reinhard.ability;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.HeroDebris;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.reinhard.ActionTimeline;
import dev.baranhan.viltrumitecore.hero.reinhard.DestructionLevel;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardTargets;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fist Combo (spec §6.2, Knight slot 1): one-handed, three hits 10 / 10 / 16.
 * Each press is the next hit; a press within {@link ReinhardRules#COMBO_CHAIN_WINDOW}
 * idle ticks after a hit ends continues the chain, later it starts over (open
 * question 1, default). A press during a hit is queued (no refusal spam). The third
 * hit adds a wave: r 4 shockwave, damage around the contact, a small Knight eruption
 * (moderate destruction, mobGriefing / unbreakable blocks honoured by HeroDebris).
 * No cooldown: each hit's clip length is its lock. A mode switch, control or
 * another action resets the chain.
 */
public final class FistCombo implements AbilityHandler {
   /** Third-hit ground eruption before the mode's destruction cap. */
   static final HeroDebris.Eruption WAVE_ERUPTION = new HeroDebris.Eruption(2.5, 1.0, 0.0, 12, 0.3, 0.6);

   public static float damage(int step) {
      return ReinhardRules.COMBO_DAMAGE[step];
   }

   public static boolean wave(int step) {
      return step == ReinhardRules.COMBO_DAMAGE.length - 1;
   }

   /** Pure: the hit a press starts after {@code lastStep} (−1 none) and {@code idle} ticks without a hit. */
   public static int next(int lastStep, int idle) {
      return lastStep >= 0 && lastStep < ReinhardRules.COMBO_DAMAGE.length - 1 && idle <= ReinhardRules.COMBO_CHAIN_WINDOW ? lastStep + 1 : 0;
   }

   /** Pure: the press starts the next hit; returns its index. */
   public static int press(ReinhardState state) {
      int step = next(state.comboStep, state.comboIdle);
      state.comboStep = step;
      state.comboIdle = 0;
      state.comboQueued = false;
      state.action.start(ReinhardAbility.FIST_COMBO, ReinhardRules.COMBO_STEP_TICKS[step], -1, step);
      return step;
   }

   /** Pure: a hit is running, so a press belongs to the combo. */
   public static boolean claims(ReinhardState state) {
      return state.action.is(ReinhardAbility.FIST_COMBO);
   }

   public static void queue(ReinhardState state) {
      state.comboQueued = true;
   }

   public static void reset(ReinhardState state) {
      state.comboStep = -1;
      state.comboIdle = 0;
      state.comboQueued = false;
   }

   /** Pure, every server tick before the timeline advances: queue, idle count, chain break. */
   public static void tickChain(ReinhardState state) {
      ActionTimeline action = state.action;
      if (action.running() && !action.is(ReinhardAbility.FIST_COMBO)) {
         reset(state);
         return;
      }

      if (action.running() || state.comboStep < 0) {
         return;
      }

      if (state.comboQueued) {
         press(state);
         return;
      }

      state.comboIdle++;
      if (state.comboIdle > ReinhardRules.COMBO_CHAIN_WINDOW) {
         reset(state);
      }
   }

   /** The wave's eruption for a destruction level (null = no blocks move). */
   @Nullable
   public static HeroDebris.Eruption eruption(DestructionLevel level) {
      HeroDebris.Eruption spec = level.scaled(WAVE_ERUPTION);
      return spec == null ? null : new HeroDebris.Eruption(Math.min(spec.radius(), ReinhardRules.COMBO_WAVE_RADIUS), spec.depth(), spec.ahead(),
         spec.maxFlying(), spec.speed(), spec.power());
   }

   @Override
   public boolean tryStart(ServerPlayer player, ReinhardState state) {
      press(state);
      return true;
   }

   @Override
   public boolean claimsPress(ReinhardState state) {
      return claims(state);
   }

   @Override
   public void secondPress(ServerPlayer player, ReinhardState state) {
      queue(state);
   }

   @Override
   public void tick(ServerPlayer player, ReinhardState state) {
      tickChain(state);
      ActionTimeline action = state.action;
      if (action.is(ReinhardAbility.FIST_COMBO) && action.elapsed() == ReinhardRules.COMBO_HIT_TICKS[action.phase()]) {
         hit(player, state, action.phase());
      }
   }

   @Override
   public void cancel(ServerPlayer player, ReinhardState state, CleanupReason reason) {
      reset(state);
   }

   private static void hit(ServerPlayer player, ReinhardState state, int step) {
      Vec3 look = player.getLookAngle();
      LivingEntity target = ReinhardTargets.inReach(player, ReinhardRules.COMBO_REACH);
      Vec3 at = target != null ? LightPunch.contact(player, target, ReinhardRules.COMBO_REACH) : reachPoint(player);
      if (target != null && Strikes.hit(player, target, damage(step))) {
         Strikes.knockback(target, look, ReinhardRules.COMBO_KNOCKBACK[step]);
         if (!wave(step)) {
            HeroFx.flash(player, at, step==0 ? .62F : .78F);
            HeroFx.hitStop(player,at,1,player.getId(),target.getId());
            ReinhardSounds.playAt(player, at, ReinhardSounds.COMBO_HIT.get(), 1.0F, 1.0F);
         }
      }

      if (wave(step)) {
         wave(player, state, target, target != null ? target.position() : at);
      }
   }

   /** Third hit: shockwave, damage around the point (not the punched target again), a small eruption. */
   private static void wave(ServerPlayer player, ReinhardState state, @Nullable LivingEntity primary, Vec3 center) {
      ServerLevel level = player.serverLevel();
      BlockPos ground = groundUnder(level, center);
      BlockState material = ground != null ? level.getBlockState(ground) : null;
      double r = ReinhardRules.COMBO_WAVE_RADIUS;
      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(r, 2.0, r),
         e -> e != primary && e.distanceToSqr(center) <= r * r && ReinhardTargets.canHit(player, e))) {
         if (Strikes.hit(player, e, ReinhardRules.COMBO_WAVE_DAMAGE)) {
            Vec3 out = e.position().subtract(center);
            Strikes.knockback(e, out.lengthSqr() < 1.0E-4 ? player.getLookAngle() : out, ReinhardRules.COMBO_WAVE_KNOCKBACK);
         }
      }

      HeroFx.shockwave(player, ground != null ? Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0) : center, 1.0F, material, (float) r);
      HeroDebris.Eruption spec = eruption(DestructionLevel.of(state.mode));
      if (spec != null && ground != null) {
         Vec3 look = player.getLookAngle();
         Vec3 forward = new Vec3(look.x, 0.0, look.z);
         forward = forward.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : forward.normalize();
         // Blocks only: the wave already hit the living things by Reinhard's own target rule.
         HeroDebris.erupt(player, ground, forward, spec, 0.0F, e -> true);
      }

      ReinhardSounds.playAt(player, center, ReinhardSounds.COMBO_FINAL.get(), 1.4F, 0.9F);
   }

   /** Where a missed punch lands: the reach point, or the block the look ray hits first. */
   private static Vec3 reachPoint(ServerPlayer player) {
      Vec3 eye = player.getEyePosition();
      Vec3 end = eye.add(player.getLookAngle().scale(ReinhardRules.COMBO_REACH));
      return player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
   }

   /** The first solid block at or up to 3 below the point. */
   @Nullable
   static BlockPos groundUnder(ServerLevel level, Vec3 at) {
      BlockPos pos = BlockPos.containing(at);
      for (int i = 0; i <= 3; i++) {
         BlockPos p = pos.below(i);
         if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
            return p;
         }
      }

      return null;
   }
}
