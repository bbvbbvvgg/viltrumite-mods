package dev.baranhan.viltrumitecore.hero.reinhard.reid;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.AbilityHandler;
import net.minecraft.server.level.ServerPlayer;

/**
 * Holy Sword slot 1 (spec §7.1): draw Reid (the 6 s event) or sheathe it (1 s).
 * No cooldown; the gate refuses it while a timeline runs, and Reid's own busy
 * phases (drawing / sheathing) refuse every press. Server only.
 */
public final class ReidToggle implements AbilityHandler {
   @Override
   public boolean tryStart(ServerPlayer player, ReinhardState state) {
      if (state.reid.phase() == Reid.Phase.SHEATHED && !ReinhardRules.canDraw()) {
         return false;
      }

      if (!state.reid.toggle()) {
         return false;
      }
      if (state.reid.phase() == Reid.Phase.DRAWING) {
         endDraw(state);
         state.drawEvent = ReidDrawEvent.start(player);
      } else if (state.reid.phase() == Reid.Phase.SHEATHING) {
         ReinhardSounds.play(player, ReinhardSounds.REID_SHEATHE.get(), 0.8F, 1.0F);
      }

      return true;
   }

   /** A cleanup (death, logout, hero change) sheathes at once; a control or dimension change cuts a running draw short. */
   @Override
   public void cancel(ServerPlayer player, ReinhardState state, CleanupReason reason) {
      if (reason != null) {
         state.reid.reset();
      } else if (state.reid.phase() == Reid.Phase.DRAWING) {
         state.reid.sheatheNow();
      }
      endDraw(state);
   }

   /** Stops a running draw scene (the lift lets go, clients get END). */
   static void endDraw(ReinhardState state) {
      if (state.drawEvent != null) {
         state.drawEvent.abort();
         state.drawEvent = null;
      }
   }
}
