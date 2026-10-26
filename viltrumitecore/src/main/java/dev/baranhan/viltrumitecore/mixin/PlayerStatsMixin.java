package dev.baranhan.viltrumitecore.mixin;

import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import dev.baranhan.viltrumitecore.entity.ViltrumiteFakePlayer;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import dev.baranhan.viltrumitecore.util.ViltrumiteStatHolder;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {Player.class},
   priority = 1
)
public abstract class PlayerStatsMixin implements ViltrumiteStatHolder {
   @Unique
   private static final EntityDataAccessor<Float> STAT_BASE_DAMAGE = SynchedEntityData.defineId(Player.class, EntityDataSerializers.FLOAT);
   @Unique
   private static final EntityDataAccessor<Float> STAT_DAMAGE_IGNORE_THRESHOLD = SynchedEntityData.defineId(Player.class, EntityDataSerializers.FLOAT);
   @Unique
   private static final EntityDataAccessor<Float> STAT_DAMAGE_REDUCTION = SynchedEntityData.defineId(Player.class, EntityDataSerializers.FLOAT);
   @Unique
   private static final EntityDataAccessor<Float> STAT_HEAL_FACTOR = SynchedEntityData.defineId(Player.class, EntityDataSerializers.FLOAT);

   @Inject(
      method = {"defineSynchedData"},
      at = {@At("TAIL")}
   )
   protected void onInitStatTracker(CallbackInfo ci) {
      Player player = (Player)(Object)this;
      player.getEntityData().define(STAT_BASE_DAMAGE, 19.0F);
      player.getEntityData().define(STAT_DAMAGE_IGNORE_THRESHOLD, ViltrumiteCoreConfig.INSTANCE.damageIgnoreThreshold);
      player.getEntityData().define(STAT_DAMAGE_REDUCTION, ViltrumiteCoreConfig.INSTANCE.damageReductionPercent);
      player.getEntityData().define(STAT_HEAL_FACTOR, 1.0F);
   }

   /** Hero override of the chassis numbers; world-event fake players keep their own stats. */
   @Unique
   private dev.baranhan.viltrumitecore.hero.HeroChassis viltrumitecore$chassis() {
      Player player = (Player)(Object)this;
      return player instanceof ViltrumiteFakePlayer ? null : dev.baranhan.viltrumitecore.hero.HeroRegistry.get(player).chassisStats(player);
   }

   @Override
   public float getBaseDamage() {
      dev.baranhan.viltrumitecore.hero.HeroChassis chassis = this.viltrumitecore$chassis();
      if (chassis != null) {
         return chassis.baseDamage();
      }

      return (Float)((Player)(Object)this).getEntityData().get(STAT_BASE_DAMAGE);
   }

   @Override
   public void setBaseDamage(float damage) {
      ((Player)(Object)this).getEntityData().set(STAT_BASE_DAMAGE, damage);
   }

   @Override
   public float getDamageIgnoreThreshold() {
      dev.baranhan.viltrumitecore.hero.HeroChassis chassis = this.viltrumitecore$chassis();
      if (chassis != null) {
         return chassis.damageIgnoreThreshold();
      }

      return (Float)((Player)(Object)this).getEntityData().get(STAT_DAMAGE_IGNORE_THRESHOLD);
   }

   @Override
   public void setDamageIgnoreThreshold(float threshold) {
      ((Player)(Object)this).getEntityData().set(STAT_DAMAGE_IGNORE_THRESHOLD, threshold);
   }

   @Override
   public float getDamageReduction() {
      dev.baranhan.viltrumitecore.hero.HeroChassis chassis = this.viltrumitecore$chassis();
      if (chassis != null) {
         return chassis.damageReduction();
      }

      return (Float)((Player)(Object)this).getEntityData().get(STAT_DAMAGE_REDUCTION);
   }

   @Override
   public void setDamageReduction(float reduction) {
      ((Player)(Object)this).getEntityData().set(STAT_DAMAGE_REDUCTION, reduction);
   }

   @Override
   public float getHealFactor() {
      return (Float)((Player)(Object)this).getEntityData().get(STAT_HEAL_FACTOR);
   }

   @Override
   public void setHealFactor(float factor) {
      ((Player)(Object)this).getEntityData().set(STAT_HEAL_FACTOR, factor);
   }

