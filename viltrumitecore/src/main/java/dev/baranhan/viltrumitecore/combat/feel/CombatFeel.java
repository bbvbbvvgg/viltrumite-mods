package dev.baranhan.viltrumitecore.combat.feel;

import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.FeelS2CPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Server API of the cinematic combat feel (wave1-22). Hero-agnostic: any hero, mob or world event
 * calls it, the client applies the player's comfort settings (reduced motion, flashes).
 *
 * <pre>
 * CombatFeel.hit(attacker, target, HitWeight.HEAVY);            // hit-stop + shake + ring
 * CombatFeel.shake(level, at, ShakeProfile.EXPLOSION, 1F, 40);  // world shake with falloff
 * CombatFeel.impactFrame(level, at, 1F, 12);                    // manga impact frame
 * CombatFeel.shockwave(level, at, 1F, 14);                      // air distortion ring
 * </pre>
 */
public final class CombatFeel {
   /** Radius in which a hit shakes viewers' cameras (with falloff). */
   public static final double SHAKE_RANGE = 32.0;
   /** Impact frames are personal: attacker, victim and very close viewers. */
   public static final double IMPACT_RANGE = 12.0;
   /** Air rings are visible from further away; the client fades them with distance. */
   public static final double WAVE_RANGE = 64.0;
   public static final int WAVE_TICKS = 14;

   private CombatFeel() {
   }

   /** A landed hit of the given weight. Server side only; client calls are ignored. */
   public static void hit(Entity attacker, Entity target, HitWeight weight) {
      if (attacker == null || target == null || weight == null || !(target.level() instanceof ServerLevel level)) {
         return;
      }
      Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
      if (weight.hitStopTicks > 0 && attacker instanceof ServerPlayer player) {
         int ticks = Math.min(weight.hitStopTicks, HeroFx.PRESET_MAX_HIT_STOP);
         if (attacker == target) {
            HeroFx.hitStop(player, at, ticks, target.getId());
         } else {
            HeroFx.hitStop(player, at, ticks, attacker.getId(), target.getId());
         }
      }
      shake(level, at, weight.shake, weight.trauma, SHAKE_RANGE);
      if (weight.impactFrame) {
         impactFrame(level, at, 1.0F, IMPACT_RANGE);
      }
      if (weight.wave > 0.0F) {
         shockwave(level, at, weight.wave, WAVE_TICKS);
      }
   }

   /** Camera shake for everyone within {@code range} of {@code at}; strength falls off with distance. */
   public static void shake(ServerLevel level, Vec3 at, ShakeProfile profile, float trauma, double range) {
      sendNear(level, at, range, FeelS2CPacket.shake(at, profile, trauma, range));
   }

   /** Personal shake without falloff (own wind-up, charge rumble). */
   public static void shake(ServerPlayer player, ShakeProfile profile, float trauma) {
      CoreMessages.sendToPlayer(FeelS2CPacket.shake(player.getEyePosition(), profile, trauma, 0.0), player);
   }

   /** Impact frame (inverted frame + speed lines toward {@code at}) for viewers within {@code range}. */
   public static void impactFrame(ServerLevel level, Vec3 at, float strength, double range) {
      sendNear(level, at, range, FeelS2CPacket.impact(at, strength, range));
   }

   /** Air distortion ring expanding from {@code at} over {@code ticks}. */
   public static void shockwave(ServerLevel level, Vec3 at, float strength, int ticks) {
      sendNear(level, at, WAVE_RANGE, FeelS2CPacket.wave(at, strength, ticks));
   }

   private static void sendNear(ServerLevel level, Vec3 at, double range, FeelS2CPacket packet) {
      if (!(range > 0.0)) {
         return;
      }
      double r2 = range * range;
      for (ServerPlayer viewer : level.players()) {
         if (viewer.position().distanceToSqr(at) <= r2) {
            CoreMessages.sendToPlayer(packet, viewer);
         }
      }
   }
}
