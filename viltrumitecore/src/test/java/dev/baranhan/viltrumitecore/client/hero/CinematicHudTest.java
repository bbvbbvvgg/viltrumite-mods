package dev.baranhan.viltrumitecore.client.hero;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class CinematicHudTest {
   @Test void hidesOnlyWhileASceneRunsAndComesBackTheNextFrame() {
      var running = new AtomicBoolean(false);
      CinematicHud.hideWhile(running::get, null);
      assertFalse(CinematicHud.hiding());
      running.set(true); assertTrue(CinematicHud.hiding());
      running.set(false); assertFalse(CinematicHud.hiding(), "no latch: the HUD returns as soon as the scene state ends");
      CinematicHud.hideWhile(() -> {throw new IllegalStateException("broken scene");}, null);
      assertFalse(CinematicHud.hiding(), "a broken scene never hides the HUD for good");
      assertTrue(CinematicHud.hides(true, true)); assertFalse(CinematicHud.hides(true, false)); assertFalse(CinematicHud.hides(false, true));
   }

   @Test void neverTouchesPlayerOptionsAndGuardsTheAbilityBar() throws Exception {
      var s = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/client/hero/CinematicHud.java"));
      assertFalse(s.contains("hideGui ="), "no option writes");
      assertFalse(s.contains("options."), "no option reads or writes");
      assertTrue(s.contains("EventPriority.HIGHEST") && s.contains("setCanceled(true)") && s.contains("RenderHandEvent"));
      assertTrue(s.contains("HeroSubtitles.render"), "the line's subtitles stay");
      var mixin = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/client/mixin/ViltrumiteInGameHudMixin.java"));
      assertTrue(mixin.contains("CinematicHud.hiding()"));
   }
}
