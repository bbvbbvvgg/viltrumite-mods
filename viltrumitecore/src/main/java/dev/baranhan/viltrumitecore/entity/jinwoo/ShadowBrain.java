package dev.baranhan.viltrumitecore.entity.jinwoo;

import dev.baranhan.viltrumitecore.hero.SafeSpot;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooRules;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooSounds;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooState;
import dev.baranhan.viltrumitecore.hero.jinwoo.ShadowArmy;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server AI of one shadow (spec §6.2): owner checks and leash, target choice by mode,
 * formation movement and the kind's attacks. Repaths are staggered by slot so a full
 * army costs a few paths per tick.
 */
public final class ShadowBrain {
   private final ShadowSoldierEntity self;
   @Nullable
   private LivingEntity target;
   private int attackTick = -1;
   private boolean special;
   private int attackCooldown;
   private int specialCooldown;
   private int tauntCooldown = 40;
   private boolean secondHit;
   @Nullable
   private UUID provoker;
   private int provokerTicks;
   private int calm;
   private boolean sentinelHidden = true;

   ShadowBrain(ShadowSoldierEntity self) {
      this.self = self;
   }

   public boolean sentinelHidden() {
      return this.sentinelHidden;
   }

   @Nullable
   public LivingEntity target() {
      return this.target;
   }

   public void onHurtBy(LivingEntity attacker) {
      this.provoker = attacker.getUUID();
      this.provokerTicks = 200;
      if (this.target == null && this.self.mayHit(attacker)) {
         this.target = attacker;
      }
   }

   /** Players are hit only by order, or when they attacked Jinwoo or this shadow (spec §6.2). */
   public boolean playerAllowed(Player player) {
      ServerPlayer owner = this.self.owner();
      JinwooState state = owner == null ? null : JinwooState.of(owner);
      if (state == null) {
         return false;
      }
      return state.orderTargetId == player.getId() || (state.lastAttackerId == player.getId() && state.lastAttackerTicks > 0)
         || (player.getUUID().equals(this.provoker) && this.provokerTicks > 0);
   }

   void tick() {
      ShadowSoldierEntity s = this.self;
      ServerPlayer owner = s.owner();
      JinwooState state = owner == null ? null : JinwooState.of(owner);
      if (owner == null || state == null || owner.level() != s.level() || !owner.isAlive()) {
         s.dissolve();
         return;
      }
      if (s.tickCount % JinwooRules.OWNER_CHECK == 0 && !ShadowArmy.stillOwned(owner, state, s)) {
         s.dissolve();
         return;
      }
      if (this.attackCooldown > 0) this.attackCooldown--;
      if (this.specialCooldown > 0) this.specialCooldown--;
      if (this.tauntCooldown > 0) this.tauntCooldown--;
      if (this.provokerTicks > 0) this.provokerTicks--;
      s.setEmpowered(state.inDomain(s.position()));

      ShadowMode mode = s.mode();
      if (mode == ShadowMode.CHARGE) {
         return;
      }
      if (mode == ShadowMode.SENTINEL) {
         this.sentinel(owner, state);
         return;
      }
      if (s.distanceToSqr(owner) > JinwooRules.LEASH * JinwooRules.LEASH) {
         this.leash(owner);
         return;
      }
      if (this.attackTick >= 0) {
         this.attackStep();
         return;
      }
      if (s.tickCount % 5 == s.slot % 5 || this.target == null || !this.target.isAlive()) {
         this.target = this.choose(owner, state, mode);
      }
      if (this.target != null) {
         this.engage(this.target, state);
      } else {
         this.formation(owner, state, mode);
      }
   }

   // --- targets ---

