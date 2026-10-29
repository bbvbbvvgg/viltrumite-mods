package dev.baranhan.viltrumitecore.hero.dio;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.dio.net.DioFxS2CPacket;
import dev.baranhan.viltrumitecore.hero.dio.net.DioNet;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Knives, the time-stop cast, the vampire grip and the Road Roller timelines. */
public final class DioAbilitiesRuntime {
   private DioAbilitiesRuntime() {
   }

   // --- knives ------------------------------------------------------------------------

   public static void startKnives(ServerPlayer player, DioState state) {
      state.startAction(HeroAction.KNIFE_THROW, DioRules.KNIFE_LENGTH);
      state.cooldowns[DioRules.CD_KNIVES] = DioRules.KNIFE_COOLDOWN;
      DioSounds.play(player.serverLevel(), player.getEyePosition(), DioSounds.KNIFE_DRAW, 0.8F, 1.0F);
   }

   public static void tickKnives(ServerPlayer player, DioState state) {
      if (state.elapsed == DioRules.KNIFE_WINDUP) {
         Vec3 look = player.getLookAngle();
         float yaw = player.getYRot();
         float pitch = player.getXRot();
         Vec3 right = Vec3.directionFromRotation(0, yaw + 90.0F);
         Vec3 hand = player.getEyePosition().add(right.scale(0.28)).add(0, -0.18, 0).add(look.scale(0.4));
         for (int i = 0; i < DioRules.KNIFE_COUNT; i++) {
            double offset = DioRules.knifeYawOffset(i, DioRules.KNIFE_COUNT);
            Vec3 dir = Vec3.directionFromRotation(pitch + (float)(Math.abs(offset) * 0.12), yaw + (float)offset);
            DioKnifeEntity.throwFrom(player, hand.add(right.scale(offset * 0.012)), dir);
         }

         DioSounds.play(player.serverLevel(), player.getEyePosition(), DioSounds.KNIFE_THROW, 1.0F, 1.0F);
      }

      if (state.elapsed >= state.length) {
         state.clearAction();
      }
   }

   // --- time stop cast --------------------------------------------------------------------

   public static void startTimeStop(ServerPlayer player, DioState state) {
      DioCombat.summon(player, state);
      state.castBlood = state.blood;
      state.blood = 0;
      int duration = DioRules.timeStopDuration(state.castBlood);
      state.startAction(HeroAction.TIME_STOP, DioRules.TIME_STOP_CAST);
      state.cooldowns[DioRules.CD_TIME_STOP] = DioRules.TIME_STOP_COOLDOWN + duration + DioRules.TIME_STOP_CAPTURE;
      HeroSpeech.ability(player, DioAbilities.TIME_STOP);
      DioSounds.play(player.serverLevel(), player.getEyePosition(), DioSounds.TIME_STOP, 2.0F, 1.0F);
   }

   public static void tickTimeStop(ServerPlayer player, DioState state) {
      if (state.elapsed == DioRules.TIME_STOP_CAPTURE) {
         DioTimeStop.start(player, player.position().add(0, 1.0, 0), DioRules.TIME_STOP_RADIUS, DioRules.timeStopDuration(state.castBlood), false);
      }

      if (state.elapsed >= state.length) {
         state.clearAction();
      }
   }

   /** A cast that ends before the world stopped gives the blood back and cools down briefly. */
   public static void cancelTimeStop(ServerPlayer player, DioState state) {
      if (state.action == HeroAction.TIME_STOP && state.elapsed < DioRules.TIME_STOP_CAPTURE) {
         state.blood = DioRules.clampBlood(state.blood + state.castBlood);
         state.cooldowns[DioRules.CD_TIME_STOP] = 40;
         HeroSpeech.interrupt(player, DioAbilities.TIME_STOP);
      }
   }

   // --- vampire drain ------------------------------------------------------------------------

