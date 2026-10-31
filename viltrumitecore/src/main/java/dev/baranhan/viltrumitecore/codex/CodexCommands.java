package dev.baranhan.viltrumitecore.codex;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Op commands (level 2) for the encyclopedia progress: {@code /codex discover <targets> <id>},
 * {@code /codex forget <targets> <id>}, {@code /codex reset <targets>}, {@code /codex list [target]}.
 * They change only the named players' own records.
 */
@Mod.EventBusSubscriber(modid = "viltrumitecore", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CodexCommands {
   private CodexCommands() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      event.getDispatcher().register(Commands.literal("codex").requires(s -> s.hasPermission(2))
         .then(Commands.literal("discover").then(Commands.argument("targets", EntityArgument.players())
            .then(Commands.argument("id", StringArgumentType.string()).executes(c -> discover(c, true)))))
         .then(Commands.literal("forget").then(Commands.argument("targets", EntityArgument.players())
            .then(Commands.argument("id", StringArgumentType.string()).executes(c -> discover(c, false)))))
         .then(Commands.literal("reset").then(Commands.argument("targets", EntityArgument.players()).executes(CodexCommands::reset)))
         .then(Commands.literal("list").executes(c -> list(c, c.getSource().getPlayerOrException()))
            .then(Commands.argument("target", EntityArgument.player()).executes(c -> list(c, EntityArgument.getPlayer(c, "target"))))));
   }

   private static int discover(CommandContext<CommandSourceStack> c, boolean open) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
      String id = StringArgumentType.getString(c, "id");
      if (CodexRecord.normalize(id) == null) {
         c.getSource().sendFailure(Component.literal("Invalid interaction id: " + id));
         return 0;
      }
      int changed = 0;
      for (ServerPlayer player : targets) {
         if (open ? CodexProgress.discoverInteraction(player, id) : CodexProgress.forgetInteraction(player, id)) {
            changed++;
         }
      }
      int n = changed;
      c.getSource().sendSuccess(() -> Component.literal("codex " + (open ? "discover " : "forget ") + id + ": " + n + "/" + targets.size()), true);
      return changed;
   }

   private static int reset(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
      targets.forEach(CodexProgress::reset);
      c.getSource().sendSuccess(() -> Component.literal("codex reset: " + targets.size()), true);
      return targets.size();
   }

   private static int list(CommandContext<CommandSourceStack> c, ServerPlayer player) {
      CodexRecord record = CodexProgress.read(player);
      c.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().getName() + " heroes=" + record.heroes() + " interactions=" + record.interactions()), false);
      return record.heroes().size() + record.interactions().size();
   }
}
