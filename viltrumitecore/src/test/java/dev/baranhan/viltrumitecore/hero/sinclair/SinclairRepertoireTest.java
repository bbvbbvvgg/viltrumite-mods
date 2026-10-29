package dev.baranhan.viltrumitecore.hero.sinclair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.act.StageAct;
import dev.baranhan.viltrumitecore.hero.sinclair.act.SinclairRepertoire;
import dev.baranhan.viltrumitecore.hero.sinclair.act.SinclairRepertoire.Source;
import org.junit.jupiter.api.Test;

/** §6.2: contract → repertoire → fallback; bad data always ends in the fallback FINALE. */
class SinclairRepertoireTest {
   @Test
   void contractWins() {
      StageAct contract = new StageAct(ActShape.NOVA, 3.0F, 200.0F, 0xFF00FF, "act.mod.big_bang");
      SinclairRepertoire.Choice choice = SinclairRepertoire.choose(contract, true, "homelander", null);
      assertSame(Source.CONTRACT, choice.source());
      assertSame(ActShape.NOVA, choice.act().shape());
      assertEquals(1.2F, choice.act().power());
      assertEquals(32.0F, choice.act().reach());
      assertEquals(0xFF00FF, choice.act().color());
   }

   @Test
   void badContractFallsBack() {
      for (StageAct bad : new StageAct[]{
         new StageAct(null, 1.0F, 10.0F, 0, null),
         new StageAct(ActShape.BEAM, Float.NaN, 10.0F, 0, null),
         new StageAct(ActShape.BEAM, 1.0F, Float.NEGATIVE_INFINITY, 0, null),
         new StageAct(ActShape.BEAM, 1.0F, 10.0F, 0, "NOT A KEY")}) {
         SinclairRepertoire.Choice choice = SinclairRepertoire.choose(bad, true, "homelander", null);
         assertSame(Source.FALLBACK, choice.source());
         assertSame(ActShape.FINALE, choice.act().shape());
      }
   }

   @Test
   void heroParodies() {
      assertSame(ActShape.BEAM, SinclairRepertoire.choose(null, true, "homelander", null).act().shape());
      assertSame(ActShape.CUT, SinclairRepertoire.choose(null, true, "reinhard", null).act().shape());
      assertSame(ActShape.BARRAGE, SinclairRepertoire.choose(null, true, "ironman", null).act().shape());
      assertSame(ActShape.AEGIS, SinclairRepertoire.choose(null, true, "regulus", null).act().shape());
      assertSame(Source.REPERTOIRE, SinclairRepertoire.choose(null, false, "regulus", null).source());
   }

   @Test
   void humansAndOtherSinclairsGetTheFallback() {
      for (String key : new String[]{"human", "viltrumite", "sinclair", "unknown_hero"}) {
         SinclairRepertoire.Choice choice = SinclairRepertoire.choose(null, true, key, null);
         assertSame(Source.FALLBACK, choice.source(), key);
         assertSame(ActShape.FINALE, choice.act().shape(), key);
      }
   }

   @Test
   void mobParodies() {
      assertSame(ActShape.NOVA, mob("minecraft:creeper"));
      assertSame(ActShape.BARRAGE, mob("minecraft:skeleton"));
      assertSame(ActShape.BARRAGE, mob("minecraft:pillager"));
      assertSame(ActShape.BARRAGE, mob("minecraft:blaze"));
      assertSame(ActShape.BARRAGE, mob("minecraft:ghast"));
      assertSame(ActShape.CUT, mob("minecraft:evoker"));
      assertSame(ActShape.CUT, mob("minecraft:vindicator"));
      assertSame(ActShape.BEAM, mob("minecraft:guardian"));
      assertSame(ActShape.BEAM, mob("minecraft:elder_guardian"));
      assertSame(ActShape.NOVA, mob("minecraft:iron_golem"));
      assertSame(ActShape.NOVA, mob("minecraft:ravager"));
      assertSame(ActShape.AEGIS, mob("minecraft:shulker"));
      assertSame(ActShape.FINALE, mob("minecraft:zombie"));
      assertSame(ActShape.FINALE, mob("othermod:thing"));
      assertSame(ActShape.FINALE, SinclairRepertoire.choose(null, false, null, null).act().shape());
   }

   @Test
   void standardActsAreWellFormed() {
      for (ActShape shape : ActShape.values()) {
         StageAct act = SinclairRepertoire.standard(shape);
         assertEquals(true, act.wellFormed(), shape.key());
         assertEquals("act.viltrumitecore.sinclair." + shape.key(), act.nameKey());
      }
   }

   private static ActShape mob(String id) {
      return SinclairRepertoire.choose(null, false, null, id).act().shape();
   }
}
