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

   /**
    * Deterministic fingerprint of the whole move table, the contract client
    * and server must share (review foundation M2): every ThorMove in wire
    * (ordinal) order with its identity (name, priority, pairing, retired)
    * and, when authored, its owner and phase timings. EnumMap / ordinal order
    * only, no hash-map iteration; SHA-256, first 16 hex digits.
    */
   public static synchronized String fingerprint() {
      return digest(table());
   }

   /** The canonical text the fingerprint hashes (tests, mismatch logs). */
   public static synchronized String table() {
      StringBuilder text = new StringBuilder("thor-moves/1");
      for (ThorMove move : ThorMove.values()) {
         text.append('|').append(move.ordinal()).append(':').append(move.name()).append(':').append(move.priority()).append(':')
            .append(move.paired() ? 'P' : '-').append(move.retired() ? 'R' : '-');
         Entry entry = ENTRIES.get(move);
         if (entry != null) {
            ThorMoveDefinition d = entry.definition();
            text.append('=').append(entry.owner()).append(',').append(d.intro()).append(',').append(d.main()).append(',').append(d.finish()).append(',')
               .append(d.cancellableFrom() == null ? "-" : d.cancellableFrom().name()).append(',').append(d.maxSteps());
         }
      }

      return text.toString();
   }

   static String digest(String table) {
      try {
         byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(table.getBytes(java.nio.charset.StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder();
         for (int i = 0; i < 8; i++) {
            hex.append(String.format(java.util.Locale.ROOT, "%02x", hash[i]));
         }

         return hex.toString();
      } catch (java.security.NoSuchAlgorithmException e) {
         throw new IllegalStateException("SHA-256 missing", e);
      }
   }

   /** Handshake rule: both sides must carry the same fingerprint (an absent channel is a mismatch). */
   public static boolean compatible(String local, @Nullable String remote) {
      return local != null && local.equals(remote);
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
