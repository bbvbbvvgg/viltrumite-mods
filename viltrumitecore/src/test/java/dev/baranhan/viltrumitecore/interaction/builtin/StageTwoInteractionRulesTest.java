package dev.baranhan.viltrumitecore.interaction.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.entity.avengers.AvengerAction;
import dev.baranhan.viltrumitecore.entity.avengers.AvengerKind;
import dev.baranhan.viltrumitecore.hero.ViltrumiteHero;
import dev.baranhan.viltrumitecore.hero.captain.CaptainHero;
import dev.baranhan.viltrumitecore.hero.combat.CombatPresence;
import dev.baranhan.viltrumitecore.hero.combat.CombatTags;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.ironman.IronManHero;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterTags;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusHero;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardHero;
import dev.baranhan.viltrumitecore.hero.thor.HammerState;
import dev.baranhan.viltrumitecore.hero.thor.ThorCombat;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirPhase;
import dev.baranhan.viltrumitecore.interaction.ContextualInteraction;
import dev.baranhan.viltrumitecore.interaction.ContextualInteractions;
import dev.baranhan.viltrumitecore.interaction.InteractionGuard;
import dev.baranhan.viltrumitecore.interaction.InteractionSpec;
import dev.baranhan.viltrumitecore.interaction.InteractionTrigger;
import dev.baranhan.viltrumitecore.interaction.InteractionTuning;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosMove;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Stage 2 of the contextual interactions: the pure rules behind the 13 new combos, the priority / conflict
 * boundaries between interactions on the same trigger, the per-pair guard in multiplayer and the ally gates.
 */
class StageTwoInteractionRulesTest {
   static final CombatPresence THOR_STRIKE = ThorCombat.presence(HammerState.IN_HAND, true, ThorMove.HAMMER_COMBO, MovePhase.MAIN);
   static final CombatPresence THOR_IDLE = ThorCombat.presence(HammerState.IN_HAND, true, null, null);
   static final CombatPresence HULKBUSTER = HulkbusterTags.presence(false, true);

   private static Map<String, InteractionSpec> specs() {
      Map<String, InteractionSpec> out = new HashMap<>();
      for (ContextualInteraction i : ContextualInteractions.builtins()) {
         out.put(i.spec().id(), i.spec());
      }
      return out;
   }

   // ---- combos ----

   @Test
   void reidStrainsOnlyInItsStrikingWindow() {
      assertTrue(ReidStrainsLionsHeart.strikes(ReinhardHero.REID_STROKE));
      assertFalse(ReidStrainsLionsHeart.strikes(ReinhardHero.REID_DRAWN), "a drawn idle sword does not strain");
      assertFalse(ReidStrainsLionsHeart.strikes(THOR_STRIKE), "Mjolnir does not cut what cannot be cut");
      assertFalse(ReidStrainsLionsHeart.strikes(CombatPresence.NONE));
   }

   @Test
   void threeReidStrokesBreakAFreshLionTwoDoNot() {
      int strain = RegulusRules.STRAIN_LION_ON; // the Lion has just been raised
      strain = RegulusHero.strainedBy(strain, InteractionTuning.REID_STRAIN);
      assertFalse(RegulusRules.seizes(strain));
      strain = RegulusHero.strainedBy(strain, InteractionTuning.REID_STRAIN);
      assertFalse(RegulusRules.seizes(strain), "two strokes on a fresh Lion: strained, still standing");
      strain = RegulusHero.strainedBy(strain, InteractionTuning.REID_STRAIN);
      assertTrue(RegulusRules.seizes(strain), "the third stroke stops the heart");
      assertEquals(RegulusRules.STRAIN_MAX, RegulusHero.strainedBy(RegulusRules.STRAIN_MAX, InteractionTuning.REID_STRAIN), "capped");
   }

   @Test
   void onlyAHammerInFlightRebounds() {
      assertTrue(MjolnirReboundsOffShield.inFlight(MjolnirPhase.THROWN));
      for (MjolnirPhase p : MjolnirPhase.values()) {
         if (p != MjolnirPhase.THROWN) {
            assertFalse(MjolnirReboundsOffShield.inFlight(p), p.name());
         }
      }
   }

