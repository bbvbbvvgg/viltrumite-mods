package dev.baranhan.viltrumiteflight.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "viltrumiteflight"
)
public class ViltrumiteFlightCommand {
   @SubscribeEvent
   public static void onCommandRegister(RegisterCommandsEvent event) {
      CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("viltrumite").requires(source -> true))
            .then(
               ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("flight").requires(source -> source.hasPermission(2)))
                     .then(
                        Commands.argument("target", EntityArgument.player())
                           .executes(context -> executeToggleFlight((CommandSourceStack)context.getSource(), EntityArgument.getPlayer(context, "target")))
                     ))
                  .executes(context -> executeToggleFlight((CommandSourceStack)context.getSource(), ((CommandSourceStack)context.getSource()).getPlayerOrException()))
            )
      );
   }

   private static int executeToggleFlight(CommandSourceStack source, ServerPlayer target) {
      boolean currentFlightState = target.getAbilities().mayfly;
      target.getAbilities().mayfly = !currentFlightState;
      if (currentFlightState) {
         target.getAbilities().flying = false;
      }

      target.onUpdateAbilities();
      // Localized by viltrumitecore's lang (commands.viltrumiteflight.*); English fallback when it is absent.
      boolean on = !currentFlightState;
      Component state = Component.translatableWithFallback(on ? "commands.viltrumitecore.state.enabled" : "commands.viltrumitecore.state.disabled",
         on ? "ENABLED" : "DISABLED").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
      source.sendSystemMessage(Component.translatableWithFallback("commands.viltrumiteflight.flight.for", "Viltrumite flight for %s: %s",
         target.getDisplayName().copy().withStyle(ChatFormatting.YELLOW), state).withStyle(ChatFormatting.WHITE));
      target.displayClientMessage(Component.translatableWithFallback("commands.viltrumiteflight.flight.self", "Viltrumite flight powers %s", state)
         .withStyle(ChatFormatting.WHITE), true);
      return 1;
   }
}