   @Inject(
      method = {"hurt"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ignoreWeakDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
      if (((ViltrumiteCorePlayer)(Object)this).isViltrumite()) {
         if ((Object)this instanceof ViltrumiteFakePlayer thisFakePlayer
            && source.getEntity() instanceof ViltrumiteFakePlayer
            && thisFakePlayer.getTargetMode() == ViltrumiteFakePlayer.TargetMode.AGGRESSIVE_NO_ALLIES) {
            cir.setReturnValue(false);
            return;
         }

         if (!source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC_KILL)) {
            float reducedDamage = amount * (1.0F - this.getDamageReduction() * 0.01F);
            if (reducedDamage < this.getDamageIgnoreThreshold()) {
               cir.setReturnValue(false);
            }
         }
      }
   }

   @ModifyVariable(
      method = {"hurt"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private float reduceIncomingDamage(float amount, DamageSource source) {
      if (!((ViltrumiteCorePlayer)(Object)this).isViltrumite()) {
         return amount;
      } else {
         return !source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC_KILL)
            ? amount * (1.0F - this.getDamageReduction() * 0.01F)
            : amount;
      }
   }

   @ModifyVariable(
      method = {"causeFoodExhaustion"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private float reduceExhaustion(float exhaustion) {
      Player player = (Player)(Object)this;
      // Hero contract: an active Lion's Heart spends no hunger (spec 6.2).
      if (dev.baranhan.viltrumitecore.hero.HeroRegistry.get(player).preventsExhaustion(player)) {
         return 0.0F;
      }

      if (!((ViltrumiteCorePlayer)player).isViltrumite()) {
         return exhaustion;
      } else {
         return player.getFoodData().getFoodLevel() >= 20 ? exhaustion : exhaustion * 0.005F;
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At("TAIL")}
   )
   private void onTick(CallbackInfo ci) {
      Player player = (Player)(Object)this;
      if (((ViltrumiteCorePlayer)player).isViltrumite()) {
         // Server only (LegacyKit.forcesMayfly): the client gets mayfly from the abilities packet.
         if (dev.baranhan.viltrumitecore.hero.LegacyKit.forcesMayfly(player instanceof ServerPlayer, true, player.getAbilities().mayfly)) {
            player.getAbilities().mayfly = true;
            ((ServerPlayer)player).onUpdateAbilities();
         }

         if (!player.level().isClientSide() && player.getHealth() < player.getMaxHealth() && player.tickCount % 40 == 0
            && dev.baranhan.viltrumitecore.hero.HeroRegistry.get(player).usesLegacyRegen(player)) {
            player.heal(this.getHealFactor());
         }
      }
   }

   @Inject(
      method = {"addAdditionalSaveData"},
      at = {@At("TAIL")}
   )
   private void onWriteViltrumiteStatsNbt(CompoundTag nbt, CallbackInfo ci) {
      Player player = (Player)(Object)this;
      CompoundTag stats = new CompoundTag();
      stats.putFloat("BaseDamage", this.getBaseDamage());
      stats.putFloat("DamageIgnoreThreshold", this.getDamageIgnoreThreshold());
      stats.putFloat("DamageReduction", this.getDamageReduction());
      stats.putFloat("HealFactor", this.getHealFactor());
      stats.putFloat("MaxFlightSpeed", ((ViltrumiteFlightPlayer)(Object)this).getMaxFlightSpeed());
      stats.putFloat("ThrottleSpeed", ((ViltrumiteFlightPlayer)(Object)this).getThrottleSpeed());
      nbt.put("ViltrumiteStats", stats);
   }

   @Inject(
      method = {"readAdditionalSaveData"},
      at = {@At("TAIL")}
   )
   private void onReadViltrumiteStatsNbt(CompoundTag nbt, CallbackInfo ci) {
      Player player = (Player)(Object)this;
      if (nbt.contains("ViltrumiteStats")) {
         CompoundTag stats = nbt.getCompound("ViltrumiteStats");
         if (stats.contains("BaseDamage")) {
            this.setBaseDamage(stats.getFloat("BaseDamage"));
         }

         if (stats.contains("DamageIgnoreThreshold")) {
            this.setDamageIgnoreThreshold(stats.getFloat("DamageIgnoreThreshold"));
         }

         if (stats.contains("DamageReduction")) {
            this.setDamageReduction(stats.getFloat("DamageReduction"));
         }

         if (stats.contains("HealFactor")) {
            this.setHealFactor(stats.getFloat("HealFactor"));
         }

         if (stats.contains("MaxFlightSpeed")) {
            ((ViltrumiteFlightPlayer)(Object)this).setMaxFlightSpeed(stats.getFloat("MaxFlightSpeed"));
         }

         if (stats.contains("ThrottleSpeed")) {
            ((ViltrumiteFlightPlayer)(Object)this).setThrottleSpeed(stats.getFloat("ThrottleSpeed"));
         }
      }
   }
}
