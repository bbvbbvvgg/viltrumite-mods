package dev.baranhan.viltrumitecore.hero.budget.multiverse;

import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.budget.BudgetFaction;
import dev.baranhan.viltrumitecore.hero.budget.BudgetHeroSpec;
import dev.baranhan.viltrumitecore.hero.budget.BudgetHeroes;
import dev.baranhan.viltrumitecore.hero.budget.BudgetMove;
import dev.baranhan.viltrumitecore.hero.budget.BudgetMoveType;
import dev.baranhan.viltrumitecore.hero.budget.BudgetMoves;
import dev.baranhan.viltrumitecore.hero.budget.BudgetShape;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The one multiverse NPC: a regular fighter, a summoned minion, a champion or
 * one of the eight bosses. It wears an expansion hero skin and casts that
 * hero's own budget moves through the shared {@link BudgetMoves} code. Never
 * saved ({@code noSave} entity type), so a world never keeps event leftovers.
 */
public class MultiverseFighter extends PathfinderMob {
   private static final EntityDataAccessor<Integer> HERO = SynchedEntityData.defineId(MultiverseFighter.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> FACTION = SynchedEntityData.defineId(MultiverseFighter.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> BOSS = SynchedEntityData.defineId(MultiverseFighter.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(MultiverseFighter.class, EntityDataSerializers.FLOAT);
   private static final int CHECK_EVERY = 5;
   private final long[] readyAt = new long[4];
   private float damageScale = MultiverseRules.FIGHTER_DAMAGE_SCALE;
   private float cooldownScale = MultiverseRules.FIGHTER_COOLDOWN_SCALE;
   private float specialScale = 1.0F;
   private int session;
   @Nullable
   private UUID owner;
   @Nullable
   private ServerBossEvent bar;
   private int specialTimer = 80;
   private int specialCycle;
   private int counterTicks;
   private int chargeTicks;
   private MultiverseBoss.Mechanic charging;
   private boolean enraged;

   public MultiverseFighter(EntityType<? extends PathfinderMob> type, Level level) {
      super(type, level);
      this.xpReward = 5;
      this.setPersistenceRequired();
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Mob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, 40.0)
         .add(Attributes.MOVEMENT_SPEED, 0.32)
         .add(Attributes.ATTACK_DAMAGE, 4.0)
         .add(Attributes.FOLLOW_RANGE, 40.0)
         .add(Attributes.ARMOR, 4.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.2);
   }

   // ---- spawning -------------------------------------------------------

   /** Spawns one fighter of {@code spec}; {@code boss} may be null for regular fighters. */
   /**
    * Arena leash: puts a knocked-away NPC back on the ground {@link MultiverseRules#LEASH_RETURN}
    * blocks from {@code anchor} (with a portal puff at both ends). No blocks are touched.
    */
   public static void pullBack(ServerLevel level, net.minecraft.world.entity.Entity entity, net.minecraft.world.phys.Vec3 anchor, double angle) {
      net.minecraft.core.BlockPos column = net.minecraft.core.BlockPos.containing(
         anchor.x + Math.cos(angle) * MultiverseRules.LEASH_RETURN, anchor.y, anchor.z + Math.sin(angle) * MultiverseRules.LEASH_RETURN);
      net.minecraft.core.BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
      level.sendParticles(ParticleTypes.PORTAL, entity.getX(), entity.getY() + 1.0, entity.getZ(), 40, 0.4, 0.8, 0.4, 0.4);
      entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
      entity.teleportTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
      entity.fallDistance = 0.0F;
      level.sendParticles(ParticleTypes.PORTAL, entity.getX(), entity.getY() + 1.0, entity.getZ(), 40, 0.4, 0.8, 0.4, 0.4);
   }

   public static MultiverseFighter spawn(ServerLevel level, Vec3 pos, BudgetHeroSpec spec, int session, @Nullable MultiverseBoss boss,
      float healthFactor, float cooldownFactor, float specialFactor) {
      MultiverseFighter fighter = dev.baranhan.viltrumitecore.entity.ViltrumiteEntities.MULTIVERSE_FIGHTER.get().create(level);
      if (fighter == null) {
         return null;
      }

      fighter.session = session;
      fighter.entityData.set(HERO, BudgetHeroes.roster().indexOf(spec));
      fighter.entityData.set(FACTION, spec.faction().ordinal());
      fighter.specialScale = specialFactor;
      float health = 40.0F;
      if (boss != null) {
         fighter.entityData.set(BOSS, MultiverseBoss.indexOf(boss));
         fighter.entityData.set(SCALE, boss.scale());
         health = boss.health() * healthFactor;
         fighter.damageScale = boss.damageScale();
         fighter.cooldownScale = boss.cooldownScale() * cooldownFactor;
         fighter.specialTimer = Math.max(20, (int)(boss.interval() * specialFactor));
         fighter.getAttribute(Attributes.ARMOR).setBaseValue(10.0);
         fighter.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.8);
         fighter.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(8.0);
         fighter.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64.0);
         Component title = Component.translatable(boss.langKey()).withStyle(ChatFormatting.BOLD, ChatFormatting.LIGHT_PURPLE);
         fighter.setCustomName(title);
         fighter.setCustomNameVisible(true);
         fighter.bar = new ServerBossEvent(title, barColor(boss.barColor()), BossEvent.BossBarOverlay.NOTCHED_10);
         fighter.bar.setDarkenScreen(boss.isFinal());
      } else {
         fighter.cooldownScale = MultiverseRules.FIGHTER_COOLDOWN_SCALE * cooldownFactor;
         fighter.setCustomName(Component.literal("[").append(Component.translatable(spec.faction().langKey())).append("] ")
            .withStyle(style -> style.withColor(spec.faction().color)).append(Component.translatable(spec.nameKey()).withStyle(ChatFormatting.WHITE)));
      }

      fighter.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.min(1024.0F, Math.max(1.0F, health)));
      fighter.setHealth(fighter.getMaxHealth());
      fighter.refreshDimensions();
      fighter.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360.0F, 0.0F);
      level.addFreshEntity(fighter);
      return fighter;
   }

   private static BossEvent.BossBarColor barColor(String name) {
      try {
         return BossEvent.BossBarColor.valueOf(name);
      } catch (IllegalArgumentException e) {
         return BossEvent.BossBarColor.PURPLE;
      }
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(HERO, 0);
      this.entityData.define(FACTION, -1);
      this.entityData.define(BOSS, -1);
      this.entityData.define(SCALE, 1.0F);
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (SCALE.equals(key)) {
         this.refreshDimensions();
      }
   }

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false));
      this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
      this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
      this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::hostileTo));
      this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, MultiverseFighter.class, 10, true, false, this::hostileTo));
   }

   // ---- identity -------------------------------------------------------

   @Nullable
   public BudgetHeroSpec spec() {
      List<BudgetHeroSpec> roster = BudgetHeroes.roster();
      int index = this.entityData.get(HERO);
      return index >= 0 && index < roster.size() ? roster.get(index) : null;
   }

   @Nullable
   public BudgetFaction faction() {
      int index = this.entityData.get(FACTION);
      BudgetFaction[] values = BudgetFaction.values();
      return index >= 0 && index < values.length ? values[index] : null;
   }

   @Nullable
   public MultiverseBoss boss() {
      return MultiverseBoss.byIndex(this.entityData.get(BOSS));
   }

   public boolean isBoss() {
      return this.boss() != null;
   }

   public int session() {
      return this.session;
   }

   public void setOwner(@Nullable UUID owner) {
      this.owner = owner;
   }

   @Override
   public float getScale() {
      return this.entityData.get(SCALE);
   }

   public void setBarName(Component name) {
      if (this.bar != null) {
         this.bar.setName(name);
      }
   }

   // ---- relations (FIVE FACTION WAR SYSTEM) ------------------------------

   @Nullable
   static BudgetFaction factionOf(Player player) {
      return player instanceof HeroPlayer hero && hero.getHeroId() != null ? BudgetFaction.ofHeroKey(hero.getHeroId().key()) : null;
   }

   private boolean sameSide(MultiverseFighter other) {
      if (this.isBoss() || other.isBoss()) {
         return this.isBoss() && other.isBoss() || this.getUUID().equals(other.owner) || other.getUUID().equals(this.owner)
            || this.owner != null && this.owner.equals(other.owner);
      }

      if (this.owner != null || other.owner != null) {
         return this.owner != null && this.owner.equals(other.owner);
      }

      BudgetFaction mine = this.faction();
      return mine != null && mine.relationTo(other.faction()) == BudgetFaction.Relation.ALLY;
   }

   public boolean hostileTo(LivingEntity other) {
      if (other == this || !other.isAlive()) {
         return false;
      }

      if (other instanceof Player player) {
         if (player.isCreative() || player.isSpectator()) {
            return false;
         }

         if (this.isBoss() || this.owner != null) {
            return true;
         }

         BudgetFaction mine = this.faction();
         return mine == null || mine.hostileTo(factionOf(player));
      }

      if (other instanceof MultiverseFighter fighter) {
         // Bosses hunt players; fighters do not gang up on bosses unless hit (HurtByTargetGoal).
         if (this.isBoss() || fighter.isBoss() || this.owner != null || fighter.owner != null) {
            return false;
         }

         BudgetFaction mine = this.faction();
         return mine != null && mine.hostileTo(fighter.faction());
      }

      return false;
   }

   /** BudgetMoves hook: an NPC's area hits skip its own side. */
   public static boolean alliedCaster(LivingEntity caster, LivingEntity target) {
      if (!(caster instanceof MultiverseFighter fighter)) {
         return false;
      }

      if (target instanceof MultiverseFighter other) {
         return fighter.sameSide(other);
      }

      if (target instanceof Player player && !fighter.isBoss() && fighter.owner == null) {
         BudgetFaction mine = fighter.faction();
         return mine != null && mine.relationTo(factionOf(player)) == BudgetFaction.Relation.ALLY;
      }

      return false;
   }

   @Override
   public boolean canAttack(LivingEntity target) {
      return super.canAttack(target) && !alliedCaster(this, target);
   }

   // ---- combat ---------------------------------------------------------

   @Override
   protected void customServerAiStep() {
      super.customServerAiStep();
      if (this.bar != null) {
         this.bar.setProgress(this.getHealth() / this.getMaxHealth());
      }

      MultiverseBoss boss = this.boss();
      if (boss != null) {
         this.tickBoss(boss);
      }

      if (this.chargeTicks > 0 || this.tickCount % CHECK_EVERY != 0) {
         return;
      }

      LivingEntity target = this.getTarget();
      BudgetHeroSpec spec = this.spec();
      if (spec == null || target == null || !target.isAlive()) {
         return;
      }

      double distance = this.distanceTo(target);
      long now = this.level().getGameTime();
      BudgetMove[] moves = spec.moves();
      for (int i = 0; i < moves.length && i < this.readyAt.length; i++) {
         BudgetMove move = moves[i];
         if (now < this.readyAt[i] || !this.wants(move, target, distance)) {
            continue;
         }

         this.face(target);
         BudgetMoves.cast(this, move, this.damageScale);
         this.readyAt[i] = now + Math.max(10L, (long)(move.cooldown() * this.cooldownScale));
         break;
      }
   }

   private boolean wants(BudgetMove move, LivingEntity target, double distance) {
      BudgetMoveType type = move.type();
      return switch (type) {
         case A -> distance < 3.5;
         case B -> distance < 20.0 && this.hasLineOfSight(target);
         case C -> distance > 6.0 && distance < 18.0;
         case D -> distance < 16.0 && move.buff().effect() != null && !this.hasEffect(move.buff().effect());
         case E -> move.shape() == BudgetShape.RING ? distance < 5.0 : distance < 28.0 && this.hasLineOfSight(target);
      };
   }

   private void face(LivingEntity target) {
      this.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
      this.yBodyRot = this.getYRot();
      this.yHeadRot = this.getYRot();
   }

   // ---- the shared boss controller ---------------------------------------

   private void tickBoss(MultiverseBoss boss) {
      ServerLevel level = (ServerLevel)this.level();
      if (this.counterTicks > 0) {
         this.counterTicks--;
         if (this.counterTicks % 5 == 0) {
            BudgetMoves.ringParticles(level, this.position().add(0.0, 1.0, 0.0), 1.2 * this.getScale(), 0x8080FF, 12);
         }
      }

      if (!this.enraged && this.getHealth() < this.getMaxHealth() * 0.5F
         && (boss.mechanic() == MultiverseBoss.Mechanic.TITAN || boss.mechanic() == MultiverseBoss.Mechanic.ALL)) {
         this.enraged = true;
         this.entityData.set(SCALE, this.getScale() * 1.5F);
         this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 600, 1, false, false));
         this.specialScale *= 0.7F;
         level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY(0.5), this.getZ(), 2, 1.0, 1.0, 1.0, 0.0);
         this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.6F);
         this.announce(Component.translatable("multiverse.viltrumitecore.enraged", this.getDisplayName()));
      }

      if (this.chargeTicks > 0) {
         this.tickCharge(level);
         return;
      }

      if (--this.specialTimer > 0 || this.getTarget() == null) {
         return;
      }

      this.specialTimer = Math.max(20, (int)(boss.interval() * this.specialScale));
      MultiverseBoss.Mechanic mechanic = boss.mechanic();
      if (mechanic == MultiverseBoss.Mechanic.ALL) {
         MultiverseBoss.Mechanic[] cycle = {MultiverseBoss.Mechanic.SHRINE, MultiverseBoss.Mechanic.ILLUSION, MultiverseBoss.Mechanic.SUMMON,
            MultiverseBoss.Mechanic.ONE_PUNCH, MultiverseBoss.Mechanic.COUNTER, MultiverseBoss.Mechanic.ATOMIC};
         mechanic = cycle[this.specialCycle++ % cycle.length];
      }

      this.special(level, mechanic);
   }

   private void special(ServerLevel level, MultiverseBoss.Mechanic mechanic) {
      LivingEntity target = this.getTarget();
      switch (mechanic) {
         case SHRINE -> {
            BudgetMoves.ringParticles(level, this.position().add(0.0, 0.3, 0.0), 8.0, 0xC00000, 48);
            BudgetMoves.ringParticles(level, this.position().add(0.0, 1.3, 0.0), 6.0, 0xFF2020, 36);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, this.getX(), this.getY(0.5), this.getZ(), 20, 5.0, 0.5, 5.0, 0.0);
            this.playSound(SoundEvents.WITHER_SHOOT, 2.0F, 0.6F);
            BudgetMoves.area(this, this.position(), 8.0, 10.0F * this.damageScale, null);
            this.announce(Component.translatable("multiverse.viltrumitecore.special.shrine"));
         }
         case ILLUSION -> {
            if (target != null) {
               Vec3 behind = target.position().subtract(target.getLookAngle().multiply(2.5, 0.0, 2.5));
               level.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY(0.5), this.getZ(), 40, 0.5, 1.0, 0.5, 0.5);
               this.teleportTo(behind.x, target.getY(), behind.z);
               this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.5F, 0.6F);
            }
            this.summon(level, 2, 6.0F, true);
            this.announce(Component.translatable("multiverse.viltrumitecore.special.illusion"));
         }
         case SUMMON -> {
            this.summon(level, 2, 30.0F, false);
            level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY(), this.getZ(), 30, 2.0, 0.2, 2.0, 0.05);
            this.playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 2.0F, 0.5F);
            this.announce(Component.translatable("multiverse.viltrumitecore.special.summon"));
         }
         case TITAN -> {
            level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.2, this.getZ(), 40, 3.0, 0.2, 3.0, 0.1);
            BudgetMoves.ringParticles(level, this.position().add(0.0, 0.2, 0.0), 6.0, 0xC06030, 40);
            this.playSound(SoundEvents.GENERIC_EXPLODE, 2.0F, 0.5F);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(6.0), e -> e != this && !alliedCaster(this, e))) {
               victim.setDeltaMovement(victim.getDeltaMovement().add(0.0, 0.9, 0.0));
               victim.hurtMarked = true;
            }
            BudgetMoves.area(this, this.position(), 6.0, 8.0F * this.damageScale, null);
            this.announce(Component.translatable("multiverse.viltrumitecore.special.titan"));
         }
         case ONE_PUNCH, ATOMIC -> {
            this.charging = mechanic;
            this.chargeTicks = mechanic == MultiverseBoss.Mechanic.ATOMIC ? 60 : 40;
            this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, this.chargeTicks, 6, false, false));
            this.announce(Component.translatable(mechanic == MultiverseBoss.Mechanic.ATOMIC
               ? "multiverse.viltrumitecore.special.atomic_charge" : "multiverse.viltrumitecore.special.punch_charge"));
            this.playSound(mechanic == MultiverseBoss.Mechanic.ATOMIC ? SoundEvents.BEACON_DEACTIVATE : SoundEvents.BEACON_POWER_SELECT, 2.0F, 0.5F);
         }
         case COUNTER -> {
            this.counterTicks = 50;
            this.playSound(SoundEvents.SHIELD_BLOCK, 2.0F, 0.6F);
            this.announce(Component.translatable("multiverse.viltrumitecore.special.counter"));
         }
         case ALL -> {
         }
      }
   }

   private void tickCharge(ServerLevel level) {
      this.chargeTicks--;
      if (this.charging == MultiverseBoss.Mechanic.ATOMIC) {
         if (this.chargeTicks % 4 == 0) {
            BudgetMoves.ringParticles(level, this.position().add(0.0, 1.0, 0.0), 1.0 + this.chargeTicks * 0.15, 0x9040FF, 24);
         }

         if (this.chargeTicks <= 0) {
            level.explode(this, this.getX(), this.getY(0.5), this.getZ(), 3.0F, Level.ExplosionInteraction.NONE);
            for (int i = 0; i < 6; i++) {
               double angle = Math.PI * 2.0 * i / 6.0;
               level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX() + Math.cos(angle) * 5.0, this.getY(0.5), this.getZ() + Math.sin(angle) * 5.0, 1, 0.0, 0.0, 0.0, 0.0);
            }
            BudgetMoves.area(this, this.position(), 10.0, 16.0F * this.damageScale, null);
            this.announce(Component.translatable("multiverse.viltrumitecore.special.atomic"));
         }
      } else {
         if (this.chargeTicks % 4 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY(0.7), this.getZ(), 6, 0.4, 0.6, 0.4, 0.02);
         }

         LivingEntity target = this.getTarget();
         if (this.chargeTicks <= 0 && target != null && target.isAlive()) {
            this.face(target);
            BudgetMoves.cast(this, new BudgetMove(BudgetMoveType.E, "serious_punch", 30.0F, 10, 0xFFFFA0, BudgetShape.BEAM,
               dev.baranhan.viltrumitecore.hero.budget.BudgetBuff.NONE), this.damageScale);
            this.announce(Component.translatable("multiverse.viltrumitecore.special.punch"));
         }
      }

      if (this.chargeTicks <= 0) {
         this.charging = null;
      }
   }

   private void summon(ServerLevel level, int count, float health, boolean decoy) {
      long alive = level.getEntitiesOfClass(MultiverseFighter.class, this.getBoundingBox().inflate(48.0), f -> this.getUUID().equals(f.owner)).size();
      List<BudgetHeroSpec> roster = BudgetHeroes.roster();
      BudgetHeroSpec own = this.spec();
      for (int i = 0; i < count && alive + i < 4; i++) {
         BudgetHeroSpec spec = decoy && own != null ? own : roster.get(this.random.nextInt(roster.size()));
         Vec3 pos = this.position().add((this.random.nextDouble() - 0.5) * 6.0, 0.5, (this.random.nextDouble() - 0.5) * 6.0);
         MultiverseFighter minion = spawn(level, pos, spec, this.session, null, 1.0F, 1.0F, 1.0F);
         if (minion != null) {
            minion.owner = this.getUUID();
            minion.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
            minion.setHealth(health);
            if (decoy) {
               minion.damageScale = 0.1F;
               minion.setCustomName(this.getCustomName());
            }
            minion.setTarget(this.getTarget());
         }
      }
   }

   private void announce(Component text) {
      for (ServerPlayer player : ((ServerLevel)this.level()).players()) {
         if (player.distanceToSqr(this) < 64.0 * 64.0) {
            player.displayClientMessage(Component.empty().append(text).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
         }
      }
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (this.counterTicks > 0 && source.getEntity() instanceof LivingEntity attacker && attacker != this) {
         // FULL COUNTER: the hit is returned with interest, the boss takes nothing.
         ServerLevel level = (ServerLevel)this.level();
         level.sendParticles(ParticleTypes.ENCHANTED_HIT, this.getX(), this.getY(0.6), this.getZ(), 20, 0.5, 0.5, 0.5, 0.3);
         level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 1.6F);
         attacker.hurt(this.damageSources().mobAttack(this), Math.min(40.0F, amount * 1.5F));
         return false;
      }

      return super.hurt(source, amount);
   }

   // ---- boss bar and lifecycle -------------------------------------------

   @Override
   public void startSeenByPlayer(ServerPlayer player) {
      super.startSeenByPlayer(player);
      if (this.bar != null) {
         this.bar.addPlayer(player);
      }
   }

   @Override
   public void stopSeenByPlayer(ServerPlayer player) {
      super.stopSeenByPlayer(player);
      if (this.bar != null) {
         this.bar.removePlayer(player);
      }
   }

   @Override
   public void remove(RemovalReason reason) {
      if (this.bar != null) {
         this.bar.removeAllPlayers();
      }

      super.remove(reason);
   }

   @Override
   public boolean removeWhenFarAway(double distance) {
      return false;
   }

   @Override
   protected boolean shouldDespawnInPeaceful() {
      return false;
   }
}
