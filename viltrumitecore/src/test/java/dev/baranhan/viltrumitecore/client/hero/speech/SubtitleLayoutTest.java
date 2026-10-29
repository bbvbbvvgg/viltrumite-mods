package dev.baranhan.viltrumitecore.client.hero.speech;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

class SubtitleLayoutTest {
   /** Advance widths of the vanilla pixel font (Latin and Cyrillic letters 6, space 4, narrow punctuation 2). */
   static final ToIntFunction<String> FONT = text -> {
      int width = 0;
      for (char c : text.toCharArray()) {
         width += switch (c) {
            case ' ' -> 4;
            case '.', ',', '!', ':', ';', 'i', '\'' -> 2;
            case 'l' -> 3;
            case 't', 'I' -> 4;
            default -> c >= 0x4E00 ? 9 : 6;
         };
      }

      return width;
   };
   static final String REFERENCE = "Мой меч способен рассечь сам мир.\nНи расстояние, ни пространство\nне станут преградой для этого удара.";

   @Test
   void referenceLineMatchesTheReferencePanel() {
      SubtitleLayout.Result layout = SubtitleLayout.layout("РЕЙНХАРД", List.of(REFERENCE), FONT, 960, true);
      assertNotNull(layout);
      assertEquals(1, layout.pages().size(), "the reference text is one three-line page");
      assertEquals(List.of("Мой меч способен рассечь сам мир.", "Ни расстояние, ни пространство", "не станут преградой для этого удара."),
         layout.pages().get(0).lines());
      assertEquals(64, layout.height());
      assertEquals(75, layout.portraitWidth());
      assertEquals(7, layout.nameY());
      assertEquals(21, layout.bodyY());
      assertEquals(9 + FONT.applyAsInt("не станут преградой для этого удара.") + 35, layout.textWidth());
      assertTrue(Math.abs(layout.textWidth() - 244) <= 4, "reference text panel is 244 px wide: " + layout.textWidth());
      assertEquals(layout.textWidth() + 5 + 75, layout.totalWidth());
   }

   @Test
   void shortLinesUseACompactPanel() {
      SubtitleLayout.Result layout = SubtitleLayout.layout("REINHARD", List.of("Enough."), FONT, 960, true);
      assertEquals(1, layout.lines());
      assertEquals(SubtitleLayout.MIN_HEIGHT, layout.height());
      assertEquals(SubtitleLayout.MIN_TEXT_PANEL, layout.textWidth());
      assertEquals(SubtitleLayout.portraitWidth(SubtitleLayout.MIN_HEIGHT), layout.portraitWidth());
      assertTrue(layout.nameY() > 7, "a single line is centred in its smaller panel");
      SubtitleLayout.Result two = SubtitleLayout.layout("REINHARD", List.of("A sword is not drawn lightly.\nRemember that."), FONT, 960, true);
      assertEquals(2, two.lines());
      assertEquals(53, two.height());
   }

   @Test
   void longTextPagesAtSentencesWithAStablePanel() {
      String text = "This is a preview of a long hero line. Long text is split into short pages at sentence ends, so the panel never grows too large. "
         + "Each page stays on screen long enough to be read calmly, and the next one fades in softly.";
      SubtitleLayout.Result layout = SubtitleLayout.layout("REINHARD", List.of(text, "When the line is over, the panel quietly fades away."), FONT, 960, true);
      assertTrue(layout.pages().size() >= 3, "two fragments, the first one over several pages");
      for (SubtitleLayout.Page page : layout.pages()) {
         assertTrue(page.lines().size() <= SubtitleLayout.MAX_LINES);
         for (String line : page.lines()) {
            assertTrue(FONT.applyAsInt(line) <= SubtitleLayout.PREFERRED_WRAP, line);
         }
      }

      assertTrue(layout.pages().get(0).lines().get(layout.pages().get(0).lines().size() - 1).endsWith("."), "the first page ends at a sentence");
      assertEquals("When the line is over, the panel quietly fades away.", String.join(" ", layout.pages().get(layout.pages().size() - 1).lines()),
         "a new fragment starts a new page");
      assertEquals(64, layout.height(), "one size for every page: the largest");
      assertTrue(layout.textWidth() <= 9 + SubtitleLayout.PREFERRED_WRAP + 35);
   }

   @Test
   void aSentenceLongerThanThreeLinesSplitsIntoBalancedPages() {
      String sentence = "word ".repeat(80).trim() + ".";
      List<List<String>> pages = SubtitleLayout.paginate(sentence, 120, FONT);
      int lines = SubtitleLayout.wrap(sentence, 120, FONT).size();
      assertEquals((lines + 2) / 3, pages.size());
      for (List<String> page : pages) {
         assertTrue(page.size() >= 2 && page.size() <= 3, "balanced chunks, no orphan line: " + page.size());
      }
   }

   @Test
   void aLongSentenceIsCutBetweenClausesIntoEvenPages() {
      String sentence = "Каждая страница держится на экране столько, сколько нужно, чтобы спокойно её прочитать, а следующая появляется плавно.";
      assertTrue(SubtitleLayout.wrap(sentence, SubtitleLayout.PREFERRED_WRAP, FONT).size() > SubtitleLayout.MAX_LINES, "needs two pages");
      List<List<String>> pages = SubtitleLayout.paginate(sentence, SubtitleLayout.PREFERRED_WRAP, FONT);
      assertEquals(2, pages.size());
      assertTrue(String.join(" ", pages.get(0)).endsWith("нужно,"), "the cut falls after a comma near the middle: " + pages.get(0));
      assertEquals(pages.get(0).size(), pages.get(1).size(), "even pages");
      assertEquals(sentence, String.join(" ", pages.get(0)) + " " + String.join(" ", pages.get(1)), "no word is lost");
   }

