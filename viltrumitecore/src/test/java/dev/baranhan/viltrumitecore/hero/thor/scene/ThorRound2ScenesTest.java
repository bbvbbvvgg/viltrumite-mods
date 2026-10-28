package dev.baranhan.viltrumitecore.hero.thor.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Review round 2, session C: fatality vs Totem of Undying and the death
 * pipeline, collision-safe holding, teleport destination rules, client scene
 * expiry, grab refusals and the server-authority / lifecycle seams.
 */
class ThorRound2ScenesTest {
   private static final Path SCENE = Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/thor/scene");
   private static final Path CLIENT = Path.of("src/main/java/dev/baranhan/viltrumitecore/client/thor/scene");

   private static String read(Path dir, String file) throws Exception {
      return Files.readString(dir.resolve(file));
   }

   // --- C-B1: fatality + Totem of Undying ---

   @Test
   void aTotemThatSavesTheVictimMeansNoFatality() {
      FatalityTrigger trigger = new FatalityTrigger();
      UUID victim = UUID.randomUUID();
      UUID thor = UUID.randomUUID();
      trigger.mark(victim, thor, 100L);
      // The totem pops inside LivingEntity.hurt: vanilla never calls die(), so no death event claims the candidate.
      trigger.expire();
      assertNull(trigger.claim(victim, thor, 100L), "no death, no scene: the totem did its vanilla job and the victim lives");
   }

   @Test
   void theRealDeathOfThatTickStartsTheSceneOnce() {
      FatalityTrigger trigger = new FatalityTrigger();
      UUID victim = UUID.randomUUID();
      UUID thor = UUID.randomUUID();
      trigger.mark(victim, thor, 100L);
      assertEquals(thor, trigger.claim(victim, thor, 100L));
      assertNull(trigger.claim(victim, thor, 100L), "a second death event (or a second fatality attempt) claims nothing");
   }

   @Test
   void onlyThatThorsKillInThatTickClaimsTheCandidate() {
      FatalityTrigger trigger = new FatalityTrigger();
      UUID victim = UUID.randomUUID();
      UUID thor = UUID.randomUUID();
      trigger.mark(victim, thor, 100L);
      assertNull(trigger.claim(victim, UUID.randomUUID(), 100L), "another killer (lava, another player) kills normally");
      trigger.mark(victim, thor, 100L);
      assertNull(trigger.claim(victim, thor, 101L), "a stale mark from an earlier tick never starts a scene");
      trigger.mark(victim, thor, 100L);
      trigger.forget(thor);
      assertFalse(trigger.pending(victim), "a Thor who logs out leaves no candidate behind");
   }

   @Test
   void theSceneStartsInTheDeathEventNotInTheDamageEvent() throws Exception {
      String scenes = read(SCENE, "ThorScenes.java");
      String lethal = scenes.substring(scenes.indexOf("private static void onLethalHit"), scenes.indexOf("static boolean startFromDeath"));
      assertFalse(lethal.contains("FatalitySessions.start("), "LETHAL_HIT (before the totem check) only marks a candidate");
      assertTrue(lethal.contains("TRIGGER.mark("));
      String death = scenes.substring(scenes.indexOf("public static void onDeath"), scenes.indexOf("public static void onTravel"));
      assertTrue(death.contains("startFromDeath("), "the scene holds the real vanilla death (after checkTotemDeathProtection)");
      assertTrue(death.contains("event.setCanceled(true)"));
      assertTrue(scenes.contains("TRIGGER.expire()"), "unclaimed candidates expire every tick");
   }

   @Test
   void aDecidedDeathSurvivesNoRestartAndNoDimensionChange() throws Exception {
      String sessions = read(SCENE, "FatalitySessions.java");
      assertTrue(sessions.contains("putUUID(DOOMED_TAG"), "the decided death is written on the victim when the scene starts");
      assertTrue(sessions.contains("remove(DOOMED_TAG)"), "the one kill removes it");
      assertTrue(sessions.contains("static void recoverDoomed"), "a victim back from a restart or crash dies on login");
      assertTrue(sessions.contains("victimAnywhere(session)"), "an abort finds a victim that already left the scene's dimension");
      String scenes = read(SCENE, "ThorScenes.java");
      assertTrue(scenes.contains("EntityTravelToDimensionEvent"), "fatality participants cannot leave through a portal or a cross-dimension teleport");
      assertTrue(scenes.contains("ServerStoppingEvent") && scenes.contains("FatalitySessions.abortAll"), "a stopping server ends scenes before the save");
      assertTrue(scenes.contains("PlayerLoggedInEvent") && scenes.contains("recoverDoomed"));
   }

