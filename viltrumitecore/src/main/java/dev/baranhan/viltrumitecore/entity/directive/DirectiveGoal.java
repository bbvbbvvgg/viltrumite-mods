package dev.baranhan.viltrumitecore.entity.directive;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Top-priority goal injected into every goal-driven mob that carries a movement directive: stand still, keep away,
 * go to a point, follow the author, guard a subject, or hold still while the book is open. It owns MOVE/LOOK/JUMP, so
 * the mob's own wandering, panicking and chasing goals wait while it runs.
 */
public final class DirectiveGoal extends Goal {
   private final Mob mob;
   private int repath;

   public DirectiveGoal(Mob mob) {
      this.mob = mob;
      this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
   }

   @Override
   public boolean canUse() {
      return (DirectiveRules.effects(MobDirectives.active(this.mob)) & DirectiveEffect.MOVEMENT) != 0;
   }

   @Override
   public boolean canContinueToUse() {
      return this.canUse();
   }

   @Override
   public boolean requiresUpdateEveryTick() {
      return true;
   }

   @Override
   public void stop() {
      this.mob.getNavigation().stop();
   }

   @Override
   public void tick() {
      List<Directive> list = MobDirectives.active(this.mob);
      int effects = DirectiveRules.effects(list);
      if (!(this.mob.level() instanceof ServerLevel level)) {
         return;
      }
      if (DirectiveEffect.OPEN.in(effects) || DirectiveEffect.ROOT.in(effects) || DirectiveEffect.FLY.in(effects)) {
         this.mob.getNavigation().stop();
         Vec3 v = this.mob.getDeltaMovement();
         if (!DirectiveEffect.FLY.in(effects)) {
            this.mob.setDeltaMovement(0.0, Math.min(v.y, 0.0), 0.0);
         }
         Directive open = MobDirectives.first(this.mob, DirectiveEffect.OPEN);
         Entity reader = open == null ? null : level.getEntity(open.author());
         if (reader != null) {
            this.mob.getLookControl().setLookAt(reader, 20.0F, 20.0F);
         }
         return;
      }
      if (--this.repath > 0) {
         return;
      }
      this.repath = 10;
      for (Directive d : list) {
         Entity author = level.getEntity(d.author());
         if (d.has(DirectiveEffect.KEEP_AWAY) && author != null) {
            double dist = this.mob.distanceTo(author);
            if (dist < MobDirectives.KEEP_AWAY_DISTANCE) {
               Vec3 away = this.mob.position().subtract(author.position()).multiply(1, 0, 1);
               away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
               Vec3 to = this.mob.position().add(away.scale(MobDirectives.KEEP_AWAY_DISTANCE - dist + 4.0));
               this.mob.getNavigation().moveTo(to.x, to.y, to.z, 1.25);
            }
            return;
         }
         if (d.has(DirectiveEffect.GO_TO) && d.point() != null) {
            if (this.mob.blockPosition().distSqr(d.point()) > 2.25) {
               this.mob.getNavigation().moveTo(d.point().getX() + 0.5, d.point().getY(), d.point().getZ() + 0.5, 1.0);
            } else {
               this.mob.getNavigation().stop();
            }
            return;
         }
         if (d.has(DirectiveEffect.FOLLOW) && author != null) {
            this.follow(author, MobDirectives.FOLLOW_DISTANCE);
            return;
         }
         if (d.has(DirectiveEffect.GUARD) && d.subject() != null) {
            Entity ward = level.getEntity(d.subject());
            if (ward != null && this.mob.getTarget() == null) {
               this.follow(ward, MobDirectives.GUARD_DISTANCE);
            }
            return;
         }
      }
   }

   private void follow(Entity who, double distance) {
      if (this.mob.distanceTo(who) > distance) {
         this.mob.getNavigation().moveTo(who, 1.1);
      } else {
         this.mob.getNavigation().stop();
         this.mob.getLookControl().setLookAt(who, 20.0F, 20.0F);
      }
   }
}
