package dev.baranhan.viltrumitecore.hero.dio;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.dio.net.DioFxS2CPacket;
import dev.baranhan.viltrumitecore.hero.dio.net.DioNet;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineTrigger;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** The World: summon/dismiss, the LMB chain, the MUDA barrage with its finisher, and the stand's hits. */
public final class DioCombat {
   private DioCombat() {
   }

   // --- summon / dismiss -------------------------------------------------------------

   public static void toggle(ServerPlayer player, DioState state) {
      if (state.standOut()) {
         dismiss(player, state);
      } else {
         summon(player, state);
      }
   }

   public static void summon(ServerPlayer player, DioState state) {
      if (state.standOut()) {
         return;
      }

      state.standPhase = DioRules.STAND_SUMMONING;
      state.standElapsed = 0;
      DioSounds.play(player.serverLevel(), player.position().add(0, 1, 0), DioSounds.STAND_SUMMON, 1.0F, 1.0F);
      DioNet.fx(player, DioFxS2CPacket.SUMMON, player.position().add(0, 1.2, 0));
      HeroSpeech.trigger(player, HeroLineTrigger.event(DioLines.SUMMON_EVENT));
   }

   public static void dismiss(ServerPlayer player, DioState state) {
      if (!state.standOut()) {
         return;
      }

      // Stand actions end with the stand.
      if (state.action == HeroAction.PRIMARY_ATTACK || state.action == HeroAction.MUDA_BARRAGE) {
         state.clearAction();
      }

      state.guardHeld = false;
      state.standPhase = DioRules.STAND_DISMISSING;
      state.standElapsed = 0;
      DioSounds.play(player.serverLevel(), player.position().add(0, 1, 0), DioSounds.STAND_DISMISS, 0.9F, 1.0F);
   }

   /** Stand phase clock, once per tick. */
   public static void tickStand(DioState state) {
      state.standElapsed++;
      if (state.standPhase == DioRules.STAND_SUMMONING && state.standElapsed >= DioRules.SUMMON_TICKS) {
         state.standPhase = DioRules.STAND_OUT;
         state.standElapsed = 0;
      } else if (state.standPhase == DioRules.STAND_DISMISSING && state.standElapsed >= DioRules.DISMISS_TICKS) {
         state.standPhase = DioRules.STAND_NONE;
         state.standElapsed = 0;
      }

      if (state.standElapsed > 100000) {
         state.standElapsed = 100000;
      }
   }

   // --- LMB chain --------------------------------------------------------------------

   public static void press(ServerPlayer player, DioState state) {
      if (state.action == HeroAction.PRIMARY_ATTACK) {
         if (state.length - state.elapsed <= DioRules.COMBO_BUFFER) {
            state.comboBuffered = true;
         }

         return;
      }

      if (state.busy() || !DioRules.comboReady(state.lastStep, state.sinceStrikeEnd)) {
         return;
      }

      summon(player, state);
      startStep(player, state, DioRules.nextComboStep(state.lastStep, state.sinceStrikeEnd));
   }

   private static void startStep(ServerPlayer player, DioState state, int step) {
      state.comboStep = step;
      state.comboBuffered = false;
      state.startAction(HeroAction.PRIMARY_ATTACK, DioRules.COMBO_LENGTH[step]);
      DioSounds.play(player.serverLevel(), player.getEyePosition(), DioSounds.STAND_WHOOSH, 0.7F, step == 2 ? 0.8F : 1.05F + player.getRandom().nextFloat() * 0.1F);
   }

   public static void tickCombo(ServerPlayer player, DioState state) {
      int step = state.comboStep;
      if (state.elapsed == DioRules.COMBO_CONTACT[step]) {
         List<LivingEntity> targets = DioTargets.cone(player, DioRules.STAND_REACH, DioRules.STAND_CONE_DEG);
         int max = step == 2 ? 3 : 1;
         int hits = 0;
         for (LivingEntity target : targets) {
            if (hits >= max) {
               break;
            }

            strike(player, state, target, DioRules.COMBO_DAMAGE[step], DioRules.COMBO_KNOCKBACK[step], step == 2 ? DioFxS2CPacket.FINISHER : DioFxS2CPacket.STAND_HIT,
               DioRules.BLOOD_STAND_HIT, step == 2 ? DioSounds.STAND_HOOK : DioSounds.STAND_HIT);
            hits++;
         }

         if (hits == 0) {
            Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(DioRules.STAND_REACH * 0.8));
            DioNet.fx(player, DioFxS2CPacket.STAND_MISS, at, player.getLookAngle(), step == 2 ? 1.4F : 1.0F, step);
         }
      }

