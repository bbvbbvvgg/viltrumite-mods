package dev.baranhan.viltrumitecore.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.baranhan.viltrumitecore.worldevent.WorldEvent;
import dev.baranhan.viltrumitecore.worldevent.WorldEventManager;
import dev.baranhan.viltrumitecore.worldevent.WorldEventType;
import dev.baranhan.viltrumitecore.worldevent.WorldEvents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class WorldEventCommands {
   private static final SuggestionProvider<CommandSourceStack> EVENT_IDS = (context, builder) -> SharedSuggestionProvider.suggest(WorldEvents.ids(), builder);

   public static LiteralArgumentBuilder<CommandSourceStack> build() {
      return (LiteralArgumentBuilder<CommandSourceStack>)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                        "event"
                     )
                     .requires(source -> source.hasPermission(2)))
                  .then(
                     Commands.literal("start")
                        .then(
                           ((RequiredArgumentBuilder)Commands.argument("id", StringArgumentType.word())
                                 .suggests(EVENT_IDS)
                                 .executes(ctx -> start((CommandSourceStack)ctx.getSource(), StringArgumentType.getString(ctx, "id"), -1)))
                              .then(
                                 Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                                    .executes(
                                       ctx -> start(
                                             (CommandSourceStack)ctx.getSource(),
                                             StringArgumentType.getString(ctx, "id"),
                                             IntegerArgumentType.getInteger(ctx, "seconds")
                                          )
                                    )
                              )
                        )
                  ))
               .then(
                  Commands.literal("stop")
                     .then(
                        Commands.argument("id", StringArgumentType.word())
                           .suggests(EVENT_IDS)
                           .executes(ctx -> stop((CommandSourceStack)ctx.getSource(), StringArgumentType.getString(ctx, "id")))
                     )
               ))
            .then(Commands.literal("stopall").executes(ctx -> {
               ServerLevel level = ((CommandSourceStack)ctx.getSource()).getLevel();
               WorldEventManager.get(level).stopAll(level);
               ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.translatable("commands.viltrumitecore.event.stopall").withStyle(ChatFormatting.WHITE), true);
               return 1;
            })))
         .then(Commands.literal("list").executes(ctx -> list((CommandSourceStack)ctx.getSource())));
   }

   private static int start(CommandSourceStack source, String id, int seconds) {
      WorldEventType type = WorldEvents.byId(id);
      if (type == null) {
         source.sendFailure(Component.translatable("commands.viltrumitecore.event.unknown", id).withStyle(ChatFormatting.RED));
         return 0;
      } else {
         ServerLevel level = source.getLevel();
         WorldEventManager manager = WorldEventManager.get(level);
         if (manager.isRunning(type)) {
            source.sendFailure(Component.translatable("commands.viltrumitecore.event.already_running", id).withStyle(ChatFormatting.RED));
            return 0;
         } else {
            int warmup = seconds >= 0 ? seconds * 20 : type.defaultWarmupTicks();
            Vec3 origin = source.getPosition();
            manager.startManual(level, type, origin, warmup);
            source.sendSuccess(() -> Component.translatable("commands.viltrumitecore.event.started", id, warmup / 20).withStyle(ChatFormatting.WHITE), true);
            return 1;
         }
      }
   }

   private static int stop(CommandSourceStack source, String id) {
      WorldEventType type = WorldEvents.byId(id);
      if (type == null) {
         source.sendFailure(Component.translatable("commands.viltrumitecore.event.unknown", id).withStyle(ChatFormatting.RED));
         return 0;
      } else {
         ServerLevel level = source.getLevel();
         if (!WorldEventManager.get(level).stop(level, type)) {
            source.sendFailure(Component.translatable("commands.viltrumitecore.event.not_running", id).withStyle(ChatFormatting.RED));
            return 0;
         } else {
            source.sendSuccess(() -> Component.translatable("commands.viltrumitecore.event.stopped", id).withStyle(ChatFormatting.WHITE), true);
            return 1;
         }
      }
   }

   private static int list(CommandSourceStack source) {
      ServerLevel level = source.getLevel();
      List<WorldEvent> events = WorldEventManager.get(level).activeEvents();
      if (events.isEmpty()) {
         source.sendSuccess(() -> Component.translatable("commands.viltrumitecore.event.none").withStyle(ChatFormatting.GRAY), false);
         return 0;
      } else {
         for (WorldEvent event : events) {
            Component state = event.hasBegun()
               ? Component.translatable("commands.viltrumitecore.event.state.active").withStyle(ChatFormatting.GREEN)
               : Component.translatable("commands.viltrumitecore.event.state.warmup", Math.round((float)event.warmupTicks() / 20.0F)).withStyle(ChatFormatting.YELLOW);
            source.sendSuccess(() -> Component.translatable("commands.viltrumitecore.event.entry", event.type().id(), state).withStyle(ChatFormatting.WHITE), false);
         }

         return events.size();
      }
   }

   private WorldEventCommands() {
   }
}
