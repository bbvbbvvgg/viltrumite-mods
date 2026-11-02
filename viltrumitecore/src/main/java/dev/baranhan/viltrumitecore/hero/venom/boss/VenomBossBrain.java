package dev.baranhan.viltrumitecore.hero.venom.boss;

import dev.baranhan.viltrumitecore.hero.venom.VenomRules;
import javax.annotation.Nullable;

/**
 * The mind of the Venom boss (spec §9.2-§9.4), pure and tested by VenomBossBrainTest. Two halves:
 * <ul>
 *   <li><b>Phases</b> by health, never back (healing does not undo a phase): HUNT above two thirds, FRENZY down to
 *   one third, STARVING below. Every border crossed asks for one roar ({@link #pendingRoar}); a reloaded boss takes
 *   its phase back silently ({@link #restore}). Each phase shortens the cooldowns and unlocks attacks: FRENZY the
 *   impale and the dive, STARVING the tendril storm and the devouring bite.</li>
 *   <li><b>Adaptation</b>: a decaying score per threat (melee, ranged, fire, loud sound, height, kiting). Past
 *   {@link #ADAPT_AT} the boss answers that way of fighting it: a shell against arrows and beams, tendril yanks and
 *   leaps against kiting, a sweep against a melee flurry, a jump back from fire, a wall pounce against a player
 *   hiding on a roof. The scores fade with a {@link #HALF_LIFE} so a change of tactics is answered again.</li>
 * </ul>
 * The weaknesses are never adapted away: fire and a loud sound still make it recoil every time (VenomBossEntity).
 */
public final class VenomBossBrain {
   public enum Phase {
      HUNT(1.0F, 1.0F),
      FRENZY(0.75F, 1.15F),
      STARVING(0.6F, 1.25F);

      public final float cooldownScale;
      public final float speedScale;

      Phase(float cooldownScale, float speedScale) {
         this.cooldownScale = cooldownScale;
         this.speedScale = speedScale;
      }

      @Nullable
      public static Phase byId(int id) {
         Phase[] values = values();
         return id >= 0 && id < values.length ? values[id] : null;
      }
   }

   /** What the boss decides to do next. */
   public enum Attack {
      NONE(0), ROAR(0), TAKEOVER(900), RETREAT(100), LASH_SWEEP(80), STORM(260), SHELL(170), POUNCE(60), DIVE(180), CLAW(11), IMPALE(70),
      TENDRIL(90), LEAP(70);

      public final int cooldown;

      Attack(int cooldown) {
         this.cooldown = cooldown;
      }
   }

   /** The ways the players fight it. */
   public enum Threat {
      MELEE, RANGED, FIRE, SOUND, HEIGHT, KITE
   }

   /** What the boss sees this tick. {@code above}: how far the target stands over the boss (negative: below). */
   public record Situation(double distance, double above, boolean sight, boolean onGround, boolean onWall, int playersNear,
      boolean mayTakeover) {
   }

   public static final float FRENZY_AT = 2.0F / 3.0F;
   public static final float STARVING_AT = 1.0F / 3.0F;
   /** Threat score at which the boss changes its answer. */
   public static final float ADAPT_AT = 12.0F;
   /** Ticks for a threat score to halve. */
   public static final int HALF_LIFE = 200;
   static final float DECAY = (float)Math.pow(0.5, 1.0 / HALF_LIFE);
   /** Per tick of a target over its head or far away. */
   static final float WATCH_RATE = 0.15F;
   public static final double HEIGHT_ABOVE = 4.0;
   public static final double KITE_DISTANCE = 12.0;
   /** Melee hits inside {@link #STREAK_WINDOW} ticks that make it sweep the attacker off. */
   public static final int STREAK_HITS = 3;
   public static final int STREAK_WINDOW = 40;
   /** The takeover health threshold of a host: the starving boss jumps on healthier prey. */
   public static final float STARVING_TAKEOVER_FRACTION = 0.75F;

   private Phase phase = Phase.HUNT;
   private boolean pendingRoar;
   private final float[] threat = new float[Threat.values().length];
   private final int[] cooldown = new int[Attack.values().length];
   private int meleeStreak;
   private int sinceMelee = STREAK_WINDOW;
   private int adaptedMask;

   public Phase phase() {
      return this.phase;
   }

   public boolean pendingRoar() {
      return this.pendingRoar;
   }

   /** Pure: the phase a health fraction belongs to. */
   public static Phase phaseFor(float fraction) {
      if (!(fraction > STARVING_AT)) {
         return Phase.STARVING;
      }
      return fraction > FRENZY_AT ? Phase.HUNT : Phase.FRENZY;
   }

