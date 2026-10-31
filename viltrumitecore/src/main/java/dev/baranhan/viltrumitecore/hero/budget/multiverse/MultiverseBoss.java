package dev.baranhan.viltrumitecore.hero.budget.multiverse;

import java.util.List;
import java.util.Locale;

/**
 * EIGHT MULTIVERSE BOSSES on one shared boss controller ({@link MultiverseFighter}):
 * each is a hero skin, numbers and one special mechanic, not its own AI.
 */
public record MultiverseBoss(String id, String heroKey, float health, float scale, float damageScale, float cooldownScale,
   Mechanic mechanic, int interval, String barColor) {

   public enum Mechanic {
      /** King of Curses: a slashing domain ring around the boss. */
      SHRINE,
      /** Lord of Illusions: teleports behind the target and leaves decoys. */
      ILLUSION,
      /** Lord of Death: raises minions. */
      SUMMON,
      /** Titan Destroyer: grows at half health, stomps and throws targets up. */
      TITAN,
      /** Absolute Power: a long telegraphed charge, then one enormous punch beam. */
      ONE_PUNCH,
      /** Demon King: Full Counter stance reflects incoming damage. */
      COUNTER,
      /** Atomic Shadow: freezes, gathers power, then a block-safe atomic blast. */
      ATOMIC,
      /** Final Champion: cycles through every other mechanic and enrages. */
      ALL
   }

   public static final List<MultiverseBoss> ALL = List.of(
      new MultiverseBoss("king_of_curses", "sukuna", 300.0F, 1.4F, 0.8F, 0.8F, Mechanic.SHRINE, 140, "RED"),
      new MultiverseBoss("lord_of_illusions", "aizen", 280.0F, 1.3F, 0.7F, 0.8F, Mechanic.ILLUSION, 120, "PURPLE"),
      new MultiverseBoss("lord_of_death", "ainz", 320.0F, 1.4F, 0.6F, 0.9F, Mechanic.SUMMON, 200, "WHITE"),
      new MultiverseBoss("titan_destroyer", "eren", 400.0F, 1.8F, 0.8F, 1.0F, Mechanic.TITAN, 160, "YELLOW"),
      new MultiverseBoss("absolute_power", "saitama", 250.0F, 1.2F, 0.5F, 1.0F, Mechanic.ONE_PUNCH, 200, "YELLOW"),
      new MultiverseBoss("demon_king", "meliodas", 300.0F, 1.3F, 0.8F, 0.8F, Mechanic.COUNTER, 160, "BLUE"),
      new MultiverseBoss("atomic_shadow", "shadow", 300.0F, 1.3F, 0.7F, 0.9F, Mechanic.ATOMIC, 240, "PURPLE"),
      new MultiverseBoss("final_champion", "gojo", 600.0F, 2.0F, 0.9F, 0.6F, Mechanic.ALL, 120, "PINK")
   );

   public static final String FINAL_ID = "final_champion";

   public String langKey() {
      return "boss.viltrumitecore.multiverse." + this.id;
   }

   public boolean isFinal() {
      return FINAL_ID.equals(this.id);
   }

   public static MultiverseBoss byId(String id) {
      String key = id == null ? "" : id.toLowerCase(Locale.ROOT);
      for (MultiverseBoss boss : ALL) {
         if (boss.id.equals(key)) {
            return boss;
         }
      }

      return null;
   }

   public static int indexOf(MultiverseBoss boss) {
      return ALL.indexOf(boss);
   }

   public static MultiverseBoss byIndex(int index) {
      return index >= 0 && index < ALL.size() ? ALL.get(index) : null;
   }

   public static MultiverseBoss finalBoss() {
      return byId(FINAL_ID);
   }

   /** Everyone except the final champion can appear as a phase-4 champion. */
   public static List<MultiverseBoss> champions() {
      return ALL.stream().filter(boss -> !boss.isFinal()).toList();
   }
}
