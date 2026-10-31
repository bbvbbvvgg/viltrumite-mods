package dev.baranhan.viltrumitecore.client.codex;

import dev.baranhan.viltrumitecore.ability.ViltrumiteAbility;
import dev.baranhan.viltrumitecore.client.AbilityInputManager;
import dev.baranhan.viltrumitecore.client.codex.CodexEntry.Category;
import dev.baranhan.viltrumitecore.client.gui.KeyLabels;
import dev.baranhan.viltrumitecore.client.wish.WishStyle;
import dev.baranhan.viltrumitecore.codex.catalog.CatalogInteraction;
import dev.baranhan.viltrumitecore.codex.catalog.InteractionCatalog;
import dev.baranhan.viltrumitecore.codex.catalog.LocalText;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.wish.OdLagnaWishes;
import dev.baranhan.viltrumitecore.hero.wish.Wish;
import dev.baranhan.viltrumitecore.hero.wish.WishSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * The encyclopedia («Хроники Вильтрума»), opened from the small book button of Od Lagna's wish screen and
 * drawn in its palette: tabs for heroes, NPCs and bosses, world events and contextual interactions, the
 * article list on the left, the article with its subtitle-style portrait on the right. Hero abilities and
 * key labels come from the live hero definitions and key bindings; contextual interactions from the catalog
 * ({@link CodexCatalog}), secret ones closed (only their hint) until the server says this player discovered
 * them ({@link CodexClientState}). Esc and «назад» fade back to the wish screen, whose state is untouched.
 */
public class CodexScreen extends Screen {
   private static final int FADE_IN = 6;
   private static final int FADE_OUT = 4;
   private static final int SWITCH = 5;
   private static final int TEXT = 0xE4E0D6;
   private static final int MUTED = 0x9A9AAE;
   private static final int HEADER = WishStyle.GOLD;
   private static final int FOUND = 0x8FD18F;
   private static final int LOCKED = 0x7C7C8E;
   private static final int TEST = 0xFF6A5A;

   @Nullable
   private final Screen parent;
   private final Category[] tabs = Category.values();
   private final List<List<Item>> items = new ArrayList<>();
   private final int[] selected = new int[Category.values().length];
   private final float[] listScroll = new float[Category.values().length];
   private final float[] listTarget = new float[Category.values().length];
   private final float[] listScrollO = new float[Category.values().length];
   private final Map<String, Integer> heroAccents = new HashMap<>();
   private CodexLayout layout;
   private InteractionCatalog catalog = InteractionCatalog.missing();
   private int tab;
   private int age;
   private int closing = -1;
   private int switchAge = SWITCH;
   private float textScroll;
   private float textScrollO;
   private float textTarget;
   private List<Placed> placed = List.of();
   private int contentHeight;
   private String builtFor = "";
   private int builtRevision = -1;

   public CodexScreen(@Nullable Screen parent) {
      super(Component.translatable(CodexEntry.gui("title")));
      this.parent = parent;
      for (Wish wish : OdLagnaWishes.all()) {
         this.heroAccents.putIfAbsent(wish.heroKey(), WishStyle.of(wish.style()).primary);
      }
   }

   /** One row of the list: an article, or a catalog interaction. */
   record Item(String id, @Nullable CodexEntry entry, @Nullable CatalogInteraction interaction) {
   }

   /** A laid-out piece of the article text, at {@code y} below the text top. */
   record Placed(Kind kind, int x, int y, int w, int h, @Nullable FormattedCharSequence line, String label, int color, @Nullable ResourceLocation icon,
      List<Component> tooltip, @Nullable Runnable action) {
   }

   enum Kind {
      HEADER,
      LINE,
      ABILITY,
      CHIP,
      LINK
   }

   @Override
   protected void init() {
      this.layout = new CodexLayout(this.width, this.height);
      if (this.age == 0) {
         this.catalog = CodexCatalog.get();
         play(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.9F);
      }
      this.rebuildItems();
      for (int i = 0; i < this.tabs.length; i++) {
         this.listTarget[i] = this.clampList(i, this.listTarget[i]);
         this.listScroll[i] = this.listScrollO[i] = this.clampList(i, this.listScroll[i]);
      }
      this.builtFor = "";
   }

