package dev.baranhan.viltrumitecore.hero.thor.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.baranhan.viltrumitecore.hero.thor.HammerState;
import dev.baranhan.viltrumitecore.hero.thor.ThorMode;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.HammerView;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Dev-only {@code /thor} fixtures for the Thor sessions: registered only
 * outside production (gradle runClient / runServer, tests) and only for
 * operators (level 2). They set bridge state directly; they never start a
 * move, spawn a hammer or stand in for a mechanic.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class ThorDevCommands {
   public static final int PERMISSION = 2;

   private ThorDevCommands() {
   }

   /** Pure gate: dev / test environment only. */
   public static boolean enabled(boolean production) {
      return !production;
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      if (!enabled(FMLEnvironment.production)) {
         return;
      }

      event.getDispatcher().register(Commands.literal("thor").requires(source -> source.hasPermission(PERMISSION))
         .then(Commands.literal("state").executes(ThorDevCommands::state))
         .then(Commands.literal("hammer").then(Commands.argument("place", StringArgumentType.word())
            .executes(c -> hammer(c, StringArgumentType.getString(c, "place")))))
         .then(Commands.literal("mode").then(Commands.argument("mode", StringArgumentType.word())
            .executes(c -> mode(c, StringArgumentType.getString(c, "mode")))))
         .then(Commands.literal("grab_window").then(Commands.argument("ticks", IntegerArgumentType.integer(1, 200))
            .executes(c -> grabWindow(c, IntegerArgumentType.getInteger(c, "ticks")))))
         .then(Commands.literal("unlock_scene").executes(ThorDevCommands::unlockScene)));
   }

   private static ThorState thor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = context.getSource().getPlayerOrException();
      ThorState state = ThorState.of(player);
      if (state == null) {
         context.getSource().sendFailure(Component.literal("Not Thor"));
      }

      return state;
   }

   private static int state(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ThorState state = thor(context);
      if (state == null) {
         return 0;
      }

      HammerView view = ThorBridge.view(state);
      context.getSource().sendSuccess(() -> Component.literal(view.toString()), false);
      return 1;
   }

   private static int hammer(CommandContext<CommandSourceStack> context, String key) throws CommandSyntaxException {
      ThorState state = thor(context);
      if (state == null) {
         return 0;
      }

      HammerState place = HammerState.fromKey(key.toLowerCase(Locale.ROOT));
      state.hammer = place;
      context.getSource().sendSuccess(() -> Component.literal("hammer = " + place.key()), false);
      return 1;
   }

   private static int mode(CommandContext<CommandSourceStack> context, String key) throws CommandSyntaxException {
      ThorState state = thor(context);
      if (state == null) {
         return 0;
      }

      state.mode = "electric".equalsIgnoreCase(key) ? ThorMode.ELECTRIC : ThorMode.NORMAL;
      context.getSource().sendSuccess(() -> Component.literal("mode = " + state.mode), false);
      return 1;
   }

   private static int grabWindow(CommandContext<CommandSourceStack> context, int ticks) throws CommandSyntaxException {
      ThorState state = thor(context);
      if (state == null) {
         return 0;
      }

      ThorBridge.openGrabWindow(state, ticks);
      context.getSource().sendSuccess(() -> Component.literal("grab window = " + state.grabWindow), false);
      return 1;
   }

   private static int unlockScene(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ThorState state = thor(context);
      if (state == null) {
         return 0;
      }

      state.sceneLock = null;
      context.getSource().sendSuccess(() -> Component.literal("scene unlocked"), false);
      return 1;
   }
}
