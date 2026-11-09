package dev.baranhan.viltrumitecore.codex;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.baranhan.viltrumitecore.codex.data.CodexData;
import dev.baranhan.viltrumitecore.codex.data.CodexEntryDef;
import dev.baranhan.viltrumitecore.codex.data.CodexUnlockRules;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Op commands (level 2) for the encyclopedia progress: {@code /codex discover|forget <targets> <id>}
 * (interactions), {@code /codex unlock|lock <targets> <entry>} and {@code /codex unlockall <targets>}
 * (Codex 2.0 entries), {@code /codex reset <targets>}, {@code /codex list [target]},
 * {@code /codex reload} (re-reads the data files). They change only the named players' own records.
 * {@code /heroselect} (any player) opens the hero select screen; the server still decides every switch.
 */
@Mod.EventBusSubscriber(modid = "viltrumitecore", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CodexCommands {
   private static final SuggestionProvider<CommandSourceStack> ENTRIES = (c, b) -> SharedSuggestionProvider.suggest(CodexData.get().entries().keySet(), b);

   private CodexCommands() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      event.getDispatcher().register(Commands.literal("codex").requires(s -> s.hasPermission(2))
         .then(Commands.literal("discover").then(Commands.argument("targets", EntityArgument.players())
            .then(Commands.argument("id", StringArgumentType.string()).executes(c -> discover(c, true)))))
         .then(Commands.literal("forget").then(Commands.argument("targets", EntityArgument.players())
            .then(Commands.argument("id", StringArgumentType.string()).executes(c -> discover(c, false)))))
         .then(Commands.literal("unlock").then(Commands.argument("targets", EntityArgument.players())
            .then(Commands.argument("entry", StringArgumentType.string()).suggests(ENTRIES).executes(c -> entry(c, true)))))
         .then(Commands.literal("lock").then(Commands.argument("targets", EntityArgument.players())
            .then(Commands.argument("entry", StringArgumentType.string()).suggests(ENTRIES).executes(c -> entry(c, false)))))
         .then(Commands.literal("unlockall").then(Commands.argument("targets", EntityArgument.players()).executes(CodexCommands::unlockAll)))
         .then(Commands.literal("reset").then(Commands.argument("targets", EntityArgument.players()).executes(CodexCommands::reset)))
         .then(Commands.literal("reload").executes(CodexCommands::reload))
         .then(Commands.literal("list").executes(c -> list(c, c.getSource().getPlayerOrException()))
            .then(Commands.argument("target", EntityArgument.player()).executes(c -> list(c, EntityArgument.getPlayer(c, "target"))))));
      event.getDispatcher().register(Commands.literal("heroselect").executes(c -> {
         CodexNetwork.openHeroSelect(c.getSource().getPlayerOrException());
         return 1;
      }));
   }

   private static int discover(CommandContext<CommandSourceStack> c, boolean open) throws CommandSyntaxException {
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

   private static int entry(CommandContext<CommandSourceStack> c, boolean open) throws CommandSyntaxException {
      Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
      String id = StringArgumentType.getString(c, "entry");
      if (CodexData.get().entry(id) == null) {
         c.getSource().sendFailure(Component.literal("Unknown codex entry: " + id));
         return 0;
      }
      int changed = 0;
      for (ServerPlayer player : targets) {
         if (open ? CodexProgress.unlock(player, id) : CodexProgress.lock(player, id)) {
            changed++;
         }
      }
      int n = changed;
      c.getSource().sendSuccess(() -> Component.literal("codex " + (open ? "unlock " : "lock ") + id + ": " + n + "/" + targets.size()), true);
      return changed;
   }

   private static int unlockAll(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
      Collection<CodexEntryDef> all = CodexData.get().entries().values();
      int opened = 0;
      for (ServerPlayer player : targets) {
         opened += CodexProgress.unlockAll(player, all);
      }
      int n = opened;
      c.getSource().sendSuccess(() -> Component.literal("codex unlockall: " + n + " entries for " + targets.size() + " players"), true);
      return opened;
   }

   private static int reset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
      targets.forEach(CodexProgress::reset);
      c.getSource().sendSuccess(() -> Component.literal("codex reset: " + targets.size()), true);
      return targets.size();
   }

   private static int reload(CommandContext<CommandSourceStack> c) {
      CodexData data = CodexData.reload();
      c.getSource().sendSuccess(() -> Component.literal("codex reload: " + data.entries().size() + " entries, " + data.cards().size()
         + " hero cards, " + data.errors().size() + " problems"), true);
      data.errors().stream().limit(5).forEach(p -> c.getSource().sendFailure(Component.literal(p)));
      return data.entries().size();
   }

   private static int list(CommandContext<CommandSourceStack> c, ServerPlayer player) {
      CodexRecord record = CodexProgress.read(player);
      int[] progress = CodexUnlockRules.progress(CodexData.get().entries().values(), record);
      c.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().getName() + " heroes=" + record.heroes() + " interactions="
         + record.interactions() + " entries=" + progress[0] + "/" + progress[1] + " " + record.entries() + " tutorials=" + record.tutorials()), false);
      return record.heroes().size() + record.interactions().size() + record.entries().size();
   }
}
