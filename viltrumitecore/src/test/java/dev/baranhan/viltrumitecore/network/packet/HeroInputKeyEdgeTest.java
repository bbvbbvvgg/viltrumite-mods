package dev.baranhan.viltrumitecore.network.packet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import dev.baranhan.viltrumitecore.hero.thor.ThorHero;
import dev.baranhan.viltrumitecore.hero.thor.ThorInput;
import org.junit.jupiter.api.Test;

/** Foundation m5: Thor's mouse inputs never enter through the key packet (only HeldInputs). */
class HeroInputKeyEdgeTest {
   @Test
   void thorMouseActionsAreDroppedFromTheKeyPacketBothEdges() {
      for (MouseButton button : new MouseButton[]{MouseButton.PRIMARY, MouseButton.SECONDARY, MouseButton.MIDDLE}) {
         HeroAction action = ThorInput.of(button).action();
         assertFalse(HeroInputC2SPacket.acceptsKeyEdge(ThorHero.INSTANCE, action), button + " must come through HeldInputs");
      }
   }

   @Test
   void thorSlotKeysStillUseTheKeyPacket() {
      for (ThorInput input : new ThorInput[]{ThorInput.POWER, ThorInput.MOBILITY, ThorInput.ULTIMATE, ThorInput.TELEPORT}) {
         assertTrue(HeroInputC2SPacket.acceptsKeyEdge(ThorHero.INSTANCE, input.action()), input.name());
      }
   }

   @Test
   void everyMouseButtonThorClaimsIsMouseOnly() {
      for (MouseButton button : MouseButton.values()) {
         ThorInput input = ThorInput.of(button);
         if (input != null) {
            assertTrue(ThorInput.mouseOnly(input.action()), button.name());
         }
      }

      assertFalse(ThorInput.mouseOnly(null));
   }
}
