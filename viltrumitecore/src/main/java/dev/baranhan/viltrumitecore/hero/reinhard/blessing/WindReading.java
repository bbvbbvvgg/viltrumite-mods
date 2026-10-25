package dev.baranhan.viltrumitecore.hero.reinhard.blessing;

import dev.baranhan.viltrumitecore.hero.HeroOwnerSnapshot;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.OwnerSection;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Blessing of Wind Reading (spec §4.5): he feels what he cannot see — every
 * invisible creature within {@link ReinhardRules#WIND_RADIUS}, and anything
 * hostile to him behind his back (a mob targeting him, a player who hit him in
 * the last 5 s, the owner of a projectile flying at him), is outlined for him
 * only (owner section THREATS, client WindReadingClient). Mode-independent.
 */
public final class WindReading {
   private WindReading() {
   }

   // ---- pure ----

   public static boolean isMarked(boolean invisible, boolean behind, boolean hostileToMe) {
      return invisible || (behind && hostileToMe);
   }

   /** Behind: horizontal dot of the look and the direction to the entity below {@link ReinhardRules#WIND_BEHIND_COS}. */
   public static boolean behind(Vec2 lookXZ, Vec2 toXZ) {
      double lookLength = Math.sqrt(lookXZ.x * lookXZ.x + lookXZ.y * lookXZ.y);
      double toLength = Math.sqrt(toXZ.x * toXZ.x + toXZ.y * toXZ.y);
      if (lookLength < 1.0E-6 || toLength < 1.0E-6) {
         return false;
      }

      return (lookXZ.x * toXZ.x + lookXZ.y * toXZ.y) / (lookLength * toLength) < ReinhardRules.WIND_BEHIND_COS;
   }

   public static boolean inRadius(double distanceSqr) {
      return distanceSqr <= (double) ReinhardRules.WIND_RADIUS * ReinhardRules.WIND_RADIUS;
   }

   /** A player hit counts as hostile for {@link ReinhardRules#WIND_RECENT_ATTACK_TICKS}. */
   public static boolean recentAttack(@Nullable Long hitAt, long now) {
      return hitAt != null && now - hitAt <= ReinhardRules.WIND_RECENT_ATTACK_TICKS;
   }

   // ---- Minecraft ----

   /** absorbIncoming: remember a player who hit him (parried and resisted hits count too). */
   public static void onAttacked(ServerPlayer self, ReinhardState state, @Nullable Entity attacker) {
      if (attacker instanceof Player player && player != self) {
         state.recentAttackers.put(player.getUUID(), self.level().getGameTime());
      }
   }

   /** Every {@link ReinhardRules#WIND_SCAN_INTERVAL} ticks: the marked ids to the owner section. */
   public static void scan(ServerPlayer self, ReinhardState state) {
      long now = self.level().getGameTime();
      state.recentAttackers.entrySet().removeIf(entry -> !recentAttack(entry.getValue(), now));
      if (!self.isAlive() || self.isSpectator()) {
         clear(self);
         return;
      }

      double radius = ReinhardRules.WIND_RADIUS;
      AABB box = self.getBoundingBox().inflate(radius);
      Set<Integer> shooters = new HashSet<>();
      Vec3 chest = self.getBoundingBox().getCenter();
      for (Projectile projectile : self.level().getEntitiesOfClass(Projectile.class, box, p -> p.isAlive() && p.getOwner() != null && p.getOwner() != self)) {
         Vec3 v = projectile.getDeltaMovement();
         Vec3 to = chest.subtract(projectile.position());
         if (v.lengthSqr() > 1.0E-4 && to.lengthSqr() > 1.0E-4 && v.normalize().dot(to.normalize()) > 0.9) {
            shooters.add(projectile.getOwner().getId());
         }
      }

      Vec2 look = Parry.lookOf(self.getYHeadRot());
      Map<UUID, Long> attackers = state.recentAttackers;
      int[] ids = self.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != self && e.isAlive()).stream()
         .filter(e -> inRadius(e.distanceToSqr(self)))
         .filter(e -> {
            boolean hostile = (e instanceof Mob mob && mob.getTarget() == self)
               || (e instanceof Player && recentAttack(attackers.get(e.getUUID()), now))
               || shooters.contains(e.getId());
            Vec2 to = new Vec2((float) (e.getX() - self.getX()), (float) (e.getZ() - self.getZ()));
            return isMarked(e.isInvisible(), behind(look, to), hostile);
         })
         .mapToInt(Entity::getId)
         .sorted()
         .toArray();
      HeroRegistry.pushOwnerSection(self, OwnerSection.THREATS, HeroOwnerSnapshot.Section.of(ids));
   }

   public static void clear(ServerPlayer self) {
      HeroRegistry.pushOwnerSection(self, OwnerSection.THREATS, HeroOwnerSnapshot.Section.EMPTY);
   }
}
