package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WindReadingClientTest {
   @Test
   void onlyTheLocalReinhardSeesTheOutline() {
      assertEquals(0xD8F4FF, WindReadingClient.color(true, true));
      // Not Reinhard (e.g. Iron Man reading his own THREATS): pass to the next source.
      assertEquals(-1, WindReadingClient.color(false, true));
      assertEquals(-1, WindReadingClient.color(true, false));
   }
}
