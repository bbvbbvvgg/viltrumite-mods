package dev.baranhan.viltrumitecore.client.rohan;

import dev.baranhan.viltrumitecore.hero.book.BookPages;
import dev.baranhan.viltrumitecore.hero.rohan.instruction.AttackKind;
import dev.baranhan.viltrumitecore.hero.rohan.instruction.InstructionParam;
import dev.baranhan.viltrumitecore.network.packet.RohanBookC2SPacket;
import dev.baranhan.viltrumitecore.network.packet.RohanBookS2CPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * The Heaven's Door book. Left page: what the body really is, page by page (identity, condition, memories, power,
 * margin notes) with a page-turn animation and handwriting that appears line by line. Right page: the time left,
 * lines left, ink, and the fixed list of lines Rohan may write (each with its ink cost; a refused line is struck
 * through and says why), the parameter of the chosen line (a body, a place or a kind of attack, picked from server
 * lists), Write, Tear out a page and Close. Keys: A/D or arrows turn pages, W/S or up/down pick a line, Enter writes,
 * Esc closes. Nothing typed by the player is ever sent: only indices into the server's lists.
 */
public class HeavensDoorScreen extends Screen {
   static final ResourceLocation BOOK = HeavensDoorRenderer.rl("textures/gui/rohan/book.png");
   static final int INK = 0xFF1E1A2E, RED = 0xFF8A1C28, FADED = 0xFF9A9080, ACCENT = 0xFF2F7A5A;
   /** Book texture size (spread 256 x 160 on a 256 x 256 sheet). */
   static final int TEX_W = 256, TEX_H = 160;
   static final int FLIP_MS = 260, CLOSE_MS = 180;
   private RohanBookClient.Session session;
   private int page;
   private long pageShownAt = System.currentTimeMillis();
   private long flipAt = -1L;
   private int flipDir;
   private long closingAt = -1L;
   private boolean sentClose;
   private int selected = -1;
   private int paramIndex;
   private final List<Hit> hits = new ArrayList<>();

   record Hit(int x0, int y0, int x1, int y1, Runnable action, List<Component> tooltip) {
      boolean in(double x, double y) {
         return x >= this.x0 && x < this.x1 && y >= this.y0 && y < this.y1;
      }
   }

   public HeavensDoorScreen(RohanBookClient.Session session) {
      super(Component.translatable("gui.viltrumitecore.rohan.title", session.targetName));
      this.session = session;
      this.selectFirstAllowed();
   }

   /** A new OPEN while the screen is up (the manuscript, a second body): keep the screen, show the new book. */
   void replace(RohanBookClient.Session s) {
      this.session = s;
      this.page = 0;
      this.pageShownAt = System.currentTimeMillis();
      this.closingAt = -1L;
      this.sentClose = false;
      this.selectFirstAllowed();
   }

   void serverClosed() {
      this.sentClose = true;
      if (this.closingAt < 0L) {
         this.closingAt = System.currentTimeMillis();
      }
   }

