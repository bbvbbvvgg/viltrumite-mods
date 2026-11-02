package dev.baranhan.viltrumitecore.hero.ghostrider;

import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.HellfireS2CPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Hellfire: the Ghost Rider's own fire. Not vanilla fire (no fire ticks, no blocks lit, no vanilla burning overlay):
 * a server burn ledger deals {@link GhostRiderRules#BURN_DAMAGE} every {@link GhostRiderRules#BURN_INTERVAL} ticks
 * through the {@code viltrumitecore:hellfire} damage type (not tagged as fire, so fire-immune bodies burn too), and the
 * clients draw the flames from {@link HellfireS2CPacket}. Water puts it out. The kill is credited to the Rider.
 */
public final class Hellfire {
   public static final ResourceKey<DamageType> HELLFIRE = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("viltrumitecore", "hellfire"));
   public static final ResourceKey<DamageType> PENANCE = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("viltrumitecore", "penance"));

   private static final class Burn {
      @Nullable UUID attacker;
      int left;
      int age;
   }

   private static final Map<LivingEntity, Burn> BURNS = new WeakHashMap<>();

   private Hellfire() {
   }

   public static DamageSource source(ServerLevel level, ResourceKey<DamageType> type, @Nullable Entity attacker) {
      return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), attacker);
   }

   /** Sets the body alight for {@code ticks} (refreshes, never stacks). The Rider and his bike never burn. */
   public static void ignite(@Nullable ServerPlayer attacker, LivingEntity target, int ticks) {
      if (!(target.level() instanceof ServerLevel) || !target.isAlive() || ticks <= 0 || target.isInWaterOrBubble()) {
         return;
      }
      if (target instanceof ServerPlayer sp && GhostRiderState.of(sp) != null && GhostRiderState.of(sp).rider) {
         return;
      }
      Burn burn = BURNS.get(target);
      boolean fresh = burn == null;
      if (fresh) {
         burn = new Burn();
         BURNS.put(target, burn);
      }
      int before = burn.left;
      burn.left = Math.min(GhostRiderRules.BURN_MAX_TICKS, Math.max(burn.left, ticks));
      if (attacker != null) {
         burn.attacker = attacker.getUUID();
      }
      if (fresh || burn.left - before >= 10) {
         CoreMessages.sendToTrackingAndSelf(new HellfireS2CPacket(target.getId(), burn.left), target);
      }
      if (fresh) {
         target.level().playSound(null, target.getX(), target.getY(), target.getZ(), GhostRiderSounds.HELLFIRE_IGNITE.get(),
            net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 0.9F + target.getRandom().nextFloat() * 0.2F);
      }
   }

   public static boolean burning(LivingEntity target) {
      Burn burn = BURNS.get(target);
      return burn != null && burn.left > 0;
   }

   public static void extinguish(LivingEntity target) {
      if (BURNS.remove(target) != null && target.isAlive()) {
         CoreMessages.sendToTrackingAndSelf(new HellfireS2CPacket(target.getId(), 0), target);
      }
   }

   /** Server tick: the burn damage, water, expiry. */
   public static void tick() {
      if (BURNS.isEmpty()) {
         return;
      }
      List<LivingEntity> ended = new ArrayList<>();
      List<Object[]> hits = new ArrayList<>();
      Iterator<Map.Entry<LivingEntity, Burn>> it = BURNS.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<LivingEntity, Burn> entry = it.next();
         LivingEntity target = entry.getKey();
         Burn burn = entry.getValue();
         if (target == null || target.isRemoved() || !target.isAlive()) {
            it.remove();
            continue;
         }
         if (target.isInWaterOrBubble()) {
            ended.add(target);
            target.level().playSound(null, target.getX(), target.getY(), target.getZ(), net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
               net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.2F);
            continue;
         }
         burn.age++;
         burn.left--;
         if (burn.age % GhostRiderRules.BURN_INTERVAL == 0) {
            hits.add(new Object[]{target, burn.attacker});
         }
         if (burn.left <= 0) {
            ended.add(target);
         }
      }
      for (Object[] hit : hits) {
         LivingEntity target = (LivingEntity)hit[0];
         if (!(target.level() instanceof ServerLevel level) || !target.isAlive()) {
            continue;
         }
         Entity attacker = hit[1] == null ? null : level.getPlayerByUUID((UUID)hit[1]);
         Vec3 keep = target.getDeltaMovement();
         target.invulnerableTime = 0;
         target.hurt(source(level, HELLFIRE, attacker), GhostRiderRules.BURN_DAMAGE);
         target.setDeltaMovement(keep); // a burn tick is no blow: no knock-back
      }
      for (LivingEntity target : ended) {
         extinguish(target);
      }
   }

   /** Server stop / level unload safety. */
   public static void clear() {
      BURNS.clear();
   }
}
