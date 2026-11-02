package dev.baranhan.viltrumitecore.hero.ghostrider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleRules;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

/** Pure numbers of the Ghost Rider: the burn-away clock, the chain, hellfire, the bike's hits and the Penance Stare. */
class GhostRiderRulesTest {
   private static final float EPS = 1.0E-4F;

   @Test
   void burnFramesRunFromJohnnyToTheSkullAndBack() {
      assertEquals(0, GhostRiderRules.burnFrame(false, 0), "Johnny at the start");
      assertEquals(GhostRiderRules.BURN_FRAMES, GhostRiderRules.burnFrame(false, GhostRiderRules.TRANSFORM_FLARE_TICK),
         "a bare skull when it bursts into flame");
      int last = 0;
      for (int t = 0; t <= GhostRiderRules.TRANSFORM_TICKS; t++) {
         int f = GhostRiderRules.burnFrame(false, t);
         assertTrue(f >= last && f <= GhostRiderRules.BURN_FRAMES, "the skin only burns further away");
         last = f;
      }
      assertEquals(GhostRiderRules.BURN_FRAMES, GhostRiderRules.burnFrame(true, 0));
      assertEquals(0, GhostRiderRules.burnFrame(true, GhostRiderRules.REVERT_TICKS), "Johnny again after the way back");
      last = GhostRiderRules.BURN_FRAMES;
      for (int t = 0; t <= GhostRiderRules.REVERT_TICKS + 5; t++) {
         int f = GhostRiderRules.burnFrame(true, t);
         assertTrue(f <= last && f >= 0, "the skin only grows back");
         last = f;
      }
   }

   @Test
   void sinsComeOnlyFromRecordedDeeds() {
      assertEquals(0, GhostRiderRules.sins(0, 0, 0.0F), "nothing is invented");
      assertEquals(GhostRiderRules.SIN_PLAYER_KILL, GhostRiderRules.sins(1, 0, 0.0F));
      assertEquals(GhostRiderRules.SIN_INNOCENT_KILL, GhostRiderRules.sins(0, 1, 0.0F));
      assertEquals(0, GhostRiderRules.sins(0, 0, GhostRiderRules.SIN_HARM - 0.1F), "a scratch is no sin");
      assertEquals(1, GhostRiderRules.sins(0, 0, GhostRiderRules.SIN_HARM));
      assertEquals(2 * GhostRiderRules.SIN_PLAYER_KILL + 3 * GhostRiderRules.SIN_INNOCENT_KILL + 4, GhostRiderRules.sins(2, 3, 45.0F));
      assertEquals(0, GhostRiderRules.sins(-5, -1, -100.0F), "corrupt negative records count as nothing");
      assertEquals(Integer.MAX_VALUE, GhostRiderRules.sins(Integer.MAX_VALUE, Integer.MAX_VALUE, Float.MAX_VALUE), "no overflow");
      assertEquals(Integer.MAX_VALUE, GhostRiderRules.sins(0, 0, Float.MAX_VALUE), "no overflow from harm alone");
      assertEquals(0, GhostRiderRules.sins(0, 0, Float.NaN), "a corrupt harm record counts as nothing");
   }

   @Test
   void penanceSparesTheInnocentGrowsWithSinsAndIsCapped() {
      assertEquals(0.0F, GhostRiderRules.penanceDamage(0, false, 20.0F), EPS, "the innocent are spared");
      assertEquals(0.0F, GhostRiderRules.penanceDamage(0, true, 500.0F), EPS, "an innocent boss too");
      assertEquals(GhostRiderRules.STARE_BASE + GhostRiderRules.STARE_PER_SIN, GhostRiderRules.penanceDamage(1, false, 20.0F), EPS);
      float last = 0.0F;
      for (int sins = 1; sins < 100; sins++) {
         float d = GhostRiderRules.penanceDamage(sins, false, 20.0F);
         assertTrue(d >= last, "more sins never burn less");
         assertTrue(d <= GhostRiderRules.STARE_CAP + EPS, "capped");
         last = d;
      }
      assertEquals(GhostRiderRules.STARE_CAP, GhostRiderRules.penanceDamage(Integer.MAX_VALUE, false, 20.0F), EPS);
      assertEquals(40.0F, GhostRiderRules.penanceDamage(1000, true, 200.0F), EPS, "a fifth of a boss's health");
      assertEquals(GhostRiderRules.STARE_BOSS_CAP, GhostRiderRules.penanceDamage(1000, true, 5000.0F), EPS, "never more than the boss cap");
      assertTrue(GhostRiderRules.penanceDamage(1000, true, 0.0F) > 0.0F, "a broken max health still gets a verdict");
   }

   @Test
   void slamFallsOffToSixtyPercentAtTheRim() {
      assertEquals(GhostRiderRules.SLAM_DAMAGE, GhostRiderRules.slamDamage(0.0), EPS);
      assertEquals(GhostRiderRules.SLAM_DAMAGE, GhostRiderRules.slamDamage(GhostRiderRules.SLAM_RADIUS * 0.5), EPS);
      assertEquals(GhostRiderRules.SLAM_DAMAGE * 0.6F, GhostRiderRules.slamDamage(GhostRiderRules.SLAM_RADIUS), EPS);
      assertEquals(GhostRiderRules.SLAM_DAMAGE * 0.6F, GhostRiderRules.slamDamage(GhostRiderRules.SLAM_RADIUS * 3), EPS);
      float last = Float.MAX_VALUE;
      for (double d = 0; d <= GhostRiderRules.SLAM_RADIUS; d += 0.25) {
         float v = GhostRiderRules.slamDamage(d);
         assertTrue(v <= last, "falls off with distance");
         last = v;
      }
   }

