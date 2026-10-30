package dev.baranhan.viltrumitecore.client.sinclair;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairFlags;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairMove;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairRules;
import dev.baranhan.viltrumitecore.hero.sinclair.act.SinclairRepertoire;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;

/**
 * The big "act copied" card of Audience Participation: when the volunteer's card reaches Sinclair's palm, a
 * readable card opens at the top of the screen with the act's name, whose ultimate it was, what the act does and
 * the window countdown. It holds {@link #HOLD} ticks (6 s) and fades; performing the act closes it early, and the
 * small HUD line ({@link SinclairHud}) stays for the rest of the window. Final integration (#91 + #92): the card
 * waits for the copy cinematic and opens with the window, so its full hold is readable after the scene.
 * Client-only, reads the local snapshot.
 */
public final class ActCardPopup {
   static final int FADE_IN = 6;
   static final int HOLD = 120;
   static final int FADE_OUT = 16;
   static final int TOTAL = FADE_IN + HOLD + FADE_OUT;
   static final int WIDTH = 200;

   private static int age = -1;
   private static boolean shown;
   private static int gone;
   @Nullable
   private static Component from;

   private ActCardPopup() {
   }

   /**
    * Pure: the card is out once the capture reaches the card tick (the shape is synced) or while it is held.
    * Never during the copy cinematic (#91): the scene shows the card on stage and the popup waits for it.
    */
   static boolean cardOut(@Nullable SinclairMove move, int elapsed, boolean cardHeld, boolean hasShape) {
      if (move == SinclairMove.COPY) {
         return false;
      }

      return hasShape && (cardHeld || move == SinclairMove.CAPTURE && elapsed >= SinclairRules.ULT_CARD_TICK);
   }

   /**
    * Pure: the popup clock is paused (age held at 0, nothing drawn) while the capture is still running — whether
    * the copy cinematic (#91) follows is only known when the capture ends, and a card flashing in for the last
    * capture ticks and vanishing under the cinematic read as a glitch. Without the cinematic the window opens on
    * the next tick and the card runs its full {@link #HOLD} from there; with it, after the scene.
    */
   static boolean waiting(@Nullable SinclairMove move) {
      return move == SinclairMove.CAPTURE || move == SinclairMove.COPY;
   }

   /** Pure: opacity 0..1 for the popup age in ticks (fade in, hold, fade out). */
   static float alpha(float age) {
      if (age < 0.0F || age >= TOTAL) {
         return 0.0F;
      }

      if (age < FADE_IN) {
         return age / FADE_IN;
      }

      return age < FADE_IN + HOLD ? 1.0F : 1.0F - (age - FADE_IN - HOLD) / FADE_OUT;
   }

   /** Pure: the age to jump to when the act is performed (straight into the fade out). */
   static int closeEarly(int age) {
      return Math.max(age, FADE_IN + HOLD);
   }

   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      HeroPublicSnapshot snapshot = mc.player == null ? null : SinclairView.of(mc.player);
      if (snapshot == null || mc.level == null) {
         age = -1;
         shown = false;
         from = null;
         return;
      }

      SinclairMove move = SinclairView.move(snapshot);
      boolean held = SinclairView.flag(snapshot, SinclairFlags.CARD_HELD);
      boolean out = cardOut(move, snapshot.actionElapsed(), held, SinclairView.shape(snapshot) != null);
      if (move == SinclairMove.CAPTURE && snapshot.controlTargetId() >= 0) {
         Entity volunteer = mc.level.getEntity(snapshot.controlTargetId());
         if (volunteer != null) {
            from = whose(volunteer);
         }
      }

      if (move == SinclairMove.COPY) {
         // The copy cinematic holds the stage: drop a pending card without a fade; it opens afresh with the window.
         shown = false;
         age = -1;
         gone = 0;
         return;
      }

      if (out) {
         gone = 0;
         if (!shown) {
            shown = true;
            age = 0;
         }
      } else if (shown) {
         if (move != null && move.isAct()) {
            shown = false;
            age = age >= 0 ? closeEarly(age) : -1;
         } else if (move != SinclairMove.CAPTURE && ++gone > 3) {
            // A few ticks of grace across the capture -> window hand-off before the card counts as gone.
            shown = false;
            age = age >= 0 ? closeEarly(age) : -1;
         }
      }

      if (!shown && age < 0 && !waiting(move)) {
         from = null;
      }

