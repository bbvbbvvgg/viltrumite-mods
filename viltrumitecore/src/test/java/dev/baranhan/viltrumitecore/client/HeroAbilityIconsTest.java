package dev.baranhan.viltrumitecore.client;

import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.hero.LegacyKit;
import dev.baranhan.viltrumitecore.hero.homelander.HomelanderAbilities;
import dev.baranhan.viltrumitecore.hero.ironman.IronManAbilities;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Every icon a hero's panel or flight bar can show resolves to a shipped house-style texture
 * (tools/assets/make_hero_icons.py): 64x64 with alpha, transparent outside the rounded frame,
 * opaque inside. Reinhard keeps his own test (ReinhardAssetsParseTest).
 */
class HeroAbilityIconsTest {
   @Test
   void homelanderDrawsEveryOwnedSlotIncludingTheFlightBar() throws IOException {
      String[] ids = {
         LegacyKit.PUNCH, LegacyKit.DASH, HomelanderAbilities.LASERS, HomelanderAbilities.FOCUS, HomelanderAbilities.ROAR,
         LegacyKit.THUNDERCLAP, "viltrumite:fast_takeoff", "viltrumite:supersonic_flight"
      };
      for (String id : ids) {
         ResourceLocation icon = HomelanderAbilities.icon(id);
         assertNotNull(icon, "Homelander has no icon for " + id);
         assertTrue(icon.getPath().startsWith("textures/gui/ability/homelander/"), id + " -> " + icon);
         assertHouseIcon(icon);
      }
      assertNull(HomelanderAbilities.icon("viltrumite:speed_lock"), "speed lock is not his");
   }

   @Test
   void ironManKitSignaturesAndHulkbusterIconsExist() throws IOException {
      List<ResourceLocation> icons = new ArrayList<>();
      for (String id : IronManAbilities.slotIds()) {
         icons.add(IronManAbilities.icon(id));
      }
      for (MarkId mark : MarkId.values()) {
         icons.add(IronManAbilities.signatureIcon(mark));
      }
      for (String hulk : new String[]{"hulk_grab", "hulk_slam", "hulk_hop"}) {
         icons.add(new ResourceLocation("viltrumitecore", "textures/gui/ability/ironman/" + hulk + ".png"));
      }
      assertEquals(18, icons.size());
      for (ResourceLocation icon : icons) {
         assertHouseIcon(icon);
      }
   }

   @Test
   void regulusIconsAreHouseIcons() throws IOException {
      for (String name : new String[]{"lions_heart", "debris_kick", "mania", "greeds_embrace", "counter", "punch"}) {
         assertHouseIcon(new ResourceLocation("viltrumitecore", "textures/gui/ability/regulus/" + name + ".png"));
      }
   }

   private static void assertHouseIcon(ResourceLocation icon) throws IOException {
      assertNotNull(icon);
      String path = "/assets/" + icon.getNamespace() + "/" + icon.getPath();
      try (InputStream stream = HeroAbilityIconsTest.class.getResourceAsStream(path)) {
         assertNotNull(stream, "missing texture: " + path);
         BufferedImage image = ImageIO.read(stream);
         assertNotNull(image, "unreadable texture: " + path);
         assertEquals(64, image.getWidth(), path + " width");
         assertEquals(64, image.getHeight(), path + " height");
         assertTrue(image.getColorModel().hasAlpha(), path + " has no alpha");
         for (int[] corner : new int[][]{{0, 0}, {63, 0}, {0, 63}, {63, 63}}) {
            assertEquals(0, image.getRGB(corner[0], corner[1]) >>> 24, path + " corner must stay transparent");
         }
         assertEquals(255, image.getRGB(32, 32) >>> 24, path + " centre must be opaque");
      }
   }
}
