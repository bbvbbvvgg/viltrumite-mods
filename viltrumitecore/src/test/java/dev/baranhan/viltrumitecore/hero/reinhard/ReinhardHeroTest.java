package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.junit.jupiter.api.Test;

/** Reinhard skeleton: id, registration, chassis flags (spec §1.2.5, §1.2.7). */
class ReinhardHeroTest {

   @Test
   void reinhardKeepsItsOrdinal() {
      // Saved/wire ordinals: heroes added later (Thor) are appended after Reinhard.
      assertSame(HeroId.REINHARD, HeroId.values()[5]);
   }

   @Test
   void fromKeyReinhard() {
      assertSame(HeroId.REINHARD, HeroId.fromKey("reinhard"));
   }

   @Test
   void legacySetKeepsReinhard() {
      assertSame(HeroId.REINHARD, HeroId.legacySet(HeroId.REINHARD, true));
      assertSame(HeroId.REINHARD, HeroId.legacySet(HeroId.REINHARD, false));
   }

   @Test
   void noKitNoFlight() {
      ReinhardHero hero = ReinhardHero.INSTANCE;
      assertFalse(hero.allowsLegacyAbilities(null));
      assertFalse(hero.allowsFlight(null));
      assertFalse(hero.grantsFlightAbility(null));
   }

   @Test
   void registered() {
      HeroRegistry.registerDefaults();
      assertSame(ReinhardHero.INSTANCE, HeroRegistry.get(HeroId.REINHARD));
   }

   @Test
   void respawnReappliesKnockbackOnce() {
      AttributeInstance knockback = new AttributeInstance(new RangedAttribute("test.knockback_resistance", 0.0, 0.0, 1.0), instance -> {
      });
      ReinhardHero.applyKnockbackResistance(knockback);
      ReinhardHero.applyKnockbackResistance(knockback);
      assertEquals(1, knockback.getModifiers().size());
      assertEquals(ReinhardRules.KNOCKBACK_RESISTANCE, knockback.getValue(), 1.0E-9);
   }

   /** A fake body: 20 max health, a knockback attribute, every setHealth recorded. */
   private static final class Body implements ReinhardHero.ChassisBody {
      final AttributeInstance knockback = new AttributeInstance(new RangedAttribute("test.knockback_resistance", 0.0, 0.0, 1.0), instance -> {
      });
      float health;

      Body(float health) {
         this.health = health;
      }

      @Override
      public AttributeInstance knockback() {
         return knockback;
      }

      @Override
      public float maxHealth() {
         return ReinhardRules.MAX_HEALTH;
      }

      @Override
      public void setHealth(float health) {
         this.health = health;
      }
   }

   @Test
   void respawnRestoresTheChassisWithoutHealing() {
      // The End return keeps the wounded body's health: the exit portal must not heal.
      Body body = new Body(7.0F);
      ReinhardHero.respawn(body);
      assertEquals(7.0F, body.health);
      assertEquals(ReinhardRules.KNOCKBACK_RESISTANCE, body.knockback.getValue(), 1.0E-9);
   }

   @Test
   void becomingReinhardStartsFull() {
      Body body = new Body(7.0F);
      ReinhardHero.become(body);
      assertEquals(ReinhardRules.MAX_HEALTH, body.health);
      assertEquals(ReinhardRules.KNOCKBACK_RESISTANCE, body.knockback.getValue(), 1.0E-9);
   }

   @Test
   void cleanupRemovesChassis() {
      AttributeInstance knockback = new AttributeInstance(new RangedAttribute("test.knockback_resistance", 0.0, 0.0, 1.0), instance -> {
      });
      ReinhardHero.applyKnockbackResistance(knockback);
      ReinhardHero.removeKnockbackResistance(knockback);
      assertTrue(knockback.getModifiers().isEmpty());
      assertEquals(0.0, knockback.getValue(), 1.0E-9);
   }

   @Test
   void noFallDamage() {
      assertTrue(ReinhardHero.INSTANCE.cancelsFallDamage(null));
   }
}
