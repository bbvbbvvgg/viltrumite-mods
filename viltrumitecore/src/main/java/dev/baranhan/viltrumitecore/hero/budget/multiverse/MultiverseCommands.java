package dev.baranhan.viltrumitecore.hero.budget.multiverse;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.baranhan.viltrumitecore.hero.budget.BudgetFaction;
import dev.baranhan.viltrumitecore.hero.budget.BudgetHeroSpec;
import dev.baranhan.viltrumitecore.hero.budget.BudgetHeroes;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /multiverse start [modifiers...] | stop | status | bossrush [stop] |
 * boss <id> | clash <faction> <faction>} (operators only).
 */
public final class MultiverseCommands {
   private MultiverseCommands() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(Commands.literal("multiverse").requires(source -> source.hasPermission(2))
         .then(Commands.literal("start")
            .executes(ctx -> start(ctx, ""))
            .then(Commands.argument("modifiers", StringArgumentType.greedyString())
               .suggests((ctx, builder) -> {
                  List<String> ids = new ArrayList<>();
                  for (ChaosModifier modifier : ChaosModifier.values()) {
                     ids.add(modifier.id());
                  }
                  ids.add("all");
                  String remaining = builder.getRemaining();
                  int space = remaining.lastIndexOf(' ');
                  return SharedSuggestionProvider.suggest(ids, space < 0 ? builder : builder.createOffset(builder.getStart() + space + 1));
               })
               .executes(ctx -> start(ctx, StringArgumentType.getString(ctx, "modifiers")))))
         .then(Commands.literal("stop").executes(MultiverseCommands::stop))
         .then(Commands.literal("status").executes(MultiverseCommands::status))
         .then(Commands.literal("bossrush")
            .executes(MultiverseCommands::bossRush)
            .then(Commands.literal("stop").executes(ctx -> {
               ServerPlayer player = ctx.getSource().getPlayerOrException();
               boolean stopped = MultiverseBossRush.stop(ctx.getSource().getServer(), player.getUUID(), "multiverse.viltrumitecore.title.bossrush_cancelled");
               ctx.getSource().sendSuccess(() -> Component.translatable(stopped ? "multiverse.viltrumitecore.bossrush_stopped" : "multiverse.viltrumitecore.none"), false);
               return stopped ? 1 : 0;
            })))
         .then(Commands.literal("boss")
            .then(Commands.argument("id", StringArgumentType.word())
               .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(MultiverseBoss.ALL.stream().map(MultiverseBoss::id), builder))
               .executes(MultiverseCommands::boss)))
         .then(Commands.literal("clash")
            .then(Commands.argument("first", StringArgumentType.word())
               .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(factionIds(), builder))
               .then(Commands.argument("second", StringArgumentType.word())
                  .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(factionIds(), builder))
                  .executes(MultiverseCommands::clash)))));
   }

   private static List<String> factionIds() {
      List<String> ids = new ArrayList<>();
      for (BudgetFaction faction : BudgetFaction.values()) {
         ids.add(faction.name().toLowerCase(java.util.Locale.ROOT));
      }
      return ids;
   }

   private static int start(CommandContext<CommandSourceStack> ctx, String text) {
      StringBuilder unknown = new StringBuilder();
      Set<ChaosModifier> mods = ChaosModifier.parse(text, unknown);
      if (unknown.length() > 0) {
         ctx.getSource().sendFailure(Component.translatable("multiverse.viltrumitecore.unknown_modifier", unknown.toString()));
         return 0;
      }

      ServerLevel level = ctx.getSource().getLevel();
      if (!MultiverseCollapse.start(level, ctx.getSource().getPosition(), mods)) {
         ctx.getSource().sendFailure(Component.translatable("multiverse.viltrumitecore.already"));
         return 0;
      }

      ctx.getSource().sendSuccess(() -> Component.translatable("multiverse.viltrumitecore.started").withStyle(ChatFormatting.LIGHT_PURPLE), true);
      return 1;
   }

   private static int stop(CommandContext<CommandSourceStack> ctx) {
      boolean event = MultiverseCollapse.stop(ctx.getSource().getServer(), "multiverse.viltrumitecore.stopped");
      int runs = MultiverseBossRush.stopAll(ctx.getSource().getServer());
      int loose = MultiverseCleanup.discard(ctx.getSource().getServer(), fighter -> true);
      ctx.getSource().sendSuccess(() -> Component.translatable("multiverse.viltrumitecore.stop_report", event ? 1 : 0, runs, loose), true);
      return 1;
   }

   private static int status(CommandContext<CommandSourceStack> ctx) {
      MultiverseCollapse event = MultiverseCollapse.active();
      if (event == null) {
         ctx.getSource().sendSuccess(() -> Component.translatable("multiverse.viltrumitecore.none"), false);
      } else {
         int alive = event.aliveWave(ctx.getSource().getLevel());
         ctx.getSource().sendSuccess(() -> Component.translatable("multiverse.viltrumitecore.status", event.phase().ordinal() + 1, event.phase().name(), alive,
            event.modifiers().toString()), false);
      }

      return 1;
   }

   private static int bossRush(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer player = ctx.getSource().getPlayerOrException();
      if (!MultiverseBossRush.start(player)) {
         ctx.getSource().sendFailure(Component.translatable("multiverse.viltrumitecore.bossrush_already"));
         return 0;
      }

      return 1;
   }

   private static int boss(CommandContext<CommandSourceStack> ctx) {
      MultiverseBoss boss = MultiverseBoss.byId(StringArgumentType.getString(ctx, "id"));
      if (boss == null) {
         ctx.getSource().sendFailure(Component.translatable("multiverse.viltrumitecore.unknown_boss", StringArgumentType.getString(ctx, "id")));
         return 0;
      }

      net.minecraft.world.entity.Entity caller = ctx.getSource().getEntity();
      Vec3 at = caller == null ? ctx.getSource().getPosition() : caller.position().add(caller.getLookAngle().multiply(4.0, 0.0, 4.0)).add(0.0, 0.5, 0.0);
      MultiverseFighter fighter = MultiverseBosses.summon(ctx.getSource().getLevel(), at, boss);
      if (fighter == null) {
         return 0;
      }

      ctx.getSource().sendSuccess(() -> Component.translatable("multiverse.viltrumitecore.boss_arrives", Component.translatable(boss.langKey())), true);
      return 1;
   }

   private static int clash(CommandContext<CommandSourceStack> ctx) {
      BudgetFaction first = BudgetFaction.byName(StringArgumentType.getString(ctx, "first"));
      BudgetFaction second = BudgetFaction.byName(StringArgumentType.getString(ctx, "second"));
      if (first == null || second == null) {
         ctx.getSource().sendFailure(Component.translatable("multiverse.viltrumitecore.unknown_faction"));
         return 0;
      }

      ServerLevel level = ctx.getSource().getLevel();
      Vec3 center = ctx.getSource().getPosition();
      Random random = new Random();
      int spawned = 0;
      BudgetFaction[] sides = {first, second};
      for (int side = 0; side < 2; side++) {
         BudgetFaction faction = sides[side];
         List<BudgetHeroSpec> pool = BudgetHeroes.roster().stream().filter(spec -> spec.faction() == faction).toList();
         for (int i = 0; i < 6; i++) {
            Vec3 at = center.add(side == 0 ? -8.0 : 8.0, 0.5, (i - 2.5) * 1.5);
            if (MultiverseFighter.spawn(level, at, pool.get(random.nextInt(pool.size())), 0, null, 1.0F, 1.0F, 1.0F) != null) {
               spawned++;
            }
         }
      }

      int total = spawned;
      ctx.getSource().sendSuccess(() -> Component.translatable("multiverse.viltrumitecore.clash", Component.translatable(first.langKey()),
         Component.translatable(second.langKey()), total).withStyle(ChatFormatting.GOLD), true);
      return total;
   }
}
