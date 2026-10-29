package dev.baranhan.viltrumitecore.hero.sinclair;

import dev.baranhan.viltrumitecore.hero.sinclair.entity.AssistantEntity;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server geometry of Sinclair's body and aim (hand points match the model's wand/card bones). */
public final class Stagecraft {
   private Stagecraft() {
   }

   private static Vec3 right(ServerPlayer player) {
      Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
      return new Vec3(-look.z, 0.0, look.x);
   }

   /** The tip of Prestige in the right hand (muzzle of sparks and bolts). */
   public static Vec3 wandTip(ServerPlayer player) {
      return player.getEyePosition().add(player.getLookAngle().scale(0.55)).add(right(player).scale(0.32)).add(0.0, -0.22, 0.0);
   }

   /** The left palm (card, gestures). */
   public static Vec3 leftHand(ServerPlayer player) {
      return player.getEyePosition().add(player.getLookAngle().scale(0.45)).add(right(player).scale(-0.34)).add(0.0, -0.4, 0.0);
   }

   /** Crosshair point within range: first block or the ray's end. */
   public static Vec3 aimPoint(ServerPlayer player, double range) {
      Vec3 eye = player.getEyePosition();
      Vec3 end = eye.add(player.getLookAngle().scale(range));
      BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
   }

   @Nullable
   public static BlockHitResult blockHit(ServerPlayer player, double range) {
      Vec3 eye = player.getEyePosition();
      BlockHitResult hit = player.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      return hit.getType() == HitResult.Type.MISS ? null : hit;
   }

   /** First living enemy on the look ray before any block, or null. */
   @Nullable
   public static LivingEntity rayEnemy(ServerPlayer player, double range) {
      Vec3 eye = player.getEyePosition();
      Vec3 end = aimPoint(player, range);
      AABB box = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
      EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, box,
         e -> e instanceof LivingEntity living && SinclairCombat.enemy(player, living));
      return hit == null ? null : SinclairCombat.living(hit.getEntity());
   }

   /** The live assistant of a state, or null (clears a stale id). */
   @Nullable
   public static AssistantEntity assistant(ServerPlayer player, SinclairState state) {
      UUID id = state.assistant;
      if (id == null || !(player.level() instanceof ServerLevel level)) {
         return null;
      }

      Entity entity = level.getEntity(id);
      if (entity instanceof AssistantEntity assistant && !assistant.isRemoved()) {
         return assistant;
      }

      return null;
   }

   /** The assistant when she can act (on stage, alive, not leaving). */
   @Nullable
   public static AssistantEntity onStage(ServerPlayer player, SinclairState state) {
      AssistantEntity assistant = assistant(player, state);
      return assistant != null && assistant.onStage() ? assistant : null;
   }

   /** A small random tilt of a direction (spark spread, degrees). */
   public static Vec3 spread(Vec3 dir, double degrees, net.minecraft.util.RandomSource random) {
      double t = Math.tan(Math.toRadians(degrees));
      return dir.normalize().add(random.nextGaussian() * t * 0.5, random.nextGaussian() * t * 0.5, random.nextGaussian() * t * 0.5).normalize();
   }
}
