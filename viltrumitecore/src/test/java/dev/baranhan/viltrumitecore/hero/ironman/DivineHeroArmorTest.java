package dev.baranhan.viltrumitecore.hero.ironman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.DamageAbsorb;
import dev.baranhan.viltrumitecore.hero.DivineDamage;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.IronManHulkbuster;
import dev.baranhan.viltrumitecore.hero.ironman.mark.EquipTimeline;
import dev.baranhan.viltrumitecore.hero.ironman.mark.IronManMarks;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Divine damage passes the Iron Man armor layers like vanilla armor (Reinhard stage 1: "armor and mark ignored"). */
class DivineHeroArmorTest {
   @BeforeAll
   static void bootstrap() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   /** A real tagged damage source, as the datapack tags bind it in game. */
   @SafeVarargs
   private static DamageSource source(String id, TagKey<DamageType>... tags) {
      ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("viltrumitecore", id));
      Holder.Reference<DamageType> holder = Holder.Reference.createStandAlone(new HolderOwner<>() {
      }, key);
      holder.bindValue(new DamageType(id, 0.0F));
      holder.bindTags(List.of(tags));
      return new DamageSource(holder);
   }

   /** viltrumitecore:divine with the tags data/ gives it: the divine tag and the vanilla armor/shield/enchantment/effect bypasses. */
   private static DamageSource divine() {
      return source("divine", DivineDamage.DIVINE_TYPES, DamageTypeTags.BYPASSES_ARMOR, DamageTypeTags.BYPASSES_SHIELD,
         DamageTypeTags.BYPASSES_ENCHANTMENTS, DamageTypeTags.BYPASSES_EFFECTS);
   }

   private static DamageSource ordinary() {
      return source("ordinary");
   }

   private static IronManState inMark() {
      IronManState state = new IronManState();
      state.suit.startEquip(MarkId.MARK_7, true, 0);
      for (int i = 0; i < EquipTimeline.DELIVERY; i++) {
         state.suit.tick();
      }

      assertTrue(state.suit.markOn());
      return state;
   }

   private static IronManState inHulkbuster() {
      IronManState state = new IronManState();
      HulkbusterLayer layer = state.hulkbuster;
      assertTrue(layer.start());
      for (int i = 0; i < HulkbusterLayer.DROP_TICKS + HulkbusterLayer.ASSEMBLE_TICKS; i++) {
         layer.tick();
      }

      layer.activate();
      assertTrue(layer.active());
      return state;
   }

   @Test
   void divineDamagePassesTheWornMark() {
      IronManState state = inMark();
      float before = state.roster.durability(MarkId.MARK_7);
      assertSame(DamageAbsorb.PASS, IronManMarks.absorb(state, divine(), 5.0F));
      assertEquals(before, state.roster.durability(MarkId.MARK_7), "the mark is not worn down by a hit it let through");
   }

   @Test
   void ordinaryDamageStillHitsTheMark() {
      IronManState state = inMark();
      float before = state.roster.durability(MarkId.MARK_7);
      assertSame(DamageAbsorb.ABSORBED, IronManMarks.absorb(state, ordinary(), 5.0F));
      assertTrue(state.roster.durability(MarkId.MARK_7) < before);
   }

   @Test
   void divineDamagePassesTheHulkbuster() {
      IronManState state = inHulkbuster();
      float before = state.hulkbuster.durability();
      assertSame(DamageAbsorb.PASS, IronManHulkbuster.absorb(state, divine(), 5.0F));
      assertEquals(before, state.hulkbuster.durability());
      assertSame(DamageAbsorb.ABSORBED, IronManHulkbuster.absorb(state, ordinary(), 5.0F));
   }

   @Test
   void invulnerabilityBypassStillPassesBothLayers() {
      DamageSource kill = source("kill", DamageTypeTags.BYPASSES_INVULNERABILITY);
      assertSame(DamageAbsorb.PASS, IronManMarks.absorb(inMark(), kill, 5.0F));
      assertSame(DamageAbsorb.PASS, IronManHulkbuster.absorb(inHulkbuster(), kill, 5.0F));
   }
}
