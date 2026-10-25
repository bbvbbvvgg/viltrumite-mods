package dev.baranhan.viltrumitecore.client.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure layout of the hero choice screen. The heroes sit in one centred row
 * (100 px buttons, 5 px gap, a 24 px head preview above each) whenever it
 * fits; on narrower GUI-scaled widths, down to Minecraft's 320 px minimum,
 * they wrap into centred rows so every choice stays readable and clickable.
 */
public final class HeroChoiceLayout {
   public static final int BUTTON_W = 100;
   public static final int BUTTON_H = 20;
   public static final int GAP = 5;
   public static final int HEAD = 24;
   /** Head top above its button top. */
   public static final int HEAD_RISE = 28;
   static final int ROW_STEP = HEAD_RISE + BUTTON_H + 6;

   private HeroChoiceLayout() {
   }

   /** Top-left of a hero button and of its head preview. */
   public record Cell(int x, int y) {
      public int headX() {
         return x + BUTTON_W / 2 - HEAD / 2;
      }

      public int headY() {
         return y - HEAD_RISE;
      }
   }

   /** Hero cells in display order, the question line and the "remain human" button. */
   public record Layout(List<Cell> heroes, int questionY, int humanX, int humanY, int columns) {
   }

   /** Widest row that fits, then balanced: four heroes become 2 x 2 rather than 3 + 1. */
   public static int columns(int width, int count) {
      int fit = Math.max(1, Math.min(count, (width + GAP) / (BUTTON_W + GAP)));
      int rows = (count + fit - 1) / fit;
      return (count + rows - 1) / rows;
   }

   /** The one-row case reproduces the original fixed layout exactly. */
   public static Layout of(int width, int height, int count) {
      int columns = columns(width, count);
      int rows = (count + columns - 1) / columns;
      int centerX = width / 2;
      int centerY = height / 2;
      int firstButtonY = centerY - 20 - (rows - 1) * ROW_STEP / 2;
      List<Cell> heroes = new ArrayList<>(count);
      for (int index = 0; index < count; index++) {
         int row = index / columns;
         int inRow = Math.min(columns, count - row * columns);
         int rowWidth = inRow * BUTTON_W + (inRow - 1) * GAP;
         heroes.add(new Cell(centerX - rowWidth / 2 + (index % columns) * (BUTTON_W + GAP), firstButtonY + row * ROW_STEP));
      }
      int lastButtonY = firstButtonY + (rows - 1) * ROW_STEP;
      return new Layout(List.copyOf(heroes), firstButtonY - HEAD_RISE - 12, centerX - BUTTON_W / 2,
         lastButtonY + BUTTON_H + 6, columns);
   }
}
