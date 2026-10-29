package dev.baranhan.viltrumitecore.hero.act;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import org.junit.jupiter.api.Test;

/** Data-only act contract (spec docs/design/2026-10-28-sinclair-design.md §6.2). */
class StageActTest {
   @Test
   void shapesAreStable() {
      assertEquals(6, ActShape.values().length);
      String[] keys = {"beam", "nova", "cut", "barrage", "aegis", "finale"};
      for (int i = 0; i < keys.length; i++) {
         assertSame(ActShape.values()[i], ActShape.fromKey(keys[i]));
         assertEquals(keys[i], ActShape.values()[i].key());
         assertSame(ActShape.values()[i], ActShape.byOrdinal(i));
      }
      assertNull(ActShape.fromKey("laser"));
      assertNull(ActShape.fromKey(null));
      assertNull(ActShape.byOrdinal(-1));
      assertNull(ActShape.byOrdinal(6));
   }

   @Test
   void wellFormedNeedsShapeAndSaneNumbers() {
      assertTrue(new StageAct(ActShape.BEAM, 1.0F, 20.0F, 0xFF0000, "act.x.beam").wellFormed());
      assertTrue(new StageAct(ActShape.CUT, 1.0F, 20.0F, 0, null).wellFormed());
      assertFalse(new StageAct(null, 1.0F, 20.0F, 0, null).wellFormed());
      assertFalse(new StageAct(ActShape.BEAM, Float.NaN, 20.0F, 0, null).wellFormed());
      assertFalse(new StageAct(ActShape.BEAM, 1.0F, Float.POSITIVE_INFINITY, 0, null).wellFormed());
      assertFalse(new StageAct(ActShape.BEAM, -1.0F, 20.0F, 0, null).wellFormed());
      assertFalse(new StageAct(ActShape.BEAM, 1.0F, 0.0F, 0, null).wellFormed());
      assertFalse(new StageAct(ActShape.BEAM, 1000.0F, 20.0F, 0, null).wellFormed());
      assertFalse(new StageAct(ActShape.BEAM, 1.0F, 20.0F, 0, "Bad Key!").wellFormed());
      assertFalse(new StageAct(ActShape.BEAM, 1.0F, 20.0F, 0, "").wellFormed());
      assertFalse(new StageAct(ActShape.BEAM, 1.0F, 20.0F, 0, "a".repeat(129)).wellFormed());
   }

   @Test
   void clampedStaysInBounds() {
      StageAct act = new StageAct(ActShape.NOVA, 9.0F, 500.0F, 0x7F123456, "k").clamped(0.6F, 1.2F, 4.0F, 32.0F);
      assertEquals(1.2F, act.power());
      assertEquals(32.0F, act.reach());
      assertEquals(0x123456, act.color());
      StageAct low = new StageAct(ActShape.NOVA, 0.01F, 1.0F, 0, null).clamped(0.6F, 1.2F, 4.0F, 32.0F);
      assertEquals(0.6F, low.power());
      assertEquals(4.0F, low.reach());
   }

   /** Only plain values: no behaviour or references can travel through the contract. */
   @Test
   void recordCarriesOnlyData() {
      for (RecordComponent component : StageAct.class.getRecordComponents()) {
         Class<?> type = component.getType();
         assertTrue(type.isPrimitive() || type == String.class || type == ActShape.class, component.getName());
      }
   }

   @Test
   void defaultSignatureActIsNull() throws Exception {
      Method method = HeroDefinition.class.getMethod("signatureAct", net.minecraft.world.entity.player.Player.class);
      assertTrue(method.isDefault());
      assertFalse(Modifier.isStatic(method.getModifiers()));
      dev.baranhan.viltrumitecore.hero.HeroRegistry.registerDefaults();
      for (dev.baranhan.viltrumitecore.hero.HeroId id : new dev.baranhan.viltrumitecore.hero.HeroId[]{
         dev.baranhan.viltrumitecore.hero.HeroId.HUMAN, dev.baranhan.viltrumitecore.hero.HeroId.VILTRUMITE}) {
         assertNull(dev.baranhan.viltrumitecore.hero.HeroRegistry.get(id).signatureAct(null), id.key());
      }
   }
}