   @Test
   void viltrumiteFistNeedsBothSides() {
      assertTrue(ViltrumiteFistMeetsVibranium.pairs(ViltrumiteHero.VILTRUMITE_BODY, CaptainHero.SHIELD_GUARD));
      assertFalse(ViltrumiteFistMeetsVibranium.pairs(CaptainHero.SHIELD_GUARD, ViltrumiteHero.VILTRUMITE_BODY), "direction matters");
      assertFalse(ViltrumiteFistMeetsVibranium.pairs(ViltrumiteHero.VILTRUMITE_BODY, CombatPresence.NONE), "no raised shield (or a broken one)");
      assertFalse(ViltrumiteFistMeetsVibranium.pairs(HULKBUSTER, CaptainHero.SHIELD_GUARD), "the Hulkbuster is not a Viltrumite");
   }

   @Test
   void repulsorShardsKeepToTheConeAndShareTheBolt() {
      Vec3 aim = new Vec3(0, 0, 1);
      assertTrue(RepulsorSplitsOnVibranium.inCone(aim, new Vec3(0, 0, 8), InteractionTuning.SPLIT_CONE_COS));
      assertTrue(RepulsorSplitsOnVibranium.inCone(aim, new Vec3(4, 0, 8), InteractionTuning.SPLIT_CONE_COS));
      assertFalse(RepulsorSplitsOnVibranium.inCone(aim, new Vec3(8, 0, 2), InteractionTuning.SPLIT_CONE_COS), "beside the bearer");
      assertFalse(RepulsorSplitsOnVibranium.inCone(aim, new Vec3(0, 0, -8), InteractionTuning.SPLIT_CONE_COS), "behind the bearer");
      assertFalse(RepulsorSplitsOnVibranium.inCone(aim, Vec3.ZERO, InteractionTuning.SPLIT_CONE_COS));
      assertEquals(6.0F, RepulsorSplitsOnVibranium.shardDamage(10.0F), 1.0E-4F);
      assertEquals(InteractionTuning.SPLIT_MIN_DAMAGE, RepulsorSplitsOnVibranium.shardDamage(1.0F), 1.0E-4F);
      assertTrue(InteractionTuning.SPLIT_SHARDS * InteractionTuning.SPLIT_SHARE > 1.0F, "splitting pays off only when the bearer aims");
   }

   @Test
   void timeStoneRewindsOnlyTheLedgerOnALiveThanos() {
      assertTrue(TimeStoneRewindsLedger.rewinds(true, true, false));
      assertFalse(TimeStoneRewindsLedger.rewinds(false, true, false), "a hit in moving time is ordinary");
      assertFalse(TimeStoneRewindsLedger.rewinds(true, false, false), "anyone else pays the ledger");
      assertFalse(TimeStoneRewindsLedger.rewinds(true, true, true), "a defeated Titan rewinds nothing");
   }

   @Test
   void onlyThanosMeleeMovesCrushAGuard() {
      for (ThanosMove move : ThanosMove.values()) {
         assertEquals(move.kind == ThanosMove.Kind.MELEE, TitanBreaksGuard.crushing(move), move.name());
      }
      assertTrue(TitanBreaksGuard.crushing(ThanosMove.CLEAVE));
      assertFalse(TitanBreaksGuard.crushing(ThanosMove.POWER_WAVE));
      assertFalse(TitanBreaksGuard.crushing(null));
   }

   @Test
   void reidCleavesIntoTwoSymmetricSlowerHalves() {
      assertTrue(ReidCleavesMeteor.cleaves(ReinhardHero.REID_DRAWN), "drawn is enough");
      assertTrue(ReidCleavesMeteor.cleaves(ReinhardHero.REID_STROKE));
      assertFalse(ReidCleavesMeteor.cleaves(CaptainHero.SHIELD_GUARD));
      Vec3 flight = new Vec3(0.6, -2.0, 0.0);
      Vec3 side = ReidCleavesMeteor.cutNormal(flight, new Vec3(0, 0, 1));
      assertEquals(0.0, side.y, 1.0E-9);
      assertEquals(1.0, side.length(), 1.0E-9);
      Vec3 a = ReidCleavesMeteor.half(flight, side, 1);
      Vec3 b = ReidCleavesMeteor.half(flight, side, -1);
      assertTrue(a.dot(side) > 0 && b.dot(side) < 0, "the halves part to either side of the cut");
      assertEquals(a.dot(side), -b.dot(side), 1.0E-9);
      assertTrue(a.length() < flight.length(), "slower than the meteor");
      assertTrue(a.y < 0 && b.y < 0, "they still fall");
      Vec3 vertical = ReidCleavesMeteor.cutNormal(new Vec3(0, -2.5, 0), new Vec3(1, 0, 0));
      assertEquals(1.0, vertical.length(), 1.0E-9, "a vertical fall is cut across the look");
   }