   private void rebuildItems() {
      this.items.clear();
      for (Category c : this.tabs) {
         List<Item> list = new ArrayList<>();
         if (c == Category.INTERACTIONS) {
            for (CatalogInteraction i : this.catalog.interactions()) {
               list.add(new Item(i.id(), null, i));
            }
         } else {
            for (CodexEntry e : CodexEntries.of(c)) {
               list.add(new Item(e.id(), e, null));
            }
         }
         this.items.add(list);
      }
      for (int i = 0; i < this.tabs.length; i++) {
         this.selected[i] = Mth.clamp(this.selected[i], 0, Math.max(0, this.items.get(i).size() - 1));
      }
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void tick() {
      this.age++;
      if (this.switchAge < SWITCH) {
         this.switchAge++;
      }
      for (int i = 0; i < this.tabs.length; i++) {
         this.listScrollO[i] = this.listScroll[i];
         this.listScroll[i] = approach(this.listScroll[i], this.listTarget[i]);
      }
      this.textScrollO = this.textScroll;
      this.textScroll = approach(this.textScroll, this.textTarget);
      if (this.closing >= 0 && ++this.closing >= FADE_OUT) {
         this.closeNow();
      }
   }

   private static float approach(float v, float target) {
      v += (target - v) * 0.45F;
      return Math.abs(target - v) < 0.05F ? target : v;
   }

   float fade(float partial) {
      float in = Math.min(1, (this.age + partial) / FADE_IN);
      if (this.closing < 0) {
         return smooth(in);
      }
      return smooth(Math.min(in, 1 - Math.min(1, (this.closing + partial) / FADE_OUT)));
   }

   // ---------------------------------------------------------------- rendering

   @Override
   public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
      CodexLayout l = this.layout;
      float a = this.fade(partial);
      float time = this.age + partial;
      Item item = this.current();
      this.ensureBuilt(item);

      g.fillGradient(0, 0, this.width, this.height, argb(0xC8 * a, 0x080B16), argb(0xEC * a, 0x030409));
      if (a < 0.03F) {
         // vanilla text turns opaque below alpha 4: nothing but the veil while (nearly) invisible
         return;
      }
      this.drawTitle(g, a, time);
      this.drawButton(g, l.backX, l.backY, CodexLayout.BACK_W, CodexLayout.BACK_H, "‹ " + I18n.get(CodexEntry.gui("back")), true,
         this.closing < 0 && l.overBack(mouseX, mouseY), false, a);
      for (int i = 0; i < this.tabs.length; i++) {
         this.drawTab(g, i, mouseX, mouseY, a);
      }
      this.drawList(g, mouseX, mouseY, a, partial);
      List<Component> tooltip = this.drawArticle(g, item, mouseX, mouseY, a, partial);
      this.drawFooter(g, a);
      if (tooltip != null && !tooltip.isEmpty() && a > 0.9F) {
         List<FormattedCharSequence> lines = new ArrayList<>();
         for (Component c : tooltip) {
            lines.addAll(this.font.split(c, Math.max(140, l.detailW - 20)));
         }
         g.renderTooltip(this.font, lines, mouseX, mouseY);
      }
   }

   private void drawTitle(GuiGraphics g, float a, float time) {
      CodexLayout l = this.layout;
      int alpha = (int)(255 * a);
      if (alpha <= 4) {
         return;
      }
      float pulse = 0.82F + 0.18F * Mth.sin(time * 0.08F);
      g.pose().pushPose();
      g.pose().translate(this.width / 2.0F, l.titleY, 0);
      g.pose().scale(l.titleScale, l.titleScale, 1);
      String title = this.title.getString();
      int maxW = (int)((this.width - 2 * (CodexLayout.MARGIN + CodexLayout.BACK_W + 6)) / l.titleScale);
      title = this.font.plainSubstrByWidth(title, Math.max(20, maxW));
      int tw = this.font.width(title);
      int glow = argb(70 * a * pulse, WishStyle.GOLD);
      for (int[] o : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
         g.drawString(this.font, title, -tw / 2 + o[0], o[1], glow, false);
      }
      g.drawString(this.font, title, -tw / 2, 0, argb(alpha, 0xFFF4DC), false);
      g.pose().popPose();
   }

   private void drawTab(GuiGraphics g, int i, int mouseX, int mouseY, float a) {
      CodexLayout l = this.layout;
      int x = l.tabX(i);
      int y = l.tabsY;
      boolean on = i == this.tab;
      boolean hot = this.closing < 0 && l.tabAt(mouseX, mouseY) == i;
      int fill = on ? 0x3A2E14 : hot ? 0x1E2440 : 0x121729;
      g.fill(x, y, x + l.tabW, y + CodexLayout.TAB_H, argb((on ? 0xE0 : 0xB0) * a, fill));
      int edge = argb((on ? 255 : hot ? 170 : 70) * a, on ? WishStyle.GOLD : 0x8C93B8);
      g.fill(x, y + CodexLayout.TAB_H - 1, x + l.tabW, y + CodexLayout.TAB_H, edge);
      if (on) {
         g.fill(x, y, x + l.tabW, y + 1, edge);
         g.fill(x, y, x + 1, y + CodexLayout.TAB_H, edge);
         g.fill(x + l.tabW - 1, y, x + l.tabW, y + CodexLayout.TAB_H, edge);
      }
      String label = I18n.get(CodexEntry.gui("tab." + this.tabs[i].key));
      String count = String.valueOf(this.items.get(i).size());
      int room = l.tabW - 8;
      boolean withCount = this.font.width(label + " " + count) <= room;
      String shown = withCount ? label : this.font.plainSubstrByWidth(label, room);
      int total = this.font.width(shown) + (withCount ? this.font.width(" " + count) : 0);
      int tx = x + (l.tabW - total) / 2;
      if (a * 255 > 5) {
         g.drawString(this.font, shown, tx, y + 4, argb(255 * a, on ? 0xFFE6A8 : hot ? 0xFFFFFF : 0xC8CCDC), false);
         if (withCount) {
            g.drawString(this.font, " " + count, tx + this.font.width(shown), y + 4, argb(200 * a, on ? 0xC9A464 : 0x7F8499), false);
         }
      }
   }

