package dev.baranhan.viltrumitecore.hero.ironman.mark.sig;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.ironman.IronManSounds;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.FlashS2CPacket;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server rays and hit helpers for the signatures. Same geometry as the Stage 2
 * {@code IronManCombat} helpers, which stay private there.
 */
public final class SignatureTrace {
   /** First living target or the block end of a shot. */
   public record Hit(@Nullable LivingEntity target, Vec3 end, boolean block) {
   }

   private SignatureTrace() {
   }

   public static Vec3 right(Vec3 look) {
      Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
      return right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
   }

   /** Forearm height in front of the shoulder: where micro-lasers and the rocket fist leave. */
   public static Vec3 hand(ServerPlayer player, boolean rightHand) {
      Vec3 look = player.getLookAngle();
      Vec3 side = right(look).scale(rightHand ? 0.38 : -0.38);
      return player.getEyePosition().add(0.0, -0.35, 0.0).add(side).add(look.scale(0.7));
   }

   /** Arc reactor: the Unibeam origin. */
   public static Vec3 chest(ServerPlayer player) {
      Vec3 look = player.getLookAngle();
      double height = player.isCrouching() ? 1.05 : 1.32;
      return player.position().add(0.0, height, 0.0).add(look.x * 0.3, 0.0, look.z * 0.3);
   }

   /** Ray from the eye along {@code dir}: the first living target, or the block that stops it. */
   public static Hit shoot(ServerPlayer player, Vec3 dir, double range) {
      return shoot(player.serverLevel(), player, player.getEyePosition(), dir, range);
   }

   /** Same ray for any shooter (Legion suits): from {@code eye}, ignoring the shooter itself. */
   public static Hit shoot(SignatureActor actor, Vec3 dir, double range) {
      return shoot(actor.level(), actor.body(), actor.eye(), dir, range);
   }

   private static Hit shoot(ServerLevel level, net.minecraft.world.entity.Entity shooter, Vec3 eye, Vec3 dir, double range) {
      Vec3 end = eye.add(dir.scale(range));
      BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
      boolean blocked = block.getType() == HitResult.Type.BLOCK;
      Vec3 to = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
      EntityHitResult entity = ProjectileUtil.getEntityHitResult(shooter, eye, to, new AABB(eye, to).inflate(1.0),
         e -> e instanceof LivingEntity && e.isAlive() && e != shooter && !e.isSpectator(), eye.distanceToSqr(to));
      if (entity != null && entity.getEntity() instanceof LivingEntity living) {
         return new Hit(living, entity.getLocation(), false);
      }

      return new Hit(null, to, blocked);
   }

   /** Direction turned by a random angle up to {@code degrees} around {@code look}. */
   public static Vec3 spread(Vec3 look, RandomSource random, double degrees) {
      Vec3 across = right(look);
      Vec3 up = across.cross(look).normalize();
      double angle = Math.toRadians(degrees) * Math.sqrt(random.nextDouble());
      double turn = random.nextDouble() * Math.PI * 2.0;
      Vec3 offset = across.scale(Math.cos(turn)).add(up.scale(Math.sin(turn))).scale(Math.tan(angle));
      return look.add(offset).normalize();
   }

   /** Knock-back that respects the impulse policy of the target. */
   public static void push(LivingEntity target, Vec3 velocity) {
      if (!HeroRegistry.allowsImpulse(target)) {
         return;
      }

      target.setDeltaMovement(target.getDeltaMovement().add(velocity));
      target.hurtMarked = true;
   }

   /** Damage that also lands inside the invulnerable window (beams and bullets hit every few ticks). */
   public static boolean strike(LivingEntity target, DamageSource source, float amount) {
      target.invulnerableTime = 0;
      return target.hurt(source, amount);
   }

   public static void flash(ServerPlayer player, float strength, int ticks) {
      CoreMessages.sendToPlayer(new FlashS2CPacket(strength, ticks), player);
   }

   public static boolean shot(LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount) {
      target.invulnerableTime=0;
      return dev.baranhan.viltrumitecore.hero.HeroAttackContext.hurt(target,source,amount,dev.baranhan.viltrumitecore.hero.HeroAttackContext.Kind.SHOT);
   }
   public static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
      IronManSounds.play(player, sound, volume, pitch);
   }
}
