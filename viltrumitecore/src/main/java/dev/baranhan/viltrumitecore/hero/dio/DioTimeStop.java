package dev.baranhan.viltrumitecore.hero.dio;

import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.ServerRules;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.dio.net.DioFxS2CPacket;
import dev.baranhan.viltrumitecore.hero.dio.net.DioNet;
import dev.baranhan.viltrumitecore.hero.dio.net.DioTimeS2CPacket;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineTrigger;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * DIO's stopped time: a LOCAL field around the cast point, never the whole
 * server. Living victims are shared ControlManager FREEZE records (recoverable
 * flags, anchored, no AI, no actions); things hang in the air with their
 * motion saved; DIO's hits on victims are deferred into the ledger and land at
 * the resume. Players are only stopped where PvP and teams allow harming them.
 */
public final class DioTimeStop {
   private static final Map<UUID, TimeStopField> FIELDS = new HashMap<>();

   private DioTimeStop() {
   }

   @Nullable
   public static TimeStopField fieldOf(UUID caster) {
      TimeStopField field = FIELDS.get(caster);
      return field == null || field.ended ? null : field;
   }

   /** Any field that holds this entity frozen right now. */
   @Nullable
   public static TimeStopField freezing(Entity entity) {
      for (TimeStopField field : FIELDS.values()) {
         if (!field.ended && field.level == entity.level() && field.isFrozen(entity.getUUID())) {
            return field;
         }
      }

      return null;
   }

   /** A field of this level whose sphere contains the point (knives stop inside it). */
   @Nullable
   public static TimeStopField covering(ServerLevel level, Vec3 point) {
      for (TimeStopField field : FIELDS.values()) {
         if (!field.ended && field.level == level && field.age >= 0 && point.distanceToSqr(field.center) <= field.radius * field.radius) {
            return field;
         }
      }

      return null;
   }

   public static TimeStopField start(ServerPlayer caster, Vec3 center, double radius, int duration, boolean scripted) {
      TimeStopField old = FIELDS.get(caster.getUUID());
      if (old != null && !old.ended) {
         end(old, ReleaseReason.INTERRUPTED);
      }

      TimeStopField field = new TimeStopField(caster.getUUID(), caster.getId(), caster.serverLevel(), center, radius, duration, scripted);
      FIELDS.put(caster.getUUID(), field);
      capture(field, caster, field.front());
      sync(field, true);
      return field;
   }

   /** One server tick of the caster's field; ends it when the clock runs out. */
   public static void tick(ServerPlayer caster) {
      TimeStopField field = fieldOf(caster.getUUID());
      if (field == null) {
         return;
      }

      if (field.level != caster.level()) {
         end(field, ReleaseReason.INTERRUPTED);
         return;
      }

      field.age++;
      if (field.age <= 8 || field.age % DioRules.TIME_STOP_RESCAN == 0) {
         capture(field, caster, field.front());
      }

      hold(field);
      if (!field.scripted && !field.resumeAnnounced && field.left() <= DioRules.TIME_STOP_RESUME) {
         field.resumeAnnounced = true;
         HeroSpeech.trigger(caster, HeroLineTrigger.event(DioLines.RESUME_EVENT));
         DioSounds.play(field.level, caster.position(), DioSounds.TIME_RESUME, 1.6F, 1.0F);
      }

      if (field.version != field.sentVersion || field.age % 10 == 0) {
         sync(field, true);
      }

      if (!field.scripted && field.age >= field.duration) {
         end(field, ReleaseReason.EXPIRED);
      }
   }

   /** Early resume on request: jump the clock to the resume window. */
   public static void hurry(ServerPlayer caster) {
      TimeStopField field = fieldOf(caster.getUUID());
      if (field != null && !field.scripted && field.left() > DioRules.TIME_STOP_RESUME) {
         field.age = field.duration - DioRules.TIME_STOP_RESUME - 1;
         sync(field, true);
      }
   }

   private static boolean stoppable(TimeStopField field, ServerPlayer caster, Entity entity) {
      if (entity == caster || !entity.isAlive() || entity.isSpectator() || entity.isPassengerOfSameVehicle(caster) || caster.hasPassenger(entity)) {
         return false;
      }

      if (entity instanceof dev.baranhan.viltrumitecore.hero.dio.DioKnifeEntity) {
         return false;
      }

      if (entity instanceof LivingEntity living) {
         if (living instanceof ArmorStand || living instanceof EnderDragon) {
            return false;
         }

         if (living instanceof Player player) {
            if (player.isCreative() || !ServerRules.pvp(caster) || !caster.canHarmPlayer(player)) {
               return false;
            }

            // Another DIO moving in his own stopped time is not stopped.
            if (player instanceof ServerPlayer other && fieldOf(other.getUUID()) != null) {
               return false;
            }
         }

         return HeroRegistry.allowsExternalControl(living, ControlKind.FREEZE) && !dev.baranhan.viltrumitecore.hero.BodyIsolation.active(living);
      }

      return entity instanceof Projectile || entity instanceof ItemEntity || entity instanceof ExperienceOrb
         || entity instanceof FallingBlockEntity || entity instanceof PrimedTnt;
   }

