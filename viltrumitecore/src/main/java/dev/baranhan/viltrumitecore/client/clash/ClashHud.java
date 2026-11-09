package dev.baranhan.viltrumitecore.client.clash;

import dev.baranhan.viltrumitecore.hero.combat.clash.ClashRules;
import dev.baranhan.viltrumitecore.network.packet.ClashStateS2CPacket;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Struggle HUD under the crosshair: a tug bar (my colour left, theirs right, the win lines, the point marker), a
 * beat pip that turns gold inside the surge window, the prompt, and a short result line when it ends.
 */
@OnlyIn(Dist.CLIENT)
public final class ClashHud {
   static final int WIDTH = 182;
   static final int HEIGHT = 7;
   static final int RESULT_TICKS = 40;
   private static final int GOLD = 0xFFFFD84A;
   @Nullable
   private static Component resultText;
   private static int resultColor;
   private static int resultUntil;
   private static int pressedAt = Integer.MIN_VALUE / 2;
   private static int pressedAge;

   private ClashHud() {
   }

   static void result(ClashStateS2CPacket packet, boolean localIsA) {
      boolean won = packet.status == ClashStateS2CPacket.A_WINS && localIsA || packet.status == ClashStateS2CPacket.B_WINS && !localIsA;
      boolean lost = packet.status == ClashStateS2CPacket.A_WINS && !localIsA || packet.status == ClashStateS2CPacket.B_WINS && localIsA;
      String key;
      if (won) {
         key = "won";
         resultColor = GOLD;
      } else if (lost) {
         key = "lost";
         resultColor = 0xFFFF5A4A;
      } else if (packet.status == ClashStateS2CPacket.DRAW) {
         key = "draw";
         resultColor = 0xFFFFA040;
      } else {
         key = "cancelled";
         resultColor = 0xFFB0B0B0;
      }

      resultText = Component.translatable("hud.viltrumitecore.clash." + key);
      resultUntil = guiTicks() + RESULT_TICKS;
   }

   static void pressed(int age) {
      pressedAt = guiTicks();
      pressedAge = age;
   }

   /** Ticks to the nearest beat (display only; the server judges with its own clock). */
   static int fromBeat(int age) {
      if (age < ClashRules.GRACE_TICKS) {
         return ClashRules.BEAT_PERIOD;
      }

      int phase = (age - ClashRules.GRACE_TICKS) % ClashRules.BEAT_PERIOD;
      return Math.min(phase, ClashRules.BEAT_PERIOD - phase);
   }

   private static int guiTicks() {
      return Minecraft.getInstance().gui.getGuiTicks();
   }

   @SubscribeEvent
   public static void onRender(RenderGuiEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.options.hideGui) {
         return;
      }

      GuiGraphics g = event.getGuiGraphics();
      Font font = mc.font;
      int cx = g.guiWidth() / 2;
      int y = g.guiHeight() / 2 + 26;
      int now = guiTicks();
      ClashClient.View view = ClashClient.local();
      if (view == null) {
         if (resultText != null && now < resultUntil) {
            g.drawCenteredString(font, resultText, cx, y - 12, resultColor);
         }

         return;
      }

      ClashStateS2CPacket p = view.last;
      boolean localIsA = p.entityA == mc.player.getId();
      int mine = ClashClient.BeamStyle.of(localIsA ? p.beamA : p.beamB).argb();
      int theirs = ClashClient.BeamStyle.of(localIsA ? p.beamB : p.beamA).argb();
      if (theirs == mine) {
         theirs = 0xFF000000 | (theirs >> 1 & 0x7F7F7F);
      }

      boolean surging = p.flag(localIsA ? ClashStateS2CPacket.FLAG_SURGE_A : ClashStateS2CPacket.FLAG_SURGE_B);
      boolean grace = p.flag(ClashStateS2CPacket.FLAG_GRACE);
      int x0 = cx - WIDTH / 2;
      g.fill(x0 - 1, y - 1, x0 + WIDTH + 1, y + HEIGHT + 1, surging ? GOLD : 0xFF181818);
      double share = Math.max(0.0, Math.min(1.0, localIsA ? view.shown : 1.0 - view.shown));
      int split = x0 + (int)Math.round(WIDTH * share);
      g.fill(x0, y, split, y + HEIGHT, mine);
      g.fill(split, y, x0 + WIDTH, y + HEIGHT, theirs);
      int lo = x0 + (int)Math.round(WIDTH * ClashRules.WIN_MARGIN);
      int hi = x0 + (int)Math.round(WIDTH * (1.0 - ClashRules.WIN_MARGIN));
      g.fill(lo, y - 2, lo + 1, y + HEIGHT + 2, 0xC0FFFFFF);
      g.fill(hi, y - 2, hi + 1, y + HEIGHT + 2, 0xC0FFFFFF);
      g.fill(split - 1, y - 3, split + 1, y + HEIGHT + 3, 0xFFFFFFFF);
      int age = view.age();
      boolean onBeat = !grace && fromBeat(age) <= ClashRules.BEAT_EARLY;
      int pipY = y + HEIGHT + 7;
      int size = onBeat ? 4 : 2;
      g.fill(cx - size, pipY - size, cx + size, pipY + size, onBeat ? GOLD : 0xFF707070);
      if (now - pressedAt < 4) {
         int tick = fromBeat(pressedAge) <= ClashRules.BEAT_EARLY && !grace ? GOLD : 0xFFFFFFFF;
         g.fill(cx - 9, pipY - 1, cx - 6, pipY + 1, tick);
         g.fill(cx + 6, pipY - 1, cx + 9, pipY + 1, tick);
      }

      Component text = grace ? Component.translatable("hud.viltrumitecore.clash.locked")
         : Component.translatable("hud.viltrumitecore.clash.mash", mc.options.keyAttack.getTranslatedKeyMessage());
      g.drawCenteredString(font, text, cx, y - 12, surging ? GOLD : 0xFFFFFFFF);
   }
}
