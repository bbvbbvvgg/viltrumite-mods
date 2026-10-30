package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.hero.HeroDestruction;
import dev.baranhan.viltrumitecore.hero.dio.DioTimeStop;
import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusFxS2CPacket;
import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusNet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Shared server combat helpers of Regulus: target filters, line of sight, hits, block breaking. */
public final class RegulusCombat {
   private RegulusCombat() {
   }

   /** May an attack of this Regulus hurt {@code entity}? Never himself, allies, spectators or his own heart subjects. */
   public static boolean hittable(ServerPlayer self, @Nullable Entity entity, RegulusState state) {
      if (!(entity instanceof LivingEntity living) || entity == self || !living.isAlive() || living.isSpectator() || entity instanceof ArmorStand) {
         return false;
      }

      if (living instanceof Player player && (player.isCreative() || self.isAlliedTo(player))) {
         return false;
      }

      return !state.subjects.contains(entity.getUUID());
   }

   public static boolean sees(ServerPlayer self, Vec3 from, Entity target) {
      Vec3 to = target.getBoundingBox().getCenter();
      return self.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self)).getType() == HitResult.Type.MISS
         || self.level().clip(new ClipContext(from, target.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self)).getType()
         == HitResult.Type.MISS;
   }

   /** Targets in a cone in front of the eyes (sorted nearest first). Pure geometry over live positions. */
   public static List<LivingEntity> cone(ServerPlayer self, RegulusState state, double reach, double coneDegrees, int max) {
      Vec3 eye = self.getEyePosition();
      Vec3 look = self.getLookAngle();
      double cos = Math.cos(Math.toRadians(coneDegrees / 2.0));
      List<LivingEntity> out = new ArrayList<>();
      for (Entity entity : self.level().getEntities(self, self.getBoundingBox().inflate(reach + 1.0))) {
         if (!hittable(self, entity, state)) {
            continue;
         }

         AABB box = entity.getBoundingBox();
         Vec3 closest = new Vec3(clamp(eye.x, box.minX, box.maxX), clamp(eye.y, box.minY, box.maxY), clamp(eye.z, box.minZ, box.maxZ));
         Vec3 to = closest.subtract(eye);
         double dist = to.length();
         if (dist > reach) {
            continue;
         }

         Vec3 toCenter = box.getCenter().subtract(eye);
         boolean inside = dist < 0.4 || inCone(look, to, cos) || inCone(look, toCenter, cos);
         if (inside && sees(self, eye, entity)) {
            out.add((LivingEntity) entity);
         }
      }

      out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(self)));
      return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
   }

   static boolean inCone(Vec3 look, Vec3 to, double cos) {
      double len = to.length();
      return len < 1.0E-6 || look.dot(to) / len >= cos;
   }

   static double clamp(double v, double lo, double hi) {
      return v < lo ? lo : v > hi ? hi : v;
   }

   /** One validated hit with the player-attack source; returns true when it landed. */
   public static boolean hit(ServerPlayer self, LivingEntity target, float damage) {
      boolean landed = target.hurt(self.damageSources().playerAttack(self), damage);
      if (landed) {
         self.setLastHurtMob(target);
      }

      return landed;
   }

   public static void push(LivingEntity target, Vec3 dir, double horizontal, double up) {
      if (target.isRemoved()) {
         return;
      }

      Vec3 flat = new Vec3(dir.x, 0.0, dir.z);
      flat = flat.lengthSqr() < 1.0E-6 ? Vec3.ZERO : flat.normalize();
      double resist = 1.0 - target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
      if (resist <= 0.0) {
         return;
      }

      target.setDeltaMovement(target.getDeltaMovement().scale(0.3).add(flat.scale(horizontal * resist)).add(0.0, up * resist, 0.0));
      target.hasImpulse = true;
      target.hurtMarked = true;
   }

   /**
    * Breaks one block for a Regulus attack when the world allows it (mobGriefing, unbreakable,
    * loaded chunk) and the hardness is within the attack's limit; the fragments are shown stopped
    * in time for a moment (client BLOCK_STOP). Returns true when the block went.
    */
   public static boolean breakBlock(ServerPlayer self, ServerLevel level, BlockPos pos, float hardnessLimit) {
      if (!level.isLoaded(pos) || !HeroDestruction.canDestroy(level, pos) || !level.mayInteract(self, pos)) {
         return false;
      }

      BlockState state = level.getBlockState(pos);
      if (!state.getFluidState().isEmpty() && state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
         return false;
      }

      if (!RegulusRules.breakable(state.getDestroySpeed(level, pos), hardnessLimit) || level.getBlockEntity(pos) != null) {
         return false;
      }

      // adventure mode, claims and protection mods: the same gates as his own hand breaking it
      if (self.blockActionRestricted(level, pos, self.gameMode.getGameModeForPlayer())
         || net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, pos, state, self))) {
         return false;
      }

      if (!HeroDestruction.destroyBlock(level, pos)) {
         return false;
      }

      RegulusNet.fx(level, RegulusFxS2CPacket.BLOCK_STOP, self, -1, Vec3.atCenterOf(pos), Vec3.ZERO, 1.0F, Block.getId(state));
      return true;
   }

   /** DIO's stopped time covers this point: Regulus' own moving objects hold still there too. */
   public static boolean timeStoppedAt(ServerLevel level, Vec3 point) {
      return DioTimeStop.covering(level, point) != null;
   }
}
