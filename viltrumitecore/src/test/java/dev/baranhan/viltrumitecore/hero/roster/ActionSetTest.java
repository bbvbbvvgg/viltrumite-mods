package dev.baranhan.viltrumitecore.hero.roster;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import io.netty.buffer.Unpooled;
import java.util.Random;
import java.util.stream.IntStream;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** Wave 1 · session 01: the action set must scale to a roster of 256+ actions with no aliasing. */
class ActionSetTest {
   private static final int SYNTHETIC_ROSTER = 256;

   @Test
   void syntheticRosterOf256ActionsMasksEveryOrdinalExactly() {
      for (int ordinal = 0; ordinal < SYNTHETIC_ROSTER; ordinal++) {
         ActionSet one = ActionSet.ofOrdinals(ordinal);
         assertEquals(1, one.size());
         for (int other = 0; other < SYNTHETIC_ROSTER; other++) {
            assertEquals(other == ordinal, one.contains(other), ordinal + " vs " + other);
         }
         assertEquals(one, ActionSet.fromWire(one.toWire()));
         assertEquals(one, ActionSet.fromBytes(one.toBytes()));
      }
   }

   @Test
   void boundaryOrdinalsDoNotAlias() {
      int[] edges = {0, 1, 62, 63, 64, 65, 127, 128, 129, 191, 192, 255, 256, 1023, ActionSet.ORDINAL_LIMIT - 1};
      ActionSet set = ActionSet.ofOrdinals(edges);
      assertEquals(edges.length, set.size());
      for (int edge : edges) {
         assertTrue(set.contains(edge), "contains " + edge);
      }
      assertFalse(ActionSet.ofOrdinals(64).contains(0), "bit 64 must not read bit 0");
      assertFalse(ActionSet.ofOrdinals(0).contains(64), "bit 0 must not read bit 64");
      assertFalse(ActionSet.ofOrdinals(128).contains(0));
      assertFalse(set.contains(2));
      assertFalse(set.contains(-1));
      assertFalse(set.contains(Integer.MAX_VALUE));
      assertArrayEquals(edges, set.ordinals().toArray());
      assertEquals(set, ActionSet.fromWire(set.toWire()));
      assertEquals(set, ActionSet.fromBytes(set.toBytes()));
   }

   @Test
   void randomSetsRoundTripThroughBytesWireAndBuffer() {
      Random random = new Random(0x012026L);
      for (int run = 0; run < 500; run++) {
         int span = 1 + random.nextInt(run % 3 == 0 ? 4096 : SYNTHETIC_ROSTER);
         double density = random.nextDouble();
         int[] ordinals = IntStream.range(0, span).filter(i -> random.nextDouble() < density).toArray();
         ActionSet set = ActionSet.ofOrdinals(ordinals);
         assertArrayEquals(ordinals, set.ordinals().toArray());
         assertEquals(set, ActionSet.fromBytes(set.toBytes()));
         assertEquals(set, ActionSet.fromWire(set.toWire()));
         assertEquals(set.hashCode(), ActionSet.fromWire(set.toWire()).hashCode());
         FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
         set.writeTo(buffer);
         assertEquals(set, ActionSet.readFrom(buffer));
         assertEquals(0, buffer.readableBytes());
      }
   }

   @Test
   void picksTheShorterLayout() {
      // A few high ordinals: sparse gaps beat a 32-byte bit set.
      byte[] sparse = ActionSet.ofOrdinals(3, 250).toBytes();
      assertEquals(ActionSet.TAG_SPARSE, sparse[0]);
      assertTrue(sparse.length <= 5, "sparse length " + sparse.length);
      // A dense block: the bit set wins.
      byte[] dense = ActionSet.range(0, 200).toBytes();
      assertEquals(ActionSet.TAG_DENSE, dense[0]);
      assertEquals(2 + 25, dense.length);
      // The whole current roster as an explicit set is still a short snapshot token.
      String token = ActionSet.range(0, HeroAction.values().length).toWire();
      assertTrue(token.length() <= 32, token);
      assertFalse(token.contains(";"), "the token must not split the packed snapshot");
   }

   @Test
   void allAndNone() {
      assertTrue(ActionSet.ALL.isAll());
      assertTrue(ActionSet.ALL.contains(0));
      assertTrue(ActionSet.ALL.contains(100_000), "ALL also covers actions appended later");
      assertFalse(ActionSet.ALL.contains(-1));
      assertTrue(ActionSet.NONE.isEmpty());
      assertFalse(ActionSet.NONE.contains(0));
      assertEquals("-1", ActionSet.ALL.toWire());
      assertSame(ActionSet.ALL, ActionSet.fromWire("-1"));
      assertSame(ActionSet.ALL, ActionSet.fromBytes(ActionSet.ALL.toBytes()));
      assertEquals(ActionSet.NONE, ActionSet.fromWire(ActionSet.NONE.toWire()));
      assertEquals(ActionSet.NONE, ActionSet.ofOrdinals());
      assertEquals(ActionSet.NONE, ActionSet.of());
      assertEquals(HeroAction.values().length, ActionSet.ALL.size());
      assertNotEquals(ActionSet.ALL, ActionSet.range(0, HeroAction.values().length), "an explicit roster is not 'everything'");
   }

