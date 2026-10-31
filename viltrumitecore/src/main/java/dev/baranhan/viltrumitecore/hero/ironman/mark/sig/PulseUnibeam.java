package dev.baranhan.viltrumitecore.hero.ironman.mark.sig;

import dev.baranhan.viltrumitecore.hero.HeroDestruction;
import dev.baranhan.viltrumitecore.hero.ironman.IronManCombatSounds;
import dev.baranhan.viltrumitecore.hero.ironman.IronManMarkSounds;
import dev.baranhan.viltrumitecore.hero.ironman.IronManRules;
import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import dev.baranhan.viltrumitecore.hero.ironman.combat.UnibeamTimeline;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkSignature;
import dev.baranhan.viltrumitecore.hero.ironman.mark.SignatureState;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * MARK_17: three short strong Unibeam pulses. No overheat and no overdraft count
 * (spec §13.6). The normal Unibeam of this mark is handled by the Unibeam code.
 */
public final class PulseUnibeam implements MarkSignature {
   @Override
   public void press(ServerPlayer player, IronManState state) {
      if (state.energy.weaponsLocked() || !state.energy.spend(SignatureRules.PULSE_COST)) {
         SignatureTrace.sound(player, IronManCombatSounds.REPULSOR_FIZZLE.get(), 0.8F, 1.0F);
         return;
      }

      SignatureState sig = state.signature;
      sig.cooldown = SignatureRules.PULSE_COOLDOWN;
      sig.active = true;
      sig.ticks = 0;
      sig.length = SignatureRules.pulseTotal();
      sig.point = SignatureTrace.chest(player).add(player.getLookAngle().scale(IronManRules.UNIBEAM_RANGE));
      SignatureTrace.sound(player, IronManMarkSounds.PULSE_UNIBEAM.get(), 1.0F, 1.0F);
   }

   @Override
   public void tick(ServerPlayer player, IronManState state) {
      SignatureState sig = state.signature;
      if (!sig.active) {
         return;
      }

      int tick = sig.ticks;
      if (tick >= SignatureRules.pulseTotal()) {
         sig.clear();
         return;
      }

      sig.ticks = tick + 1;
      if (SignatureRules.pulseAt(tick) < 0) {
         return;
      }

      sig.point = pulse(SignatureActor.of(player), SignatureTrace.chest(player), player.getLookAngle(), SignatureRules.pulseHitsTick(tick));
   }

   /** Shared pulse kernel (Tony and the Legion's Mark 17): beam end through glass, hits along it on hit ticks. */
   public static Vec3 pulse(SignatureActor actor, Vec3 from, Vec3 dir, boolean hits) {
      var guard = beamEnd(actor, from, dir);
      if (hits) {
         hitAlong(actor, from, guard, dir);
      }

      return guard.end();
   }

   /** Glass and leaves give way (as the Unibeam); everything else stops the pulse. */
   private static dev.baranhan.viltrumitecore.hero.HeroRayDefense.Stop beamEnd(SignatureActor actor, Vec3 from, Vec3 dir) {
      ServerLevel level = actor.level();
      Vec3 end = from.add(dir.scale(IronManRules.UNIBEAM_RANGE));
      BlockHitResult block = null;
      for (int i = 0; i < 4; i++) {
         block = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor.body()));
         var guard=dev.baranhan.viltrumitecore.hero.HeroRayDefense.clip(actor.body(),from,block.getType()==HitResult.Type.BLOCK?block.getLocation():end,
            IronManRules.UNIBEAM_RADIUS,actor::canHit);
         if(guard.defender()!=null)return guard;
         if (block.getType() != HitResult.Type.BLOCK || i == 3) {
            break;
         }

         BlockPos pos = block.getBlockPos();
         Set<String> tags = new HashSet<>();
         level.getBlockState(pos).getTags().forEach(tag -> tags.add(tag.location().toString()));
         if (!UnibeamTimeline.breaksBlock(tags) || !HeroDestruction.canDestroy(level, pos) || !HeroDestruction.destroyBlock(level, pos)) {
            break;
         }
      }

      return new dev.baranhan.viltrumitecore.hero.HeroRayDefense.Stop(block != null && block.getType() == HitResult.Type.BLOCK ? block.getLocation() : end,null);
   }

   private static void hitAlong(SignatureActor actor, Vec3 from, dev.baranhan.viltrumitecore.hero.HeroRayDefense.Stop guard, Vec3 dir) {
      Vec3 to=guard.end();
      double radius = IronManRules.UNIBEAM_RADIUS;
      Vec3 segment = to.subtract(from);
      double length = segment.length();
      AABB box = new AABB(from, to).inflate(radius + 1.0);
      DamageSource source = actor.attack();
      for (LivingEntity target : actor.level().getEntitiesOfClass(LivingEntity.class, box, actor::canHit)) {
         Vec3 center = target.getBoundingBox().getCenter();
         double t = length < 1.0E-3 ? 0.0 : Mth.clamp(center.subtract(from).dot(segment) / (length * length), 0.0, 1.0);
         Vec3 closest = from.add(segment.scale(t));
         double reach = radius + target.getBbWidth() / 2.0 + target.getBbHeight() / 4.0;
         if (closest.distanceTo(center) > reach) {
            continue;
         }

         if(!guard.permits(target,from))continue;
         if (SignatureTrace.strike(target, source, SignatureRules.PULSE_DAMAGE)) {
            SignatureTrace.push(target, dir.scale(IronManRules.UNIBEAM_PUSH));
            if (target instanceof Mob mob) {
               mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, IronManRules.UNIBEAM_MOB_BLIND_TICKS, 0, false, false));
               mob.setTarget(null);
            }
         }

         if (target instanceof ServerPlayer victim) {
            SignatureTrace.flash(victim, 0.9F, UnibeamTimeline.flashTicks(IronManRules.FLASH_BEAM_TICKS, true));
         }
      }
   }
}
