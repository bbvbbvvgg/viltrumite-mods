package dev.baranhan.viltrumitecore.hero.ironman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class EnergyTest {
   private static final float EPS = 1.0E-4F;

   @Test
   void startsFull() {
      Energy energy = new Energy();
      assertEquals(IronManRules.ENERGY_MAX, energy.value(), EPS);
      assertFalse(energy.empty());
      assertFalse(energy.weaponsLocked());
   }

   @Test
   void spendRefusesWhenShort() {
      Energy energy = new Energy();
      assertTrue(energy.spend(95.0F));
      assertFalse(energy.spend(6.0F));
      assertEquals(5.0F, energy.value(), EPS);
      assertTrue(energy.spend(5.0F));
      assertEquals(0.0F, energy.value(), EPS);
   }

   @Test
   void drainClampsAtZero() {
      Energy energy = new Energy();
      energy.drain(150.0F);
      assertEquals(0.0F, energy.value(), EPS);
      assertTrue(energy.empty());
   }

   @Test
   void regenWaits20Ticks() {
      Energy energy = new Energy();
      energy.spend(10.0F);
      for (int i = 0; i < IronManRules.ENERGY_REGEN_DELAY; i++) {
         energy.tick();
      }

      assertEquals(90.0F, energy.value(), EPS);
      energy.tick();
      assertEquals(90.0F + IronManRules.ENERGY_REGEN_PER_TICK, energy.value(), EPS);
   }

   @Test
   void drainDelaysRegenLikeSpend() {
      Energy energy = new Energy();
      for (int i = 0; i < 100; i++) {
         energy.drain(IronManRules.DRAIN_CRUISE);
         energy.tick();
      }

      assertEquals(100.0F - 100 * IronManRules.DRAIN_CRUISE, energy.value(), 1.0E-3F);
   }

   @Test
   void regensTenPerSecond() {
      Energy energy = new Energy();
      energy.spend(50.0F);
      for (int i = 0; i < IronManRules.ENERGY_REGEN_DELAY; i++) {
         energy.tick();
      }

      for (int i = 0; i < 20; i++) {
         energy.tick();
      }

      assertEquals(60.0F, energy.value(), EPS);
      for (int i = 0; i < 200; i++) {
         energy.tick();
      }

      assertEquals(IronManRules.ENERGY_MAX, energy.value(), EPS);
   }

   @Test
   void zeroLocksWeaponsUntil20() {
      Energy energy = new Energy();
      energy.drain(100.0F);
      assertTrue(energy.weaponsLocked());
      for (int i = 0; i < IronManRules.ENERGY_REGEN_DELAY; i++) {
         energy.tick();
      }

      // 19.5 after 39 regen ticks: still locked.
      for (int i = 0; i < 39; i++) {
         energy.tick();
      }

      assertEquals(19.5F, energy.value(), EPS);
      assertTrue(energy.weaponsLocked());
      energy.tick();
      assertFalse(energy.weaponsLocked());
   }

   @Test
   void lowButNotEmptyDoesNotLock() {
      Energy energy = new Energy();
      energy.spend(90.0F);
      assertFalse(energy.weaponsLocked());
   }

   @Test
   void saveLoadRoundTrip() {
      Energy energy = new Energy();
      energy.drain(100.0F);
      energy.tick();
      CompoundTag tag = new CompoundTag();
      energy.save(tag);
      assertTrue(tag.contains("Energy"));
      Energy loaded = new Energy();
      loaded.load(tag);
      assertEquals(energy.value(), loaded.value(), EPS);
      assertTrue(loaded.weaponsLocked());

      Energy fresh = new Energy();
      fresh.load(new CompoundTag());
      assertEquals(IronManRules.ENERGY_MAX, fresh.value(), EPS);
   }

   @Test
   void regenScaled() {
      // Mana Absorption: scale 0.5 -> +5/s instead of +10/s after the delay.
      Energy full = new Energy();
      Energy half = new Energy();
      full.drain(50.0F);
      half.drain(50.0F);
      for (int i = 0; i < IronManRules.ENERGY_REGEN_DELAY + 20; i++) {
         full.tick(1.0F);
         half.tick(0.5F);
      }

      assertEquals(50.0F + 20 * IronManRules.ENERGY_REGEN_PER_TICK, full.value(), EPS);
      assertEquals(50.0F + 10 * IronManRules.ENERGY_REGEN_PER_TICK, half.value(), EPS);
      assertEquals(10.0F, 20 * IronManRules.ENERGY_REGEN_PER_TICK, EPS);
   }

   @Test
   void drainNotScaled() {
      // Hovering in a worn suit drains 1/s whatever the regen scale.
      IronManState full = new IronManState();
      IronManState half = new IronManState();
      for (IronManState state : new IronManState[]{full, half}) {
         state.suit.toggle();
         for (int i = 0; i < IronManRules.SUIT_DEPLOY_TICKS; i++) {
            state.suit.tick();
         }
      }

      for (int i = 0; i < 20; i++) {
         IronManHero.flightEnergyTick(full, dev.baranhan.viltrumiteflight.util.FlightState.HOVER, 1.0F);
         IronManHero.flightEnergyTick(half, dev.baranhan.viltrumiteflight.util.FlightState.HOVER, 0.5F);
      }

      assertEquals(IronManRules.ENERGY_MAX - 1.0F, full.energy.value(), 1.0E-3F);
      assertEquals(full.energy.value(), half.energy.value(), EPS);
      Energy energy = new Energy();
      assertTrue(energy.spend(10.0F));
      assertEquals(IronManRules.ENERGY_MAX - 10.0F, energy.value(), EPS);
   }
}
