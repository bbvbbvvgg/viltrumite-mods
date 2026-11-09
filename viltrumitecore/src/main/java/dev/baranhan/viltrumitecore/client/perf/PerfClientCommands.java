package dev.baranhan.viltrumitecore.client.perf;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.baranhan.viltrumitecore.config.ViltrumiteClientConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/** {@code /vfx} — this client's effect quality ({@code quality low|medium|high|ultra}) and overlay ({@code overlay}). */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class PerfClientCommands {
   private PerfClientCommands() {
   }

   @SubscribeEvent
   public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
      List<String> levels = new ArrayList<>();
      for (EffectQuality quality : EffectQuality.values()) {
         levels.add(quality.id());
      }

      event.getDispatcher().register(Commands.literal("vfx")
         .executes(PerfClientCommands::show)
         .then(Commands.literal("quality").executes(PerfClientCommands::show)
            .then(Commands.argument("level", StringArgumentType.word())
               .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(levels, builder))
               .executes(PerfClientCommands::set)))
         .then(Commands.literal("overlay").executes(ctx -> {
            PerfClient.setOverlay(!PerfClient.overlay());
            ctx.getSource().sendSuccess(() -> PerfClient.overlay()
               ? Component.translatableWithFallback("viltrumitecore.perf.overlay.on", "Performance overlay on")
               : Component.translatableWithFallback("viltrumitecore.perf.overlay.off", "Performance overlay off"), false);
            return 1;
         })));
   }

   private static int show(CommandContext<CommandSourceStack> ctx) {
      EffectQuality quality = EffectQuality.parse(ViltrumiteClientConfig.INSTANCE.effectQuality, EffectQuality.HIGH);
      ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("viltrumitecore.perf.quality.current",
         "Effect quality: %s. Particles %s of %s live, %s/s spawned, %s/s thinned, %s/s over budget",
         quality.id(), ParticleBudget.live(), ParticleBudget.liveLimit(), ParticleBudget.admittedPerSecond(),
         ParticleBudget.thinnedPerSecond(), ParticleBudget.overBudgetPerSecond()), false);
      return 1;
   }

   private static int set(CommandContext<CommandSourceStack> ctx) {
      String wanted = StringArgumentType.getString(ctx, "level");
      EffectQuality quality = EffectQuality.parse(wanted, null);
      if (quality == null) {
         ctx.getSource().sendFailure(Component.translatableWithFallback("viltrumitecore.perf.quality.unknown",
            "Unknown quality %s: low, medium, high or ultra", wanted));
         return 0;
      }

      ViltrumiteClientConfig.INSTANCE.effectQuality = quality.id();
      ViltrumiteClientConfig.save();
      ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("viltrumitecore.perf.quality.set",
         "Effect quality set to %s", quality.id()), false);
      return 1;
   }
}
