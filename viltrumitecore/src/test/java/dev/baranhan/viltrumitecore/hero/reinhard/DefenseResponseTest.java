package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroAttackContext;
import dev.baranhan.viltrumitecore.hero.ProjectileResponse;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction.Kind;
import org.junit.jupiter.api.Test;

/** Regressions of the PR #41 review: rear arrows, contextual reactions outside the parry cone, parry feedback. */
class DefenseResponseTest {

   @Test
   void rearThreatsEvadeExceptBraces() {
      assertEquals(Kind.EVADE, ReinhardHero.classify(Kind.ARROW, 150.0F));
      assertEquals(Kind.EVADE, ReinhardHero.classify(Kind.PROJECTILE, -120.0F));
      assertEquals(Kind.BRACE, ReinhardHero.classify(Kind.BRACE, 170.0F));
      assertEquals(Kind.ARROW, ReinhardHero.classify(Kind.ARROW, 30.0F));
      assertEquals(Kind.PALM, ReinhardHero.classify(Kind.PALM, -100.0F));
   }

   @Test
   void rearArrowDuringAnotherCommittedReactionIsDeflectedNotCaught() {
      // The committed presentation still shows the front palm, the new threat is behind.
      assertEquals(ProjectileResponse.DEFLECT, ReinhardHero.arrowResponse(false, Kind.EVADE, Kind.PALM));
      assertEquals(ProjectileResponse.CATCH, ReinhardHero.arrowResponse(false, Kind.ARROW, Kind.PALM));
      assertEquals(ProjectileResponse.CATCH, ReinhardHero.arrowResponse(false, Kind.ARROW, Kind.ARROW));
      assertEquals(ProjectileResponse.DEFLECT, ReinhardHero.arrowResponse(false, Kind.ARROW, Kind.EVADE));
      assertEquals(ProjectileResponse.DEFLECT, ReinhardHero.arrowResponse(true, Kind.ARROW, Kind.ARROW));
   }

   @Test
   void contextualAttacksMapToTheirReaction() {
      assertEquals(Kind.BRACE, ReinhardHero.contextKind(HeroAttackContext.Kind.WAVE));
      assertEquals(Kind.PROJECTILE, ReinhardHero.contextKind(HeroAttackContext.Kind.SHOT));
      assertNull(ReinhardHero.contextKind(null));
   }

   @Test
   void contextualHitsReactEvenWhenTheFrontalParryRejectsThem() {
      // A Repulsor shot from behind: not parried, still a reaction (turned into an evade by the rear guard).
      assertEquals(Kind.PROJECTILE, ReinhardHero.hitReaction(false, Kind.PROJECTILE, 5.0F, true));
      assertEquals(Kind.EVADE, ReinhardHero.classify(ReinhardHero.hitReaction(false, Kind.PROJECTILE, 5.0F, true), 160.0F));
      assertEquals(Kind.BRACE, ReinhardHero.hitReaction(false, Kind.BRACE, 30.0F, false));
      assertNull(ReinhardHero.hitReaction(false, null, 5.0F, true), "plain unparried melee keeps no pose");
      assertEquals(Kind.EVADE, ReinhardHero.hitReaction(true, null, 25.0F, false));
      assertEquals(Kind.PALM, ReinhardHero.hitReaction(true, null, 5.0F, true));
      assertEquals(Kind.WEAPON, ReinhardHero.hitReaction(true, null, 5.0F, false));
   }

   @Test
   void everyParriedHitGetsFeedbackOncePerAttackerAndTick() {
      ReinhardState state = new ReinhardState();
      assertTrue(state.parryFx(100L, 7));
      assertFalse(state.parryFx(100L, 7), "a second hurt call of the same swing");
      assertTrue(state.parryFx(100L, 8), "a second attacker in the same tick");
      assertTrue(state.parryFx(103L, 8), "the next hit of a combo during the defense hold");
      assertTrue(state.parryFx(104L, 8));
   }
}
