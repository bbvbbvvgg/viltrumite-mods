package dev.baranhan.viltrumitecore.client.codex;

/**
 * Pure geometry of the encyclopedia screen (no Minecraft types, unit-tested for every GUI scale): a back
 * button and the title on top, four tabs, the scrolled article list on the left and the article on the right
 * (a fixed header with the subtitle-style portrait, then the scrolled text). All values are GUI pixels.
 */
public final class CodexLayout {
   public static final int MARGIN = 8;
   public static final int GAP = 6;
   public static final int BACK_W = 56;
   public static final int BACK_H = 16;
   public static final int TAB_H = 16;
   public static final int TAB_GAP = 4;
   public static final int TABS = 4;
   public static final int ROW = 18;
   public static final int ROW_GAP = 2;
   public static final int PITCH = ROW + ROW_GAP;
   public static final int FOOTER = 11;

   public final int width;
   public final int height;
   public final float titleScale;
   public final int titleY;
   public final int backX;
   public final int backY;
   public final int tabsY;
   public final int tabsX;
   public final int tabW;
   public final int bodyTop;
   public final int bodyBottom;
   public final int listX;
   public final int listW;
   public final int detailX;
   public final int detailW;
   public final int portraitW;
   public final int portraitH;
   public final int textTop;
   public final int footerY;

   public CodexLayout(int width, int height) {
      this.width = Math.max(1, width);
      this.height = Math.max(1, height);
      this.titleScale = this.width >= 420 && this.height >= 300 ? 1.5F : 1.0F;
      this.backX = MARGIN;
      this.backY = MARGIN;
      this.titleY = MARGIN + (BACK_H - Math.round(8 * this.titleScale)) / 2;
      this.tabsY = MARGIN + BACK_H + (this.height >= 300 ? 8 : 5);
      int tabsTotal = Math.min(this.width - 2 * MARGIN, 4 * 118 + 3 * TAB_GAP);
      this.tabW = (tabsTotal - (TABS - 1) * TAB_GAP) / TABS;
      this.tabsX = (this.width - (this.tabW * TABS + (TABS - 1) * TAB_GAP)) / 2;
      this.bodyTop = this.tabsY + TAB_H + (this.height >= 300 ? 8 : 5);
      this.footerY = this.height - MARGIN - 8;
      this.bodyBottom = Math.max(this.bodyTop + 2 * PITCH, this.footerY - 4);
      this.listX = MARGIN;
      this.listW = clamp(Math.round(this.width * 0.27F), 92, 170);
      this.detailX = this.listX + this.listW + GAP;
      this.detailW = Math.max(60, this.width - MARGIN - this.detailX);
      int bodyH = this.bodyBottom - this.bodyTop;
      this.portraitH = clamp(Math.round(bodyH * 0.34F), 34, 76);
      this.portraitW = Math.round(this.portraitH * 75 / 64.0F);
      this.textTop = this.bodyTop + this.portraitH + 6;
   }

   public int bodyHeight() {
      return this.bodyBottom - this.bodyTop;
   }

   public int textHeight() {
      return this.bodyBottom - this.textTop;
   }

   /** Left x of tab i. */
   public int tabX(int i) {
      return this.tabsX + i * (this.tabW + TAB_GAP);
   }

   public int tabAt(double mouseX, double mouseY) {
      if (mouseY < this.tabsY || mouseY >= this.tabsY + TAB_H) {
         return -1;
      }
      for (int i = 0; i < TABS; i++) {
         if (mouseX >= this.tabX(i) && mouseX < this.tabX(i) + this.tabW) {
            return i;
         }
      }
      return -1;
   }

   public boolean overBack(double mouseX, double mouseY) {
      return inside(mouseX, mouseY, this.backX, this.backY, BACK_W, BACK_H);
   }

   public boolean overList(double mouseX, double mouseY) {
      return inside(mouseX, mouseY, this.listX, this.bodyTop, this.listW, this.bodyHeight());
   }

   public boolean overText(double mouseX, double mouseY) {
      return inside(mouseX, mouseY, this.detailX, this.textTop, this.detailW, this.textHeight());
   }

   public float maxListScroll(int count) {
      return Math.max(0, count * PITCH - ROW_GAP - this.bodyHeight());
   }

   public float rowY(int index, float scroll) {
      return this.bodyTop + index * PITCH - scroll;
   }

   public int rowAt(double mouseX, double mouseY, float scroll, int count) {
      if (!this.overList(mouseX, mouseY)) {
         return -1;
      }
      double local = mouseY - this.bodyTop + scroll;
      int index = (int)Math.floor(local / PITCH);
      if (index < 0 || index >= count || local - index * PITCH >= ROW) {
         return -1;
      }
      return index;
   }

   public float scrollToShow(int index, float scroll, int count) {
      float top = index * PITCH;
      float bottom = top + ROW;
      float max = this.maxListScroll(count);
      if (top < scroll) {
         return Math.max(0, Math.min(max, top));
      }
      if (bottom > scroll + this.bodyHeight()) {
         return Math.max(0, Math.min(max, bottom - this.bodyHeight()));
      }
      return Math.max(0, Math.min(max, scroll));
   }

   /** Width the article text wraps to (inside the pane, the scroll bar to its right). */
   public int textWrap() {
      return Math.max(40, this.detailW - 14);
   }

   static boolean inside(double x, double y, int left, int top, int w, int h) {
      return x >= left && x < left + w && y >= top && y < top + h;
   }

   static int clamp(int v, int lo, int hi) {
      return Math.max(lo, Math.min(hi, v));
   }
}
