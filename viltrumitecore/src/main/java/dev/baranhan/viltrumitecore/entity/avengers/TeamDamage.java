package dev.baranhan.viltrumitecore.entity.avengers;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;

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
   private static final Logger LOGGER = LogUtils.getLogger();
   /** Playtest log (-Dviltrumite.qa.team=true): time from a target's first team hit to its death. */
   private static final boolean QA = Boolean.getBoolean("viltrumite.qa.team");

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
      private final long first;
      private double tokens;
      private long at;
      private boolean logged;

      public Bucket(float maxHealth, long now) {
         this.maxHealth = maxHealth;
         this.tokens = burst(maxHealth);
         this.at = now;
         this.first = now;
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

   /** Server, after a team hit landed: logs the kill time of the target in a playtest (see {@link #QA}). */
   public static void afterHit(LivingEntity target) {
      if (!QA || target.isAlive()) {
         return;
      }
      Bucket b = BUCKETS.get(target);
      if (b != null && !b.logged) {
         b.logged = true;
         LOGGER.info("TEAMKILL {} maxHp={} killed {} s after the first team hit", net.minecraft.world.entity.EntityType.getKey(target.getType()),
            b.maxHealth, String.format(java.util.Locale.ROOT, "%.2f", (target.level().getGameTime() - b.first) / 20.0));
      }
   }

   /**
    * Server, any death (playtest log only): a target the team had hit logs its kill time even when the last blow
    * was not a team hit (Steve's shield, fire, a vanilla mob), with the killing source.
    */
   public static void onDeath(LivingEntity target, net.minecraft.world.damagesource.DamageSource source) {
      if (!QA) {
         return;
      }
      Bucket b = BUCKETS.get(target);
      if (b != null && !b.logged) {
         b.logged = true;
         net.minecraft.world.entity.Entity by = source.getEntity();
         LOGGER.info("TEAMKILL {} maxHp={} killed {} s after the first team hit (last blow {} by {})",
            net.minecraft.world.entity.EntityType.getKey(target.getType()), b.maxHealth,
            String.format(java.util.Locale.ROOT, "%.2f", (target.level().getGameTime() - b.first) / 20.0),
            source.getMsgId(), by == null ? "-" : net.minecraft.world.entity.EntityType.getKey(by.getType()));
      }
   }
}
