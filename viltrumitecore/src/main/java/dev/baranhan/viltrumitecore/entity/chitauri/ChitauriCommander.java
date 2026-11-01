package dev.baranhan.viltrumitecore.entity.chitauri;

import dev.baranhan.viltrumitecore.balance.BalanceTable;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriDestruction;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriSounds;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriWar;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriNet;
import dev.baranhan.viltrumitecore.worldevent.chitauri.war.WarRules;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Chitauri field commander: taller, crested, in gold-trimmed plate with a war glaive. It leads from behind cover:
 * every soldier within 20 blocks shares its target and aims tighter, its squad takes the war layer's orders with a
 * pointing gesture, and twice in its life it raises the glaive and signals the portal for a reinforcement drop to its
 * position (paid from the hive budget). Close in, it sweeps the glaive in an arc. When it falls, the squads around it
 * break and fall back. Its signals are non-verbal (a horn-like pulse); their meaning goes out as subtitles.
 */
public class ChitauriCommander extends ChitauriSoldier {
   public static final byte EVENT_SIGNAL = 76, EVENT_COMMAND = 77, EVENT_SWEEP = 78, EVENT_DISEMBARK = 79, EVENT_STAGGER = 74;
   public static final int SIGNAL_TICKS = 44;
   public static final double AURA = 20.0;

   private int callsLeft = WarRules.COMMANDER_CALLS;
   private int signalTimer = -1;
   private int signalCooldown = 200;

   public int clientSignalStart = -1000;
   public int clientCommandStart = -1000;
   public int clientSweepStart = -1000;
   public int clientDisembarkStart = -1000;
   public int clientStaggerStart = -1000;

   public ChitauriCommander(EntityType<? extends ChitauriCommander> type, Level level) {
      super(type, level);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, BalanceTable.CHITAURI_COMMANDER_HEALTH)
         .add(Attributes.MOVEMENT_SPEED, 0.26)
         .add(Attributes.FOLLOW_RANGE, 48.0)
         .add(Attributes.ATTACK_DAMAGE, 7.0)
         .add(Attributes.ATTACK_KNOCKBACK, 0.9)
         .add(Attributes.ARMOR, 8.0)
         .add(Attributes.ARMOR_TOUGHNESS, 4.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
   }

   @Override
   protected void registerRoleGoals() {
      this.goalSelector.addGoal(1, new SignalGoal(this));
   }

   public int callsLeft() {
      return this.callsLeft;
   }

   // ---- weapon: a three-bolt fan from the glaive head, a wide sweep up close ----

   @Override
   protected int burstSize() {
      return 2;
   }

   @Override
   protected int burstGap() {
      return 8;
   }

   @Override
   protected int chargeTicks() {
      return 16;
   }

   @Override
   protected int burstCooldown() {
      return 40 + this.random.nextInt(24);
   }

   @Override
   protected double meleeRange() {
      return 3.0;
   }

   @Override
   protected int meleeCooldown() {
      return 30;
   }

   @Override
   protected boolean mayFight() {
      return this.signalTimer < 0;
   }

   @Override
   protected void fireAt(LivingEntity target) {
      ServerLevel level = (ServerLevel)this.level();
      Vec3 from = this.muzzle();
      Vec3 aim = target.position().add(0, target.getBbHeight() * 0.55, 0);
      double dist = from.distanceTo(aim);
      aim = aim.add(target.getDeltaMovement().multiply(1.0, 0.3, 1.0).scale(dist / 2.6 * 0.8));
      Vec3 dir = aim.subtract(from);
      for (int i = -1; i <= 1; i++) {
         ChitauriBolt.shoot(level, this, from, dir.yRot(i * 0.07F), 2.6, 1.0F, 1.2F);
      }
      this.level().broadcastEntityEvent(this, EVENT_SHOOT);
      ChitauriSounds.play(level, from, ChitauriSounds.COMMANDER_STAFF, 2.2F, 0.95F + this.random.nextFloat() * 0.1F);
      ChitauriDestruction.danger(level, this.position(), 14.0F, 80);
   }

