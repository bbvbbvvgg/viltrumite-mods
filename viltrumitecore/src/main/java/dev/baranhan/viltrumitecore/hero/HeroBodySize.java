package dev.baranhan.viltrumitecore.hero;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Hero body size (both sides): the per-pose hitbox and eye height of a scaled hero body
 * ({@link HeroDefinition#bodyScale} height, {@link HeroDefinition#bodyWidthScale} width).
 * One table for the size event (HeroSizeEvents), the pose fit checks (EntityPoseBoxMixin: crouch /
 * crawl under a low ceiling instead of standing inside it) and the tests.
 * <ul>
 * <li>SLEEPING keeps the vanilla 0.2 box so the body lies in a bed like everyone's.</li>
 * <li>Lying poses (swim / crawl, elytra, riptide) grow at most {@link #LYING_MAX}: a big hero still
 * fits a one-block crawl space (0.6 × 1.5 = 0.9 tall).</li>
 * </ul>
 */
public final class HeroBodySize {
   /** Height factor cap of the lying poses. */
   public static final float LYING_MAX = 1.5F;

   private HeroBodySize() {
   }

   /** Width / height factor of one body; {@link #VANILLA} is 1 × 1. */
   public record Scale(float width, float height) {
      public static final Scale VANILLA = new Scale(1.0F, 1.0F);

      public boolean vanilla() {
         return this.width == 1.0F && this.height == 1.0F;
      }

      /** Non-finite or non-positive factors fall back to 1. */
      public static Scale of(float width, float height) {
         return new Scale(sane(width), sane(height));
      }

      private static float sane(float value) {
         return Float.isFinite(value) && value > 0.0F ? value : 1.0F;
      }
   }

   public static Scale of(Player player) {
      if (!(player instanceof HeroPlayer)) {
         return Scale.VANILLA;
      }

      HeroDefinition hero = HeroRegistry.get(player);
      return Scale.of(hero.bodyWidthScale(player), hero.bodyScale(player));
   }

   public static float heightFactor(Pose pose, Scale scale) {
      return switch (pose) {
         case SLEEPING -> 1.0F;
         case SWIMMING, FALL_FLYING, SPIN_ATTACK -> Math.min(scale.height(), LYING_MAX);
         default -> scale.height();
      };
   }

   public static float widthFactor(Pose pose, Scale scale) {
      return pose == Pose.SLEEPING ? 1.0F : scale.width();
   }

   public static EntityDimensions dimensions(EntityDimensions base, Pose pose, Scale scale) {
      if (scale.vanilla()) {
         return base;
      }

      return base.scale(widthFactor(pose, scale), heightFactor(pose, scale));
   }

   public static float eyeHeight(float base, Pose pose, Scale scale) {
      return base * heightFactor(pose, scale);
   }

   /** Compare actual dimensions and eye height with the current hero's expected pose. */
   public static final float MATCH_EPS = 1.0E-4F;

   public static boolean matches(float width, float height, float eye, EntityDimensions base,
                                 float baseEye, Pose pose, Scale scale) {
      EntityDimensions expected = dimensions(base, pose, scale);
      return Math.abs(width - expected.width) <= MATCH_EPS
         && Math.abs(height - expected.height) <= MATCH_EPS
         && Math.abs(eye - eyeHeight(baseEye, pose, scale)) <= MATCH_EPS;
   }

   /** The box Entity.getBoundingBoxForPose builds, for the scaled dimensions. */
   public static AABB box(Vec3 feet, EntityDimensions dimensions) {
      double half = dimensions.width / 2.0;
      return new AABB(feet.x - half, feet.y, feet.z - half, feet.x + half, feet.y + dimensions.height, feet.z + half);
   }

   /** Dismount bounds, relative to the entity's feet. */
   public static AABB localBox(EntityDimensions dimensions) {
      return box(Vec3.ZERO, dimensions);
   }
}