   /**
    * The health changed: returns the new phase when a border was crossed (only forward, once; a big hit across two
    * borders goes straight to the last one with a single roar), otherwise null.
    */
   @Nullable
   public Phase health(float fraction) {
      Phase next = phaseFor(fraction);
      if (next.ordinal() <= this.phase.ordinal()) {
         return null;
      }
      this.phase = next;
      this.pendingRoar = true;
      return next;
   }

   /** After a reload: back to the phase of the health, no roar. */
   public void restore(float fraction) {
      Phase next = phaseFor(fraction);
      if (next.ordinal() > this.phase.ordinal()) {
         this.phase = next;
      }
      this.pendingRoar = false;
   }

   public void hurt(Threat kind, float amount) {
      if (!(amount > 0) || !Float.isFinite(amount)) {
         return;
      }
      this.threat[kind.ordinal()] += Math.min(amount, 40.0F);
      if (kind == Threat.MELEE) {
         this.meleeStreak = this.sinceMelee < STREAK_WINDOW ? this.meleeStreak + 1 : 1;
         this.sinceMelee = 0;
      }
   }

   /** Per AI tick, what the target does: standing over the boss (a roof, a wall) or keeping far away. */
   public void watch(double distance, double above) {
      if (above > HEIGHT_ABOVE) {
         this.threat[Threat.HEIGHT.ordinal()] += WATCH_RATE;
      }
      if (distance > KITE_DISTANCE) {
         this.threat[Threat.KITE.ordinal()] += WATCH_RATE;
      }
   }

   public void tick() {
      for (int i = 0; i < this.threat.length; i++) {
         this.threat[i] *= DECAY;
      }
      for (int i = 0; i < this.cooldown.length; i++) {
         if (this.cooldown[i] > 0) {
            this.cooldown[i]--;
         }
      }
      if (this.sinceMelee < STREAK_WINDOW) {
         this.sinceMelee++;
      } else {
         this.meleeStreak = 0;
      }
   }

   public float threat(Threat kind) {
      return this.threat[kind.ordinal()];
   }

   public boolean adapted(Threat kind) {
      return this.threat[kind.ordinal()] >= ADAPT_AT;
   }

   /** The first time since the boss spawned (or since it forgot) that it adapts to a threat; for its taunt. */
   @Nullable
   public Threat newlyAdapted() {
      for (Threat t : Threat.values()) {
         int bit = 1 << t.ordinal();
         if (adapted(t)) {
            if ((this.adaptedMask & bit) == 0) {
               this.adaptedMask |= bit;
               return t;
            }
         } else if (this.threat[t.ordinal()] < ADAPT_AT * 0.25F) {
            this.adaptedMask &= ~bit;
         }
      }
      return null;
   }

   public int meleeStreak() {
      return this.sinceMelee < STREAK_WINDOW ? this.meleeStreak : 0;
   }

   public boolean ready(Attack a) {
      return this.cooldown[a.ordinal()] <= 0;
   }

   public int cooldown(Attack a) {
      return this.cooldown[a.ordinal()];
   }

   /** The attack started: its cooldown, shortened by the phase and by the adaptation it answers. */
   public void used(Attack a) {
      if (a == Attack.ROAR) {
         this.pendingRoar = false;
         return;
      }
      this.cooldown[a.ordinal()] = cooldownFor(a);
      if (a == Attack.LASH_SWEEP) {
         this.meleeStreak = 0;
      }
   }

   /**
    * A loud sound just made the boss recoil (the weakness itself never changes). Once it has learned that someone
    * fights it with noise it answers the noisemaker: the tendril yank and the leap are ready at once, so the next
    * choice drags or jumps at whoever rang the bell. True when it answers (the caller then turns on the source).
    */
   public boolean provokedByNoise() {
      if (!adapted(Threat.SOUND)) {
         return false;
      }
      this.cooldown[Attack.TENDRIL.ordinal()] = 0;
      this.cooldown[Attack.LEAP.ordinal()] = 0;
      return true;
   }

   /** Sets a cooldown directly (a takeover that could not start waits a little before it tries again). */
   public void delay(Attack a, int ticks) {
      this.cooldown[a.ordinal()] = Math.max(this.cooldown[a.ordinal()], ticks);
   }

   int cooldownFor(Attack a) {
      float scale = this.phase.cooldownScale;
      boolean chased = adapted(Threat.KITE) || adapted(Threat.RANGED);
      if ((a == Attack.TENDRIL || a == Attack.LEAP) && chased) {
         scale *= 0.6F;
      }
      if (a == Attack.LASH_SWEEP && adapted(Threat.MELEE)) {
         scale *= 0.5F;
      }
      return Math.max(1, Math.round(a.cooldown * scale));
   }

