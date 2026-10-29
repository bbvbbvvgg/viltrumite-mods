package dev.baranhan.viltrumitecore.hero.thor.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Foundation M2: the move table both sides must share has a deterministic fingerprint checked at login. */
class ThorMoveFingerprintTest {
   @AfterEach
   void reset() {
      ThorMoves.resetForTests();
   }

   @Test
   void stableAndHexOfFixedLength() {
      String first = ThorMoves.fingerprint();
      assertEquals(first, ThorMoves.fingerprint());
      assertTrue(first.matches("[0-9a-f]{16}"), first);
   }

   @Test
   void tableListsEveryMoveInWireOrder() {
      String table = ThorMoves.table();
      int at = -1;
      for (ThorMove move : ThorMove.values()) {
         int next = table.indexOf("|" + move.ordinal() + ":" + move.name() + ":");
         assertTrue(next > at, move + " out of wire order");
         at = next;
      }
   }

   @Test
   void anyAuthoringDifferenceChangesTheFingerprint() {
      String bare = ThorMoves.fingerprint();
      ThorMoves.register("B", ThorMove.WRATH, new ThorMoveDefinition(24, 8, 20, null, 1), ThorMoveHandler.NONE);
      String withWrath = ThorMoves.fingerprint();
      assertNotEquals(bare, withWrath, "a client without the move must not pass");

      ThorMoves.resetForTests();
      ThorMoves.register("B", ThorMove.WRATH, new ThorMoveDefinition(24, 9, 20, null, 1), ThorMoveHandler.NONE);
      assertNotEquals(withWrath, ThorMoves.fingerprint(), "a different timing must not pass");

      ThorMoves.resetForTests();
      ThorMoves.register("B", ThorMove.WRATH, new ThorMoveDefinition(24, 8, 20, MovePhase.FINISH, 1), ThorMoveHandler.NONE);
      assertNotEquals(withWrath, ThorMoves.fingerprint(), "a different cancel window must not pass");
   }

   @Test
   void registrationOrderDoesNotMatter() {
      ThorMoves.register("B", ThorMove.WRATH, new ThorMoveDefinition(24, 8, 20, null, 1), ThorMoveHandler.NONE);
      ThorMoves.register("A", ThorMove.RECALL, new ThorMoveDefinition(1, 1, 1, null, 1), ThorMoveHandler.NONE);
      String one = ThorMoves.fingerprint();
      ThorMoves.resetForTests();
      ThorMoves.register("A", ThorMove.RECALL, new ThorMoveDefinition(1, 1, 1, null, 1), ThorMoveHandler.NONE);
      ThorMoves.register("B", ThorMove.WRATH, new ThorMoveDefinition(24, 8, 20, null, 1), ThorMoveHandler.NONE);
      assertEquals(one, ThorMoves.fingerprint());
   }

   @Test
   void absentOrForeignRemoteIsRefused() {
      String local = ThorMoves.fingerprint();
      assertTrue(ThorMoves.compatible(local, local));
      assertFalse(ThorMoves.compatible(local, null), "a client without the channel");
      assertFalse(ThorMoves.compatible(local, "ABSENT \ud83e\udd14"));
   }
}