      if (state.elapsed >= state.length) {
         boolean buffered = state.comboBuffered;
         state.lastStep = step;
         state.sinceStrikeEnd = 0;
         state.clearAction();
         if (buffered && step < 2) {
            startStep(player, state, step + 1);
         }
      }
   }

   // --- MUDA barrage -------------------------------------------------------------------

   public static void startBarrage(ServerPlayer player, DioState state) {
      summon(player, state);
      state.startAction(HeroAction.MUDA_BARRAGE, DioRules.BARRAGE_WINDUP + DioRules.BARRAGE_MAX);
      state.barrageHeld = true;
      state.finisher = false;
      DioSounds.play(player.serverLevel(), player.getEyePosition(), DioSounds.BARRAGE_RUSH, 1.0F, 1.0F);
   }

   public static void tickBarrage(ServerPlayer player, DioState state) {
      if (!state.finisher) {
         if (DioRules.barrageHitTick(state.elapsed)) {
            int hits = 0;
            for (LivingEntity target : DioTargets.cone(player, DioRules.BARRAGE_RANGE, DioRules.BARRAGE_CONE_DEG)) {
               if (hits++ >= 4) {
                  break;
               }

               strike(player, state, target, DioRules.BARRAGE_HIT_DAMAGE, 0.06F, DioFxS2CPacket.BARRAGE_HIT, DioRules.BLOOD_BARRAGE_HIT,
                  (state.elapsed / DioRules.BARRAGE_HIT_INTERVAL) % 2 == 0 ? DioSounds.BARRAGE_HIT : null);
            }

            if (hits == 0 && state.elapsed % 4 == 0) {
               Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(2.6));
               DioNet.fx(player, DioFxS2CPacket.STAND_MISS, at, player.getLookAngle(), 0.5F, 9);
            }
         }

         if (state.elapsed > DioRules.BARRAGE_WINDUP && (state.elapsed - DioRules.BARRAGE_WINDUP) % 12 == 0) {
            DioSounds.play(player.serverLevel(), player.getEyePosition(), DioSounds.BARRAGE_RUSH, 0.9F, 0.95F + player.getRandom().nextFloat() * 0.1F);
         }

         boolean released = !state.barrageHeld && state.elapsed >= DioRules.BARRAGE_MIN;
         if (released || state.elapsed >= DioRules.BARRAGE_WINDUP + DioRules.BARRAGE_MAX) {
            state.finisher = true;
            state.elapsed = 0;
            state.length = DioRules.FINISHER_LENGTH;
            state.cooldowns[DioRules.CD_BARRAGE] = DioRules.BARRAGE_COOLDOWN;
            DioSounds.play(player.serverLevel(), player.getEyePosition(), DioSounds.STAND_WHOOSH, 1.0F, 0.7F);
         }

         return;
      }

      if (state.elapsed == DioRules.FINISHER_CONTACT) {
         int hits = 0;
         for (LivingEntity target : DioTargets.cone(player, DioRules.STAND_REACH, DioRules.STAND_CONE_DEG + 8)) {
            if (hits++ >= 4) {
               break;
            }

            strike(player, state, target, DioRules.FINISHER_DAMAGE, DioRules.FINISHER_KNOCKBACK, DioFxS2CPacket.FINISHER, DioRules.BLOOD_STAND_HIT, DioSounds.FINISHER);
         }

         if (hits == 0) {
            Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(DioRules.STAND_REACH * 0.8));
            DioNet.fx(player, DioFxS2CPacket.STAND_MISS, at, player.getLookAngle(), 1.6F, 3);
         }
      }

      if (state.elapsed >= state.length) {
         state.lastStep = -1;
         state.sinceStrikeEnd = -1;
         state.clearAction();
      }
   }

   // --- the hit itself ------------------------------------------------------------------

   /**
    * One stand hit. A victim stopped by this DIO's field takes nothing now: the
    * hit goes into the ledger and lands at the resume.
    */
   public static void strike(ServerPlayer player, DioState state, LivingEntity target, float base, float knockback, int fxKind, int blood,
      net.minecraftforge.registries.RegistryObject<net.minecraft.sounds.SoundEvent> sound) {
      float damage = DioRules.standDamage(base, state.inSun);
      Vec3 away = target.position().subtract(player.position());
      Vec3 flat = new Vec3(away.x, 0, away.z);
      flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z) : flat.normalize();
      Vec3 contact = target.getBoundingBox().getCenter().add(flat.scale(-target.getBbWidth() * 0.5)).add(0, target.getBbHeight() * 0.12, 0);
      ServerLevel level = player.serverLevel();
      if (DioTimeStop.defer(player, target, damage, flat.scale(knockback))) {
         DioHero.addBlood(player, blood);
         DioNet.fx(target, DioFxS2CPacket.FROZEN_HIT, contact, flat, Math.max(0.6F, knockback), fxKind);
         if (sound != null) {
            DioSounds.play(level, contact, sound, 0.55F, 0.75F);
         }

         return;
      }

      target.invulnerableTime = 0;
      boolean hurt = target.hurt(player.damageSources().playerAttack(player), damage);
      if (hurt) {
         DioHero.addBlood(player, blood);
         if (knockback > 0.0F && HeroRegistry.allowsImpulse(target)) {
            target.knockback(knockback, -flat.x, -flat.z);
            if (knockback >= 0.8F) {
               target.setDeltaMovement(target.getDeltaMovement().add(0, 0.18 + knockback * 0.1, 0));
            }

            target.hurtMarked = true;
         }
      }

      DioNet.fx(target, fxKind, contact, flat, hurt ? Math.max(0.5F, knockback) : 0.3F, hurt ? 1 : 0);
      if (sound != null) {
         DioSounds.play(level, contact, sound, fxKind == DioFxS2CPacket.BARRAGE_HIT ? 0.6F : 1.0F, 0.92F + player.getRandom().nextFloat() * 0.16F);
      }
   }

   // --- guard -------------------------------------------------------------------------------

   public static boolean guarding(DioState state) {
      return state.guardHeld && state.standOut() && !state.busy();
   }
}
