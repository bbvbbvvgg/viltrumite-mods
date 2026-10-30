package dev.baranhan.viltrumitecore.client.regulus;

import dev.baranhan.viltrumitecore.client.hero.PanelStyle;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusFlags;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Regulus HUD left of the ability bar (spec §8): the heart strain bar (ivory → gold, red-ink when
 * the hostless Lion nears its seizure), the heart line (host / hostless countdown / Lion resting),
 * subjects with the hop timer, the Greed's Touch combo pips, the reply window and the judgement
 * line while Kingdom of One plays. Everything comes from the synced snapshot.
 */
public final class RegulusHud {
   public static final int BAR_WIDTH = 46;
   public static final int BAR_HEIGHT = 4;
   static final int COMBO_STEPS = 3;

   private RegulusHud() {
   }

   public static void draw(GuiGraphics g, PanelStyle.Bounds abilityBar, Player player, HeroPublicSnapshot s) {
      Minecraft mc = Minecraft.getInstance();
      Font font = mc.font;
      int x1 = abilityBar.x() - 3;
      int x0 = x1 - BAR_WIDTH;
      int y = abilityBar.y();
      long time = player.level().getGameTime();
      int strain = RegulusView.strain(s);
      boolean lion = RegulusView.lion(s);
      boolean hostAlive = RegulusView.flag(s, RegulusFlags.HOST_ALIVE);
      boolean warn = RegulusView.flag(s, RegulusFlags.STRAIN_WARN);
      boolean seized = RegulusView.flag(s, RegulusFlags.SEIZED);
      // strain
      g.fill(x0 - 1, y - 1, x1 + 1, y + BAR_HEIGHT + 1, 0xFF000000);
      g.fill(x0, y, x1, y + BAR_HEIGHT, 0xFF2A2633);
      int fill = Math.round(Mth.clamp(strain / (float) RegulusRules.STRAIN_MAX, 0.0F, 1.0F) * BAR_WIDTH);
      int colour = strainColour(strain, warn, seized, time);
      g.fill(x0, y, x0 + fill, y + BAR_HEIGHT, 0xFF000000 | colour);
      g.fill(x0, y, x0 + fill, y + 1, 0xFF000000 | RegulusPalette.mix(colour, 0xFFFFFF, 0.5F));
      int mark = x0 + Math.round(RegulusRules.STRAIN_WARN / (float) RegulusRules.STRAIN_MAX * BAR_WIDTH);
      g.fill(mark, y - 1, mark + 1, y + BAR_HEIGHT + 1, 0xFF000000 | RegulusPalette.HEART);
      // the heart line, above the bar
      Component line = heartLine(mc, s, lion, hostAlive, seized, strain);
      if (line != null) {
         int c = seized ? RegulusPalette.mix(RegulusPalette.INK, RegulusPalette.HEART, 0.6F) : !hostAlive && lion ? RegulusPalette.HEART : RegulusPalette.IVORY;
         g.drawString(font, line, x1 - font.width(line), y - 12, 0xFF000000 | c, true);
      }

      // subjects and the hop timer, left of the bar
      int subjects = RegulusView.extra(s, RegulusFlags.EXTRA_SUBJECTS);
      if (subjects > 0) {
         String text = subjects + "/" + RegulusRules.KING_MAX_SUBJECTS;
         int tx = x0 - font.width(text) - 4;
         g.drawString(font, text, tx, y - 2, 0xFF000000 | RegulusPalette.PALE_GOLD, true);
         int hop = RegulusView.extra(s, RegulusFlags.EXTRA_HOP);
         if (hop > 0) {
            int w = Math.round(Mth.clamp(hop / (float) RegulusRules.KING_HOP_TICKS, 0.0F, 1.0F) * font.width(text));
            g.fill(tx, y + 7, tx + w, y + 8, 0xFF000000 | RegulusPalette.HEART);
         }
      }

      // combo pips under nothing: just right of the bar's start, below it
      int combo = RegulusView.extra(s, RegulusFlags.EXTRA_COMBO);
      for (int i = 0; i < COMBO_STEPS; i++) {
         int px = x0 + i * 4;
         boolean on = i < combo;
         g.fill(px, y + BAR_HEIGHT + 2, px + 3, y + BAR_HEIGHT + 4, on ? 0xFF000000 | RegulusPalette.GOLD : 0x80000000 | RegulusPalette.GREY);
      }

      if (RegulusView.flag(s, RegulusFlags.REPLY_WINDOW) || RegulusView.flag(s, RegulusFlags.REPLY_ARMED)) {
         Component reply = Component.translatable("hud.viltrumitecore.regulus.reply");
         g.drawString(font, reply, x1 - font.width(reply), y - 22, 0xFF000000 | RegulusPalette.GLASS, true);
      }

      // the judgement line, centred over the ability bar
      int target = RegulusView.ultTargetId(s);
      if (target >= 0) {
         Entity e = player.level().getEntity(target);
         if (e != null) {
            Component judge = Component.translatable("hud.viltrumitecore.regulus.judging", e.getDisplayName());
            int cx = abilityBar.x() + abilityBar.width() / 2;
            g.drawString(font, judge, cx - font.width(judge) / 2, abilityBar.y() - 46, 0xFF000000 | RegulusPalette.PALE_GOLD, true);
         }
      }
   }

   /** Pure: the strain bar colour (ivory → gold; the warning pulses heart-red; the seizure is ink). */
   static int strainColour(int strain, boolean warn, boolean seized, long time) {
      if (seized) {
         return RegulusPalette.INK;
      }

      float k = Mth.clamp(strain / (float) RegulusRules.STRAIN_MAX, 0.0F, 1.0F);
      int base = RegulusPalette.mix(RegulusPalette.IVORY, RegulusPalette.GOLD, k);
      if (!warn) {
         return base;
      }

      float pulse = 0.5F + 0.5F * (float) Math.sin(time * 0.6);
      return RegulusPalette.mix(base, RegulusPalette.HEART, 0.35F + 0.45F * pulse);
   }

   /** The heart line: Lion resting, seizure, hostless countdown or the host. */
   static Component heartLine(Minecraft mc, HeroPublicSnapshot s, boolean lion, boolean hostAlive, boolean seized, int strain) {
      if (seized) {
         return Component.translatable("hud.viltrumitecore.regulus.seized");
      }

      if (RegulusView.flag(s, RegulusFlags.LION_LOCKED)) {
         int ticks = RegulusView.extra(s, RegulusFlags.EXTRA_LION_LOCK);
         return Component.translatable("hud.viltrumitecore.regulus.lion_locked", seconds(ticks));
      }

      if (!lion) {
         return null;
      }

      if (!hostAlive) {
         return Component.translatable("hud.viltrumitecore.regulus.hostless", seconds(RegulusRules.hostlessTicksLeft(strain)));
      }

      Entity host = mc.level == null ? null : mc.level.getEntity(RegulusView.hostId(s));
      return host == null ? Component.translatable("hud.viltrumitecore.regulus.host_far")
         : Component.translatable("hud.viltrumitecore.regulus.host", host.getDisplayName());
   }

   /** Pure: ticks as seconds with one decimal ("3.5"). */
   static String seconds(int ticks) {
      int tenths = (Math.max(0, ticks) + 1) / 2;
      return tenths / 10 + "." + tenths % 10;
   }
}
