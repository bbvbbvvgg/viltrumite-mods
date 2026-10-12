package dev.baranhan.viltrumitecore.hero.reinhard.reid;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Server reactions to Reid's state machine events ({@link Reid#tick}) and the
 * combat rule of the auto-return (spec §7.3). Later tasks hang the draw scene,
 * the sheathe sound and the aura on these events. Server only, except the pure
 * {@link #isCombat}.
 */
public final class ReidLife {
   private ReidLife() {
   }

   /** Pure: a hit taken is combat when an entity caused it (environment damage — fire, fall, void, cactus — is not). */
   public static boolean isCombat(@Nullable Entity cause, @Nullable Entity direct) {
      return cause != null || direct != null;
   }

   public static void tick(ServerPlayer player, ReinhardState state, Reid.Event event) {
   }
}
