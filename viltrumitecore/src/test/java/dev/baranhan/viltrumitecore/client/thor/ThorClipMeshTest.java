package dev.baranhan.viltrumitecore.client.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationController;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationParser;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoPose;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * The rig of the clip body check itself (PR #74 CI fix): Mjölnir is placed as ThorRenderer draws it, the
 * draw / holster switch follows the server's contact tick, every clip has a hammer place, and the scan
 * still sees a forearm driven into the chest.
 */
class ThorClipMeshTest {
   private static BakedGeoModel body() throws Exception {
      return ThorClipRig.load(ThorClipChecks.BODY);
   }

   @Test
   void idleWithTheBeltHammerIsClean() throws Exception {
      BakedGeoModel body = body();
      BakedGeoModel belt = ThorClipRig.load(ThorClipRig.GEO.resolve("thor/mjolnir_belt.geo.json"));
      Vector3f grip = ThorClipMesh.beltGrip(body, belt);
      Animation idle = AnimationParser.parse(JsonParser.parseString(Files.readString(ThorClipContract.FILE)).getAsJsonObject()).get("idle");
      AnimationController.seek(idle, body, 0.0);
      List<ThorClipMesh.Hit> hits = ThorClipMesh.scan(body, null, belt, grip);
      assertTrue(hits.isEmpty(), () -> "idle: " + hits);
   }

   @Test
   void theHandHammerHangsOnTheGripLocator() throws Exception {
      BakedGeoModel body = body();
      body.resetBones();
      BakedGeoModel hand = ThorClipRig.load(ThorClipChecks.HAMMER);
      Vector3f grip = GeoPose.point(body.getBone(ThorAssets.MJOLNIR_GRIP));
      Map<String, ThorClipMesh.Part> parts = ThorClipMesh.hammerParts(hand, GeoPose.pivotFrame(body.getBone(ThorAssets.MJOLNIR_GRIP)), "mjolnir");
      float nearest = Float.POSITIVE_INFINITY;
      for (Vector3f p : parts.get("handle").points()) {
         nearest = Math.min(nearest, p.distance(grip) * 16.0F);
      }

      // The fist closes on the shaft: the handle passes within a couple of px of the grip point.
      assertTrue(nearest < 2.0F, "handle is " + nearest + " px from mjolnir_grip");
   }

   @Test
   void aForearmDrivenIntoTheChestIsCaught() throws Exception {
      BakedGeoModel body = body();
      body.resetBones();
      // Shift the left forearm (with the fist) bodily onto the belly: deep inside the torso.
      GeoBone forearm = body.getBone("left_forearm");
      Vector3f delta = GeoPose.point(body.getBone("belly")).sub(GeoPose.point(forearm));
      forearm.posX = -delta.x * 16.0F;
      forearm.posY = delta.y * 16.0F;
      forearm.posZ = delta.z * 16.0F;
      List<ThorClipMesh.Hit> hits = ThorClipMesh.scan(body, null, null, null);
      assertFalse(hits.isEmpty(), "a forearm pushed into the belly must be a hit");
      assertTrue(hits.stream().anyMatch(h -> h.moving().startsWith("body:left_")), () -> hits.toString());
   }

   @Test
   void drawAndHolsterSwitchOnTheContactTick() {
      int draw = dev.baranhan.viltrumitecore.hero.thor.combat.ThorAttacks.envelope(dev.baranhan.viltrumitecore.hero.thor.ThorMove.DRAW).phases().contactTick();
      assertEquals(ThorClipChecks.Place.BELT, ThorClipChecks.place("draw", draw - 1));
      assertEquals(ThorClipChecks.Place.HAND, ThorClipChecks.place("draw", draw));
      assertEquals(ThorClipChecks.Place.BELT, ThorClipChecks.place("guard_fist", 0));
      assertEquals(ThorClipChecks.Place.HAND, ThorClipChecks.place("guard_hammer", 0));
      assertEquals(ThorClipChecks.Place.HAND, ThorClipChecks.place("holster", 13));
      assertEquals(ThorClipChecks.Place.BELT, ThorClipChecks.place("holster", 14));
      assertEquals(ThorClipChecks.Place.HAND, ThorClipChecks.place("catch", 0));
      assertEquals(ThorClipChecks.Place.AWAY, ThorClipChecks.place("throw_release", 0));
      assertEquals(ThorClipChecks.Place.BELT, ThorClipChecks.place("fist_jab", 0));
   }

   @Test
   void everyAuthoredClipHasAHammerPlace() throws Exception {
      for (String name : AnimationParser.parse(JsonParser.parseString(Files.readString(ThorClipContract.FILE)).getAsJsonObject()).keySet()) {
         ThorClipChecks.place(name, 0);
      }
   }
}