   private static void capture(TimeStopField field, ServerPlayer caster, double front) {
      ServerLevel level = field.level;
      ControlManager manager = ControlManager.get(level);
      double r2 = front * front;
      AABB box = new AABB(field.center, field.center).inflate(front);
      long expires = level.getGameTime() + field.duration - field.age + 40L;
      for (Entity entity : level.getEntities((Entity)null, box, e -> e.position().distanceToSqr(field.center) <= r2)) {
         if (field.isFrozen(entity.getUUID()) || !stoppable(field, caster, entity)) {
            continue;
         }

         if (entity instanceof LivingEntity living) {
            if (manager.tryAcquireRecoverableFreeze(living, caster.getUUID(), field.effectId, expires)) {
               field.living.add(living.getUUID());
               field.version++;
            }
         } else {
            if (entity instanceof Projectile projectile && projectile.getDeltaMovement().lengthSqr() < 1.0E-4) {
               continue;
            }

            double px = 0, py = 0, pz = 0;
            if (entity instanceof AbstractHurtingProjectile hurting) {
               px = hurting.xPower;
               py = hurting.yPower;
               pz = hurting.zPower;
               hurting.xPower = 0;
               hurting.yPower = 0;
               hurting.zPower = 0;
            }

            field.things.put(entity.getUUID(), new TimeStopField.Thing(entity.getDeltaMovement(), entity.isNoGravity(), px, py, pz));
            if (entity instanceof Projectile projectile) {
               manager.freezeProjectile(projectile, caster.getUUID());
            }

            entity.setNoGravity(true);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.hurtMarked = true;
            field.version++;
         }
      }

      // Keep the freeze records alive for the whole field.
      for (UUID id : field.living) {
         manager.refresh(id, caster.getUUID(), field.effectId, ControlKind.FREEZE, expires);
      }
   }

   /** Things stay put every tick (fireballs accelerate, TNT burns, items drift otherwise). */
   private static void hold(TimeStopField field) {
      for (Map.Entry<UUID, TimeStopField.Thing> entry : field.things.entrySet()) {
         Entity entity = field.level.getEntity(entry.getKey());
         if (entity == null) {
            continue;
         }

         if (entity.getDeltaMovement().lengthSqr() > 0.0) {
            entity.setDeltaMovement(Vec3.ZERO);
            entity.hurtMarked = true;
         }

         if (entity instanceof PrimedTnt tnt) {
            tnt.setFuse(tnt.getFuse() + 1);
         }

         if (entity instanceof AbstractHurtingProjectile hurting) {
            hurting.xPower = 0;
            hurting.yPower = 0;
            hurting.zPower = 0;
         }
      }
   }

   /**
    * A hit DIO lands on one of his frozen victims: deferred into the ledger.
    * Returns false when the target is not frozen by this caster (hit normally).
    */
   public static boolean defer(ServerPlayer caster, LivingEntity target, float damage, Vec3 push) {
      TimeStopField field = fieldOf(caster.getUUID());
      if (field == null || !field.living.contains(target.getUUID())) {
         return false;
      }

      TimeStopField.Ledger entry = field.ledger.computeIfAbsent(target.getUUID(), id -> new TimeStopField.Ledger());
      entry.damage += damage;
      entry.hits++;
      entry.push = entry.push.add(push);
      return true;
   }

   public static boolean frozenBy(ServerPlayer caster, Entity target) {
      TimeStopField field = fieldOf(caster.getUUID());
      return field != null && field.isFrozen(target.getUUID());
   }

