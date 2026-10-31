package dev.baranhan.viltrumitecore.client.hero;

import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

/**
 * Look of the ability panel (ViltrumiteInGameHudMixin). The layout (six
 * vertical slots, three flight slots, page indicator) never moves; a style
 * only paints frames, slot cells, the cooldown overlay and the accent colour.
 * Registered per hero in {@link PanelStyles}; {@link #DEFAULT} is the
 * original drawing.
 */
public interface PanelStyle {
   PanelStyle DEFAULT = new DefaultPanelStyle();

   /** Frames of both bars: horizontal flight bar and vertical ability bar. */
   void drawFrames(GuiGraphics graphics, Bounds flightBar, Bounds abilityBar);

   /** A 16x16 slot cell before its icon; {@code empty} = no ability in it. */
   void drawSlot(GuiGraphics graphics, int x, int y, boolean empty);

   /** Overlay of a greyed (cooldown / unavailable) 16x16 icon. */
   void drawCooldown(GuiGraphics graphics, int x, int y);

   /** Page number colour (RGB). */
   int accentColor();

   /**
    * Hero HUD next to the panel (mode icons, timers), drawn after both bars.
    * Draws only outside the fixed layout; default: nothing.
    */
   default void drawAttachments(GuiGraphics graphics, Bounds abilityBar, Player player, HeroPublicSnapshot snapshot) {
   }

   /**
    * How far {@link #drawAttachments} reaches: up to this many px left of the ability bar, from
    * {@link #attachmentsUp()} px above the bar's top down to the bottom of the screen (the emblem and page badge
    * over the bar are common to every style). Other HUDs keep out of it (the Thanos timer, ThanosHudLayout).
    * Default: a gauge left of the bar's top with its number and a short label (Captain, Jinwoo, Regulus, Sinclair).
    */
   default int attachmentsLeft() {
      return 80;
   }

   /** See {@link #attachmentsLeft()}. */
   default int attachmentsUp() {
      return 24;
   }

   record Bounds(int x, int y, int width, int height) {
   }
}
