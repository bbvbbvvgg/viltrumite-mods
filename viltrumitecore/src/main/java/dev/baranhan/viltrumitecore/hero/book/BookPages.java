package dev.baranhan.viltrumitecore.hero.book;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/** The written content of one book: pages of lines (lang key + string args), wire-encodable. */
public final class BookPages implements BookPageSink {
   /** Hard caps so a malicious or broken provider cannot flood a packet. */
   public static final int MAX_PAGES = 16;
   public static final int MAX_LINES = 14;
   public static final int MAX_ARGS = 4;
   public static final int MAX_STRING = 96;

   public record Line(String key, List<String> args) {
   }

   public record Page(String title, List<Line> lines) {
   }

   private final List<Page> pages = new ArrayList<>();

   @Override
   public void page(String titleKey) {
      if (this.pages.size() < MAX_PAGES) {
         this.pages.add(new Page(clip(titleKey), new ArrayList<>()));
      }
   }

   @Override
   public void line(String key, String... args) {
      if (this.pages.isEmpty()) {
         this.page("book.viltrumitecore.page.notes");
      }
      Page page = this.pages.get(this.pages.size() - 1);
      if (page.lines().size() >= MAX_LINES) {
         if (this.pages.size() >= MAX_PAGES) {
            return;
         }
         this.page(page.title());
         page = this.pages.get(this.pages.size() - 1);
      }
      List<String> list = new ArrayList<>();
      for (int i = 0; i < args.length && i < MAX_ARGS; i++) {
         list.add(clip(args[i] == null ? "" : args[i]));
      }
      page.lines().add(new Line(clip(key), list));
   }

   public List<Page> pages() {
      return this.pages;
   }

   public int lineCount() {
      int n = 0;
      for (Page p : this.pages) {
         n += p.lines().size();
      }
      return n;
   }

   /** Keeps only the first {@code keep} pages (partial reading of the strongest books). */
   public void truncate(int keep) {
      while (this.pages.size() > keep) {
         this.pages.remove(this.pages.size() - 1);
      }
   }

   static String clip(String s) {
      return s.length() > MAX_STRING ? s.substring(0, MAX_STRING) : s;
   }

   public void write(FriendlyByteBuf buf) {
      buf.writeVarInt(this.pages.size());
      for (Page p : this.pages) {
         buf.writeUtf(p.title(), MAX_STRING);
         buf.writeVarInt(p.lines().size());
         for (Line l : p.lines()) {
            buf.writeUtf(l.key(), MAX_STRING);
            buf.writeVarInt(l.args().size());
            for (String a : l.args()) {
               buf.writeUtf(a, MAX_STRING);
            }
         }
      }
   }

   public static BookPages read(FriendlyByteBuf buf) {
      BookPages out = new BookPages();
      int pages = Math.min(buf.readVarInt(), MAX_PAGES);
      for (int i = 0; i < pages; i++) {
         Page p = new Page(buf.readUtf(MAX_STRING), new ArrayList<>());
         int lines = Math.min(buf.readVarInt(), MAX_LINES);
         for (int j = 0; j < lines; j++) {
            String key = buf.readUtf(MAX_STRING);
            int n = Math.min(buf.readVarInt(), MAX_ARGS);
            List<String> args = new ArrayList<>();
            for (int k = 0; k < n; k++) {
               args.add(buf.readUtf(MAX_STRING));
            }
            p.lines().add(new Line(key, args));
         }
         out.pages.add(p);
      }
      return out;
   }
}
