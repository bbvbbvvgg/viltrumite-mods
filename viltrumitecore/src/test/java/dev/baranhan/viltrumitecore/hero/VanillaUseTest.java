package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.VanillaUse.Aim;
import dev.baranhan.viltrumitecore.hero.VanillaUse.Owner;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Thor B-B1: RMB arbitration between an opted-in hero and vanilla use. */
class VanillaUseTest {
   @Test
   void anItemInEitherHandIsAlwaysVanillasClick() {
      // Food, potions, bows, crossbows, shields, pearls, buckets, leads, blocks, armor: whatever it is, vanilla uses it.
      for (Aim aim : Aim.values()) {
         assertSame(Owner.VANILLA, VanillaUse.decide(aim, false), aim.name());
      }
   }

   @Test
   void blocksAndInteractiveEntitiesStayVanillaWithEmptyHands() {
      assertSame(Owner.VANILLA, VanillaUse.decide(Aim.BLOCK, true), "doors, buttons, chests, crafting tables");
      assertSame(Owner.VANILLA, VanillaUse.decide(Aim.INTERACTIVE_ENTITY, true), "villagers, horses, boats, item frames, pets, a lying Mjölnir");
   }

   @Test
   void theHeroOwnsOnlyTheAirAndEnemiesWithEmptyHands() {
      assertSame(Owner.HERO, VanillaUse.decide(Aim.NOTHING, true));
      assertSame(Owner.HERO, VanillaUse.decide(Aim.HOSTILE_ENTITY, true));
   }

   @Test
   void thorOptsInOutsideScenesAndTheClientSeamAsksIt() throws Exception {
      String thor = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/thor/ThorHero.java"));
      assertTrue(thor.contains("public boolean yieldsToVanillaUse(Player player)"));
      assertTrue(thor.contains("return !ThorScenes.claimsMouse(player);"), "a scene (grab throw) still owns RMB even facing a door (C-M15)");
      String mouse = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/client/hero/HeroMouseInput.java"));
      assertTrue(mouse.contains("hero.yieldsToVanillaUse(player)") && mouse.contains("VanillaUse.vanillaOwns("));
      String def = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/HeroDefinition.java"));
      assertTrue(def.contains("default boolean yieldsToVanillaUse(Player player) { return false; }"), "other heroes keep their claim unchanged");
   }

   @Test
   void interactiveEntityRuleCoversTheVanillaEmptyHandInteractions() throws Exception {
      String use = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/VanillaUse.java"));
      for (String type : new String[]{"AbstractVillager", "ArmorStand", "AbstractHorse", "Saddleable", "OwnableEntity", "HeroTargets.canHit"}) {
         assertTrue(use.contains(type), type);
      }
      assertTrue(use.contains("!(target instanceof LivingEntity)"), "every non-living entity keeps its own interaction");
      assertFalse(use.contains("Items."), "no per-item exception table");
   }
}