   @Test
   void legacyLongMaskBridge() {
      assertSame(ActionSet.ALL, ActionSet.fromLegacyMask(-1L));
      assertEquals(ActionSet.ofOrdinals(0, 2), ActionSet.fromWire("5"));
      assertEquals(ActionSet.NONE, ActionSet.fromWire("0"));
      assertEquals(ActionSet.ofOrdinals(63), ActionSet.fromLegacyMask(Long.MIN_VALUE));
      assertEquals(5L, ActionSet.fromWire("5").legacyMask());
      assertEquals(-1L, ActionSet.ALL.legacyMask());
      assertEquals(0L, ActionSet.NONE.legacyMask());
      assertEquals(1L, ActionSet.ofOrdinals(0, 64, 200).legacyMask(), "the long view keeps bits 0..63 only");
      for (int bit = 0; bit < 64; bit++) {
         long mask = 1L << bit;
         assertEquals(mask, ActionSet.fromLegacyMask(mask).legacyMask());
      }
   }

   @Test
   void derivedSets() {
      ActionSet set = ActionSet.ofOrdinals(1, 70);
      assertEquals(ActionSet.ofOrdinals(1, 70, 200), set.with(200));
      assertSame(set, set.with(70));
      assertEquals(ActionSet.ofOrdinals(1), set.without(70));
      assertEquals(ActionSet.NONE, set.without(70).without(1), "trailing words are trimmed so equality holds");
      assertEquals(ActionSet.ofOrdinals(1, 5, 70, 300), set.union(ActionSet.ofOrdinals(5, 300)));
      assertSame(ActionSet.ALL, set.union(ActionSet.ALL));
      HeroAction last = HeroAction.values()[HeroAction.values().length - 1];
      ActionSet allButLast = ActionSet.ALL.without(last);
      assertFalse(allButLast.contains(last));
      assertEquals(HeroAction.values().length - 1, allButLast.size());
      assertTrue(ActionSet.of(HeroAction.LIONS_HEART, last).contains(last));
      assertEquals(last.ordinal() + 1, ActionSet.of(last).span());
   }

   @Test
   void rejectsMalformedAndOversizedInput() {
      assertThrows(IllegalArgumentException.class, () -> ActionSet.ofOrdinals(-1));
      assertThrows(IllegalArgumentException.class, () -> ActionSet.ofOrdinals(ActionSet.ORDINAL_LIMIT));
      assertThrows(NumberFormatException.class, () -> ActionSet.fromWire(""));
      assertThrows(NumberFormatException.class, () -> ActionSet.fromWire("~"));
      assertThrows(NumberFormatException.class, () -> ActionSet.fromWire("~!!"));
      assertThrows(NumberFormatException.class, () -> ActionSet.fromWire("abc"));
      assertThrows(NumberFormatException.class, () -> ActionSet.fromBytes(new byte[]{9}));
      assertThrows(NumberFormatException.class, () -> ActionSet.fromBytes(new byte[]{ActionSet.TAG_ALL, 0}));
      // Dense: declared length past the limit or past the data.
      assertThrows(NumberFormatException.class, () -> ActionSet.fromBytes(new byte[]{ActionSet.TAG_DENSE, (byte) 0xFF, (byte) 0xFF, 0x7F}));
      assertThrows(NumberFormatException.class, () -> ActionSet.fromBytes(new byte[]{ActionSet.TAG_DENSE, 4, 1}));
      // Sparse: huge count, truncated gaps, ordinal past the limit, an endless varint.
      assertThrows(NumberFormatException.class, () -> ActionSet.fromBytes(new byte[]{ActionSet.TAG_SPARSE, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x07}));
      assertThrows(NumberFormatException.class, () -> ActionSet.fromBytes(new byte[]{ActionSet.TAG_SPARSE, 3, 1}));
      assertThrows(NumberFormatException.class, () -> ActionSet.fromBytes(new byte[]{ActionSet.TAG_SPARSE, 1, (byte) 0x80, (byte) 0x80, 0x02}));
      assertThrows(NumberFormatException.class, () -> ActionSet.fromBytes(new byte[]{ActionSet.TAG_SPARSE, 1, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01}));
   }

   @Test
   void toStringNamesKnownActions() {
      assertEquals("ActionSet[ALL]", ActionSet.ALL.toString());
      assertEquals("ActionSet[]", ActionSet.NONE.toString());
      assertEquals("ActionSet[LIONS_HEART, #1000]", ActionSet.ofOrdinals(HeroAction.LIONS_HEART.ordinal(), 1000).toString());
   }
}
