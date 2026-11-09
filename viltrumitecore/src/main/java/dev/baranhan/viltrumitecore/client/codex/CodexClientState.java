package dev.baranhan.viltrumitecore.client.codex;

import dev.baranhan.viltrumitecore.codex.CodexRecord;
import dev.baranhan.viltrumitecore.codex.data.CodexData;
import dev.baranhan.viltrumitecore.codex.data.CodexEntryDef;
import dev.baranhan.viltrumitecore.codex.data.CodexUnlockRules;
import javax.annotation.Nullable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * The local player's encyclopedia progress as the server last sent it (codex:snapshot): which heroes this
 * player has chosen, which contextual interactions they have discovered, which Codex 2.0 entries are open and
 * which hero tutorials are finished. Only the own record ever reaches a client, so another player's progress can
 * never unlock anything here. Cleared on logout, so a different world or server never sees the previous one's
 * progress.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class CodexClientState {
   private static volatile CodexRecord record = new CodexRecord();
   private static volatile int revision;
   private static volatile boolean received;

   private CodexClientState() {
   }

   public static void accept(CodexRecord snapshot) {
      record = snapshot == null ? new CodexRecord() : snapshot.copy();
      received = true;
      revision++;
   }

   public static boolean hasHero(@Nullable String heroKey) {
      return record.hasHero(heroKey);
   }

   public static boolean hasInteraction(@Nullable String id) {
      return record.hasInteraction(id);
   }

   public static boolean hasEntry(@Nullable String id) {
      return record.hasEntry(id);
   }

   public static boolean hasTutorial(@Nullable String hero) {
      return record.hasTutorial(hero);
   }

   /** True once the server sent the first snapshot of this session (tutorials wait for it). */
   public static boolean received() {
      return received;
   }

   /** Whether a Codex 2.0 entry is readable: open by default, opened by the server or matched by local progress. */
   public static boolean isUnlocked(@Nullable CodexEntryDef entry) {
      return entry != null && CodexUnlockRules.isUnlocked(entry, record);
   }

   public static boolean isUnlocked(@Nullable String id) {
      return isUnlocked(CodexData.get().entry(id));
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
      received = false;
      revision++;
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      clear();
   }
}
