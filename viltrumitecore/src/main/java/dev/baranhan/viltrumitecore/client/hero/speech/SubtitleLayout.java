package dev.baranhan.viltrumitecore.client.hero.speech;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Pure layout of a hero subtitle in GUI pixels (measured from the reference at
 * GUI scale 2): text panel 244×64 for three 200 px lines with the name at
 * (9, 7), lines at 21/32/43, portrait panel 75×64, 5 px gap. Text wraps at
 * {@link #PREFERRED_WRAP}; every fragment starts a new page; a page holds at
 * most {@link #MAX_LINES} lines and breaks at sentence ends. The panel size is
 * the maximum over all pages, so it never jumps while a long line plays.
 */
public final class SubtitleLayout {
   public static final int PAD_LEFT = 9;
   public static final int PAD_RIGHT = 35;
   public static final int PAD_RIGHT_MIN = 10;
   public static final int NAME_TO_BODY = 14;
   public static final int LINE_PITCH = 11;
   public static final int GLYPH_HEIGHT = 8;
   public static final int MAX_LINES = 3;
   public static final int PREFERRED_WRAP = 214;
   public static final int MIN_WRAP = 72;
   /** Below this wrap width the portrait is dropped (very small windows at a large GUI scale). */
   public static final int PORTRAIT_MIN_WRAP = 120;
   public static final int MIN_TEXT_PANEL = 150;
   public static final int GAP = 5;
   public static final int SCREEN_MARGIN = 6;
   public static final int MIN_HEIGHT = 52;
   public static final int REFERENCE_HEIGHT = 64;
   public static final int REFERENCE_PORTRAIT = 75;
   private static final String ELLIPSIS = "...";

   /** One page: its wrapped lines and its character count (drives the reading time). */
   public record Page(List<String> lines, int chars) {
   }

   public record Result(List<Page> pages, String name, int textWidth, int portraitWidth, int height, int nameY, int bodyY, int lines) {
      public int totalWidth() {
         return this.portraitWidth > 0 ? this.textWidth + GAP + this.portraitWidth : this.textWidth;
      }

      public boolean hasPortrait() {
         return this.portraitWidth > 0;
      }

      public int[] pageChars() {
         int[] chars = new int[this.pages.size()];
         for (int i = 0; i < chars.length; i++) {
            chars[i] = this.pages.get(i).chars();
         }

         return chars;
      }
   }

   private SubtitleLayout() {
   }

   /** Lays out one line for a screen {@code screenWidth} GUI px wide; null when there is no text. */
   @javax.annotation.Nullable
   public static Result layout(String name, List<String> fragments, ToIntFunction<String> width, int screenWidth, boolean portrait) {
      int available = Math.max(MIN_WRAP + PAD_LEFT + PAD_RIGHT_MIN, screenWidth - 2 * SCREEN_MARGIN);
      boolean withPortrait = portrait;
      int wrap = Math.min(PREFERRED_WRAP, available - (REFERENCE_PORTRAIT + GAP) - PAD_LEFT - PAD_RIGHT_MIN);
      if (withPortrait && wrap < PORTRAIT_MIN_WRAP) {
         withPortrait = false;
      }

      if (!withPortrait) {
         wrap = Math.min(PREFERRED_WRAP, available - PAD_LEFT - PAD_RIGHT_MIN);
      }

      wrap = Math.max(MIN_WRAP, wrap);
      List<Page> pages = new ArrayList<>();
      for (String fragment : fragments) {
         for (List<String> lines : paginate(fragment, wrap, width)) {
            pages.add(new Page(List.copyOf(lines), chars(lines)));
         }
      }

      if (pages.isEmpty()) {
         return null;
      }

      int lines = 1;
      int content = 0;
      for (Page page : pages) {
         lines = Math.max(lines, page.lines().size());
         for (String line : page.lines()) {
            content = Math.max(content, width.applyAsInt(line));
         }
      }

      int height = height(lines);
      int portraitWidth = withPortrait ? portraitWidth(height) : 0;
      int room = available - (withPortrait ? portraitWidth + GAP : 0);
      String shown = fit(name, room - PAD_LEFT - PAD_RIGHT_MIN, width);
      content = Math.max(content, width.applyAsInt(shown));
      int textWidth = Math.max(MIN_TEXT_PANEL, PAD_LEFT + content + PAD_RIGHT);
      if (textWidth > room) {
         textWidth = Math.max(PAD_LEFT + content + PAD_RIGHT_MIN, room);
      }

      int nameY = nameY(height, lines);
      return new Result(List.copyOf(pages), shown, textWidth, portraitWidth, height, nameY, nameY + NAME_TO_BODY, lines);
   }

   /**
    * Bottom edge of the panel (GUI y): just above the status bars
    * ({@code barsHeight} = ForgeGui left/right height), and above the held-item
    * name or the action bar while one is shown (vanilla/Forge positions).
    */
   public static int bottom(int screenHeight, int barsHeight, boolean actionBar, boolean itemName, boolean survival) {
      int bottom = screenHeight - barsHeight - 1;
      if (itemName) {
         bottom = Math.min(bottom, screenHeight - Math.max(barsHeight, 59) + (survival ? 0 : 14) - 2);
      }

      if (actionBar) {
         bottom = Math.min(bottom, screenHeight - Math.max(barsHeight + 9, 68) - 4 - 3);
      }

      return bottom;
   }

   /** Panel height for this many body lines: 64 for three (reference), never under {@link #MIN_HEIGHT}. */
   public static int height(int lines) {
      return Math.max(MIN_HEIGHT, 31 + LINE_PITCH * Math.max(1, lines));
   }

   /** Name baseline row: the name + body block sits slightly above centre (reference: 7 px top, 13 px bottom). */
   public static int nameY(int height, int lines) {
      int block = NAME_TO_BODY + LINE_PITCH * (Math.max(1, lines) - 1) + GLYPH_HEIGHT;
      return Math.max(4, Math.round((height - block) / 2.0F) - 3);
   }

   public static int portraitWidth(int height) {
      return Math.round(height * (float)REFERENCE_PORTRAIT / REFERENCE_HEIGHT);
   }

   /** Pages of one fragment: whole sentences packed into at most three lines; a longer sentence is split into balanced chunks. */
   public static List<List<String>> paginate(String fragment, int maxWidth, ToIntFunction<String> width) {
      List<List<String>> pages = new ArrayList<>();
      String current = null;
      boolean currentBreak = false;
      for (Sentence sentence : sentences(fragment)) {
         if (current != null) {
            String joined = current + (currentBreak ? "\n" : " ") + sentence.text();
            if (wrap(flow(joined, maxWidth, width), maxWidth, width).size() <= MAX_LINES) {
               current = joined;
               currentBreak = sentence.lineBreakAfter();
               continue;
            }

            pages.add(even(current, maxWidth, width));
            current = null;
         }

         List<String> lines = wrap(flow(sentence.text(), maxWidth, width), maxWidth, width);
         if (lines.isEmpty()) {
            continue;
         }

         if (lines.size() <= MAX_LINES) {
            current = sentence.text();
            currentBreak = sentence.lineBreakAfter();
         } else {
            pages.addAll(splitLong(flow(sentence.text(), maxWidth, width), maxWidth, width));
         }
      }

      if (current != null) {
         List<String> lines = even(current, maxWidth, width);
         if (!lines.isEmpty()) {
            pages.add(lines);
         }
      }

      return pages;
   }

   /**
    * Word wrap with even lines: the same number of lines as the greedy wrap at
    * {@code maxWidth}, at the narrowest width that still gives that many, so a
    * short sentence on a narrow screen does not leave one word on its own line.
    * Authored breaks are kept only while every authored line fits (see {@link #flow}).
    */
   public static List<String> even(String text, int maxWidth, ToIntFunction<String> width) {
      text = flow(text, maxWidth, width);
      List<String> greedy = wrap(text, maxWidth, width);
      if (greedy.size() < 2) {
         return greedy;
      }

      int low = 1;
      int high = maxWidth;
      while (low < high) {
         int mid = (low + high) >>> 1;
         if (wrap(text, mid, width).size() <= greedy.size()) {
            high = mid;
         } else {
            low = mid + 1;
         }
      }

      List<String> lines = wrap(text, high, width);
      return lines.size() == greedy.size() ? lines : greedy;
   }

   /**
    * The text as it should flow at {@code maxWidth}: authored line breaks stay
    * while every authored line fits on one line; once one of them has to be
    * rewrapped they become spaces, so the rewrap stays even instead of leaving
    * a stray word before the next authored break.
    */
   static String flow(String text, int maxWidth, ToIntFunction<String> width) {
      if (text.indexOf('\n') < 0) {
         return text;
      }

      for (String line : text.split("\n", -1)) {
         if (width.applyAsInt(line.trim()) > maxWidth) {
            return text.replace('\n', ' ');
         }
      }

      return text;
   }

   /** Greedy word wrap; '\n' is an authored line break; a word wider than the line is cut. */
   public static List<String> wrap(String text, int maxWidth, ToIntFunction<String> width) {
      List<String> out = new ArrayList<>();
      for (String paragraph : text.split("\n", -1)) {
         String line = "";
         for (String word : paragraph.trim().split("\\s+")) {
            if (word.isEmpty()) {
               continue;
            }

            String candidate = line.isEmpty() ? word : line + " " + word;
            if (width.applyAsInt(candidate) <= maxWidth) {
               line = candidate;
               continue;
            }

            if (!line.isEmpty()) {
               out.add(line);
               line = "";
            }

            while (width.applyAsInt(word) > maxWidth) {
               int cut = fitPrefix(word, maxWidth, width);
               out.add(word.substring(0, cut));
               word = word.substring(cut);
            }

            line = word;
         }

         if (!line.isEmpty()) {
            out.add(line);
         }
      }

      return out;
   }

   /** A sentence and whether the author put a line break after it. */
   public record Sentence(String text, boolean lineBreakAfter) {
   }

   /** Splits at . ! ? … (closing quotes/brackets stay with the sentence) followed by whitespace. */
   public static List<Sentence> sentences(String text) {
      List<Sentence> out = new ArrayList<>();
      int start = 0;
      int length = text.length();
      int i = 0;
      while (i < length) {
         char c = text.charAt(i);
         if (c == '.' || c == '!' || c == '?' || c == '\u2026' || c == '\u3002' || c == '\uff01' || c == '\uff1f') {
            int end = i + 1;
            while (end < length && closing(text.charAt(end))) {
               end++;
            }

            boolean cjk = c == '\u3002' || c == '\uff01' || c == '\uff1f';
            if (end == length || Character.isWhitespace(text.charAt(end)) || cjk) {
               int next = end;
               boolean lineBreak = false;
               while (next < length && Character.isWhitespace(text.charAt(next))) {
                  lineBreak |= text.charAt(next) == '\n';
                  next++;
               }

               add(out, text.substring(start, end), lineBreak);
               start = next;
               i = next;
               continue;
            }
         }

         i++;
      }

      if (start < length) {
         add(out, text.substring(start), false);
      }

      return out;
   }

   private static void add(List<Sentence> out, String sentence, boolean lineBreak) {
      String trimmed = sentence.strip();
      if (!trimmed.isEmpty()) {
         out.add(new Sentence(trimmed, lineBreak));
      }
   }

   private static boolean closing(char c) {
      return c == '"' || c == '\'' || c == ')' || c == ']' || c == '\u00bb' || c == '\u201d' || c == '\u2019' || c == '\u300d' || c == '\u300f'
         || c == '.' || c == '!' || c == '?' || c == '\u2026';
   }

   /**
    * A sentence longer than one page: cut between clauses (after , ; : or a dash)
    * into the fewest pages of at most three lines, as even as possible; a clause
    * that alone is longer than a page is cut by lines ({@link #balance}).
    */
   static List<List<String>> splitLong(String sentence, int maxWidth, ToIntFunction<String> width) {
      List<String> units = new ArrayList<>();
      List<String> joins = new ArrayList<>();
      List<String> clauses = new ArrayList<>();
      List<String> separators = new ArrayList<>();
      clauses(sentence, clauses, separators);
      for (int c = 0; c < clauses.size(); c++) {
         List<String> lines = wrap(clauses.get(c), maxWidth, width);
         if (lines.size() <= MAX_LINES) {
            units.add(clauses.get(c));
            joins.add(separators.get(c));
         } else {
            List<List<String>> chunks = balance(lines);
            for (int k = 0; k < chunks.size(); k++) {
               units.add(String.join(" ", chunks.get(k)));
               joins.add(k == chunks.size() - 1 ? separators.get(c) : " ");
            }
         }
      }

      int m = units.size();
      int[] pages = new int[m + 1];
      long[] cost = new long[m + 1];
      int[] from = new int[m + 1];
      java.util.Arrays.fill(pages, Integer.MAX_VALUE);
      pages[0] = 0;
      for (int j = 1; j <= m; j++) {
         for (int i = j - 1; i >= 0; i--) {
            int n = wrap(join(units, joins, i, j), maxWidth, width).size();
            if (n > MAX_LINES) {
               break;
            }

            if (pages[i] == Integer.MAX_VALUE) {
               continue;
            }

            long c = cost[i] + (long)n * n;
            if (pages[i] + 1 < pages[j] || pages[i] + 1 == pages[j] && c < cost[j]) {
               pages[j] = pages[i] + 1;
               cost[j] = c;
               from[j] = i;
            }
         }
      }

      List<List<String>> out = new ArrayList<>();
      if (pages[m] == Integer.MAX_VALUE) {
         return balance(wrap(sentence, maxWidth, width));
      }

      for (int j = m; j > 0; j = from[j]) {
         out.add(0, even(join(units, joins, from[j], j), maxWidth, width));
      }

      return out;
   }

   /** Clauses of a sentence, each keeping its trailing , ; : or dash; separators[i] is the whitespace after clause i. */
   private static void clauses(String sentence, List<String> clauses, List<String> separators) {
      int start = 0;
      int n = sentence.length();
      for (int i = 0; i < n; i++) {
         char c = sentence.charAt(i);
         boolean cjk = c == '\uff0c' || c == '\u3001' || c == '\uff1b' || c == '\uff1a';
         boolean soft = cjk || c == ',' || c == ';' || c == ':' || c == '\u2014' || c == '\u2013';
         if (!soft || i + 1 >= n || !cjk && !Character.isWhitespace(sentence.charAt(i + 1))) {
            continue;
         }

         int end = i + 1;
         int next = end;
         while (next < n && Character.isWhitespace(sentence.charAt(next))) {
            next++;
         }

         if (next >= n) {
            break;
         }

         String clause = sentence.substring(start, end).trim();
         if (!clause.isEmpty()) {
            clauses.add(clause);
            separators.add(sentence.substring(end, next).indexOf('\n') >= 0 ? "\n" : " ");
         }

         start = next;
         i = next - 1;
      }

      String tail = sentence.substring(Math.min(start, n)).trim();
      if (!tail.isEmpty()) {
         clauses.add(tail);
         separators.add(" ");
      }
   }

   private static String join(List<String> units, List<String> joins, int from, int to) {
      StringBuilder out = new StringBuilder(units.get(from));
      for (int k = from + 1; k < to; k++) {
         out.append(joins.get(k - 1)).append(units.get(k));
      }

      return out.toString();
   }

   /** Chunks of at most three lines, as even as possible, preferring a cut after a line ending in , ; : or a dash. */
   static List<List<String>> balance(List<String> lines) {
      int n = lines.size();
      int pages = (n + MAX_LINES - 1) / MAX_LINES;
      List<List<String>> out = new ArrayList<>();
      int start = 0;
      for (int page = 0; page < pages; page++) {
         int left = pages - page;
         int remaining = n - start;
         int size = (remaining + left - 1) / left;
         if (left > 1) {
            int shorter = size - 1;
            int restAfterShorter = remaining - shorter;
            if (shorter >= 1 && restAfterShorter <= (left - 1) * MAX_LINES && softEnd(lines.get(start + shorter - 1)) && !softEnd(lines.get(start + size - 1))) {
               size = shorter;
            }
         }

         out.add(List.copyOf(lines.subList(start, start + size)));
         start += size;
      }

      return out;
   }

   private static boolean softEnd(String line) {
      if (line.isEmpty()) {
         return false;
      }

      char c = line.charAt(line.length() - 1);
      return c == ',' || c == ';' || c == ':' || c == '\u2014' || c == '\u2013' || c == '-' || c == '\uff0c' || c == '\u3001';
   }

   private static int fitPrefix(String word, int maxWidth, ToIntFunction<String> width) {
      int cut = 1;
      while (cut < word.length() && width.applyAsInt(word.substring(0, cut + 1)) <= maxWidth) {
         cut++;
      }

      return cut;
   }

   static String fit(String text, int maxWidth, ToIntFunction<String> width) {
      if (width.applyAsInt(text) <= maxWidth) {
         return text;
      }

      String cut = text;
      while (!cut.isEmpty() && width.applyAsInt(cut + ELLIPSIS) > maxWidth) {
         cut = cut.substring(0, cut.length() - 1);
      }

      return cut.isEmpty() ? "" : cut.strip() + ELLIPSIS;
   }

   private static int chars(List<String> lines) {
      int chars = 0;
      for (String line : lines) {
         chars += line.codePointCount(0, line.length());
      }

      return chars;
   }
}
