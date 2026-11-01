package dev.baranhan.viltrumitecore.entity.thanos;

import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriSoldier;
import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathCause;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosTargets;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.ThanosHolds;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.ThanosWar;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.ThanosWarRules;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.ThanosWarSounds;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.order.CullMove;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.order.OrderBrain;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Cull Obsidian (THANOS 2.0): the Black Order's brute. A huge walker with a hammer and a hooked
 * blade on a chain: wide swings, a ground slam that rings out (heroes on the ground are thrown),
 * a charge straight through walls (a corridor in the buildings, through the war's budget), the
 * chain that hooks a flyer or a sniper and reels him in, a grab and throw. A Hulkbuster gets his
 * full attention: a roar that hardens him, then the clash (he locks with the armour and drives it
 * back). Heavy hits stagger him only when they really land.
 */
public class CullObsidianEntity extends BlackOrderEntity {
   private final OrderBrain<CullMove> brain;
   @Nullable
   private CullMove move;
   @Nullable
   private LivingEntity held;
   private Vec3 chargeDir = Vec3.ZERO;
   private int chargeBlocks;
   private final Set<Integer> chargeHit = new HashSet<>();
   private int hardened;
   private boolean sawHulk;
   private int stepTimer;

   public CullObsidianEntity(EntityType<? extends CullObsidianEntity> type, Level level) {
      super(type, level);
      this.brain = new OrderBrain<>(CullMove.class, level.random.nextLong());
      this.setMaxUpStep(1.6F);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return PathfinderMob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, ThanosWarRules.CULL_HEALTH)
         .add(Attributes.ARMOR, 14.0)
         .add(Attributes.ARMOR_TOUGHNESS, 8.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
         .add(Attributes.MOVEMENT_SPEED, 0.27)
         .add(Attributes.FOLLOW_RANGE, 64.0)
         .add(Attributes.ATTACK_DAMAGE, 16.0);
   }

   @Override
   protected OrderBrain<?> brain() {
      return this.brain;
   }

   @Override
   @Nullable
   protected Object currentMove() {
      return this.move;
   }

   @Nullable
   public CullMove move() {
      return this.move;
   }

   @Override
   protected float stride() {
      return 2.6F;
   }

   @Override
   protected double knockbackFactor() {
      return 0.0;
   }

   public boolean hardened() {
      return this.hardened > 0;
   }

   @Override
   protected double targetScore(LivingEntity e, double distance, double threat, double rangedShare) {
      // the closest brawler, and above all a Hulkbuster
      double s = 60.0 / (4.0 + distance) + threat * 0.015;
      if (heavy(e)) {
         s *= 2.2;
      }

      if (airborne(e)) {
         s *= 0.7;
      }

      return s;
   }

   @Override
   protected void onArrived() {
      ServerLevel level = (ServerLevel)this.level();
      ThanosWarSounds.play(level, this.position(), ThanosWarSounds.CULL_ROAR, 2.4F, 0.9F, 96.0);
      this.fx(ThanosFxS2CPacket.CULL_ROAR, 0, this.position().add(0, 2.4, 0), this.facing(), 1.0F, 20);
      ThanosWar w = this.war;
      if (w != null) {
         w.say(ThanosWar.VOICE_CULL, this, "arrive", HeroLine.Priority.ULTIMATE, 0, true);
      }
   }

   @Override
   protected OrderBrain.Situation situation(LivingEntity t) {
      ThanosWar w = this.war;
      double d = Math.sqrt(t.distanceToSqr(this));
      int close = 0;
      if (w != null) {
         for (ServerPlayer p : w.heroes()) {
            if (p.distanceToSqr(this) < 49.0) {
               close++;
            }
         }
      }

      boolean hulk = heavy(t);
      if (hulk && !this.sawHulk && w != null) {
         this.sawHulk = true;
         w.say(ThanosWar.VOICE_CULL, this, "hulkbuster", HeroLine.Priority.ABILITY, 0, true);
      }

      boolean ranged = w != null && (w.threat.rangedShare(t.getUUID()) >= ThanosWarRules.RANGED_SHARE || d > 14.0);
      boolean threatened = w != null && (w.thanos.threatened() || w.defensive());
      boolean controllable = w != null && w.holds.canHold(t, ThanosHolds.Kind.DRAG);
      boolean pinned = w != null && w.holds.pinned(t);
      return new OrderBrain.Situation(true, d, airborne(t), this.sees(t), ranged, hulk, close, this.incomingProjectiles(8.0), threatened, controllable, pinned,
         this.getHealth() / this.getMaxHealth(), false);
   }

   @Override
   protected boolean choose(OrderBrain.Situation s) {
      CullMove pick = this.brain.choose(s);
      if (pick == null) {
         return false;
      }

      this.brain.commit(pick);
      this.move = pick;
      this.moveTick = 0;
      this.showMove(pick.ordinal());
      this.getNavigation().stop();
      this.begin(pick);
      return true;
   }

   @Override
   protected void endMove() {
      this.releaseHeld(false);
      this.chargeHit.clear();
      if (this.move != null) {
         this.move = null;
         this.showMove(-1);
      }
   }

   @Override
   protected void tickCurrent() {
      CullMove m = this.move;
      if (m == null) {
         return;
      }

      int t = this.moveTick++;
      LivingEntity target = this.target;
      boolean charging = (m == CullMove.CHARGE || m == CullMove.CLASH) && t >= m.telegraph() && t < m.telegraph() + m.active();
      if (target != null && !charging) {
         this.faceTowards(target.position(), t < m.telegraph() ? 9.0F : 3.0F);
      }

      if (t < m.telegraph()) {
         this.telegraph(m, t);
      } else if (t < m.telegraph() + m.active()) {
         this.active(m, t - m.telegraph());
      } else if (t >= m.length()) {
         this.endMove();
      }
   }

   // ---- moves ------------------------------------------------------------------------------------
   private void begin(CullMove m) {
      ServerLevel level = (ServerLevel)this.level();
      switch (m) {
         case SWING -> ThanosWarSounds.play(level, this.position(), ThanosWarSounds.CULL_SWING, 1.4F, 0.8F, 32.0);
         case SLAM -> this.fx(ThanosFxS2CPacket.CULL_SLAM, 0, this.front(2.2), Vec3.ZERO, 6.0F, m.telegraph());
         case CHARGE, CLASH -> {
            LivingEntity t = this.target;
            Vec3 to = t == null ? this.facing() : t.position().subtract(this.position());
            this.chargeDir = new Vec3(to.x, 0.0, to.z).lengthSqr() < 1.0E-4 ? this.facing() : new Vec3(to.x, 0.0, to.z).normalize();
            this.chargeBlocks = ThanosWarRules.CHARGE_BLOCKS;
            this.fx(ThanosFxS2CPacket.CULL_CHARGE, 0, this.position(), this.chargeDir, 26.0F, m.telegraph());
            ThanosWarSounds.play(level, this.position(), ThanosWarSounds.CULL_ROAR, 2.0F, 1.1F, 64.0);
            if (this.war != null) {
               this.war.say(ThanosWar.VOICE_CULL, this, m == CullMove.CLASH ? "clash" : "charge", HeroLine.Priority.ABILITY, 500, false);
            }
         }
         case CHAIN -> ThanosWarSounds.play(level, this.position(), ThanosWarSounds.CULL_CHAIN, 1.4F, 0.9F, 40.0);
         case GRAB -> ThanosWarSounds.play(level, this.position(), ThanosWarSounds.CULL_GRAB, 1.2F, 1.0F, 32.0);
         case ROAR -> {
         }
      }
   }

   private void telegraph(CullMove m, int t) {
      if ((m == CullMove.CHARGE || m == CullMove.CLASH) && t < m.telegraph() - 4 && this.target != null) {
         // he lines up on the target until the last moment
         Vec3 to = this.target.position().subtract(this.position());
         if (to.x * to.x + to.z * to.z > 1.0E-4) {
            this.chargeDir = new Vec3(to.x, 0.0, to.z).normalize();
         }
      }
   }

   private void active(CullMove m, int t) {
      ServerLevel level = (ServerLevel)this.level();
      LivingEntity target = this.target;
      switch (m) {
         case SWING -> {
            if (t == 1) {
               this.swing(m.damage(), 4.6, 0.25);
            }
         }
         case SLAM -> {
            if (t == 0) {
               this.slam(m.damage());
            }
         }
         case CHARGE -> this.tickCharge(m, t, false);
         case CLASH -> this.tickCharge(m, t, true);
         case CHAIN -> this.tickChain(m, t);
         case GRAB -> this.tickGrab(m, t);
         case ROAR -> {
            if (t == 0) {
               this.hardened = 200;
               this.fx(ThanosFxS2CPacket.CULL_ROAR, 0, this.position().add(0, 2.4, 0), this.facing(), 1.4F, 30);
               ThanosWarSounds.play(level, this.position(), ThanosWarSounds.CULL_ROAR, 3.0F, 0.85F, 96.0);
               for (LivingEntity v : this.victims(this.position(), 5.5)) {
                  Vec3 out = v.position().subtract(this.position());
                  out = new Vec3(out.x, 0.0, out.z).lengthSqr() < 1.0E-4 ? this.facing() : new Vec3(out.x, 0.0, out.z).normalize();
                  ThanosTargets.push(v, out.scale(0.9).add(0, 0.3, 0));
               }

               // the Chitauri around him rally on his target
               if (target != null) {
                  for (ChitauriSoldier s : level.getEntitiesOfClass(ChitauriSoldier.class, this.getBoundingBox().inflate(24.0))) {
                     if (s.getTarget() == null) {
                        s.setTarget(target);
                     }
                  }
               }
            }
         }
      }
   }

   private Vec3 front(double ahead) {
      return this.position().add(this.facing().scale(ahead));
   }

   /** A wide arc in front of him. */
   private void swing(float damage, double reach, double minDot) {
      Vec3 f = this.facing();
      boolean hit = false;
      for (LivingEntity v : this.victims(this.position().add(0, 1.2, 0), reach)) {
         Vec3 to = v.position().subtract(this.position());
         Vec3 flat = new Vec3(to.x, 0.0, to.z);
         if (flat.lengthSqr() > 1.0E-4 && flat.normalize().dot(f) < minDot) {
            continue;
         }

         if (this.strike(v, damage)) {
            hit = true;
            ThanosTargets.push(v, f.scale(1.1).add(0, 0.35, 0));
         }
      }

      ServerLevel level = (ServerLevel)this.level();
      ThanosWarSounds.play(level, this.position(), hit ? ThanosWarSounds.CULL_SLAM : ThanosWarSounds.CULL_SWING, hit ? 1.6F : 1.0F, 1.1F, 40.0);
   }

   private void slam(float damage) {
      ServerLevel level = (ServerLevel)this.level();
      Vec3 at = this.front(2.2);
      this.fx(ThanosFxS2CPacket.CULL_SLAM, 1, at, Vec3.ZERO, 6.5F, 0);
      ThanosWarSounds.play(level, at, ThanosWarSounds.CULL_SLAM, 3.0F, 0.8F, 80.0);
      for (LivingEntity v : this.victims(at, 6.5)) {
         if (airborne(v) && v.getY() > at.y + 2.5) {
            continue; // the ring runs along the ground
         }

         double d = v.position().distanceTo(at);
         float f = ThanosWarRules.strikeFalloff(d, 6.5);
         if (this.strike(v, damage * f)) {
            Vec3 out = v.position().subtract(at);
            out = new Vec3(out.x, 0.0, out.z).lengthSqr() < 1.0E-4 ? this.facing() : new Vec3(out.x, 0.0, out.z).normalize();
            ThanosTargets.push(v, out.scale(0.8 * f).add(0, 0.7 * f, 0));
         }
      }

      ThanosWar w = this.war;
      if (w != null) {
         w.destruction.crater(at.add(0, -0.5, 0), 2.2, ThanosWarRules.STRIKE_HARDNESS, 12);
         w.destruction.impact(at, 7.0F, 0.4F, AftermathCause.DESTRUCTION, false);
      }
   }

   /** Straight on through walls; stops on the target or a wall he cannot break (then staggers). */
   private void tickCharge(CullMove m, int t, boolean clash) {
      ServerLevel level = (ServerLevel)this.level();
      ThanosWar w = this.war;
      double speed = clash ? 0.85 : 0.95;
      Vec3 dir = this.chargeDir;
      this.setYRot((float)(Math.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0F);
      this.setYBodyRot(this.getYRot());
      if (t == 0) {
         // the burst off the mark: the rush of the charge (the roar was the wind-up)
         ThanosWarSounds.play(level, this.position(), ThanosWarSounds.CULL_CHARGE, 2.2F, clash ? 0.9F : 1.0F, 64.0);
      }

      if (t % 4 == 0) {
         ThanosWarSounds.play(level, this.position(), ThanosWarSounds.CULL_STEP, 1.4F, 1.2F, 40.0);
      }

      // the wall ahead goes first (a 2 x 3 corridor)
      if (w != null && this.chargeBlocks > 0 && !clash) {
         int broke = w.destruction.corridor(this.position().add(dir.scale(0.9)), dir, 1.4, 2, 3, ThanosWarRules.STRIKE_HARDNESS, Math.min(8, this.chargeBlocks));
         if (broke > 0) {
            this.chargeBlocks -= broke;
            this.fx(ThanosFxS2CPacket.WALL_BREAK, 0, this.position().add(dir.scale(1.6)).add(0, 1.4, 0), dir, Math.min(3.0F, broke * 0.4F), broke);
            ThanosWarSounds.play(level, this.position(), ThanosWarSounds.CULL_WALL, 2.2F, 0.85F + this.random.nextFloat() * 0.2F, 64.0);
            if (this.chargeBlocks <= 0 || broke >= 4) {
               w.destruction.impact(this.position(), 4.0F, 0.25F, AftermathCause.DESTRUCTION, false);
            }
         }
      }

      Vec3 v = dir.scale(speed);
      this.setDeltaMovement(v.x, this.getDeltaMovement().y, v.z);
      for (LivingEntity e : this.victims(this.position().add(dir.scale(1.0)).add(0, 1.0, 0), 2.0)) {
         if (!this.chargeHit.add(e.getId())) {
            continue;
         }

         if (this.strike(e, m.damage())) {
            ThanosTargets.push(e, dir.scale(clash ? 1.0 : 1.6).add(0, 0.55, 0));
            this.fx(clash ? ThanosFxS2CPacket.CLASH : ThanosFxS2CPacket.WALL_BREAK, e.getId(), e.position().add(0, 1, 0), dir, 1.5F, 0);
            ThanosWarSounds.play(level, e.position(), ThanosWarSounds.CULL_SLAM, 2.0F, 0.9F, 48.0);
         }

         if (e == this.target || clash) {
            // locked with the target: the charge ends here, the clash drives the armour back
            if (clash) {
               this.hardened = Math.max(this.hardened, 60);
               this.stagger = 10;
            }

            this.moveTick = m.telegraph() + m.active();
            this.setDeltaMovement(Vec3.ZERO);
            return;
         }
      }

      // a wall he could not break: he crashes into it
      if (t > 3 && this.horizontalCollision) {
         Vec3 ahead = this.position().add(0, 1.0, 0).add(dir.scale(1.2));
         boolean solid = level.clip(new ClipContext(this.position().add(0, 1.0, 0), ahead, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
            .getType() != HitResult.Type.MISS;
         if (solid) {
            this.moveTick = m.length() - 4;
            this.stagger = 24;
            this.react(REACT_STAGGER);
            this.fx(ThanosFxS2CPacket.WALL_BREAK, 0, ahead, dir, 1.0F, 0);
            ThanosWarSounds.play(level, ahead, ThanosWarSounds.CULL_WALL, 2.4F, 0.6F, 64.0);
         }
      }
   }

   private void tickChain(CullMove m, int t) {
      ThanosWar w = this.war;
      LivingEntity target = this.target;
      ServerLevel level = (ServerLevel)this.level();
      if (w == null) {
         return;
      }

      if (t == 0) {
         if (target == null || !this.sees(target)) {
            this.moveTick = m.length() - m.recovery();
            return;
         }

         this.fx(ThanosFxS2CPacket.CULL_CHAIN, target.getId(), this.hand(), target.position().subtract(this.hand()), 1.0F, m.active());
         ThanosWarSounds.play(level, this.position(), ThanosWarSounds.CULL_CHAIN, 1.8F, 1.1F, 48.0);
         this.strike(target, m.damage());
         int granted = w.holds.canHold(target, ThanosHolds.Kind.DRAG)
            ? w.holds.hold(target, ThanosHolds.Kind.DRAG, this, m.active(), () -> this.position().add(this.facing().scale(2.2)).add(0, 1.0, 0), 0.75)
            : 0;
         if (granted > 0) {
            this.held = target;
            if (airborne(target)) {
               w.say(ThanosWar.VOICE_CULL, this, "chain", HeroLine.Priority.ABILITY, 500, false);
            }
         } else {
            // no hold: the hook still yanks him a little
            Vec3 pull = this.position().subtract(target.position()).normalize().scale(0.9);
            ThanosTargets.push(target, pull.add(0, 0.2, 0));
            this.moveTick = m.length() - m.recovery();
         }

         return;
      }

      LivingEntity h = this.held;
      if (h == null) {
         return;
      }

      if (!w.holds.held(h) || h.distanceToSqr(this) < 9.0) {
         this.releaseHeld(false);
         // reeled in: straight into a swing
         this.moveTick = m.length();
         this.brain.tick();
      }
   }

   private void tickGrab(CullMove m, int t) {
      ThanosWar w = this.war;
      LivingEntity target = this.target;
      if (w == null) {
         return;
      }

      if (t == 0) {
         if (target == null || target.distanceToSqr(this) > 25.0 || !w.holds.canHold(target, ThanosHolds.Kind.CARRY)) {
            this.swing(m.damage(), 4.0, 0.3);
            this.moveTick = m.length() - m.recovery();
            return;
         }

         int granted = w.holds.hold(target, ThanosHolds.Kind.CARRY, this, m.active() - 4, () -> this.position().add(0, this.getBbHeight() + 0.6, 0), 0.6);
         if (granted <= 0) {
            this.swing(m.damage(), 4.0, 0.3);
            this.moveTick = m.length() - m.recovery();
            return;
         }

         this.held = target;
         ThanosWarSounds.play((ServerLevel)this.level(), this.position(), ThanosWarSounds.CULL_GRAB, 1.6F, 0.9F, 40.0);
         return;
      }

      LivingEntity h = this.held;
      if (h != null && (!w.holds.held(h) || t >= m.active() - 4)) {
         this.releaseHeld(true);
      }
   }

   private void releaseHeld(boolean thrown) {
      LivingEntity h = this.held;
      this.held = null;
      ThanosWar w = this.war;
      if (h == null || w == null) {
         return;
      }

      w.holds.release(h);
      if (thrown && h.isAlive()) {
         ThanosTargets.push(h, this.facing().scale(1.8).add(0, 0.7, 0));
         this.strike(h, CullMove.GRAB.damage());
         ThanosWarSounds.play((ServerLevel)this.level(), h.position(), ThanosWarSounds.CULL_SWING, 1.6F, 0.7F, 40.0);
      }
   }

   public Vec3 hand() {
      Vec3 f = this.facing();
      Vec3 side = new Vec3(-f.z, 0.0, f.x);
      return this.position().add(0, 2.2, 0).add(f.scale(0.6)).add(side.scale(0.9));
   }

   // ---- between moves ---------------------------------------------------------------------------------
   @Override
   protected void position(LivingEntity t) {
      this.faceTowards(t.position(), 10.0F);
      double d2 = t.distanceToSqr(this);
      if (d2 > 9.0) {
         double speed = this.flag(FLAG_ENRAGED) ? 1.25 : 1.1;
         if (this.tickCount % 10 == 0 || this.getNavigation().isDone()) {
            this.getNavigation().moveTo(t, speed);
         }
      } else {
         this.getNavigation().stop();
      }

      if (this.getNavigation().isInProgress() && --this.stepTimer <= 0) {
         this.stepTimer = 9;
         ThanosWarSounds.play((ServerLevel)this.level(), this.position(), ThanosWarSounds.CULL_STEP, 1.1F, 0.9F + this.random.nextFloat() * 0.2F, 32.0);
      }
   }

   @Override
   protected void passive() {
      if (this.hardened > 0) {
         this.hardened--;
      }

      if (this.target == null) {
         ThanosWar w = this.war;
         if (w != null && this.distanceToSqr(w.thanos) > 100.0 && this.tickCount % 20 == 0) {
            this.getNavigation().moveTo(w.thanos, 1.0);
         }
      }
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      // hardened (his roar, the clash): the armour takes the edge off
      return super.hurt(source, this.hardened > 0 ? amount * 0.6F : amount);
   }

   @Override
   protected void hurtSound() {
      ThanosWarSounds.play((ServerLevel)this.level(), this.position(), ThanosWarSounds.CULL_HURT, 1.4F, 0.9F + this.random.nextFloat() * 0.15F, 40.0);
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return null;
   }

   @Override
   protected SoundEvent getDeathSound() {
      return null;
   }

   @Override
   protected SoundEvent getAmbientSound() {
      return null;
   }

   @Override
   protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
   }

   @Override
   public void die(DamageSource source) {
      super.die(source);
      if (!this.level().isClientSide()) {
         this.endMove();
         ThanosWarSounds.play((ServerLevel)this.level(), this.position(), ThanosWarSounds.CULL_DEATH, 2.2F, 1.0F, 80.0);
         ThanosWar w = this.war;
         if (w != null) {
            w.say(ThanosWar.VOICE_CULL, this, "death", HeroLine.Priority.ULTIMATE, 0, true);
         }
      }
   }

   @Override
   public void remove(RemovalReason reason) {
      if (!this.level().isClientSide()) {
         this.releaseHeld(false);
      }

      super.remove(reason);
   }
}
