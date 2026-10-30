package dev.baranhan.viltrumitecore.hero.thor;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Player feedback: every refused press answers with a sound and an action-bar line (add_hero §4.4). */
public final class ThorFeedback {
   public static final String NOT_READY = "hud.viltrumitecore.thor.not_ready";
   public static final String HANDS_BUSY = "hud.viltrumitecore.thor.hands_busy";
   public static final String BUSY = "hud.viltrumitecore.thor.busy";
   public static final String CONTROLS = "hud.viltrumitecore.thor.controls";
   /** "Mjölnir is not with you" (the same key session A uses for its own refusals). */
   public static final String NO_HAMMER = "hud.viltrumitecore.thor.no_hammer";
   /** Audit v3: MMB would take Mjölnir from the belt, but the inventory has no room for the item in the hand. */
   public static final String DRAW_NO_SLOT = "hud.viltrumitecore.thor.draw.no_slot";
   /** Audit v3: B (Crush) with Mjölnir on the belt behind an item: take it first with MMB. */
   public static final String CRUSH_TAKE_FIRST = "hud.viltrumitecore.thor.crush.take_first";

   private ThorFeedback() {
   }

   /** "&lt;move&gt;: in development". */
   public static Component notReady(ThorMove move) {
      return Component.translatable(NOT_READY, Component.translatable(move.translationKey()));
   }

   public static void refuse(ServerPlayer player, Component message) {
      ThorSounds.refuse(player);
      player.displayClientMessage(message, true);
   }

   /** Draw / holster sound when Mjölnir changed place (also when an item in the hand sent it to the belt). */
   public static void hammerMoved(ServerPlayer player, HammerState before, HammerState now) {
      if (before == now) {
         return;
      }

      if (now == HammerState.IN_HAND) {
         ThorSounds.play(player, ThorCue.DRAW);
      } else if (now == HammerState.HOLSTERED) {
         ThorSounds.play(player, ThorCue.HOLSTER);
      }
   }
}