   private void selectFirstAllowed() {
      this.selected = -1;
      this.paramIndex = 0;
      for (int i = 0; i < this.session.entries.size(); i++) {
         if (this.session.entries.get(i).refusal().isEmpty()) {
            this.selected = i;
            return;
         }
      }
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void removed() {
      if (!this.sentClose) {
         this.sentClose = true;
         RohanBookClient.closeByReader(this.session);
      }
   }

   @Override
   public void onClose() {
      this.startClosing();
   }

   private void startClosing() {
      if (this.closingAt < 0L) {
         this.closingAt = System.currentTimeMillis();
         if (!this.sentClose) {
            this.sentClose = true;
            RohanBookClient.closeByReader(this.session);
         }
      }
   }

   @Override
   public void tick() {
      if (this.closingAt >= 0L && System.currentTimeMillis() - this.closingAt > CLOSE_MS && this.minecraft != null) {
         this.minecraft.setScreen(null);
      }
   }

   private int pageCount() {
      return Math.max(1, this.session.pages.pages().size());
   }

   private void flip(int dir) {
      int next = Mth.clamp(this.page + dir, 0, this.pageCount() - 1);
      if (next == this.page) {
         return;
      }
      this.page = next;
      this.flipDir = dir;
      this.flipAt = System.currentTimeMillis();
      this.pageShownAt = this.flipAt + FLIP_MS / 2;
      RohanBookClient.send(RohanBookC2SPacket.FLIP, this.session, "", 0);
   }

   private int paramCount(RohanBookS2CPacket.Entry e) {
      InstructionParam p = param(e);
      return switch (p) {
         case ENTITY -> this.session.candidates.size();
         case POINT -> this.session.points.size();
         case KIND -> AttackKind.values().length;
         default -> 0;
      };
   }

   static InstructionParam param(RohanBookS2CPacket.Entry e) {
      InstructionParam[] v = InstructionParam.values();
      return e.param() >= 0 && e.param() < v.length ? v[e.param()] : InstructionParam.NONE;
   }

   private Component paramLabel(RohanBookS2CPacket.Entry e, int i) {
      return switch (param(e)) {
         case ENTITY -> i < this.session.candidates.size() ? Component.translatable("gui.viltrumitecore.rohan.param.entity", this.session.candidates.get(i).name())
            : Component.empty();
         case POINT -> {
            if (i >= this.session.points.size()) {
               yield Component.empty();
            }
            RohanBookS2CPacket.Point p = this.session.points.get(i);
            yield Component.translatable("gui.viltrumitecore.rohan.param.point",
               Component.translatable(p.label()).append(String.format(Locale.ROOT, " (%d %d %d)", p.pos().getX(), p.pos().getY(), p.pos().getZ())));
         }
         case KIND -> Component.translatable("gui.viltrumitecore.rohan.param.kind",
            Component.translatable("book.viltrumitecore.kind." + AttackKind.values()[Math.min(i, AttackKind.values().length - 1)].name().toLowerCase(Locale.ROOT)));
         default -> Component.empty();
      };
   }

   private void write() {
      if (this.selected < 0 || this.selected >= this.session.entries.size() || this.session.readOnly()) {
         return;
      }
      RohanBookS2CPacket.Entry e = this.session.entries.get(this.selected);
      if (!e.refusal().isEmpty()) {
         return;
      }
      RohanBookClient.send(RohanBookC2SPacket.WRITE, this.session, e.id(), this.paramIndex);
   }

   private void moveSelection(int dir) {
      int n = this.session.entries.size();
      if (n == 0) {
         return;
      }
      int s = this.selected < 0 ? (dir > 0 ? -1 : n) : this.selected;
      for (int k = 0; k < n; k++) {
         s = (s + dir + n) % n;
         if (this.session.entries.get(s).refusal().isEmpty()) {
            this.selected = s;
            this.paramIndex = 0;
            return;
         }
      }
   }

   @Override
   public boolean keyPressed(int key, int scan, int mods) {
      switch (key) {
         case GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_LEFT -> {
            this.flip(-1);
            return true;
         }
         case GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_RIGHT -> {
            this.flip(1);
            return true;
         }
         case GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_UP -> {
            this.moveSelection(-1);
            return true;
         }
         case GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_DOWN -> {
            this.moveSelection(1);
            return true;
         }
         case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
            this.write();
            return true;
         }
         default -> {
            return super.keyPressed(key, scan, mods);
         }
      }
   }

   @Override
   public boolean mouseClicked(double x, double y, int button) {
      for (Hit h : List.copyOf(this.hits)) {
         if (h.in(x, y) && h.action != null) {
            h.action.run();
            return true;
         }
      }
      return super.mouseClicked(x, y, button);
   }

   @Override
   public boolean mouseScrolled(double x, double y, double delta) {
      if (delta != 0.0) {
         this.flip(delta > 0 ? -1 : 1);
         return true;
      }
      return false;
   }

   /** Book rectangle for a screen (pure): width up to 360 px, 0.625 aspect, centred. */
   public static int[] layout(int width, int height) {
      int w = Math.min(width - 16, 360);
      int h = Math.round(w * 0.625F);
      if (h > height - 24) {
         h = height - 24;
         w = Math.round(h / 0.625F);
      }
      return new int[]{(width - w) / 2, (height - h) / 2, w, h};
   }