   @Test
   void hellfireBurnIsBoundedAndWaterProofNumbersAddUp() {
      assertEquals(0.0F, GhostRiderRules.burnTotal(0), EPS);
      assertEquals(4.0F, GhostRiderRules.burnTotal(GhostRiderRules.LASH_BURN_TICKS), EPS);
      assertEquals(GhostRiderRules.burnTotal(GhostRiderRules.BURN_MAX_TICKS), GhostRiderRules.burnTotal(GhostRiderRules.BURN_MAX_TICKS * 10), EPS,
         "the burn never stacks past its cap");
      assertTrue(GhostRiderRules.burnTotal(GhostRiderRules.BURN_MAX_TICKS) <= 20.0F, "a full burn never kills a full-health player alone");
   }

   @Test
   void normalRidingNeverHurtsAndRamsScaleWithSpeed() {
      assertEquals(0.0F, GhostRiderRules.bikeHitDamage(0.0), EPS);
      assertEquals(0.0F, GhostRiderRules.bikeHitDamage(GhostRiderRules.HIT_SPEED - 0.01), EPS, "cruising only nudges");
      assertEquals(GhostRiderRules.HIT_DAMAGE_MIN, GhostRiderRules.bikeHitDamage(GhostRiderRules.HIT_SPEED), EPS);
      assertEquals(GhostRiderRules.HIT_DAMAGE_MAX, GhostRiderRules.bikeHitDamage(HellCycleRules.MAX_SPEED), EPS);
      assertEquals(GhostRiderRules.HIT_DAMAGE_MAX, GhostRiderRules.bikeHitDamage(HellCycleRules.BOOST_SPEED), EPS, "boost is the ram's job");
      assertTrue(GhostRiderRules.HIT_SPEED > HellCycleRules.MAX_SPEED * 0.5, "half throttle through a crowd is harmless");
      assertTrue(GhostRiderRules.RAM_DAMAGE > GhostRiderRules.HIT_DAMAGE_MAX, "the boosted ram hits hardest");
   }

   @Test
   void chainPullsOnlyManSizedBodies() {
      assertTrue(GhostRiderRules.pullable(0.6F, 1.95F, false), "zombie");
      assertTrue(GhostRiderRules.pullable(0.6F, 1.8F, false), "player");
      assertFalse(GhostRiderRules.pullable(1.4F, 2.7F, false), "iron golem");
      assertFalse(GhostRiderRules.pullable(0.6F, 1.8F, true), "bosses only get bitten");
   }

   @Test
   void theRiderShrugsOffFalls() {
      assertEquals(0.0F, GhostRiderRules.fallDamage(GhostRiderRules.SAFE_FALL), EPS);
      assertEquals(5.0F, GhostRiderRules.fallDamage(GhostRiderRules.SAFE_FALL + 10.0F), EPS);
   }

   @Test
   void cooldownTableMatchesTheSlots() {
      assertEquals(GhostRiderRules.COOLDOWNS, GhostRiderRules.COOLDOWN_TICKS.length);
      for (int t : GhostRiderRules.COOLDOWN_TICKS) {
         assertTrue(t > 0);
      }
      assertTrue(GhostRiderRules.COOLDOWN_TICKS[GhostRiderRules.CD_STARE] >= 600, "the verdict is rare");
      assertTrue(GhostRiderRules.STARE_INNOCENT_COOLDOWN < GhostRiderRules.COOLDOWN_TICKS[GhostRiderRules.CD_STARE]);
      assertTrue(GhostRiderRules.DASH_FOOT_COOLDOWN < GhostRiderRules.COOLDOWN_TICKS[GhostRiderRules.CD_DASH]);
   }

   @Test
   void ledgerKeepsDeedsAcrossSaves() {
      assertTrue(PenanceLedger.Deeds.NONE.innocent());
      assertEquals(0, PenanceLedger.Deeds.NONE.sins());
      PenanceLedger.Deeds killer = new PenanceLedger.Deeds(1, 2, 25.0F);
      assertFalse(killer.innocent());
      assertEquals(GhostRiderRules.sins(1, 2, 25.0F), killer.sins());
      CompoundTag tag = new CompoundTag();
      ListTag souls = new ListTag();
      CompoundTag soul = new CompoundTag();
      UUID id = UUID.randomUUID();
      soul.putUUID("Id", id);
      soul.putInt("Players", 1);
      soul.putInt("Innocents", 2);
      soul.putFloat("Harm", 25.0F);
      souls.add(soul);
      souls.add(new CompoundTag());
      tag.put("Souls", souls);
      CompoundTag again = PenanceLedger.load(tag).save(new CompoundTag());
      ListTag list = again.getList("Souls", 10);
      assertEquals(1, list.size(), "the record without an id is dropped");
      CompoundTag c = list.getCompound(0);
      assertEquals(id, c.getUUID("Id"));
      assertEquals(1, c.getInt("Players"));
      assertEquals(2, c.getInt("Innocents"));
      assertEquals(25.0F, c.getFloat("Harm"), EPS);
   }
}
