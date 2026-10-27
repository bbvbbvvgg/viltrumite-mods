package dev.baranhan.viltrumitecore.hero.thor.scene;

import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Server side of the double-WASD dash (decisions 110, 135): the client moves
 * the local player at once (its own movement), the server checks that a
 * dash is allowed now (ground or HOVER, never full flight, no scene, not
 * held) and on cooldown, and plays it for everyone around.
 */
final class ThorDash {
   private static final Map<UUID, Long> NEXT = new HashMap<>();

   private ThorDash() {
   }

   static boolean allowed(ServerPlayer player) {
      ThorState state = ThorState.of(player);
      return state != null && player.isAlive()
         && ThorMovementRules.dashAllowed(ThorBridge.sceneLocked(state), HeroDamage.isAnchored(player) || dev.baranhan.viltrumitecore.hero.HeroHeldTargets.holder(player) != null, ThorScenes.flightState(player), player.onGround());
   }

   static void onDash(ServerPlayer player, int direction) {
      if (direction < 0 || direction > 3 || !allowed(player)) {
         return;
      }

      long now = player.level().getGameTime();
      Long next = NEXT.get(player.getUUID());
      if (next != null && now < next) {
         return;
      }

      NEXT.put(player.getUUID(), now + Math.max(1, ThorSceneConfig.dashCooldown - 2));
      HeroFx.airTrail(player, player);
      SceneFx.sound(player, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 1.5F);
   }

   static void forget(UUID player) {
      NEXT.remove(player);
   }
}
