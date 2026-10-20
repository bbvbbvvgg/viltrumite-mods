package dev.baranhan.viltrumitecore.hero.reinhard.ability;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Stomp (spec §6.2, Knight slot 5): the right leg rises and slams on tick
 * {@link ReinhardRules#STOMP_HIT_TICK}: everything Reinhard may hit within
 * {@link ReinhardRules#STOMP_RADIUS} on the ground plane (±{@link ReinhardRules#STOMP_HEIGHT})
 * takes {@link ReinhardRules#STOMP_DAMAGE} and an outward push, a ground ring
 * with camera shake, and in Knight the top layer erupts (moderate destruction,
 * blocks only; mobGriefing / unbreakable blocks honoured by HeroDebris).
 * Pure parts ({@link #inArea}, {@link #start}, {@link #hitNow}, {@link #eruption}) are unit tested.
 */
public final class Stomp implements AbilityHandler {
   /** Offset of a target's feet from his feet: within the radius horizontally and on the ground plane. */
   public static boolean inArea(double dx, double dy, double dz) {
      double r = ReinhardRules.STOMP_RADIUS;
      return Math.abs(dy) <= ReinhardRules.STOMP_HEIGHT && dx * dx + dz * dz <= r * r + 1.0E-9;
   }

   public static void start(ReinhardState state) {
      state.cooldowns[ReinhardAbility.STOMP.ordinal()] = ReinhardRules.STOMP_COOLDOWN;
      state.action.start(ReinhardAbility.STOMP, ReinhardRules.STOMP_TICKS, -1);
   }

   public static boolean hitNow(ActionTimeline action) {
      return action.is(ReinhardAbility.STOMP) && action.elapsed() == ReinhardRules.STOMP_HIT_TICK;
   }

   /** The eruption the destruction level allows (null = no block moves). */
   @Nullable
   public static HeroDebris.Eruption eruption(DestructionLevel level) {
      return level.scaled(ReinhardRules.STOMP_ERUPTION);
   }

   @Override
   public boolean tryStart(ServerPlayer player, ReinhardState state) {
      start(state);
      return true;
   }

   @Override
   public void tick(ServerPlayer player, ReinhardState state) {
      if (hitNow(state.action)) {
         slam(player, state);
      }
   }

   private static void slam(ServerPlayer player, ReinhardState state) {
      ServerLevel level = player.serverLevel();
      Vec3 feet = player.position();
      double r = ReinhardRules.STOMP_RADIUS;
      double h = ReinhardRules.STOMP_HEIGHT;
      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(feet, feet).inflate(r, h, r),
         e -> inArea(e.getX() - feet.x, e.getY() - feet.y, e.getZ() - feet.z) && ReinhardTargets.canHit(player, e))) {
         if (Strikes.hit(player, e, ReinhardRules.STOMP_DAMAGE)) {
            Vec3 out = e.position().subtract(feet);
            Strikes.knockback(e, out.horizontalDistanceSqr() < 1.0E-4 ? player.getLookAngle() : out, ReinhardRules.STOMP_KNOCKBACK);
         }
      }

      BlockPos ground = FistCombo.groundUnder(level, feet);
      BlockState material = ground != null ? level.getBlockState(ground) : null;
      HeroFx.slam(player, ground != null ? Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0) : feet, material, (float) r);
      HeroDebris.Eruption spec = eruption(DestructionLevel.of(state.mode));
      if (spec != null && ground != null) {
         Vec3 look = player.getLookAngle();
         Vec3 forward = new Vec3(look.x, 0.0, look.z);
         forward = forward.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : forward.normalize();
         // Blocks only: the slam already hit the living things by Reinhard's own target rule.
         HeroDebris.erupt(player, ground, forward, spec, 0.0F, e -> true);
      }

      ReinhardSounds.playAt(player, feet, ReinhardSounds.STOMP.get(), 1.6F, 0.7F);
   }
}
