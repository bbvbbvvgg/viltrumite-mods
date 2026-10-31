package dev.baranhan.viltrumitecore.hero.ironman.mark.sig;

import dev.baranhan.viltrumitecore.hero.ironman.IronManSounds;
import dev.baranhan.viltrumitecore.hero.ironman.combat.IronManTargets;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Who fires a signature weapon: Tony himself or a suit of the Iron Legion
 * (stage 6). The weapon kernels (micro-laser beam, shoulder-gun round, pulse,
 * rocket fist) take an actor, so the player signatures and the Legion share
 * one implementation and no fake player is needed.
 */
public interface SignatureActor {
   LivingEntity body();

   ServerLevel level();

   Vec3 eye();

   Vec3 look();

   /** Forearm muzzle (micro-lasers, rocket fist). */
   Vec3 hand(boolean right);

   /** Arc reactor (Unibeam pulses). */
   Vec3 chest();

   DamageSource attack();

   /** Area and beam selection: alive, not self, not an ally, PvP rule for players. */
   boolean canHit(Entity target);

   /** Single-target hit: Tony leaves it to vanilla {@code hurt}; a Legion suit applies its ally rule. */
   boolean mayStrike(LivingEntity target);

   void sound(SoundEvent sound, float volume, float pitch);

   default RandomSource random() {
      return this.body().getRandom();
   }

   /** The rocket fist is back on the right hand. */
   default void fistReturned() {
   }

   /** An entity that fires signatures without being a player. */
   interface Provider {
      SignatureActor signatureActor();
   }

   static SignatureActor of(ServerPlayer player) {
      return new PlayerActor(player);
   }

   @Nullable
   static SignatureActor resolve(@Nullable Entity entity) {
      if (entity instanceof ServerPlayer player) {
         return of(player);
      }

      return entity instanceof Provider provider ? provider.signatureActor() : null;
   }

   /** Tony: the stage 4 behaviour, unchanged. */
   record PlayerActor(ServerPlayer player) implements SignatureActor {
      @Override
      public LivingEntity body() {
         return this.player;
      }

      @Override
      public ServerLevel level() {
         return this.player.serverLevel();
      }

      @Override
      public Vec3 eye() {
         return this.player.getEyePosition();
      }

      @Override
      public Vec3 look() {
         return this.player.getLookAngle();
      }

      @Override
      public Vec3 hand(boolean right) {
         return SignatureTrace.hand(this.player, right);
      }

      @Override
      public Vec3 chest() {
         return SignatureTrace.chest(this.player);
      }

      @Override
      public DamageSource attack() {
         return this.player.damageSources().playerAttack(this.player);
      }

      @Override
      public boolean canHit(Entity target) {
         return IronManTargets.canHit(this.player, target);
      }

      @Override
      public boolean mayStrike(LivingEntity target) {
         return true;
      }

      @Override
      public void sound(SoundEvent sound, float volume, float pitch) {
         IronManSounds.play(this.player, sound, volume, pitch);
      }

      @Override
      public void fistReturned() {
         RocketFist.reattach(this.player);
      }
   }
}
