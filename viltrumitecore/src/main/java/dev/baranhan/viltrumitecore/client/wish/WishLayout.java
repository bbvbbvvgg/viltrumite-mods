package dev.baranhan.viltrumitecore.client.wish;

import dev.baranhan.viltrumitecore.hero.wish.Wish;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure geometry of the «чего ты желаешь?» screen (no Minecraft types, unit-tested for every GUI scale):
 * title on top, a scrolled column of wish cards, the echo line of the selected wish, and the two
 * buttons pinned to the bottom. All values are GUI pixels.
 */
public final class WishLayout {
   public static final int ROW = 24;
   public static final int GAP = 4;
   public static final int PITCH = ROW + GAP;
   public static final int BUTTON_H = 20;
   public static final int MARGIN = 10;

   public final int width;
   public final int height;
   public final int count;
   public final float titleScale;
   public final int titleY;
   public final int cardW;
   public final int cardX;
   public final int listTop;
   public final int listBottom;
   public final int echoY;
   public final int buttonY;
   public final int buttonW;
   public final int confirmX;
   public final int cancelX;

   public WishLayout(int width, int height, int count) {
      this.width = Math.max(1, width);
      this.height = Math.max(1, height);
      this.count = Math.max(0, count);
      this.titleScale = this.height < 280 ? 1.5F : 2.0F;
      this.titleY = clamp(Math.round(this.height * 0.07F), 8, 40);
      int titleBottom = this.titleY + Math.round(9 * this.titleScale);
      this.cardW = Math.min(this.width - 2 * MARGIN, clamp(this.width - 40, 160, 300));
      this.cardX = (this.width - this.cardW) / 2;
      this.buttonY = this.height - MARGIN - BUTTON_H;
      this.echoY = this.buttonY - 14;
      this.listTop = titleBottom + (this.height < 280 ? 8 : 14);
      this.listBottom = Math.max(this.listTop + ROW, this.echoY - 6);
      this.buttonW = Math.max(40, Math.min(120, (this.cardW - 8) / 2));
      int pair = this.buttonW * 2 + 8;
      this.confirmX = (this.width - pair) / 2;
      this.cancelX = this.confirmX + this.buttonW + 8;
   }

   public int listHeight() {
      return this.listBottom - this.listTop;
   }

   public int contentHeight() {
      return this.count <= 0 ? 0 : this.count * PITCH - GAP;
   }

   public float maxScroll() {
      return Math.max(0, this.contentHeight() - this.listHeight());
   }

   public float clampScroll(float scroll) {
      return Math.max(0, Math.min(this.maxScroll(), scroll));
   }

   /** Top of card i for the current scroll (may lie outside the list area: it is scissored). */
   public float rowY(int index, float scroll) {
      return this.listTop + index * PITCH - scroll;
   }

   /** Card under the mouse, or -1 (outside the list area, between cards, or past the last). */
   public int rowAt(double mouseX, double mouseY, float scroll) {
      if (mouseX < this.cardX || mouseX >= this.cardX + this.cardW || mouseY < this.listTop || mouseY >= this.listBottom) {
         return -1;
      }
      double local = mouseY - this.listTop + scroll;
      int index = (int)Math.floor(local / PITCH);
      if (index < 0 || index >= this.count || local - index * PITCH >= ROW) {
         return -1;
      }
      return index;
   }

   /** Scroll that brings card i fully into view (keyboard navigation). */
   public float scrollToShow(int index, float scroll) {
      float top = index * PITCH;
      float bottom = top + ROW;
      if (top < scroll) {
         return this.clampScroll(top);
      }
      if (bottom > scroll + this.listHeight()) {
         return this.clampScroll(bottom - this.listHeight());
      }
      return this.clampScroll(scroll);
   }

   public boolean overConfirm(double mouseX, double mouseY) {
      return inside(mouseX, mouseY, this.confirmX, this.buttonY, this.buttonW, BUTTON_H);
   }

   public boolean overCancel(double mouseX, double mouseY) {
      return inside(mouseX, mouseY, this.cancelX, this.buttonY, this.buttonW, BUTTON_H);
   }

   static boolean inside(double x, double y, int left, int top, int w, int h) {
      return x >= left && x < left + w && y >= top && y < top + h;
   }

   static int clamp(int v, int lo, int hi) {
      return Math.max(lo, Math.min(hi, v));
   }

   /**
    * «исполнено» marks: the wish whose hero the player already is. Only for heroes a single wish grants;
    * wishes sharing a hero (the two Iron Man wishes) differ by more than the hero, so the server answers them.
    */
   public static boolean[] fulfilled(List<Wish> wishes, String heroKey) {
      boolean[] out = new boolean[wishes.size()];
      if (heroKey == null) {
         return out;
      }
      Map<String, Integer> perHero = new HashMap<>();
      for (Wish wish : wishes) {
         perHero.merge(wish.heroKey(), 1, Integer::sum);
      }
      for (int i = 0; i < wishes.size(); i++) {
         Wish wish = wishes.get(i);
         out[i] = wish.heroKey().equals(heroKey) && perHero.get(wish.heroKey()) == 1;
      }
      return out;
   }
}
