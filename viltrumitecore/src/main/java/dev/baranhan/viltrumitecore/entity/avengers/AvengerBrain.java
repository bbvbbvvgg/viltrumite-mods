package dev.baranhan.viltrumitecore.entity.avengers;

import dev.baranhan.viltrumitecore.hero.SafeSpot;
import dev.baranhan.viltrumitecore.hero.captain.CaptainFx;
import dev.baranhan.viltrumitecore.hero.captain.CaptainRules;
import dev.baranhan.viltrumitecore.hero.captain.CaptainState;
import dev.baranhan.viltrumitecore.hero.captain.CaptainTargets;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Fight AI of a summoned Avenger (spec §5.7, after the ceremony). The base owns target choice (what attacks
 * Steve, what Steve hits, who hit this Avenger, the nearest hostile around Steve), the leash to Steve and
 * the timed body actions; each kind's subclass decides how it fights. Server only.
 */
public abstract class AvengerBrain {
   protected final AvengerEntity self;
   @Nullable protected LivingEntity target;
   protected AvengerAction action = AvengerAction.NONE;
   protected int actionTick = -1;
   protected int actionLength;
   /** Per-kind cooldowns (indices defined by the subclass). */
   protected final int[] cd = new int[8];
   @Nullable private LivingEntity provoker;
   private int provokerTicks;
   protected int comboStep;
   private int stuck;
   private double bestGap = Double.MAX_VALUE;

   protected AvengerBrain(AvengerEntity self) {
      this.self = self;
   }

   static AvengerBrain create(AvengerEntity e, AvengerKind kind) {
      return switch (kind) {
         case IRON_MAN -> new IronManBrain(e);
         case THOR -> new ThorBrain(e);
         case HULK -> new HulkBrain(e);
         case BLACK_WIDOW -> new WidowBrain(e);
         case HAWKEYE -> new HawkeyeBrain(e);
      };
   }

   @Nullable
   public LivingEntity target() {
      return this.target;
   }

   void onHurtBy(LivingEntity attacker) {
      if (this.self.mayHit(attacker)) {
         this.provoker = attacker;
         this.provokerTicks = 120;
      }
      this.onHurt(attacker);
   }

   protected void onHurt(LivingEntity attacker) {
   }

   /** Fight start (the circle broke up). */
   public void begin() {
   }

   void tick() {
      ServerPlayer owner = this.self.owner();
      if (owner == null || !owner.isAlive() || owner.level() != this.self.level()) {
         return; // the scene removes the team
      }
      for (int i = 0; i < this.cd.length; i++) {
         if (this.cd[i] > 0) this.cd[i]--;
      }
      if (this.provokerTicks > 0 && --this.provokerTicks == 0) {
         this.provoker = null;
      }
      double toOwner = this.self.distanceToSqr(owner);
      if (toOwner > CaptainRules.LEASH_TELEPORT * CaptainRules.LEASH_TELEPORT) {
         this.leash(owner);
         return;
      }
      if (this.target != null && (!this.target.isAlive() || !this.self.mayHit(this.target)
         || this.target.distanceToSqr(owner) > CaptainRules.LEASH * CaptainRules.LEASH * 1.6)) {
         this.target = null;
      }
      if (this.self.tickCount % 10 == this.self.getId() % 10 || this.target == null && this.self.tickCount % 4 == 0) {
         LivingEntity next = this.choose(owner);
         if (next != this.target) {
            this.target = next;
            this.stuck = 0;
            this.bestGap = Double.MAX_VALUE;
         }
      }
      if (this.actionTick >= 0) {
         this.actionTick++;
         if (this.target != null) {
            this.self.getLookControl().setLookAt(this.target, 60.0F, 60.0F);
         }
         this.onAction(this.action, this.actionTick);
         if (this.actionTick >= this.actionLength) {
            AvengerAction done = this.action;
            this.actionTick = -1;
            this.action = AvengerAction.NONE;
            this.onActionEnd(done);
         }
         return;
      }
      if (this.target != null) {
         this.engage(this.target, owner);
      } else {
         this.follow(owner);
      }
   }

   // --- actions ---

   protected void start(AvengerAction a, int length) {
      this.action = a;
      this.actionTick = 0;
      this.actionLength = length;
      this.self.play(a);
   }