   /** The host's health share at or under which the boss may take a player over. */
   public float takeoverFraction() {
      return this.phase == Phase.STARVING ? STARVING_TAKEOVER_FRACTION : VenomRules.TAKEOVER_HEALTH_FRACTION;
   }

   public double tendrilRange() {
      boolean chased = adapted(Threat.KITE) || adapted(Threat.RANGED);
      return VenomRules.TENDRIL_RANGE * (chased ? 0.9 : 0.7);
   }

   public static final double CLAW_RANGE = 3.6;
   public static final double SWEEP_RANGE = VenomRules.LASH_SWEEP_RADIUS;
   public static final double IMPALE_MAX = VenomRules.LASH_IMPALE_RANGE + 1.5;
   public static final double STORM_MIN = 4.0, STORM_MAX = 20.0;
   public static final double DIVE_MIN = 6.0, DIVE_MAX = 16.0;
   public static final double POUNCE_MAX = 24.0;

   /**
    * The next attack, first match wins: the phase roar; a takeover; a jump back from a burning fighter; the sweep
    * against a crowd or a melee flurry; the starving storm; the shell against shooters; the pounce on a roof; the
    * dive; claws; the impale; a tendril yank; a leap. NONE: keep walking at the target. Does not start a cooldown
    * (the caller calls {@link #used} once the attack really began).
    */
   public Attack choose(Situation s) {
      if (this.pendingRoar) {
         return Attack.ROAR;
      }
      double d = s.distance();
      if (s.mayTakeover() && d < VenomRules.TAKEOVER_RANGE && ready(Attack.TAKEOVER)) {
         return Attack.TAKEOVER;
      }
      if (adapted(Threat.FIRE) && d < 3.5 && s.onGround() && ready(Attack.RETREAT)) {
         return Attack.RETREAT;
      }
      if (d < SWEEP_RANGE && ready(Attack.LASH_SWEEP) && (s.playersNear() >= 2 || adapted(Threat.MELEE) && meleeStreak() >= STREAK_HITS)) {
         return Attack.LASH_SWEEP;
      }
      if (this.phase == Phase.STARVING && d >= STORM_MIN && d <= STORM_MAX && s.sight() && ready(Attack.STORM)) {
         return Attack.STORM;
      }
      if (adapted(Threat.RANGED) && d > 5.0 && ready(Attack.SHELL)) {
         return Attack.SHELL;
      }
      if (adapted(Threat.HEIGHT) && s.above() > HEIGHT_ABOVE && d < POUNCE_MAX && (s.onGround() || s.onWall()) && ready(Attack.POUNCE)) {
         return Attack.POUNCE;
      }
      if (this.phase != Phase.HUNT && d >= DIVE_MIN && d <= DIVE_MAX && s.onGround() && s.above() < HEIGHT_ABOVE && ready(Attack.DIVE)) {
         return Attack.DIVE;
      }
      if (d < CLAW_RANGE && ready(Attack.CLAW)) {
         return Attack.CLAW;
      }
      if (this.phase != Phase.HUNT && d >= CLAW_RANGE && d <= IMPALE_MAX && s.sight() && ready(Attack.IMPALE)) {
         return Attack.IMPALE;
      }
      if (d > 6.0 && d < tendrilRange() && s.sight() && ready(Attack.TENDRIL)) {
         return Attack.TENDRIL;
      }
      if (d > 9.0 && s.onGround() && ready(Attack.LEAP)) {
         return Attack.LEAP;
      }
      return Attack.NONE;
   }

   /** Pure: the bite of the starving boss while it holds its prey (ticks of the hold at which it bites). */
   public static boolean biteAt(int holdTicks) {
      return holdTicks == 12 || holdTicks == 22 || holdTicks == 32;
   }

   /** Pure: the launch of a jump at a point {@code dx,dz} away and {@code dy} up: {x, y, z} velocity. */
   public static double[] pounce(double dx, double dy, double dz) {
      double flat = Math.sqrt(dx * dx + dz * dz);
      double up = Math.min(1.7, 0.55 + Math.max(0.0, dy) * 0.085);
      double speed = flat < 1.0E-4 ? 0.0 : Math.min(1.25, flat * 0.075);
      double nx = flat < 1.0E-4 ? 0.0 : dx / flat;
      double nz = flat < 1.0E-4 ? 0.0 : dz / flat;
      return new double[]{nx * speed, up, nz * speed};
   }
}
