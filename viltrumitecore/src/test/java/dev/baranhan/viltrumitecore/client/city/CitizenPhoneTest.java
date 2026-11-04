package dev.baranhan.viltrumitecore.client.city;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.city.citizen.CitizenLook;
import dev.baranhan.viltrumitecore.city.citizen.CitizenState;
import dev.baranhan.viltrumitecore.client.anim.pose.PoseRig;
import dev.baranhan.viltrumitecore.city.life.PhoneRoutine;
import dev.baranhan.viltrumitecore.city.life.PhoneRoutine.Kind;
import dev.baranhan.viltrumitecore.city.life.PhoneRoutine.Moment;
import dev.baranhan.viltrumitecore.city.life.PhoneRoutine.Phase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

/**
 * #151: the phone model sits in the posed hand (screen to the eyes when reading, at the ear for a call), its
 * texture is painted, the grip eases between both and the phone is wired into the renderer and the AI.
 */
class CitizenPhoneTest {
   private static final Path MAIN = Path.of("src/main/java/dev/baranhan/viltrumitecore");

   private static CitizenModel posed(int seed, Moment m) {
      CitizenLook look = CitizenLook.of(seed);
      CitizenModel model = new CitizenModel(CitizenModel.create(look.slim()).bakeRoot(), look.slim());
      model.setAllVisible(true);
      model.prepare(look);
      PoseRig.restorePivots(model);
      for (ModelPart p : new ModelPart[]{model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg}) {
         p.xRot = 0.0F;
         p.yRot = 0.0F;
         p.zRot = 0.0F;
      }
      float[] w = new float[CitizenState.values().length];
      w[CitizenState.IDLE.ordinal()] = 1.0F;
      CitizenPoser.apply(model, w, CitizenPoser.NONE, 0.0F, CitizenState.IDLE, 40.0F, seed, look, 0.0F, 0.0F, 40.0F, -1.0F, m, 1.0F);
      return model;
   }

   /** Phone centre and screen normal (pointing out of the screen) in model pixels. */
   private static Vector3f[] phone(CitizenModel model, Moment m) {
      PoseStack pose = new PoseStack();
      CitizenPhoneLayer.grip(pose, model, CitizenPoser.earGrip(m));
      Matrix4f mat = pose.last().pose();
      Vector4f c = mat.transform(new Vector4f(0.0F, 0.0F, 0.0F, 1.0F));
      Vector4f f = mat.transform(new Vector4f(0.0F, 0.0F, -1.0F, 1.0F));
      Vector3f centre = new Vector3f(c.x, c.y, c.z).mul(16.0F);
      Vector3f normal = new Vector3f(f.x - c.x, f.y - c.y, f.z - c.z).normalize();
      return new Vector3f[]{centre, normal};
   }

   /** A point of the head in model pixels (head space, y down, face at -z). */
   private static Vector3f head(CitizenModel model, float x, float y, float z) {
      PoseStack pose = new PoseStack();
      model.head.translateAndRotate(pose);
      Vector4f p = pose.last().pose().transform(new Vector4f(x / 16.0F, y / 16.0F, z / 16.0F, 1.0F));
      return new Vector3f(p.x, p.y, p.z).mul(16.0F);
   }

   @Test
   void readingHoldsTheScreenToTheEyesInFrontOfTheChest() {
      for (int seed : new int[]{1, 2, 77, 1234, -99}) {
         for (int v = 0; v < 8; v++) {
            Moment m = new Moment(Phase.LOOK, 30.0F, 120.0F, Kind.READ, v, 3, 44.0F);
            CitizenModel model = posed(seed, m);
            Vector3f[] ph = phone(model, m);
            Vector3f eyes = head(model, 0.0F, -4.0F, -4.0F);
            Vector3f toEyes = new Vector3f(eyes).sub(ph[0]);
            float dist = toEyes.length();
            float dot = toEyes.normalize().dot(ph[1]);
            assertTrue(dot > 0.75F, "screen faces the eyes, dot " + dot + " seed " + seed + " v " + v);
            assertTrue(dist > 5.0F && dist < 16.0F, "in front of the chest, " + dist + " px from the eyes");
            assertTrue(ph[0].z < -2.0F, "held in front of the body, z " + ph[0].z);
         }
      }
   }

   @Test
   void aCallHoldsThePhoneAtTheEar() {
      for (int seed : new int[]{1, 2, 77, 1234, -99}) {
         for (int v = 0; v < 8; v++) {
            Moment m = new Moment(Phase.TALK, 60.0F, 200.0F, Kind.CALL, v, 3, 120.0F);
            CitizenModel model = posed(seed, m);
            Vector3f[] ph = phone(model, m);
            // the right ear: the side of the head (model x -4), at eye height
            Vector3f ear = head(model, -4.0F, -4.0F, 0.0F);
            float d = new Vector3f(ph[0]).sub(ear).length();
            assertTrue(d < 3.0F, "phone at the ear, " + d + " px off, seed " + seed + " v " + v);
            Vector3f toHead = head(model, 0.0F, -4.0F, 0.0F).sub(ph[0]).normalize();
            assertTrue(toHead.dot(ph[1]) > 0.4F, "screen to the cheek " + toHead.dot(ph[1]));
         }
      }
   }

