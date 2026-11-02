package dev.baranhan.viltrumitecore.hero.rohan;

import dev.baranhan.viltrumitecore.hero.book.BookProfile;
import dev.baranhan.viltrumitecore.network.packet.RohanBookS2CPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;

/** One open Heaven's Door session: a reader and one (touch) or several (sight) open books. Server only. */
final class BookSession {
   /** One open book inside the session. */
   static final class Book {
      final UUID uuid;
      final int entityId;
      final BookProfile profile;
      int struggles;
      /** Struggle presses in the current second (rate limit). */
      int pressSecond = -1;
      int pressCount;

      Book(UUID uuid, int entityId, BookProfile profile) {
         this.uuid = uuid;
         this.entityId = entityId;
         this.profile = profile;
      }
   }

   final int id;
   final UUID reader;
   final BookOrigin origin;
   final List<Book> books = new ArrayList<>();
   int ticksLeft;
   int linesLeft;
   /** Counts down to the close after the last line; -1 = not closing. */
   int closing = -1;
   int age;
   final List<Integer> candidates = new ArrayList<>();
   final List<BlockPos> points = new ArrayList<>();
   final List<RohanBookS2CPacket.Point> pointLabels = new ArrayList<>();

   BookSession(int id, UUID reader, BookOrigin origin) {
      this.id = id;
      this.reader = reader;
      this.origin = origin;
   }

   boolean mass() {
      return this.origin.sight;
   }

   @Nullable
   Book book(UUID uuid) {
      for (Book b : this.books) {
         if (b.uuid.equals(uuid)) {
            return b;
         }
      }
      return null;
   }

   @Nullable
   Book primary() {
      return this.books.isEmpty() ? null : this.books.get(0);
   }
}