   @Nullable
   private LivingEntity choose(ServerPlayer owner, JinwooState state, ShadowMode mode) {
      ServerLevel level = (ServerLevel)this.self.level();
      if (mode == ShadowMode.ATTACK) {
         Entity ordered = state.orderTargetId >= 0 ? level.getEntity(state.orderTargetId) : null;
         if (ordered instanceof LivingEntity living && living.isAlive() && this.self.mayHit(living)) {
            return living;
         }
         if (state.orderPoint != null) {
            return this.nearestEnemy(state.orderPoint, JinwooRules.COMMAND_AREA);
         }
      }
      if (mode == ShadowMode.GUARD) {
         LivingEntity attacker = this.attackerOf(level, state);
         if (attacker != null) {
            return attacker;
         }
         return this.nearestEnemy(owner.position(), JinwooRules.GUARD_RADIUS * 2.0);
      }
      LivingEntity attacker = this.attackerOf(level, state);
      if (attacker != null) {
         return attacker;
      }
      if (this.provoker != null && this.provokerTicks > 0 && level.getEntity(this.provoker) instanceof LivingEntity p && p.isAlive() && this.self.mayHit(p)
         && p.distanceToSqr(owner) < 24 * 24) {
         return p;
      }
      if (state.markTarget != null && state.markTicks > 0 && level.getEntity(state.markTarget) instanceof LivingEntity marked && marked.isAlive()
         && this.self.mayHit(marked) && marked.distanceToSqr(owner) < JinwooRules.LEASH * JinwooRules.LEASH) {
         return marked;
      }
      return this.nearestEnemy(owner.position(), JinwooRules.FOLLOW_TARGET_RANGE);
   }

   @Nullable
   private LivingEntity attackerOf(ServerLevel level, JinwooState state) {
      if (state.lastAttackerId >= 0 && state.lastAttackerTicks > 0 && level.getEntity(state.lastAttackerId) instanceof LivingEntity a && a.isAlive()
         && this.self.mayHit(a)) {
         return a;
      }
      return null;
   }