   /** Returns false (refusal) when no target is in reach or the grip cannot hold it. */
   public static boolean startDrain(ServerPlayer player, DioState state) {
      LivingEntity target = DioTargets.look(player, DioRules.DRAIN_RANGE);
      if (target == null) {
         return false;
      }

      ServerLevel level = player.serverLevel();
      UUID effect = UUID.randomUUID();
      boolean frozenByMe = DioTimeStop.frozenBy(player, target);
      boolean owns = false;
      if (!frozenByMe) {
         if (!HeroRegistry.allowsExternalControl(target, ControlKind.FREEZE)
            || !ControlManager.get(level).tryAcquire(target, player.getUUID(), effect, ControlKind.FREEZE, level.getGameTime() + DioRules.DRAIN_LENGTH + 20)) {
            return false;
         }

         owns = true;
      }

      DioCombat.dismiss(player, state);
      state.startAction(HeroAction.VAMPIRE_DRAIN, DioRules.DRAIN_LENGTH);
      state.cooldowns[DioRules.CD_DRAIN] = DioRules.DRAIN_COOLDOWN;
      state.drainTarget = target.getUUID();
      state.drainTargetId = target.getId();
      state.drainEffect = effect;
      state.drainOwnsControl = owns;
      DioSounds.play(level, target.getEyePosition(), DioSounds.DRAIN_GRAB, 1.0F, 1.0F);
      return true;
   }

   public static void tickDrain(ServerPlayer player, DioState state) {
      Entity entity = state.drainTarget == null ? null : player.serverLevel().getEntity(state.drainTarget);
      if (!(entity instanceof LivingEntity target) || !target.isAlive() || target.distanceTo(player) > DioRules.DRAIN_BREAK_DISTANCE) {
         stopDrain(player, state);
         return;
      }

      if (state.elapsed > 0 && state.elapsed % DioRules.DRAIN_PULSE == 0) {
         HeroDamage.applyCleanDamage(target, player.damageSources().playerAttack(player), DioRules.DRAIN_DAMAGE);
         if (player.getHealth() < player.getMaxHealth()) {
            player.heal(DioRules.DRAIN_HEAL);
         }

         DioHero.addBlood(player, DioRules.BLOOD_DRAIN_PULSE);
         Vec3 from = target.getBoundingBox().getCenter().add(0, target.getBbHeight() * 0.2, 0);
         DioNet.fx(player, DioFxS2CPacket.DRAIN_PULSE, from, player.getEyePosition().subtract(from), 1.0F, target.getId());
         DioSounds.play(player.serverLevel(), from, DioSounds.DRAIN_PULSE, 1.0F, 0.9F + player.getRandom().nextFloat() * 0.15F);
      }

      if (state.elapsed >= state.length || !target.isAlive()) {
         stopDrain(player, state);
      }
   }

   public static void stopDrain(ServerPlayer player, DioState state) {
      if (state.drainOwnsControl && state.drainTarget != null && state.drainEffect != null) {
         ControlManager.get(player.serverLevel()).release(state.drainTarget, state.drainEffect, ReleaseReason.NORMAL_END);
      }

      state.drainTarget = null;
      state.drainTargetId = -1;
      state.drainEffect = null;
      state.drainOwnsControl = false;
      if (state.action == HeroAction.VAMPIRE_DRAIN) {
         state.clearAction();
      }
   }

   // --- road roller -------------------------------------------------------------------------

   public static void startRoller(ServerPlayer player, DioState state) {
      DioCombat.dismiss(player, state);
      state.blood = 0;
      state.cooldowns[DioRules.CD_ROLLER] = DioRules.ROLLER_COOLDOWN;
      state.rollerStart = player.position();
      state.rollerTarget = DioTargets.rollerPoint(player);
      state.startAction(HeroAction.ROAD_ROLLER, DioRules.ROLLER_LENGTH);
      HeroSpeech.ability(player, DioAbilities.ROAD_ROLLER);
      DioSounds.play(player.serverLevel(), player.getEyePosition(), DioSounds.TIME_STOP, 2.0F, 1.0F);
   }

