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
               ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("\u00a7fAll world events stopped."), true);
               return 1;
            })))
         .then(Commands.literal("list").executes(ctx -> list((CommandSourceStack)ctx.getSource())));
   }

   private static int start(CommandSourceStack source, String id, int seconds) {
      WorldEventType type = WorldEvents.byId(id);
      if (type == null) {
         source.sendFailure(Component.literal("\u00a7cUnknown event: " + id));
         return 0;
      } else {
         ServerLevel level = source.getLevel();
         WorldEventManager manager = WorldEventManager.get(level);
         if (manager.isRunning(type)) {
            source.sendFailure(Component.literal("\u00a7c'" + id + "' is already running."));
            return 0;
         } else {
            int warmup = seconds >= 0 ? seconds * 20 : type.defaultWarmupTicks();
            Vec3 origin = source.getPosition();
            manager.startManual(level, type, origin, warmup);
            source.sendSuccess(() -> Component.literal("\u00a7fStarted '" + id + "' \u00a77(" + warmup / 20 + "s countdown)"), true);
            return 1;
         }
      }
   }

   private static int stop(CommandSourceStack source, String id) {
      WorldEventType type = WorldEvents.byId(id);
      if (type == null) {
         source.sendFailure(Component.literal("\u00a7cUnknown event: " + id));
         return 0;
      } else {
         ServerLevel level = source.getLevel();
         if (!WorldEventManager.get(level).stop(level, type)) {
            source.sendFailure(Component.literal("\u00a7c'" + id + "' is not running."));
            return 0;
         } else {
            source.sendSuccess(() -> Component.literal("\u00a7fStopped '" + id + "'."), true);
            return 1;
         }
      }
   }

   private static int list(CommandSourceStack source) {
      ServerLevel level = source.getLevel();
      List<WorldEvent> events = WorldEventManager.get(level).activeEvents();
      if (events.isEmpty()) {
         source.sendSuccess(() -> Component.literal("\u00a77No active world events."), false);
         return 0;
      } else {
         for (WorldEvent event : events) {
            String state = event.hasBegun() ? "\u00a7aactive" : "\u00a7e" + Math.round((float)event.warmupTicks() / 20.0F) + "s left";
            source.sendSuccess(() -> Component.literal("\u00a7f- " + event.type().id() + " \u00a77[" + state + "\u00a77]"), false);
         }

         return events.size();
      }
   }

   private WorldEventCommands() {
   }
}
