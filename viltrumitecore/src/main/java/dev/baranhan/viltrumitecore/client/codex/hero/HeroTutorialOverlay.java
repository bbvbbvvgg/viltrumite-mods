package dev.baranhan.viltrumitecore.client.codex.hero;

import dev.baranhan.viltrumitecore.client.codex.CodexClientState;
import dev.baranhan.viltrumitecore.client.gui.KeyLabels;
import dev.baranhan.viltrumitecore.codex.CodexNetwork;
import dev.baranhan.viltrumitecore.codex.data.CodexData;
import dev.baranhan.viltrumitecore.codex.data.HeroCard;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * First-time hero tutorial: three short steps at the top of the screen right after the player becomes a
 * hero he has never finished the tutorial for (any path: hero select screen, first-join choice, a wish).
 * A step ends when its key is pressed, or by itself after a while (sooner if the key is unbound). When the
 * last step ends the server remembers it, so the tutorial never shows again for that hero.
 */
@Mod.EventBusSubscriber(modid = "viltrumitecore", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HeroTutorialOverlay {
   static final int STEP_TICKS = 400;
   static final int UNBOUND_STEP_TICKS = 160;
   static final int PANEL_W = 270;
   @Nullable
   private static HeroId hero;
   @Nullable
   private static HeroCard card;
   private static int step;
   private static int stepTicks;
   private static boolean wasDown;
   @Nullable
   private static HeroId lastSeen;
   private static boolean seenInit;

   private HeroTutorialOverlay() {
   }

   /** Starts the tutorial for this hero unless it was already done (or the hero has none). */
   public static void offer(@Nullable HeroId next) {
      if (next == null || next == HeroId.HUMAN || CodexClientState.hasTutorial(next.key()) || next == hero) {
         return;
      }
      HeroCard c = CodexData.get().card(next.key());
      if (c.tutorial().size() != HeroCard.TUTORIAL_STEPS) {
         return;
      }
      hero = next;
      card = c;
      step = 0;
      stepTicks = 0;
      KeyMapping key = key(c.tutorial().get(0).key());
      wasDown = key != null && key.isDown();
   }

   public static boolean active() {
      return hero != null;
   }

   @Nullable
   static KeyMapping key(String name) {
      for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
         if (mapping.getName().equals(name)) {
            return mapping;
         }
      }
      return null;
   }

   @Nullable
   private static HeroId localHero() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && (Object)mc.player instanceof HeroPlayer heroPlayer && heroPlayer.getHeroId() != null) {
         return HeroId.fromKey(heroPlayer.getHeroId().key());
      }
      return null;
   }

   @SubscribeEvent
   public static void onTick(TickEvent.ClientTickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (event.phase != TickEvent.Phase.END || mc.player == null || !CodexClientState.received()) {
         return;
      }
      HeroId now = localHero();
      if (!seenInit) {
         seenInit = true;
         lastSeen = now;
      } else if (now != lastSeen) {
         lastSeen = now;
         offer(now);
      }
      if (hero == null || card == null || mc.screen != null) {
         return;
      }
      if (now != hero) {
         hero = null;
         card = null;
         return;
      }
      HeroCard.TutorialStep current = card.tutorial().get(step);
      KeyMapping key = key(current.key());
      boolean down = key != null && key.isDown();
      stepTicks++;
      int limit = key == null || key.isUnbound() ? UNBOUND_STEP_TICKS : STEP_TICKS;
      boolean advance = down && !wasDown || stepTicks >= limit;
      wasDown = down;
      if (!advance) {
         return;
      }
      mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4F, 0.35F));
      step++;
      stepTicks = 0;
      if (step >= card.tutorial().size()) {
         String done = hero.key();
         hero = null;
         card = null;
         mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.6F, 0.4F));
         CodexNetwork.sendToServer(mc.getConnection() == null ? null : mc.getConnection().getConnection(), new CodexNetwork.TutorialDone(done));
      } else {
         KeyMapping next = key(card.tutorial().get(step).key());
         wasDown = next != null && next.isDown();
      }
   }

   @SubscribeEvent
   public static void onRender(RenderGuiEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      HeroId shown = hero;
      HeroCard c = card;
      if (shown == null || c == null || mc.screen != null || mc.options.hideGui) {
         return;
      }
      GuiGraphics g = event.getGuiGraphics();
      int sw = event.getWindow().getGuiScaledWidth();
      int w = Math.min(PANEL_W, sw - 20);
      int x = (sw - w) / 2;
      int y = 8;
      int accent = 0xFF000000 | (c.accent() & 0xFFFFFF);
      HeroCard.TutorialStep current = c.tutorial().get(Math.min(step, c.tutorial().size() - 1));
      KeyMapping key = key(current.key());
      String label = key == null ? "" : KeyLabels.shortName(key);
      Component keyText = label.isEmpty() ? Component.translatable("heroselect.viltrumitecore.tutorial.unbound") : Component.literal(label);
      List<FormattedCharSequence> lines = mc.font.split(Component.translatable(current.text(), keyText), w - 16);
      int n = Math.min(2, lines.size());
      int h = 26 + n * 10;
      g.fill(x, y, x + w, y + h, 0xD00C0E13);
      g.fill(x, y, x + w, y + 2, accent);
      Component header = Component.translatable("heroselect.viltrumitecore.tutorial.header", HeroSelectScreen.heroName(shown), step + 1, c.tutorial().size());
      g.drawString(mc.font, header, x + 8, y + 6, accent, false);
      for (int i = 0; i < n; i++) {
         g.drawString(mc.font, lines.get(i), x + 8, y + 18 + i * 10, 0xFFFFFFFF, false);
      }
      int limit = key == null || key.isUnbound() ? UNBOUND_STEP_TICKS : STEP_TICKS;
      float part = (step + Math.min(1.0F, stepTicks / (float)limit)) / c.tutorial().size();
      g.fill(x, y + h - 2, x + w, y + h - 1, 0x30FFFFFF);
      g.fill(x, y + h - 2, x + (int)(w * part), y + h - 1, accent);
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      hero = null;
      card = null;
      lastSeen = null;
      seenInit = false;
   }
}
