package dev.baranhan.viltrumitecore.hero.roster;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.StringJoiner;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Immutable set of {@link HeroAction} ordinals with no 64-action limit (wave 1 · session 01).
 *
 * <p>{@link #ALL} means "every action, including actions appended later"; every hero sends it today.
 * An explicit set holds any ordinal in [0, {@link #ORDINAL_LIMIT}) with no aliasing: ordinal 64 never
 * reads ordinal 0. Wire forms:
 * <ul>
 *   <li>bytes: tag 0 = ALL; tag 1 = dense bit set (varint byte count + little-endian bytes);
 *       tag 2 = sparse (varint count + varint gaps {@code ordinal - previous - 1}). The shorter form wins,
 *       dense on a tie. Every decoder allocation is bounded by {@link #ORDINAL_LIMIT}.</li>
 *   <li>snapshot token: {@code "-1"} for ALL, else {@code "~"} + unpadded base64url of the bytes. A legacy
 *       decimal {@code long} mask is still accepted on read.</li>
 * </ul>
 */
public final class ActionSet {
   /** One past the highest ordinal an explicit set accepts (4 KiB of bits at most). */
   public static final int ORDINAL_LIMIT = 1 << 15;
   static final String ALL_TOKEN = "-1";
   static final char SET_PREFIX = '~';
   static final byte TAG_ALL = 0;
   static final byte TAG_DENSE = 1;
   static final byte TAG_SPARSE = 2;
   private static final long[] NO_WORDS = new long[0];

   /** Every action, including actions appended to {@link HeroAction} later. */
   public static final ActionSet ALL = new ActionSet(true, NO_WORDS);
   /** No action at all. */
   public static final ActionSet NONE = new ActionSet(false, NO_WORDS);

   private final boolean all;
   /** Bit {@code o & 63} of word {@code o >>> 6} is ordinal o. Never ends with a zero word. */
   private final long[] words;
   /** Cached snapshot token; a benign race at worst computes it twice. */
   @Nullable
   private String wire;

   private ActionSet(boolean all, long[] words) {
      this.all = all;
      this.words = words;
   }

   private static ActionSet ofWords(long[] words) {
      int length = words.length;
      while (length > 0 && words[length - 1] == 0L) {
         length--;
      }
      if (length == 0) {
         return NONE;
      }
      return new ActionSet(false, length == words.length ? words : Arrays.copyOf(words, length));
   }

   private static void check(int ordinal) {
      if (ordinal < 0 || ordinal >= ORDINAL_LIMIT) {
         throw new IllegalArgumentException("action ordinal " + ordinal + " is outside [0, " + ORDINAL_LIMIT + ")");
      }
   }

   public static ActionSet of(HeroAction... actions) {
      int[] ordinals = new int[actions.length];
      for (int i = 0; i < actions.length; i++) {
         ordinals[i] = actions[i].ordinal();
      }
      return ofOrdinals(ordinals);
   }

   /** A set of raw ordinals; each must be in [0, {@link #ORDINAL_LIMIT}). */
   public static ActionSet ofOrdinals(int... ordinals) {
      int max = -1;
      for (int ordinal : ordinals) {
         check(ordinal);
         max = Math.max(max, ordinal);
      }
      if (max < 0) {
         return NONE;
      }
      long[] words = new long[(max >>> 6) + 1];
      for (int ordinal : ordinals) {
         words[ordinal >>> 6] |= 1L << ordinal;
      }
      return ofWords(words);
   }

   /** Ordinals {@code from} (inclusive) to {@code to} (exclusive). */
   public static ActionSet range(int from, int to) {
      if (from < 0 || to > ORDINAL_LIMIT || from > to) {
         throw new IllegalArgumentException("bad action range [" + from + ", " + to + ")");
      }
      if (from == to) {
         return NONE;
      }
      long[] words = new long[((to - 1) >>> 6) + 1];
      for (int ordinal = from; ordinal < to; ordinal++) {
         words[ordinal >>> 6] |= 1L << ordinal;
      }
      return ofWords(words);
   }

   /** The pre-wave-1 64-bit mask: {@code -1L} is {@link #ALL}, other values are bits 0..63. */
   public static ActionSet fromLegacyMask(long mask) {
      if (mask == -1L) {
         return ALL;
      }
      return mask == 0L ? NONE : new ActionSet(false, new long[]{mask});
   }

   public boolean isAll() {
      return this.all;
   }

   public boolean isEmpty() {
      return !this.all && this.words.length == 0;
   }

   public boolean contains(@Nullable HeroAction action) {
      return action != null && this.contains(action.ordinal());
   }

   /** Exact for every ordinal; a negative ordinal is never contained. */
   public boolean contains(int ordinal) {
      if (ordinal < 0) {
         return false;
      }
      if (this.all) {
         return true;
      }
      int word = ordinal >>> 6;
      return word < this.words.length && (this.words[word] & (1L << ordinal)) != 0L;
   }

   /** Number of actions; {@link #ALL} counts the current roster. */
   public int size() {
      if (this.all) {
         return HeroAction.values().length;
      }
      int count = 0;
      for (long word : this.words) {
         count += Long.bitCount(word);
      }
      return count;
   }

   /** Ordinals in ascending order; {@link #ALL} streams the current roster. */
   public IntStream ordinals() {
      if (this.all) {
         return IntStream.range(0, HeroAction.values().length);
      }
      long[] bits = this.words;
      return IntStream.range(0, bits.length << 6).filter(ordinal -> (bits[ordinal >>> 6] & (1L << ordinal)) != 0L);
   }

   /** Visits every ordinal in ascending order without boxing. */
   public void forEachOrdinal(IntConsumer consumer) {
      if (this.all) {
         this.ordinals().forEach(consumer);
         return;
      }
      for (int index = 0; index < this.words.length; index++) {
         long word = this.words[index];
         while (word != 0L) {
            consumer.accept((index << 6) + Long.numberOfTrailingZeros(word));
            word &= word - 1;
         }
      }
   }

   /** Highest ordinal + 1, or 0 when empty; the current roster size for {@link #ALL}. */
   public int span() {
      if (this.all) {
         return HeroAction.values().length;
      }
      if (this.words.length == 0) {
         return 0;
      }
      int last = this.words.length - 1;
      return (last << 6) + Long.SIZE - Long.numberOfLeadingZeros(this.words[last]);
   }

   /** The pre-wave-1 64-bit view: {@code -1L} for {@link #ALL}, else bits 0..63 (later ordinals are dropped). */
   public long legacyMask() {
      if (this.all) {
         return -1L;
      }
      return this.words.length == 0 ? 0L : this.words[0];
   }

   public ActionSet with(HeroAction action) {
      return this.with(action.ordinal());
   }

   public ActionSet with(int ordinal) {
      check(ordinal);
      if (this.contains(ordinal)) {
         return this;
      }
      long[] next = Arrays.copyOf(this.words, Math.max(this.words.length, (ordinal >>> 6) + 1));
      next[ordinal >>> 6] |= 1L << ordinal;
      return ofWords(next);
   }

   public ActionSet without(HeroAction action) {
      return this.without(action.ordinal());
   }

   /** On {@link #ALL} this first becomes the current roster as an explicit set. */
   public ActionSet without(int ordinal) {
      if (!this.contains(ordinal)) {
         return this;
      }
      ActionSet base = this.all ? range(0, HeroAction.values().length) : this;
      if (!base.contains(ordinal)) {
         return base;
      }
      long[] next = base.words.clone();
      next[ordinal >>> 6] &= ~(1L << ordinal);
      return ofWords(next);
   }

   public ActionSet union(ActionSet other) {
      if (this.all || other.all) {
         return ALL;
      }
      if (other.words.length == 0) {
         return this;
      }
      if (this.words.length == 0) {
         return other;
      }
      long[] next = Arrays.copyOf(this.words, Math.max(this.words.length, other.words.length));
      for (int i = 0; i < other.words.length; i++) {
         next[i] |= other.words[i];
      }
      return ofWords(next);
   }

   /** Compact binary form; see the class comment. */
   public byte[] toBytes() {
      if (this.all) {
         return new byte[]{TAG_ALL};
      }
      int byteCount = (this.span() + 7) >>> 3;
      ByteArrayOutputStream dense = new ByteArrayOutputStream(byteCount + 4);
      dense.write(TAG_DENSE);
      writeVarInt(dense, byteCount);
      for (int i = 0; i < byteCount; i++) {
         dense.write((int) (this.words[i >>> 3] >>> ((i & 7) << 3)) & 0xFF);
      }
      ByteArrayOutputStream sparse = new ByteArrayOutputStream();
      sparse.write(TAG_SPARSE);
      writeVarInt(sparse, this.size());
      int[] previous = {-1};
      this.forEachOrdinal(ordinal -> {
         writeVarInt(sparse, ordinal - previous[0] - 1);
         previous[0] = ordinal;
      });
      return (sparse.size() < dense.size() ? sparse : dense).toByteArray();
   }

   /** Reads {@link #toBytes()}; malformed, truncated, trailing or oversized input throws {@link NumberFormatException}. */
   public static ActionSet fromBytes(byte[] bytes) {
      if (bytes == null || bytes.length == 0) {
         throw new NumberFormatException("empty action set");
      }
      byte tag = bytes[0];
      int[] cursor = {1};
      if (tag == TAG_ALL) {
         expectEnd(bytes, cursor[0]);
         return ALL;
      }
      if (tag == TAG_DENSE) {
         int byteCount = readVarInt(bytes, cursor);
         if (byteCount > ORDINAL_LIMIT / 8 || cursor[0] + byteCount != bytes.length) {
            throw new NumberFormatException("bad dense action set length " + byteCount);
         }
         long[] words = new long[(byteCount + 7) >>> 3];
         for (int i = 0; i < byteCount; i++) {
            words[i >>> 3] |= (bytes[cursor[0] + i] & 0xFFL) << ((i & 7) << 3);
         }
         return ofWords(words);
      }
      if (tag == TAG_SPARSE) {
         int count = readVarInt(bytes, cursor);
         // Each gap takes at least one byte, so the count can never ask for more than the input holds.
         if (count > ORDINAL_LIMIT || count > bytes.length - cursor[0]) {
            throw new NumberFormatException("bad sparse action set count " + count);
         }
         int[] ordinals = new int[count];
         long ordinal = -1L;
         for (int i = 0; i < count; i++) {
            ordinal += (long) readVarInt(bytes, cursor) + 1L;
            if (ordinal >= ORDINAL_LIMIT) {
               throw new NumberFormatException("action ordinal past the limit");
            }
            ordinals[i] = (int) ordinal;
         }
         expectEnd(bytes, cursor[0]);
         return ofOrdinals(ordinals);
      }
      throw new NumberFormatException("unknown action set tag " + tag);
   }

   private static void expectEnd(byte[] bytes, int cursor) {
      if (cursor != bytes.length) {
         throw new NumberFormatException("trailing bytes after the action set");
      }
   }

   public void writeTo(FriendlyByteBuf buffer) {
      buffer.writeByteArray(this.toBytes());
   }

   public static ActionSet readFrom(FriendlyByteBuf buffer) {
      return fromBytes(buffer.readByteArray(8 + ORDINAL_LIMIT / 8));
   }

   /**
    * The snapshot token. Never contains {@code ';'}.
    * <ul>
    *   <li>{@link #ALL}: {@code "-1"} (the pre-wave-1 token, byte for byte);</li>
    *   <li>a set inside ordinals 0..63: the pre-wave-1 decimal mask (old snapshots re-encode unchanged);</li>
    *   <li>anything else, including the explicit full set 0..63 whose mask would read as {@code -1}:
    *   {@code "~" + base64url}.</li>
    * </ul>
    */
   public String toWire() {
      String token = this.wire;
      if (token == null) {
         if (this.all) {
            token = ALL_TOKEN;
         } else if (this.words.length == 0) {
            token = "0";
         } else if (this.words.length == 1 && this.words[0] != -1L) {
            token = Long.toString(this.words[0]);
         } else {
            token = SET_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(this.toBytes());
         }
         this.wire = token;
      }
      return token;
   }

   /** Reads {@link #toWire()} or a legacy decimal mask; bad input throws {@link NumberFormatException}. */
   public static ActionSet fromWire(String token) {
      if (token == null) {
         throw new NumberFormatException("null action set token");
      }
      if (ALL_TOKEN.equals(token)) {
         return ALL;
      }
      if (!token.isEmpty() && token.charAt(0) == SET_PREFIX) {
         byte[] bytes;
         try {
            bytes = Base64.getUrlDecoder().decode(token.substring(1));
         } catch (IllegalArgumentException exception) {
            throw new NumberFormatException("bad action set token");
         }
         return fromBytes(bytes);
      }
      return fromLegacyMask(Long.parseLong(token));
   }

   static void writeVarInt(ByteArrayOutputStream out, int value) {
      while ((value & ~0x7F) != 0) {
         out.write((value & 0x7F) | 0x80);
         value >>>= 7;
      }
      out.write(value);
   }

   static int readVarInt(byte[] bytes, int[] cursor) {
      int value = 0;
      for (int shift = 0; shift < 35; shift += 7) {
         if (cursor[0] >= bytes.length) {
            throw new NumberFormatException("truncated action set");
         }
         byte next = bytes[cursor[0]++];
         value |= (next & 0x7F) << shift;
         if ((next & 0x80) == 0) {
            if (value < 0) {
               throw new NumberFormatException("negative varint in action set");
            }
            return value;
         }
      }
      throw new NumberFormatException("varint too long in action set");
   }

   @Override
   public boolean equals(Object other) {
      if (this == other) {
         return true;
      }
      if (!(other instanceof ActionSet set)) {
         return false;
      }
      return this.all == set.all && Arrays.equals(this.words, set.words);
   }

   @Override
   public int hashCode() {
      return this.all ? -1 : Arrays.hashCode(this.words);
   }

   @Override
   public String toString() {
      if (this.all) {
         return "ActionSet[ALL]";
      }
      HeroAction[] roster = HeroAction.values();
      StringJoiner joiner = new StringJoiner(", ", "ActionSet[", "]");
      this.forEachOrdinal(ordinal -> joiner.add(ordinal < roster.length ? roster[ordinal].name() : "#" + ordinal));
      return joiner.toString();
   }
}
