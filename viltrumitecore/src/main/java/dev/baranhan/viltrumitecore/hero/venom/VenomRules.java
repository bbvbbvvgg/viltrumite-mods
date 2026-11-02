package dev.baranhan.viltrumitecore.hero.venom;

import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import net.minecraft.util.Mth;

/**
 * Venom's numbers (spec docs/design/2026-11-02-venom-design.md §6). Server-authoritative; the clients only read the
 * snapshot. Ticks unless noted. Pure helpers are tested by VenomRulesTest.
 */
public final class VenomRules {
   // ---- body ----
   public static final double HEALTH_BONUS = 20.0;     // 40 HP in symbiote form
   public static final double ARMOR = 6.0;
   public static final double TOUGHNESS = 2.0;
   public static final double KNOCKBACK_RESIST = 0.35;
   public static final double SPEED_BONUS = 0.18;
   public static final float BODY_HEIGHT_SCALE = 1.18F; // 2.12 blocks
   public static final float BODY_WIDTH_SCALE = 1.16F;
   public static final float RAGE_HEIGHT_SCALE = 1.28F;
   public static final float RAGE_WIDTH_SCALE = 1.24F;
   public static final double REACH_BONUS = 0.75;
   public static final float SUPER_JUMP = 1.45F;        // ~11 blocks
   public static final float RAGE_SUPER_JUMP = 1.7F;
   public static final double WALL_CLIMB_SPEED = 0.24;
   public static final double WALL_RUN_SPEED = 0.42;

   // ---- transformation ----
   public static final int TRANSFORM_TICKS = 46;
   /** Stage borders of the transformation (spec §3): limbs, torso + head, proportions, face + emblem, pose + roar. */
   public static final int STAGE_LIMBS = 0, STAGE_TORSO = 12, STAGE_GROW = 22, STAGE_FACE = 32, STAGE_POSE = 40;
   public static final int REVERT_TICKS = 30;
   public static final int REFORM_TICKS = 14;

   // ---- hunger (resource 0..1000) ----
   public static final int HUNGER_MAX = 1000;
   public static final int HUNGER_CLAW = 45;
   public static final int HUNGER_LASH = 35;
   public static final int HUNGER_WEB = 110;
   public static final int HUNGER_KILL = 220;
   public static final int HUNGER_DECAY_IDLE = 1;      // per 10 ticks out of combat
   public static final int RAGE_MIN = 500;
   public static final int RAGE_DRAIN = 3;             // per tick in rage
   public static final int RAGE_MAX_TICKS = 300;
   public static final int SHELL_DRAIN = 2;

   // ---- regeneration ----
   public static final int REGEN_DELAY = 60;
   public static final float REGEN_PER_TICK = 0.05F;   // 1 HP/s
   public static final float RAGE_REGEN_PER_TICK = 0.1F;
   public static final int FIRE_REGEN_BLOCK = 100;
   /** hellfire_scalds_symbiote: extra damage factor of a hellfire hit and the regeneration stop (5 s). */
   public static final float SCALD_DAMAGE = 1.25F;
   public static final int SCALD_REGEN_BLOCK = 100;

   // ---- weaknesses ----
   public static final float FIRE_TAKEN = 1.75F;
   public static final float SONIC_TAKEN = 1.5F;
   /** A loud sound this close (blocks) at this volume makes the symbiote recoil. */
   public static final double SOUND_RADIUS = 10.0;
   public static final float SOUND_MIN_VOLUME = 2.0F;
   public static final int RECOIL_TICKS = 24;
   public static final int RECOIL_IMMUNITY = 60;

   // ---- claws (LMB) ----
   public static final int COMBO_LENGTH = 3;
   public static final float[] CLAW_DAMAGE = {7.0F, 7.0F, 11.0F};
   public static final double CLAW_REACH = 3.6;
   public static final double CLAW_ARC_DEG = 110.0;
   public static final int COMBO_WINDOW = 14;

   // ---- tendrils ----
   public static final double TENDRIL_RANGE = 28.0;
   public static final double ZIP_SPEED = 1.55;
   public static final int ZIP_MAX_TICKS = 22;
   public static final double PULL_SPEED = 1.35;
   public static final double GRAB_RANGE = 7.0;
   public static final int HOLD_MAX_TICKS = 80;
   public static final double THROW_SPEED = 2.2;
   public static final float SLAM_DAMAGE = 12.0F;
   public static final float THROW_IMPACT_DAMAGE = 8.0F;
   public static final double LASH_SWEEP_RADIUS = 4.6;
   public static final float LASH_SWEEP_DAMAGE = 8.0F;
   public static final double LASH_IMPALE_RANGE = 7.5;
   public static final float LASH_IMPALE_DAMAGE = 12.0F;
   public static final double LASH_SLAM_RADIUS = 5.0;
   public static final float LASH_SLAM_DAMAGE = 13.0F;
   /** Capsule radius of a claw or tendril against a Spider's web line. */
   public static final double LINE_CUT_RADIUS = 0.6;
   public static final double CLASH_RANGE = 3.2;
   public static final int CLASH_TICKS = 8;

