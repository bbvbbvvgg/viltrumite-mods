package dev.baranhan.viltrumitecore.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.entity.SummonOwners;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriChariot;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriLeviathan;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriMob;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriSoldier;
import dev.baranhan.viltrumitecore.entity.thanos.ThanosEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.entity.monster.Enemy;
import org.junit.jupiter.api.Test;

/**
 * Guards of the final integration of #101 (Thanos), #99 (Chitauri) and #98 (Iron Legion): the
 * cross-system rules that no single branch could test.
 */
class FinalIntegrationCompatTest {
   private static final Path SRC = Path.of("src/main/java/dev/baranhan/viltrumitecore");

   @Test
   void everyInvaderAndThanosIsAnEnemyForHeroTargeting() {
      // Avengers, the Iron Legion, golems and hero homing all treat Enemy as hostile by default.
      for (Class<?> type : new Class<?>[]{ChitauriSoldier.class, ChitauriChariot.class, ChitauriLeviathan.class}) {
         assertTrue(Enemy.class.isAssignableFrom(type), type.getSimpleName() + " must be an Enemy");
         assertTrue(ChitauriMob.class.isAssignableFrom(type), type.getSimpleName() + " must be a ChitauriMob");
      }
      assertTrue(Enemy.class.isAssignableFrom(ThanosEntity.class), "Thanos must be an Enemy");
   }

   @Test
   void nothingIsASummonOfNobody() {
      assertFalse(SummonOwners.isSummon(null));
      assertTrue(SummonOwners.ownerOf(null) == null);
   }

   @Test
   void summonsOfOtherHeroesFollowTheirOwnersRules() throws IOException {
      String legion = Files.readString(SRC.resolve("hero/ironman/legion/LegionAllies.java"));
      assertTrue(legion.contains("SummonOwners.isSummon(target)"), "Legion suits must judge another hero's summon by its owner");
      assertTrue(legion.contains("SummonOwners.ownerOf(attacker)"), "a summon's hit on a suit must follow its owner's PvP rule");
      String captain = Files.readString(SRC.resolve("hero/captain/CaptainTargets.java"));
      assertTrue(captain.contains("SummonOwners.isSummon(e)"), "Captain and his Avengers must judge another hero's summon by its owner");
   }

   @Test
   void thanosAndTheInvasionRefuseEachOtherWithoutForce() throws IOException {
      String conflicts = Files.readString(SRC.resolve("worldevent/WorldEventConflicts.java"));
      assertTrue(conflicts.contains("ThanosEvent.running()"), "/chitauri start must see a running Thanos event");
      String thanos = Files.readString(SRC.resolve("command/ThanosCommands.java"));
      assertTrue(thanos.contains("WorldEventConflicts.invasion("), "/thanos start must see a running invasion");
      assertTrue(thanos.contains("Commands.literal(\"force\")"), "/thanos start force overrides like /chitauri start force");
      String meteor = Files.readString(SRC.resolve("command/MeteorRainCommands.java"));
      assertTrue(meteor.contains("WorldEventConflicts.invasion("), "/meteor start keeps its invasion check");
   }
}
