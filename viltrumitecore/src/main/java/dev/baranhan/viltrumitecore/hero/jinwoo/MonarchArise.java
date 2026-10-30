package dev.baranhan.viltrumitecore.hero.jinwoo;

import dev.baranhan.viltrumitecore.entity.jinwoo.ShadowSoldierEntity;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * «Армия теней, восстань!» (spec §5.9): one 100-tick server timeline. Clients drive
 * camera, sky, ducking, ghosts and the Domain from the public snapshot
 * (move ARISE + elapsed, DOMAIN flag + action target).
 */
public final class MonarchArise {
   private MonarchArise() {
   }

   static boolean start(ServerPlayer player, JinwooState state) {
      JinwooCombat.autoDraw(player, state);
      state.start(JinwooMove.ARISE, -1);
      state.domainCenter = player.position();
      state.domainLeft = JinwooRules.ULT_DOMAIN;
      state.ultSpoken = HeroSpeech.ability(player, JinwooAbility.MONARCH.id());
      state.summonQueue.clear();
      JinwooSounds.play(player, JinwooSounds.ULT_CHARGE, 2.0F, 1.0F);
      return true;
   }

   static void tick(ServerPlayer player, JinwooState state) {
      int t = state.elapsed;
      player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);
      if (t < JinwooRules.ULT_CANCEL_BEFORE && HeroDamage.isAnchored(player)) {
         cancel(player, state);
         return;
      }
      if (t == JinwooRules.ULT_RISE_START) {
         JinwooSounds.play(player, JinwooSounds.ULT_RISE, 2.5F, 1.0F);
         // Every ready shadow of the roster rises in waves until the active limit.
         int room = JinwooRules.ARMY_ACTIVE - state.roster.activeCount();
         for (ShadowRoster.Entry entry : state.roster.summonOrder(Math.max(0, room))) {
            state.summonQueue.add(entry.serial);
         }
      }
      if (t >= JinwooRules.ULT_RISE_START && t < JinwooRules.ULT_RISE_END && !state.summonQueue.isEmpty() && t % 3 == 0) {
         int serial = state.summonQueue.remove(0);
         ShadowRoster.Entry entry = state.roster.bySerial(serial);
         if (entry != null) {
            int index = state.roster.activeCount();
            double radius = 4.0 + (index % 3) * 2.2;
            Vec3 at = ShadowArmy.ringPoint(player, index, JinwooRules.ARMY_ACTIVE, radius);
            ShadowSoldierEntity s = ShadowArmy.spawn(player, state, entry, at, index);
            if (s != null) {
               JinwooFx.around(player, JinwooFx.STREAM, 1, player.position(), s.position());
            }
         }
      }
      if (t == 40) {
         JinwooSounds.play(player, JinwooSounds.ULT_ROAR, 2.5F, 1.0F);
      }
      if (t == JinwooRules.ULT_WAVE) {
         wave(player, state);
      }
   }

   /** 60-75: the legion's charge, 14 to every hostile in 22 blocks, Slowness II and the Monarch's mark. */
   static void wave(ServerPlayer player, JinwooState state) {
      Vec3 center = state.domainCenter == null ? player.position() : state.domainCenter;
      JinwooSounds.play(player, JinwooSounds.ULT_WAVE, 2.5F, 1.0F);
      JinwooFx.around(player, JinwooFx.WAVE, 3, center, new Vec3(JinwooRules.ULT_RADIUS, 0, 0));
      List<LivingEntity> hostile = JinwooTargets.around(player, center.add(0, 1, 0), JinwooRules.ULT_RADIUS,
         e -> waveHits(e instanceof Enemy, e instanceof Player) && JinwooCombat.mayHit(player, e));
      LivingEntity strongest = null;
      for (LivingEntity e : hostile) {
         JinwooCombat.strike(player, state, e, JinwooRules.ULT_DAMAGE, true, 2);
         if (e.isAlive()) {
            e.addEffect(JinwooCombat.hidden(MobEffects.MOVEMENT_SLOWDOWN, JinwooRules.ULT_SLOW_TICKS, 1));
            if (strongest == null || e.getMaxHealth() > strongest.getMaxHealth()) {
               strongest = e;
            }
         }
      }
      if (strongest != null) {
         state.markTarget = strongest.getUUID();
         state.markTicks = JinwooRules.MARK_TICKS;
      }
      state.order = JinwooState.ArmyOrder.FOLLOW;
      ShadowArmy.rally(player, state);
   }

   /**
    * Pure: the charge strikes hostile mobs and, under the server's PvP rules (checked by
    * {@link JinwooCombat#mayHit}), enemy players — the opponent caught in the scene was only a
    * spectator before (playtest).
    */
   static boolean waveHits(boolean hostileMob, boolean player) {
      return hostileMob || player;
   }

   /** Broken before the rise (control/anchor): line cut, half the mana back, Domain gone. */
   static void cancel(ServerPlayer player, JinwooState state) {
      HeroSpeech.interrupt(player, JinwooAbility.MONARCH.id());
      state.mana = Math.min(JinwooRules.MANA_MAX, state.mana + JinwooRules.ultRefund());
      state.domainLeft = 0;
      state.domainCenter = null;
      state.summonQueue.clear();
      state.endMove();
   }

   /** Domain clock (spec §5.9: 400 t). */
   static void tickDomain(JinwooState state) {
      if (state.domainLeft > 0 && --state.domainLeft == 0) {
         state.domainCenter = null;
      }
   }
}