   private void drawList(GuiGraphics g, int mouseX, int mouseY, float a, float partial) {
      CodexLayout l = this.layout;
      List<Item> list = this.items.get(this.tab);
      g.fill(l.listX - 2, l.bodyTop - 2, l.listX + l.listW + 2, l.bodyBottom + 2, argb(0x70 * a, 0x05070E));
      float scroll = Mth.lerp(partial, this.listScrollO[this.tab], this.listScroll[this.tab]);
      int hovered = this.closing < 0 ? l.rowAt(mouseX, mouseY, scroll, list.size()) : -1;
      if (list.isEmpty()) {
         String msg = this.font.plainSubstrByWidth(I18n.get(CodexEntry.gui("empty")), l.listW - 8);
         g.drawString(this.font, msg, l.listX + 4, l.bodyTop + 5, argb(200 * a, MUTED), false);
         return;
      }
      g.enableScissor(l.listX, l.bodyTop, l.listX + l.listW, l.bodyBottom);
      for (int i = 0; i < list.size(); i++) {
         int y = Math.round(l.rowY(i, scroll));
         if (y + CodexLayout.ROW < l.bodyTop || y > l.bodyBottom) {
            continue;
         }
         Item item = list.get(i);
         boolean sel = i == this.selected[this.tab];
         boolean hot = i == hovered;
         boolean locked = this.locked(item);
         int accent = this.accent(item);
         int bg = sel ? 0x3A2E14 : hot ? 0x1E2440 : 0x121729;
         g.fill(l.listX, y, l.listX + l.listW, y + CodexLayout.ROW, argb((sel ? 0xD8 : 0x98) * a, bg));
         g.fill(l.listX, y, l.listX + (sel || hot ? 3 : 2), y + CodexLayout.ROW, argb((locked ? 90 : 230) * a, accent));
         if (sel) {
            int border = argb(200 * a, WishStyle.GOLD);
            g.fill(l.listX, y, l.listX + l.listW, y + 1, border);
            g.fill(l.listX, y + CodexLayout.ROW - 1, l.listX + l.listW, y + CodexLayout.ROW, border);
         }
         String mark = this.mark(item);
         int right = l.listX + l.listW - 4;
         if (mark != null) {
            int mw = this.font.width(mark);
            g.drawString(this.font, mark, right - mw, y + 5, argb(220 * a, locked ? LOCKED : FOUND), false);
            right -= mw + 3;
         }
         String name = this.name(item);
         int room = right - (l.listX + 7);
         if (this.font.width(name) > room) {
            name = this.font.plainSubstrByWidth(name, room - this.font.width("…")) + "…";
         }
         int color = locked ? LOCKED : sel ? 0xFFE2A0 : hot ? 0xFFFFFF : 0xDAD6CC;
         if (a * 255 > 5) {
            g.drawString(this.font, name, l.listX + 7, y + 5, argb(255 * a, color), !locked);
         }
      }
      g.disableScissor();
      float max = l.maxListScroll(list.size());
      if (max > 0) {
         scrollBar(g, l.listX + l.listW + 3, l.bodyTop, l.bodyBottom, scroll, max, l.bodyHeight(), list.size() * CodexLayout.PITCH, a);
      }
   }