   // ---- shell ----
   public static final float SHELL_ABSORB = 0.6F;
   public static final int SHELL_MAX_TICKS = 120;
   public static final double SHELL_SLOW = -0.4;

   // ---- rage ----
   public static final float RAGE_DAMAGE = 1.3F;
   public static final double RAGE_SPEED = 0.2;

   // ---- host takeover ----
   public static final double TAKEOVER_RANGE = 4.5;
   public static final int TAKEOVER_COST = 250;
   /** The boss rides a host for a shorter time. */
   public static final int BOSS_CONTROL_TICKS = 120;
   /** After a release the host is safe from the ground, lava and fire this long (nothing the driving did can kill). */
   public static final int HOST_SAFE_TICKS = 60;
   public static final float TAKEOVER_HEALTH_FRACTION = 0.6F;
   public static final int ENGULF_TICKS = 30;
   public static final int RELEASE_TICKS = 16;
   public static final int RESIST_TO_BREAK = 100;
   public static final int RESIST_PER_PRESS = 6;
   public static final int RESIST_DECAY = 1;           // per 4 ticks
   public static final int RESIST_PRESS_GAP = 2;       // at most one counted press per 2 ticks
   public static final double TAKEOVER_LEASH = 32.0;
   public static final int VICTIM_IMMUNITY = 1200;     // 60 s anti-chain
   public static final float HUSK_DAMAGE_TAKEN = 1.5F;
   public static final float HUSK_BREAK_DAMAGE = 4.0F;
   public static final double DRIVEN_ATTACK_REACH = 3.4;
   public static final int DRIVEN_ATTACK_GAP = 10;

   // ---- cooldowns (indices into VenomState.cooldowns) ----
   public static final int CD_TRANSFORM = 0, CD_TENDRIL = 1, CD_LASH = 2, CD_SHELL = 3, CD_RAGE = 4, CD_TAKEOVER = 5, CD_GRAB = 6;
   public static final int COOLDOWNS = 7;
   public static final int[] COOLDOWN_TICKS = {40, 26, 70, 100, 400, 1200, 24};

   private VenomRules() {
   }

   /** Control phase length from the config (3..15 s). */
   public static int controlTicks() {
      double s = ViltrumiteCoreConfig.INSTANCE == null ? 10.0 : ViltrumiteCoreConfig.INSTANCE.venomPossessionMaxSeconds;
      return controlTicks(s);
   }

   /** Pure. */
   public static int controlTicks(double seconds) {
      if (!Double.isFinite(seconds)) {
         seconds = 10.0;
      }
      return (int)Math.round(Mth.clamp(seconds, 3.0, 15.0) * 20.0);
   }

   /** Pure: the transformation stage at a tick (0 limbs .. 4 pose). */
   public static int stage(int tick) {
      if (tick >= STAGE_POSE) return 4;
      if (tick >= STAGE_FACE) return 3;
      if (tick >= STAGE_GROW) return 2;
      if (tick >= STAGE_TORSO) return 1;
      return 0;
   }

   /** Pure: resistance after one press (rate-limited by the caller) and with decay. */
   public static int resist(int current, boolean press, int decayTicks) {
      int v = current + (press ? RESIST_PER_PRESS : 0) - decayTicks / 4 * RESIST_DECAY;
      return Mth.clamp(v, 0, RESIST_TO_BREAK);
   }

   /** Pure: is a target weakened enough for a takeover (health fraction, or held / cocooned / stunned). */
   public static boolean weakened(float health, float maxHealth, boolean held) {
      return held || maxHealth > 0 && health / maxHealth <= TAKEOVER_HEALTH_FRACTION;
   }

   /** Pure: incoming damage factor for a symbiote body (fire / sonic weakness, shell). */
   public static float incomingFactor(boolean fire, boolean sonic, boolean shell) {
      if (fire) {
         return FIRE_TAKEN; // fire burns through the shell
      }
      float f = sonic ? SONIC_TAKEN : 1.0F;
      return shell && !sonic ? f * (1.0F - SHELL_ABSORB) : f;
   }

   /** Pure: fall damage in form - the symbiote takes the shock of anything below 40 blocks. */
   public static float fallDamage(float fallDistance) {
      return Math.max(0.0F, fallDistance - 40.0F) * 0.5F;
   }

   /** Pure: hunger after a gain, clamped. */
   public static int feed(int hunger, int gain) {
      return Mth.clamp(hunger + gain, 0, HUNGER_MAX);
   }
}
