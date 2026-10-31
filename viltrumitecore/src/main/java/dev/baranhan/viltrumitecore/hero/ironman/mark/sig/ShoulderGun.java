package dev.baranhan.viltrumitecore.hero.ironman.mark.sig;

import dev.baranhan.viltrumitecore.hero.ironman.IronManCombatSounds;
import dev.baranhan.viltrumitecore.hero.ironman.IronManMarkSounds;
import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkSignature;
import dev.baranhan.viltrumitecore.hero.ironman.mark.SignatureState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * WAR_MACHINE_MK2: shoulder gun. Hold RMB or the slot: one bullet every
 * {@link SignatureRules#GUN_INTERVAL} ticks at the crosshair, energy instead of ammo (spec §13.7).
 */
public final class ShoulderGun implements MarkSignature {
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
      if (state.energy.weaponsLocked() || state.energy.value() < SignatureRules.GUN_COST_PER_TICK) {
         SignatureTrace.sound(player, IronManCombatSounds.REPULSOR_FIZZLE.get(), 0.8F, 1.0F);
         return;
      }

      SignatureState sig = state.signature;
      sig.held = true;
      sig.ticks = 0;
      sig.length = SignatureRules.GUN_HOLD_MAX;
      sig.active = true;
      sig.point = null;
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

      if (state.energy.weaponsLocked() || state.energy.value() < SignatureRules.GUN_COST_PER_TICK) {
         SignatureTrace.sound(player, IronManCombatSounds.REPULSOR_FIZZLE.get(), 0.8F, 1.0F);
         sig.clear();
         return;
      }

      state.energy.drain(SignatureRules.GUN_COST_PER_TICK);
      int tick = sig.ticks;
      sig.ticks++;
      if (!SignatureRules.gunFires(tick)) {
         return;
      }

      sig.point = round(SignatureActor.of(player), player.getLookAngle());
   }

   /** Shared round kernel (Tony and the Legion's War Machine): spread, ray, hit, report. Returns the impact point. */
   public static Vec3 round(SignatureActor actor, Vec3 aim) {
      Vec3 dir = SignatureTrace.spread(aim, actor.random(), SignatureRules.GUN_SPREAD_DEG);
      SignatureTrace.Hit hit = SignatureTrace.shoot(actor, dir, SignatureRules.GUN_RANGE);
      if (hit.target() != null && actor.mayStrike(hit.target())) {
         SignatureTrace.shot(hit.target(), actor.attack(), SignatureRules.GUN_DAMAGE);
      }

      actor.sound(IronManMarkSounds.SHOULDER_GUN.get(), 0.6F, 0.95F + actor.random().nextFloat() * 0.1F);
      return hit.end();
   }
}
