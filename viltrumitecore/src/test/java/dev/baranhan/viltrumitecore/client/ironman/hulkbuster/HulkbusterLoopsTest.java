package dev.baranhan.viltrumitecore.client.ironman.hulkbuster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.ironman.hulkbuster.HulkbusterLoops.Facts;
import dev.baranhan.viltrumitecore.client.ironman.hulkbuster.HulkbusterLoops.Kind;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkMove;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterMotion;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HulkbusterLoopsTest {
   private static Facts facts(HulkbusterLayer.Phase phase, HulkMove move, MovePhase mp, HulkbusterMotion.State motion, int ticks, int charge,
         int damage, double speed, boolean alive) {
      return new Facts(phase, move, mp, motion, ticks, charge, damage, speed, alive);
   }

   private static Facts active(HulkMove move, MovePhase mp, HulkbusterMotion.State motion) {
      return facts(HulkbusterLayer.Phase.ACTIVE, move, mp, motion, 0, 0, 0, 0.0, true);
   }

   @Test
   void nothingPlaysOutsideTheMachine() {
      for (Kind kind : Kind.values()) {
         assertEquals(0.0F, HulkbusterLoops.target(kind, null).volume());
         assertEquals(0.0F, HulkbusterLoops.target(kind, facts(HulkbusterLayer.Phase.NONE, null, MovePhase.INTRO,
            HulkbusterMotion.State.JET_RISE, 0, 3, 2, 0.3, true)).volume(), kind.name());
         assertEquals(0.0F, HulkbusterLoops.target(kind, facts(HulkbusterLayer.Phase.ACTIVE, HulkMove.CHARGED, MovePhase.INTRO,
            HulkbusterMotion.State.JET_RISE, 0, 3, 2, 0.3, false)).volume(), kind.name() + " dead");
      }
   }

   @Test
   void servoFollowsSpeed() {
      float idle = HulkbusterLoops.target(Kind.SERVO, active(null, MovePhase.INTRO, HulkbusterMotion.State.NONE)).volume();
      float run = HulkbusterLoops.target(Kind.SERVO, facts(HulkbusterLayer.Phase.ACTIVE, null, MovePhase.INTRO, HulkbusterMotion.State.NONE,
         0, 0, 0, 0.3, true)).volume();
      assertTrue(idle > 0.0F && run > idle);
   }

   @Test
   void thrustersOnlyInThrusterStatesAndStopOnCancel() {
      assertTrue(HulkbusterLoops.target(Kind.THRUSTER, active(null, MovePhase.INTRO, HulkbusterMotion.State.HOVER)).volume() > 0.0F);
      assertTrue(HulkbusterLoops.target(Kind.THRUSTER, active(HulkMove.JET_RAM, MovePhase.MAIN, HulkbusterMotion.State.NONE)).volume() > 0.0F);
      assertEquals(0.0F, HulkbusterLoops.target(Kind.THRUSTER, active(HulkMove.JET_RAM, MovePhase.FINISH, HulkbusterMotion.State.NONE)).volume());
      assertEquals(0.0F, HulkbusterLoops.target(Kind.THRUSTER, active(null, MovePhase.INTRO, HulkbusterMotion.State.NONE)).volume());
      assertEquals(0.0F, HulkbusterLoops.target(Kind.THRUSTER, active(null, MovePhase.INTRO, HulkbusterMotion.State.STAGGER)).volume());
   }

   @Test
   void chargeOnlyDuringTheWindUp() {
      assertTrue(HulkbusterLoops.target(Kind.CHARGE, active(HulkMove.CHARGED, MovePhase.INTRO, HulkbusterMotion.State.NONE)).volume() > 0.0F);
      assertTrue(HulkbusterLoops.target(Kind.CHARGE, active(HulkMove.HEAVY_REPULSORS, MovePhase.INTRO, HulkbusterMotion.State.NONE)).volume() > 0.0F);
      assertEquals(0.0F, HulkbusterLoops.target(Kind.CHARGE, active(HulkMove.CHARGED, MovePhase.MAIN, HulkbusterMotion.State.NONE)).volume());
      assertEquals(0.0F, HulkbusterLoops.target(Kind.CHARGE, active(HulkMove.COMBO, MovePhase.INTRO, HulkbusterMotion.State.NONE)).volume());
   }

   @Test
   void sparksGrowWithDamage() {
      float one = HulkbusterLoops.target(Kind.SPARK, facts(HulkbusterLayer.Phase.ACTIVE, null, MovePhase.INTRO, HulkbusterMotion.State.NONE,
         0, 0, 1, 0, true)).volume();
      float two = HulkbusterLoops.target(Kind.SPARK, facts(HulkbusterLayer.Phase.ACTIVE, null, MovePhase.INTRO, HulkbusterMotion.State.NONE,
         0, 0, 2, 0, true)).volume();
      assertTrue(one > 0.0F && two > one);
      assertEquals(0.0F, HulkbusterLoops.target(Kind.SPARK, active(null, MovePhase.INTRO, HulkbusterMotion.State.NONE)).volume());
   }

   @Test
   void fadeReachesTheTargetBothWays() {
      float v = 0.0F;
      for (int i = 0; i < 30; i++) {
         v = HulkbusterLoops.approach(v, 0.8F);
      }
      assertEquals(0.8F, v, 1e-6);
      for (int i = 0; i < 30; i++) {
         v = HulkbusterLoops.approach(v, 0.0F);
      }
      assertEquals(0.0F, v, 1e-6);
   }

   @Test
   void everyHulkbusterSoundHasItsAssetAndNoVanillaSound() throws Exception {
      String json = Files.readString(Path.of("src/main/resources/assets/viltrumitecore/sounds.json"));
      for (String event : new String[]{"step", "screech", "swing", "punch", "hammer", "jackhammer", "finish_punch", "charged_punch", "charge",
            "clamp", "block_hit", "repulsor_charge", "repulsor_beam", "dash", "cough", "land", "slam", "grab", "throw", "hop", "ult_ignite",
            "ult_impact", "refuse", "drop", "exit", "assemble", "break", "servo_loop", "thruster_loop", "charge_loop", "spark_loop"}) {
         assertTrue(json.contains("\"ironman_hulkbuster_" + event + "\""), event);
      }
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("viltrumitecore:ironman/hulkbuster/([a-z_0-9]+)").matcher(json);
      int files = 0;
      while (m.find()) {
         files++;
         assertTrue(Files.exists(Path.of("src/main/binassets/assets/viltrumitecore/sounds/ironman/hulkbuster/" + m.group(1) + ".ogg.b64")), m.group(1));
      }
      assertTrue(files >= 50, "files " + files);
      try (var paths = Files.list(Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/ironman/hulkbuster"))) {
         for (Path p : paths.toList()) {
            assertTrue(!Files.readString(p).contains("SoundEvents."), p + " uses a vanilla sound");
         }
      }
   }
}