   @Test
   void viltrumiteNeverHurlsIntoHisFeet() {
      Vec3 down = ViltrumiteHurlsMeteor.hurl(new Vec3(0, -1, 0.01), new Vec3(0, -1.0, 0));
      assertTrue(down.y > 0, "aimed at the ground, the throw still goes up and out");
      Vec3 fast = ViltrumiteHurlsMeteor.hurl(new Vec3(1, 0.5, 0), new Vec3(0, -4.0, 0));
      assertEquals(4.0, fast.length(), 1.0E-9, "keeps a faster meteor's speed");
      Vec3 slow = ViltrumiteHurlsMeteor.hurl(new Vec3(1, 0.5, 0), new Vec3(0, -1.0, 0));
      assertEquals(InteractionTuning.HURL_SPEED, slow.length(), 1.0E-9);
   }

   @Test
   void shadowRefugeIsOutsideTheBlastAndInReach() {
      assertTrue(ShadowExchangeMeteor.refuge(InteractionTuning.METEOR_BLAST_RADIUS + 1.0, 10.0));
      assertFalse(ShadowExchangeMeteor.refuge(InteractionTuning.METEOR_BLAST_RADIUS - 0.5, 10.0), "a shadow inside the blast is no refuge");
      assertFalse(ShadowExchangeMeteor.refuge(30.0, InteractionTuning.EXCHANGE_RANGE + 1.0), "too far for the exchange");
   }

   @Test
   void lightningRulesCoverPlayerAndNpcThor() {
      assertTrue(Leviathans.lightning(true, CombatPresence.NONE), "a lightning damage type");
      assertTrue(Leviathans.lightning(false, THOR_STRIKE), "Mjolnir in its striking window");
      assertFalse(Leviathans.lightning(false, THOR_IDLE), "a hammer at rest is no bolt");
      assertFalse(Leviathans.lightning(false, ReinhardHero.REID_STROKE));
      assertTrue(Leviathans.npcThunder(AvengerKind.THOR, AvengerAction.SPECIAL_1), "NPC Thor's hammer throw");
      assertTrue(Leviathans.npcThunder(AvengerKind.THOR, AvengerAction.SPECIAL_2), "NPC Thor's storm");
      assertFalse(Leviathans.npcThunder(AvengerKind.THOR, AvengerAction.ATTACK_1), "his plain combo");
      assertFalse(Leviathans.npcThunder(AvengerKind.IRON_MAN, AvengerAction.SPECIAL_2));
   }

   @Test
   void heavyHittersAreTheMechTheViltrumiteAndTheHulk() {
      assertTrue(HeavyHitterHaltsLeviathan.heavy(HULKBUSTER, null));
      assertTrue(HeavyHitterHaltsLeviathan.heavy(ViltrumiteHero.VILTRUMITE_BODY, null));
      assertTrue(HeavyHitterHaltsLeviathan.heavy(CombatPresence.NONE, AvengerKind.HULK));
      assertFalse(HeavyHitterHaltsLeviathan.heavy(CombatPresence.NONE, AvengerKind.BLACK_WIDOW));
      assertFalse(HeavyHitterHaltsLeviathan.heavy(IronManHero.POWERED_ARMOR, null), "the flight suit is not the Hulkbuster");
      assertFalse(HeavyHitterHaltsLeviathan.heavy(THOR_STRIKE, AvengerKind.THOR), "Thor's meeting with a Leviathan is the lightning one");
   }

   // ---- ally protection ----

   @Test
   void legionDischargeNeverStrikesWithoutAnEnemyVerdict() {
      assertTrue(ThunderChargesLegion.dischargeable(true, true, true));
      assertFalse(ThunderChargesLegion.dischargeable(false, true, true), "commander offline: the suit only soaks the charge");
      assertFalse(ThunderChargesLegion.dischargeable(true, false, true), "an ally, a summon or a PvP-spared player of the commander");
      assertFalse(ThunderChargesLegion.dischargeable(true, true, false), "no animals, villagers, item frames");
   }

   @Test
   void presencesOfDifferentHeroesNeverOverlap() {
      List<CombatPresence> heroes = List.of(CaptainHero.SHIELD_GUARD, ReinhardHero.REID_DRAWN, ViltrumiteHero.VILTRUMITE_BODY, IronManHero.POWERED_ARMOR,
         HULKBUSTER, THOR_STRIKE);
      List<String> tags = List.of(CombatTags.WEAPON_SHIELD, CombatTags.TRAIT_ANNIHILATING, CombatTags.BODY_VILTRUMITE, CombatTags.ARMOR_POWERED,
         HulkbusterTags.BODY_MECH_HEAVY, CombatTags.WEAPON_HAMMER);
      for (int i = 0; i < heroes.size(); i++) {
         for (int j = 0; j < tags.size(); j++) {
            assertEquals(i == j, heroes.get(i).has(tags.get(j)), heroes.get(i).weaponId() + " / " + tags.get(j));
         }
      }
   }

