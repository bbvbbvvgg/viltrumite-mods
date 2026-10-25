package dev.baranhan.viltrumitecore.hero;

import java.util.Comparator;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;

/** Server-authoritative stop for piercing rays. No reflection, damage or block edits here. */
public final class HeroRayDefense {
   private HeroRayDefense() {}
   public record Stop(Vec3 end, @Nullable ServerPlayer defender) {
      public boolean permits(LivingEntity target, Vec3 from) {
         return target != defender && (defender == null || target.getBoundingBox().getCenter().subtract(from).dot(end.subtract(from)) <= end.distanceToSqr(from));
      }
   }
   public static Stop clip(ServerPlayer shooter, Vec3 from, Vec3 end, double radius, Predicate<LivingEntity> allowed) {
      var candidates=shooter.serverLevel().getEntitiesOfClass(ServerPlayer.class,new AABB(from,end).inflate(radius+1),
         p -> p != shooter && p.isAlive() && !p.isSpectator() && allowed.test(p)
            && p.getBoundingBox().inflate(radius).clip(from,end).isPresent());
      candidates.sort(Comparator.comparingDouble(p -> p.getBoundingBox().inflate(radius).clip(from,end).orElse(end).distanceToSqr(from)));
      for(var p:candidates) {
         Vec3 hit=p.getBoundingBox().inflate(radius).clip(from,end).orElse(end);
         var hero=HeroRegistry.get(p);
         if(hero.blocksContinuousRay(p,from,hit)) {
            Vec3 palm=hero.defenseContact(p);
            return new Stop(palm == null ? hit : palm,p);
         }
      }
      return new Stop(end,null);
   }
}
