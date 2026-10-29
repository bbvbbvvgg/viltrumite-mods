package dev.baranhan.viltrumitecore.hero.thor.bridge;

import dev.baranhan.viltrumitecore.hero.thor.ThorInput;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * What a move handler receives. {@code player} is null only in pure tests;
 * {@code target} is the enemy that made the MMB resolver pick the melee
 * branch (ThorMove.HAMMER_STRIKE), otherwise null. {@code input} is the
 * key whose press reached {@code onStart} / {@code admits} (LMB and RMB both
 * feed the combo moves), null for phase events and technical starts.
 */
public record ThorMoveContext(@Nullable ServerPlayer player, ThorState state, ThorMove move, @Nullable LivingEntity target,
   @Nullable ThorInput input) {
   /** Without the pressed input (phase events, technical starts). */
   public ThorMoveContext(@Nullable ServerPlayer player, ThorState state, ThorMove move, @Nullable LivingEntity target) {
      this(player, state, move, target, null);
   }
}
