package dev.baranhan.viltrumitecore.balance;

/** What kind of incoming hit the PvE profile is asked about ({@link CombatBalance#classify}). */
public enum IncomingKind {
   /** A melee hit of a mob or NPC. */
   MELEE,
   /** A melee hit whose raw amount (before armor) is at least {@link BalanceRules#HEAVY_RAW}. */
   HEAVY,
   /** Arrows, bolts, beams, fireballs of a mob or NPC. */
   PROJECTILE,
   /** Explosions and blasts. */
   BLAST,
   /** An attack of a boss (Thanos, a leviathan, the Warden, the Wither, the dragon). */
   BOSS,
   /** World damage: fall, fire, lava, drowning, cactus, magic of no one. */
   WORLD,
   /** A player (hero) attacker: PvP keeps the kits' own numbers. */
   PVP,
   /** Must land untouched: /kill, the void, divine damage, annihilation scopes. */
   EXEMPT
}
