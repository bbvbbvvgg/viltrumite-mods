package dev.baranhan.viltrumitecore.client.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PR #27 review: the hero buttons must stay on screen down to the 320 px GUI-scaled minimum. */
class HeroChoiceLayoutTest {
   private static final int HEROES = 4;

   @Test void wideScreensKeepTheOriginalSingleRow() {
      for (int width : new int[]{415, 427, 640, 854, 1920}) {
         for (int height : new int[]{240, 360, 480}) {
            HeroChoiceLayout.Layout layout = HeroChoiceLayout.of(width, height, HEROES);
            int centerX = width / 2, centerY = height / 2;
            assertEquals(4, layout.columns());
            for (int i = 0; i < HEROES; i++) {
               HeroChoiceLayout.Cell cell = layout.heroes().get(i);
               assertEquals(centerX - 207 + i * 105, cell.x(), "legacy heroX at " + width);
               assertEquals(centerY - 20, cell.y());
               assertEquals(centerX - 207 + i * 105 + 50 - 12, cell.headX());
               assertEquals(centerY - 48, cell.headY());
            }
            assertEquals(centerY - 60, layout.questionY());
            assertEquals(centerX - 50, layout.humanX());
            assertEquals(centerY + 6, layout.humanY());
         }
      }
   }

   @Test void narrowGuiScalesWrapIntoBalancedRows() {
      assertEquals(2, HeroChoiceLayout.columns(320, HEROES));
      assertEquals(2, HeroChoiceLayout.columns(414, HEROES));
      assertEquals(4, HeroChoiceLayout.columns(415, HEROES));
   }

   @Test void everyChoiceStaysOnScreenAndApart() {
      for (int width = 320; width <= 1000; width++) {
         for (int height = 240; height <= 300; height += 30) {
            HeroChoiceLayout.Layout layout = HeroChoiceLayout.of(width, height, HEROES);
            List<int[]> boxes = new ArrayList<>();
            for (HeroChoiceLayout.Cell cell : layout.heroes()) {
               boxes.add(new int[]{cell.x(), cell.y(), HeroChoiceLayout.BUTTON_W, HeroChoiceLayout.BUTTON_H});
               boxes.add(new int[]{cell.headX(), cell.headY(), HeroChoiceLayout.HEAD, HeroChoiceLayout.HEAD});
            }
            boxes.add(new int[]{layout.humanX(), layout.humanY(), HeroChoiceLayout.BUTTON_W, HeroChoiceLayout.BUTTON_H});
            for (int[] box : boxes) {
               String where = width + "x" + height + " " + java.util.Arrays.toString(box);
               assertTrue(box[0] >= 0 && box[0] + box[2] <= width, "inside horizontally " + where);
               assertTrue(box[1] >= 0 && box[1] + box[3] <= height, "inside vertically " + where);
            }
            for (int a = 0; a < boxes.size(); a++) {
               for (int b = a + 1; b < boxes.size(); b++) {
                  assertFalse(overlap(boxes.get(a), boxes.get(b)), width + "x" + height + " boxes " + a + "/" + b);
               }
            }
            int firstHeadY = layout.heroes().get(0).headY();
            assertTrue(layout.questionY() >= 0 && layout.questionY() + 9 <= firstHeadY, "question above the heads");
         }
      }
   }

   private static boolean overlap(int[] a, int[] b) {
      return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
   }
}
