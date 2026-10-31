package dev.baranhan.viltrumitecore.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.baranhan.viltrumitecore.worldevent.WorldEventConflicts;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosEvent;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosMove;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosRules;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * {@code /thanos start [force]} (operator: Thanos appears 12 blocks ahead of the caller; refused
 * while the Chitauri invasion runs unless {@code force}, see WorldEventConflicts), {@code /thanos
 * stop} (operator), {@code /thanos status} (everyone), {@code /thanos skip <seconds>}
 * (operator, QA: leave that many seconds on the clock; 0 runs straight into the snap) and
 * {@code /thanos move <move>} (operator, QA: the named move is his next one, past the brain).
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class ThanosCommands {
   private ThanosCommands() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      event.getDispatcher().register(Commands.literal("thanos")
         .then(Commands.literal("start").requires(s -> s.hasPermission(2)).executes(c -> start(c, false))
            .then(Commands.literal("force").executes(c -> start(c, true))))
         .then(Commands.literal("stop").requires(s -> s.hasPermission(2)).executes(ThanosCommands::stop))
         .then(Commands.literal("status").executes(ThanosCommands::status))
         .then(Commands.literal("skip").requires(s -> s.hasPermission(2))
            .then(Commands.argument("seconds", IntegerArgumentType.integer(0, ThanosRules.DURATION_TICKS / ThanosRules.TPS))
               .executes(c -> skip(c, IntegerArgumentType.getInteger(c, "seconds")))))
         .then(Commands.literal("move").requires(s -> s.hasPermission(2))
            .then(Commands.argument("move", StringArgumentType.word())
               .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(ThanosMove.values()).map(m -> m.name().toLowerCase(Locale.ROOT)), b))
               .executes(c -> move(c, StringArgumentType.getString(c, "move"))))));
   }

   private static int start(CommandContext<CommandSourceStack> c, boolean force) throws CommandSyntaxException {
      ServerPlayer player = c.getSource().getPlayerOrException();
      // the Chitauri invasion owns the city: no Thanos over it without an explicit force
      List<Component> conflicts = WorldEventConflicts.invasion(player.serverLevel());
      if (!ThanosEvent.running() && !WorldEventConflicts.mayStart(conflicts.size(), force)) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.chitauri.conflict", ComponentUtils.formatList(conflicts, Component.literal(", "))));
         return 0;
      }

      ThanosEvent.StartResult result = ThanosEvent.start(player);
      switch (result) {
         case STARTED -> {
            c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.thanos.started").withStyle(ChatFormatting.DARK_PURPLE), true);
            if (player.isCreative() || player.isSpectator()) {
               c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.thanos.creative_hint").withStyle(ChatFormatting.GRAY), false);
            }

            return 1;
         }
         case ALREADY -> c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.thanos.already"));
         case PAUSED -> c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.thanos.paused"));
         case NO_SPACE -> c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.thanos.no_space"));
      }

      return 0;
   }

   private static int stop(CommandContext<CommandSourceStack> c) {
      if (!ThanosEvent.stop()) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.thanos.not_running"));
         return 0;
      }

      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.thanos.stopped"), true);
      return 1;
   }

   private static int status(CommandContext<CommandSourceStack> c) {
      ThanosEvent e = ThanosEvent.active();
      if (e == null || !ThanosEvent.running()) {
         c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.thanos.none").withStyle(ChatFormatting.GRAY), false);
         return 0;
      }

      float max = Math.max(1.0F, e.thanos().getMaxHealth());
      String health = String.format(Locale.ROOT, "%.0f/%.0f (%.0f%%)", e.thanos().getHealth(), max, 100.0F * e.thanos().getHealth() / max);
      Component stage = Component.translatable("commands.viltrumitecore.thanos.stage." + e.stage().name().toLowerCase(Locale.ROOT));
      Component phase = Component.translatable("commands.viltrumitecore.thanos.phase." + e.phase().name().toLowerCase(Locale.ROOT));
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.thanos.status", stage, ThanosRules.clock(e.clock().remaining()), health,
         e.heroes().size(), phase, e.movesStarted()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
      return 1;
   }

   private static int move(CommandContext<CommandSourceStack> c, String name) {
      ThanosMove move = null;
      for (ThanosMove m : ThanosMove.values()) {
         if (m.name().equalsIgnoreCase(name)) {
            move = m;
         }
      }

      if (move == null || !ThanosEvent.queueMove(move)) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.thanos.not_fighting"));
         return 0;
      }

      Component label = Component.translatable("hud.viltrumitecore.thanos.move." + move.name().toLowerCase(Locale.ROOT));
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.thanos.queued", label), true);
      return 1;
   }

   private static int skip(CommandContext<CommandSourceStack> c, int seconds) {
      if (!ThanosEvent.skipTo(seconds)) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.thanos.not_fighting"));
         return 0;
      }

      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.thanos.skipped", ThanosRules.clock(seconds * ThanosRules.TPS)), true);
      return 1;
   }
}
