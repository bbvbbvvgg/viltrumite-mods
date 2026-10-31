package dev.baranhan.viltrumitecore.hero.captain;

import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.ServerRules;
import dev.baranhan.viltrumitecore.hero.captain.assemble.AvengersTeam;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Target picking and the shared hit / stun helpers of Steve and his team (server). */
public final class CaptainTargets {
   private CaptainTargets() {
   }

   /** Legal target of Steve's (or his team's) hits: never Steve, his team, his pets; players only with PvP. */
   public static boolean mayHit(@Nullable ServerPlayer owner, LivingEntity e) {
      if (!e.isAlive() || e == owner || e instanceof ArmorStand && ((ArmorStand)e).isMarker()) {
         return false;
      }
      if (owner != null && AvengersTeam.ally(owner, e)) {
         return false;
      }
      if (e instanceof Player other) {
         return owner != null && ServerRules.pvp(owner) && !other.isCreative() && !other.isSpectator();
      }
      if (dev.baranhan.viltrumitecore.entity.SummonOwners.isSummon(e)) {
         // Another hero's summon (an Iron Legion suit, a shadow): the PvP rule of its owner; no owner online: spared.
         ServerPlayer summoner = dev.baranhan.viltrumitecore.entity.SummonOwners.ownerOf(e);
         return summoner != null && summoner != owner && mayHit(owner, summoner);
      }
      return true;
   }

   public static boolean ownedBy(Entity e, UUID owner) {
      return e instanceof OwnableEntity pet && owner.equals(pet.getOwnerUUID());
   }

   /** Living targets in a frontal arc in front of the player. */
   public static List<LivingEntity> arc(ServerPlayer player, double reach, double degrees, Predicate<LivingEntity> ok) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      Vec3 flat = new Vec3(look.x, 0, look.z);
      flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
      double cos = Math.cos(Math.toRadians(degrees / 2));
      List<LivingEntity> out = new ArrayList<>();
      for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach + 1.0), ok)) {
         Vec3 c = e.getBoundingBox().getCenter();
         Vec3 d = c.subtract(eye);
         double dist = Math.sqrt(e.getBoundingBox().distanceToSqr(eye));
         if (dist > reach) {
            continue;
         }
         Vec3 h = new Vec3(d.x, 0, d.z);
         if (h.lengthSqr() > 0.04 && h.normalize().dot(flat) < cos) {
            continue;
         }
         out.add(e);
      }
      out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
      return out;
   }

   public static List<LivingEntity> around(Entity source, Vec3 at, double radius, Predicate<LivingEntity> ok) {
      List<LivingEntity> out = new ArrayList<>();
      for (LivingEntity e : source.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius), ok)) {
         if (e.getBoundingBox().distanceToSqr(at) <= radius * radius) {
            out.add(e);
         }
      }
      return out;
   }

   /** The living entity under the crosshair within range (parts resolve to the parent), or null. */
   @Nullable
   public static LivingEntity aimed(ServerPlayer player, double range, Predicate<LivingEntity> ok) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      LivingEntity best = null;
      double bestScore = Double.MAX_VALUE;
      for (Entity raw : player.level().getEntities(player, player.getBoundingBox().expandTowards(look.scale(range)).inflate(2.0))) {
         Entity e = raw instanceof EnderDragonPart part ? part.parentMob : raw;
         if (!(e instanceof LivingEntity living) || !ok.test(living)) {
            continue;
         }
         Vec3 c = living.getBoundingBox().getCenter();
         Vec3 d = c.subtract(eye);
         double along = d.dot(look);
         if (along <= 0 || along > range) {
            continue;
         }
         double off = d.subtract(look.scale(along)).length();
         double allow = 0.6 + living.getBbWidth() * 0.5 + along * 0.06;
         if (off > allow || !visible(player.level(), eye, c)) {
            continue;
         }
         double score = off / allow + along / range * 0.4;
         if (score < bestScore) {
            bestScore = score;
            best = living;
         }
      }
      return best;
   }

   public static boolean visible(net.minecraft.world.level.Level level, Vec3 from, Vec3 to) {
      return level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
         net.minecraft.world.level.ClipContext.Fluid.NONE, null)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
   }

   /** One hit through the attacker's damage source; ignores i-frames. True when it landed. */
   public static boolean strike(LivingEntity attacker, LivingEntity target, float damage) {
      target.invulnerableTime = 0;
      net.minecraft.world.damagesource.DamageSource source = attacker instanceof Player p
         ? attacker.damageSources().playerAttack(p) : attacker.damageSources().mobAttack(attacker);
      return target.hurt(source, damage);
   }

   /** Push a target (honours hero control rules and anchors). */
   public static void push(LivingEntity target, Vec3 velocity) {
      if (!HeroRegistry.allowsExternalControl(target, ControlKind.IMPULSE) || HeroDamage.isAnchored(target)) {
         return;
      }
      double resist = target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
      Vec3 v = velocity.scale(Math.max(0.0, 1.0 - resist));
      target.setDeltaMovement(target.getDeltaMovement().scale(0.3).add(v));
      target.hurtMarked = true;
   }

   /** Stagger through the shared freeze control with an expiry (falls through silently when refused). */
   public static boolean stun(LivingEntity caster, LivingEntity target, int ticks) {
      if (!(target.level() instanceof ServerLevel level) || !target.isAlive() || ticks <= 0) {
         return false;
      }
      if (!HeroRegistry.allowsExternalControl(target, ControlKind.FREEZE) || HeroDamage.isAnchored(target)) {
         return false;
      }
      return ControlManager.get(level).tryAcquire(target, caster.getUUID(), UUID.randomUUID(), ControlKind.FREEZE, level.getGameTime() + ticks);
   }
}
