package dev.baranhan.viltrumitecore.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.HeroLineS2CPacket;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Operator-only (level 2) preview of the cinematic hero subtitles: play any
 * registered line, the short/long format samples or literal text, and stop.
 * Uses the production path ({@link HeroSpeech}); gameplay is untouched.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class HeroLineCommands {
   private static final HeroLine PREVIEW_SHORT = HeroLine.builder("viltrumitecore:preview_short")
      .text("subtitle.viltrumitecore.preview.short")
      .cooldown(0)
      .build();
   private static final HeroLine PREVIEW_LONG = HeroLine.builder("viltrumitecore:preview_long")
      .textSeries("subtitle.viltrumitecore.preview.long.", 2)
      .cooldown(0)
      .build();

   private HeroLineCommands() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      event.getDispatcher().register(Commands.literal("herolines").requires(source -> source.hasPermission(2))
         .then(Commands.literal("play")
            .then(Commands.argument("line", ResourceLocationArgument.id())
               .suggests((context, builder) -> SharedSuggestionProvider.suggest(HeroSpeech.allLineIds(), builder))
               .executes(context -> play(context, context.getSource().getPlayerOrException()))
               .then(Commands.argument("speaker", EntityArgument.player())
                  .executes(context -> play(context, EntityArgument.getPlayer(context, "speaker"))))))
         .then(Commands.literal("preview")
            .then(Commands.literal("short").executes(context -> preview(context.getSource(), PREVIEW_SHORT)))
            .then(Commands.literal("long").executes(context -> preview(context.getSource(), PREVIEW_LONG))))
         .then(Commands.literal("say")
            .then(Commands.argument("text", StringArgumentType.greedyString())
               .executes(context -> say(context.getSource(), StringArgumentType.getString(context, "text")))))
         .then(Commands.literal("stop").executes(context -> stop(context.getSource()))));
   }

   private static int play(CommandContext<CommandSourceStack> context, ServerPlayer speaker) {
      String id = ResourceLocationArgument.getId(context, "line").toString();
      Optional<HeroSpeech.Owned> owned = HeroSpeech.find(id);
      if (owned.isEmpty()) {
         context.getSource().sendFailure(Component.translatable("commands.viltrumitecore.herolines.unknown", id));
         return 0;
      }

      return report(context.getSource(), HeroSpeech.play(speaker, owned.get().line(), owned.get().hero(), true), id);
   }

   private static int preview(CommandSourceStack source, HeroLine line) throws CommandSyntaxException {
      ServerPlayer speaker = source.getPlayerOrException();
      return report(source, HeroSpeech.play(speaker, line, HeroSpeech.voiceOf(speaker), true), line.id());
   }

   private static int say(CommandSourceStack source, String text) throws CommandSyntaxException {
      return report(source, HeroSpeech.sayLiteral(source.getPlayerOrException(), text, HeroLine.Priority.ABILITY), HeroSpeech.LITERAL_LINE);
   }

   private static int stop(CommandSourceStack source) throws CommandSyntaxException {
      ServerPlayer player = source.getPlayerOrException();
      HeroSpeech.interrupt(player, null);
      CoreMessages.sendToPlayer(HeroLineS2CPacket.clear(), player);
      source.sendSuccess(() -> Component.translatable("commands.viltrumitecore.herolines.stopped"), false);
      return 1;
   }

   private static int report(CommandSourceStack source, boolean played, String id) {
      if (!played) {
         source.sendFailure(Component.translatable("commands.viltrumitecore.herolines.skipped", id));
         return 0;
      }

      source.sendSuccess(() -> Component.translatable("commands.viltrumitecore.herolines.played", id), false);
      return 1;
   }
}
