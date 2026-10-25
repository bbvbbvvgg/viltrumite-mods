package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.reinhard.blessing.Parry;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.WindReading;
import net.minecraft.world.phys.Vec2;
import org.junit.jupiter.api.Test;

/** Blessing of Wind Reading (spec §4.5). */
class WindReadingTest {
   private static final Vec2 LOOK = Parry.lookOf(0.0F);
   private static final Vec2 FRONT = new Vec2(0.5F, 4.0F);
   private static final Vec2 BACK = new Vec2(-0.5F, -4.0F);

   @Test
   void invisibleAlwaysMarked() {
      assertTrue(WindReading.isMarked(true, false, false));
      assertTrue(WindReading.isMarked(true, WindReading.behind(LOOK, FRONT), false));
      assertTrue(WindReading.isMarked(true, WindReading.behind(LOOK, BACK), true));
   }

   @Test
   void behindAttackerMarked() {
      assertTrue(WindReading.behind(LOOK, BACK));
      assertTrue(WindReading.isMarked(false, WindReading.behind(LOOK, BACK), true));
      // Just behind the shoulder line still counts (dot < 0).
      assertTrue(WindReading.behind(LOOK, new Vec2(4.0F, -0.2F)));
   }

   @Test
   void behindPassiveNotMarked() {
      assertFalse(WindReading.isMarked(false, WindReading.behind(LOOK, BACK), false));
   }

   @Test
   void frontAttackerNotMarked() {
      assertFalse(WindReading.behind(LOOK, FRONT));
      assertFalse(WindReading.isMarked(false, WindReading.behind(LOOK, FRONT), true));
   }

   @Test
   void outsideRadiusIgnored() {
      assertTrue(WindReading.inRadius(31.9 * 31.9));
      assertTrue(WindReading.inRadius(32.0 * 32.0));
      assertFalse(WindReading.inRadius(32.1 * 32.1));
   }

   @Test
   void recentAttackLastsFiveSeconds() {
      assertTrue(WindReading.recentAttack(1000L, 1100L));
      assertFalse(WindReading.recentAttack(1000L, 1101L));
      assertFalse(WindReading.recentAttack(null, 1000L));
   }
}
