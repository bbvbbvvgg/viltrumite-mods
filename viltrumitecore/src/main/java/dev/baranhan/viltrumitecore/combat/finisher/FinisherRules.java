package dev.baranhan.viltrumitecore.combat.finisher;

/**
 * Pure validation rules of the finisher (wave1-22, task T6). No Minecraft class lives here: the
 * server glue ({@link FinisherService}) and the client prediction
 * ({@code client.cinematic.finisher.FinisherTargeting}) both call {@link #judge}, so the HUD hint and
 * the server verdict can never drift apart. The server is still the only authority: it re-reads every
 * fact from the live world.
 */
public final class FinisherRules {
   /** Reach of the finisher in blocks (design §6). */
   public static final double RANGE = 4.5;
   /** A target below this health is finisher-ready even when the fraction is not reached yet. */
   public static final float HEALTH_FLOOR = 4.0F;
   /** Finisher threshold as a fraction of the maximum health. */
   public static final float HEALTH_FRACTION = 0.20F;
   /** A boss is tougher: only its last 8 % may be finished. */
   public static final float BOSS_FRACTION = 0.08F;
   /** Anti-spam: one accepted request per this many ticks. */
   public static final int COOLDOWN_TICKS = 20;
   /**
    * Size of the blow relative to the health it must remove. Armor never lets more than its share of a
    * hit through (in vanilla the worst case keeps 20 %), so the blow is oversized by this factor: the
    * remainder still removes the target's health for any body and any armor. Mob, NPC and boss bodies
    * have no anti one-shot cap ({@code CombatBalance} caps a hero victim only, and PvP hits are exempt).
    */
   public static final float ARMOR_HEADROOM = 8.0F;

   /** Why a request was allowed or refused. The server logs it; nothing of it goes on the wire. */
   public enum Verdict {
      ALLOW,
      DEAD,
      OTHER_LEVEL,
      TOO_FAR,
      NO_LINE_OF_SIGHT,
      TOO_HEALTHY,
      PVP_FORBIDDEN,
      ALLY
   }

   /** Everything the verdict needs, as plain values (gathered by the server, or predicted by the client). */
   public record Facts(boolean alive, boolean sameLevel, boolean lineOfSight, double distance,
                       float health, float maxHealth, boolean boss,
                       boolean player, boolean pvpAllowed, boolean ally) {
   }

   private FinisherRules() {
   }

   /** The health a target may still have and be finished; a boss uses the lower fraction. */
   public static float threshold(float maxHealth, boolean boss) {
      float fraction = boss ? BOSS_FRACTION : HEALTH_FRACTION;
      float sane = Float.isFinite(maxHealth) && maxHealth > 0.0F ? maxHealth : 0.0F;
      return Math.max(HEALTH_FLOOR, fraction * sane);
   }

   /** True when the target's health is at or below its threshold. A NaN health never passes. */
   public static boolean healthAllows(float health, float maxHealth, boolean boss) {
      return Float.isFinite(health) && health >= 0.0F && health <= threshold(maxHealth, boss);
   }

   /** True when the target is within reach. A NaN or negative distance never passes. */
   public static boolean inRange(double distance) {
      return Double.isFinite(distance) && distance >= 0.0 && distance <= RANGE;
   }

   /** Anti-spam gate: a stored cooldown still running refuses the request. */
   public static boolean onCooldown(int remainingTicks) {
      return remainingTicks > 0;
   }

   /** Damage of the finishing blow: enough to remove the given health through armor. */
   public static float finishingDamage(float health, float absorption) {
      float hp = Float.isFinite(health) && health > 0.0F ? health : 0.0F;
      float absorb = Float.isFinite(absorption) && absorption > 0.0F ? absorption : 0.0F;
      return (hp + absorb + 1.0F) * ARMOR_HEADROOM;
   }

   /**
    * The verdict, in the order a refusal should read: a dead body first, then the geometry, then the
    * health, then the PvP rule (players may be finished only when the server allows PvP and the target
    * is not an ally).
    */
   public static Verdict judge(Facts facts) {
      if (facts == null || !facts.alive()) {
         return Verdict.DEAD;
      }
      if (!facts.sameLevel()) {
         return Verdict.OTHER_LEVEL;
      }
      if (!inRange(facts.distance())) {
         return Verdict.TOO_FAR;
      }
      if (!facts.lineOfSight()) {
         return Verdict.NO_LINE_OF_SIGHT;
      }
      if (!healthAllows(facts.health(), facts.maxHealth(), facts.boss())) {
         return Verdict.TOO_HEALTHY;
      }
      if (facts.player()) {
         if (!facts.pvpAllowed()) {
            return Verdict.PVP_FORBIDDEN;
         }
         if (facts.ally()) {
            return Verdict.ALLY;
         }
      }
      return Verdict.ALLOW;
   }
}