   protected boolean busy() {
      return this.actionTick >= 0;
   }

   protected abstract void engage(LivingEntity t, ServerPlayer owner);

   protected abstract void onAction(AvengerAction a, int tick);

   protected void onActionEnd(AvengerAction a) {
   }

   // --- targets ---

   @Nullable
   private LivingEntity choose(ServerPlayer owner) {
      ServerLevel level = (ServerLevel)this.self.level();
      CaptainState state = CaptainState.of(owner);
      if (state != null) {
         if (state.lastAttackerId >= 0 && level.getEntity(state.lastAttackerId) instanceof LivingEntity a && this.usable(owner, a)) {
            return a;
         }
         if (state.markTarget != null && state.markTicks > 0 && level.getEntity(state.markTarget) instanceof LivingEntity m && this.usable(owner, m)) {
            return m;
         }
      }
      if (this.provoker != null && this.usable(owner, this.provoker)) {
         return this.provoker;
      }
      if (this.target != null && this.usable(owner, this.target)) {
         return this.target;
      }
      return this.nearestHostile(owner.position(), CaptainRules.TARGET_RANGE);
   }

   private boolean usable(ServerPlayer owner, LivingEntity e) {
      return e.isAlive() && this.self.mayHit(e) && e.distanceToSqr(owner) < CaptainRules.LEASH * CaptainRules.LEASH * 1.4;
   }

