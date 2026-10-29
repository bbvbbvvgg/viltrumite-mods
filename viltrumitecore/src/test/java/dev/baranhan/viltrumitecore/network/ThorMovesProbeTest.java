package dev.baranhan.viltrumitecore.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.network.NetworkRegistry;
import org.junit.jupiter.api.Test;

class ThorMovesProbeTest {
   @Test
   void forgePingProbesAreRefusedQuietly() {
      assertTrue(CoreMessages.isProbe(NetworkRegistry.ABSENT.version()));
      assertTrue(CoreMessages.isProbe(NetworkRegistry.ACCEPTVANILLA));
      assertFalse(CoreMessages.isProbe("ebc00c6640e0905c"));
      assertFalse(CoreMessages.acceptsThorMoves("client", "ebc00c6640e0905c", NetworkRegistry.ABSENT.version()));
   }
}
