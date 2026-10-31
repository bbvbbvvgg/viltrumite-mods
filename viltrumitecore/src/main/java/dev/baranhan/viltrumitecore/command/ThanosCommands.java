package dev.baranhan.viltrumitecore.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosEvent;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosRules;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * {@code /thanos start} (operator: Thanos appears 12 blocks ahead of the caller), {@code /thanos
 * stop} (operator), {@code /thanos status} (everyone) and {@code /thanos skip <seconds>}
 * (operator, QA: leave that many seconds on the clock; 0 runs straight into the snap).
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class ThanosCommands {
   private ThanosCommands() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      event.getDispatcher().register(Commands.literal("thanos")
         .then(Commands.literal("start").requires(s -> s.hasPermission(2)).executes(ThanosCommands::start))
         .then(Commands.literal("stop").requires(s -> s.hasPermission(2)).executes(ThanosCommands::stop))
         .then(Commands.literal("status").executes(ThanosCommands::status))
         .then(Commands.literal("skip").requires(s -> s.hasPermission(2))
            .then(Commands.argument("seconds", IntegerArgumentType.integer(0, ThanosRules.DURATION_TICKS / ThanosRules.TPS))
               .executes(c -> skip(c, IntegerArgumentType.getInteger(c, "seconds"))))));
   }

   private static int start(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      ServerPlayer player = c.getSource().getPlayerOrException();
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

   private static int skip(CommandContext<CommandSourceStack> c, int seconds) {
      if (!ThanosEvent.skipTo(seconds)) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.thanos.not_fighting"));
         return 0;
      }

      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.thanos.skipped", ThanosRules.clock(seconds * ThanosRules.TPS)), true);
      return 1;
   }
}
