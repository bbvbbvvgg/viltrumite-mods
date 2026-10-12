package dev.baranhan.viltrumitecore.hero.reinhard.blessing;

import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Blessing of the Holy Sword (spec §4.1): a direct melee hit from the front
 * (±60° around the look, horizontal) is parried — no damage, a short parry
 * pose, a spark at the contact point. Projectiles, explosions, hits from
 * behind or the side and divine damage are not parried. No knockback to the
 * attacker: pure defence. Mode-independent.
 */
public final class Parry {
   /** Chest height of the contact point above the lower of the two feet. */
   private static final double CONTACT_HEIGHT = 1.2;
   private static final int SPARKS = 10;

   private Parry() {
   }

   // ---- pure ----

   /** A direct melee hit: the attacker itself dealt it, not a projectile or explosion, not divine. */
   public static boolean isMelee(boolean directIsAttacker, boolean projectile, boolean explosion, boolean divine) {
      return directIsAttacker && !projectile && !explosion && !divine;
   }

   /** Attacker inside the front cone: {@code dot(look, toAttacker) ≥ PARRY_CONE_COS} on normalized horizontal vectors. */
   public static boolean inFront(Vec2 lookXZ, Vec2 toAttackerXZ) {
      double lookLength = Math.sqrt(lookXZ.x * lookXZ.x + lookXZ.y * lookXZ.y);
      double toLength = Math.sqrt(toAttackerXZ.x * toAttackerXZ.x + toAttackerXZ.y * toAttackerXZ.y);
      if (lookLength < 1.0E-6 || toLength < 1.0E-6) {
         // Attacker inside his column (or no horizontal look): no direction to parry from.
         return false;
      }

      double dot = (lookXZ.x * toAttackerXZ.x + lookXZ.y * toAttackerXZ.y) / (lookLength * toLength);
      return dot >= ReinhardRules.PARRY_CONE_COS;
   }

   /** Horizontal look of a yaw in degrees (Minecraft: x = -sin, z = cos). */
   public static Vec2 lookOf(float yawDegrees) {
      float rad = yawDegrees * Mth.DEG_TO_RAD;
      return new Vec2(-Mth.sin(rad), Mth.cos(rad));
   }

   // ---- Minecraft ----

   /** Does this hit get parried. */
   public static boolean parries(ServerPlayer self, DamageSource source, boolean divine) {
      Entity attacker = source.getEntity();
      Entity direct = source.getDirectEntity();
      boolean directIsAttacker = attacker instanceof LivingEntity && attacker != self && direct == attacker;
      boolean projectile = direct instanceof Projectile || source.is(DamageTypeTags.IS_PROJECTILE);
      boolean explosion = source.is(DamageTypeTags.IS_EXPLOSION);
      if (!isMelee(directIsAttacker, projectile, explosion, divine)) {
         return false;
      }

      Vec2 to = new Vec2((float) (attacker.getX() - self.getX()), (float) (attacker.getZ() - self.getZ()));
      return inFront(lookOf(self.getYHeadRot()), to);
   }

   /** Spark at the contact point (between the bodies, chest height), gold-white sparks toward the attacker, sound. */
   public static void effects(ServerPlayer self, Entity attacker) {
      Vec3 contact = new Vec3((self.getX() + attacker.getX()) * 0.5, Math.min(self.getY(), attacker.getY()) + CONTACT_HEIGHT,
         (self.getZ() + attacker.getZ()) * 0.5);
      Vec3 out = new Vec3(attacker.getX() - self.getX(), 0.0, attacker.getZ() - self.getZ());
      out = out.lengthSqr() < 1.0E-6 ? lookVec(self) : out.normalize();
      RandomSource random = self.getRandom();
      float[] ends = new float[SPARKS * 3];
      for (int i = 0; i < SPARKS; i++) {
         double side = (random.nextDouble() - 0.5) * 1.6;
         double reach = 0.5 + random.nextDouble() * 0.7;
         Vec3 end = contact.add(out.scale(reach)).add(-out.z * side, (random.nextDouble() - 0.3) * 0.9, out.x * side);
         ends[i * 3] = (float) end.x;
         ends[i * 3 + 1] = (float) end.y;
         ends[i * 3 + 2] = (float) end.z;
      }

      HeroFx.flash(self, contact);
      HeroFx.shards(self, contact, 0.4F, Blocks.GOLD_BLOCK.defaultBlockState(), ends, null);
      ReinhardSounds.play(self, ReinhardSounds.PARRY.get(), 1.0F, 1.0F);
   }

   private static Vec3 lookVec(ServerPlayer self) {
      Vec2 look = lookOf(self.getYHeadRot());
      return new Vec3(look.x, 0.0, look.y);
   }
}