   @Override
   public void render(GuiGraphics g, int mx, int my, float partial) {
      this.renderBackground(g);
      this.hits.clear();
      int[] r = layout(this.width, this.height);
      int bx = r[0], by = r[1], bw = r[2], bh = r[3];
      long now = System.currentTimeMillis();
      float close = this.closingAt < 0L ? 0.0F : Math.min(1.0F, (now - this.closingAt) / (float)CLOSE_MS);
      g.pose().pushPose();
      if (close > 0.0F) {
         // the book folds shut toward the spine
         float sx = 1.0F - close * close;
         g.pose().translate(bx + bw / 2.0F, 0.0F, 0.0F);
         g.pose().scale(Math.max(0.02F, sx), 1.0F, 1.0F);
         g.pose().translate(-(bx + bw / 2.0F), 0.0F, 0.0F);
      }
      g.blit(BOOK, bx, by, bw, bh, 0.0F, 0.0F, TEX_W, TEX_H, 256, 256);
      int pad = Math.max(10, bw / 18);
      int pageW = bw / 2 - pad * 2;
      int lx = bx + pad, rx = bx + bw / 2 + pad, top = by + pad;
      List<Component> tooltip = null;
      this.drawLeft(g, lx, top, pageW, bh - pad * 2, now, mx, my);
      List<Component> t2 = this.drawRight(g, rx, top, pageW, bh - pad * 2, mx, my);
      if (t2 != null) {
         tooltip = t2;
      }
      this.drawFlip(g, bx, by, bw, bh, now);
      g.pose().popPose();
      this.drawResult(g, bx, by + bh + 4, bw);
      if (tooltip != null && close == 0.0F) {
         g.renderComponentTooltip(this.font, tooltip, mx, my);
      }
   }

   private void text(GuiGraphics g, Component c, int x, int y, int color) {
      g.drawString(this.font, c, x, y, color, false);
   }

   private void drawLeft(GuiGraphics g, int x, int y, int w, int h, long now, int mx, int my) {
      Component title = Component.translatable("gui.viltrumitecore.rohan.title", this.session.targetName);
      this.text(g, title.copy().withStyle(ChatFormatting.BOLD), x, y, INK);
      List<BookPages.Page> pages = this.session.pages.pages();
      int yy = y + 13;
      if (pages.isEmpty()) {
         this.text(g, Component.translatable("gui.viltrumitecore.rohan.empty"), x, yy, FADED);
      } else {
         BookPages.Page p = pages.get(Math.min(this.page, pages.size() - 1));
         Component pt = Component.translatable(p.title());
         this.text(g, pt, x, yy, ACCENT);
         g.fill(x, yy + 9, x + this.font.width(pt), yy + 10, ACCENT);
         yy += 14;
         // handwriting: the lines appear one after another, a pen dot at the newest one
         long shown = Math.max(0L, now - this.pageShownAt);
         int reveal = (int)(shown / 70L);
         int drawn = 0;
         int bottom = y + h - 12;
         for (BookPages.Line line : p.lines()) {
            List<FormattedCharSequence> wrapped = this.font.split(RohanBookClient.line(line), w);
            for (FormattedCharSequence seq : wrapped) {
               if (drawn > reveal || yy > bottom - 9) {
                  break;
               }
               g.drawString(this.font, seq, x, yy, line.key().endsWith(".written") ? RED : INK, false);
               if (drawn == reveal) {
                  int ex = x + Math.min(w, this.font.width(seq)) + 2;
                  g.fill(ex, yy + 6, ex + 2, yy + 8, INK);
               }
               yy += 10;
               drawn++;
            }
         }
      }
      if (this.session.partial()) {
         this.text(g, Component.translatable("gui.viltrumitecore.rohan.partial"), x, y + h - 22, FADED);
      }
      Component pn = Component.translatable("gui.viltrumitecore.rohan.page", this.page + 1, this.pageCount());
      this.text(g, pn, x + (w - this.font.width(pn)) / 2, y + h - 9, FADED);
      // corner arrows
      if (this.page > 0) {
         this.arrow(g, x, y + h - 10, "<", () -> this.flip(-1), Component.translatable("gui.viltrumitecore.rohan.prev"));
      }
      if (this.page < this.pageCount() - 1) {
         this.arrow(g, x + w - 8, y + h - 10, ">", () -> this.flip(1), Component.translatable("gui.viltrumitecore.rohan.next"));
      }
   }

   private void arrow(GuiGraphics g, int x, int y, String s, Runnable action, Component tip) {
      this.text(g, Component.literal(s).withStyle(ChatFormatting.BOLD), x, y, INK);
      this.hits.add(new Hit(x - 2, y - 2, x + 10, y + 10, action, List.of(tip)));
   }

