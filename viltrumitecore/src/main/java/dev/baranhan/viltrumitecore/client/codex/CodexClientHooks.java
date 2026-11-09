package dev.baranhan.viltrumitecore.client.codex;

import dev.baranhan.viltrumitecore.client.codex.hero.HeroSelectScreen;
import dev.baranhan.viltrumitecore.client.codex.hero.HeroTutorialOverlay;
import dev.baranhan.viltrumitecore.codex.data.CodexData;
import dev.baranhan.viltrumitecore.codex.data.CodexEntryDef;
import dev.baranhan.viltrumitecore.codex.hero.HeroSelectService;
import dev.baranhan.viltrumitecore.hero.HeroId;
import net.minecraft.client.Minecraft;

/** Client reactions to codex packets (only ever called on the client main thread). */
public final class CodexClientHooks {
   private CodexClientHooks() {
   }

   /** A new Codex 2.0 entry is open: a toast with its category glyph and title. */
   public static void unlocked(String id) {
      CodexEntryDef entry = CodexData.get().entry(id);
      if (entry != null) {
         Minecraft.getInstance().getToasts().addToast(new CodexUnlockToast(entry));
      }
   }

   public static void selectResult(HeroSelectService.Status status, String hero, int cooldownTicks) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.screen instanceof HeroSelectScreen screen) {
         screen.onResult(status, hero, cooldownTicks);
      } else if (status != HeroSelectService.Status.READY && mc.player != null) {
         mc.player.displayClientMessage(HeroSelectScreen.statusText(status, hero, cooldownTicks), true);
      }
      if (status == HeroSelectService.Status.OK) {
         HeroId id = HeroId.fromKey(hero);
         if (id != null) {
            HeroTutorialOverlay.offer(id);
         }
      }
   }

   public static void openHeroSelect() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.setScreen(new HeroSelectScreen());
      }
   }
}
