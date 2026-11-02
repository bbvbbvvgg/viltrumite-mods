package dev.baranhan.viltrumitecore.hero.rohan.instruction;

import dev.baranhan.viltrumitecore.hero.book.BookProfile;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Everything a line is checked and applied against: who writes, whose book, the book's profile, and the validated
 * parameter (at most one of subject / point / kind is set).
 */
public record InstructionContext(ServerPlayer author, LivingEntity target, BookProfile profile, @Nullable LivingEntity subject,
   @Nullable BlockPos point, @Nullable AttackKind kind, long gameTime, int struggles) {
}
