package dev.baranhan.viltrumitecore.client.thor;

import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirEntity;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirPose;
import javax.annotation.Nullable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Client lift anchor API (rework §3 / §6) for the "lift Mjölnir out of
 * the ground" animation (stages R1 / R2): where the palms go on a planted
 * hammer. Positions are world space, interpolated at {@code partialTick}
 * like the hammer's own render. The handle axis is always world +Y; the
 * head's yaw is given so the lifter can square up to the head. Common
 * numbers and the server twin: {@link MjolnirPose},
 * {@link MjolnirEntity#handleTop(float)}.
 */
public final class MjolnirAnchors {
   /** How far around a player a planted hammer is looked for. */
   public static final double SEARCH_RADIUS = 6.0;

   /**
    * One planted hammer's grip: {@code handleTop} = top of the handle
    * (pommel end); {@code upperPalm} / {@code lowerPalm} = palm centres on
    * the handle axis just below it; {@code up} = handle axis (world +Y);
    * {@code headYaw} = entity yaw of the head (degrees).
    */
   public record Grip(MjolnirEntity hammer, Vec3 rest, Vec3 handleTop, Vec3 upperPalm, Vec3 lowerPalm, Vec3 up, float headYaw) {
   }

   private MjolnirAnchors() {
   }

   /** Grip of a planted hammer, or null when it is not planted (in a hand, flying, stuck in a wall). */
   @Nullable
   public static Grip grip(MjolnirEntity hammer, float partialTick) {
      if (!hammer.planted()) {
         return null;
      }

      Vec3 rest = hammer.getPosition(partialTick);
      return new Grip(hammer, rest, MjolnirPose.handleTop(rest), MjolnirPose.upperPalm(rest), MjolnirPose.lowerPalm(rest), new Vec3(0.0, 1.0, 0.0),
         hammer.getViewYRot(partialTick));
   }

   /**
    * The grip of the planted hammer this player is trying to lift (synced
    * lifter ids, MjolnirLift), or null. Drives the lift attempt pose.
    */
   @Nullable
   public static Grip lifting(Player player, float partialTick) {
      MjolnirEntity hammer = planted(player);
      if (hammer == null) {
         return null;
      }

      for (int id : hammer.lifters()) {
         if (id == player.getId()) {
            return grip(hammer, partialTick);
         }
      }

      return null;
   }

   /** The grip of the nearest planted hammer around this player (e.g. Thor's pull-out), or null. */
   @Nullable
   public static Grip nearest(Player player, float partialTick) {
      MjolnirEntity hammer = planted(player);
      return hammer == null ? null : grip(hammer, partialTick);
   }

   @Nullable
   private static MjolnirEntity planted(Player player) {
      AABB box = player.getBoundingBox().inflate(SEARCH_RADIUS);
      MjolnirEntity best = null;
      double bestDistance = Double.MAX_VALUE;
      for (MjolnirEntity hammer : player.level().getEntitiesOfClass(MjolnirEntity.class, box, MjolnirEntity::planted)) {
         double distance = hammer.distanceToSqr(player);
         if (distance < bestDistance) {
            best = hammer;
            bestDistance = distance;
         }
      }

      return best;
   }
}