   @Nullable
   private List<Component> drawArticle(GuiGraphics g, @Nullable Item item, int mouseX, int mouseY, float a, float partial) {
      CodexLayout l = this.layout;
      g.fill(l.detailX - 2, l.bodyTop - 2, l.detailX + l.detailW + 2, l.bodyBottom + 2, argb(0x70 * a, 0x05070E));
      float k = smooth((this.switchAge + (this.switchAge < SWITCH ? partial : 0)) / (float)SWITCH);
      float aa = a * k;
      if (item == null) {
         Component msg = this.tabs[this.tab] == Category.INTERACTIONS ? this.catalogMessage() : Component.translatable(CodexEntry.gui("select"));
         int y = l.bodyTop + 8;
         for (FormattedCharSequence line : this.font.split(msg, l.textWrap())) {
            g.drawString(this.font, line, l.detailX + 6, y, argb(230 * a, MUTED), false);
            y += 10;
         }
         return null;
      }
      int accent = this.accent(item);
      // header: portrait in a subtitle panel, name, role, tags
      int px = l.detailX + 2;
      int py = l.bodyTop;
      int slide = Math.round((1 - k) * 6);
      CodexEntry.Visual visual = this.visual(item);
      if (this.locked(item)) {
         g.pose().pushPose();
         g.pose().translate(px, py, 0);
         dev.baranhan.viltrumitecore.client.hero.speech.SubtitleRenderer.panel(g, l.portraitW, l.portraitH, aa);
         g.pose().popPose();
         CodexPortraits.halo(g, 0x6A6A7A, px + 2, py + 2, l.portraitW - 4, l.portraitH - 4, aa * 0.6F);
         if (aa * 255 > 5) {
            g.drawCenteredString(this.font, "?", px + l.portraitW / 2, py + l.portraitH / 2 - 4, argb(255 * aa, 0xC8C8D8));
         }
      } else {
         CodexPortraits.framed(g, visual, accent, px, py, l.portraitW, l.portraitH, aa);
      }
      int hx = px + l.portraitW + 8 + slide;
      int hw = l.detailX + l.detailW - hx - 4;
      String name = this.name(item);
      float nameScale = l.portraitH >= 52 && this.font.width(name) * 1.5F <= hw ? 1.5F : 1.0F;
      int ny = py + 3;
      if (aa * 255 > 5) {
         g.pose().pushPose();
         g.pose().translate(hx, ny, 0);
         g.pose().scale(nameScale, nameScale, 1);
         String shown = this.font.plainSubstrByWidth(name, (int)(hw / nameScale));
         g.drawString(this.font, shown, 0, 0, argb(255 * aa, this.locked(item) ? LOCKED : 0xFFF1D2), true);
         g.pose().popPose();
      }
      g.fill(hx, ny + Math.round(9 * nameScale) + 2, hx + Math.min(hw, 60), ny + Math.round(9 * nameScale) + 3, argb(200 * aa, accent));
      int ry = ny + Math.round(9 * nameScale) + 6;
      String role = this.role(item);
      int roleLines = 0;
      int maxRoleLines = Math.max(1, (l.portraitH - (ry - py) - 12) / 10);
      for (FormattedCharSequence line : this.font.split(Component.literal(role).withStyle(s -> s.withItalic(true)), hw)) {
         if (roleLines >= maxRoleLines) {
            break;
         }
         if (aa * 255 > 5) {
            g.drawString(this.font, line, hx, ry + roleLines * 10, argb(230 * aa, 0xBFC3D6), false);
         }
         roleLines++;
      }
      int tx = hx;
      int ty = py + l.portraitH - 11;
      for (String[] tag : this.tags(item)) {
         int color = Integer.parseInt(tag[1], 16);
         int w = this.font.width(tag[0]) + 6;
         if (tx + w > l.detailX + l.detailW - 2) {
            break;
         }
         g.fill(tx, ty, tx + w, ty + 11, argb(0x90 * aa, 0x0C0F1C));
         g.fill(tx, ty + 10, tx + w, ty + 11, argb(200 * aa, color));
         if (aa * 255 > 5) {
            g.drawString(this.font, tag[0], tx + 3, ty + 2, argb(255 * aa, color), false);
         }
         tx += w + 4;
      }

      // the scrolled text
      float scroll = Mth.lerp(partial, this.textScrollO, this.textScroll);
      List<Component> tooltip = null;
      g.enableScissor(l.detailX, l.textTop, l.detailX + l.detailW, l.bodyBottom);
      int top = l.textTop - Math.round(scroll) + slide;
      boolean inText = l.overText(mouseX, mouseY) && this.closing < 0;
      for (Placed p : this.placed) {
         int y = top + p.y();
         if (y + p.h() < l.textTop || y > l.bodyBottom) {
            continue;
         }
         int x = l.detailX + 4 + p.x();
         boolean hot = inText && mouseX >= x && mouseX < x + p.w() && mouseY >= y && mouseY < y + p.h();
         if (hot && !p.tooltip().isEmpty()) {
            tooltip = p.tooltip();
         }
         if (aa * 255 <= 5) {
            continue;
         }
         switch (p.kind()) {
            case HEADER -> {
               g.drawString(this.font, p.label(), x, y + 3, argb(255 * aa, HEADER), false);
               g.fill(x, y + 12, x + Math.min(p.w(), this.font.width(p.label()) + 16), y + 13, argb(90 * aa, HEADER));
            }
            case LINE -> g.drawString(this.font, p.line(), x, y, argb(255 * aa, p.color()), false);
            case ABILITY -> {
               if (hot) {
                  g.fill(x - 2, y, x + p.w(), y + p.h(), argb(0x60 * aa, 0x2A3048));
               }
               if (p.icon() != null) {
                  com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                  com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, aa);
                  g.blit(p.icon(), x, y + 1, 0.0F, 0.0F, 16, 16, 16, 16);
                  com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
               }
               g.drawString(this.font, this.font.plainSubstrByWidth(p.label(), p.w() - 22), x + 21, y + 5, argb(255 * aa, p.color()), false);
            }
            case CHIP -> {
               boolean link = p.action() != null;
               g.fill(x, y, x + p.w(), y + p.h(), argb((hot && link ? 0xE0 : 0xA8) * aa, hot && link ? 0x2A3048 : 0x121729));
               g.fill(x, y, x + 2, y + p.h(), argb(220 * aa, p.color()));
               if (hot && link) {
                  g.fill(x, y + p.h() - 1, x + p.w(), y + p.h(), argb(220 * aa, WishStyle.GOLD));
               }
               g.drawString(this.font, p.label(), x + 5, y + 3, argb(255 * aa, link ? (hot ? 0xFFFFFF : 0xDAD6CC) : MUTED), false);
            }
            case LINK -> {
               if (hot && p.action() != null) {
                  g.fill(x - 2, y - 1, x + p.w(), y + p.h() - 1, argb(0x60 * aa, 0x2A3048));
               }
               g.drawString(this.font, p.label(), x, y + 1, argb(255 * aa, p.color()), false);
            }
         }
      }
      g.disableScissor();
      float max = this.maxText();
      if (max > 0) {
         scrollBar(g, l.detailX + l.detailW - 3, l.textTop, l.bodyBottom, scroll, max, l.textHeight(), this.contentHeight, a);
         if (scroll > 0.5F) {
            g.fillGradient(l.detailX, l.textTop, l.detailX + l.detailW - 4, l.textTop + 6, argb(160 * a, 0x05070E), 0);
         }
         if (scroll < max - 0.5F) {
            g.fillGradient(l.detailX, l.bodyBottom - 8, l.detailX + l.detailW - 4, l.bodyBottom, 0, argb(170 * a, 0x05070E));
         }
      }
      return tooltip;
   }

   private void drawFooter(GuiGraphics g, float a) {
      CodexLayout l = this.layout;
      if (a * 255 <= 5) {
         return;
      }
      String text;
      int color = MUTED;
      if (this.tabs[this.tab] == Category.INTERACTIONS) {
         int found = 0;
         for (CatalogInteraction i : this.catalog.interactions()) {
            if (CodexClientState.hasInteraction(i.id())) {
               found++;
            }
         }
         text = this.catalog.status() == InteractionCatalog.Status.LOADED && !this.catalog.isEmpty()
            ? I18n.get(CodexEntry.gui("interaction.count"), found, this.catalog.interactions().size()) : "";
      } else {
         text = I18n.get(CodexEntry.gui("subtitle"));
      }
      if (this.catalog.testData()) {
         String badge = I18n.get(CodexEntry.gui("catalog.test"));
         int bw = this.font.width(badge) + 8;
         int bx = this.width - CodexLayout.MARGIN - bw;
         g.fill(bx, l.footerY - 2, bx + bw, l.footerY + 9, argb(0xC0 * a, 0x3A0E0E));
         g.drawString(this.font, badge, bx + 4, l.footerY, argb(255 * a, TEST), false);
      }
      String shown = this.font.plainSubstrByWidth(text, this.width - 2 * CodexLayout.MARGIN - (this.catalog.testData() ? 150 : 0));
      g.drawString(this.font, shown, CodexLayout.MARGIN, l.footerY, argb(200 * a, color), false);
   }

   private void drawButton(GuiGraphics g, int x, int y, int w, int h, String label, boolean active, boolean hot, boolean primary, float a) {
      int fill = primary ? mix(0x3A2A0E, 0x6A4A12, hot ? 1 : 0.35F) : mix(0x181C2C, 0x2A3048, hot ? 1 : 0);
      g.fill(x, y, x + w, y + h, argb((active ? 0xD0 : 0x80) * a, fill));
      int edge = argb((active ? (hot ? 255 : 170) : 60) * a, primary ? WishStyle.GOLD : 0x8C93B8);
      g.fill(x, y, x + w, y + 1, edge);
      g.fill(x, y + h - 1, x + w, y + h, edge);
      g.fill(x, y, x + 1, y + h, edge);
      g.fill(x + w - 1, y, x + w, y + h, edge);
      if (a * 255 > 5) {
         g.drawCenteredString(this.font, this.font.plainSubstrByWidth(label, w - 6), x + w / 2, y + (h - 8) / 2, argb(255 * a, hot ? 0xFFFFFF : 0xD8DCEC));
      }
   }

   private static void scrollBar(GuiGraphics g, int x, int top, int bottom, float scroll, float max, int view, int content, float a) {
      g.fill(x, top, x + 2, bottom, argb(50 * a, 0xFFFFFF));
      int h = bottom - top;
      int thumb = Math.max(12, Math.round(h * Math.min(1, view / (float)Math.max(1, content))));
      int ty = top + Math.round((h - thumb) * (scroll / max));
      g.fill(x, ty, x + 2, ty + thumb, argb(200 * a, WishStyle.GOLD));
   }

   // ---------------------------------------------------------------- article content

   private void ensureBuilt(@Nullable Item item) {
      String key = this.tab + ":" + (item == null ? "-" : item.id()) + ":" + this.layout.width + "x" + this.layout.height;
      if (key.equals(this.builtFor) && this.builtRevision == CodexClientState.revision()) {
         return;
      }
      boolean sameArticle = key.equals(this.builtFor);
      this.builtFor = key;
      this.builtRevision = CodexClientState.revision();
      this.placed = item == null ? List.of() : this.build(item);
      int h = 0;
      for (Placed p : this.placed) {
         h = Math.max(h, p.y() + p.h());
      }
      this.contentHeight = h + 6;
      if (!sameArticle) {
         this.textScroll = this.textScrollO = this.textTarget = 0;
      } else {
         this.textTarget = Mth.clamp(this.textTarget, 0, this.maxText());
      }
   }

   private float maxText() {
      return Math.max(0, this.contentHeight - this.layout.textHeight());
   }

   private final class Builder {
      final List<Placed> out = new ArrayList<>();
      final int wrap = CodexScreen.this.layout.textWrap();
      int y = 2;

      void header(String key) {
         this.y += this.out.isEmpty() ? 0 : 5;
         this.out.add(new Placed(Kind.HEADER, 0, this.y, this.wrap, 15, null, I18n.get(CodexEntry.gui("section." + key)), HEADER, null, List.of(), null));
         this.y += 16;
      }

      void text(String text, int color) {
         for (FormattedCharSequence line : CodexScreen.this.font.split(Component.literal(text), this.wrap)) {
            this.out.add(new Placed(Kind.LINE, 0, this.y, this.wrap, 10, line, "", color, null, List.of(), null));
            this.y += 10;
         }
         this.y += 2;
      }

      void ability(@Nullable ResourceLocation icon, String name, List<Component> tooltip) {
         this.out.add(new Placed(Kind.ABILITY, 0, this.y, this.wrap, 18, null, name, TEXT, icon, tooltip, null));
         this.y += 19;
      }

      void link(String label, int color, List<Component> tooltip, @Nullable Runnable action) {
         String shown = CodexScreen.this.font.plainSubstrByWidth(label, this.wrap - 4);
         this.out.add(new Placed(Kind.LINK, 0, this.y, CodexScreen.this.font.width(shown) + 4, 11, null, shown, color, null, tooltip, action));
         this.y += 12;
      }

      /** A wrapped row of chips: {label, colour, target id or null[, tooltip or null]}. */
      void chips(List<Object[]> chips) {
         int x = 0;
         for (Object[] c : chips) {
            String label = CodexScreen.this.font.plainSubstrByWidth((String)c[0], this.wrap - 12);
            int w = CodexScreen.this.font.width(label) + 9;
            if (x > 0 && x + w > this.wrap) {
               x = 0;
               this.y += 15;
            }
            String target = (String)c[2];
            Runnable action = target == null ? null : () -> CodexScreen.this.open(target);
            List<Component> tip = c.length > 3 && c[3] != null ? List.of(Component.literal((String)c[3])) : List.of();
            this.out.add(new Placed(Kind.CHIP, x, this.y, w, 13, null, label, (Integer)c[1], null, tip, action));
            x += w + 4;
         }
         if (!chips.isEmpty()) {
            this.y += 15;
         }
      }
   }

   private List<Placed> build(Item item) {
      Builder b = new Builder();
      if (item.entry() != null) {
         CodexEntry e = item.entry();
         for (String section : e.sections()) {
            switch (section) {
               case CodexEntry.LIVE_ABILITIES -> this.abilities(b, e);
               case CodexEntry.LIVE_KEYS -> this.keys(b, e);
               case CodexEntry.INTERACTIONS -> this.interactionsOf(b, e);
               case "relations" -> {
                  String key = CodexEntry.key(e.id(), section);
                  if (I18n.exists(key) || !e.relations().isEmpty()) {
                     b.header(section);
                     if (I18n.exists(key)) {
                        b.text(I18n.get(key), TEXT);
                     }
                     List<Object[]> chips = new ArrayList<>();
                     for (String rel : e.relations()) {
                        CodexEntry r = CodexEntries.get(rel);
                        if (r != null) {
                           chips.add(new Object[]{I18n.get(r.nameKey()), this.accent(r), r.id()});
                        }
                     }
                     b.chips(chips);
                  }
               }
               default -> {
                  String key = CodexEntry.key(e.id(), section);
                  if (I18n.exists(key)) {
                     b.header(section);
                     b.text(I18n.get(key), TEXT);
                  }
               }
            }
         }
      } else if (item.interaction() != null) {
         this.interactionArticle(b, item.interaction());
      }
      return b.out;
   }

   private void abilities(Builder b, CodexEntry e) {
      HeroId id = HeroId.fromKey(e.id());
      if (id == null) {
         return;
      }
      HeroDefinition hero = HeroRegistry.get(id);
      b.header("abilities");
      List<ViltrumiteAbility> list = hero == null ? List.of() : hero.panelAbilities();
      if (list.isEmpty()) {
         b.text(I18n.get(CodexEntry.gui("abilities.none")), MUTED);
         return;
      }
      for (ViltrumiteAbility ability : list) {
         List<Component> tip = ability.getTooltip();
         String name = tip.isEmpty() ? ability.getId() : tip.get(0).getString();
         ResourceLocation icon = hero.abilityIcon(ability.getId());
         b.ability(icon != null ? icon : ability.getIcon(), name, tip);
      }
   }

   private void keys(Builder b, CodexEntry e) {
      HeroId id = HeroId.fromKey(e.id());
      HeroDefinition hero = id == null ? null : HeroRegistry.get(id);
      if (hero == null || hero.panelAbilities().isEmpty()) {
         return;
      }
      b.header("keys");
      List<String> slots = new ArrayList<>();
      for (KeyMapping k : new KeyMapping[]{AbilityInputManager.abilityKey1, AbilityInputManager.abilityKey2, AbilityInputManager.abilityKey3,
         AbilityInputManager.abilityKey4, AbilityInputManager.abilityKey5, AbilityInputManager.abilityKey6}) {
         String s = KeyLabels.shortName(k);
         if (!s.isEmpty()) {
            slots.add(s);
         }
      }
      b.text(I18n.get(CodexEntry.gui("keys.slots"), String.join(" · ", slots)), TEXT);
      String menu = KeyLabels.shortName(AbilityInputManager.abilityMenuKey);
      if (!menu.isEmpty()) {
         b.text(I18n.get(CodexEntry.gui("keys.menu"), menu), TEXT);
      }
      String swap = KeyLabels.shortName(AbilityInputManager.abilitySwapKey);
      if (!swap.isEmpty()) {
         b.text(I18n.get(CodexEntry.gui("keys.swap"), swap), TEXT);
      }
   }

   private void interactionsOf(Builder b, CodexEntry e) {
      b.header("interactions");
      if (this.catalog.status() != InteractionCatalog.Status.LOADED || this.catalog.isEmpty()) {
         b.text(this.catalogMessage().getString(), MUTED);
         return;
      }
      int shown = 0;
      int hidden = 0;
      for (CatalogInteraction i : this.catalog.interactions()) {
         if (!i.involves(e.aliases())) {
            continue;
         }
         shown++;
         if (this.locked(i)) {
            hidden++;
            continue;
         }
         boolean found = CodexClientState.hasInteraction(i.id());
         String label = (found ? "✓ " : "• ") + this.text(i.name(), i.id());
         b.link(label, found ? FOUND : TEXT, List.of(), () -> this.openInteraction(i.id()));
      }
      if (shown == 0) {
         b.text(I18n.get(CodexEntry.gui("interaction.none_for_entry")), MUTED);
      } else if (hidden > 0) {
         b.link("? " + I18n.get(CodexEntry.gui("interaction.secret_left"), hidden), LOCKED,
            List.of(Component.translatable(CodexEntry.gui("interaction.locked_hint"))), null);
      }
      if (this.catalog.testData()) {
         b.text(I18n.get(CodexEntry.gui("catalog.test")), TEST);
      }
   }

   private void interactionArticle(Builder b, CatalogInteraction i) {
      if (this.catalog.testData()) {
         b.text(I18n.get(CodexEntry.gui("catalog.test")), TEST);
      }
      if (this.locked(i)) {
         b.header("hint");
         String hint = this.text(i.hint(), "");
         b.text(hint.isEmpty() ? I18n.get(CodexEntry.gui("interaction.locked_hint")) : hint, TEXT);
         return;
      }
      if (!i.participants().isEmpty()) {
         b.header("participants");
         List<Object[]> chips = new ArrayList<>();
         for (CatalogInteraction.Participant p : i.participants()) {
            CodexEntry target = CodexEntries.byAlias(p.id());
            String raw = this.text(p.name(), p.id());
            String label = target != null ? I18n.get(target.nameKey()) : raw;
            chips.add(new Object[]{label, target != null ? this.accent(target) : 0x8C93B8, target != null ? target.id() : null,
               raw.equals(label) ? null : raw});
         }
         b.chips(chips);
      }
      this.section(b, "description", i.description());
      this.section(b, "lore", i.lore());
      if (!i.conditions().isEmpty()) {
         b.header("conditions");
         for (LocalText c : i.conditions()) {
            b.text("• " + this.text(c, ""), TEXT);
         }
      }
      this.section(b, "mechanic", i.mechanic());
      this.section(b, "visuals", i.visuals());
      if (i.secret()) {
         this.section(b, "hint", i.hint());
      }
      List<Object[]> related = new ArrayList<>();
      for (String r : i.related()) {
         CodexEntry target = CodexEntries.byAlias(r);
         if (target != null) {
            related.add(new Object[]{I18n.get(target.nameKey()), this.accent(target), target.id()});
         }
      }
      if (!related.isEmpty()) {
         b.header("relations");
         b.chips(related);
      }
   }

   private void section(Builder b, String header, LocalText text) {
      String s = this.text(text, "");
      if (!s.isEmpty()) {
         b.header(header);
         b.text(s, TEXT);
      }
   }

   // ---------------------------------------------------------------- item helpers

   @Nullable
   private Item current() {
      List<Item> list = this.items.get(this.tab);
      return list.isEmpty() ? null : list.get(Mth.clamp(this.selected[this.tab], 0, list.size() - 1));
   }

   private boolean locked(Item item) {
      return item.interaction() != null && this.locked(item.interaction());
   }

   private boolean locked(CatalogInteraction i) {
      return i.secret() && !CodexClientState.hasInteraction(i.id());
   }

   private String name(Item item) {
      if (item.entry() != null) {
         return I18n.get(item.entry().nameKey());
      }
      CatalogInteraction i = item.interaction();
      return this.locked(i) ? I18n.get(CodexEntry.gui("interaction.locked")) : this.text(i.name(), i.id());
   }

   private String role(Item item) {
      if (item.entry() != null) {
         return I18n.exists(item.entry().roleKey()) ? I18n.get(item.entry().roleKey()) : "";
      }
      CatalogInteraction i = item.interaction();
      if (this.locked(i)) {
         return I18n.get(CodexEntry.gui("interaction.locked_hint"));
      }
      List<String> names = new ArrayList<>();
      for (CatalogInteraction.Participant p : i.participants()) {
         CodexEntry target = CodexEntries.byAlias(p.id());
         names.add(target != null ? I18n.get(target.nameKey()) : this.text(p.name(), p.id()));
      }
      return String.join(" × ", names);
   }

   @Nullable
   private String mark(Item item) {
      if (item.entry() != null) {
         return item.entry().category() == Category.HEROES && CodexClientState.hasHero(item.entry().id()) ? "✦" : null;
      }
      CatalogInteraction i = item.interaction();
      return this.locked(i) ? "?" : CodexClientState.hasInteraction(i.id()) ? "✓" : null;
   }

   /** {label, rrggbb} tags under the article name. */
   private List<String[]> tags(Item item) {
      List<String[]> out = new ArrayList<>();
      CodexEntry e = item.entry();
      if (e != null) {
         if (e.category() == Category.HEROES && CodexClientState.hasHero(e.id())) {
            out.add(new String[]{I18n.get(CodexEntry.gui("chosen")), "8FD18F"});
         }
         if (e.kind() != null) {
            String color = switch (e.kind()) {
               case "boss" -> "FF6A5A";
               case "enemy" -> "E09A5A";
               case "ally" -> "7FC8FF";
               case "cosmic" -> "FFD98A";
               case "event" -> "C9A0FF";
               default -> "B8BCC8";
            };
            out.add(new String[]{I18n.get(CodexEntry.gui("kind." + e.kind())), color});
         }
      } else if (item.interaction() != null) {
         CatalogInteraction i = item.interaction();
         if (this.locked(i)) {
            out.add(new String[]{I18n.get(CodexEntry.gui("interaction.locked")), "8C8C9E"});
         } else if (CodexClientState.hasInteraction(i.id())) {
            out.add(new String[]{I18n.get(CodexEntry.gui("interaction.found")), "8FD18F"});
         } else {
            out.add(new String[]{I18n.get(CodexEntry.gui("interaction.open")), "B8BCC8"});
         }
         if (this.catalog.testData()) {
            out.add(new String[]{"TEST", "FF6A5A"});
         }
      }
      return out;
   }

   private int accent(Item item) {
      if (item.entry() != null) {
         return this.accent(item.entry());
      }
      CodexEntry first = this.firstParticipant(item.interaction());
      return this.locked(item.interaction()) ? 0x6A6A7A : first != null ? this.accent(first) : WishStyle.GOLD;
   }

   private int accent(CodexEntry e) {
      return e.category() == Category.HEROES ? this.heroAccents.getOrDefault(e.id(), e.accent()) : e.accent();
   }

   private CodexEntry.Visual visual(Item item) {
      if (item.entry() != null) {
         return item.entry().visual();
      }
      CodexEntry first = this.firstParticipant(item.interaction());
      return first != null ? first.visual() : CodexEntry.Visual.halo();
   }

   @Nullable
   private CodexEntry firstParticipant(@Nullable CatalogInteraction i) {
      if (i == null) {
         return null;
      }
      for (CatalogInteraction.Participant p : i.participants()) {
         CodexEntry e = CodexEntries.byAlias(p.id());
         if (e != null) {
            return e;
         }
      }
      return null;
   }

   private Component catalogMessage() {
      return switch (this.catalog.status()) {
         case MISSING -> Component.translatable(CodexEntry.gui("catalog.missing"));
         case INVALID -> Component.translatable(CodexEntry.gui("catalog.invalid"));
         case LOADED -> this.catalog.isEmpty() ? Component.translatable(CodexEntry.gui("catalog.empty")) : Component.empty();
      };
   }

   private String text(LocalText text, String fallback) {
      if (text == null || text.isEmpty()) {
         return fallback;
      }
      Minecraft mc = Minecraft.getInstance();
      String lang = mc.getLanguageManager() == null ? "en_us" : mc.getLanguageManager().getSelected();
      Function<String, String> translate = key -> I18n.exists(key) ? I18n.get(key) : null;
      String s = text.resolve(lang, translate);
      return s == null || s.isEmpty() ? fallback : s;
   }

   // ---------------------------------------------------------------- navigation

   /** Opens the article with this id (relation chips, participants). */
   void open(String id) {
      for (int t = 0; t < this.tabs.length; t++) {
         List<Item> list = this.items.get(t);
         for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(id) && list.get(i).entry() != null) {
               this.show(t, i);
               return;
            }
         }
      }
   }

   void openInteraction(String id) {
      int t = Category.INTERACTIONS.ordinal();
      List<Item> list = this.items.get(t);
      for (int i = 0; i < list.size(); i++) {
         if (list.get(i).id().equals(id)) {
            this.show(t, i);
            return;
         }
      }
   }

   private void show(int tab, int index) {
      boolean changed = tab != this.tab || index != this.selected[tab];
      this.tab = tab;
      this.selected[tab] = index;
      this.listTarget[tab] = this.layout.scrollToShow(index, this.listTarget[tab], this.items.get(tab).size());
      if (changed) {
         this.switchAge = 0;
         play(WishSounds.SELECT, 1.1F + 0.03F * (index % 8), 0.45F);
      }
   }

   private void switchTab(int tab) {
      if (tab == this.tab || tab < 0 || tab >= this.tabs.length) {
         return;
      }
      this.tab = tab;
      this.switchAge = 0;
      play(SoundEvents.BOOK_PAGE_TURN, 1.0F + 0.05F * tab, 0.8F);
   }

   private float clampList(int tab, float v) {
      return Mth.clamp(v, 0, this.layout.maxListScroll(this.items.get(tab).size()));
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.closing >= 0 || button != 0) {
         return super.mouseClicked(mouseX, mouseY, button);
      }
      CodexLayout l = this.layout;
      if (l.overBack(mouseX, mouseY)) {
         this.onClose();
         return true;
      }
      int t = l.tabAt(mouseX, mouseY);
      if (t >= 0) {
         this.switchTab(t);
         return true;
      }
      int row = l.rowAt(mouseX, mouseY, this.listScroll[this.tab], this.items.get(this.tab).size());
      if (row >= 0) {
         this.show(this.tab, row);
         return true;
      }
      if (l.overText(mouseX, mouseY)) {
         int top = l.textTop - Math.round(this.textScroll);
         for (Placed p : this.placed) {
            int x = l.detailX + 4 + p.x();
            int y = top + p.y();
            if (p.action() != null && mouseX >= x && mouseX < x + p.w() && mouseY >= y && mouseY < y + p.h()) {
               p.action().run();
               return true;
            }
         }
      }
      return super.mouseClicked(mouseX, mouseY, button);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      CodexLayout l = this.layout;
      if (l.overList(mouseX, mouseY)) {
         this.listTarget[this.tab] = this.clampList(this.tab, this.listTarget[this.tab] - (float)delta * CodexLayout.PITCH * 1.5F);
      } else {
         this.textTarget = Mth.clamp(this.textTarget - (float)delta * 20.0F, 0, this.maxText());
      }
      return true;
   }

   @Override
   public boolean keyPressed(int key, int scanCode, int modifiers) {
      if (this.closing < 0) {
         int size = this.items.get(this.tab).size();
         switch (key) {
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> {
               if (size > 0) {
                  this.show(this.tab, Math.min(size - 1, this.selected[this.tab] + 1));
               }
               return true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> {
               if (size > 0) {
                  this.show(this.tab, Math.max(0, this.selected[this.tab] - 1));
               }
               return true;
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_TAB -> {
               this.switchTab((this.tab + 1) % this.tabs.length);
               return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> {
               this.switchTab((this.tab + this.tabs.length - 1) % this.tabs.length);
               return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
               this.textTarget = Mth.clamp(this.textTarget + this.layout.textHeight() * 0.8F, 0, this.maxText());
               return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
               this.textTarget = Mth.clamp(this.textTarget - this.layout.textHeight() * 0.8F, 0, this.maxText());
               return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
               this.onClose();
               return true;
            }
            default -> {
            }
         }
      }
      return super.keyPressed(key, scanCode, modifiers);
   }

   /** Esc and «назад»: fade out, then back to the screen that opened the encyclopedia. */
   @Override
   public void onClose() {
      if (this.closing < 0) {
         this.closing = 0;
         play(SoundEvents.BOOK_PAGE_TURN, 0.85F, 0.6F);
      }
   }

   private void closeNow() {
      if (this.minecraft != null && this.minecraft.screen == this) {
         this.minecraft.setScreen(this.parent);
      }
   }

   @Override
   public void renderBackground(GuiGraphics g) {
   }

   // ---------------------------------------------------------------- QA hooks (playtest harness)

   public int tabIndex() {
      return this.tab;
   }

   public void qaSelect(int tab, int index) {
      this.tab = Mth.clamp(tab, 0, this.tabs.length - 1);
      this.show(this.tab, Mth.clamp(index, 0, Math.max(0, this.items.get(this.tab).size() - 1)));
      this.switchAge = SWITCH;
   }

   public void qaScroll(float pixels) {
      this.textTarget = this.textScroll = this.textScrollO = Mth.clamp(pixels, 0, this.maxText());
   }

   // ---------------------------------------------------------------- utils

   private static void play(SoundEvent sound, float pitch, float volume) {
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
   }

   static float smooth(float k) {
      k = Mth.clamp(k, 0, 1);
      return k * k * (3 - 2 * k);
   }

   static int argb(float alpha, int rgb) {
      int al = Mth.clamp(Math.round(alpha), 0, 255);
      return al << 24 | rgb & 0xFFFFFF;
   }

   static int mix(int from, int to, float k) {
      k = Mth.clamp(k, 0, 1);
      int r = Math.round(Mth.lerp(k, from >> 16 & 0xFF, to >> 16 & 0xFF));
      int g = Math.round(Mth.lerp(k, from >> 8 & 0xFF, to >> 8 & 0xFF));
      int b = Math.round(Mth.lerp(k, from & 0xFF, to & 0xFF));
      return r << 16 | g << 8 | b;
   }
}