   @Override
   protected void melee(LivingEntity target) {
      ServerLevel level = (ServerLevel)this.level();
      this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
      this.level().broadcastEntityEvent(this, EVENT_SWEEP);
      ChitauriSounds.play(level, this.position(), ChitauriSounds.COMMANDER_SWEEP, 1.2F, 0.95F + this.random.nextFloat() * 0.1F);
      float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
      Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(3.2, 1.0, 3.2),
         e -> e.isAlive() && ChitauriMob.validTarget(e))) {
         Vec3 to = e.position().subtract(this.position());
         if (to.horizontalDistance() < 0.3 || to.multiply(1, 0, 1).normalize().dot(fwd) > 0.2) {
            this.doHurtTarget(e);
         }
      }
   }

   @Override
   public void onDisembark() {
      this.level().broadcastEntityEvent(this, EVENT_DISEMBARK);
   }

   /** The war layer gave this commander's squad a new order: a pointing gesture. */
   public void gesture() {
      if (this.signalTimer < 0) {
         this.level().broadcastEntityEvent(this, EVENT_COMMAND);
         ChitauriSounds.play((ServerLevel)this.level(), this.position(), ChitauriSounds.COMMANDER_ORDER, 1.6F, 1.0F);
      }
   }

   @Override
   protected float capHit(DamageSource source, float amount) {
      if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         return amount;
      }
      return Math.min(amount, this.getMaxHealth() * BalanceTable.CHITAURI_COMMANDER_HIT_CAP);
   }

   @Override
   protected void afterHurt(DamageSource source, float amount) {
      if (amount >= this.getMaxHealth() * 0.18F) {
         this.level().broadcastEntityEvent(this, EVENT_STAGGER);
      }
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide || !this.isAlive() || this.tickCount % 20 != 0) {
         return;
      }
      // the aura: shared target, tighter aim
      long until = this.level().getGameTime() + 30;
      LivingEntity t = this.getTarget();
      for (ChitauriSoldier mate : this.level().getEntitiesOfClass(ChitauriSoldier.class, new AABB(this.blockPosition()).inflate(AURA, 6.0, AURA),
         m -> m != this && m.isAlive() && !(m instanceof ChitauriCommander))) {
         mate.commandedUntil = until;
         if (t != null && mate.getTarget() == null && ChitauriMob.validTarget(t)) {
            mate.setTarget(t);
         }
      }
   }

   @Override
   public void die(DamageSource source) {
      super.die(source);
      if (!this.level().isClientSide) {
         ChitauriWar.commanderDown((ServerLevel)this.level(), this);
      }
   }

   @Override
   public void handleEntityEvent(byte id) {
      switch (id) {
         case EVENT_SIGNAL -> this.clientSignalStart = this.tickCount;
         case EVENT_COMMAND -> this.clientCommandStart = this.tickCount;
         case EVENT_SWEEP -> this.clientSweepStart = this.tickCount;
         case EVENT_DISEMBARK -> this.clientDisembarkStart = this.tickCount;
         case EVENT_STAGGER -> this.clientStaggerStart = this.tickCount;
         default -> super.handleEntityEvent(id);
      }
   }

   @Override
   protected SoundEvent getAmbientSound() {
      return ChitauriSounds.COMMANDER_GROWL.get();
   }

   @Override
   public int getAmbientSoundInterval() {
      return 220;
   }

   /** Heroes near and calls left: plant the glaive, raise it, the signal pulse goes up, a drop is called. */
   static final class SignalGoal extends Goal {
      private final ChitauriCommander c;

      SignalGoal(ChitauriCommander c) {
         this.c = c;
         this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
      }

      @Override
      public boolean canUse() {
         if (this.c.signalCooldown > 0) {
            this.c.signalCooldown--;
            return false;
         }
         if (this.c.callsLeft <= 0 || this.c.isPassenger() || this.c.severed() || !(this.c.level() instanceof ServerLevel level)) {
            return false;
         }
         Player hero = level.getNearestPlayer(this.c, 32.0);
         if (hero == null || !ChitauriMob.validTarget(hero) || this.c.distanceToSqr(hero) < 6.0 * 6.0) {
            return false;
         }
         return ChitauriWar.mayCallReinforcements(level, this.c);
      }

      @Override
      public boolean canContinueToUse() {
         return this.c.signalTimer >= 0 && this.c.isAlive();
      }

      @Override
      public void start() {
         this.c.signalTimer = 0;
         this.c.getNavigation().stop();
         this.c.level().broadcastEntityEvent(this.c, EVENT_SIGNAL);
      }

      @Override
      public void stop() {
         this.c.signalTimer = -1;
         this.c.signalCooldown = 600 + this.c.random.nextInt(300);
      }

      @Override
      public boolean requiresUpdateEveryTick() {
         return true;
      }

      @Override
      public void tick() {
         ServerLevel level = (ServerLevel)this.c.level();
         int t = ++this.c.signalTimer;
         if (t == 18) {
            Vec3 top = this.c.position().add(0, 3.6, 0);
            ChitauriNet.fx(level, new ChitauriFxS2CPacket(ChitauriFxS2CPacket.SIGNAL, top, new Vec3(0, 1, 0), 1.0F, this.c.getId()), top, 160);
            ChitauriSounds.playWide(level, top, ChitauriSounds.COMMANDER_SIGNAL, 4.0F, 1.0F, 140.0);
            if (ChitauriWar.callReinforcements(level, this.c)) {
               this.c.callsLeft--;
            }
         }
         if (t >= SIGNAL_TICKS) {
            this.c.signalTimer = -1;
         }
      }
   }
}
