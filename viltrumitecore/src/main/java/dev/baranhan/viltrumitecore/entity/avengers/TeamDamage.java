package dev.baranhan.viltrumitecore.entity.avengers;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;

/**
 * Damage pacing of the summoned team: every Avenger hit (melee, area, projectile, joint move) goes through
 * here. Raw numbers are scaled by {@link #SCALE}, then one target takes at most a budget from the whole team:
 * a token bucket with a short burst and a steady rate set by {@link #MIN_KILL_SECONDS}. An evil iron golem
 * (100 HP) holds out at least ~16 s against all five (it died in ~3 s before), a zombie still drops in about
 * 1.5 s, and the spread rule of the target choice sends the team over a crowd instead of piling on one mob.
 * A capped hit still lands as a glance (flinch, knockback, sound), paid as a small token debt. Server only.
 */
public final class TeamDamage {
   /** Scale of every raw Avenger hit (the per-kind numbers stay the identity: Hulk and Thor hit hardest). */
   public static final float SCALE = 0.7F;
   /**
    * Shortest time (seconds) the whole team may take to kill a target of a given max health: small mobs still
    * fall in a second or two, an iron golem (100 HP) takes 16 s, bosses take longer but not forever.
    * Pairs {max health, seconds}, interpolated linearly.
    */
   static final double[][] MIN_KILL_SECONDS = {{0, 0.5}, {20, 1.5}, {40, 3.5}, {100, 16.0}, {300, 30.0}, {1000, 45.0}};
   /** Burst the bucket holds: max(BURST_MIN, max health x BURST_SHARE). */
   public static final float BURST_MIN = 6.0F;
   public static final float BURST_SHARE = 0.08F;
   /** Damage of a hit that found the bucket empty. */
   public static final float GLANCE = 0.5F;

   private static final Map<LivingEntity, Bucket> BUCKETS = new WeakHashMap<>();

   private TeamDamage() {
   }

   /** Steady budget per target, HP per second: what is left after the burst, spread over the minimum kill time. */
   public static float rate(float maxHealth) {
      return (float)Math.max(1.0, (maxHealth - burst(maxHealth)) / minKillSeconds(maxHealth));
   }

   public static double minKillSeconds(float maxHealth) {
      double[][] t = MIN_KILL_SECONDS;
      for (int i = 1; i < t.length; i++) {
         if (maxHealth <= t[i][0]) {
            double u = (maxHealth - t[i - 1][0]) / (t[i][0] - t[i - 1][0]);
            return t[i - 1][1] + u * (t[i][1] - t[i - 1][1]);
         }
      }
      return t[t.length - 1][1];
   }

   public static float burst(float maxHealth) {
      return Math.max(BURST_MIN, maxHealth * BURST_SHARE);
   }

   /** The team budget of one target (pure: time is the caller's game tick). */
   public static final class Bucket {
      private final float maxHealth;
      private double tokens;
      private long at;

      public Bucket(float maxHealth, long now) {
         this.maxHealth = maxHealth;
         this.tokens = burst(maxHealth);
         this.at = now;
      }

      /** Damage a hit of {@code raw} may deal at tick {@code now}; spends it (a glance runs a small debt). */
      public float spend(long now, float raw) {
         double refill = Math.max(0L, now - this.at) * rate(this.maxHealth) / 20.0;
         this.tokens = Math.min(burst(this.maxHealth), this.tokens + refill);
         this.at = now;
         float wanted = raw * SCALE;
         float dealt = (float)Math.max(GLANCE, Math.min(wanted, this.tokens));
         dealt = Math.min(dealt, Math.max(wanted, GLANCE));
         this.tokens -= dealt;
         return dealt;
      }
   }

   /** Server: what this team hit on {@code target} deals now. */
   public static float take(LivingEntity target, float raw) {
      long now = target.level().getGameTime();
      Bucket b = BUCKETS.get(target);
      if (b == null || b.maxHealth != target.getMaxHealth()) {
         b = new Bucket(target.getMaxHealth(), now);
         BUCKETS.put(target, b);
      }
      return b.spend(now, raw);
   }
}
