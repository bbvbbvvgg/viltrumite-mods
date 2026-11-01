package dev.baranhan.viltrumitecore.worldevent.ultron;

import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.worldevent.ultron.UltronBossRules.Stage;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronBossRules.Tethers;
import org.junit.jupiter.api.Test;

class UltronBossRulesTest {
   @Test
   void healthScalesWithTheGroupAndIsCapped() {
      assertEquals(UltronBossRules.BASE_HEALTH, UltronBossRules.maxHealth(1));
      assertEquals(UltronBossRules.BASE_HEALTH, UltronBossRules.maxHealth(0));
      assertEquals(UltronBossRules.BASE_HEALTH * 1.5F, UltronBossRules.maxHealth(2), 1.0E-3);
      assertEquals(UltronBossRules.maxHealth(6), UltronBossRules.maxHealth(30));
      float max = UltronBossRules.maxHealth(1);
      assertEquals(Math.max(UltronBossRules.MIN_HIT_CAP, max * UltronBossRules.HIT_CAP), UltronBossRules.hitCap(max));
      assertTrue(UltronBossRules.secondCap(max) >= UltronBossRules.hitCap(max));
      // even at the per-second cap the fight lasts at least 20 seconds
      assertTrue(max / UltronBossRules.secondCap(max) >= 19.9F);
   }

   @Test
   void stagesOnlyMoveForward() {
      assertEquals(Stage.TACTICAL, UltronBossRules.stageFor(1.0F, Stage.TACTICAL));
      assertEquals(Stage.AERIAL, UltronBossRules.stageFor(UltronBossRules.AERIAL_AT, Stage.TACTICAL));
      assertEquals(Stage.OVERDRIVE, UltronBossRules.stageFor(0.1F, Stage.TACTICAL));
      assertEquals(Stage.OVERDRIVE, UltronBossRules.stageFor(0.9F, Stage.OVERDRIVE), "healing never undoes a stage");
      assertEquals(Stage.AERIAL, UltronBossRules.stageFor(0.5F, Stage.AERIAL));
   }

   @Test
   void eachStandingNodeMakesHimHarder() {
      Tethers none = UltronBossRules.tethers(0, 0, 0, false, 0, 0, 0, false);
      assertEquals(Tethers.NONE, none);
      Tethers full = UltronBossRules.tethers(3, 3, 3, true, 2, 2, 5, true);
      assertEquals(UltronBossRules.MAX_REDUCTION, full.reduction(), 1.0E-5);
      assertEquals(UltronBossRules.MAX_REGEN, full.regenPerSecond(), 1.0E-6);
      assertTrue(full.cooldownFactor() < 1.0F && full.cooldownFactor() >= UltronBossRules.MIN_COOLDOWN_FACTOR);
      assertEquals(1.0F + UltronBossRules.CORE_DAMAGE, full.damageFactor(), 1.0E-5);
      assertTrue(full.summons() && full.sensors());
      Tethers one = UltronBossRules.tethers(1, 0, 0, false, 0, 0, 0, false);
      assertEquals(UltronBossRules.BULWARK_REDUCTION, one.reduction(), 1.0E-5);
      assertEquals(100.0F * (1.0F - UltronBossRules.BULWARK_REDUCTION), UltronBossRules.incoming(100.0F, one, false), 1.0E-3);
      assertEquals(100.0F * (1.0F - UltronBossRules.TRANSITION_REDUCTION), UltronBossRules.incoming(100.0F, none, true), 1.0E-3);
      // learned protocols only harden the ascendant
      assertEquals(0.0F, UltronBossRules.tethers(0, 0, 0, false, 0, 0, 3, false).reduction());
      assertEquals(UltronBossRules.MAX_LEARNED, UltronBossRules.tethers(0, 0, 0, false, 0, 0, 9, true).reduction(), 1.0E-5);
   }

   @Test
   void damageScalesMildly() {
      assertEquals(1.0F, UltronBossRules.damageScale(1));
      assertEquals(1.25F, UltronBossRules.damageScale(6), 1.0E-5);
      assertEquals(1.25F, UltronBossRules.damageScale(60), 1.0E-5);
   }
}
