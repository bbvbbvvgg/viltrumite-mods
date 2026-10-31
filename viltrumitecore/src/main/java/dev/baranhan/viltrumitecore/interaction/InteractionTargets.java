package dev.baranhan.viltrumitecore.interaction;

import dev.baranhan.viltrumitecore.entity.SummonOwners;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.ServerRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Hero-neutral target rules of the interaction side effects (shockwaves, blasts): PvP and vanilla teams for players,
 * the owner's rules for hero summons ({@link SummonOwners}), tamed pets of the participants spared. Server side.
 */
public final class InteractionTargets {
   private InteractionTargets() {
   }

   /** The player answering for {@code e}: itself, the owner of a hero summon, or null. */
   @Nullable
   public static ServerPlayer ownerOf(@Nullable Entity e) {
      if (e instanceof ServerPlayer p) {
         return p;
      }
      return SummonOwners.ownerOf(e);
   }

   /** May two players fight each other right now (server PvP + vanilla team friendly fire)? */
   public static boolean rivals(ServerPlayer a, ServerPlayer b) {
      if (a == b || a.isSpectator() || b.isSpectator() || b.isCreative()) {
         return false;
      }
      return ServerRules.pvp(a) && a.canHarmPlayer(b) && !a.isAlliedTo(b);
   }

   /** Is {@code e} protected from a side effect caused by the {@code protectedOnes}: one of them, their summon, pet or team mate. */
   public static boolean spared(LivingEntity e, ServerPlayer... protectedOnes) {
      if (!e.isAlive() || e instanceof ArmorStand) {
         return true;
      }
      ServerPlayer owner = ownerOf(e);
      for (ServerPlayer p : protectedOnes) {
         if (p == null) {
            continue;
         }
         if (e == p || owner == p || e.isAlliedTo(p)) {
            return true;
         }
         if (e instanceof OwnableEntity pet && p.getUUID().equals(pet.getOwnerUUID())) {
            return true;
         }
      }
      return false;
   }

   /** May {@code striker}'s side effect hurt {@code e}? Players only as PvP rivals, summons only when their owner is one. */
   public static boolean mayHurt(ServerPlayer striker, LivingEntity e) {
      if (spared(e, striker)) {
         return false;
      }
      if (e instanceof Player other) {
         return other instanceof ServerPlayer sp && rivals(striker, sp);
      }
      if (SummonOwners.isSummon(e)) {
         ServerPlayer owner = SummonOwners.ownerOf(e);
         return owner != null && rivals(striker, owner);
      }
      return true;
   }

   /** Living entities around a point, nearest first. */
   public static List<LivingEntity> around(Entity source, Vec3 at, double radius) {
      List<LivingEntity> out = new ArrayList<>();
      for (LivingEntity e : source.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius))) {
         if (e.getBoundingBox().distanceToSqr(at) <= radius * radius) {
            out.add(e);
         }
      }
      out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(at)));
      return out;
   }

   /** Push honouring hero control rules, anchors and knockback resistance. False when the target refused. */
   public static boolean push(LivingEntity target, Vec3 velocity) {
      if (!HeroRegistry.allowsImpulse(target)) {
         return false;
      }
      double resist = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
      Vec3 v = velocity.scale(Math.max(0.0, 1.0 - resist));
      target.setDeltaMovement(target.getDeltaMovement().scale(0.2).add(v));
      target.hasImpulse = true;
      target.hurtMarked = true;
      return true;
   }

   /** Horizontal unit vector from {@code from} to {@code to}; {@code fallback} when they overlap. */
   public static Vec3 away(Vec3 from, Vec3 to, Vec3 fallback) {
      Vec3 d = new Vec3(to.x - from.x, 0.0, to.z - from.z);
      if (d.lengthSqr() < 1.0E-4) {
         Vec3 f = new Vec3(fallback.x, 0.0, fallback.z);
         return f.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : f.normalize();
      }
      return d.normalize();
   }

   /** The point a raised guard occupies: in front of the chest along the look. */
   public static Vec3 guardPoint(LivingEntity defender) {
      Vec3 look = defender.getLookAngle();
      Vec3 flat = new Vec3(look.x, 0.0, look.z);
      flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
      return defender.getEyePosition().add(flat.scale(0.7)).add(0, -0.35, 0);
   }
}
