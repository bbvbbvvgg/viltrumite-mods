package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusFxS2CPacket;
import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusNet;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineTrigger;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * «Право на ответ» (spec §5.6): a raised palm. A hit inside the window (ticks 4..13) is not
 * received at all; he answers it at once with a flick of the stopped air (8 + half the refused hit)
 * at the attacker, wherever he is within 16 blocks and in sight. Projectiles in the window are
 * turned back. Cooldown 6 s after an answer, 10 s after a miss.
 */
public final class RightOfReply {
   static final double STRIKE_RANGE = 16.0;

   private RightOfReply() {
   }

   static void press(ServerPlayer player, RegulusState state) {
      if (state.replyCooldown > 0 || state.busy()) {
         return;
      }

      state.startMove(RegulusMove.REPLY);
      state.replyAttacker = null;
      state.replyBlocked = 0.0F;
      state.replyLanded = false;
      RegulusSounds.play(player.level(), player.position().add(0.0, 1.3, 0.0), RegulusSounds.REPLY_STANCE, 0.8F, 1.0F);
   }

   static boolean windowOpen(RegulusState state) {
      return state.move == RegulusMove.REPLY && RegulusRules.replyWindow(state.moveTick);
   }

   /** absorbIncoming inside the window: the hit is refused; the reply strike starts. */
   static void refuse(ServerPlayer self, RegulusState state, DamageSource source, float raw) {
      Entity attacker = source.getEntity();
      state.replyBlocked = raw;
      state.replyAttacker = attacker instanceof LivingEntity && attacker != self ? attacker.getUUID() : null;
      ServerLevel level = (ServerLevel) self.level();
      Vec3 at = self.getEyePosition().add(self.getLookAngle().scale(0.5));
      RegulusSounds.play(level, at, RegulusSounds.RECOIL, 1.0F, 1.2F);
      RegulusNet.fx(level, RegulusFxS2CPacket.REPLY_BLOCK, self, attacker == null ? -1 : attacker.getId(), at);
      state.startMove(RegulusMove.REPLY_STRIKE);
      HeroSpeech.trigger(self, HeroLineTrigger.event(RegulusLines.EVENT_REPLY));
   }

   static void tickMove(ServerPlayer player, RegulusState state) {
      if (state.move != RegulusMove.REPLY_STRIKE || state.moveTick != RegulusRules.REPLY_STRIKE_CONTACT) {
         return;
      }

      ServerLevel level = (ServerLevel) player.level();
      state.replyCooldown = RegulusRules.REPLY_COOLDOWN_HIT;
      Entity entity = state.replyAttacker == null ? null : level.getEntity(state.replyAttacker);
      Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.6));
      if (entity instanceof LivingEntity target && RegulusCombat.hittable(player, target, state) && target.distanceToSqr(player) <= STRIKE_RANGE * STRIKE_RANGE
         && RegulusCombat.sees(player, player.getEyePosition(), target)) {
         target.invulnerableTime = 0;
         float damage = RegulusRules.replyDamage(state.replyBlocked);
         if (RegulusCombat.hit(player, target, damage)) {
            state.replyLanded = true;
            RegulusCombat.push(target, target.position().subtract(player.position()), 1.1, 0.3);
         }

         Vec3 at = target.getBoundingBox().getCenter();
         RegulusSounds.play(level, at, RegulusSounds.REPLY_STRIKE, 1.1F, 1.0F);
         RegulusNet.fx(level, RegulusFxS2CPacket.REPLY_STRIKE, player, target.getId(), at, at.subtract(from), damage, 0);
      } else {
         RegulusNet.fx(level, RegulusFxS2CPacket.REPLY_STRIKE, player, -1, from.add(player.getLookAngle().scale(3.0)), player.getLookAngle(), 0.0F, 0);
      }
   }

   /** REPLY ran out without a hit: the long cooldown. */
   static void onEnd(ServerPlayer player, RegulusState state, RegulusMove move) {
      if (move == RegulusMove.REPLY) {
         state.replyCooldown = RegulusRules.REPLY_COOLDOWN_MISS;
      }
   }
}
