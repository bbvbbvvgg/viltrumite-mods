package dev.baranhan.viltrumitecore.hero.thor.bridge;

import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorRules;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Per-move authoring registry: a move can start only once a definition and a
 * handler are registered for its id. Each move has one owner; a second
 * registration fails, so independent sessions cannot overwrite each other.
 * Retired moves ({@link ThorMove#retired()}) can never be registered.
 * The foundation registers only the technical DRAW / HOLSTER timings (no
 * input reaches them).
 */
public final class ThorMoves {
   public static final String FOUNDATION = "foundation";

   private record Entry(String owner, ThorMoveDefinition definition, ThorMoveHandler handler) {
   }

   private static final Map<ThorMove, Entry> ENTRIES = new EnumMap<>(ThorMove.class);

   static {
      registerFoundation();
   }

   private ThorMoves() {
   }

   public static synchronized void register(String owner, ThorMove move, ThorMoveDefinition definition, ThorMoveHandler handler) {
      if (owner == null || owner.isBlank() || move == null || definition == null || handler == null) {
         throw new IllegalArgumentException("owner, move, definition and handler are required");
      }

      if (move.retired()) {
         throw new IllegalArgumentException(move + " is retired: not an approved mechanic");
      }

      Entry existing = ENTRIES.get(move);
      if (existing != null) {
         throw new IllegalStateException(move + " is already authored by " + existing.owner());
      }

      ENTRIES.put(move, new Entry(owner, definition, handler));
   }

   public static synchronized boolean authored(ThorMove move) {
      return ENTRIES.containsKey(move);
   }

   @Nullable
   public static synchronized ThorMoveDefinition definition(ThorMove move) {
      Entry entry = ENTRIES.get(move);
      return entry == null ? null : entry.definition();
   }

   /** The registered handler, or {@link ThorMoveHandler#NONE}. */
   public static synchronized ThorMoveHandler handler(ThorMove move) {
      Entry entry = ENTRIES.get(move);
      return entry == null ? ThorMoveHandler.NONE : entry.handler();
   }

   @Nullable
   public static synchronized String owner(ThorMove move) {
      Entry entry = ENTRIES.get(move);
      return entry == null ? null : entry.owner();
   }

   /** Tests only: back to the foundation state (DRAW / HOLSTER). */
   public static synchronized void resetForTests() {
      ENTRIES.clear();
      registerFoundation();
   }

   private static void registerFoundation() {
      ENTRIES.put(ThorMove.DRAW, new Entry(FOUNDATION,
         new ThorMoveDefinition(ThorRules.DRAW_INTRO, ThorRules.DRAW_MAIN, ThorRules.DRAW_FINISH, MovePhase.FINISH, 1), ThorMoveHandler.NONE));
      ENTRIES.put(ThorMove.HOLSTER, new Entry(FOUNDATION,
         new ThorMoveDefinition(ThorRules.HOLSTER_INTRO, ThorRules.HOLSTER_MAIN, ThorRules.HOLSTER_FINISH, MovePhase.FINISH, 1), ThorMoveHandler.NONE));
   }
}
