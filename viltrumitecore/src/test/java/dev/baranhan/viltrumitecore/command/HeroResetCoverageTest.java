package dev.baranhan.viltrumitecore.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

/**
 * Final integration: /hero cooldown reset (#87) reaches the own timers of every hero that keeps them outside
 * PersistentCooldowns, including the heroes reworked or added in parallel (#86 Thor v3, #89 Regulus Reborn,
 * #90 DIO, #91/#92 Sinclair). Human and the Viltrumite use only the shared (persistent / legacy) timers.
 */
class HeroResetCoverageTest {
   @Test
   void everyHeroWithOwnTimersOverridesTheReset() throws Exception {
      List<String> heroes = List.of(
         "dev.baranhan.viltrumitecore.hero.regulus.RegulusHero",
         "dev.baranhan.viltrumitecore.hero.homelander.HomelanderHero",
         "dev.baranhan.viltrumitecore.hero.ironman.IronManHero",
         "dev.baranhan.viltrumitecore.hero.reinhard.ReinhardHero",
         "dev.baranhan.viltrumitecore.hero.thor.ThorHero",
         "dev.baranhan.viltrumitecore.hero.dio.DioHero",
         "dev.baranhan.viltrumitecore.hero.sinclair.SinclairHero",
         "dev.baranhan.viltrumitecore.hero.jinwoo.JinwooHero");
      ClassLoader loader = HeroResetCoverageTest.class.getClassLoader();
      for (String name : heroes) {
         Class<?> hero = Class.forName(name, false, loader);
         assertEquals(hero, hero.getMethod("resetCooldowns", ServerPlayer.class).getDeclaringClass(),
            name + " must reset its own cooldowns");
         assertEquals(boolean.class, HeroDefinition.class.getMethod("resetCooldowns", ServerPlayer.class).getReturnType());
      }
   }
}
