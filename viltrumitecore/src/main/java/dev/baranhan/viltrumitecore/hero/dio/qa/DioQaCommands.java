package dev.baranhan.viltrumitecore.hero.dio.qa;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.baranhan.viltrumitecore.hero.dio.DioRules;
import dev.baranhan.viltrumitecore.hero.dio.DioState;
import dev.baranhan.viltrumitecore.hero.dio.DioTimeStop;
import java.util.Arrays;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Opt-in operator QA harness ({@code -Dviltrumitecore.dio.qa=true}): set blood,
 * clear cooldowns, print state. Absent in a normal game.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class DioQaCommands {
   public static final boolean ENABLED = Boolean.getBoolean("viltrumitecore.dio.qa");

   private DioQaCommands() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      if (!ENABLED) {
         return;
      }

      event.getDispatcher().register(Commands.literal("dioqa").requires(s -> s.hasPermission(2))
         .then(Commands.literal("blood").then(Commands.argument("amount", IntegerArgumentType.integer(0, DioRules.BLOOD_MAX))
            .executes(c -> blood(c.getSource(), IntegerArgumentType.getInteger(c, "amount")))))
         .then(Commands.literal("reset").executes(c -> reset(c.getSource())))
         .then(Commands.literal("state").executes(c -> state(c.getSource()))));
   }

   private static DioState state(ServerPlayer player) {
      return DioState.of(player);
   }

   private static int blood(CommandSourceStack source, int amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      DioState state = state(source.getPlayerOrException());
      if (state == null) {
         return 0;
      }

      state.blood = DioRules.clampBlood(amount);
      source.sendSuccess(() -> Component.literal("DIO blood = " + state.blood), false);
      return 1;
   }

   private static int reset(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      DioState state = state(source.getPlayerOrException());
      if (state == null) {
         return 0;
      }

      Arrays.fill(state.cooldowns, 0);
      source.sendSuccess(() -> Component.literal("DIO cooldowns cleared"), false);
      return 1;
   }

   private static int state(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      ServerPlayer player = source.getPlayerOrException();
      DioState state = state(player);
      if (state == null) {
         return 0;
      }

      String text = "DIO blood=" + state.blood + " stand=" + state.standPhase + " action=" + state.action + "@" + state.elapsed
         + " cds=" + Arrays.toString(state.cooldowns) + " field=" + (DioTimeStop.fieldOf(player.getUUID()) != null) + " sun=" + state.inSun;
      source.sendSuccess(() -> Component.literal(text), false);
      return 1;
   }
}
