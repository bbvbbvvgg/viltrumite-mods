package dev.baranhan.viltrumitecore.hero.reinhard;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

/**
 * One target rule for every Reinhard hit and area of stages 3–6: living, alive,
 * not himself, not a spectator, not a creative player, not allied, and players
 * only when the server allows PvP against them. A {@link PartEntity} (dragon
 * part) answers for its parent.
 */
public final class ReinhardTargets {
   private ReinhardTargets() {
   }

   /** What the rule needs to know about a candidate (pure core, unit tested). */
   public record Facts(boolean living, boolean alive, boolean self, boolean spectator, boolean creativePlayer, boolean allied, boolean player,
      boolean pvpAllowed) {
   }

   public static boolean canHit(Facts f) {
      if (!f.living() || !f.alive() || f.self() || f.spectator() || f.allied()) {
         return false;
      }

      return !f.player() || (!f.creativePlayer() && f.pvpAllowed());
   }

   public static boolean canHit(ServerPlayer self, @Nullable Entity entity) {
      Entity e = resolve(entity);
      if (e == null) {
         return false;
      }

      boolean player = e instanceof Player;
      Player other = player ? (Player) e : null;
      return canHit(new Facts(e instanceof LivingEntity, e.isAlive(), e == self, e.isSpectator(), other != null && other.isCreative(),
         self.isAlliedTo(e) || e.isAlliedTo(self), player, other != null && self.canHarmPlayer(other)));
   }

   /** The entity a hit lands on: a part entity's parent, else itself. */
   @Nullable
   public static Entity resolve(@Nullable Entity entity) {
      return entity instanceof PartEntity<?> part ? part.getParent() : entity;
   }

   /** Server: the hittable living entity under the crosshair within {@code reach} (blocks stop the ray). */
   @Nullable
   public static LivingEntity inReach(ServerPlayer self, double reach) {
      return lookTarget(self, reach, e -> canHit(self, e));
   }

   /**
    * Both sides: the first living entity (part entities resolved to the parent) on the look
    * ray within {@code range}, accepted by {@code filter}; blocks (collider shapes) stop the ray.
    */
   @Nullable
   public static LivingEntity lookTarget(Player self, double range, Predicate<Entity> filter) {
      Vec3 eye = self.getEyePosition();
      Vec3 end = eye.add(self.getLookAngle().scale(range));
      BlockHitResult block = self.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self));
      Vec3 to = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
      EntityHitResult hit = ProjectileUtil.getEntityHitResult(self, eye, to, new AABB(eye, to).inflate(1.0),
         e -> e != self && !e.isSpectator() && resolve(e) instanceof LivingEntity && filter.test(resolve(e)), eye.distanceToSqr(to));
      return hit != null && resolve(hit.getEntity()) instanceof LivingEntity living ? living : null;
   }
}
