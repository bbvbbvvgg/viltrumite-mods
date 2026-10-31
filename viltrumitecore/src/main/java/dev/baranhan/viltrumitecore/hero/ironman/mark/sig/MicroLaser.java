package dev.baranhan.viltrumitecore.hero.ironman.mark.sig;

import dev.baranhan.viltrumitecore.hero.ironman.IronManCombatSounds;
import dev.baranhan.viltrumitecore.hero.ironman.IronManMarkSounds;
import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkSignature;
import dev.baranhan.viltrumitecore.hero.ironman.mark.SignatureState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

/** MARK_7: micro-lasers from the forearms: thin precise RMB beam, one target, breaks shields (spec §13.2). */
public final class MicroLaser implements MarkSignature {
   @Override
   public boolean onRmb() {
      return true;
   }

   @Override
   public boolean held() {
      return true;
   }

   @Override
   public void press(ServerPlayer player, IronManState state) {
      if (state.energy.weaponsLocked() || state.energy.empty()) {
         SignatureTrace.sound(player, IronManCombatSounds.REPULSOR_FIZZLE.get(), 0.8F, 1.0F);
         return;
      }

      SignatureState sig = state.signature;
      sig.held = true;
      sig.ticks = 0;
      sig.length = SignatureRules.LASER_HOLD_MAX;
      sig.active = true;
      SignatureTrace.sound(player, IronManMarkSounds.MICRO_LASER.get(), 1.0F, 1.0F);
   }

   @Override
   public void release(ServerPlayer player, IronManState state) {
      state.signature.clear();
   }

   @Override
   public void tick(ServerPlayer player, IronManState state) {
      SignatureState sig = state.signature;
      if (!sig.held) {
         return;
      }

      if (state.energy.weaponsLocked() || state.energy.value() < SignatureRules.LASER_COST) {
         SignatureTrace.sound(player, IronManCombatSounds.REPULSOR_FIZZLE.get(), 0.8F, 1.0F);
         sig.clear();
         return;
      }

      state.energy.drain(SignatureRules.LASER_COST);
      int tick = sig.ticks;
      sig.ticks++;
      Beam beam = beam(SignatureActor.of(player), player.getLookAngle());
      if (beam.point() != null) {
         sig.point = beam.point();
      }

      if (beam.blocked()) {
         return;
      }

      if (tick % SignatureRules.LASER_SOUND_INTERVAL == 0 && tick > 0) {
         SignatureTrace.sound(player, IronManMarkSounds.MICRO_LASER.get(), 0.6F, 1.0F);
      }
   }

   /** One beam tick: where it ends (null = keep the last point) and whether a hero defense stopped it. */
   public record Beam(@javax.annotation.Nullable net.minecraft.world.phys.Vec3 point, boolean blocked) {
   }

   /**
    * Shared beam kernel (Tony and the Iron Legion's Mark 7): ray from the actor's
    * eye, a hero defense may stop it at the palm, else the first target takes
    * the tick damage and loses its shield.
    */
   public static Beam beam(SignatureActor actor, net.minecraft.world.phys.Vec3 dir) {
      SignatureTrace.Hit hit = SignatureTrace.shoot(actor, dir, SignatureRules.LASER_RANGE);
      if (hit.target() instanceof ServerPlayer defender && dev.baranhan.viltrumitecore.hero.HeroRegistry.get(defender)
         .blocksContinuousRay(defender, actor.eye(), hit.end())) {
         var palm = dev.baranhan.viltrumitecore.hero.HeroRegistry.get(defender).defenseContact(defender);
         return new Beam(palm != null ? palm : hit.end(), true);
      }

      if (hit.target() != null && actor.mayStrike(hit.target())) {
         breakShield(hit.target());
         SignatureTrace.strike(hit.target(), actor.attack(), SignatureRules.LASER_DAMAGE);
      }

      return new Beam(hit.end(), false);
   }

   /** Vanilla shield goes on the axe-style cooldown; an Iron Man shield drops at once. */
   private static void breakShield(LivingEntity target) {
      if (!(target instanceof ServerPlayer victim)) {
         return;
      }

      victim.getCooldowns().addCooldown(Items.SHIELD, SignatureRules.LASER_SHIELD_COOLDOWN);
      if (victim.isUsingItem() && victim.getUseItem().is(Items.SHIELD)) {
         victim.stopUsingItem();
      }

      IronManState other = IronManState.of(victim);
      if (other != null) {
         other.shield.lower();
      }
   }
}
