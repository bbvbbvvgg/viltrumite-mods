package dev.baranhan.viltrumitecore.client.codex;

import dev.baranhan.viltrumitecore.codex.CodexRecord;
import javax.annotation.Nullable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * The local player's encyclopedia progress as the server last sent it (codex:snapshot): which heroes this
 * player has chosen and which contextual interactions they have discovered. Only the own record ever reaches
 * a client, so another player's choices can never unlock anything here. Cleared on logout, so a different
 * world or server never sees the previous one's progress.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class CodexClientState {
   private static volatile CodexRecord record = new CodexRecord();
   private static volatile int revision;

   private CodexClientState() {
   }

   public static void accept(CodexRecord snapshot) {
      record = snapshot == null ? new CodexRecord() : snapshot.copy();
      revision++;
   }

   public static boolean hasHero(@Nullable String heroKey) {
      return record.hasHero(heroKey);
   }

   public static boolean hasInteraction(@Nullable String id) {
      return record.hasInteraction(id);
   }

   public static CodexRecord snapshot() {
      return record.copy();
   }

   /** Bumped on every snapshot: screens that cache unlock state refresh when it changes. */
   public static int revision() {
      return revision;
   }

   public static void clear() {
      record = new CodexRecord();
      revision++;
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      clear();
   }
}
