package dev.baranhan.viltrumitecore.hero.book;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** Book content: page overflow, hard caps and the wire round-trip. */
class BookPagesTest {
   @Test
   void firstLineOpensANotesPage() {
      BookPages b = new BookPages();
      b.line("k");
      assertEquals(1, b.pages().size());
      assertEquals("book.viltrumitecore.page.notes", b.pages().get(0).title());
   }

   @Test
   void longPagesOverflowIntoContinuationAndStopAtCap() {
      BookPages b = new BookPages();
      b.page("t");
      for (int i = 0; i < BookPages.MAX_LINES * (BookPages.MAX_PAGES + 3); i++) {
         b.line("l" + i);
      }
      assertEquals(BookPages.MAX_PAGES, b.pages().size());
      assertEquals(BookPages.MAX_PAGES * BookPages.MAX_LINES, b.lineCount());
      for (BookPages.Page p : b.pages()) {
         assertEquals("t", p.title(), "continuation pages keep the title");
         assertTrue(p.lines().size() <= BookPages.MAX_LINES);
      }
   }

   @Test
   void argsAndStringsAreClipped() {
      BookPages b = new BookPages();
      String huge = "x".repeat(500);
      b.line(huge, "a", "b", "c", "d", "e", null);
      BookPages.Line l = b.pages().get(0).lines().get(0);
      assertEquals(BookPages.MAX_STRING, l.key().length());
      assertEquals(BookPages.MAX_ARGS, l.args().size());
      b.page(huge);
      assertEquals(BookPages.MAX_STRING, b.pages().get(1).title().length());
   }

   @Test
   void nullArgBecomesEmpty() {
      BookPages b = new BookPages();
      b.line("k", (String) null);
      assertEquals("", b.pages().get(0).lines().get(0).args().get(0));
   }

   @Test
   void truncateKeepsFirstPages() {
      BookPages b = new BookPages();
      for (int i = 0; i < 5; i++) {
         b.page("p" + i);
         b.line("l");
      }
      b.truncate(2);
      assertEquals(2, b.pages().size());
      assertEquals("p1", b.pages().get(1).title());
   }

   @Test
   void wireRoundTrip() {
      BookPages b = new BookPages();
      b.page("book.viltrumitecore.page.identity");
      b.line("book.viltrumitecore.line.name", "@entity.minecraft.zombie");
      b.line("book.viltrumitecore.line.health", "20", "20");
      b.page("book.viltrumitecore.page.powers");
      b.line("book.viltrumitecore.line.none");
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      b.write(buf);
      BookPages r = BookPages.read(buf);
      assertEquals(b.pages(), r.pages());
      assertEquals(0, buf.readableBytes());
   }

   @Test
   void readerClampsHostilePageCount() {
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      buf.writeVarInt(BookPages.MAX_PAGES + 50);
      for (int i = 0; i < BookPages.MAX_PAGES; i++) {
         buf.writeUtf("p");
         buf.writeVarInt(0);
      }
      assertEquals(BookPages.MAX_PAGES, BookPages.read(buf).pages().size());
   }
}
