package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroChassis;
import dev.baranhan.viltrumitecore.hero.LegacyKit;
import org.junit.jupiter.api.Test;

class LethalChopTest {
   @Test
   void damage35() {
      assertEquals(35.0F, ReinhardRules.CHOP_BASE_DAMAGE * 1.75F, 1.0E-4F);
      HeroChassis chassis = ReinhardHero.INSTANCE.chassisStats(null);
      assertEquals(ReinhardRules.CHOP_BASE_DAMAGE, chassis.baseDamage());
      assertEquals(0.0F, chassis.damageReduction());
      assertEquals(0.0F, chassis.damageIgnoreThreshold());
   }

   @Test
   void onlyChopFromKit() {
      assertTrue(ReinhardHero.allowsKitAbility(LegacyKit.CHOP, 0));
      for (String id : new String[]{LegacyKit.PUNCH, LegacyKit.DASH, LegacyKit.THUNDERCLAP, LegacyKit.GRAB, LegacyKit.BLOCK, LegacyKit.BARRAGE,
         LegacyKit.SPEED, LegacyKit.LOCK}) {
         assertFalse(ReinhardHero.allowsKitAbility(id, 0), id);
      }
      assertFalse(ReinhardHero.allowsKitAbility(LegacyKit.CHOP, 1 << ReinhardFlags.REID_DRAWING), "not while Reid is being drawn");
      assertFalse(ReinhardHero.allowsKitAbility(LegacyKit.CHOP, 1 << ReinhardFlags.REID_SHEATHING));
      assertTrue(ReinhardHero.allowsKitAbility(LegacyKit.CHOP, 1 << ReinhardFlags.REID_DRAWN));
   }

   @Test
   void noViltrumiteChassis() {
      assertFalse(ReinhardHero.INSTANCE.allowsLegacyAbilities(null));
   }

   @Test
   void chopIconIsLethalChop() {
      assertTrue(ReinhardAbilities.icon(LegacyKit.CHOP).getPath().endsWith("/lethal_chop.png"));
   }

   @Test
   void chopClipFollowsSide() {
      assertEquals(dev.baranhan.viltrumitecore.client.reinhard.ReinhardClips.CHOP_LEFT,
         dev.baranhan.viltrumitecore.client.reinhard.ReinhardPoses.chopClip(20, true, 0.0F).animation());
      assertEquals(0.5, dev.baranhan.viltrumitecore.client.reinhard.ReinhardPoses.chopClip(10, false, 0.0F).seconds(), 1.0E-9);
      assertEquals(null, dev.baranhan.viltrumitecore.client.reinhard.ReinhardPoses.chopClip(0, false, 0.0F));
   }
}
