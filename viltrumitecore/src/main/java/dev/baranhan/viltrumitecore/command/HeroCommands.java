package dev.baranhan.viltrumitecore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.baranhan.viltrumitecore.entity.avengers.TeamMoves;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.PersistentCooldowns;
import dev.baranhan.viltrumitecore.hero.captain.assemble.AvengersTeam;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Debug commands of the hero layer (operators, permission level 2):
 * {@code /hero cooldown reset [targets] [hero]} zeroes every ability cooldown of
 * the targets' current hero. With {@code hero} only targets currently playing
 * that hero are touched. Cooldown timers only: running casts, channels,
 * resources and active states are left alone; the public snapshot is pushed at
 * once so the HUD updates the same tick. {@code /hero team <vault|beam|shield>}
 * makes the summoned Avengers try that joint move at its next chance (playtest).
 */
public final class HeroCommands {
   private static final SuggestionProvider<CommandSourceStack> HEROES = (context, builder) -> SharedSuggestionProvider.suggest(
      java.util.Arrays.stream(HeroId.values()).map(HeroId::key), builder);

   private HeroCommands() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(Commands.literal("hero")
         .requires(source -> source.hasPermission(2))
         .then(Commands.literal("cooldown")
            .then(Commands.literal("reset")
               .executes(context -> reset(context.getSource(), List.of(context.getSource().getPlayerOrException()), null))
               .then(Commands.argument("targets", EntityArgument.players())
                  .executes(context -> reset(context.getSource(), EntityArgument.getPlayers(context, "targets"), null))
                  .then(Commands.argument("hero", StringArgumentType.word())
                     .suggests(HEROES)
                     .executes(context -> reset(context.getSource(), EntityArgument.getPlayers(context, "targets"),
                        StringArgumentType.getString(context, "hero")))))))
         .then(Commands.literal("team")
            .then(Commands.argument("move", StringArgumentType.word())
               .suggests((context, builder) -> SharedSuggestionProvider.suggest(TeamMoves.names(), builder))
               .executes(context -> team(context.getSource(), context.getSource().getPlayerOrException(),
                  StringArgumentType.getString(context, "move"))))));
   }

   /** Playtest of the Avengers' joint moves: the named one starts at its next chance (cooldown and gap ignored). */
   private static int team(CommandSourceStack source, ServerPlayer player, String move) {
      int index = TeamMoves.parse(move);
      if (index < 0) {
         source.sendFailure(Component.translatable("commands.viltrumitecore.hero.team.unknown", move));
         return 0;
      }
      if (!AvengersTeam.forceTeamMove(player, index)) {
         source.sendFailure(Component.translatable("commands.viltrumitecore.hero.team.no_fight", player.getDisplayName()));
         return 0;
      }
      source.sendSuccess(() -> Component.translatable("commands.viltrumitecore.hero.team.forced", move), true);
      return 1;
   }

   /** Pure: the hero named on the command line (its save key, case-insensitive), or null. */
   @Nullable
   static HeroId parseHero(String name) {
      String key = name.toLowerCase(Locale.ROOT);
      for (HeroId id : HeroId.values()) {
         if (id.key().equals(key)) {
            return id;
         }
      }
      return null;
   }

   private static int reset(CommandSourceStack source, Collection<ServerPlayer> targets, @Nullable String heroName) {
      HeroId filter = null;
      if (heroName != null) {
         filter = parseHero(heroName);
         if (filter == null) {
            source.sendFailure(Component.translatable("commands.viltrumitecore.hero.unknown", heroName));
            return 0;
         }
      }

      int done = 0;
      for (ServerPlayer player : targets) {
         HeroDefinition hero = HeroRegistry.get(player);
         if (filter != null && hero.id() != filter) {
            source.sendFailure(Component.translatable("commands.viltrumitecore.hero.cooldown.reset.other_hero", player.getDisplayName(),
               heroName(hero.id()), heroName(filter)));
            continue;
         }

         boolean own = resetCooldowns(player, hero);
         Component name = heroName(hero.id());
         source.sendSuccess(() -> Component.translatable(own ? "commands.viltrumitecore.hero.cooldown.reset.success"
            : "commands.viltrumitecore.hero.cooldown.reset.shared_only", player.getDisplayName(), name), true);
         done++;
      }

      return done;
   }

   /** Shared timers (persistent store, legacy kit, item cooldowns), then the hero's own, then the HUD sync. */
   static boolean resetCooldowns(ServerPlayer player, HeroDefinition hero) {
      PersistentCooldowns.clearAll(player);
      if (player instanceof ViltrumiteCorePlayer core) {
         core.setPunchCooldown(0);
         core.setBlockCooldown(0);
         core.setBarrageCooldown(0);
      }

      for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
         ItemStack stack = player.getInventory().getItem(slot);
         if (!stack.isEmpty() && player.getCooldowns().isOnCooldown(stack.getItem())) {
            player.getCooldowns().removeCooldown(stack.getItem());
         }
      }

      boolean own = hero.resetCooldowns(player);
      HeroRegistry.syncSnapshot(player);
      return own;
   }

   private static Component heroName(HeroId id) {
      return Component.translatableWithFallback("hero.viltrumitecore." + id.key(), id.key());
   }
}
