package dev.baranhan.viltrumitecore.codex;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Fired on the Forge bus (server side) after a Codex 2.0 entry was newly opened for a player and saved.
 * Other systems (quests, achievements, analytics) can listen; it cannot be cancelled.
 */
public class CodexUnlockEvent extends PlayerEvent {
   private final String entryId;

   public CodexUnlockEvent(ServerPlayer player, String entryId) {
      super(player);
      this.entryId = entryId;
   }

   public String getEntryId() {
      return this.entryId;
   }
}
