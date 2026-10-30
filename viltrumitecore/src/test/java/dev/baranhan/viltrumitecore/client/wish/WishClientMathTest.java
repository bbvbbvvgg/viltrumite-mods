package dev.baranhan.viltrumitecore.client.wish;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.hero.wish.Wish;
import dev.baranhan.viltrumitecore.hero.wish.WishGrant;
import dev.baranhan.viltrumitecore.hero.wish.WishTimeline;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** The wish screen layout at every common GUI size, the camera path and the style table. */
class WishClientMathTest {
   /** Scaled GUI sizes: 640x480 at scale 2, 1280x720 at 3-4, 1920x1080 at 1-4, tiny windows. */
   private static final int[][] SIZES = {{320, 240}, {300, 200}, {427, 240}, {480, 270}, {640, 360}, {683, 384}, {854, 480}, {960, 540}, {1920, 1080},
      {240, 180}};

   @Test
   void layoutFitsEveryScreen() {
      for (int[] size : SIZES) {
         for (int count : new int[]{1, 10, 25}) {
            WishLayout l = new WishLayout(size[0], size[1], count);
            String at = size[0] + "x" + size[1] + " n=" + count;
            assertTrue(l.cardX >= 0 && l.cardX + l.cardW <= size[0], "card off screen " + at);
            assertTrue(l.cardW >= Math.min(160, size[0] - 2 * WishLayout.MARGIN), "card too narrow " + at);
            assertTrue(l.titleY >= 0 && l.titleY + 9 * l.titleScale <= l.listTop, "title overlaps the list " + at);
            assertTrue(l.listHeight() >= WishLayout.ROW, "no room for one card " + at);
            assertTrue(l.listBottom <= l.echoY && l.echoY + 9 <= l.buttonY, "echo overlaps " + at);
            assertTrue(l.buttonY + WishLayout.BUTTON_H <= size[1], "buttons off screen " + at);
            assertTrue(l.confirmX >= 0 && l.cancelX + l.buttonW <= size[0] && l.confirmX + l.buttonW <= l.cancelX, "buttons overlap " + at);
            // scrolled to the end the last card is fully visible; at the top the first is
            float max = l.maxScroll();
            assertTrue(l.rowY(count - 1, max) + WishLayout.ROW <= l.listBottom + 0.01F, "last card unreachable " + at);
            assertEquals(l.listTop, l.rowY(0, 0), 0.01F);
            assertEquals(0, l.clampScroll(-50));
            assertEquals(max, l.clampScroll(1.0E6F));
            // hit testing: the middle of every visible card, nothing in the gaps
            for (int i = 0; i < count; i++) {
               float y = l.rowY(i, max) + WishLayout.ROW / 2.0F;
               if (y > l.listTop && y < l.listBottom) {
                  assertEquals(i, l.rowAt(l.cardX + l.cardW / 2.0, y, max), "card " + i + " " + at);
               }
               float gap = l.rowY(i, 0) + WishLayout.ROW + WishLayout.GAP / 2.0F;
               if (gap > l.listTop && gap < l.listBottom) {
                  assertEquals(-1, l.rowAt(l.cardX + 5, gap, 0), "gap after " + i + " " + at);
               }
            }
            assertEquals(-1, l.rowAt(l.cardX - 1, l.listTop + 2, 0));
            // keyboard: scrolling to a card makes it fully visible
            for (int i = 0; i < count; i++) {
               float s = l.scrollToShow(i, 0);
               assertTrue(l.rowY(i, s) >= l.listTop - 0.01F && l.rowY(i, s) + WishLayout.ROW <= l.listBottom + 0.01F, "card " + i + " hidden " + at);
            }
         }
      }
   }

   @Test
   void fulfilledMarksOnlyTheUniqueHeroWish() {
      List<Wish> wishes = List.of(new Wish("time", "dio", "time", WishGrant.hero("dio")), new Wish("a", "ironman", "tech", WishGrant.hero("ironman")),
         new Wish("b", "ironman", "tech", WishGrant.hero("ironman")), new Wish("return", "human", "mortal", WishGrant.hero("human")));
      assertArrayEquals(new boolean[]{true, false, false, false}, WishLayout.fulfilled(wishes, "dio"));
      assertArrayEquals(new boolean[]{false, false, false, false}, WishLayout.fulfilled(wishes, "ironman"));
      assertArrayEquals(new boolean[]{false, false, false, true}, WishLayout.fulfilled(wishes, "human"));
      assertArrayEquals(new boolean[4], WishLayout.fulfilled(wishes, null));
   }

   @Test
   void cameraLeavesAndReturnsToTheEyeWithoutJumps() {
      Vec3 origin = new Vec3(10, 64, -3);
      Vec3 eye = origin.add(0, 1.62, 0);
      Vec3 look = new Vec3(0, 0, 1);
      Vec3[] start = WishCamera.blended(origin, 0, 0, eye, look);
      Vec3[] end = WishCamera.blended(origin, 0, WishTimeline.LENGTH, eye, look);
      assertTrue(start[0].distanceTo(eye) < 1.0E-6 && end[0].distanceTo(eye) < 1.0E-6, "camera starts and ends in the eye");
      Vec3 prev = start[0];
      Vec3 prevLook = start[1];
      for (float t = 0.25F; t <= WishTimeline.LENGTH; t += 0.25F) {
         Vec3[] shot = WishCamera.blended(origin, 0, t, eye, look);
         assertTrue(shot[0].distanceTo(prev) < 0.35, "camera jumps at " + t);
         assertTrue(shot[1].distanceTo(prevLook) < 0.35, "look target jumps at " + t);
         prev = shot[0];
         prevLook = shot[1];
         Vec3[] raw = WishCamera.shot(origin, 0, t);
         double d = Math.sqrt(Math.pow(raw[0].x - origin.x, 2) + Math.pow(raw[0].z - origin.z, 2));
         assertTrue(d > 2.0 && d < 6.0, "shot distance " + d + " at " + t);
      }
      // the first shot is in front of the player (facing +z at yaw 0)
      assertTrue(WishCamera.shot(origin, 0, 0)[0].z > origin.z);
   }

   @Test
   void everyStyleIsKnownAndItsSoundsExist() throws IOException {
      JsonObject sounds = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/viltrumitecore/sounds.json"))).getAsJsonObject();
      for (WishStyle style : WishStyle.values()) {
         assertEquals(style, WishStyle.of(style.key));
         for (String sound : new String[]{style.buildSound, style.climaxSound}) {
            if (sound != null) {
               assertTrue(sounds.has(sound), style + " uses a missing sound " + sound);
            }
         }
      }
      for (String key : new String[]{"time", "shadow", "thunder", "cards", "tech", "crimson", "holy", "still", "mortal"}) {
         assertEquals(key, WishStyle.of(key).key);
      }
      assertEquals(WishStyle.MORTAL, WishStyle.of("unknown"));
   }
}
