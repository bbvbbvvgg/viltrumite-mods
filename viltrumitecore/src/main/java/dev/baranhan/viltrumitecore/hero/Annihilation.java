package dev.baranhan.viltrumitecore.hero;

import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.entity.PartEntity;

/**
 * The Reid strike (Reinhard spec §1.3): a hit that kills every mortal target.
 * Its damage type {@code viltrumitecore:annihilation} bypasses invulnerability,
 * armor, shields, enchantments, effects and resistance, so totems, armor and
 * every hero layer (Hulkbuster, marks, nano) step aside; inside its scope the
 * hero HP floors on unblockable damage step aside too (like {@link AdminKill}).
 * It never touches a divine target and never pierces absolute invulnerability
 * (Lion's Heart, spec §3.3). Hero-neutral: any hero may strike with it.
 */
public final class Annihilation {
   private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

   public enum Outcome {
      KILLED,
      BLOCKED_ABSOLUTE,
      IMMUNE_DIVINE,
      SKIPPED;
   }

   private Annihilation() {
   }

   /** Pure: divine first, then absolute invulnerability, then creative / spectator or dead targets. */
   public static Outcome decide(DivineTargets.Verdict verdict, boolean creativeOrSpectator, boolean alive) {
      if (verdict == DivineTargets.Verdict.DIVINE) {
         return Outcome.IMMUNE_DIVINE;
      }

      if (verdict == DivineTargets.Verdict.ABSOLUTE) {
         return Outcome.BLOCKED_ABSOLUTE;
      }

      return creativeOrSpectator || !alive ? Outcome.SKIPPED : Outcome.KILLED;
   }

   /** Scope of a Reid strike; nests and closes on a throw. */
   public static void run(Runnable action) {
      int[] depth = DEPTH.get();
      depth[0]++;
      try {
         action.run();
      } finally {
         depth[0]--;
      }
   }

   public static boolean active() {
      return DEPTH.get()[0] > 0;
   }

   public static ResourceKey<DamageType> type() {
      return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("viltrumitecore", "annihilation"));
   }

   public static DamageSource source(ServerLevel level, Entity attacker) {
      return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type()), attacker);
   }

   /**
    * One Reid strike. A part (dragon) strikes its parent. KILLED: {@code hurt(MAX)}
    * inside the scope; when a mod cancelled the hurt, the target dies directly.
    * BLOCKED_ABSOLUTE: a spark at the target, no damage.
    */
   public static Outcome strike(@javax.annotation.Nullable ServerPlayer attacker, Entity target) {
      Entity resolved = target instanceof PartEntity<?> part ? part.getParent() : target;
      if (!(resolved instanceof LivingEntity living)) {
         return Outcome.SKIPPED;
      }

      boolean creative = living instanceof Player player && (player.isCreative() || player.isSpectator());
      Outcome outcome = decide(DivineTargets.verdict(living), creative, living.isAlive());
      if (outcome == Outcome.BLOCKED_ABSOLUTE) {
         HeroFx.flash((ServerLevel)living.level(), living.position().add(0.0, living.getBbHeight() * 0.6, 0.0));
      } else if (outcome == Outcome.KILLED) {
         ServerLevel level = (ServerLevel)living.level();
         DamageSource source = source(level, attacker);
         run(() -> {
            living.invulnerableTime = 0;
            living.hurt(source, Float.MAX_VALUE);
            if (living.isAlive()) {
               living.setHealth(0.0F);
               living.die(source);
            }
         });
      }

      return outcome;
   }
}
