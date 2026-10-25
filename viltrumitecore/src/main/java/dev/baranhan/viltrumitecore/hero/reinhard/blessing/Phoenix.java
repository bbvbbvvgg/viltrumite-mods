package dev.baranhan.viltrumitecore.hero.reinhard.blessing;

import dev.baranhan.viltrumitecore.hero.PersistentCooldowns;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Divine Protection of the Phoenix (spec §4.2): a lethal divine hit revives
 * him at full health at a point 6-10 blocks away (away from the attacker),
 * in flames; then {@link ReinhardRules#PHOENIX_COOLDOWN} online ticks in
 * {@link PersistentCooldowns}, so relog, hero change and death keep the timer.
 */
public final class Phoenix {
   /** Fire immunity after the revive (3 s). */
   static final int FIRE_IMMUNITY_TICKS = 60;
   private static final int ANGLES = 16;
   private static final int[] DISTANCE_ORDER = {8, 7, 9, 6, 10};
   /** Vertical search for ground around the origin height. */
   private static final int SEARCH_UP = 4;
   private static final int SEARCH_DOWN = 6;

   @FunctionalInterface
   public interface Fits {
      boolean test(int x, int z);
   }

   private Phoenix() {
   }

   // ---- pure ----

   /** Does the Phoenix save this hit: divine, lethal, and the cooldown is over. */
   public static boolean saves(boolean divine, float health, float amount, int cooldownLeft) {
      return divine && amount >= health && cooldownLeft <= 0;
   }

   /**
    * Revive column: a ring 6-10 blocks around the origin, directions closest to
    * {@code away} (horizontal x/z, null = any) first; origin when nothing fits.
    */
   public static int[] pickPoint(int originX, int originZ, @Nullable double[] away, Fits fits) {
      double awayAngle = away == null || (away[0] == 0.0 && away[1] == 0.0) ? 0.0 : Math.atan2(away[1], away[0]);
      List<double[]> directions = new ArrayList<>();
      for (int i = 0; i < ANGLES; i++) {
         double angle = awayAngle + Math.PI * 2.0 * i / ANGLES;
         double off = Math.abs(Math.atan2(Math.sin(angle - awayAngle), Math.cos(angle - awayAngle)));
         directions.add(new double[]{angle, off});
      }

      directions.sort(Comparator.comparingDouble(d -> d[1]));
      for (double[] direction : directions) {
         for (int distance : DISTANCE_ORDER) {
            int x = originX + (int) Math.round(Math.cos(direction[0]) * distance);
            int z = originZ + (int) Math.round(Math.sin(direction[0]) * distance);
            if (fits.test(x, z)) {
               return new int[]{x, z};
            }
         }
      }

      return new int[]{originX, originZ};
   }

   // ---- Minecraft ----

   public static boolean ready(ServerPlayer player) {
      return PersistentCooldowns.get(player, ReinhardRules.PHOENIX_KEY) <= 0;
   }

   /** Full HP, fire out, teleport, flames, cooldown. Called from clampFinalDamage (the hit then deals 0). */
   public static void revive(ServerPlayer player, @Nullable Entity attacker) {
      ServerLevel level = player.serverLevel();
      Vec3 origin = player.position();
      double[] away = attacker == null ? null : new double[]{origin.x - attacker.getX(), origin.z - attacker.getZ()};
      int originY = player.getBlockY();
      int[] column = pickPoint(player.getBlockX(), player.getBlockZ(), away, (x, z) -> standY(level, player, x, z, originY) != null);
      Integer y = standY(level, player, column[0], column[1], originY);
      PersistentCooldowns.set(player, ReinhardRules.PHOENIX_KEY, ReinhardRules.PHOENIX_COOLDOWN);
      player.setHealth(player.getMaxHealth());
      player.clearFire();
      player.fallDistance = 0.0F;
      player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, FIRE_IMMUNITY_TICKS, 0, false, false, true));
      HeroFx.flash(player, origin.add(0.0, 1.0, 0.0));
      if (y != null && (column[0] != player.getBlockX() || column[1] != player.getBlockZ())) {
         player.teleportTo(column[0] + 0.5, y, column[1] + 0.5);
      }

      Vec3 feet = player.position();
      HeroFx.flash(player, feet.add(0.0, 1.0, 0.0));
      HeroFx.launch(player, feet, 0.6F, Blocks.FIRE.defaultBlockState());
      ReinhardSounds.play(player, ReinhardSounds.PHOENIX.get(), 1.0F, 1.0F);
   }

   /** Standing height in this column near {@code originY}: solid floor, free body box, no fluid; or null. */
   @Nullable
   private static Integer standY(ServerLevel level, ServerPlayer player, int x, int z, int originY) {
      if (!level.getWorldBorder().isWithinBounds(new BlockPos(x, originY, z)) || !level.hasChunkAt(new BlockPos(x, originY, z))) {
         return null;
      }

      for (int dy = SEARCH_UP; dy >= -SEARCH_DOWN; dy--) {
         BlockPos feet = new BlockPos(x, originY + dy, z);
         BlockPos floor = feet.below();
         if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty() || !level.getFluidState(floor).isEmpty()) {
            continue;
         }

         if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) {
            continue;
         }

         AABB box = player.getDimensions(player.getPose()).makeBoundingBox(x + 0.5, feet.getY(), z + 0.5);
         if (level.noCollision(player, box)) {
            return feet.getY();
         }
      }

      return null;
   }
}
