package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import dev.baranhan.viltrumitecore.hero.ironman.combat.IronManTargets;
import java.util.Comparator;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ultimate "Jet ram" (rework spec §7), driven by the JET_RAM staged move: ticks 0-30 the thrusters open
 * (hyper armour, rooted), 30-46 the 15-block jet dash takes the first target, 46-90 the candle up ~25
 * blocks with blows in the air, 90-110 the dive, contact at exactly tick 110 (HulkbusterRules.ULT_CONTACT):
 * crater through CarveJobs, shock wave, flash. 110-160 the long tail (rooted, steam). Costs 10 % durability.
 */
public final class HulkbusterUltimate {
   private HulkbusterUltimate() {
   }

   public static void tick(ServerPlayer player, IronManState state, int elapsed) {
      HulkbusterKit kit = state.hulkKit;
      Vec3 dir = kit.ultDir == null ? HulkbusterCombat.flatLook(player) : kit.ultDir;
      if (elapsed < HulkbusterRules.ULT_DASH_START) {
         player.setDeltaMovement(0.0, Math.min(0.0, player.getDeltaMovement().y), 0.0);
         player.hurtMarked = true;
         if (elapsed % 6 == 0) {
            HeroFx.launch(player, player.position(), 0.5F, player.level().getBlockState(player.blockPosition().below()));
         }
      } else if (elapsed < HulkbusterRules.ULT_DASH_END) {
         double speed = HulkbusterRules.ULT_DASH_DISTANCE / (HulkbusterRules.ULT_DASH_END - HulkbusterRules.ULT_DASH_START);
         player.setDeltaMovement(dir.x * speed, 0.05, dir.z * speed);
         player.hurtMarked = true;
         if (kit.ultTarget < 0) {
            capture(player, state);
         }

         if (elapsed % 3 == 0) {
            HeroFx.airTrail(player, player);
         }
      } else if (elapsed < HulkbusterRules.ULT_RISE_END) {
         double rise = HulkbusterRules.ULT_RISE / (HulkbusterRules.ULT_RISE_END - HulkbusterRules.ULT_DASH_END);
         player.setDeltaMovement(dir.x * 0.08, rise, dir.z * 0.08);
         player.hurtMarked = true;
         player.resetFallDistance();
         LivingEntity captive = captive(player, kit);
         if (captive != null && (elapsed - HulkbusterRules.ULT_DASH_END) % 10 == 5) {
            captive.invulnerableTime = 0;
            captive.hurt(player.damageSources().playerAttack(player), 3.0F);
            HeroFx.hitStop(player, captive.getEyePosition(), 2, player.getId(), captive.getId());
            HeroFx.flash(player, captive.getEyePosition(), 0.6F);
            HulkbusterSounds.play(player, HulkbusterSounds.Cue.FACE_PUNCH, captive.getEyePosition(), 1.1F);
         }
      } else if (elapsed < HulkbusterRules.ULT_CONTACT) {
         player.setDeltaMovement(dir.x * 0.05, player.onGround() ? 0.0 : -3.2, dir.z * 0.05);
         player.hurtMarked = true;
         player.resetFallDistance();
      } else if (elapsed == HulkbusterRules.ULT_CONTACT) {
         impact(player, state);
      } else {
         player.setDeltaMovement(0.0, Math.min(0.0, player.getDeltaMovement().y), 0.0);
         player.hurtMarked = true;
         if (elapsed % 10 == 0) {
            HeroFx.launch(player, player.position(), 0.25F, null);
         }
      }

      LivingEntity captive = captive(player, kit);
      if (captive != null) {
         if (kit.grab.escape >= HulkbusterRules.ESCAPE_FULL && kit.grab.strong && elapsed < HulkbusterRules.ULT_RISE_END) {
            release(player, kit, ReleaseReason.INTERRUPTED);
         } else {
            Vec3 at = HulkbusterCombat.anchor(player.position(), player.getYRot(), captive.getBbWidth(), captive.getBbHeight());
            captive.setPos(at.x, at.y, at.z);
            captive.setDeltaMovement(Vec3.ZERO);
            captive.resetFallDistance();
         }
      }
   }

