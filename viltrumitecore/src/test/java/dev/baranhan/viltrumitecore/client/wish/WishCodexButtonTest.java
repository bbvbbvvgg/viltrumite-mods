package dev.baranhan.viltrumitecore.client.wish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Regression of the wish screen after the encyclopedia: only the small corner button and the hero badge were
 * added. The button never covers the title, the cards or the buttons at any GUI size; the badge needs the
 * player's own codex record; everything else of the screen stays as it was.
 */
class WishCodexButtonTest {
   private static final int[][] SIZES = {{320, 240}, {300, 200}, {427, 240}, {480, 270}, {640, 360}, {683, 384}, {854, 480}, {960, 540}, {1920, 1080},
      {240, 180}};

   @Test
   void cornerButtonStaysOutOfTheWay() {
      for (int[] size : SIZES) {
         WishLayout l = new WishLayout(size[0], size[1], 10);
         String at = size[0] + "x" + size[1];
         int right = WishScreen.CODEX_X + WishScreen.CODEX_SIZE;
         int bottom = WishScreen.CODEX_Y + WishScreen.CODEX_SIZE;
         assertTrue(bottom <= l.listTop, "button reaches the cards " + at);
         assertTrue(right < size[0] / 2 - 40, "button reaches the title " + at);
         assertTrue(bottom < l.buttonY, at);
         assertTrue(WishScreen.overCodex(WishScreen.CODEX_X + 1, WishScreen.CODEX_Y + 1));
         assertFalse(WishScreen.overCodex(right, bottom));
         // the badge fits a card row
         assertTrue(WishScreen.BADGE <= WishLayout.ROW && WishScreen.BADGE + 30 < l.cardW, at);
      }
   }

   @Test
   void onlyTheTwoAgreedChanges() throws IOException {
      String src = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/client/wish/WishScreen.java"));
      // the badge is drawn only for heroes in this player's own codex record (server snapshot, per player)
      assertTrue(src.contains("if (CodexClientState.hasHero(wish.heroKey())) {"));
      assertEquals(1, count(src, "CodexClientState."), "the badge is the only use of the codex state");
      assertEquals(1, count(src, "new CodexScreen(this)"), "the button opens the encyclopedia with this screen as parent");
      // geometry and behaviour of the menu itself are untouched
      assertEquals(24, WishLayout.ROW);
      assertEquals(4, WishLayout.GAP);
      assertEquals(20, WishLayout.BUTTON_H);
      assertTrue(src.contains("if (this.age == 0) {\n         play(WishSounds.OPEN"), "reopening after the encyclopedia does not replay the open sound");
   }

   private static int count(String s, String what) {
      int n = 0;
      for (int i = s.indexOf(what); i >= 0; i = s.indexOf(what, i + 1)) {
         n++;
      }
      return n;
   }
}
