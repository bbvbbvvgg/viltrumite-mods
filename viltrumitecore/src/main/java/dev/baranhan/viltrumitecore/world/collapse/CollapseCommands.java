package dev.baranhan.viltrumitecore.world.collapse;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import dev.baranhan.viltrumitecore.hero.HeroDestruction;

/**
 * {@code /collapse} (op 2): QA and admin tools. {@code strike} and {@code cut} damage the world through the
 * normal pipeline (blocks removed inside a cause scope, the ledger settles, the analyzer decides), so they
 * test exactly what a Leviathan or a meteor would trigger; nothing is forced.
 */
public final class CollapseCommands {
   private static final String[] CAUSES = {"command", "leviathan", "meteor", "chitauri", "hero", "explosion"};

   private CollapseCommands() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(Commands.literal("collapse").requires(s -> s.hasPermission(2))
         .then(Commands.literal("status").executes(CollapseCommands::status))
         .then(Commands.literal("clear").executes(CollapseCommands::clear))
         .then(Commands.literal("analyze").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(CollapseCommands::analyze)))
         .then(Commands.literal("strike").then(Commands.argument("pos", BlockPosArgument.blockPos())
            .then(Commands.argument("radius", IntegerArgumentType.integer(1, 8)).executes(c -> strike(c, "command"))
               .then(Commands.argument("cause", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(CAUSES, b))
                  .executes(c -> strike(c, StringArgumentType.getString(c, "cause")))))))
         .then(Commands.literal("cut").then(Commands.argument("from", BlockPosArgument.blockPos()).then(Commands.argument("to", BlockPosArgument.blockPos())
            .executes(c -> cut(c, "command"))
            .then(Commands.argument("cause", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(CAUSES, b))
               .executes(c -> cut(c, StringArgumentType.getString(c, "cause"))))))));
   }

   private static int status(CommandContext<CommandSourceStack> ctx) {
      ServerLevel level = ctx.getSource().getLevel();
      DamageLedger ledger = StructuralDamage.ledger(level);
      String head = (CollapseDirector.enabled() ? "" : "[disabled] ") + CollapseDirector.status() + String.format("; ledger %d blocks in %d cells",
         ledger.size(), ledger.cellCount());
      ctx.getSource().sendSuccess(() -> Component.literal(head), false);
      for (String line : CollapseDirector.describeActive()) {
         ctx.getSource().sendSuccess(() -> Component.literal(line), false);
      }
      return CollapseDirector.activeCount();
   }

   private static int clear(CommandContext<CommandSourceStack> ctx) {
      ServerLevel level = ctx.getSource().getLevel();
      int n = CollapseDirector.activeCount();
      CollapseDirector.abortAll(level);
      StructuralDamage.ledger(level).clear();
      CollapseDirector.resetMetrics();
      ctx.getSource().sendSuccess(() -> Component.literal("collapse: aborted " + n + ", ledger cleared"), true);
      return n;
   }

   private static int analyze(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      ServerLevel level = ctx.getSource().getLevel();
      BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
      CollapseAnalyzer.Plan plan = CollapseDirector.tryStart(level, pos.getX(), pos.getY(), pos.getZ(), 0.0F, CollapseCause.COMMAND, true);
      String text = plan.accepted()
         ? String.format("collapse would start: %s %s, rule %s, %d blocks%s, box %d..%d / %d..%d / %d..%d, ground %d, fall dir %.2f %.2f (%d damaged)",
            plan.variant(), plan.severity(), plan.rule(), plan.blocks().length, plan.truncated() ? " (truncated)" : "", plan.minX(), plan.maxX(), plan.minY(),
            plan.maxY(), plan.minZ(), plan.maxZ(), plan.groundY(), plan.fallX(), plan.fallZ(), plan.damaged())
         : String.format("no collapse: %s (%d damaged blocks, %d cells)", plan.reason(), plan.damaged(), plan.cells());
      ctx.getSource().sendSuccess(() -> Component.literal(text), false);
      return plan.accepted() ? 1 : 0;
   }

   /** Damages a sphere the way an attack would; the collapse (if any) follows from the analysis. */
   private static int strike(CommandContext<CommandSourceStack> ctx, String causeId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      ServerLevel level = ctx.getSource().getLevel();
      BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
      int r = IntegerArgumentType.getInteger(ctx, "radius");
      CollapseCause cause = CollapseCause.byId(causeId);
      int[] removed = new int[1];
      StructuralDamage.run(cause, () -> {
         BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
         for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
               for (int dz = -r; dz <= r; dz++) {
                  if (dx * dx + dy * dy + dz * dz <= r * r + r) {
                     p.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                     if (HeroDestruction.clearBlock(level, p.immutable())) {
                        removed[0]++;
                     }
                  }
               }
            }
         }
      });
      CollapseDirector.noteCause(level, cause);
      StructuralDamage.impact(level, Vec3.atCenterOf(pos), cause);
      ctx.getSource().sendSuccess(() -> Component.literal("collapse strike (" + cause.id() + "): " + removed[0] + " blocks removed"), true);
      return removed[0];
   }

   /** Removes a box (a floor slab, a row of columns) the way an attack would. At most 4096 blocks. */
   private static int cut(CommandContext<CommandSourceStack> ctx, String causeId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      ServerLevel level = ctx.getSource().getLevel();
      BlockPos a = BlockPosArgument.getLoadedBlockPos(ctx, "from");
      BlockPos b = BlockPosArgument.getLoadedBlockPos(ctx, "to");
      int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX());
      int y0 = Math.min(a.getY(), b.getY()), y1 = Math.max(a.getY(), b.getY());
      int z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());
      long volume = (long)(x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
      if (volume > 4096L) {
         ctx.getSource().sendFailure(Component.literal("collapse cut: at most 4096 blocks"));
         return 0;
      }
      CollapseCause cause = CollapseCause.byId(causeId);
      int[] removed = new int[1];
      StructuralDamage.run(cause, () -> {
         for (BlockPos p : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            if (HeroDestruction.clearBlock(level, p.immutable())) {
               removed[0]++;
            }
         }
      });
      CollapseDirector.noteCause(level, cause);
      StructuralDamage.impact(level, new Vec3((x0 + x1) / 2.0 + 0.5, (y0 + y1) / 2.0 + 0.5, (z0 + z1) / 2.0 + 0.5), cause);
      ctx.getSource().sendSuccess(() -> Component.literal("collapse cut (" + cause.id() + "): " + removed[0] + " blocks removed"), true);
      return removed[0];
   }
}
