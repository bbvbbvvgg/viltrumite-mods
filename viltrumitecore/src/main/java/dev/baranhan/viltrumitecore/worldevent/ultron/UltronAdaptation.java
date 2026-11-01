package dev.baranhan.viltrumitecore.worldevent.ultron;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Ultron's adaptation (pure, unit-tested). The director feeds it only what the machines really
 * observed ({@link #observe}): hits a unit took inside the network's coverage (classified by the
 * attacker's geometry), node losses, repelled attacks, losses concentrated in one sector. Pressure
 * per tactic decays over time; learning scales with the sensors online (none: frozen). From the
 * adaptation phase on, enough pressure activates a protocol, at most {@link #maxActive} at once,
 * each with a duration, a cooldown after it lapses and a counter that ends it early.
 */
public final class UltronAdaptation {
   public enum Tactic {
      AERIAL,
      RANGED,
      MELEE,
      FACTORY_LOSS,
      SECTOR_FOCUS,
      DEFENSE_HELD
   }

   public enum Protocol {
      /** Aerial attacks → interceptors in production, drones and interceptors hunt flyers. */
      ANTI_AIR("anti_air", Tactic.AERIAL, 28.0F, 150 * UltronRules.TPS),
      /** Ranged attacks → frontal deflector shields on sentries and brutes. */
      DEFLECTOR("deflector", Tactic.RANGED, 32.0F, 150 * UltronRules.TPS),
      /** A foundry lost → production relocates to a reserve site far from the players. */
      RELOCATION("relocation", Tactic.FACTORY_LOSS, 10.0F, 180 * UltronRules.TPS),
      /** Losses focused on one sector → forces pulled out of it, the assault shifts elsewhere. */
      REDISTRIBUTION("redistribution", Tactic.SECTOR_FOCUS, 24.0F, 120 * UltronRules.TPS),
      /** A node held against an attack → that node fortified (shield, regen, guards). */
      FORTIFY("fortify", Tactic.DEFENSE_HELD, 10.0F, 120 * UltronRules.TPS);

      private final String key;
      public final Tactic tactic;
      public final float threshold;
      public final int duration;

      Protocol(String key, Tactic tactic, float threshold, int duration) {
         this.key = key;
         this.tactic = tactic;
         this.threshold = threshold;
         this.duration = duration;
      }

      public String key() {
         return this.key;
      }

      public static Protocol byId(int id) {
         Protocol[] all = values();
         return id >= 0 && id < all.length ? all[id] : ANTI_AIR;
      }
   }

   /** Something changed that the players should learn about (subtitle line, HUD, effects). */
   public record Change(Protocol protocol, boolean active, Reason reason) {
   }

   public enum Reason {
      ACTIVATED,
      RENEWED,
      LAPSED,
      COUNTERED
   }

   /** Pressure halves in this many seconds (event tactics like a lost foundry fade faster). */
   public static final float HALF_LIFE_S = 120.0F;
   public static final float EVENT_HALF_LIFE_S = 45.0F;
   public static final int COOLDOWN = 60 * UltronRules.TPS;
   /**
    * Counters: a protocol ends early once the players answered it for {@link #COUNTER_HOLD} seconds in a row:
    * they stopped the tactic (its recent pressure below {@link #COUNTER_RECENT}) or, against DEFLECTOR, switched
    * to melee (recent melee above {@link #MELEE_BEATS_SHIELD} times the recent ranged). Recent pressure halves
    * every {@link #RECENT_HALF_LIFE_S} seconds. RELOCATION, REDISTRIBUTION and FORTIFY answer events and run out.
    */
   public static final float RECENT_HALF_LIFE_S = 8.0F;
   public static final float COUNTER_RECENT = 1.0F;
   public static final float MELEE_BEATS_SHIELD = 1.5F;
   public static final int COUNTER_HOLD = 30;

   private final float[] pressure = new float[Tactic.values().length];
   private final float[] recent = new float[Tactic.values().length];
   private final int[] left = new int[Protocol.values().length];
   private final int[] cooldown = new int[Protocol.values().length];
   private final int[] level = new int[Protocol.values().length];
   private final int[] lowFor = new int[Protocol.values().length];
   private final int maxActive;
   private float learning = 1.0F;
   private int activations;
   /** The sector REDISTRIBUTION pulls out of, the node FORTIFY covers (-1: none). */
   private int focusSector = -1;
   private int fortifiedNode = -1;

   public UltronAdaptation(int maxActive) {
      this.maxActive = Math.max(1, maxActive);
   }

   /** Learning rate from the sensors online: 0 → frozen, 1 → 0.6, 2 → 1.0, 3+ → 1.2. */
   public static float learningFor(int sensorsOnline) {
      return switch (Math.max(0, sensorsOnline)) {
         case 0 -> 0.0F;
         case 1 -> 0.6F;
         case 2 -> 1.0F;
         default -> 1.2F;
      };
   }

   public void setLearning(float learning) {
      this.learning = Math.max(0.0F, learning);
   }

   public float learning() {
      return this.learning;
   }

   public boolean frozen() {
      return this.learning <= 0.0F;
   }

   /** Records an observation (scaled by the learning rate; nothing while frozen). */
   public void observe(Tactic tactic, float amount) {
      if (amount > 0.0F && this.learning > 0.0F) {
         this.pressure[tactic.ordinal()] += amount * this.learning;
         this.recent[tactic.ordinal()] += amount;
      }
   }

   /** Restores saved pressure (no learning factor). */
   public void setPressure(Tactic tactic, float value) {
      this.pressure[tactic.ordinal()] = Math.max(0.0F, value);
   }

   public float pressure(Tactic tactic) {
      return this.pressure[tactic.ordinal()];
   }

   /** Pressure of the last few seconds (fast decay): what the players are doing right now. */
   public float recent(Tactic tactic) {
      return this.recent[tactic.ordinal()];
   }

   /** Sum of the combat tactics' pressure (the machine war ends early when it is high). */
   public float combatPressure() {
      return this.pressure(Tactic.AERIAL) + this.pressure(Tactic.RANGED) + this.pressure(Tactic.MELEE);
   }

   public boolean active(Protocol p) {
      return this.left[p.ordinal()] > 0;
   }

   public int ticksLeft(Protocol p) {
      return this.left[p.ordinal()];
   }

   public int cooldown(Protocol p) {
      return this.cooldown[p.ordinal()];
   }

   public int level(Protocol p) {
      return this.level[p.ordinal()];
   }

   public int activeCount() {
      int n = 0;
      for (int l : this.left) {
         if (l > 0) {
            n++;
         }
      }
      return n;
   }

   public int maxActive() {
      return this.maxActive;
   }

   public int activations() {
      return this.activations;
   }

   public int focusSector() {
      return this.focusSector;
   }

   public void setFocusSector(int sector) {
      this.focusSector = sector;
   }

   public int fortifiedNode() {
      return this.fortifiedNode;
   }

   public void setFortifiedNode(int node) {
      this.fortifiedNode = node;
   }

   /** Restores one protocol's saved state. */
   public void restore(Protocol p, int ticksLeft, int cooldownTicks, int lvl) {
      this.left[p.ordinal()] = Math.max(0, ticksLeft);
      this.cooldown[p.ordinal()] = Math.max(0, cooldownTicks);
      this.level[p.ordinal()] = Math.max(0, lvl);
   }

   public void setActivations(int count) {
      this.activations = Math.max(0, count);
   }

   /** The director finished a protocol's job (RELOCATION built its foundry): it ends now. */
   @Nullable
   public Change complete(Protocol p) {
      if (this.left[p.ordinal()] <= 0) {
         return null;
      }
      this.left[p.ordinal()] = 0;
      this.cooldown[p.ordinal()] = COOLDOWN;
      return new Change(p, false, Reason.LAPSED);
   }

   /**
    * One second passes: pressure decays, durations and cooldowns run down, counters end protocols,
    * and when {@code canActivate} (adaptation phase or later) new ones start. Returns the changes.
    */
   public List<Change> second(boolean canActivate) {
      List<Change> out = new ArrayList<>();
      for (Tactic t : Tactic.values()) {
         boolean event = t == Tactic.FACTORY_LOSS || t == Tactic.DEFENSE_HELD;
         float half = event ? EVENT_HALF_LIFE_S : HALF_LIFE_S;
         this.pressure[t.ordinal()] *= (float)Math.pow(0.5, 1.0 / half);
         if (this.pressure[t.ordinal()] < 0.01F) {
            this.pressure[t.ordinal()] = 0.0F;
         }
         this.recent[t.ordinal()] *= (float)Math.pow(0.5, 1.0 / RECENT_HALF_LIFE_S);
         if (this.recent[t.ordinal()] < 0.01F) {
            this.recent[t.ordinal()] = 0.0F;
         }
      }
      for (Protocol p : Protocol.values()) {
         int i = p.ordinal();
         if (this.cooldown[i] > 0) {
            this.cooldown[i] = Math.max(0, this.cooldown[i] - UltronRules.TPS);
         }
         if (this.left[i] <= 0) {
            continue;
         }
         this.left[i] = Math.max(0, this.left[i] - UltronRules.TPS);
         if (this.countered(p)) {
            this.lowFor[i]++;
         } else {
            this.lowFor[i] = 0;
         }
         if (this.lowFor[i] >= COUNTER_HOLD) {
            this.left[i] = 0;
            this.cooldown[i] = COOLDOWN;
            this.lowFor[i] = 0;
            out.add(new Change(p, false, Reason.COUNTERED));
         } else if (this.left[i] == 0) {
            if (this.pressure[p.tactic.ordinal()] >= p.threshold && !this.frozen()) {
               this.left[i] = p.duration;
               this.level[i]++;
               out.add(new Change(p, true, Reason.RENEWED));
            } else {
               this.cooldown[i] = COOLDOWN;
               out.add(new Change(p, false, Reason.LAPSED));
            }
         }
      }
      if (canActivate && !this.frozen()) {
         while (this.activeCount() < this.maxActive) {
            Protocol best = null;
            float bestRatio = 1.0F;
            for (Protocol p : Protocol.values()) {
               int i = p.ordinal();
               if (this.left[i] > 0 || this.cooldown[i] > 0) {
                  continue;
               }
               float ratio = this.pressure[p.tactic.ordinal()] / p.threshold;
               if (ratio >= bestRatio) {
                  bestRatio = ratio;
                  best = p;
               }
            }
            if (best == null) {
               break;
            }
            int i = best.ordinal();
            this.left[i] = best.duration;
            this.level[i]++;
            this.lowFor[i] = 0;
            this.activations++;
            // the trigger is spent: the same event must happen again to renew it
            if (best.tactic == Tactic.FACTORY_LOSS || best.tactic == Tactic.DEFENSE_HELD) {
               this.pressure[best.tactic.ordinal()] = 0.0F;
            }
            out.add(new Change(best, true, Reason.ACTIVATED));
         }
      }
      return out;
   }

   /** The players' answer to a running protocol: they stopped the tactic, or switched to its weakness. */
   boolean countered(Protocol p) {
      if (p == Protocol.RELOCATION || p == Protocol.REDISTRIBUTION || p == Protocol.FORTIFY) {
         return false;
      }
      float own = this.recent[p.tactic.ordinal()];
      if (p == Protocol.DEFLECTOR && this.recent[Tactic.MELEE.ordinal()] > Math.max(COUNTER_RECENT, own * MELEE_BEATS_SHIELD)) {
         // melee beats the frontal shields
         return true;
      }
      return own < COUNTER_RECENT;
   }
}
