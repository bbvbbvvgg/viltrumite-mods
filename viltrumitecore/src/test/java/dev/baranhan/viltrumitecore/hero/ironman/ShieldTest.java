package dev.baranhan.viltrumitecore.hero.ironman;

import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.hero.ironman.combat.Reflect;
import dev.baranhan.viltrumitecore.hero.ironman.combat.Shield;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ShieldTest {
   private static final Vec3 LOOK = new Vec3(0, 0, 1);
   private static final Vec3 FRONT = new Vec3(0, 0, 3);
   private static final Vec3 BACK = new Vec3(0, 0, -3);

   @Test
   void frontHitAbsorbed() {
      Energy e = new Energy();
      Shield s = new Shield();
      s.raise(100L, e);
      assertSame(Shield.Block.ABSORBED, s.hit(120L, LOOK, FRONT, e));
   }

   @Test
   void backHitPasses() {
      Energy e = new Energy();
      Shield s = new Shield();
      s.raise(100L, e);
      assertSame(Shield.Block.PASS, s.hit(120L, LOOK, BACK, e));
      assertEquals(IronManRules.ENERGY_MAX, e.value(), 1.0E-4F, "a passed hit costs nothing");
   }

   @Test
   void costs4PerHit() {
      Energy e = new Energy();
      Shield s = new Shield();
      s.raise(0L, e);
      s.hit(50L, LOOK, FRONT, e);
      s.hit(60L, LOOK, FRONT, e);
      assertEquals(IronManRules.ENERGY_MAX - 8.0F, e.value(), 1.0E-4F);
   }

   @Test
   void noShieldWhenLocked() {
      Energy e = new Energy();
      e.drain(IronManRules.ENERGY_MAX);
      Shield s = new Shield();
      assertFalse(s.raise(0L, e));
      assertSame(Shield.Block.PASS, s.hit(1L, LOOK, FRONT, e));
   }

   @Test
   void perfectOnlyFirst5Ticks() {
      Energy e = new Energy();
      Shield s = new Shield();
      s.raise(1000L, e);
      assertSame(Shield.Block.PERFECT, s.hit(1005L, LOOK, FRONT, e));
      assertSame(Shield.Block.ABSORBED, s.hit(1006L, LOOK, FRONT, e));
      // Holding forever never counts as perfect.
      assertSame(Shield.Block.ABSORBED, s.hit(100000L, LOOK, FRONT, e));
   }

   @Test
   void repeatPressDoesNotReopenPerfectWindow() {
      Energy e = new Energy();
      Shield s = new Shield();
      s.raise(1000L, e);
      s.raise(1004L, e);
      s.raise(1008L, e);
      assertSame(Shield.Block.ABSORBED, s.hit(1009L, LOOK, FRONT, e), "a press while raised keeps the first raise time");
   }

   @Test
   void fSpamGivesNoPerfectBlock() {
      Energy e = new Energy();
      Shield s = new Shield();
      s.raise(1000L, e);
      s.lower(1002L);
      s.raise(1004L, e);
      assertSame(Shield.Block.ABSORBED, s.hit(1005L, LOOK, FRONT, e), "re-raise right after a lower is not perfect");
      s.lower(1006L);
      s.raise(1006L + Shield.PERFECT_REARM_TICKS, e);
      assertSame(Shield.Block.PERFECT, s.hit(1007L + Shield.PERFECT_REARM_TICKS, LOOK, FRONT, e), "a calm raise is perfect again");
   }

   @Test
   void loweredShieldPasses() {
      Energy e = new Energy();
      Shield s = new Shield();
      s.raise(0L, e);
      s.lower();
      assertSame(Shield.Block.PASS, s.hit(1L, LOOK, FRONT, e));
   }

   @Test
   void reflectReversesVelocity() {
      Vec3 out = Reflect.velocity(new Vec3(0, 0, -2), null, 1.0);
      assertEquals(2.0, out.z, 1.0E-9);
      assertEquals(0.0, out.x, 1.0E-9);
      Vec3 aimed = Reflect.velocity(new Vec3(0, 0, -2), new Vec3(3, 0, 4), 1.0);
      assertEquals(2.0, aimed.length(), 1.0E-6);
      assertEquals(0.6, aimed.normalize().x, 1.0E-6, "back toward the shooter");
   }
}
