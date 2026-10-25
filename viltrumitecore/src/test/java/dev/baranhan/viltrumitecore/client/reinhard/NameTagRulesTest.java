package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NameTagRulesTest {
   @Test
   void sneakDims() {
      // Vanilla EntityRenderer.renderNameTag: the first pass is 0x20FFFFFF (553648127), see-through unless sneaking;
      // the solid white pass is drawn only when not sneaking.
      assertEquals(553648127, NameTagRules.FAINT_TEXT);
      assertEquals(0x20, NameTagRules.FAINT_TEXT >>> 24);
      assertFalse(NameTagRules.seeThrough(true));
      assertFalse(NameTagRules.solidPass(true));
      assertTrue(NameTagRules.seeThrough(false));
      assertTrue(NameTagRules.solidPass(false));
   }

   @Test
   void eventResultDecides() {
      // Forge EntityRenderer.render: DENY hides, ALLOW forces, DEFAULT asks shouldShowName.
      assertFalse(NameTagRules.show(true, false, true));
      assertTrue(NameTagRules.show(false, true, false));
      assertTrue(NameTagRules.show(false, false, true));
      assertFalse(NameTagRules.show(false, false, false));
   }
}