   @Test
   void theGripEasesBetweenReadingAndTheEar() {
      assertEquals(0.0F, CitizenPoser.earGrip(new Moment(Phase.TO_EAR, 0.0F, PhoneRoutine.TO_EAR, Kind.CALL, 0, 1, 0)), 1.0E-4F);
      assertEquals(1.0F, CitizenPoser.earGrip(new Moment(Phase.TO_EAR, PhoneRoutine.TO_EAR, PhoneRoutine.TO_EAR, Kind.CALL, 0, 1, 0)), 1.0E-4F);
      assertEquals(1.0F, CitizenPoser.earGrip(new Moment(Phase.TALK, 3.0F, 100.0F, Kind.CALL, 0, 1, 0)), 1.0E-4F);
      assertEquals(1.0F, CitizenPoser.earGrip(new Moment(Phase.FROM_EAR, 0.0F, PhoneRoutine.FROM_EAR, Kind.CALL, 0, 1, 0)), 1.0E-4F);
      assertEquals(0.0F, CitizenPoser.earGrip(new Moment(Phase.FROM_EAR, PhoneRoutine.FROM_EAR - 3.0F, PhoneRoutine.FROM_EAR, Kind.CALL, 0, 1, 0)), 1.0E-4F);
      float prev = 0.0F;
      for (float t = 0.0F; t <= PhoneRoutine.TO_EAR; t += 0.25F) {
         float k = CitizenPoser.earGrip(new Moment(Phase.TO_EAR, t, PhoneRoutine.TO_EAR, Kind.CALL, 0, 1, 0));
         assertTrue(k >= prev - 1.0E-5F && k - prev < 0.2F, "smooth, no jump");
         prev = k;
      }
   }

   @Test
   void theTextureIsPaintedWithCasesAndScreens() {
      int[] px = PhonePainter.paint();
      assertEquals(PhonePainter.W * PhonePainter.H, px.length);
      Set<Integer> screens = new HashSet<>();
      for (int s = 0; s < 8; s++) {
         StringBuilder sig = new StringBuilder();
         for (int y = 0; y < 32; y++) {
            for (int x = 0; x < PhonePainter.SCREEN_W; x++) {
               int c = px[(PhonePainter.SCREENS_Y + y) * PhonePainter.W + s * PhonePainter.SCREEN_W + x];
               assertEquals(0xFF, c >>> 24, "opaque");
               sig.append(c).append(',');
               if (PhonePainter.scrolls(s) && y < 16) {
                  assertEquals(c, px[(PhonePainter.SCREENS_Y + y + 16) * PhonePainter.W + s * PhonePainter.SCREEN_W + x], "seamless scroll");
               }
            }
         }
         screens.add(sig.toString().hashCode());
      }
      assertEquals(8, screens.size(), "every screen differs");
      Set<Integer> cases = new HashSet<>();
      for (int seed = 0; seed < 200; seed++) {
         int c = PhonePainter.caseOf(seed * 7919 + 13);
         assertTrue(c >= 0 && c < PhonePainter.CASES);
         cases.add(c);
      }
      assertTrue(cases.size() >= 6, "phones come in colours");
   }

   @Test
   void theScreenFollowsThePhase() {
      assertEquals(PhonePainter.OFF, PhonePainter.screenFor(new Moment(Phase.TALK, 50, 200, Kind.CALL, 0, 1, 0)));
      assertEquals(PhonePainter.DIAL, PhonePainter.screenFor(new Moment(Phase.DIAL, 5, 40, Kind.CALL, 0, 1, 0)));
      assertEquals(PhonePainter.CALL, PhonePainter.screenFor(new Moment(Phase.HANG_UP, 5, 12, Kind.CALL, 0, 1, 0)));
      assertEquals(PhonePainter.LOCK, PhonePainter.screenFor(new Moment(Phase.DRAW, 10, 14, Kind.READ, 0, 1, 0)));
      Set<Integer> looks = new HashSet<>();
      for (int v = 0; v < 8; v++) {
         Moment m = new Moment(Phase.LOOK, 40, 200, Kind.READ, v, 1, 0);
         int s = PhonePainter.screenFor(m);
         looks.add(s);
         for (float t = 0; t < 200; t += 3.7F) {
            float o = PhonePainter.scroll(s, new Moment(Phase.LOOK, t, 200, Kind.READ, v, 1, 0));
            assertTrue(o >= 0.0F && o < PhonePainter.SCREEN_H);
         }
      }
      assertEquals(4, looks.size(), "chat, feed, map, video");
      assertNotEquals(PhonePainter.scroll(PhonePainter.CHAT, new Moment(Phase.LOOK, 0, 200, Kind.READ, 0, 1, 0)),
         PhonePainter.scroll(PhonePainter.CHAT, new Moment(Phase.LOOK, 100, 200, Kind.READ, 0, 1, 0)), "the feed scrolls");
   }

   @Test
   void thePhoneIsWiredIntoTheRendererAndTheStreetAi() throws Exception {
      String renderer = Files.readString(MAIN.resolve("client/city/CitizenRenderer.java"));
      assertTrue(renderer.contains("addLayer(new CitizenPhoneLayer(this))"));
      String goals = Files.readString(MAIN.resolve("city/citizen/CitizenGoals.java"));
      assertTrue(goals.contains("this.c.onCall()"), "callers stop for the call");
      assertTrue(goals.contains("this.c.phone().active()"), "no chat while on the phone");
      String entity = Files.readString(MAIN.resolve("city/citizen/CitizenEntity.java"));
      assertTrue(entity.contains("PhoneRoutine.calm("));
      for (String f : new String[]{"client/city/CitizenPhoneLayer.java", "client/city/PhonePainter.java", "city/life/PhoneRoutine.java", "client/city/CitizenPoser.java"}) {
         String src = Files.readString(MAIN.resolve(f)).toLowerCase();
         assertFalse(src.contains("geckolib") || src.contains("playeranimator"), f + " uses the mod's own animation system only");
      }
   }
}
