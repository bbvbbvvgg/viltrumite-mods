package dev.baranhan.viltrumitecore.hero.thor;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Player feedback: every refused press answers with a sound and an action-bar line (add_hero §4.4). */
public final class ThorFeedback {
   public static final String NOT_READY = "hud.viltrumitecore.thor.not_ready";
   public static final String HANDS_BUSY = "hud.viltrumitecore.thor.hands_busy";
   public static final String BUSY = "hud.viltrumitecore.thor.busy";
   public static final String CONTROLS = "hud.viltrumitecore.thor.controls";

   private ThorFeedback() {
   }

   /** "&lt;move&gt;: in development". */
   public static Component notReady(ThorMove move) {
      return Component.translatable(NOT_READY, Component.translatable(move.translationKey()));
   }

   public static void refuse(ServerPlayer player, Component message) {
      player.playNotifySound(SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, ThorRules.REFUSE_VOLUME, ThorRules.REFUSE_PITCH);
      player.displayClientMessage(message, true);
   }

   /** A sound everyone nearby hears (the player included). */
   public static void play(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
      player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
   }

   /** Draw / holster sound when Mjölnir changed place (also when an item in the hand sent it to the belt). */
   public static void hammerMoved(ServerPlayer player, HammerState before, HammerState now) {
      if (before == now) {
         return;
      }

      if (now == HammerState.IN_HAND) {
         play(player, SoundEvents.ARMOR_EQUIP_NETHERITE, ThorRules.DRAW_VOLUME, ThorRules.DRAW_PITCH);
      } else if (now == HammerState.HOLSTERED) {
         play(player, SoundEvents.ARMOR_EQUIP_CHAIN, ThorRules.HOLSTER_VOLUME, ThorRules.HOLSTER_PITCH);
      }
   }
}
