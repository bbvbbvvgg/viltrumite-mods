package dev.baranhan.viltrumitecore.hero.redhulk;

import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.ServerRules;
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
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Target picking and the shared hit / push / stun helpers of the Red Hulk (server). */
public final class RedHulkTargets {
   private RedHulkTargets() {
   }

   /** Legal target: never himself or his pets; players only with PvP; another hero's summon by its owner's rule. */
   public static boolean mayHit(@Nullable ServerPlayer owner, LivingEntity e) {
      if (!e.isAlive() || e == owner || e instanceof ArmorStand stand && stand.isMarker()) {
         return false;
      }
      if (owner != null && e instanceof OwnableEntity pet && owner.getUUID().equals(pet.getOwnerUUID())) {
         return false;
      }
      if (e instanceof Player other) {
         return owner != null && ServerRules.pvp(owner) && !other.isCreative() && !other.isSpectator();
      }
      if (dev.baranhan.viltrumitecore.entity.SummonOwners.isSummon(e)) {
         ServerPlayer summoner = dev.baranhan.viltrumitecore.entity.SummonOwners.ownerOf(e);
         return summoner != null && summoner != owner && mayHit(owner, summoner);
      }
      return true;
   }

   /** The passive heat aura only scorches hostiles (and players with PvP): villagers and animals are spared. */
   public static boolean auraMayHit(ServerPlayer owner, LivingEntity e) {
      return mayHit(owner, e) && (e instanceof Enemy || e instanceof Player);
   }

   public static List<LivingEntity> arc(ServerPlayer player, double reach, double degrees, Predicate<LivingEntity> ok) {
      Vec3 eye = player.getEyePosition();
      Vec3 flat = flat(player.getLookAngle());
      double cos = Math.cos(Math.toRadians(degrees / 2));
      List<LivingEntity> out = new ArrayList<>();
      for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach + 1.0), ok)) {
         Vec3 d = e.getBoundingBox().getCenter().subtract(eye);
         if (Math.sqrt(e.getBoundingBox().distanceToSqr(eye)) > reach) {
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
      out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(at)));
      return out;
   }

   /** Living entities within {@code width} of the segment from -> to (sorted along it). */
   public static List<LivingEntity> along(Entity source, Vec3 from, Vec3 to, double width, Predicate<LivingEntity> ok) {
      Vec3 axis = to.subtract(from);
      double len = axis.length();
      List<LivingEntity> out = new ArrayList<>();
      if (len < 1.0E-3) {
         return out;
      }
      Vec3 dir = axis.scale(1.0 / len);
      AABB box = new AABB(from, to).inflate(width + 1.0);
      for (LivingEntity e : source.level().getEntitiesOfClass(LivingEntity.class, box, ok)) {
         Vec3 c = e.getBoundingBox().getCenter();
         double t = c.subtract(from).dot(dir);
         if (t < 0 || t > len) {
            continue;
         }
         double off = c.subtract(from.add(dir.scale(t))).length();
         if (off <= width + e.getBbWidth() * 0.5) {
            out.add(e);
         }
      }
      out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
      return out;
   }

   public static boolean visible(net.minecraft.world.level.Level level, Vec3 from, Vec3 to) {
      return level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
         net.minecraft.world.level.ClipContext.Fluid.NONE, null)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
   }

   /** Where a ray from {@code from} towards {@code to} first meets a block (or {@code to}). */
   public static Vec3 clip(ServerPlayer player, Vec3 from, Vec3 to) {
      var hit = player.level().clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
         net.minecraft.world.level.ClipContext.Fluid.NONE, player));
      return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? to : hit.getLocation();
   }

   /** One hit through the player's melee source (heat / fury scale it in modifyOutgoingDamage); ignores i-frames. */
   public static boolean strike(ServerPlayer attacker, LivingEntity target, float damage) {
      target.invulnerableTime = 0;
      return target.hurt(attacker.damageSources().playerAttack(attacker), damage);
   }

   /** Push a target (honours hero control rules, anchors and knockback resistance). */
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

   public static Vec3 flat(Vec3 v) {
      Vec3 h = new Vec3(v.x, 0, v.z);
      return h.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : h.normalize();
   }

   /** Outward horizontal direction from {@code from} to the entity (any direction when on top). */
   public static Vec3 outward(Vec3 from, Entity e) {
      Vec3 d = e.position().subtract(from);
      return flat(d.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : d);
   }

   public static Vec3 center(Entity e) {
      return e.getBoundingBox().getCenter();
   }
}