   @Nullable
   protected LivingEntity nearestHostile(Vec3 at, double radius) {
      List<LivingEntity> list = this.self.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
         e -> this.hostile(e) && this.self.mayHit(e));
      LivingEntity best = null;
      double bestD = radius * radius;
      for (LivingEntity e : list) {
         double d = e.distanceToSqr(this.self);
         if (d <= bestD) {
            best = e;
            bestD = d;
         }
      }
      return best;
   }

   /** A monster, or any mob that hunts Steve or one of the team. */
   protected boolean hostile(LivingEntity e) {
      if (e instanceof Enemy) {
         return true;
      }
      LivingEntity hunted = e instanceof Mob mob ? mob.getTarget() : null;
      return hunted != null && hunted.isAlive() && this.self.isAlliedTo(hunted);
   }

   protected List<LivingEntity> enemiesAround(Vec3 at, double radius) {
      return CaptainTargets.around(this.self, at, radius, this.self::mayHit);
   }

   // --- movement helpers ---

   protected double gap(LivingEntity t) {
      return Math.max(0.0, this.self.distanceTo(t) - (t.getBbWidth() + this.self.getBbWidth()) * 0.5);
   }

   protected boolean sees(LivingEntity t) {
      return CaptainTargets.visible(this.self.level(), this.self.getEyePosition(), t.getBoundingBox().getCenter());
   }

   /** Walks towards t; a target that cannot be reached for 6 s is dropped for a while. */
   protected void approach(LivingEntity t, double speed) {
      double gap = this.gap(t);
      if (this.self.tickCount % 4 == 0 || this.self.getNavigation().isDone()) {
         this.self.getNavigation().moveTo(t, speed);
      }
      if (gap < this.bestGap - 0.5) {
         this.bestGap = gap;
         this.stuck = 0;
      } else if (++this.stuck > 120) {
         this.stuck = 0;
         this.bestGap = Double.MAX_VALUE;
         this.unreachable(t);
      }
   }

   /** Called when t cannot be reached on foot; default: forget it. */
   protected void unreachable(LivingEntity t) {
      this.target = null;
   }

   protected void follow(ServerPlayer owner) {
      Vec3 goal = followPoint(owner);
      double d = this.self.position().distanceToSqr(goal);
      if (d > 9.0) {
         if (this.self.tickCount % 8 == 0 || this.self.getNavigation().isDone()) {
            this.self.getNavigation().moveTo(goal.x, goal.y, goal.z, d > 144 ? 1.5 : 1.1);
         }
      } else {
         this.self.getNavigation().stop();
         this.self.getLookControl().setLookAt(owner.getX() + owner.getLookAngle().x * 8, owner.getEyeY(), owner.getZ() + owner.getLookAngle().z * 8);
      }
   }

   /** Fight-follow place around Steve: the circle slot, 4 blocks out, rotated with Steve's facing. */
   protected Vec3 followPoint(ServerPlayer owner) {
      double ang = Math.toRadians(owner.getYRot() + 180.0 + CaptainRules.slotAngle(this.self.kind().ordinal()));
      return owner.position().add(-Math.sin(ang) * 4.0, 0, Math.cos(ang) * 4.0);
   }

   protected void leash(ServerPlayer owner) {
      Vec3 wanted = this.followPoint(owner);
      Vec3 spot = SafeSpot.find(wanted, new SafeSpot.LevelProbe(this.self.level(), this.self), 4.0, 1.0).orElse(owner.position());
      this.self.getNavigation().stop();
      this.self.teleportTo(spot.x, spot.y, spot.z);
      CaptainFx.around(this.self, CaptainFx.THRUST, 1, spot, Vec3.ZERO);
      this.target = null;
      this.actionTick = -1;
      this.action = AvengerAction.NONE;
   }

   protected void face(LivingEntity t) {
      this.self.getLookControl().setLookAt(t, 60.0F, 60.0F);
      Vec3 d = t.position().subtract(this.self.position());
      float yaw = (float)(Math.atan2(-d.x, d.z) * 180.0 / Math.PI);
      this.self.setYRot(yaw);
      this.self.setYBodyRot(yaw);
   }

   /** Melee hits in a frontal arc; returns how many landed. */
   protected int strikeArc(double reach, double degrees, float damage, double knockback, double lift) {
      Vec3 look = this.flatLook();
      double cos = Math.cos(Math.toRadians(degrees / 2));
      int n = 0;
      for (LivingEntity e : this.enemiesAround(this.self.position().add(0, this.self.getBbHeight() * 0.5, 0), reach + this.self.getBbWidth())) {
         Vec3 d = e.position().subtract(this.self.position());
         Vec3 h = new Vec3(d.x, 0, d.z);
         if (h.lengthSqr() > 0.04 && h.normalize().dot(look) < cos) {
            continue;
         }
         if (CaptainTargets.strike(this.self, e, damage)) {
            n++;
            CaptainTargets.push(e, look.scale(knockback).add(0, lift, 0));
            CaptainFx.around(this.self, CaptainFx.CONTACT, 6, e.getBoundingBox().getCenter(), this.self.getEyePosition());
         }
      }
      return n;
   }

   protected Vec3 flatLook() {
      float yaw = this.self.getYRot();
      double r = Math.toRadians(yaw);
      return new Vec3(-Math.sin(r), 0, Math.cos(r));
   }

   /** First block hit along a ray, or the end. */
   protected Vec3 rayEnd(Vec3 from, Vec3 to) {
      var hit = this.self.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.self));
      return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
   }

   /** Living entities the segment passes within {@code width}. */
   protected List<LivingEntity> alongRay(Vec3 from, Vec3 to, double width) {
      Vec3 d = to.subtract(from);
      double len = d.length();
      Vec3 n = len < 1.0E-4 ? Vec3.ZERO : d.scale(1.0 / len);
      AABB box = new AABB(from, to).inflate(width + 1.0);
      return this.self.level().getEntitiesOfClass(LivingEntity.class, box, e -> {
         if (!this.self.mayHit(e)) {
            return false;
         }
         Vec3 c = e.getBoundingBox().getCenter().subtract(from);
         double along = Math.max(0, Math.min(len, c.dot(n)));
         return c.subtract(n.scale(along)).length() <= width + e.getBbWidth() * 0.5;
      });
   }

   /** Ballistic velocity (mob gravity 0.08, drag 0.98) to reach {@code to} in {@code ticks}. */
   protected static Vec3 ballistic(Vec3 from, Vec3 to, int ticks) {
      double t = Math.max(4, ticks);
      Vec3 d = to.subtract(from);
      double vy = (d.y + 0.5 * 0.08 * t * t) / t;
      return new Vec3(d.x / t / 0.93, vy, d.z / t / 0.93);
   }

   protected Entity selfEntity() {
      return this.self;
   }

   protected Vec3 hand(boolean right) {
      Vec3 look = this.flatLook();
      Vec3 side = new Vec3(-look.z, 0, look.x).scale(right ? 0.4 : -0.4);
      return this.self.position().add(0, this.self.getBbHeight() * 0.68, 0).add(side).add(look.scale(0.5));
   }
}
