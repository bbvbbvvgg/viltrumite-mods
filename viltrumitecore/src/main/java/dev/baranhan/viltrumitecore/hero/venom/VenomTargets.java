package dev.baranhan.viltrumitecore.hero.venom;

import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderTargets;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Target rules of Venom (server). The legality rules (PvP, pets, summons, bystanders) are the shared ones the Ghost
 * Rider introduced; Venom adds the symbiote's points on the body (shoulders the tendrils grow from, claws).
 */
public final class VenomTargets {
   private VenomTargets() {
   }

   public static boolean mayHit(@Nullable ServerPlayer owner, LivingEntity e) {
      return GhostRiderTargets.mayHit(owner, e);
   }

   /** Area hits: never city bystanders or innocent souls (shared rule). */
   public static boolean mayAutoHit(ServerPlayer owner, LivingEntity e) {
      return GhostRiderTargets.mayAutoHit(owner, e);
   }

   public static boolean isBoss(@Nullable Entity e) {
      return e instanceof dev.baranhan.viltrumitecore.hero.venom.boss.VenomBossEntity || GhostRiderTargets.isBoss(e);
   }

   public static List<LivingEntity> arc(ServerPlayer player, Vec3 from, Vec3 axis, double reach, double degrees, Predicate<LivingEntity> ok) {
      return GhostRiderTargets.arc(player, from, axis, reach, degrees, ok);
   }

   public static List<LivingEntity> around(Entity source, Vec3 at, double radius, Predicate<LivingEntity> ok) {
      return GhostRiderTargets.around(source, at, radius, ok);
   }

   public static List<LivingEntity> along(Entity source, Vec3 from, Vec3 to, double width, Predicate<LivingEntity> ok) {
      return GhostRiderTargets.along(source, from, to, width, ok);
   }

   @Nullable
   public static LivingEntity aimed(List<LivingEntity> candidates, Vec3 eye, Vec3 look, double degrees) {
      return GhostRiderTargets.aimed(candidates, eye, look, degrees);
   }

   public static boolean visible(Level level, Vec3 from, Vec3 to) {
      return GhostRiderTargets.visible(level, from, to);
   }

   public static Vec3 clip(Entity viewer, Vec3 from, Vec3 to) {
      return GhostRiderTargets.clip(viewer, from, to);
   }

   /** One blow through the attacker's melee source (a player or the boss); ignores i-frames. */
   public static boolean strike(LivingEntity attacker, LivingEntity target, float damage) {
      target.invulnerableTime = 0;
      DamageSource source = attacker instanceof Player p ? attacker.damageSources().playerAttack(p) : attacker.damageSources().mobAttack(attacker);
      return target.hurt(source, damage);
   }

   public static void push(LivingEntity target, Vec3 velocity) {
      GhostRiderTargets.push(target, velocity);
   }

   public static boolean stun(LivingEntity caster, LivingEntity target, int ticks) {
      return GhostRiderTargets.stun(caster, target, ticks);
   }

   public static Vec3 flat(Vec3 v) {
      return GhostRiderTargets.flat(v);
   }

   public static Vec3 outward(Vec3 from, Entity e) {
      return GhostRiderTargets.outward(from, e);
   }

   public static Vec3 center(Entity e) {
      return e.getBoundingBox().getCenter();
   }

   /** A shoulder of the body (side +1 = right, -1 = left), where the back tendrils grow from. */
   public static Vec3 shoulder(Entity body, int side) {
      Vec3 flat = Vec3.directionFromRotation(0, body.getYRot());
      Vec3 right = new Vec3(-flat.z, 0, flat.x);
      return body.position().add(0, body.getBbHeight() * 0.78, 0).add(right.scale(0.42 * side)).subtract(flat.scale(0.12));
   }

   /** The claw hand (right) in the world. */
   public static Vec3 hand(Entity body) {
      Vec3 flat = Vec3.directionFromRotation(0, body.getYRot());
      Vec3 right = new Vec3(-flat.z, 0, flat.x);
      return body.position().add(0, body.getBbHeight() * 0.6, 0).add(right.scale(0.45)).add(flat.scale(0.35));
   }

   /** Moves a body along the path, stopping at the first block (never through walls). */
   public static void place(LivingEntity t, Vec3 at) {
      Vec3 before = t.position();
      Vec3 safe = dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderCombat.sweep(before, at, 0.6,
         p -> t.level().noCollision(t, t.getDimensions(t.getPose()).makeBoundingBox(p)));
      t.setPos(safe.x, safe.y, safe.z);
      t.setDeltaMovement(safe.subtract(before).scale(0.5));
      t.resetFallDistance();
      t.hurtMarked = true;
      if (t instanceof net.minecraft.world.entity.Mob mob) {
         mob.getNavigation().stop();
      }
   }

   /** A hit of fire (fire, lava, a burning blow): the symbiote's first weakness. */
   public static boolean fire(DamageSource source) {
      return source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) || source.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING);
   }

   /** A sonic hit (sonic boom, explosions' blast wave): the second weakness. */
   public static boolean sonic(DamageSource source) {
      return source.is(net.minecraft.world.damagesource.DamageTypes.SONIC_BOOM) || source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
   }
}
