package dev.baranhan.viltrumitecore.hero.sinclair;

import dev.baranhan.viltrumitecore.hero.DivineTargets;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.SinclairOwned;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.entity.PartEntity;

/** Server damage and target rules shared by Sinclair's abilities and entities (spec §4, §5, §7). */
public final class SinclairCombat {
   private SinclairCombat() {
   }

   /** The living body behind a hit entity (multipart parents included), or null. */
   @Nullable
   public static LivingEntity living(@Nullable Entity entity) {
      if (entity instanceof PartEntity<?> part) {
         entity = part.getParent();
      }

      return entity instanceof LivingEntity living ? living : null;
   }

   /** A fair target of a Sinclair trick: alive, not him, not his own entities, not a spectator. */
   public static boolean enemy(ServerPlayer owner, @Nullable LivingEntity target) {
      if (target == null || !target.isAlive() || target.isRemoved() || SinclairOwned.ownedBy(target, owner.getUUID())) {
         return false;
      }

      if (target instanceof Player player && (player.isSpectator() || player.isCreative() && !player.isAlive())) {
         return false;
      }

      return !target.isAlliedTo(owner);
   }

   /** §5.4: who the rabbit hex may turn (not bosses, not absolute or divine, not STASIS-immune heroes). */
   public static boolean hexable(ServerPlayer owner, LivingEntity target) {
      if (!enemy(owner, target) || target instanceof Player player && player.isSpectator()) {
         return false;
      }

      TagKey<EntityType<?>> bosses = Tags.EntityTypes.BOSSES;
      return !target.getType().is(bosses) && !DivineTargets.isAbsolutelyInvulnerable(target) && !DivineTargets.isDivine(target)
         && HeroRegistry.allowsExternalControl(target, ControlKind.STASIS);
   }

   /** Projectile damage credited to Sinclair (§7). True when the hit landed. */
   public static boolean projectileHit(ServerPlayer owner, Projectile projectile, LivingEntity target, float damage) {
      if (!enemy(owner, target)) {
         return false;
      }

      DamageSource source = owner.damageSources().mobProjectile(projectile, owner);
      // Bursts land faster than the vanilla hurt cooldown; every spark counts (as in the canon).
      target.invulnerableTime = 0;
      return target.hurt(source, damage);
   }

   /** Direct act / trick damage credited to Sinclair. True when the hit landed. */
   public static boolean trickHit(ServerPlayer owner, LivingEntity target, float damage) {
      if (!enemy(owner, target)) {
         return false;
      }

      target.invulnerableTime = 0;
      return target.hurt(owner.damageSources().indirectMagic(owner, owner), damage);
   }

   /** §5.1 "Vexed": −25 % speed for 1 s. */
   public static void vex(LivingEntity target) {
      Daze.slow(target, Daze.VEX_ID, "Sinclair vexed", SinclairRules.BOLT_SLOW, SinclairRules.BOLT_SLOW_TICKS);
   }

   /** §5.5 "Dazzled": −40 % speed 30 t, a mob forgets its target for 40 t. */
   public static void daze(LivingEntity target) {
      Daze.slow(target, Daze.DAZE_ID, "Sinclair dazzled", SinclairRules.DAZE_SLOW, SinclairRules.DAZE_TICKS);
      Daze.blind(target, SinclairRules.DAZE_MOB_TARGET_TICKS);
   }

   /** Push a target away from a point, only when its hero allows impulses. */
   public static void shove(LivingEntity target, Vec3 from, double horizontal, double up) {
      if (!HeroRegistry.allowsImpulse(target)) {
         return;
      }

      Vec3 away = target.position().subtract(from).multiply(1.0, 0.0, 1.0);
      away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 0.0) : away.normalize().scale(horizontal);
      target.setDeltaMovement(target.getDeltaMovement().add(away.x, up, away.z));
      target.hurtMarked = true;
   }

   /** Living enemies inside a sphere around a point. */
   public static List<LivingEntity> enemiesNear(ServerPlayer owner, Vec3 at, double radius) {
      AABB box = new AABB(at, at).inflate(radius);
      return owner.level().getEntitiesOfClass(LivingEntity.class, box, e -> enemy(owner, e) && e.getBoundingBox().getCenter().distanceToSqr(at) <= radius * radius + e.getBbWidth());
   }

   /** Any living enemy within a radius (Ovation's "next to an enemy"). */
   public static boolean enemyNear(ServerPlayer owner, Vec3 at, double radius) {
      return !enemiesNear(owner, at, radius).isEmpty();
   }

   /** Shared contact flash (HeroFx, synced to trackers). */
   public static void flash(ServerPlayer owner, Vec3 at, float power) {
      HeroFx.flash(owner, at, power);
   }

   /** Owner id helper that tolerates nulls. */
   public static boolean same(@Nullable UUID a, @Nullable UUID b) {
      return a != null && a.equals(b);
   }
}
