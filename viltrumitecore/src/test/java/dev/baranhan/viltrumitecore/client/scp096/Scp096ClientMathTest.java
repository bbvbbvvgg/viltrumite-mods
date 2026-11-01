package dev.baranhan.viltrumitecore.client.scp096;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.scp.scp096.Scp096State;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pure client logic of SCP-096: clip layers and their easing, the mono downmix, the original-sound lookup and pack. */
class Scp096ClientMathTest {
   private final float[] out = new float[Scp096Pose.SLOTS];

   private float[] targets(Scp096State s, Scp096State prev, float since, boolean fromSit, boolean face, boolean cover, boolean climb, boolean leap,
         float move, float air) {
      Scp096Pose.targets(s, prev, since, fromSit, face, cover, climb, leap, move, air, this.out);
      return this.out;
   }

   private static float layerSum(float[] w) {
      float sum = 0.0F;
      for (int i = 0; i < Scp096Pose.LAYERS; i++) {
         sum += w[i];
      }
      return sum;
   }

   @Test
   void everyStateWantsAFullPose() {
      for (Scp096State s : Scp096State.values()) {
         for (Scp096State prev : Scp096State.values()) {
            for (float since : new float[]{0.0F, 0.7F, 2.0F, 50.0F}) {
               for (float move : new float[]{0.0F, 0.03F, 0.6F}) {
                  for (int flags = 0; flags < 16; flags++) {
                     float[] t = targets(s, prev, since, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0, false, move, 0.0F);
                     assertEquals(1.0F, layerSum(t), 1.0E-5F, s + " after " + prev + " at " + since + "s move " + move + " flags " + flags);
                  }
               }
            }
         }
      }
   }

   @Test
   void transitionsPlayTheirOneShots() {
      assertEquals(1.0F, targets(Scp096State.STAND, Scp096State.SIT, 0.5F, false, false, false, false, false, 0, 0)[Scp096Pose.STAND_UP]);
      assertEquals(1.0F, targets(Scp096State.STAND, Scp096State.SIT, 2.0F, false, false, false, false, false, 0, 0)[Scp096Pose.STAND]);
      assertEquals(1.0F, targets(Scp096State.SIT, Scp096State.WANDER, 0.5F, false, false, false, false, false, 0, 0)[Scp096Pose.SIT_DOWN]);
      assertEquals(1.0F, targets(Scp096State.SIT, Scp096State.CALMING, 0.5F, false, false, false, false, false, 0, 0)[Scp096Pose.SIT],
         "the calm clip already ends seated");
      assertEquals(1.0F, targets(Scp096State.SIT, Scp096State.SIT, 9.0F, false, true, false, false, false, 0, 0)[Scp096Pose.PEEK]);
      assertEquals(1.0F, targets(Scp096State.TRIGGERED, Scp096State.SIT, 0.1F, true, true, false, false, false, 0, 0)[Scp096Pose.GAZE_SIT]);
      assertEquals(1.0F, targets(Scp096State.TRIGGERED, Scp096State.WANDER, 0.1F, false, true, false, false, false, 0, 0)[Scp096Pose.GAZE]);
      assertEquals(1.0F, targets(Scp096State.HYSTERIA, Scp096State.TRIGGERED, 0.3F, true, true, false, false, false, 0, 0)[Scp096Pose.RISE]);
      assertEquals(1.0F, targets(Scp096State.HYSTERIA, Scp096State.TRIGGERED, 1.6F, true, true, false, false, false, 0, 0)[Scp096Pose.HYSTERIA]);
      assertEquals(1.0F, targets(Scp096State.HYSTERIA, Scp096State.TRIGGERED, 0.3F, false, true, false, false, false, 0, 0)[Scp096Pose.HYSTERIA]);
      assertEquals(1.0F, targets(Scp096State.CALMING, Scp096State.CHASE, 1.0F, false, false, false, false, false, 0, 0)[Scp096Pose.CALM]);
      // a freshly tracked entity (prev == state) never replays a transition
      assertEquals(1.0F, targets(Scp096State.STAND, Scp096State.STAND, 0.0F, false, false, false, false, false, 0, 0)[Scp096Pose.STAND]);
   }