   // ---- priorities and conflicts ----

   @Test
   void sameTriggerConflictsResolveInTheDesignedOrder() {
      Map<String, InteractionSpec> s = specs();
      // A raised shield: Reid's cut beats everything, then the melee Mjolnir, then the thrown one, the fist, the bolt.
      assertOrder(s, "reid_shatters_vibranium", "mjolnir_rebounds_off_shield", "viltrumite_fist_meets_vibranium", "titan_breaks_guard");
      assertOrder(s, "mjolnir_meets_vibranium", "mjolnir_rebounds_off_shield");
      // A meteor: the shield first (it protects the team), then the hero's own answer, the Leviathan, Thor's lightning last.
      assertOrder(s, "vibranium_stops_meteor", "reid_cleaves_meteor", "viltrumite_hurls_meteor", "shadow_exchange_meteor", "meteor_downs_leviathan",
         "mjolnir_shatters_meteor");
      // Lightning: a player's armour before a drone, the Leviathan's chain before its fist halt, both before the Legion bonus.
      assertOrder(s, "thunder_overcharges_armor", "thunder_charges_legion");
      assertOrder(s, "lightning_runs_the_leviathan", "heavy_hitter_halts_leviathan", "legion_vs_chitauri", "titan_scatters_summons");
      for (String id : List.of("reid_strains_lions_heart", "mjolnir_rebounds_off_shield", "viltrumite_fist_meets_vibranium", "time_stone_rewinds_ledger",
         "titan_breaks_guard", "thunder_charges_legion")) {
         assertEquals(InteractionTrigger.HIT, s.get(id).trigger(), id);
      }
      for (String id : List.of("repulsor_splits_on_vibranium", "reid_cleaves_meteor", "viltrumite_hurls_meteor", "shadow_exchange_meteor",
         "meteor_downs_leviathan")) {
         assertEquals(InteractionTrigger.PROJECTILE, s.get(id).trigger(), id);
      }
      for (String id : List.of("lightning_runs_the_leviathan", "heavy_hitter_halts_leviathan")) {
         assertEquals(InteractionTrigger.HURT, s.get(id).trigger(), id);
      }
   }

   private static void assertOrder(Map<String, InteractionSpec> s, String... ids) {
      for (int i = 1; i < ids.length; i++) {
         assertTrue(s.get(ids[i - 1]).priority() > s.get(ids[i]).priority(), ids[i - 1] + " before " + ids[i]);
      }
   }

   @Test
   void consumingHitInteractionsSwallowTheRestOfTheStroke() {
      Map<String, InteractionSpec> s = specs();
      for (String id : List.of("reid_strains_lions_heart", "mjolnir_rebounds_off_shield", "viltrumite_fist_meets_vibranium", "titan_breaks_guard")) {
         assertTrue(s.get(id).aftermathTicks() > 0, id + " needs an aftermath: one swing reaches the body several times");
         assertTrue(s.get(id).cooldownTicks() >= s.get(id).aftermathTicks(), id);
      }
      for (String id : List.of("lightning_runs_the_leviathan", "heavy_hitter_halts_leviathan")) {
         assertTrue(s.get(id).cooldownTicks() >= 100, id + " is a set piece, not a per-hit bonus");
      }
   }

   // ---- multiplayer ----

   @Test
   void twoDuelsAtOnceDoNotShareACooldown() {
      InteractionSpec rebound = specs().get("mjolnir_rebounds_off_shield");
      InteractionGuard g = new InteractionGuard();
      int thorA = 10, capA = 11, thorB = 20, capB = 21;
      g.fired(rebound, thorA, capA, 100);
      assertTrue(g.coolingDown(rebound.id(), thorA, capA, 101));
      assertFalse(g.coolingDown(rebound.id(), thorB, capB, 101), "the other pair on the server fires on its own");
      assertFalse(g.coolingDown(rebound.id(), thorA, capB, 101), "the same Thor on another shield too");
      assertTrue(g.inAftermath(capA, thorA, 105), "the rest of that throw is swallowed");
      assertFalse(g.inAftermath(capB, thorA, 105));
      assertFalse(g.coolingDown(rebound.id(), thorA, capA, 100 + rebound.cooldownTicks()));
   }
}