      if (age >= 0 && waiting(move)) {
         age = 0;
      } else if (age >= 0 && ++age >= TOTAL) {
         age = -1;
      }
   }

   /** "Thor · Steve" for a hero player, the plain name otherwise. */
   private static Component whose(Entity volunteer) {
      if (volunteer instanceof HeroPlayer heroPlayer && volunteer instanceof Player) {
         HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
         if (snapshot != null && snapshot.heroId() != HeroId.HUMAN) {
            String key = "hero.viltrumitecore." + snapshot.heroId().key();
            if (I18n.exists(key)) {
               return Component.translatable(key).append(" · ").append(volunteer.getDisplayName());
            }
         }
      }

      return volunteer.getDisplayName();
   }

   public static void onRenderGui(RenderGuiEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (age < 0 || mc.options.hideGui || mc.player == null) {
         return;
      }

      HeroPublicSnapshot snapshot = SinclairView.of(mc.player);
      if (snapshot != null && waiting(SinclairView.move(snapshot))) {
         return;
      }

      ActShape shape = snapshot == null ? null : SinclairView.shape(snapshot);
      float a = alpha(age + event.getPartialTick());
      if (shape == null || a <= 0.05F) {
         return;
      }

      draw(event.getGuiGraphics(), mc.font, snapshot, shape, a);
   }

   private static int argb(float alpha, int rgb) {
      return Mth.clamp((int) (alpha * 255.0F), 5, 255) << 24 | rgb & 0xFFFFFF;
   }

   private static void draw(GuiGraphics g, Font font, HeroPublicSnapshot snapshot, ActShape shape, float a) {
      int color = SinclairView.color(snapshot);
      boolean fallback = SinclairView.flag(snapshot, SinclairFlags.FALLBACK);
      Component name = Component.translatable(SinclairRepertoire.nameKey(shape));
      if (fallback) {
         name = Component.translatable("hud.viltrumitecore.sinclair.fallback", name);
      }

      List<FormattedCharSequence> desc = font.split(Component.translatable(SinclairRepertoire.nameKey(shape) + ".desc"), WIDTH - 20);
      int lines = Math.min(4, desc.size());
      int h = 14 + 16 + (from != null ? 12 : 0) + lines * 10 + 26;
      int x = (g.guiWidth() - WIDTH) / 2;
      int y = 18 - Math.round((1.0F - Math.min(1.0F, a * 1.5F)) * 12.0F);

      g.fill(x - 2, y - 2, x + WIDTH + 2, y + h + 2, argb(a * 0.9F, color));
      g.fill(x, y, x + WIDTH, y + h, argb(a * 0.94F, 0x150F22));
      g.fill(x + 2, y + 2, x + WIDTH - 2, y + 3, argb(a, SinclairPalette.GOLD));
      g.fill(x + 2, y + h - 3, x + WIDTH - 2, y + h - 2, argb(a, SinclairPalette.GOLD));

      RenderSystem.enableBlend();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, a);
      g.blit(SinclairAssets.ACT_CARD_ICON, x + 6, y + 6, 16, 16, 0.0F, 0.0F, 16, 16, 16, 16);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

      int ty = y + 6;
      g.drawString(font, Component.translatable("hud.viltrumitecore.sinclair.card_title"), x + 28, ty, argb(a, SinclairPalette.GOLD), true);
      ty += 11;
      g.pose().pushPose();
      g.pose().translate(x + 28, ty, 0.0F);
      g.pose().scale(1.5F, 1.5F, 1.0F);
      int nameWidth = font.width(name);
      float fit = Math.min(1.0F, (WIDTH - 34) / (nameWidth * 1.5F));
      g.pose().scale(fit, fit, 1.0F);
      g.drawString(font, name, 0, 0, argb(a, brighten(color)), true);
      g.pose().popPose();
      ty += 16;
      if (from != null) {
         g.drawString(font, Component.translatable("hud.viltrumitecore.sinclair.card_from", from), x + 10, ty, argb(a, 0xC9B6FF), true);
         ty += 12;
      }

      for (int i = 0; i < lines; i++) {
         g.drawString(font, desc.get(i), x + 10, ty, argb(a, 0xEDE6D6), false);
         ty += 10;
      }

      ty += 4;
      if (SinclairView.flag(snapshot, SinclairFlags.CARD_HELD)) {
         int left = SinclairView.extra(snapshot, SinclairFlags.EXTRA_WINDOW);
         float k = Mth.clamp(left / (float) SinclairRules.ULT_WINDOW, 0.0F, 1.0F);
         g.fill(x + 10, ty + 10, x + WIDTH - 10, ty + 12, argb(a * 0.8F, 0x3B2A63));
         g.fill(x + 10, ty + 10, x + 10 + Math.round((WIDTH - 20) * k), ty + 12, argb(a, color));
         g.drawString(font, Component.translatable("hud.viltrumitecore.sinclair.window", SinclairHud.seconds(left)), x + 10, ty, argb(a, 0xF2C14E), true);
      }
   }

   /** Lifts dark act colours so the name stays readable on the dark card. */
   static int brighten(int rgb) {
      int r = rgb >> 16 & 0xFF;
      int gr = rgb >> 8 & 0xFF;
      int b = rgb & 0xFF;
      int max = Math.max(r, Math.max(gr, b));
      if (max >= 0xC0) {
         return rgb & 0xFFFFFF;
      }

      float k = max == 0 ? 0.0F : 0xC0 / (float) max;
      return Math.min(255, Math.round(r * k + 40)) << 16 | Math.min(255, Math.round(gr * k + 40)) << 8 | Math.min(255, Math.round(b * k + 40));
   }
}
