package dev.baranhan.viltrumitecore.hero.dio;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Operator debug commands for DIO (permission 2, like the other admin commands):
 * {@code /dio blood fill [targets]} and {@code /dio blood infinite on|off [targets]}.
 * They only change {@link DioState}; the bar reaches the HUD through the regular
 * hero snapshot (resource + {@link DioRules#FLAG_INFINITE_BLOOD}), and every cost
 * still goes through the normal checks (cooldowns, busy, sun), so nothing else changes.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class DioCommands {
   private DioCommands() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      event.getDispatcher().register(Commands.literal("dio").requires(s -> s.hasPermission(2))
         .then(Commands.literal("blood")
            .then(Commands.literal("fill")
               .executes(c -> fill(c, self(c)))
               .then(Commands.argument("targets", EntityArgument.players()).executes(c -> fill(c, EntityArgument.getPlayers(c, "targets")))))
            .then(Commands.literal("infinite")
               .then(Commands.literal("on")
                  .executes(c -> infinite(c, self(c), true))
                  .then(Commands.argument("targets", EntityArgument.players()).executes(c -> infinite(c, EntityArgument.getPlayers(c, "targets"), true))))
               .then(Commands.literal("off")
                  .executes(c -> infinite(c, self(c), false))
                  .then(Commands.argument("targets", EntityArgument.players()).executes(c -> infinite(c, EntityArgument.getPlayers(c, "targets"), false)))))));
   }

   private static Collection<ServerPlayer> self(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      return List.of(c.getSource().getPlayerOrException());
   }

   private static int fill(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) {
      int done = 0;
      for (ServerPlayer player : targets) {
         DioState state = DioState.of(player);
         if (state == null) {
            c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.dio.not_dio", player.getDisplayName()));
            continue;
         }

         state.blood = DioRules.BLOOD_MAX;
         done++;
         c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.dio.blood.fill", player.getDisplayName()), true);
      }

      return done;
   }

   private static int infinite(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets, boolean on) {
      int done = 0;
      for (ServerPlayer player : targets) {
         DioState state = DioState.of(player);
         if (state == null) {
            c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.dio.not_dio", player.getDisplayName()));
            continue;
         }

         state.infiniteBlood = on;
         if (on) {
            state.blood = DioRules.BLOOD_MAX;
         }

         done++;
         String key = on ? "commands.viltrumitecore.dio.blood.infinite.on" : "commands.viltrumitecore.dio.blood.infinite.off";
         c.getSource().sendSuccess(() -> Component.translatable(key, player.getDisplayName()), true);
      }

      return done;
   }
}
