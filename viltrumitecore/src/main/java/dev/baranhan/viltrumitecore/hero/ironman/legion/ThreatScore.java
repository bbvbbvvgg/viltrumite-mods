package dev.baranhan.viltrumitecore.hero.ironman.legion;

/**
 * Threat analysis of the coordinator (design §5), pure. The server adapter
 * fills a {@link Threat} per candidate; this class decides who may be a
 * target at all and how urgent each one is.
 */
public final class ThreatScore {
   private ThreatScore() {
   }

   /** What a candidate is to the owner. Decided once by the server adapter. */
   public enum Relation {
      /** Tony himself. */
      OWNER,
      /** Another Legion suit, Tony's pet or team mate, or a hero-protected ally. */
      ALLY,
      /** Engine-protected (cinematic, spectator, creative). */
      PROTECTED,
      /** A player while the server forbids PvP (or the team rule). */
      PLAYER_NO_PVP,
      /** A player the server lets Tony hurt. */
      PLAYER_PVP,
      /** Villagers, golems, traders: only on an explicit ATTACK order. */
      VILLAGER,
      /** Animals and neutral mobs. */
      NEUTRAL,
      /** Monsters. */
      HOSTILE
   }

   /**
    * One candidate on the board.
    *
    * @param relation what it is to Tony
    * @param sinceHitOwner ticks since it last hurt Tony (huge = never)
    * @param sinceHitSuit ticks since it last hurt a suit (huge = never)
    * @param danger rough damage per hit it deals (attack attribute or projectile)
    * @param distanceToOwner blocks
    * @param visible a clear line from the nearest suit
    * @param health current health
    * @param maxHealth max health
    * @param armor armor attribute
    * @param flying flies or hovers (overwatch and Mark 39 chase it)
    * @param tonyTarget Tony's crosshair or last hit target
    * @param ordered target of an explicit ATTACK order
    */
   public record Threat(
      Relation relation,
      int sinceHitOwner,
      int sinceHitSuit,
      float danger,
      double distanceToOwner,
      boolean visible,
      float health,
      float maxHealth,
      float armor,
      boolean flying,
      boolean tonyTarget,
      boolean ordered
   ) {
      public boolean aggressor() {
         return this.sinceHitOwner < LegionRules.THREAT_MEMORY || this.sinceHitSuit < LegionRules.THREAT_MEMORY;
      }

      /** Health a hit really has to take: armor makes a target last longer (vanilla armor curve, simplified). */
      public float effectiveHealth() {
         float armorFactor = 1.0F + Math.max(0.0F, Math.min(30.0F, this.armor)) / 25.0F;
         return Math.max(0.5F, this.health) * armorFactor;
      }
   }

   /**
    * May the Legion shoot it at all? Never Tony, allies, protected entities or
    * players without PvP — not even on order. Villagers only on order; neutral
    * mobs and PvP players on order or once they attacked Tony or a suit.
    */
   public static boolean eligible(Threat threat) {
      return switch (threat.relation()) {
         case OWNER, ALLY, PROTECTED, PLAYER_NO_PVP -> false;
         case VILLAGER -> threat.ordered();
         case NEUTRAL, PLAYER_PVP -> threat.ordered() || threat.aggressor();
         case HOSTILE -> true;
      };
   }

   /** Urgency; higher first. Ineligible candidates score negative infinity. */
   public static double score(Threat threat, LegionPriority priority, double engageRadius) {
      if (!eligible(threat)) {
         return Double.NEGATIVE_INFINITY;
      }

      double score = 0.0;
      if (threat.ordered()) {
         score += 1000.0;
      }

      // Who is hurting Tony right now matters most; the memory fades over THREAT_MEMORY.
      if (threat.sinceHitOwner() < LegionRules.THREAT_MEMORY) {
         score += 120.0 * (1.0 - threat.sinceHitOwner() / (double)LegionRules.THREAT_MEMORY) + 40.0;
      }

      if (threat.sinceHitSuit() < LegionRules.THREAT_MEMORY) {
         score += 40.0 * (1.0 - threat.sinceHitSuit() / (double)LegionRules.THREAT_MEMORY) + 10.0;
      }

      if (threat.relation() == Relation.HOSTILE) {
         score += 25.0;
      }

      score += Math.min(40.0, threat.danger() * 3.0);
      double radius = Math.max(1.0, engageRadius);
      score += 35.0 * Math.max(0.0, 1.0 - threat.distanceToOwner() / radius);
      if (!threat.visible()) {
         score -= 20.0;
      }

      float fraction = threat.maxHealth() <= 0.0F ? 1.0F : Math.max(0.0F, Math.min(1.0F, threat.health() / threat.maxHealth()));
      switch (priority) {
         case NEAREST -> {
            score += 30.0 * Math.max(0.0, 1.0 - threat.distanceToOwner() / radius);
            // A nearly dead attacker is quick to remove.
            score += 10.0 * (1.0 - fraction);
         }
         case STRONGEST -> score += Math.min(60.0, threat.effectiveHealth() * 0.6) + Math.min(30.0, threat.danger() * 3.0);
         case TONY_TARGET -> {
            if (threat.tonyTarget()) {
               score += 200.0;
            }
         }
      }

      if (threat.tonyTarget()) {
         score += 30.0;
      }

      return score;
   }

   /** A candidate outside the engage radius is kept only if it is still hitting Tony or was ordered. */
   public static boolean inReach(Threat threat, double engageRadius) {
      return threat.ordered() || threat.distanceToOwner() <= engageRadius || threat.sinceHitOwner() < LegionRules.INTERRUPT_WINDOW;
   }
}
