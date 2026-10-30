package dev.baranhan.viltrumitecore.hero.homelander;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Server-side sounds for refusal and overheat (vanilla sounds, spec §5, §6.3). */
final class HomelanderFeedback {
   private HomelanderFeedback() {
   }

   /** Quiet refusal click, heard only by the player. */
   static void refuse(ServerPlayer player) {
      player.playNotifySound(SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.4F, 1.6F);
   }

   /** Hiss of overheated eyes; everyone nearby hears it. Smoke is drawn on the client from the snapshot lock flag. */
   static void overheat(ServerPlayer player) {
      player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.4F);
      HomelanderLines.say(player, HomelanderLines.EVENT_OVERHEAT);
   }
}