   @Test
   void theChaseRunsLeapsAndClimbs() {
      float[] run = targets(Scp096State.CHASE, Scp096State.HYSTERIA, 3.0F, false, true, false, false, false, 0.6F, 0);
      assertEquals(1.0F, run[Scp096Pose.RUN], 1e-6F);
      float[] wait = targets(Scp096State.CHASE, Scp096State.HYSTERIA, 3.0F, false, true, false, false, false, 0.0F, 0);
      assertEquals(1.0F, wait[Scp096Pose.RAGE], 1e-6F);
      assertEquals(1.0F, targets(Scp096State.CHASE, Scp096State.HYSTERIA, 3.0F, false, true, false, false, true, 0.6F, 0)[Scp096Pose.LEAP]);
      assertEquals(1.0F, targets(Scp096State.CHASE, Scp096State.HYSTERIA, 3.0F, false, true, false, false, false, 0.6F, 8)[Scp096Pose.LEAP],
         "falling off a roof uses the airborne pose too");
      assertEquals(1.0F, targets(Scp096State.CHASE, Scp096State.HYSTERIA, 3.0F, false, true, false, true, true, 0.6F, 8)[Scp096Pose.CLIMB],
         "climbing wins over the airborne pose");
      assertTrue(Scp096Pose.runWeight(0.07F) > 0.0F && Scp096Pose.runWeight(0.07F) < 1.0F, "a blend while accelerating");
      assertTrue(Scp096Pose.walkWeight(0.0F) == 0.0F && Scp096Pose.walkWeight(0.2F) == 1.0F);
   }

   @Test
   void easingIsFrameRateIndependentAndSnapsWhenAsked() {
      float[] t = targets(Scp096State.CHASE, Scp096State.HYSTERIA, 3.0F, false, true, false, false, false, 0.6F, 0).clone();
      float[] a = new float[Scp096Pose.SLOTS];
      float[] b = new float[Scp096Pose.SLOTS];
      a[Scp096Pose.HYSTERIA] = 1.0F;
      b[Scp096Pose.HYSTERIA] = 1.0F;
      for (int i = 0; i < 30; i++) {
         Scp096Pose.ease(a, t, Scp096State.CHASE, 1.0F / 30.0F, false);
      }
      for (int i = 0; i < 144; i++) {
         Scp096Pose.ease(b, t, Scp096State.CHASE, 1.0F / 144.0F, false);
      }
      for (int i = 0; i < Scp096Pose.SLOTS; i++) {
         assertEquals(a[i], b[i], 1.0E-3F, "slot " + i + " after one second at 30 and 144 fps");
      }
      assertTrue(a[Scp096Pose.RUN] > 0.99F, "the run is in within a second");
      float[] c = new float[Scp096Pose.SLOTS];
      Scp096Pose.ease(c, t, Scp096State.CHASE, 0.0F, true);
      assertTrue(Arrays.equals(c, t));
   }

   @Test
   void quaternionsRoundTripTheEngineEulerOrder() {
      java.util.Random r = new java.util.Random(96L);
      float[] q = new float[4];
      float[] back = new float[3];
      float[] q2 = new float[4];
      for (int i = 0; i < 500; i++) {
         float rx = (float)((r.nextDouble() * 2 - 1) * Math.PI);
         float ry = (float)((r.nextDouble() * 2 - 1) * Math.PI * 0.49);
         float rz = (float)((r.nextDouble() * 2 - 1) * Math.PI);
         Scp096Blend.quat(rx, ry, rz, q);
         Scp096Blend.euler(q, back);
         assertEquals(rx, back[0], 1.0E-3F);
         assertEquals(ry, back[1], 1.0E-3F);
         assertEquals(rz, back[2], 1.0E-3F);
         // the other Euler branch of the same orientation gives the same rotation (up to the quaternion sign)
         Scp096Blend.quat((float)(rx + Math.PI), (float)(Math.PI - ry), (float)(rz + Math.PI), q2);
         float dot = Math.abs(q[0] * q2[0] + q[1] * q2[1] + q[2] * q2[2] + q[3] * q2[3]);
         assertEquals(1.0F, dot, 1.0E-4F);
      }
   }

