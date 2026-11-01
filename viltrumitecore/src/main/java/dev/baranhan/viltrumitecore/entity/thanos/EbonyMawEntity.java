package dev.baranhan.viltrumitecore.entity.thanos;

import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosTargets;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.ThanosHolds;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.ThanosWar;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.ThanosWarRules;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.ThanosWarSounds;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.WarGround;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.order.MawMove;
import dev.baranhan.viltrumitecore.worldevent.thanos.war.order.OrderBrain;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ebony Maw (THANOS 2.0): Thanos's herald, a floating telekinetic. He keeps 10-18 blocks from his
 * target and uses the city: blocks torn out of the buildings around him circle him and fly at the
 * heroes one after another (several heroes in one volley), one heavy block thrown fast, a hero
 * lifted and hurled (through the hold ledger and the hero's own protections), a ring of debris
 * around Thanos that stops shots, a piece of a building dropped on a hero, a drift away from
 * brawlers. Between moves he catches shots aimed at him or at Thanos (they stop and drop).
 */
public class EbonyMawEntity extends BlackOrderEntity {
   static final double PREFERRED = 13.0;
   static final double HOVER = 3.2;
   static final int CATCH_GAP = 30;
   private final OrderBrain<MawMove> brain;
   @Nullable
   private MawMove move;
   private final List<WarDebrisEntity> held = new ArrayList<>();
   private int thrown;
   private int catchCooldown;
   private int structuresCheck;
   private boolean structures;
   private Vec3 strafe = Vec3.ZERO;
   private int strafeTimer;
   @Nullable
   private LivingEntity grabbed;

   public EbonyMawEntity(EntityType<? extends EbonyMawEntity> type, Level level) {
      super(type, level);
      this.brain = new OrderBrain<>(MawMove.class, level.random.nextLong());
      this.setNoGravity(true);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return PathfinderMob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, ThanosWarRules.MAW_HEALTH)
         .add(Attributes.ARMOR, 6.0)
         .add(Attributes.ARMOR_TOUGHNESS, 4.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
         .add(Attributes.MOVEMENT_SPEED, 0.28)
         .add(Attributes.FLYING_SPEED, 0.4)
         .add(Attributes.FOLLOW_RANGE, 64.0)
         .add(Attributes.ATTACK_DAMAGE, 8.0);
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
   public MawMove move() {
      return this.move;
   }

   @Override
   protected float stride() {
      return 3.0F;
   }

   @Override
   protected double knockbackFactor() {
      return 0.6;
   }

   @Override
   protected double targetScore(LivingEntity e, double distance, double threat, double rangedShare) {
      // he hunts the ones who fight from afar and the flyers: his reach is the city itself
      double s = 40.0 / (8.0 + distance) + threat * 0.02;
      if (rangedShare >= ThanosWarRules.RANGED_SHARE) {
         s *= 1.6;
      }

      if (airborne(e)) {
         s *= 1.4;
      }

      return s;
   }

   @Override
   protected void onArrived() {
      ThanosWar w = this.war;
      if (w != null) {
         w.say(ThanosWar.VOICE_MAW, this, "arrive", HeroLine.Priority.ULTIMATE, 0, true);
      }

      ThanosWarSounds.play((ServerLevel)this.level(), this.position(), ThanosWarSounds.MAW_VOICE, 1.6F, 1.0F, 64.0);
   }

   @Override
   protected OrderBrain.Situation situation(LivingEntity t) {
      ThanosWar w = this.war;
      double d = Math.sqrt(t.distanceToSqr(this));
      if (--this.structuresCheck <= 0) {
         this.structuresCheck = 40;
         this.structures = this.structuresNear();
      }

      int close = 0;
      if (w != null) {
         for (ServerPlayer p : w.heroes()) {
            if (p.distanceToSqr(this) < 36.0) {
               close++;
            }
         }
      }

      boolean ranged = w != null && w.threat.rangedShare(t.getUUID()) >= ThanosWarRules.RANGED_SHARE;
      boolean threatened = w != null && (w.thanos.threatened() || w.defensive());
      boolean controllable = w != null && w.holds.canHold(t, ThanosHolds.Kind.CARRY);
      boolean pinned = w != null && w.holds.pinned(t);
      return new OrderBrain.Situation(true, d, airborne(t), this.sees(t), ranged, heavy(t), close, this.incomingProjectiles(12.0) + this.thanosIncoming(),
         threatened, controllable, pinned, this.getHealth() / this.getMaxHealth(), this.structures);
   }

   private int thanosIncoming() {
      ThanosWar w = this.war;
      if (w == null || w.thanos.distanceToSqr(this) > 30.0 * 30.0) {
         return 0;
      }

      int n = 0;
      for (Projectile p : this.level().getEntitiesOfClass(Projectile.class, w.thanos.getBoundingBox().inflate(10.0))) {
         if (p.getOwner() != null && !friendly(p.getOwner()) && p.getDeltaMovement().lengthSqr() > 0.04) {
            n++;
         }
      }

      return n;
   }

   @Override
   protected boolean choose(OrderBrain.Situation s) {
      MawMove pick;
      if (this.flag(FLAG_GUARD) && this.brain.allowed(MawMove.BARRIER)) {
         pick = MawMove.BARRIER;
      } else {
         pick = this.brain.choose(s);
      }

      if (pick == null) {
         return false;
      }

      this.start(pick);
      return true;
   }

   private void start(MawMove m) {
      this.brain.commit(m);
      this.move = m;
      this.moveTick = 0;
      this.thrown = 0;
      this.showMove(m.ordinal());
      this.begin(m);
   }

   @Override
   protected void endMove() {
      if (this.move == MawMove.GRAB) {
         this.releaseGrab(false);
      }

      for (WarDebrisEntity d : this.held) {
         if (d.isAlive() && d.mode() != WarDebrisEntity.FLY && d.mode() != WarDebrisEntity.FALL) {
            d.drop(3.0F, 1.0, 0);
         }
      }

      this.held.clear();
      if (this.move != null) {
         this.move = null;
         this.showMove(-1);
      }
   }

   @Override
   protected void tickCurrent() {
      MawMove m = this.move;
      if (m == null) {
         return;
      }

      int t = this.moveTick++;
      LivingEntity target = this.target;
      if (target != null) {
         this.faceTowards(target.position(), 10.0F);
      }

      this.hover(target, 0.6);
      if (t < m.telegraph()) {
         this.telegraph(m, t);
      } else if (t < m.telegraph() + m.active()) {
         this.active(m, t - m.telegraph());
      } else if (t >= m.length()) {
         this.endMove();
      }
   }

   // ---- moves ------------------------------------------------------------------------------------
   private void begin(MawMove m) {
      ServerLevel level = (ServerLevel)this.level();
      switch (m) {
         case SPEAR -> this.liftAround(1, 1.4F, true);
         case VOLLEY -> {
            int n = Math.min(6, 3 + (this.war == null ? 0 : this.war.heroCount() - 1));
            this.liftAround(n, 1.0F, false);
            ThanosWarSounds.play(level, this.position(), ThanosWarSounds.MAW_LIFT, 1.6F, 1.0F, 48.0);
            if (this.war != null) {
               this.war.say(ThanosWar.VOICE_MAW, this, "volley", HeroLine.Priority.ABILITY, 400, false);
            }
         }
         case GRAB -> {
            if (this.target != null) {
               this.fx(ThanosFxS2CPacket.TK_GRAB, this.target.getId(), this.hand(), Vec3.ZERO, 0.0F, m.telegraph());
            }

            ThanosWarSounds.play(level, this.position(), ThanosWarSounds.MAW_GRAB, 1.4F, 1.0F, 40.0);
         }
         case BARRIER -> {
            this.liftAround(7, 1.0F, false);
            ThanosWarSounds.play(level, this.position(), ThanosWarSounds.MAW_BARRIER, 1.6F, 1.0F, 48.0);
         }
         case TOPPLE -> this.topple();
         case BLINK -> this.fx(ThanosFxS2CPacket.TK_LIFT, 0, this.position(), Vec3.ZERO, 1.0F, m.telegraph());
      }
   }

   private void telegraph(MawMove m, int t) {
      if (m == MawMove.BARRIER && t == m.telegraph() - 1) {
         this.formBarrier(m.active());
      }
   }

   private void active(MawMove m, int t) {
      LivingEntity target = this.target;
      ServerLevel level = (ServerLevel)this.level();
      switch (m) {
         case SPEAR -> {
            if (t == 0 && target != null) {
               this.throwNext(target, 1.9, m.damage(), 1.4);
            }
         }
         case VOLLEY -> {
            if (t % 5 == 0) {
               LivingEntity at = this.volleyTarget(this.thrown);
               if (at != null) {
                  this.throwNext(at, 1.5, m.damage(), 1.6);
               }
            }
         }
         case GRAB -> this.tickGrab(m, t);
         case BARRIER -> {
            if (t % 20 == 0) {
               this.formBarrier(m.active() - t);
            }
         }
         case TOPPLE -> {
            if (t == 0) {
               for (WarDebrisEntity d : this.held) {
                  if (d.isAlive()) {
                     d.drop(m.damage(), 3.0, ThanosWarRules.ROCK_BLOCKS / 2);
                  }
               }

               this.held.clear();
               ThanosWarSounds.play(level, this.position(), ThanosWarSounds.MAW_THROW, 1.6F, 0.7F, 48.0);
            }
         }
         case BLINK -> {
            if (t == 0) {
               this.blink();
            }
         }
      }
   }

   /** Tears {@code n} blocks out of the city near him (loose rubble when there is nothing to tear). */
   private void liftAround(int n, float size, boolean side) {
      ThanosWar w = this.war;
      if (w == null) {
         return;
      }

      ServerLevel level = (ServerLevel)this.level();
      List<BlockPos> spots = w.destruction.liftable(this.position(), 10.0, n, ThanosWarRules.STRIKE_HARDNESS);
      int made = 0;
      for (BlockPos pos : spots) {
         BlockState state = w.destruction.lift(pos, ThanosWarRules.STRIKE_HARDNESS);
         if (state == null) {
            continue;
         }

         WarDebrisEntity d = WarDebrisEntity.spawn(level, this, state, Vec3.atCenterOf(pos), size);
         if (d != null) {
            this.held.add(d);
            this.fx(ThanosFxS2CPacket.TK_LIFT, d.getId(), Vec3.atCenterOf(pos), Vec3.ZERO, size, 12);
            made++;
         }
      }

      // nothing to tear out (open ground, budget spent): rubble from the street
      for (int i = made; i < n; i++) {
         Vec3 g = WarGround.top(level, this.getX() + this.random.nextGaussian() * 4.0, this.getZ() + this.random.nextGaussian() * 4.0);
         WarDebrisEntity d = WarDebrisEntity.spawn(level, this, i % 2 == 0 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState(), g, size);
         if (d != null) {
            this.held.add(d);
         }
      }

      for (int i = 0; i < this.held.size(); i++) {
         WarDebrisEntity d = this.held.get(i);
         if (side && i == 0) {
            d.hold(this.hand().add(this.facing().scale(1.2)).add(0, 0.6, 0));
         } else {
            d.orbit(Math.PI * 2.0 * i / Math.max(1, this.held.size()), 2.6 + (i % 2) * 0.8, 1.8 + (i % 3) * 0.6, 0.14);
         }
      }
   }

   @Nullable
   private LivingEntity volleyTarget(int index) {
      ThanosWar w = this.war;
      if (w == null) {
         return this.target;
      }

      // several heroes in reach: the volley is shared out between them
      List<LivingEntity> in = new ArrayList<>();
      if (this.target != null) {
         in.add(this.target);
      }

      for (ServerPlayer p : w.heroes()) {
         if (p != this.target && this.validTarget(p) && p.distanceToSqr(this) < 34.0 * 34.0) {
            in.add(p);
         }
      }

      return in.isEmpty() ? null : in.get(index % in.size());
   }

   private void throwNext(LivingEntity at, double speed, float damage, double radius) {
      WarDebrisEntity next = null;
      for (WarDebrisEntity d : this.held) {
         if (d.isAlive() && (d.mode() == WarDebrisEntity.ORBIT || d.mode() == WarDebrisEntity.HOLD)) {
            next = d;
            break;
         }
      }

      if (next == null) {
         return;
      }

      this.held.remove(next);
      Vec3 aim = at.position().add(0, at.getBbHeight() * 0.5, 0);
      double dist = aim.distanceTo(next.position());
      double ticks = dist / speed;
      Vec3 lead = aim.add(at.getDeltaMovement().scale(Math.min(12.0, ticks) * 0.8));
      Vec3 v = lead.subtract(next.position()).normalize().scale(speed);
      next.launch(v, at, damage, radius, 3);
      this.thrown++;
      ThanosWarSounds.play((ServerLevel)this.level(), next.position(), ThanosWarSounds.MAW_THROW, 1.2F, 0.9F + this.random.nextFloat() * 0.3F, 40.0);
   }

   private void formBarrier(int ticks) {
      ThanosWar w = this.war;
      Entity guard = w != null && w.thanos.distanceToSqr(this) < 40.0 * 40.0 ? w.thanos : this;
      Vec3 c = guard.position().add(0, guard.getBbHeight() * 0.55, 0);
      int n = this.held.size();
      double spin = this.tickCount * 0.02;
      for (int i = 0; i < n; i++) {
         WarDebrisEntity d = this.held.get(i);
         if (!d.isAlive()) {
            continue;
         }

         double a = spin + Math.PI * 2.0 * i / Math.max(1, n);
         double r = guard == this ? 2.2 : 2.9;
         d.barrier(c.add(Math.cos(a) * r, Math.sin(i * 1.7) * 0.9, Math.sin(a) * r), ticks + 4);
      }

      this.fx(ThanosFxS2CPacket.TK_BARRIER, guard.getId(), c, Vec3.ZERO, guard == this ? 2.2F : 2.9F, ticks);
   }

   private void topple() {
      ThanosWar w = this.war;
      LivingEntity t = this.target;
      if (w == null || t == null) {
         return;
      }

      ServerLevel level = (ServerLevel)this.level();
      // a piece of the nearest building (above the street if there is one) breaks loose over the hero
      List<BlockPos> spots = w.destruction.liftable(t.position().add(0, 4, 0), 9.0, ThanosWarRules.TOPPLE_BLOCKS, ThanosWarRules.STRIKE_HARDNESS);
      Vec3 above = t.position().add(0, 9.0, 0);
      BlockState chunk = Blocks.STONE_BRICKS.defaultBlockState();
      int torn = 0;
      for (BlockPos pos : spots) {
         BlockState s = w.destruction.lift(pos, ThanosWarRules.STRIKE_HARDNESS);
         if (s != null) {
            chunk = s;
            torn++;
            this.fx(ThanosFxS2CPacket.TK_LIFT, 0, Vec3.atCenterOf(pos), above.subtract(Vec3.atCenterOf(pos)), 1.0F, 20);
         }
      }

      float size = 1.6F + Math.min(1.6F, torn * 0.12F);
      WarDebrisEntity big = WarDebrisEntity.spawn(level, this, chunk, spots.isEmpty() ? above : Vec3.atCenterOf(spots.get(0)), size);
      if (big != null) {
         big.hold(above);
         this.held.add(big);
      }

      for (int i = 0; i < 2; i++) {
         WarDebrisEntity d = WarDebrisEntity.spawn(level, this, chunk, above.add(this.random.nextGaussian(), 1.0, this.random.nextGaussian()), 1.0F);
         if (d != null) {
            d.hold(above.add(this.random.nextGaussian() * 1.6, 0.8, this.random.nextGaussian() * 1.6));
            this.held.add(d);
         }
      }

      Vec3 ground = WarGround.below(level, t.position().add(0, 1, 0), 24);
      ThanosTargets.fx(level, ThanosFxS2CPacket.MOON_WARN, this.getId(), 1, ground, above.subtract(ground), 3.0F, MawMove.TOPPLE.telegraph() + 12);
      ThanosWarSounds.play(level, above, ThanosWarSounds.MAW_LIFT, 2.0F, 0.6F, 56.0);
   }

   private void tickGrab(MawMove m, int t) {
      ThanosWar w = this.war;
      LivingEntity target = this.target;
      if (w == null) {
         return;
      }

      if (t == 0) {
         if (target == null || !w.holds.canHold(target, ThanosHolds.Kind.CARRY)) {
            // the hold would not land (protected, held too long already): a spear instead
            this.moveTick = m.telegraph() + m.active();
            return;
         }

         int granted = w.holds.hold(target, ThanosHolds.Kind.CARRY, this, m.active() - 8, () -> this.hand().add(this.facing().scale(4.0)).add(0, 3.5, 0), 0.4);
         if (granted <= 0) {
            this.moveTick = m.telegraph() + m.active();
            return;
         }

         this.grabbed = target;
         this.fx(ThanosFxS2CPacket.TK_GRAB, target.getId(), this.hand(), Vec3.ZERO, 1.0F, granted);
         if (target instanceof ServerPlayer) {
            w.say(ThanosWar.VOICE_MAW, this, "grab", HeroLine.Priority.ABILITY, 600, false);
         }

         return;
      }

      LivingEntity g = this.grabbed;
      if (g == null) {
         return;
      }

      if (!w.holds.held(g) || t >= m.active() - 8) {
         this.releaseGrab(true);
      }
   }

   private void releaseGrab(boolean hurl) {
      LivingEntity g = this.grabbed;
      this.grabbed = null;
      ThanosWar w = this.war;
      if (g == null || w == null) {
         return;
      }

      w.holds.release(g);
      if (hurl && g.isAlive()) {
         // slammed down and away
         Vec3 away = g.position().subtract(this.position());
         away = new Vec3(away.x, 0.0, away.z);
         away = away.lengthSqr() < 1.0E-4 ? this.facing() : away.normalize();
         ThanosTargets.push(g, away.scale(1.3).add(0.0, -0.9, 0.0));
         this.strike(g, MawMove.GRAB.damage());
         ThanosWarSounds.play((ServerLevel)this.level(), g.position(), ThanosWarSounds.MAW_THROW, 1.4F, 0.8F, 40.0);
      }
   }

   private void blink() {
      ThanosWar w = this.war;
      Vec3 from = this.position();
      Vec3 away = Vec3.ZERO;
      if (w != null) {
         for (ServerPlayer p : w.heroes()) {
            Vec3 d = from.subtract(p.position());
            double len = d.length();
            if (len < 10.0 && len > 0.01) {
               away = away.add(d.scale(1.0 / len));
            }
         }
      }

      if (away.lengthSqr() < 1.0E-4) {
         away = this.facing().scale(-1.0);
      }

      away = new Vec3(away.x, 0.0, away.z).normalize();
      ServerLevel level = (ServerLevel)this.level();
      for (int tries = 0; tries < 6; tries++) {
         double dist = 9.0 + this.random.nextDouble() * 5.0;
         double turn = (this.random.nextDouble() - 0.5) * 1.6;
         Vec3 dir = new Vec3(away.x * Math.cos(turn) - away.z * Math.sin(turn), 0.0, away.x * Math.sin(turn) + away.z * Math.cos(turn));
         Vec3 to = WarGround.below(level, from.add(dir.scale(dist)).add(0, 4, 0), 12).add(0, HOVER, 0);
         if (level.noCollision(this, this.getBoundingBox().move(to.subtract(from)))) {
            this.fx(ThanosFxS2CPacket.TK_LIFT, 0, from, to.subtract(from), 1.4F, 10);
            this.teleportTo(to.x, to.y, to.z);
            return;
         }
      }
   }

   // ---- between moves ---------------------------------------------------------------------------------
   @Override
   protected void position(LivingEntity t) {
      this.faceTowards(t.position(), 8.0F);
      this.hover(t, 1.0);
   }

   /** Floats at his preferred distance from the target, a few blocks over the ground, drifting sideways. */
   private void hover(@Nullable LivingEntity t, double pace) {
      Vec3 pos = this.position();
      Vec3 want;
      if (t != null) {
         Vec3 from = pos.subtract(t.position());
         Vec3 flat = new Vec3(from.x, 0.0, from.z);
         flat = flat.lengthSqr() < 1.0E-4 ? this.facing().scale(-1.0) : flat.normalize();
         if (--this.strafeTimer <= 0) {
            this.strafeTimer = 40 + this.random.nextInt(40);
            this.strafe = new Vec3(-flat.z, 0.0, flat.x).scale(this.random.nextBoolean() ? 1.0 : -1.0);
         }

         want = t.position().add(flat.scale(PREFERRED)).add(this.strafe.scale(3.0));
      } else {
         ThanosWar w = this.war;
         want = w == null ? pos : w.thanos.position().add(this.facing().scale(-5.0));
      }

      Vec3 ground = WarGround.below((ServerLevel)this.level(), new Vec3(want.x, Math.max(want.y, pos.y) + 3.0, want.z), 16);
      double y = Math.max(ground.y + HOVER, t == null ? ground.y + HOVER : Math.min(t.getY() + 2.0, ground.y + 12.0));
      want = new Vec3(want.x, y, want.z);
      Vec3 d = want.subtract(pos);
      double len = d.length();
      double speed = 0.24 * pace * (this.flag(FLAG_ENRAGED) ? 1.2 : 1.0);
      Vec3 v = len < 0.4 ? Vec3.ZERO : d.scale(Math.min(speed, len) / len);
      this.setDeltaMovement(this.getDeltaMovement().scale(0.5).add(v.scale(0.5)));
   }

   @Override
   protected void passive() {
      if (this.target == null) {
         this.hover(null, 0.6);
      }

      this.catchShots();
   }

   /** Shots aimed at him or at Thanos (within 10 blocks of him) stop dead in the air and drop. */
   private void catchShots() {
      if (this.catchCooldown > 0) {
         this.catchCooldown--;
         return;
      }

      ThanosWar w = this.war;
      AABB box = this.getBoundingBox().inflate(10.0);
      int caught = 0;
      for (Projectile p : this.level().getEntitiesOfClass(Projectile.class, box)) {
         Entity owner = p.getOwner();
         if (owner == null || friendly(owner) || caught >= 2) {
            continue;
         }

         Vec3 v = p.getDeltaMovement();
         if (v.lengthSqr() < 0.04) {
            continue;
         }

         boolean atMe = v.normalize().dot(this.position().add(0, 1, 0).subtract(p.position()).normalize()) > 0.8;
         boolean atThanos = w != null && v.normalize().dot(w.thanos.position().add(0, 2, 0).subtract(p.position()).normalize()) > 0.85
            && p.distanceToSqr(w.thanos) < 14.0 * 14.0;
         if (!atMe && !atThanos) {
            continue;
         }

         p.setDeltaMovement(v.scale(0.05).add(0, -0.05, 0));
         p.hurtMarked = true;
         caught++;
         this.fx(ThanosFxS2CPacket.TK_CATCH, p.getId(), p.position(), v, 1.0F, 0);
      }

      if (caught > 0) {
         this.catchCooldown = CATCH_GAP;
         ThanosWarSounds.play((ServerLevel)this.level(), this.position(), ThanosWarSounds.MAW_CATCH, 1.0F, 1.0F, 32.0);
      }
   }

   @Override
   protected boolean deflects(DamageSource source) {
      // a shot that reaches him may still be turned aside (not while he is busy)
      if (this.move == null && source.getDirectEntity() instanceof Projectile && this.catchCooldown <= 0 && this.random.nextInt(4) == 0) {
         this.catchCooldown = CATCH_GAP;
         this.fx(ThanosFxS2CPacket.TK_CATCH, source.getDirectEntity().getId(), source.getDirectEntity().position(), Vec3.ZERO, 1.0F, 0);
         return true;
      }

      return false;
   }

   /** His right hand (where the telekinesis gathers). */
   public Vec3 hand() {
      Vec3 f = this.facing();
      Vec3 side = new Vec3(-f.z, 0.0, f.x);
      return this.position().add(0, 1.5, 0).add(f.scale(0.5)).add(side.scale(-0.45));
   }

   @Override
   protected void hurtSound() {
      ThanosWarSounds.play((ServerLevel)this.level(), this.position(), ThanosWarSounds.MAW_HURT, 1.0F, 0.95F + this.random.nextFloat() * 0.1F, 32.0);
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
   public void die(DamageSource source) {
      super.die(source);
      if (!this.level().isClientSide()) {
         this.endMove();
         ThanosWarSounds.play((ServerLevel)this.level(), this.position(), ThanosWarSounds.MAW_DEATH, 1.8F, 1.0F, 64.0);
         ThanosWar w = this.war;
         if (w != null) {
            w.say(ThanosWar.VOICE_MAW, this, "death", HeroLine.Priority.ULTIMATE, 0, true);
         }
      }
   }

   @Override
   public void remove(RemovalReason reason) {
      if (!this.level().isClientSide()) {
         this.releaseGrab(false);
         for (WarDebrisEntity d : this.held) {
            d.shatter();
         }

         this.held.clear();
      }

      super.remove(reason);
   }
}
