package dev.baranhan.viltrumitecore.hero.budget.multiverse;

import dev.baranhan.viltrumitecore.hero.budget.BudgetFaction;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Pure numbers of the multiverse modes (no Minecraft classes, unit-tested):
 * how many enemies, how long the pauses, which champions, how fast NPCs cast.
 */
public final class MultiverseRules {
   public static final int RIFT_TICKS = 100;
   public static final int CLOSING_TICKS = 80;
   public static final int FINAL_COUNTDOWN_TICKS = 60;
   public static final int NO_PLAYER_FAIL_TICKS = 600;
   public static final double ARENA_RADIUS = 96.0;
   public static final int BOSS_RUSH_PAUSE_TICKS = 100;
   /** A player this close to a living event NPC still counts as in the arena (a chased boss may drift past ARENA_RADIUS). */
   public static final double ENGAGE_RADIUS = 48.0;
   /** Regular NPCs hit softer than players (a 40-damage Normal Punch would end every event). */
   public static final float FIGHTER_DAMAGE_SCALE = 0.35F;
   public static final float FIGHTER_COOLDOWN_SCALE = 1.5F;
   public static final float CHAOS_COOLDOWN_SCALE = 0.33F;
   public static final float CHAMPION_HEALTH_FACTOR = 0.6F;

   private MultiverseRules() {
   }

   public static int invaders(Set<ChaosModifier> mods) {
      return mods.contains(ChaosModifier.DOUBLE_TROUBLE) ? 12 : 6;
   }

   public static int warGroupSize(Set<ChaosModifier> mods) {
      return mods.contains(ChaosModifier.DOUBLE_TROUBLE) ? 8 : 4;
   }

   public static int warGroups() {
      return 3;
   }

   /**
    * The factions of phase 3. Three "hero" factions are mutually neutral and
    * would only stare at each other until the timeout, so a draw of three
    * heroic factions trades its last one for a hostile faction.
    */
   public static List<BudgetFaction> warFactions(Random random) {
      List<BudgetFaction> all = new ArrayList<>(List.of(BudgetFaction.values()));
      java.util.Collections.shuffle(all, random);
      List<BudgetFaction> picked = new ArrayList<>(all.subList(0, Math.min(warGroups(), all.size())));
      if (picked.stream().allMatch(BudgetFaction::heroic)) {
         for (BudgetFaction faction : all) {
            if (!faction.heroic()) {
               picked.set(picked.size() - 1, faction);
               break;
            }
         }
      }
      return picked;
   }

   /** The war is over when no two standing factions are hostile to each other. */
   public static boolean warSettled(Set<BudgetFaction> standing) {
      for (BudgetFaction a : standing) {
         for (BudgetFaction b : standing) {
            if (a.hostileTo(b)) {
               return false;
            }
         }
      }
      return true;
   }

   public static int championCount(Set<ChaosModifier> mods) {
      return mods.contains(ChaosModifier.BOSS_MAYHEM) ? 3 : 2;
   }

   public static int phasePause(Set<ChaosModifier> mods) {
      return mods.contains(ChaosModifier.BOSS_MAYHEM) ? 20 : 100;
   }

   public static int invasionTimeout(Set<ChaosModifier> mods) {
      return mods.contains(ChaosModifier.BOSS_MAYHEM) ? 900 : 1800;
   }

   public static int warTimeout(Set<ChaosModifier> mods) {
      return mods.contains(ChaosModifier.BOSS_MAYHEM) ? 1200 : 2400;
   }

   public static int finalCountdown(Set<ChaosModifier> mods) {
      return mods.contains(ChaosModifier.BOSS_MAYHEM) ? 10 : FINAL_COUNTDOWN_TICKS;
   }

   /** Multiplier on boss special-attack intervals. */
   public static float bossSpecialScale(Set<ChaosModifier> mods) {
      return mods.contains(ChaosModifier.BOSS_MAYHEM) ? 0.5F : 1.0F;
   }

   /** Multiplier on NPC ability cooldowns. */
   public static float npcCooldownScale(Set<ChaosModifier> mods) {
      return mods.contains(ChaosModifier.ULTIMATE_CHAOS) ? CHAOS_COOLDOWN_SCALE : 1.0F;
   }

   /** Champions of phase 4: the fixed line-up, or a random draw with Random Champions. */
   public static List<MultiverseBoss> champions(Set<ChaosModifier> mods, Random random) {
      List<MultiverseBoss> pool = new ArrayList<>(MultiverseBoss.champions());
      int count = Math.min(championCount(mods), pool.size());
      List<MultiverseBoss> picked = new ArrayList<>();
      if (mods.contains(ChaosModifier.RANDOM_CHAMPIONS)) {
         for (int i = 0; i < count; i++) {
            picked.add(pool.remove(random.nextInt(pool.size())));
         }
      } else {
         for (String id : new String[]{"titan_destroyer", "demon_king", "lord_of_illusions"}) {
            if (picked.size() < count) {
               picked.add(MultiverseBoss.byId(id));
            }
         }
      }

      return picked;
   }
}