   public static void tickRoller(ServerPlayer player, DioState state) {
      ServerLevel level = player.serverLevel();
      Vec3 target = state.rollerTarget;
      int t = state.elapsed;
      player.fallDistance = 0.0F;
      if (t == DioRules.ROLLER_CAPTURE) {
         DioTimeStop.start(player, player.position().add(0, 1.0, 0), DioRules.ROLLER_RADIUS, DioRules.ROLLER_RESUME - DioRules.ROLLER_CAPTURE + 4, true);
      }

      if (t == 24) {
         DioSounds.play(level, player.position().add(0, 3, 0), DioSounds.ROLLER_APPEAR, 1.4F, 1.0F);
      }

      if (t == DioRules.ROLLER_SLAM) {
         for (LivingEntity victim : around(player, target, DioRules.ROLLER_SLAM_RADIUS)) {
            Vec3 out = flatAway(victim.position(), target).scale(0.9);
            if (!DioTimeStop.defer(player, victim, DioRules.ROLLER_SLAM_DAMAGE, out)) {
               victim.invulnerableTime = 0;
               victim.hurt(player.damageSources().playerAttack(player), DioRules.ROLLER_SLAM_DAMAGE);
            }
         }

         DioNet.fx(player, DioFxS2CPacket.ROLLER_SLAM, target, Vec3.ZERO, 2.5F, 0);
         DioSounds.play(level, target, DioSounds.ROLLER_SLAM, 2.4F, 1.0F);
      }

      if (DioRules.rollerPulseTick(t)) {
         for (LivingEntity victim : around(player, target, 3.0)) {
            if (!DioTimeStop.defer(player, victim, DioRules.ROLLER_PULSE_DAMAGE, flatAway(victim.position(), target).scale(0.12))) {
               victim.invulnerableTime = 0;
               victim.hurt(player.damageSources().playerAttack(player), DioRules.ROLLER_PULSE_DAMAGE);
            }
         }

         DioNet.fx(player, DioFxS2CPacket.ROLLER_PUNCH, target.add(0, 1.6, 0), Vec3.ZERO, 1.0F, t);
         DioSounds.play(level, target.add(0, 1.6, 0), DioSounds.ROLLER_PUNCH, 1.3F, 0.9F + player.getRandom().nextFloat() * 0.25F);
      }

      if (t == DioRules.ROLLER_BARRAGE_END) {
         DioSounds.play(level, player.getEyePosition(), DioSounds.WRYYY, 2.0F, 1.0F);
      }

      if (t == DioRules.ROLLER_RESUME) {
         TimeStopField field = DioTimeStop.fieldOf(player.getUUID());
         if (field != null) {
            DioTimeStop.end(field, ReleaseReason.EXPIRED);
         }

         for (LivingEntity victim : around(player, target, DioRules.ROLLER_BLAST_RADIUS)) {
            victim.invulnerableTime = 0;
            if (victim.hurt(player.damageSources().playerAttack(player), DioRules.ROLLER_BLAST_DAMAGE) && HeroRegistry.allowsImpulse(victim)) {
               Vec3 out = flatAway(victim.position(), target);
               victim.knockback(1.6, -out.x, -out.z);
               victim.setDeltaMovement(victim.getDeltaMovement().add(0, 0.55, 0));
               victim.hurtMarked = true;
            }
         }

         DioNet.fx(player, DioFxS2CPacket.ROLLER_EXPLODE, target.add(0, 1.0, 0), Vec3.ZERO, 3.0F, 0);
         DioSounds.play(level, target, DioSounds.ROLLER_EXPLODE, 3.0F, 1.0F);
      }

      if (t >= state.length) {
         state.rollerStart = null;
         state.rollerTarget = null;
         state.clearAction();
      }
   }

   /** Interrupted ult: time moves again and the line stops. */
   public static void cancelRoller(ServerPlayer player, DioState state) {
      if (state.action == HeroAction.ROAD_ROLLER) {
         HeroSpeech.interrupt(player, DioAbilities.ROAD_ROLLER);
         state.rollerStart = null;
         state.rollerTarget = null;
      }
   }

   private static java.util.List<LivingEntity> around(ServerPlayer player, Vec3 at, double radius) {
      return player.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, radius + 1.0, radius),
         e -> DioTargets.hittable(player, e) && e.position().distanceToSqr(at) <= (radius + e.getBbWidth() * 0.5) * (radius + e.getBbWidth() * 0.5));
   }

   private static Vec3 flatAway(Vec3 from, Vec3 center) {
      Vec3 d = new Vec3(from.x - center.x, 0, from.z - center.z);
      return d.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : d.normalize();
   }
}