   @Test
   void blendingTwoBranchesOfOnePoseKeepsThePose() {
      // the arm of the sitting pose as the IK wrote it and its smaller twin: a 50/50 nlerp must stay on that pose
      float d = (float)(Math.PI / 180.0);
      float[] a = new float[4];
      float[] b = new float[4];
      Scp096Blend.quat(131.43F * d, -160.88F * d, -145.01F * d, a);
      Scp096Blend.quat(-48.57F * d, -19.12F * d, 34.99F * d, b);
      float[] mid = new float[4];
      Scp096Blend.nlerp(a, b, 0.5F, mid);
      float dot = Math.abs(a[0] * mid[0] + a[1] * mid[1] + a[2] * mid[2] + a[3] * mid[3]);
      assertEquals(1.0F, dot, 1.0E-3F, "no swing through the body");
      // and a real blend lands between the two orientations
      Scp096Blend.quat(0.0F, 0.0F, 0.0F, a);
      Scp096Blend.quat(1.0F, 0.0F, 0.0F, b);
      Scp096Blend.nlerp(a, b, 0.5F, mid);
      float[] e = new float[3];
      Scp096Blend.euler(mid, e);
      assertEquals(0.5F, e[0], 1.0E-4F);
      Scp096Blend.euler(new float[4], e);
      assertEquals(0.0F, e[0]);
   }

   @Test
   void clipTimesChainWithoutSeams() {
      for (int i = 0; i < Scp096Pose.LAYERS; i++) {
         double t = Scp096Pose.time(i, 100.0F, 1234.5F, 17.25, true, 3);
         assertTrue(t >= 0.0 && t <= Scp096Pose.LENGTH[i] + 1e-9, Scp096Pose.CLIP[i] + " time " + t);
      }
      // the one-shots hold their last frame, the hysteria loop starts where the rise ends
      assertEquals(Scp096Pose.LENGTH[Scp096Pose.STAND_UP], Scp096Pose.time(Scp096Pose.STAND_UP, 9.0F, 0, 0, false, 0), 1e-9);
      assertEquals(0.0, Scp096Pose.time(Scp096Pose.HYSTERIA, (float)Scp096Pose.LENGTH[Scp096Pose.RISE], 0, 0, true, 0), 1e-6);
      assertEquals(0.25 * Scp096Pose.LENGTH[Scp096Pose.RUN], Scp096Pose.time(Scp096Pose.RUN, 0, 0, 3.25, false, 0), 1e-9);
      assertEquals(Scp096Pose.CLIP.length, Scp096Pose.LAYERS);
      assertEquals(Scp096Pose.FILE.length, Scp096Pose.LAYERS);
      assertEquals(Scp096Pose.ONE_SHOT.length, Scp096Pose.LAYERS);
      assertEquals(0.0F, Scp096Pose.strike(-0.1, 0.6));
      assertEquals(0.0F, Scp096Pose.strike(0.6, 0.6));
      assertEquals(1.0F, Scp096Pose.strike(0.3, 0.6), 1e-6F);
   }

   @Test
   void stereoIsAveragedToMono() {
      ByteBuffer in = ByteBuffer.allocate(4 * 3 + 2).order(ByteOrder.LITTLE_ENDIAN);
      in.putShort((short)1000).putShort((short)3000);
      in.putShort((short)-32768).putShort((short)-32768);
      in.putShort((short)32767).putShort((short)-32767);
      in.putShort((short)5);
      in.flip();
      ByteBuffer out = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
      MonoDownmixStream.downmix(in, out);
      out.flip();
      assertEquals(3, out.remaining() / 2, "the trailing half frame is dropped");
      assertEquals(2000, out.getShort());
      assertEquals(-32768, out.getShort());
      assertEquals(0, out.getShort());
      assertEquals(2, in.remaining(), "the unread half frame stays in the input");
   }

   @Test
   void originalsAreFoundInEveryLayoutAndValidated(@TempDir Path dir) throws IOException {
      assertEquals(List.of("SFX/Music/096.ogg", "096.ogg", "SFX_Music_096.ogg"), Scp096OriginalSounds.candidates("SFX/Music/096.ogg"));
      assertTrue(Scp096OriginalSounds.looksLikeGame("SCP - Containment Breach v1.3.11"));
      assertTrue(Scp096OriginalSounds.looksLikeGame("scpcb"));
      assertTrue(Scp096OriginalSounds.looksLikeGame("SCP-CB Ultimate Edition"));
      assertFalse(Scp096OriginalSounds.looksLikeGame("Minecraft"));
      assertFalse(Scp096OriginalSounds.isOgg(new byte[]{'R', 'I', 'F', 'F'}));
      assertFalse(Scp096OriginalSounds.isOgg(null));
      Path game = dir.resolve("SCP - Containment Breach");
      Files.createDirectories(game.resolve("SFX/Music"));
      Files.createDirectories(game.resolve("SFX/SCP/096"));
      byte[] ogg = new byte[4096];
      ogg[0] = 'O';
      ogg[1] = 'g';
      ogg[2] = 'g';
      ogg[3] = 'S';
      Files.write(game.resolve("SFX/Music/096.ogg"), ogg);
      Files.write(game.resolve("SFX/SCP/096/Scream.ogg"), ogg);
      Files.write(game.resolve("SFX/Music/096Angered.ogg"), new byte[4096]);   // not an Ogg file
      Path flat = dir.resolve("flat");
      Files.createDirectories(flat);
      Files.write(flat.resolve("SFX_SCP_096_Triggered.ogg"), ogg);
      Files.write(flat.resolve("096Chase.ogg"), Arrays.copyOf(ogg, 100));     // too small
      Map<String, Path> found = Scp096OriginalSounds.locate(List.of(dir.resolve("missing"), flat, game));
      assertEquals(Set.of("cry", "scream", "triggered"), found.keySet());
      assertEquals(flat.resolve("SFX_SCP_096_Triggered.ogg"), found.get("triggered"));
      assertEquals(List.of(game), Scp096OriginalSounds.gameFolders(dir));
   }

