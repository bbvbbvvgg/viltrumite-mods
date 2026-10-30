package dev.baranhan.viltrumitecore.hero.sinclair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.sinclair.act.CopyCard;
import dev.baranhan.viltrumitecore.hero.sinclair.act.SinclairRepertoire;
import org.junit.jupiter.api.Test;

/** What the copy card says (design 2026-11-02 §2.3). */
class CopyCardTest {
   @Test
   void homelanderCardNamesHeatVisionAndTheFootlightBeam() {
      CopyCard.Plan plan = CopyCard.plan(ActShape.BEAM, "homelander", SinclairRepertoire.Source.REPERTOIRE);
      assertEquals(SinclairRepertoire.nameKey(ActShape.BEAM), plan.title());
      assertEquals("copy.viltrumitecore.sinclair.origin.homelander", plan.origin());
      assertEquals("copy.viltrumitecore.sinclair.hero.homelander", plan.heroName());
      assertEquals("copy.viltrumitecore.sinclair.desc.beam", plan.desc());
      assertEquals("copy.viltrumitecore.sinclair.header", plan.header());
      assertFalse(plan.fallback());
   }

   @Test
   void theSpareActIsNeverPresentedAsAStolenUltimate() {
      CopyCard.Plan plan = CopyCard.plan(ActShape.FINALE, "thor", SinclairRepertoire.Source.FALLBACK);
      assertTrue(plan.fallback());
      assertEquals("copy.viltrumitecore.sinclair.header.fallback", plan.header());
      assertEquals("copy.viltrumitecore.sinclair.origin.fallback", plan.origin());
      // even a FINALE reached another way wears the spare ribbon
      assertTrue(CopyCard.plan(ActShape.FINALE, null, SinclairRepertoire.Source.REPERTOIRE).fallback());
   }

   @Test
   void mobsAndContractsGetTheirOwnOrigin() {
      CopyCard.Plan mob = CopyCard.plan(ActShape.NOVA, null, SinclairRepertoire.Source.REPERTOIRE);
      assertNull(mob.heroName());
      assertEquals("copy.viltrumitecore.sinclair.origin.mob.nova", mob.origin());
      CopyCard.Plan contract = CopyCard.plan(ActShape.CUT, "jinwoo", SinclairRepertoire.Source.CONTRACT);
      assertEquals("copy.viltrumitecore.sinclair.origin.contract", contract.origin());
      assertEquals("copy.viltrumitecore.sinclair.hero.jinwoo", contract.heroName());
      assertEquals("copy.viltrumitecore.sinclair.hero.unknown", CopyCard.plan(ActShape.CUT, "someone_new", SinclairRepertoire.Source.CONTRACT).heroName());
   }

   @Test
   void everyPlanKeyIsListed() {
      java.util.List<String> keys = CopyCard.allKeys();
      for (ActShape shape : ActShape.values()) {
         for (SinclairRepertoire.Source source : SinclairRepertoire.Source.values()) {
            for (String hero : new String[]{null, "homelander", "reinhard", "ironman", "regulus", "thor", "dio", "jinwoo", "x"}) {
               CopyCard.Plan plan = CopyCard.plan(shape, hero, source);
               assertTrue(keys.contains(plan.origin()), plan.origin());
               assertTrue(keys.contains(plan.desc()), plan.desc());
               assertTrue(keys.contains(plan.header()), plan.header());
               assertTrue(plan.heroName() == null || keys.contains(plan.heroName()), String.valueOf(plan.heroName()));
            }
         }
      }

      assertTrue(keys.contains("copy.viltrumitecore.sinclair.header.volunteer"));
      assertTrue(keys.contains("copy.viltrumitecore.sinclair.volunteer"));
   }
}
