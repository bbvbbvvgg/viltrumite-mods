package dev.baranhan.viltrumitecore.client.venom;

import dev.baranhan.viltrumitecore.client.hero.PanelStyle;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.venom.VenomFlags;
import dev.baranhan.viltrumitecore.hero.venom.VenomRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Symbiote panel: glossy black frames with a wet blue-white rim and the white spider emblem; left of the ability bar
 * the hunger gauge (the symbiote feeds on fights and webs; rage and the takeover need it: their marks sit on the
 * gauge), a FRENZY / SHELL / RIDING state label and the takeover timer.
 */
public final class VenomPanelStyle implements PanelStyle {
   public static final VenomPanelStyle INSTANCE = new VenomPanelStyle();
   private static final ResourceLocation EMBLEM = VenomView.rl("textures/gui/venom/emblem.png");
   static final int BACK = 0xE0060609;
   static final int RIM = 0xFFC8D4F0;
   static final int RIM_DARK = 0xFF1C2030;
   static final int SHEEN = 0xFF5A6FA8;
   static final int SLOT = 0xFF0E0F16;
   static final int SLOT_EMPTY = 0x900E0F16;
   static final int COOLDOWN = 0xB0000000;
   public static final int BAR_WIDTH = 48;
   public static final int BAR_HEIGHT = 5;

   private VenomPanelStyle() {
   }

   @Override
   public void drawFrames(GuiGraphics g, Bounds flightBar, Bounds abilityBar) {
      frame(g, flightBar);
      frame(g, abilityBar);
      int x = abilityBar.x() + (abilityBar.width() - 16) / 2;
      g.blit(EMBLEM, x, abilityBar.y() - 32, 0.0F, 0.0F, 16, 16, 16, 16);
   }

   @Override
   public void drawSlot(GuiGraphics g, int x, int y, boolean empty) {
      g.fill(x, y, x + 16, y + 16, empty ? SLOT_EMPTY : SLOT);
      if (empty) {
         g.fill(x + 7, y + 7, x + 9, y + 9, RIM_DARK);
      }
   }

   @Override
   public void drawCooldown(GuiGraphics g, int x, int y) {
      g.fill(x, y, x + 16, y + 16, COOLDOWN);
      g.fill(x, y + 15, x + 16, y + 16, SHEEN);
   }

   @Override
   public int accentColor() {
      return RIM & 0xFFFFFF;
   }

   /** Pure: the gauge colour at a hunger fraction: deep violet when starved, white-blue when fed. ARGB. */
   public static int hungerColor(float f) {
      f = Math.max(0, Math.min(1, f));
      int r = (int)(70 + 160 * f);
      int gg = (int)(40 + 180 * f * f);
      int b = (int)(140 + 110 * f);
      return 0xFF000000 | Math.min(255, r) << 16 | Math.min(255, gg) << 8 | Math.min(255, b);
   }

   /** Pure: the lang key of the state label (null: none). */
   public static String stateKey(int flags) {
      if (VenomFlags.has(flags, VenomFlags.POSSESSING)) return "hud.viltrumitecore.venom.state.riding";
      if (VenomFlags.has(flags, VenomFlags.RAGE)) return "hud.viltrumitecore.venom.state.frenzy";
      if (VenomFlags.has(flags, VenomFlags.SHELL)) return "hud.viltrumitecore.venom.state.shell";
      if (VenomFlags.has(flags, VenomFlags.RECOIL)) return "hud.viltrumitecore.venom.state.recoil";
      if (!VenomFlags.has(flags, VenomFlags.FORM)) return "hud.viltrumitecore.venom.state.host";
      return null;
   }

   @Override
   public void drawAttachments(GuiGraphics g, Bounds abilityBar, Player player, HeroPublicSnapshot s) {
      int x1 = abilityBar.x() - 3;
      int x0 = x1 - BAR_WIDTH;
      int y = abilityBar.y() + 2;
      float hunger = VenomView.hunger(s);
      boolean blink = (player.tickCount / 4) % 2 == 0;
      g.fill(x0 - 1, y - 1, x1 + 1, y + BAR_HEIGHT + 1, 0xFF000000);
      g.fill(x0, y, x1, y + BAR_HEIGHT, 0xFF0A0A12);
      int fill = Math.round(hunger * BAR_WIDTH);
      for (int i = 0; i < fill; i++) {
         g.fill(x0 + i, y, x0 + i + 1, y + BAR_HEIGHT, hungerColor((i + 0.5F) / BAR_WIDTH));
      }
      if (fill > 0) {
         g.fill(x0, y, x0 + fill, y + 1, 0x60FFFFFF);
      }
      for (int mark : new int[]{VenomRules.TAKEOVER_COST, VenomRules.RAGE_MIN}) {
         int mx = x0 + Math.round(mark / (float)VenomRules.HUNGER_MAX * BAR_WIDTH);
         g.fill(mx, y - 1, mx + 1, y + BAR_HEIGHT + 1, s.resource() >= mark ? 0xFFF0F4FF : RIM_DARK);
      }
      var font = Minecraft.getInstance().font;
      String key = stateKey(s.heroFlags());
      if (key != null) {
         Component label = Component.translatable(key);
         int color = VenomFlags.has(s.heroFlags(), VenomFlags.RAGE) ? blink ? 0xFFFF4A5A : 0xFFE8E8F8 : 0xFFB8C4E8;
         g.drawString(font, label, x1 - font.width(label), y - 12, color, true);
      }
      int left = Math.max(0, s.resource2());
      if (left > 0) {
         int w = Math.round(Math.min(1.0F, left / (float)Math.max(1, VenomPossessionClient.maxRemaining)) * BAR_WIDTH);
         g.fill(x0 - 1, y - 4, x1 + 1, y - 1, 0xFF000000);
         g.fill(x0, y - 3, x0 + w, y - 2, 0xFFB090F0);
      }
   }

   private static void frame(GuiGraphics g, Bounds b) {
      int x0 = b.x(), y0 = b.y(), x1 = x0 + b.width(), y1 = y0 + b.height();
      g.fill(x0, y0, x1, y1, BACK);
      g.fill(x0, y0, x1, y0 + 1, RIM);
      g.fill(x0, y1 - 1, x1, y1, RIM_DARK);
      g.fill(x0, y0, x0 + 1, y1, RIM);
      g.fill(x1 - 1, y0, x1, y1, RIM_DARK);
      g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, SHEEN);
   }
}