   private static void capture(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      AABB box = player.getBoundingBox().inflate(1.6, 0.5, 1.6);
      LivingEntity target = player.serverLevel().getEntitiesOfClass(LivingEntity.class, box, e -> e != player && HulkbusterCombat.grabbable(player, e))
         .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
      if (target == null) {
         return;
      }

      UUID effect = UUID.randomUUID();
      if (ControlManager.get(player.serverLevel()).tryAcquire(target, player.getUUID(), effect, ControlKind.PULL, player.level().getGameTime() + 140L)) {
         kit.ultTarget = target.getId();
         kit.ultTargetUuid = target.getUUID();
         kit.ultEffect = effect;
         kit.grab.escape = 0.0F;
         kit.grab.strong = target instanceof net.minecraft.world.entity.player.Player victim && HulkbusterTags.superstrong(victim);
         HeroFx.hitStop(player, target.getBoundingBox().getCenter(), 4, player.getId(), target.getId());
         HulkbusterSounds.play(player, HulkbusterSounds.Cue.GRAB);
      }
   }

   @Nullable
   static LivingEntity captive(ServerPlayer player, HulkbusterKit kit) {
      if (kit.ultTarget < 0) {
         return null;
      }

      Entity entity = player.level().getEntity(kit.ultTarget);
      if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
         release(player, kit, ReleaseReason.TARGET_LOST);
         return null;
      }

      return living;
   }

   private static void impact(ServerPlayer player, IronManState state) {
      HulkbusterKit kit = state.hulkKit;
      ServerLevel level = player.serverLevel();
      Vec3 at = player.position();
      LivingEntity captive = captive(player, kit);
      release(player, kit, ReleaseReason.NORMAL_END);
      double r = HulkbusterRules.ULT_RADIUS;
      for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r, 3.0, r), e -> e != player && IronManTargets.canHit(player, e))) {
         double distance = target.position().distanceTo(at);
         if (distance > r) {
            continue;
         }

         float damage = target == captive ? HulkbusterRules.ULT_DAMAGE : (float)(HulkbusterRules.ULT_DAMAGE * 0.5 * (1.0 - distance / r * 0.6));
         target.invulnerableTime = 0;
         if (target.hurt(player.damageSources().playerAttack(player), damage) && HeroRegistry.allowsImpulse(target)) {
            Vec3 away = target.position().subtract(at);
            Vec3 flat = away.horizontalDistanceSqr() < 1.0E-4 ? Vec3.ZERO : new Vec3(away.x, 0.0, away.z).normalize();
            target.setDeltaMovement(flat.scale(1.6).add(0.0, 0.9, 0.0));
            target.hurtMarked = true;
         }
      }

      BlockPos ground = BlockPos.containing(at.x, at.y - 0.5, at.z);
      HeroFx.slam(player, at, level.getBlockState(ground), 9.0F);
      HeroFx.shockwave(player, at, 2.5F, level.getBlockState(ground), (float)r * 1.6F);
      HeroFx.flash(player, at.add(0.0, 1.0, 0.0), 2.0F);
      HulkbusterSounds.play(player, HulkbusterSounds.Cue.ULT_IMPACT);
      HulkbusterDestruction.crater(level, at, 3.6, HulkbusterDestruction.CRATER_HARDNESS);
      state.hulkbuster.absorbSelf(HulkbusterRules.ULT_SELF_COST * HulkbusterLayer.DURABILITY);
   }

   private static void release(ServerPlayer player, HulkbusterKit kit, ReleaseReason reason) {
      if (kit.ultTarget < 0) {
         return;
      }

      if (kit.ultTargetUuid != null && kit.ultEffect != null) {
         ControlManager.get(player.serverLevel()).release(kit.ultTargetUuid, kit.ultEffect, reason);
      }

      Entity entity = player.level().getEntity(kit.ultTarget);
      if (entity instanceof ServerPlayer victim && !victim.hasDisconnected()) {
         victim.connection.teleport(victim.getX(), victim.getY(), victim.getZ(), victim.getYRot(), victim.getXRot());
      }

      kit.ultTarget = -1;
      kit.ultTargetUuid = null;
      kit.ultEffect = null;
   }

   public static void end(ServerPlayer player, IronManState state) {
      release(player, state.hulkKit, ReleaseReason.INTERRUPTED);
      state.hulkKit.ultDir = null;
   }
}