   // --- C-B2: collision-safe holding ---

   /** A wall at x >= 2 (blocks the line of sight and the box). */
   private static SceneSpace.Probe wallAt(double wallX, double halfWidth) {
      return new SceneSpace.Probe() {
         @Override
         public boolean free(Vec3 feet) {
            return feet.x + halfWidth < wallX;
         }

         @Override
         public double reach(Vec3 from, Vec3 to) {
            if (to.x < wallX) {
               return 1.0;
            }

            return (wallX - from.x) / (to.x - from.x);
         }
      };
   }

   @Test
   void aHeldBodyIsPulledBackFromTheWallNeverThrough() {
      Vec3 chest = new Vec3(0.0, 1.1, 0.0);
      Vec3 near = new Vec3(0.65, 0.5, 0.0);
      Vec3 wanted = new Vec3(3.0, 0.5, 0.0);
      Vec3 placed = SceneSpace.place(wallAt(2.0, 0.3), chest, near, wanted, 0.9);
      assertNotNull(placed);
      assertTrue(placed.x + 0.3 < 2.0, "the body's box stays in front of the wall");
      assertTrue(placed.x > near.x, "and as close to the wanted point as the wall allows");
   }

   @Test
   void aBodyBehindAThinWallIsNeverPlacedThere() {
      // A one-block wall between 1.5 and 2.5: the far side is free but unseen.
      SceneSpace.Probe thin = new SceneSpace.Probe() {
         @Override
         public boolean free(Vec3 feet) {
            return feet.x + 0.3 < 1.5 || feet.x - 0.3 > 2.5;
         }

         @Override
         public double reach(Vec3 from, Vec3 to) {
            return to.x < 1.5 ? 1.0 : (1.5 - from.x) / (to.x - from.x);
         }
      };
      Vec3 placed = SceneSpace.place(thin, new Vec3(0, 1.1, 0), new Vec3(0.65, 0.5, 0), new Vec3(3.2, 0.5, 0), 0.9);
      assertNotNull(placed);
      assertTrue(placed.x < 1.5, "never on the other side of the wall");
   }

   @Test
   void noFreePointAtAllIsReportedNotForced() {
      SceneSpace.Probe solid = new SceneSpace.Probe() {
         @Override
         public boolean free(Vec3 feet) {
            return false;
         }

         @Override
         public double reach(Vec3 from, Vec3 to) {
            return 0.0;
         }
      };
      assertNull(SceneSpace.place(solid, Vec3.ZERO, new Vec3(0.6, 0, 0), new Vec3(2, 0, 0), 0.9), "the grab degrades (last safe point / end), no noclip");
   }

   @Test
   void everyGrabMovementGoesThroughTheSafePlacement() throws Exception {
      String grab = read(SCENE, "GrabSessions.java");
      assertTrue(grab.contains("SceneSpace.placeHeld("), "hold / drag uses the collision-safe placement");
      assertFalse(grab.contains("GrabPose.at(GrabFlow.Stage.ACTION"), "releases start from the last safe point, not a raw pose");
      assertTrue(grab.contains("settle(session, victim)"), "a released body never stays inside blocks");
      assertTrue(read(SCENE, "ThorSceneView.java").contains("SceneSpace.placeHeld("), "clients draw the same corrected point");
      assertTrue(read(SCENE, "SceneFx.java").contains("SLAM_MAX_DROP"), "the slam never teleports a body far down");
   }

   // --- M11, MINOR-1: grab refusals ---

   @Test
   void bossesMultipartAndGiantsAreRefused() {
      assertTrue(GrabSessions.tooBig(true, false, 0.9, 3.5 - 1.0));
      assertTrue(GrabSessions.tooBig(false, true, 1.0, 1.0), "multipart (Ender Dragon)");
      assertTrue(GrabSessions.tooBig(false, false, 2.5, 2.0), "wider than the limit (giant adaptations are OPEN)");
      assertTrue(GrabSessions.tooBig(false, false, 1.0, 3.5));
      assertFalse(GrabSessions.tooBig(false, false, 0.6, 1.8), "a player-sized body is grabbed");
   }

