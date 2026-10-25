package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroMobFear;
import dev.baranhan.viltrumitecore.hero.LegacyKit;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.food.FoodData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Overhaul items 1, 9, 16, 17: creepers, survival flight, hunger, knockback. */
class ReinhardSurvivalTest {
   @BeforeAll
   static void bootstrap() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   @Test
   void creepersOnlyFleeLikeFromCats() {
      assertTrue(ReinhardHero.INSTANCE.scaresMobType(EntityType.CREEPER));
      for (EntityType<?> other : new EntityType<?>[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.ENDERMAN,
         EntityType.COW, EntityType.VILLAGER, EntityType.IRON_GOLEM, EntityType.CAT, EntityType.WARDEN}) {
         assertFalse(ReinhardHero.INSTANCE.scaresMobType(other), "other mobs keep their AI: " + other);
      }
      // Creeper.registerGoals: the cat AvoidEntityGoal is priority 3 with speeds 1.0 / 1.2.
      assertEquals(3, HeroMobFear.PRIORITY);
      assertEquals(1.0, HeroMobFear.WALK_SPEED);
      assertEquals(1.2, HeroMobFear.SPRINT_SPEED);
      assertTrue(HeroMobFear.RANGE >= 6.0F && HeroMobFear.RANGE <= 10.0F);
      assertFalse(HeroMobFear.scares(null, null));
   }

   @Test
   void kitMayflyIsServerOnly() {
      // Root cause of the random survival flight: the client granted mayfly while its
      // freshly created LocalPlayer still carried the default (kit) hero id.
      assertFalse(LegacyKit.forcesMayfly(false, true, false), "the client never grants mayfly");
      assertTrue(LegacyKit.forcesMayfly(true, true, false), "the server grants the kit's flight");
      assertFalse(LegacyKit.forcesMayfly(true, true, true), "idempotent");
      assertFalse(LegacyKit.forcesMayfly(true, false, false), "never for heroes outside the kit");
      assertFalse(ReinhardHero.INSTANCE.allowsLegacyAbilities(null), "Reinhard is no kit user");
      assertFalse(ReinhardHero.INSTANCE.allowsFlight(null));
      assertFalse(ReinhardHero.INSTANCE.grantsFlightAbility(null));
   }

   @Test
   void hungerAndSaturationStayFull() {
      assertTrue(ReinhardHero.INSTANCE.preventsExhaustion(null));
      FoodData food = new FoodData();
      food.setFoodLevel(3);
      food.setSaturation(0.0F);
      food.addExhaustion(12.0F);
      ReinhardHero.keepFed(food);
      assertEquals(20, food.getFoodLevel());
      assertEquals(20.0F, food.getSaturationLevel());
      assertEquals(0.0F, food.getExhaustionLevel());
   }

   @Test
   void knockbackImmunityIsTotalButControlsStay() {
      assertTrue(ReinhardHero.INSTANCE.knockbackImmune(null));
      assertFalse(ReinhardHero.INSTANCE.allowsExternalControl(null, ControlKind.IMPULSE), "no hero push, knock or throw");
      assertTrue(ReinhardHero.INSTANCE.allowsExternalControl(null, ControlKind.PULL));
      assertTrue(ReinhardHero.INSTANCE.allowsExternalControl(null, ControlKind.FREEZE));
      assertTrue(ReinhardHero.INSTANCE.allowsExternalControl(null, ControlKind.STASIS));
      assertTrue(ReinhardHero.INSTANCE.allowsExternalControl(null, ControlKind.VILTRUMITE_GRAB));
      assertEquals(1.0, ReinhardRules.KNOCKBACK_RESISTANCE);
   }
}
