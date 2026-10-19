package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The icon pipeline contract (2026-10-19 rework): every shipped ability and every mode has
 * a 16×16 / 12×12 tile with a transparent cut corner, an opaque dark plate and enough
 * luminance contrast that the figure reads at native size. The files are the committed
 * {@code .png.b64} assets (the Gradle decode task turns them into resources), so this checks
 * what ships, not what the generator intended.
 */
class ReinhardIconContractTest {
   private static final Path BINASSETS = Path.of("src/main/binassets/assets/viltrumitecore/textures/gui");
   private static final double MIN_STDDEV = 40.0;
   private static final double MIN_BRIGHT_SHARE = 0.03;
   private static final double MAX_MEAN_LUMA = 150.0;

   private static BufferedImage load(String relative) throws IOException {
      Path file = BINASSETS.resolve(relative + ".png.b64");
      assertTrue(Files.isRegularFile(file), "missing icon " + file);
      byte[] png = Base64.getMimeDecoder().decode(Files.readString(file, StandardCharsets.US_ASCII).trim());
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
      assertTrue(image != null, "not a PNG: " + file);
      return image;
   }

   private static double luma(int rgb) {
      int r = rgb >> 16 & 0xFF;
      int g = rgb >> 8 & 0xFF;
      int b = rgb & 0xFF;
      return 0.2126 * r + 0.7152 * g + 0.0722 * b;
   }

   private static void checkTile(String relative, int size) throws IOException {
      BufferedImage icon = load(relative);
      assertEquals(size, icon.getWidth(), relative + " width");
      assertEquals(size, icon.getHeight(), relative + " height");
      // Cut corners: transparent, the plate itself opaque.
      for (int[] corner : new int[][]{{0, 0}, {size - 1, 0}, {0, size - 1}, {size - 1, size - 1}}) {
         assertEquals(0, icon.getRGB(corner[0], corner[1]) >>> 24, relative + " corner alpha");
      }
      assertEquals(255, icon.getRGB(1, 1) >>> 24, relative + " plate alpha");

      int count = 0;
      double sum = 0;
      double squares = 0;
      int bright = 0;
      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            int argb = icon.getRGB(x, y);
            if (argb >>> 24 == 0) {
               continue;
            }

            double l = luma(argb);
            count++;
            sum += l;
            squares += l * l;
            if (l > 170.0) {
               bright++;
            }
         }
      }

      double mean = sum / count;
      double stddev = Math.sqrt(Math.max(0.0, squares / count - mean * mean));
      assertTrue(stddev >= MIN_STDDEV, relative + " contrast: sd " + stddev);
      assertTrue(bright / (double) count >= MIN_BRIGHT_SHARE, relative + " bright share " + bright / (double) count);
      // A washed-out tile (the old white plates) has a high mean and almost no spread.
      assertTrue(mean <= MAX_MEAN_LUMA, relative + " mean luma " + mean);
   }

   @Test
   void everyAbilityIconShipsAtPanelSize() throws IOException {
      for (ReinhardAbility ability : ReinhardAbility.values()) {
         checkTile("ability/reinhard/" + ability.key(), 16);
      }

      checkTile("ability/reinhard/reid_drawn", 16);
   }

   @Test
   void everyModeIconShipsAtHudSize() throws IOException {
      for (String mode : new String[]{"restrained", "knight", "holy_sword"}) {
         checkTile("reinhard/mode_" + mode, 12);
      }
   }

   @Test
   void everyAbilityHasItsOwnArt() throws IOException {
      Set<Integer> hashes = new HashSet<>();
      for (ReinhardAbility ability : ReinhardAbility.values()) {
         BufferedImage icon = load("ability/reinhard/" + ability.key());
         int hash = 1;
         for (int y = 0; y < icon.getHeight(); y++) {
            for (int x = 0; x < icon.getWidth(); x++) {
               hash = hash * 31 + icon.getRGB(x, y);
            }
         }

         assertTrue(hashes.add(hash), "duplicate art: " + ability.key());
      }
   }
}