   private List<Component> drawRight(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
      List<Component> tooltip = null;
      RohanBookClient.Session s = this.session;
      float left = s.secondsLeft();
      Component time = Component.translatable("gui.viltrumitecore.rohan.time", String.format(Locale.ROOT, "%.1f", left));
      this.text(g, time, x, y, left < 2.0F ? RED : INK);
      // time bar
      float frac = s.ticks <= 0 ? 0.0F : Mth.clamp(left * 20.0F / Math.max(1, s.ticks + 1), 0.0F, 1.0F);
      g.fill(x, y + 10, x + w, y + 12, 0x40201A10);
      g.fill(x, y + 10, x + Math.round(w * frac), y + 12, left < 2.0F ? RED : ACCENT);
      Component lines = Component.translatable("gui.viltrumitecore.rohan.lines", s.lines);
      Component ink = Component.translatable("gui.viltrumitecore.rohan.ink", Math.round(s.ink));
      this.text(g, lines, x, y + 15, INK);
      this.text(g, ink, x + w - this.font.width(ink), y + 15, INK);
      int yy = y + 28;
      if (s.mass()) {
         for (FormattedCharSequence seq : this.font.split(Component.translatable("gui.viltrumitecore.rohan.mass", s.targets.length), w)) {
            g.drawString(this.font, seq, x, yy, ACCENT, false);
            yy += 9;
         }
         yy += 2;
      }
      if (s.readOnly()) {
         this.text(g, Component.translatable("gui.viltrumitecore.rohan.read_only").withStyle(ChatFormatting.ITALIC), x, yy, RED);
         yy += 12;
      } else {
         this.text(g, Component.translatable("gui.viltrumitecore.rohan.instructions").withStyle(ChatFormatting.UNDERLINE), x, yy, ACCENT);
         yy += 11;
         int listBottom = y + h - 46;
         for (int i = 0; i < s.entries.size() && yy < listBottom; i++) {
            RohanBookS2CPacket.Entry e = s.entries.get(i);
            boolean refused = !e.refusal().isEmpty();
            boolean sel = i == this.selected;
            Component name = Component.translatable("book.viltrumitecore.instruction." + e.id());
            Component cost = Component.translatable("gui.viltrumitecore.rohan.cost", Math.round(e.cost()));
            int costW = this.font.width(cost);
            String clipped = this.font.plainSubstrByWidth(name.getString(), w - costW - 6);
            Component shown = Component.literal(clipped);
            if (refused) {
               shown = shown.copy().withStyle(ChatFormatting.STRIKETHROUGH);
            }
            if (sel) {
               g.fill(x - 2, yy - 1, x + w + 1, yy + 9, 0x302F7A5A);
            }
            this.text(g, shown, x, yy, refused ? FADED : sel ? ACCENT : INK);
            this.text(g, cost, x + w - costW, yy, refused ? FADED : e.cost() > s.ink ? RED : INK);
            final int idx = i;
            List<Component> tip = new ArrayList<>();
            tip.add(name);
            tip.add(Component.translatable("book.viltrumitecore.instruction." + e.id() + ".desc").withStyle(ChatFormatting.GRAY));
            if (refused) {
               tip.add(Component.translatable("book.viltrumitecore.refuse." + e.refusal()).withStyle(ChatFormatting.RED));
            }
            Hit hit = new Hit(x - 2, yy - 1, x + w + 1, yy + 9, refused ? null : () -> {
               if (this.selected == idx) {
                  this.write();
               } else {
                  this.selected = idx;
                  this.paramIndex = 0;
               }
            }, tip);
            this.hits.add(hit);
            if (hit.in(mx, my)) {
               tooltip = tip;
            }
            yy += 10;
         }
         // parameter of the chosen line
         if (this.selected >= 0 && this.selected < s.entries.size()) {
            RohanBookS2CPacket.Entry e = s.entries.get(this.selected);
            int n = this.paramCount(e);
            int py = y + h - 44;
            if (n > 0) {
               this.paramIndex = Math.floorMod(this.paramIndex, n);
               Component label = this.paramLabel(e, this.paramIndex);
               String clipped = this.font.plainSubstrByWidth(label.getString(), w - 24);
               this.arrow(g, x, py, "<", () -> this.paramIndex = Math.floorMod(this.paramIndex - 1, n), label);
               this.text(g, Component.literal(clipped), x + 10, py, INK);
               this.arrow(g, x + w - 8, py, ">", () -> this.paramIndex = Math.floorMod(this.paramIndex + 1, n), label);
            }
            boolean can = e.refusal().isEmpty() && s.lines > 0 && e.cost() <= s.ink;
            tooltip = this.button(g, x, y + h - 32, w, Component.translatable("gui.viltrumitecore.rohan.write"), can, this::write, mx, my, tooltip, null);
         }
      }
      // already written into this book (this session)
      if (!s.written.isEmpty()) {
         int wy = y + h - 58 - Math.min(3, s.written.size()) * 9;
         if (s.readOnly()) {
            wy = yy + 4;
         }
         this.text(g, Component.translatable("gui.viltrumitecore.rohan.written_here"), x, wy, FADED);
         for (int i = Math.max(0, s.written.size() - 3); i < s.written.size(); i++) {
            wy += 9;
            this.text(g, Component.literal(this.font.plainSubstrByWidth(s.written.get(i).getString(), w)), x, wy, RED);
         }
      }
      int half = (w - 4) / 2;
      tooltip = this.button(g, x, y + h - 18, half, Component.translatable("gui.viltrumitecore.rohan.rip"), !s.readOnly(),
         () -> RohanBookClient.send(RohanBookC2SPacket.RIP, this.session, "", 0), mx, my, tooltip, null);
      tooltip = this.button(g, x + half + 4, y + h - 18, half, Component.translatable("gui.viltrumitecore.rohan.close"), true, this::startClosing, mx, my, tooltip,
         Component.translatable("gui.viltrumitecore.rohan.hint"));
      return tooltip;
   }