   @Test
   void thePackReplacesOnlyTheFoundEvents() {
      JsonObject json = JsonParser.parseString(Scp096OriginalPack.soundsJson(Set.of("cry", "triggered", "chase"))).getAsJsonObject();
      assertEquals(Set.of("scp096.cry", "scp096.triggered", "scp096.chase_music"), json.keySet());
      JsonObject cry = json.getAsJsonObject("scp096.cry");
      assertTrue(cry.get("replace").getAsBoolean());
      assertEquals("subtitles.viltrumitecore.scp096.cry", cry.get("subtitle").getAsString());
      JsonObject sound = cry.getAsJsonArray("sounds").get(0).getAsJsonObject();
      assertEquals("viltrumitecore:scp096/original/cry", sound.get("name").getAsString());
      assertTrue(sound.get("stream").getAsBoolean());
      assertEquals(20, sound.get("attenuation_distance").getAsInt());
      assertFalse(json.getAsJsonObject("scp096.chase_music").has("subtitle"), "the music has no subtitle");
      assertFalse(json.getAsJsonObject("scp096.triggered").getAsJsonArray("sounds").get(0).getAsJsonObject().has("attenuation_distance"),
         "the sting is not positional");
      assertTrue(Scp096OriginalPack.attenuation("scream") > Scp096OriginalPack.attenuation("angered"));
   }

   @Test
   void loopsMusicAndCameraFollowTheState() {
      assertEquals(0, Scp096ClientSounds.loopFor(Scp096State.SIT));
      assertEquals(0, Scp096ClientSounds.loopFor(Scp096State.CALMING));
      assertEquals(1, Scp096ClientSounds.loopFor(Scp096State.HYSTERIA));
      assertEquals(2, Scp096ClientSounds.loopFor(Scp096State.CHASE));
      assertEquals(0.0F, Scp096ClientSounds.loopVolume(0, Scp096State.CALMING, 0));
      assertTrue(Scp096ClientSounds.loopVolume(0, Scp096State.CALMING, 60) > 0.8F);
      assertEquals(1.0F, Scp096ClientSounds.musicVolume(5.0));
      assertEquals(0.6F, Scp096ClientSounds.musicVolume(500.0));
      assertTrue(Scp096ClientSounds.musicVolume(40.0) < 1.0F && Scp096ClientSounds.musicVolume(40.0) > 0.6F);
      RandomSource r = RandomSource.create(7L);
      for (int i = 0; i < 50; i++) {
         assertTrue(Scp096ClientSounds.shotGap(2, r) < Scp096ClientSounds.shotGap(0, RandomSource.create(i)), "screams come faster than sobs");
      }
      assertEquals(0.0F, Scp096ClientSounds.fovZoom(0.0F, 0.0F, 3.0));
      float sting = Scp096ClientSounds.fovZoom(1.0F, 0.0F, 0.0);
      assertTrue(sting > 0.05F && sting < 0.1F, "the sting zooms in a little: " + sting);
      float lo = Scp096ClientSounds.fovZoom(0.0F, 1.0F, 5.4);
      float hi = Scp096ClientSounds.fovZoom(0.0F, 1.0F, 1.8);
      assertTrue(hi > lo, "the chase pulse breathes with a 7.2 s period");
      assertEquals(Scp096ClientSounds.fovZoom(0.0F, 1.0F, 1.0), Scp096ClientSounds.fovZoom(0.0F, 1.0F, 8.2), 1e-5F);
   }
}
