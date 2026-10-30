package dev.baranhan.viltrumitecore.client.render.scene;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SceneEntityHideTest {
   @Test void nullAndFailingRulesNeverHide() {
      assertFalse(SceneEntityHide.hidden(null));
      SceneEntityHide.register(e -> {throw new IllegalStateException("boom");});
      assertFalse(SceneEntityHide.hidden(null));
   }
}