   /** An ink-stamp button; returns the tooltip to show (if hovered). */
   private List<Component> button(GuiGraphics g, int x, int y, int w, Component label, boolean enabled, Runnable action, int mx, int my,
      List<Component> tooltip, Component hint) {
      boolean hover = mx >= x && mx < x + w && my >= y && my < y + 14;
      int border = enabled ? (hover ? ACCENT : INK) : FADED;
      g.fill(x, y, x + w, y + 14, enabled && hover ? 0x302F7A5A : 0x18201A10);
      g.fill(x, y, x + w, y + 1, border);
      g.fill(x, y + 13, x + w, y + 14, border);
      g.fill(x, y, x + 1, y + 14, border);
      g.fill(x + w - 1, y, x + w, y + 14, border);
      String clipped = this.font.plainSubstrByWidth(label.getString(), w - 4);
      this.text(g, Component.literal(clipped), x + (w - this.font.width(clipped)) / 2, y + 3, enabled ? INK : FADED);
      this.hits.add(new Hit(x, y, x + w, y + 14, enabled ? action : null, List.of(label)));
      if (hover && hint != null) {
         return List.of(label, hint.copy().withStyle(ChatFormatting.GRAY));
      }
      return tooltip;
   }

   private void drawFlip(GuiGraphics g, int bx, int by, int bw, int bh, long now) {
      if (this.flipAt < 0L) {
         return;
      }
      float f = (now - this.flipAt) / (float)FLIP_MS;
      if (f >= 1.0F) {
         this.flipAt = -1L;
         return;
      }
      // a sheet turning over the spine: its visible width follows |cos|, darker as it stands upright
      int spine = bx + bw / 2;
      int half = bw / 2 - 6;
      float c = (float)Math.cos(f * Math.PI);
      int width = Math.round(Math.abs(c) * half);
      boolean leftSide = this.flipDir > 0 ? c < 0.0F : c > 0.0F;
      int x0 = leftSide ? spine - width : spine, x1 = leftSide ? spine : spine + width;
      int shade = (int)(40 + 60 * (1.0F - Math.abs(c)));
      g.fill(x0, by + 5, x1, by + bh - 5, 0xFFF3E9D2);
      g.fill(x0, by + 5, x1, by + bh - 5, (shade << 24) | 0x3A2A10);
      g.fill(leftSide ? x0 : x1 - 1, by + 5, leftSide ? x0 + 1 : x1, by + bh - 5, 0x60201008);
   }

   private void drawResult(GuiGraphics g, int x, int y, int w) {
      RohanBookClient.Session s = this.session;
      if (s.result == null) {
         return;
      }
      long age = RohanBookClient.gameTime() - s.resultAt;
      if (age > 70) {
         return;
      }
      int color = s.resultOk ? 0xFFB8F0C8 : 0xFFFF9A9A;
      g.drawCenteredString(this.font, s.result, x + w / 2, y, color);
   }
}