   @Nullable
   private LivingEntity nearestEnemy(Vec3 at, double radius) {
      List<LivingEntity> list = this.self.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
         e -> e instanceof Enemy && e.isAlive() && this.self.mayHit(e));
      LivingEntity best = null;
      double bestD = radius * radius;
      for (LivingEntity e : list) {
         double d = e.distanceToSqr(at);
         if (d <= bestD) {
            best = e;
            bestD = d;
         }
      }
      return best;
   }

   // --- movement ---

   private void formation(ServerPlayer owner, JinwooState state, ShadowMode mode) {
      ShadowSoldierEntity s = this.self;
      Vec3 goal;
      if (mode == ShadowMode.ATTACK && state.orderPoint != null) {
         goal = state.orderPoint;
      } else if (mode == ShadowMode.GUARD) {
         double[] off = JinwooRules.guardSlot(s.slot, Math.max(1, ShadowArmy.guardCount(owner)));
         goal = owner.position().add(off[0], 0, off[1]);
      } else {
         double[] off = JinwooRules.followSlot(s.slot);
         goal = ShadowArmy.local(owner, off[0], off[1]);
      }
      double d = s.position().distanceToSqr(goal);
      if (d > 2.0) {
         if (s.tickCount % 5 == s.slot % 5 || s.getNavigation().isDone()) {
            s.getNavigation().moveTo(goal.x, goal.y, goal.z, d > 64 ? 1.6 : 1.1);
         }
      } else {
         s.getNavigation().stop();
         s.setYRot(owner.getYRot());
         s.setYBodyRot(owner.getYRot());
         s.setYHeadRot(owner.getYHeadRot());
      }
   }

   private void leash(ServerPlayer owner) {
      ShadowSoldierEntity s = this.self;
      double[] off = JinwooRules.followSlot(s.slot);
      Vec3 wanted = ShadowArmy.local(owner, off[0], off[1]);
      Vec3 spot = SafeSpot.find(wanted, new SafeSpot.LevelProbe(s.level(), s), 4.0, 1.0).orElse(owner.position());
      s.getNavigation().stop();
      s.teleportTo(spot.x, spot.y, spot.z);
      s.level().broadcastEntityEvent(s, ShadowSoldierEntity.EVENT_RISE);
      this.target = null;
      this.attackTick = -1;
   }

   private void sentinel(ServerPlayer owner, JinwooState state) {
      ShadowSoldierEntity s = this.self;
      ServerLevel level = (ServerLevel)s.level();
      Entity ward = s.ward == null || s.wardPoint != null ? null : level.getEntity(s.ward);
      Vec3 wardAt;
      if (s.wardPoint != null) {
         wardAt = s.wardPoint;
      } else if (ward instanceof LivingEntity w && w.isAlive()) {
         wardAt = w.position();
      } else {
         ShadowArmy.dropSentinel(owner, state, s);
         return;
      }
      if (this.attackTick >= 0) {
         this.attackStep();
         return;
      }
      if (this.target != null && (!this.target.isAlive() || this.target.distanceToSqr(wardAt) > 20 * 20)) {
         this.target = null;
      }
      if (this.target == null && s.tickCount % 5 == s.slot % 5) {
         LivingEntity threat = this.nearestEnemy(wardAt, JinwooRules.SENTINEL_ALERT);
         if (threat != null) {
            this.target = threat;
            this.sentinelHidden = false;
            s.setHidden(false);
            s.level().broadcastEntityEvent(s, ShadowSoldierEntity.EVENT_RISE);
            JinwooSounds.play(s, JinwooSounds.SHADOW_RISE, 0.7F, 1.2F);
         }
      }
      if (this.target != null) {
         this.calm = 0;
         this.engage(this.target, state);
         return;
      }
      if (!this.sentinelHidden) {
         Vec3 at = wardAt;
         if (s.distanceToSqr(at) > 2.5 && ++this.calm < 80) {
            if (s.getNavigation().isDone() || s.tickCount % 10 == 0) s.getNavigation().moveTo(at.x, at.y, at.z, 1.3);
            return;
         }
         this.sentinelHidden = true;
         s.setHidden(true);
         JinwooSounds.play(s, JinwooSounds.SHADOW_DISSOLVE, 0.5F, 1.3F);
      }
      // Hidden in the ward's shadow: ride along under its feet.
      s.getNavigation().stop();
      s.setDeltaMovement(Vec3.ZERO);
      if (ward != null) {
         s.teleportTo(ward.getX(), ward.getY(), ward.getZ());
      }
   }

   // --- attacks ---

   private double reachTo(LivingEntity t) {
      return Math.max(0.0, this.self.distanceTo(t) - (t.getBbWidth() + this.self.getBbWidth()) * 0.5);
   }

   private void engage(LivingEntity t, JinwooState state) {
      ShadowSoldierEntity s = this.self;
      s.getLookControl().setLookAt(t, 40.0F, 40.0F);
      double gap = this.reachTo(t);
      ShadowKind kind = s.kind();
      if (kind == ShadowKind.BEAST && this.tauntCooldown <= 0) {
         this.taunt();
      }
      if (this.specialCooldown <= 0 && this.attackCooldown <= 0) {
         if (kind == ShadowKind.KNIGHT && gap > 2.0 && gap <= ShadowKind.KNIGHT_LUNGE_RANGE) {
            this.startAttack(true);
            this.specialCooldown = ShadowKind.KNIGHT_LUNGE_COOLDOWN;
            return;
         }
         if (kind == ShadowKind.INSECT && gap > 3.0 && gap <= ShadowKind.INSECT_LEAP_RANGE && s.onGround()) {
            this.startAttack(true);
            this.specialCooldown = ShadowKind.INSECT_LEAP_COOLDOWN;
            Vec3 d = t.position().subtract(s.position());
            Vec3 h = new Vec3(d.x, 0, d.z).normalize();
            s.setDeltaMovement(h.x * Math.min(1.25, gap * 0.16), 0.55, h.z * Math.min(1.25, gap * 0.16));
            return;
         }
      }
      if (gap <= ShadowKind.REACH) {
         s.getNavigation().stop();
         if (this.attackCooldown <= 0) {
            this.startAttack(false);
         }
      } else if (s.tickCount % 4 == s.slot % 4 || s.getNavigation().isDone()) {
         s.getNavigation().moveTo(t, gap > 6 ? 1.5 : 1.25);
      }
   }

   private void startAttack(boolean special) {
      this.attackTick = 0;
      this.special = special;
      this.secondHit = false;
      this.self.level().broadcastEntityEvent(this.self, special ? ShadowSoldierEntity.EVENT_SPECIAL : ShadowSoldierEntity.EVENT_ATTACK);
   }

   private void attackStep() {
      ShadowSoldierEntity s = this.self;
      ShadowKind kind = s.kind();
      int length = this.special ? kind.attackLength() + 4 : kind.attackLength();
      int contact = kind.attackContact();
      LivingEntity t = this.target;
      int tick = this.attackTick++;
      if (t != null && t.isAlive()) {
         s.getLookControl().setLookAt(t, 60.0F, 60.0F);
         s.setYRot(s.getYHeadRot());
         s.setYBodyRot(s.getYHeadRot());
      }
      if (this.special && kind == ShadowKind.KNIGHT && tick >= 4 && tick <= contact && t != null) {
         Vec3 d = t.position().subtract(s.position());
         Vec3 h = new Vec3(d.x, 0, d.z);
         if (h.lengthSqr() > 1.0) {
            h = h.normalize();
            s.setDeltaMovement(h.x * 0.85, s.getDeltaMovement().y, h.z * 0.85);
         }
      }
      boolean contactNow = tick == contact || (kind == ShadowKind.INSECT && !this.special && tick == contact + 4);
      if (contactNow && t != null && t.isAlive()) {
         float bonus = s.empowered() ? JinwooRules.DOMAIN_DAMAGE_BONUS : 1.0F;
         if (kind == ShadowKind.BEAST && !this.special) {
            this.slam(kind.damage * bonus);
         } else if (this.reachTo(t) <= ShadowKind.REACH + (this.special ? 1.0 : 0.6)) {
            float dmg = this.special ? (kind == ShadowKind.KNIGHT ? ShadowKind.KNIGHT_LUNGE_DAMAGE : ShadowKind.INSECT_LEAP_DAMAGE) : kind.damage;
            this.strike(t, dmg * bonus);
         }
      }
      if (this.attackTick >= length) {
         this.attackTick = -1;
         this.attackCooldown = kind.attackInterval;
      }
   }

   private void strike(LivingEntity t, float damage) {
      ShadowSoldierEntity s = this.self;
      if (t.hurt(s.damageSources().mobAttack(s), damage)) {
         JinwooSounds.play(s, s.kind() == ShadowKind.INSECT ? JinwooSounds.SHADOW_CLAW : JinwooSounds.SHADOW_SLASH, 0.8F,
            0.9F + s.getRandom().nextFloat() * 0.2F);
         if (t instanceof Mob mob && mob.getTarget() == null) {
            mob.setTarget(s);
         }
      }
   }

   private void slam(float damage) {
      ShadowSoldierEntity s = this.self;
      double r = ShadowKind.BEAST_SLAM_RADIUS;
      List<LivingEntity> list = s.level().getEntitiesOfClass(LivingEntity.class, s.getBoundingBox().inflate(r, 1.0, r), e -> s.mayHit(e));
      for (LivingEntity e : list) {
         if (e.distanceToSqr(s) <= (r + 1) * (r + 1) && e.hurt(s.damageSources().mobAttack(s), damage) && s.impulseAllowed(e)) {
            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.4, 0));
            e.hurtMarked = true;
         }
      }
      JinwooSounds.play(s, JinwooSounds.SHADOW_SLAM, 1.0F, 0.8F);
      dev.baranhan.viltrumitecore.hero.jinwoo.JinwooFx.around(s, dev.baranhan.viltrumitecore.hero.jinwoo.JinwooFx.WAVE, 0, s.position(),
         new Vec3(r, 0, 0));
   }

   /** Tank's provocation (spec §6.1): enemies within 10 blocks turn on him. */
   private void taunt() {
      ShadowSoldierEntity s = this.self;
      this.tauntCooldown = ShadowKind.BEAST_TAUNT_COOLDOWN;
      double r = ShadowKind.BEAST_TAUNT_RANGE;
      List<Mob> mobs = s.level().getEntitiesOfClass(Mob.class, s.getBoundingBox().inflate(r), m -> m instanceof Enemy && s.mayHit(m));
      if (mobs.isEmpty()) {
         return;
      }
      for (Mob m : mobs) {
         m.setTarget(s);
      }
      JinwooSounds.play(s, JinwooSounds.SHADOW_ROAR, 1.2F, 0.9F);
      s.level().broadcastEntityEvent(s, ShadowSoldierEntity.EVENT_EYES);
   }

   /** Forget the current target (orders change). */
   public void reset() {
      this.target = null;
      this.attackTick = -1;
      this.sentinelHidden = true;
      this.calm = 0;
   }
}
