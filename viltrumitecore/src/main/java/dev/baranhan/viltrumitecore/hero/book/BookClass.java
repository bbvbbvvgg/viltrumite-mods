package dev.baranhan.viltrumitecore.hero.book;

/**
 * What kind of body a book belongs to (docs/rohan/heavens-door-design.md §5). Instruction durations and the reading
 * window come from the class; the profile's numbers refine it.
 */
public enum BookClass {
   /** Animals: the easiest books (canon: animals are simpler to open). */
   ANIMAL,
   /** Ordinary hostile or neutral mobs. */
   SIMPLE_MOB,
   /** Villagers, illagers, piglins and similar thinking mobs. */
   SMART_MOB,
   /** City citizens and other bystanders. */
   CITIZEN,
   /** A hero's or a boss's summon (shadow soldiers, legion suits, Black Order guards). */
   SUMMON,
   /** Bosses and world-event giants. */
   BOSS,
   /** Another player (hero or not). */
   PLAYER
}
