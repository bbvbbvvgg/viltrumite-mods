package dev.baranhan.viltrumitecore.t1000;

/**
 * Pure damage model (covered by T1000DamageTest). Two pools: integrity (the liquid shell: absorbs, regenerates)
 * and core (the vanilla health: real damage, the final kill). One hit never takes more than {@link #CORE_CAP}
 * of the core unless the body is frozen or lies as a puddle, so no single blow destroys it, and nothing that
 * merely scratches the shell kills it either.
 */
public final class T1000Damage {
   public static final float MAX_INTEGRITY = 100.0F;
   public static final float MAX_CORE = 60.0F;
   /** Share of a normal hit that reaches the core. */
   public static final float BLEED = 0.15F;
   public static final float HEAVY_BLEED = 0.3F;
   public static final float HEAT_BLEED = 0.8F;
   /** A hit at least this strong deforms the body (dent, stagger). */
   public static final float HEAVY_HIT = 18.0F;
   public static final float CORE_CAP = 0.35F;
   /** Integrity regenerated per tick once {@link #REGEN_DELAY} ticks passed without a hit. */
   public static final float REGEN_PER_TICK = 0.3F;
   public static final float PUDDLE_REGEN_PER_TICK = 0.9F;
   public static final int REGEN_DELAY = 40;
   /** Cold above this is frozen: brittle, no regeneration, the next solid hit shatters. */
   public static final float FROZEN = 1.0F;
   public static final float SHATTER_HIT = 8.0F;

   public enum Kind {
      /** Blades, fists, arrows, bullets: the shell takes it. */
      NORMAL,
      /** Explosions, giant fists, falling blocks: the shell deforms, more reaches the core. */
      HEAVY,
      /** Fire, lava, lightning, plasma: the metal softens, regeneration stops. */
      HEAT,
      /** Freezing (powder snow, frost): makes it brittle. */
      COLD,
      /** /kill, the void: no shell, no cap. */
      ABSOLUTE,
      /** Falling, drowning, suffocation, poison, starvation: nothing to hurt. */
      IMMUNE
   }

   /** Result of one hit: what each pool loses and what the body does. */
   public record Split(float integrity, float core, float cold, boolean heavy, boolean shatter) {
   }

   private T1000Damage() {
   }

   /**
    * Splits a hit of {@code amount} of this kind on a body with {@code integrity} left, {@code cold} stored,
    * lying as a puddle or not, with {@code maxCore} core in total.
    */
   public static Split split(float amount, Kind kind, float integrity, float cold, boolean puddle, float maxCore) {
      if (!(amount > 0.0F) || kind == Kind.IMMUNE) {
         return new Split(0.0F, 0.0F, 0.0F, false, false);
      }
      if (kind == Kind.ABSOLUTE) {
         return new Split(integrity, amount, 0.0F, false, false);
      }
      boolean frozen = cold >= FROZEN;
      if (frozen && kind != Kind.HEAT && kind != Kind.COLD && amount >= SHATTER_HIT) {
         // brittle metal breaks apart: big core damage, the shell is gone
         return new Split(integrity, Math.max(amount * 2.0F, maxCore * 0.4F), -cold, true, true);
      }
      float shell;
      float core;
      float addCold = 0.0F;
      boolean heavy = false;
      switch (kind) {
         case HEAVY -> {
            shell = amount * 1.2F;
            core = amount * HEAVY_BLEED;
            heavy = true;
         }
         case HEAT -> {
            shell = amount * 0.5F;
            core = amount * HEAT_BLEED;
         }
         case COLD -> {
            shell = amount * 0.5F;
            core = amount * 0.05F;
            addCold = amount * 0.09F;
         }
         default -> {
            shell = amount;
            core = amount * BLEED;
            heavy = amount >= HEAVY_HIT;
            if (heavy) {
               core = amount * HEAVY_BLEED;
            }
         }
      }
      if (puddle) {
         // nothing left to absorb: everything goes to the core, doubled
         core = (core + shell * 0.25F) * 2.0F;
         shell = 0.0F;
      } else if (shell > integrity) {
         // what the shell could not take reaches the core at half strength
         core += (shell - integrity) * 0.5F;
         shell = integrity;
      }
      if (!puddle && !frozen) {
         core = Math.min(core, maxCore * CORE_CAP);
      }
      return new Split(shell, core, addCold, heavy, false);
   }

   /** Integrity after one tick of regeneration. */
   public static float regen(float integrity, int ticksSinceHit, boolean puddle, boolean heated, float cold) {
      if (heated || cold >= FROZEN || ticksSinceHit < (puddle ? 0 : REGEN_DELAY)) {
         return integrity;
      }
      return Math.min(MAX_INTEGRITY, integrity + (puddle ? PUDDLE_REGEN_PER_TICK : REGEN_PER_TICK));
   }

   /** Core regeneration per tick: slow in a fight, faster while disguised and calm. */
   public static float coreRegen(boolean disguised, int ticksSinceHit) {
      if (ticksSinceHit < 200) {
         return 0.0F;
      }
      return disguised ? 0.05F : 0.0125F;
   }

   /** Cold decays every tick. */
   public static float thaw(float cold) {
      return Math.max(0.0F, cold - 0.006F);
   }
}
