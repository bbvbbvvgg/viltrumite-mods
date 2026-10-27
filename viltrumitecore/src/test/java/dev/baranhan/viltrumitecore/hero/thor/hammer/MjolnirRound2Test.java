package dev.baranhan.viltrumitecore.hero.thor.hammer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.thor.HammerState;
import dev.baranhan.viltrumitecore.hero.thor.ThorHands;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Round-2 pure rules of the world hammer: transform shaft, PvP spare, spawn protection, charged break pause, aim cone, grace prune, "in the hand". */
class MjolnirRound2Test {
   private static List<ThorTransform.Cell> column(ThorTransform.Cell... cells) {
      return new ArrayList<>(List.of(cells));
   }

   /** A-m9: only a column of natural ground is carved; a build, a fluid or a protected block anywhere means no shaft at all. */
   @Test
   void theTransformBoltCarvesOnlyNaturalGround() {
      ThorTransform.Cell n = ThorTransform.Cell.NATURAL;
      ThorTransform.Cell o = ThorTransform.Cell.OPEN;
      assertTrue(ThorTransform.shaft(column(n, n, o, n, n, n, n, o), 6), "a cave under natural rock (air gaps allowed)");
      assertFalse(ThorTransform.shaft(column(n, n, n, n, n), 6), "not deep enough");
      assertFalse(ThorTransform.shaft(column(n, n, n, n, n, n, ThorTransform.Cell.BUILT), 6), "a house on top: never drilled");
      assertFalse(ThorTransform.shaft(column(ThorTransform.Cell.BUILT, n, n, n, n, n, n), 6), "a ceiling of planks");
      assertFalse(ThorTransform.shaft(column(n, n, n, n, n, n, ThorTransform.Cell.FLUID, ThorTransform.Cell.FLUID), 6), "an ocean floor: never flooded");
      assertFalse(ThorTransform.shaft(column(n, n, n, ThorTransform.Cell.PROTECTED, n, n, n), 6), "a protected block: nothing carved");
      assertFalse(ThorTransform.shaft(column(o, o, o), 6));
   }

   /** A-m2: damage with no attacker spares players only while PvP is off. */
   @Test
   void ownerlessHammerDamageSparesPlayersWithPvpOff() {
      assertTrue(MjolnirImpact.spared(true, false, false));
      assertFalse(MjolnirImpact.spared(true, false, true));
      assertFalse(MjolnirImpact.spared(true, true, false), "a known attacker goes through vanilla's own PvP rule");
      assertFalse(MjolnirImpact.spared(false, false, false), "mobs are hit");
   }

   /** A-m8: the first-fall crater never touches the protected spawn square. */
   @Test
   void theFirstFallKeepsOutOfSpawnProtection() {
      int r = MjolnirTuning.CRATER_RADIUS;
      assertTrue(MjolnirFirstFall.spawnProtected(0, 0, 16, r));
      assertTrue(MjolnirFirstFall.spawnProtected(16 + r, -3, 16, r), "the crater edge reaches the square");
      assertFalse(MjolnirFirstFall.spawnProtected(16 + r + 1, 0, 16, r));
      assertFalse(MjolnirFirstFall.spawnProtected(0, 0, 0, r), "no protection configured");
   }

   /** A-m12: a charged throw waits at a breakable block when only this tick's budget is spent. */
   @Test
   void aChargedThrowWaitsInsteadOfStickingWhenTheTickBudgetIsSpent() {
      int per = MjolnirTuning.CHARGED_BREAK_PER_TICK;
      assertTrue(MjolnirFlight.pausesAtBlock(true, false, per, per));
      assertFalse(MjolnirFlight.pausesAtBlock(true, true, per, 2), "a protected block stops it");
      assertFalse(MjolnirFlight.pausesAtBlock(true, false, MjolnirTuning.CHARGED_BREAK_TOTAL, per), "the total is spent: it sticks");
      assertFalse(MjolnirFlight.pausesAtBlock(false, false, 0, 0), "a normal throw sticks");
   }

   /** A-m14: the cheap cone pre-filter agrees with the launch aim. */
   @Test
   void theAimConeFilterMatchesTheAim() {
      Vec3 origin = Vec3.ZERO;
      Vec3 look = new Vec3(0.0, 0.0, 1.0);
      assertTrue(MjolnirTargeting.inCone(origin, look, new Vec3(0.5, 0.0, 20.0)));
      assertFalse(MjolnirTargeting.inCone(origin, look, new Vec3(10.0, 0.0, 10.0)), "45 degrees off");
      assertFalse(MjolnirTargeting.inCone(origin, look, new Vec3(0.0, 0.0, MjolnirTuning.THROW_RANGE + 1.0)), "out of range");
      assertFalse(MjolnirTargeting.inCone(origin, Vec3.ZERO, new Vec3(0.0, 0.0, 5.0)));
      MjolnirTargeting.Candidate inside = new MjolnirTargeting.Candidate(1, new Vec3(0.5, 0.0, 20.0));
      assertEquals(inside, MjolnirTargeting.aim(origin, look, List.of(inside)));
   }

   /** A-N4: ended graces leave the table. */
   @Test
   void endedPullGracesArePruned() {
      Map<UUID, Long> graces = new HashMap<>();
      UUID old = UUID.randomUUID();
      UUID live = UUID.randomUUID();
      graces.put(old, 100L);
      graces.put(live, 300L);
      MjolnirPull.prune(graces, 200L);
      assertFalse(graces.containsKey(old));
      assertTrue(graces.containsKey(live));
   }

   /** A-M2 / X5: the wire-value twin of ThorBridge.hammerHeld (flight profile, flight arm pose). */
   @Test
   void mjolnirIsInTheHandOnlyWhenAttachedAndTheHandIsFree() {
      assertTrue(ThorHands.holds(HammerState.IN_HAND, true));
      assertFalse(ThorHands.holds(HammerState.IN_HAND, false), "an item in the right hand keeps it on the belt");
      for (HammerState state : HammerState.values()) {
         if (state != HammerState.IN_HAND) {
            assertFalse(ThorHands.holds(state, true), state.name());
         }
      }
   }

   /**
    * A-m6: the projected hammer on ThorState has one writer, session A's
    * store; besides it only the foundation's technical DRAW / HOLSTER
    * (ThorActions) and ThorState's own load / copy write the fields.
    */
   @Test
   void onlyTheStoreWritesTheProjectedHammer() throws IOException {
      Pattern write = Pattern.compile("\\.hammer(Detached)?\\s*=[^=]");
      Set<String> allowed = Set.of("MjolnirWorld.java", "ThorActions.java", "ThorState.java");
      Path root = Path.of("src/main/java/dev/baranhan/viltrumitecore");
      try (Stream<Path> files = Files.walk(root.resolve("hero"))) {
         for (Path file : (Iterable<Path>)files.filter(p -> p.toString().endsWith(".java"))::iterator) {
            if (!allowed.contains(file.getFileName().toString())) {
               assertFalse(write.matcher(Files.readString(file)).find(), file + " writes ThorState.hammer outside the store");
            }
         }
      }
   }
}
