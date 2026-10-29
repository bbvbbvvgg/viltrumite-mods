package dev.baranhan.viltrumitecore.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Shared code must not branch on a concrete hero id: hero behaviour goes
 * through HeroDefinition hooks. Hero-named places are allowed (hero/<id>/,
 * client/<id>/, client/mixin/<Hero>*, client/render/vfx/<Hero>*); the files
 * below predate the rule and are tolerated until they move behind hooks.
 */
class NoHeroBranchTest {
   private static final Path ROOT = Path.of("src/main/java/dev/baranhan/viltrumitecore");
   private static final Pattern BRANCH = Pattern.compile("HeroId\\.(VILTRUMITE|REGULUS|HOMELANDER|IRON_MAN|REINHARD|DIO|SINCLAIR|JINWOO)\\b");
   private static final Pattern HERO_PLACE = Pattern.compile(
      "(hero|client)/(regulus|homelander|viltrumite|ironman|reinhard|dio|sinclair|jinwoo)/.*|entity/jinwoo/.*|client/mixin/(Regulus|Homelander|IronMan|Reinhard|Dio|FirstPersonRegulus|FirstPersonHomelander|FirstPersonDio)\\w*\\.java|client/render/vfx/(Regulus|Homelander|IronMan|Reinhard)\\w*\\.java"
   );
   private static final Set<String> LEGACY = Set.of(
      "ability/ViltrumiteAbilities.java",
      "client/ViltrumiteCoreClient.java",
      "client/gui/RaceSelectionScreen.java",
      "client/mixin/GameRendererDashMixin.java",
      "client/mixin/SilhouetteRendererMixin.java",
      "hero/HeroDamage.java",
      "hero/HeroEvents.java",
      "hero/HeroId.java",
      "hero/ViltrumiteHero.java",
      "item/EvangeliumItem.java",
      "mixin/PlayerHeroMixin.java"
   );

   @Test
   void sharedCodeDoesNotBranchOnHeroIds() throws IOException {
      List<String> offenders;
      try (Stream<Path> files = Files.walk(ROOT)) {
         offenders = files.filter(p -> p.toString().endsWith(".java"))
            .filter(p -> {
               String rel = ROOT.relativize(p).toString().replace('\\', '/');
               if (HERO_PLACE.matcher(rel).matches() || LEGACY.contains(rel)) {
                  return false;
               }
               try {
                  return BRANCH.matcher(Files.readString(p)).find();
               } catch (IOException e) {
                  throw new RuntimeException(e);
               }
            })
            .map(p -> ROOT.relativize(p).toString())
            .sorted()
            .toList();
      }

      assertTrue(offenders.isEmpty(), "Hero-id branches in shared code (use a HeroDefinition hook): " + offenders);
   }
}