   @Test
   void authoredBreaksGiveWayWhenALineHasToBeRewrapped() {
      List<List<String>> wide = SubtitleLayout.paginate(REFERENCE, SubtitleLayout.PREFERRED_WRAP, FONT);
      assertEquals(List.of(List.of("Мой меч способен рассечь сам мир.", "Ни расстояние, ни пространство", "не станут преградой для этого удара.")), wide,
         "authored breaks stay while every authored line fits");
      int narrow = FONT.applyAsInt("Ни расстояние, ни пространство");
      List<List<String>> pages = SubtitleLayout.paginate(REFERENCE, narrow, FONT);
      assertEquals(2, pages.size());
      List<String> second = pages.get(1);
      assertEquals("Ни расстояние, ни пространство не станут преградой для этого удара.", String.join(" ", second), "no word is lost");
      int shortest = second.stream().mapToInt(FONT).min().orElseThrow();
      int longest = second.stream().mapToInt(FONT).max().orElseThrow();
      assertTrue(shortest * 2 >= longest, "no stray word on its own line: " + second);
      second.forEach(line -> assertTrue(FONT.applyAsInt(line) <= narrow));
   }

   @Test
   void wrapKeepsAuthoredBreaksAndCutsOverlongWords() {
      assertEquals(List.of("one", "two"), SubtitleLayout.wrap("one\ntwo", 200, FONT));
      List<String> cut = SubtitleLayout.wrap("x".repeat(50), 60, FONT);
      assertTrue(cut.size() >= 5);
      cut.forEach(line -> assertTrue(FONT.applyAsInt(line) <= 60));
      List<String> cjk = SubtitleLayout.wrap("我的剑足以斩开这个世界。无论距离还是空间，都无法阻挡这一击。", 90, FONT);
      assertTrue(cjk.size() >= 3, "text without spaces wraps by character");
      assertEquals(2, SubtitleLayout.sentences("我的剑足以斩开这个世界。都无法阻挡这一击。").size());
      assertEquals(List.of(new SubtitleLayout.Sentence("Wait...", false), new SubtitleLayout.Sentence("\"Now!\"", true), new SubtitleLayout.Sentence("Go", false)),
         SubtitleLayout.sentences("Wait... \"Now!\"\nGo"));
   }

   @Test
   void smallScreensShrinkTheTextThenDropThePortrait() {
      SubtitleLayout.Result wide = SubtitleLayout.layout("REINHARD", List.of(REFERENCE), FONT, 960, true);
      SubtitleLayout.Result narrow = SubtitleLayout.layout("REINHARD", List.of(REFERENCE), FONT, 300, true);
      SubtitleLayout.Result tiny = SubtitleLayout.layout("REINHARD", List.of(REFERENCE), FONT, 200, true);
      assertTrue(wide.hasPortrait() && narrow.hasPortrait());
      assertTrue(narrow.totalWidth() <= 300 - 2 * SubtitleLayout.SCREEN_MARGIN, "fits: " + narrow.totalWidth());
      assertTrue(narrow.pages().size() >= wide.pages().size());
      assertFalse(tiny.hasPortrait(), "no room for a readable line next to a portrait");
      assertTrue(tiny.totalWidth() <= 200 - 2 * SubtitleLayout.SCREEN_MARGIN);
      for (int width : new int[]{160, 240, 320, 427, 480, 640, 960, 1280}) {
         SubtitleLayout.Result layout = SubtitleLayout.layout("REINHARD", List.of(REFERENCE), FONT, width, true);
         assertTrue(layout.totalWidth() <= Math.max(width - 2 * SubtitleLayout.SCREEN_MARGIN, 91), width + " -> " + layout.totalWidth());
         layout.pages().forEach(page -> assertTrue(page.lines().size() <= SubtitleLayout.MAX_LINES));
      }
   }

   @Test
   void panelSitsAboveTheBarsAndMovesOverTheActionBarAndItemName() {
      assertEquals(480 - 49 - 1, SubtitleLayout.bottom(480, 49, false, false, true), "11 px above the hearts like the reference");
      assertEquals(480 - 59 - 2, SubtitleLayout.bottom(480, 49, false, true, true), "above the held-item name");
      assertEquals(480 - 72 - 3, SubtitleLayout.bottom(480, 49, true, false, true), "above the action bar text");
      assertEquals(480 - 69 - 2, SubtitleLayout.bottom(480, 69, false, true, true), "with high bars the item name sits right above them");
      assertEquals(480 - 45 - 2, SubtitleLayout.bottom(480, 39, false, true, false), "creative: lower item name");
   }

   @Test
   void longNamesAreShortened() {
      String name = SubtitleLayout.fit("A".repeat(80), 100, FONT);
      assertTrue(name.endsWith("...") && FONT.applyAsInt(name) <= 100);
      assertNull(SubtitleLayout.layout("X", List.of("  "), FONT, 960, true), "no text, no panel");
   }
}
