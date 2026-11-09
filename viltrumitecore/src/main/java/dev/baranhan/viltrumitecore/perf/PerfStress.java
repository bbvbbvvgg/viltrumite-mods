package dev.baranhan.viltrumitecore.perf;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** {@code /vperf stress ...}: controlled load to measure the budgets (filled in by the stress slice). */
public final class PerfStress {
   private PerfStress() {
   }

   static LiteralArgumentBuilder<CommandSourceStack> node() {
      return Commands.literal("stress").then(Commands.literal("stop").executes(ctx -> {
         ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("viltrumitecore.perf.stress.stopped", "Stress stopped"), false);
         return 1;
      }));
   }
}