   /** Time moves again: release, restore motion, then pay the ledger in one blow. */
   public static void end(TimeStopField field, ReleaseReason reason) {
      if (field.ended) {
         return;
      }

      field.ended = true;
      FIELDS.remove(field.caster, field);
      ServerLevel level = field.level;
      ControlManager manager = ControlManager.get(level);
      for (UUID id : field.living) {
         manager.release(id, field.effectId, reason);
      }

      ControlManager.releaseProjectilesFor(field.caster);
      for (Map.Entry<UUID, TimeStopField.Thing> entry : field.things.entrySet()) {
         Entity entity = level.getEntity(entry.getKey());
         if (entity == null) {
            continue;
         }

         TimeStopField.Thing thing = entry.getValue();
         entity.setNoGravity(thing.noGravity);
         entity.setDeltaMovement(thing.motion);
         entity.hurtMarked = true;
         if (entity instanceof AbstractHurtingProjectile hurting) {
            hurting.xPower = thing.powerX;
            hurting.yPower = thing.powerY;
            hurting.zPower = thing.powerZ;
         }
      }

      Entity casterEntity = level.getEntity(field.caster);
      ServerPlayer caster = casterEntity instanceof ServerPlayer sp ? sp : null;
      List<Map.Entry<UUID, TimeStopField.Ledger>> owed = new ArrayList<>(field.ledger.entrySet());
      for (Map.Entry<UUID, TimeStopField.Ledger> entry : owed) {
         Entity entity = level.getEntity(entry.getKey());
         if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
            continue;
         }

         pay(caster, target, entry.getValue());
      }

      sync(field, false);
      if (caster != null) {
         DioSounds.play(level, caster.position(), DioSounds.TIME_TICK, 0.9F, 0.6F);
      }
   }

   private static void pay(@Nullable ServerPlayer caster, LivingEntity target, TimeStopField.Ledger entry) {
      float amount = DioRules.ledgerPayout(entry.damage, target.getMaxHealth(), target instanceof Player);
      if (amount <= 0.0F) {
         return;
      }

      DamageSource source = caster != null ? caster.damageSources().playerAttack(caster) : target.damageSources().magic();
      target.invulnerableTime = 0;
      boolean hurt = target.hurt(source, amount);
      if (hurt && entry.push.lengthSqr() > 1.0E-4 && HeroRegistry.allowsImpulse(target) && !HeroDamage.isAnchored(target)) {
         Vec3 push = entry.push;
         double strength = Math.min(DioRules.LEDGER_MAX_KNOCKBACK, push.length());
         Vec3 flat = new Vec3(push.x, 0, push.z);
         if (flat.lengthSqr() > 1.0E-4) {
            target.knockback(strength, -flat.x, -flat.z);
         }

         target.setDeltaMovement(target.getDeltaMovement().add(0, Math.min(0.6, 0.12 + strength * 0.12), 0));
         target.hurtMarked = true;
      }

      Vec3 at = target.position().add(0, target.getBbHeight() * 0.6, 0);
      if (caster != null) {
         DioNet.fx(target, DioFxS2CPacket.LEDGER_BURST, at, entry.push, Math.min(4.0F, 1.0F + entry.damage / 10.0F), entry.hits);
      }

      DioSounds.play((ServerLevel)target.level(), at, DioSounds.LEDGER_BURST, 1.2F, 0.9F + target.getRandom().nextFloat() * 0.2F);
   }

   private static void sync(TimeStopField field, boolean active) {
      int[] ids = new int[Math.min(DioTimeS2CPacket.MAX_IDS, field.living.size() + field.things.size())];
      int n = 0;
      for (UUID id : field.living) {
         if (n >= ids.length) {
            break;
         }

         Entity entity = field.level.getEntity(id);
         if (entity != null) {
            ids[n++] = entity.getId();
         }
      }

      for (UUID id : field.things.keySet()) {
         if (n >= ids.length) {
            break;
         }

         Entity entity = field.level.getEntity(id);
         if (entity != null) {
            ids[n++] = entity.getId();
         }
      }

      field.sentVersion = field.version;
      DioTimeS2CPacket packet = new DioTimeS2CPacket(active, field.effectId, field.casterEntityId, field.center.x, field.center.y, field.center.z,
         (float)field.radius, field.duration, field.age, field.scripted, java.util.Arrays.copyOf(ids, n));
      DioNet.near(field.level, field.center, field.radius + 64.0, packet);
   }

   /** Caster lifecycle (death, logout, hero change, dimension change): time moves again at once. */
   public static void cleanupCaster(UUID caster) {
      TimeStopField field = FIELDS.get(caster);
      if (field != null) {
         end(field, ReleaseReason.INTERRUPTED);
      }
   }

   /** True when this player is DIO with a live field (used by hero hooks on both sides via the snapshot). */
   public static boolean active(Player player) {
      if (player.level().isClientSide) {
         return player instanceof HeroPlayer hp && hp.getHeroSnapshot() != null && hp.getHeroSnapshot().heroId() == HeroId.DIO
            && hp.getHeroSnapshot().heroFlag(DioRules.FLAG_TIME_STOP);
      }

      return fieldOf(player.getUUID()) != null;
   }

   /** Server stop: forget every field (ControlManager's recoverable flags restore victims on the next start). */
   public static void clear() {
      FIELDS.clear();
   }
}