   @Test
   void playersFollowPvpCreativeAndTeamRules() {
      assertTrue(GrabSessions.playerGrabbable(false, true, true));
      assertFalse(GrabSessions.playerGrabbable(true, true, true), "creative");
      assertFalse(GrabSessions.playerGrabbable(false, false, true), "PvP off");
      assertFalse(GrabSessions.playerGrabbable(false, true, false), "friendly fire off / team");
   }

   // --- M3: teleport destination ---

   @Test
   void neverOnTheNetherRoofNorOutsideTheBuildHeight() {
      // Nether: min 0, max 256, logical height 128 (bedrock roof at y 123..127).
      assertTrue(TeleportRules.heightAllowed(64.0, 1.8, 0, 256, true, 128));
      assertFalse(TeleportRules.heightAllowed(128.0, 1.8, 0, 256, true, 128), "on top of the bedrock ceiling");
      assertFalse(TeleportRules.heightAllowed(200.0, 1.8, 0, 256, true, 128));
      assertTrue(TeleportRules.heightAllowed(200.0, 1.8, -64, 320, false, 384), "overworld sky is fine");
      assertFalse(TeleportRules.heightAllowed(319.0, 1.8, -64, 320, false, 384), "the body must fit under the build limit");
      assertFalse(TeleportRules.heightAllowed(-70.0, 1.8, -64, 320, false, 384));
   }

   @Test
   void hurtingFloorsAreNotLandingSpots() {
      Predicate<BlockPathTypes> hazard = TeleportRules::hazardous;
      assertTrue(hazard.test(BlockPathTypes.LAVA));
      assertTrue(hazard.test(BlockPathTypes.DAMAGE_FIRE), "magma, campfire, fire");
      assertTrue(hazard.test(BlockPathTypes.DAMAGE_OTHER), "cactus");
      assertTrue(hazard.test(BlockPathTypes.DAMAGE_CAUTIOUS), "sweet berry bush");
      assertTrue(hazard.test(BlockPathTypes.POWDER_SNOW));
      assertFalse(hazard.test(BlockPathTypes.WALKABLE));
   }

   @Test
   void theTeleportPickIsJudgedAgainOnTheServer() throws Exception {
      String tp = read(SCENE, "TeleportSessions.java");
      assertTrue(tp.contains("ClipContext.Fluid.ANY"), "a lake bottom is not what the camera sees");
      assertTrue(tp.contains("hazard(level, pos)") && tp.contains("heightAllowed("));
      assertFalse(tp.contains("setNoGravity"), "C-M5: no NoGravity flag that could stick to a save");
      assertTrue(tp.contains("TeleportRules.expired("), "bounded selection");
      String scenes = read(SCENE, "ThorScenes.java");
      String attack = scenes.substring(scenes.indexOf("public static void onAttack"), scenes.indexOf("static boolean bound("));
      assertFalse(attack.contains("TeleportSessions"), "C-M4: no free invulnerability while choosing a point (not in the canon)");
   }

   // --- M6: server authority over the fatality ---

   @Test
   void theServerOwnsThorsScenePosition() throws Exception {
      String sessions = read(SCENE, "FatalitySessions.java");
      assertTrue(sessions.contains("THOR_DRIFT") && sessions.contains("connection.teleport("), "a client off the plan is put back");
      assertTrue(sessions.contains("pathClear("), "no scene whose path crosses blocks");
      assertTrue(sessions.contains("stepsWithin("), "MINOR-14: no intro step over the limit");
      assertTrue(sessions.contains("stopRiding()"), "M2: no body in a saddle");
   }

   // --- M10: client scene copies expire ---

   @Test
   void clientSceneCopiesExpireWithoutSyncs() {
      SceneSync grab = SceneSync.grab(1, 2, new GrabFlow(null));
      assertFalse(ThorSceneView.expired(grab, 10));
      assertTrue(ThorSceneView.expired(grab, ThorSceneView.STALE_TICKS + 1), "a lost END never glues a body to Thor's hand");
   }

   // --- M9: ducking ---

   @Test
   void theFatalityDucksTheBackgroundNotItsOwnSounds() throws Exception {
      String client = read(CLIENT, "ThorSceneClient.java");
      assertFalse(client.contains("SoundSource.MASTER, 0.25F"), "MASTER would duck the scene too");
      assertFalse(client.substring(client.indexOf("DUCKED = {"), client.indexOf("};", client.indexOf("DUCKED = {"))).contains("PLAYERS"),
         "scene sounds play on PLAYERS");
   }
}
