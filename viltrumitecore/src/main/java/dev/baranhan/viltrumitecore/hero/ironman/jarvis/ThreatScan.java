package dev.baranhan.viltrumitecore.hero.ironman.jarvis;

import dev.baranhan.viltrumitecore.hero.HeroOwnerSnapshot;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.OwnerSection;
import dev.baranhan.viltrumitecore.hero.ironman.IronManRules;
import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * JARVIS threat list (spec §11.1), server. Every
 * {@link IronManRules#THREAT_SCAN_INTERVAL} ticks with the helmet closed:
 * mobs whose target is the player, ranged mobs drawing at the player, and
 * players within {@link IronManRules#THREAT_RANGE} looking at us with a weapon.
 * Goes to the owner as the THREATS owner section (nearest first).
 */
public final class ThreatScan {
   public static final int MAX_THREATS = 16;

   private ThreatScan() {
   }

   /** A mob that has us as its target, or a ranged mob drawing at us. */
   public static boolean mobThreat(boolean targetsPlayer, boolean drawingAtPlayer) {
      return targetsPlayer || drawingAtPlayer;
   }

   /** Drawing a bow / crossbow while aimed at the player. */
   public static boolean drawing(boolean ranged, boolean usingItem, boolean aimedAtPlayer) {
      return ranged && usingItem && aimedAtPlayer;
   }

   /** Another player looking at us (cone, line of sight) with a weapon in hand or drawing. */
   public static boolean playerThreat(double lookDot, boolean lineOfSight, boolean armed, boolean drawing) {
      return lookDot >= IronManRules.THREAT_LOOK_DOT && lineOfSight && (armed || drawing);
   }

   /**
    * Projectile arrow: the straight path from pos along velocity passes within
    * miss of the centre, ahead of the projectile, within range.
    */
   public static boolean projectilePassesNear(Vec3 pos, Vec3 velocity, Vec3 centre, double range, double miss) {
      if (pos.distanceToSqr(centre) > range * range) {
         return false;
      }

      double speedSqr = velocity.lengthSqr();
      if (speedSqr < 1.0E-4) {
         return false;
      }

      Vec3 to = centre.subtract(pos);
      double t = to.dot(velocity) / speedSqr;
      if (t < 0.0) {
         return false;
      }

      return pos.add(velocity.scale(t)).distanceToSqr(centre) <= miss * miss;
   }

   static boolean armed(ItemStack stack) {
      return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem
         || stack.getItem() instanceof ProjectileWeaponItem || stack.getItem() instanceof TridentItem;
   }

   /** Server tick: refresh the owner's THREATS section. */
   public static void tick(ServerPlayer player, IronManState state, boolean active) {
      if (!active) {
         if (!state.threats.isEmpty()) {
            state.threats.clear();
            HeroRegistry.pushOwnerSection(player, OwnerSection.THREATS, HeroOwnerSnapshot.Section.EMPTY);
         }

         return;
      }

      if (player.tickCount % IronManRules.THREAT_SCAN_INTERVAL != 0) {
         return;
      }

      double range = IronManRules.THREAT_RANGE;
      AABB box = player.getBoundingBox().inflate(range);
      Vec3 eye = player.getEyePosition();
      List<LivingEntity> found = new ArrayList<>();
      for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
         e -> e != player && e.isAlive() && !e.isSpectator() && e.distanceToSqr(player) <= range * range)) {
         if (entity instanceof Mob mob) {
            boolean targets = mob.getTarget() == player;
            boolean drawing = drawing(mob instanceof RangedAttackMob, mob.isUsingItem(), targets);
            if (mobThreat(targets, drawing)) {
               found.add(mob);
            }
         } else if (entity instanceof Player other && !other.isCreative() && !player.isAlliedTo(other)
            && !HeroRegistry.get(other).hiddenFromScan(other)) {
            Vec3 toUs = eye.subtract(other.getEyePosition());
            double dot = toUs.lengthSqr() < 1.0E-4 ? 1.0 : other.getViewVector(1.0F).dot(toUs.normalize());
            boolean armedNow = armed(other.getMainHandItem()) || armed(other.getOffhandItem());
            if (playerThreat(dot, other.hasLineOfSight(player), armedNow, other.isUsingItem() && armed(other.getUseItem()))) {
               found.add(other);
            }
         }
      }

      found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
      List<Integer> ids = new ArrayList<>();
      for (LivingEntity entity : found) {
         if (ids.size() >= MAX_THREATS) {
            break;
         }

         ids.add(entity.getId());
      }

      if (!ids.equals(state.threats)) {
         state.threats.clear();
         state.threats.addAll(ids);
         HeroRegistry.pushOwnerSection(player, OwnerSection.THREATS, HeroOwnerSnapshot.Section.of(ids.stream().mapToInt(Integer::intValue).toArray()));
      }
   }
}
