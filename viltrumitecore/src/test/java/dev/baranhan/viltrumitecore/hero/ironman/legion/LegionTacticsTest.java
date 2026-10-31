package dev.baranhan.viltrumitecore.hero.ironman.legion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionTactics.Assignment;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionTactics.Stance;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionTactics.Suit;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionTactics.Target;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class LegionTacticsTest {
   private static Suit suit(int slot, MarkId mark, Vec3 pos, float fraction) {
      return new Suit(slot, LegionProfile.of(mark), pos, fraction, true);
   }

   @Test
   void nearestSuitInterruptsToDefendTony() {
      List<Suit> suits = List.of(suit(0, MarkId.MARK_7, new Vec3(20, 0, 0), 1.0F), suit(1, MarkId.MARK_42, new Vec3(2, 0, 0), 1.0F),
         suit(2, MarkId.MARK_39, new Vec3(-15, 0, 0), 1.0F));
      Target onTony = new Target(7, 50.0, 20.0F, new Vec3(1, 0, 1), 1.5, true, false);
      Target far = new Target(8, 80.0, 20.0F, new Vec3(25, 0, 0), 25.0, false, false);
      List<Assignment> plan = LegionTactics.assign(suits, List.of(onTony, far));
      Assignment mark42 = plan.get(1);
      assertTrue(mark42.interrupt());
      assertEquals(7, mark42.targetId());
      assertFalse(plan.get(0).interrupt());
   }

   @Test
   void noOverkillPileOnOneWeakTarget() {
      List<Suit> suits = List.of(suit(0, MarkId.MARK_7, Vec3.ZERO, 1.0F), suit(1, MarkId.MARK_42, Vec3.ZERO, 1.0F),
         suit(2, MarkId.WAR_MACHINE_MK2, Vec3.ZERO, 1.0F));
      Target weak = new Target(1, 100.0, 4.0F, new Vec3(5, 0, 0), 5.0, false, false);
      Target strong = new Target(2, 60.0, 80.0F, new Vec3(6, 0, 0), 6.0, false, false);
      List<Assignment> plan = LegionTactics.assign(suits, List.of(weak, strong));
      long onWeak = plan.stream().filter(a -> a.targetId() == 1).count();
      long onStrong = plan.stream().filter(a -> a.targetId() == 2).count();
      assertEquals(1, onWeak);
      assertEquals(2, onStrong);
   }

   @Test
   void sameTargetGetsDifferentRoles() {
      List<Suit> suits = List.of(suit(0, MarkId.MARK_7, Vec3.ZERO, 1.0F), suit(1, MarkId.MARK_42, Vec3.ZERO, 1.0F), suit(2, MarkId.MARK_15, Vec3.ZERO, 1.0F));
      Target boss = new Target(3, 90.0, 300.0F, new Vec3(8, 0, 0), 8.0, false, false);
      List<Assignment> plan = LegionTactics.assign(suits, List.of(boss));
      assertEquals(3, plan.stream().filter(a -> a.targetId() == 3).count());
      assertNotEquals(plan.get(1).role(), plan.get(2).role());
      assertEquals(3, plan.stream().map(Assignment::role).distinct().count());
   }

   @Test
   void damagedSuitsRetreatOrReturn() {
      List<Suit> suits = List.of(suit(0, MarkId.MARK_7, Vec3.ZERO, 0.2F), suit(1, MarkId.MARK_42, Vec3.ZERO, 0.05F));
      Target boss = new Target(3, 90.0, 300.0F, new Vec3(8, 0, 0), 8.0, false, false);
      List<Assignment> plan = LegionTactics.assign(suits, List.of(boss));
      assertEquals(Stance.RETREAT, plan.get(0).stance());
      assertEquals(3, plan.get(0).targetId());
      assertEquals(Stance.RETURN, plan.get(1).stance());
      assertFalse(plan.get(1).hasTarget());
   }

   @Test
   void noTargetsMeansGuard() {
      List<Assignment> plan = LegionTactics.assign(List.of(suit(0, MarkId.MARK_17, Vec3.ZERO, 1.0F)), List.of());
      assertEquals(Stance.GUARD, plan.get(0).stance());
      assertFalse(plan.get(0).hasTarget());
   }

   @Test
   void guardianStaysCloseToTony() {
      List<Suit> suits = List.of(suit(0, MarkId.IRON_HEART_MK3, Vec3.ZERO, 1.0F));
      Target far = new Target(1, 90.0, 40.0F, new Vec3(20, 0, 0), 20.0, false, false);
      assertFalse(LegionTactics.assign(suits, List.of(far)).get(0).hasTarget());
      Target near = new Target(2, 50.0, 40.0F, new Vec3(3, 0, 0), 3.0, false, false);
      assertEquals(2, LegionTactics.assign(suits, List.of(far, near)).get(0).targetId());
   }

   @Test
   void heavyWeaponsAvoidOverkillAndTony() {
      assertFalse(LegionTactics.heavyAllowed(LegionWeapon.PULSE_UNIBEAM, 3.0F, LegionRules.REPULSOR_DAMAGE, 10.0));
      assertFalse(LegionTactics.heavyAllowed(LegionWeapon.SLAM, 100.0F, LegionRules.REPULSOR_DAMAGE, 2.0));
      assertTrue(LegionTactics.heavyAllowed(LegionWeapon.PULSE_UNIBEAM, 100.0F, LegionRules.REPULSOR_DAMAGE, 10.0));
      assertTrue(LegionTactics.heavyAllowed(LegionWeapon.MICRO_LASER, 1.0F, LegionRules.REPULSOR_DAMAGE, 0.5));
      assertFalse(LegionTactics.missilesAllowed(100.0F, LegionRules.REPULSOR_DAMAGE, 1.0));
      assertTrue(LegionTactics.missilesAllowed(100.0F, LegionRules.REPULSOR_DAMAGE, 12.0));
   }

   @Test
   void unavailableSuitsGetNoOrders() {
      List<Suit> suits = List.of(new Suit(0, LegionProfile.of(MarkId.MARK_7), Vec3.ZERO, 1.0F, false));
      assertTrue(LegionTactics.assign(suits, List.of()).isEmpty());
   }

   @Test
   void everyMarkHasAProfile() {
      for (MarkId id : MarkId.values()) {
         LegionProfile profile = LegionProfile.of(id);
         assertEquals(id, profile.mark());
         assertTrue(profile.cruise() > 0.4 && profile.cruise() < 2.0, id.name());
         assertTrue(profile.dps() > 0.0F);
      }

      assertTrue(LegionProfile.of(MarkId.MARK_15).silent());
      assertTrue(LegionProfile.of(MarkId.MARK_39).cruise() > LegionProfile.of(MarkId.WAR_MACHINE_MK2).cruise());
   }
}
