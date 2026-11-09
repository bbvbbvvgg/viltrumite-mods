package dev.baranhan.viltrumitecore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Playtest-only dev commands (wave 1, session 02 - playtest rig).
 *
 * <p>Registered only when the JVM runs with {@code -Dviltrumitecore.playtest=true}
 * ({@code tools/playtest/rig.py} sets it on the server and on every client). Without the
 * flag nothing is registered, so normal games and the release jar are not affected.
 *
 * <p>Every reply is one plain ASCII line that starts with {@code playtest}, so RCON
 * responses and log checks in JSON scenarios can match it with a simple regex:
 * <ul>
 *   <li>{@code /playtest ping} - {@code playtest pong}</li>
 *   <li>{@code /playtest heroes} - {@code playtest heroes human,viltrumite,...}</li>
 *   <li>{@code /playtest who [targets]} - {@code playtest who NAME hero=KEY hp=20.0 dim=minecraft:overworld pos=0,64,0}</li>
 *   <li>{@code /playtest hero <targets> <hero>} - {@code playtest hero NAME human -> thor ok|deferred|refused|same}</li>
 * </ul>
 *
 * <p>{@code hero} uses the same server path as the race choice screen
 * ({@link HeroRegistry#changeHero}), so hero entry rules stay in force; it only marks the
 * one-time race choice as made, so the choice screen does not block a scripted player.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class PlaytestCommands {
   public static final boolean ENABLED = Boolean.getBoolean("viltrumitecore.playtest");

   private PlaytestCommands() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      if (!ENABLED) {
         return;
      }

      CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
      dispatcher.register(
         Commands.literal("playtest")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("ping").executes(ctx -> {
               reply(ctx.getSource(), "playtest pong");
               return 1;
            }))
            .then(Commands.literal("heroes").executes(ctx -> {
               reply(ctx.getSource(), "playtest heroes " + String.join(",", keys()));
               return keys().size();
            }))
            .then(Commands.literal("who")
               .executes(ctx -> who(ctx.getSource(), ctx.getSource().getServer().getPlayerList().getPlayers()))
               .then(Commands.argument("targets", EntityArgument.players())
                  .executes(ctx -> who(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))
            .then(Commands.literal("hero")
               .then(Commands.argument("targets", EntityArgument.players())
                  .then(Commands.argument("hero", StringArgumentType.word())
                     .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(keys(), builder))
                     .executes(ctx -> hero(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                        StringArgumentType.getString(ctx, "hero"))))))
      );
   }

   static List<String> keys() {
      return Arrays.stream(HeroId.values()).map(HeroId::key).toList();
   }

   private static int who(CommandSourceStack source, Collection<ServerPlayer> players) {
      if (players.isEmpty()) {
         reply(source, "playtest who none");
         return 0;
      }

      for (ServerPlayer player : players) {
         reply(source, String.format(Locale.ROOT, "playtest who %s hero=%s hp=%.1f dim=%s pos=%d,%d,%d",
            player.getGameProfile().getName(), HeroRegistry.get(player).id().key(), player.getHealth(),
            player.level().dimension().location(), player.blockPosition().getX(), player.blockPosition().getY(),
            player.blockPosition().getZ()));
      }

      return players.size();
   }

   private static int hero(CommandSourceStack source, Collection<ServerPlayer> players, String key) {
      HeroId target = HeroId.fromKey(key.toLowerCase(Locale.ROOT));
      if (target == null) {
         source.sendFailure(Component.literal("playtest hero unknown " + key + " (known: " + String.join(",", keys()) + ")"));
         return 0;
      }

      int accepted = 0;
      for (ServerPlayer player : players) {
         if (player instanceof ViltrumiteCorePlayer corePlayer) {
            corePlayer.setChosenRace(true);
         }

         HeroId before = HeroRegistry.get(player).id();
         String state;
         if (before == target) {
            state = "same";
         } else if (HeroRegistry.changeHero(player, target)) {
            state = HeroRegistry.get(player).id() == target ? "ok" : "deferred";
         } else {
            state = "refused";
         }

         if (!state.equals("refused")) {
            accepted++;
         }

         reply(source, "playtest hero " + player.getGameProfile().getName() + " " + before.key() + " -> " + target.key() + " " + state);
      }

      return accepted;
   }

   private static void reply(CommandSourceStack source, String line) {
      source.sendSuccess(() -> Component.literal(line), false);
   }
}
