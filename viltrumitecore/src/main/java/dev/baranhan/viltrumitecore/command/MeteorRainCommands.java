package dev.baranhan.viltrumitecore.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.baranhan.viltrumitecore.worldevent.meteor.MeteorRain;
import java.util.Collection;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * {@code /meteor start [targets]} (operator: the rain blows up terrain), {@code /meteor stop [targets]}
 * (a player may always stop their own rain; others and {@code all} need an operator) and
 * {@code /meteor status}.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class MeteorRainCommands {
   private MeteorRainCommands() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      event.getDispatcher().register(Commands.literal("meteor")
         .then(Commands.literal("start").requires(s -> s.hasPermission(2))
            .executes(c -> start(c, self(c)))
            .then(Commands.argument("targets", EntityArgument.players()).executes(c -> start(c, EntityArgument.getPlayers(c, "targets")))))
         .then(Commands.literal("stop")
            .executes(c -> stop(c, self(c)))
            .then(Commands.literal("all").requires(s -> s.hasPermission(2)).executes(MeteorRainCommands::stopAll))
            .then(Commands.argument("targets", EntityArgument.players()).requires(s -> s.hasPermission(2))
               .executes(c -> stop(c, EntityArgument.getPlayers(c, "targets")))))
         .then(Commands.literal("status").executes(MeteorRainCommands::status)));
   }

   private static Collection<ServerPlayer> self(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      return List.of(c.getSource().getPlayerOrException());
   }

   private static int start(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) {
      int done = 0;
      for (ServerPlayer player : targets) {
         if (!MeteorRain.start(player)) {
            c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.meteor.already", player.getDisplayName()));
            continue;
         }

         done++;
         c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.meteor.started", player.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
         if (c.getSource().getEntity() != player) {
            player.sendSystemMessage(Component.translatable("commands.viltrumitecore.meteor.started.you").withStyle(ChatFormatting.GOLD));
         }
      }

      return done;
   }

   private static int stop(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) {
      int done = 0;
      for (ServerPlayer player : targets) {
         if (!MeteorRain.stop(player.getUUID())) {
            c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.meteor.not_running", player.getDisplayName()));
            continue;
         }

         done++;
         c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.meteor.stopped", player.getDisplayName()), true);
      }

      return done;
   }

   private static int stopAll(CommandContext<CommandSourceStack> c) {
      int count = MeteorRain.stopAll();
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.meteor.stopped_all", count), true);
      return count;
   }

   private static int status(CommandContext<CommandSourceStack> c) {
      Collection<MeteorRain.Session> sessions = MeteorRain.sessions();
      if (sessions.isEmpty()) {
         c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.meteor.none").withStyle(ChatFormatting.GRAY), false);
         return 0;
      }

      for (MeteorRain.Session session : sessions) {
         ServerPlayer owner = c.getSource().getServer().getPlayerList().getPlayer(session.owner());
         Component name = owner != null ? owner.getDisplayName() : Component.literal(session.owner().toString());
         long seconds = session.ageTicks(c.getSource().getServer()) / 20L;
         boolean paused = owner == null || !owner.level().dimension().equals(session.dimension());
         c.getSource().sendSuccess(() -> Component.translatable(
               paused ? "commands.viltrumitecore.meteor.entry.paused" : "commands.viltrumitecore.meteor.entry",
               name, session.dimension().location().toString(), seconds, session.launched(), session.inFlight())
            .withStyle(ChatFormatting.GOLD), false);
      }

      return sessions.size();
   }
}
