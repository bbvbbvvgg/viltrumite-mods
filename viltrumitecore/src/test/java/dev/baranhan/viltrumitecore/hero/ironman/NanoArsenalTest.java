package dev.baranhan.viltrumitecore.hero.ironman;

import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.hero.ironman.combat.HammerLaunch;
import dev.baranhan.viltrumitecore.hero.ironman.combat.NanoArsenal;
import dev.baranhan.viltrumitecore.hero.ironman.combat.RightTool;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class NanoArsenalTest {
   private static void formed(NanoArsenal a) {
      for (int i = 0; i < IronManRules.NANO_FORM_TICKS; i++) {
         a.tick();
      }
   }

   @Test
   void formCosts8() {
      Energy e = new Energy();
      NanoArsenal a = new NanoArsenal();
      assertSame(RightTool.NANO_BLADE, a.press(e));
      assertEquals(IronManRules.ENERGY_MAX - 8.0F, e.value(), 1.0E-4F);
      assertTrue(a.forming());
      assertNull(a.press(e), "no switch mid-wave");
   }

   @Test
   void switchBladeHammer() {
      Energy e = new Energy();
      NanoArsenal a = new NanoArsenal();
      a.press(e);
      formed(a);
      assertSame(RightTool.NANO_HAMMER, a.press(e));
      formed(a);
      assertSame(RightTool.NANO_BLADE, a.press(e));
      assertEquals(IronManRules.ENERGY_MAX - 24.0F, e.value(), 1.0E-4F);
   }

   @Test
   void mmbDissolves() {
      Energy e = new Energy();
      NanoArsenal a = new NanoArsenal();
      a.press(e);
      formed(a);
      assertSame(RightTool.REPULSOR, dev.baranhan.viltrumitecore.hero.ironman.combat.ToolCycle.next(RightTool.NANO_BLADE, a.weapon(), null));
      assertTrue(a.dissolve());
      assertNull(a.weapon());
      assertEquals(IronManRules.NANO_FORM_TICKS, a.dissolveTicks());
      assertFalse(a.dissolve());
   }

   @Test
   void lockedEnergyFormsNothing() {
      Energy e = new Energy();
      e.drain(IronManRules.ENERGY_MAX);
      assertNull(new NanoArsenal().press(e));
   }

   @Test
   void bladeSunders100() {
      assertEquals(100, IronManRules.BLADE_SUNDER_TICKS);
   }

   @Test
   void airborneHitSlamsDown() {
      assertTrue(HammerLaunch.slamsDown(false, 3.0));
      assertFalse(HammerLaunch.slamsDown(true, 0.0));
      assertFalse(HammerLaunch.slamsDown(false, 0.5), "a hop is not airborne");
   }

   @Test
   void launchBreaksOnlySoftBlocks() {
      assertTrue(HammerLaunch.soft(false, 0.5F));
      assertTrue(HammerLaunch.soft(false, 1.5F));
      assertFalse(HammerLaunch.soft(false, 3.0F), "stone-like");
      assertFalse(HammerLaunch.soft(false, -1.0F), "bedrock");
      assertFalse(HammerLaunch.soft(true, 0.0F), "air");
   }

   @Test
   void launchLasts20AndSpeedGrows() {
      HammerLaunch h = new HammerLaunch();
      h.launch(5);
      for (int i = 0; i < IronManRules.HAMMER_LAUNCH_TICKS - 1; i++) {
         h.tick();
      }
      assertTrue(h.isFlying(5));
      h.tick();
      assertFalse(h.isFlying(5));
      assertTrue(HammerLaunch.speed(20) > HammerLaunch.speed(0));
   }
}
