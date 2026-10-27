package dev.baranhan.viltrumitecore.hero.thor.scene;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.baranhan.viltrumitecore.hero.thor.ThorActions;
import dev.baranhan.viltrumitecore.hero.thor.ThorHands;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import dev.baranhan.viltrumitecore.hero.thor.dev.ThorDevCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Dev-only children of the shared {@code /thor} root (ThorDevCommands: dev
 * environment and operators only). They start C's scenes without B and
 * without a lethal hit, for visual / network checks; a dev fatality is a
 * rehearsal and never kills.
 * <ul>
 *   <li>{@code /thor grab <target>} — hold the target at once;</li>
 *   <li>{@code /thor fatality <target> <1-3>} — play scene 1-3 on any living target;</li>
 *   <li>{@code /thor debug grab-window <target>} — open the visual window on the target (then MMB grabs);</li>
 *   <li>{@code /thor debug scene-config [key value]} — list / set the PLACEHOLDER values;</li>
 *   <li>{@code /thor debug scene-stop} — end every scene of the caller.</li>
 * </ul>
 */
final class ThorSceneCommands {
   private ThorSceneCommands() {
   }

   static void register() {
      ThorDevCommands.registerChild(ThorScenes.OWNER, "grab", node -> node.then(Commands.argument("target", EntityArgument.entity())
         .executes(c -> grab(c, EntityArgument.getEntity(c, "target")))));
      ThorDevCommands.registerChild(ThorScenes.OWNER, "fatality", node -> node.then(Commands.argument("target", EntityArgument.entity())
         .then(Commands.argument("scene", IntegerArgumentType.integer(1, 3))
            .executes(c -> fatality(c, EntityArgument.getEntity(c, "target"), IntegerArgumentType.getInteger(c, "scene"))))));
      ThorDevCommands.registerChild(ThorScenes.OWNER, "debug grab-window", node -> node.then(Commands.argument("target", EntityArgument.entity())
         .executes(c -> grabWindow(c, EntityArgument.getEntity(c, "target")))));
      ThorDevCommands.registerChild(ThorScenes.OWNER, "debug scene-config", node -> node.executes(ThorSceneCommands::listConfig)
         .then(Commands.argument("key", StringArgumentType.word()).then(Commands.argument("value", StringArgumentType.word())
            .executes(c -> setConfig(c, StringArgumentType.getString(c, "key"), StringArgumentType.getString(c, "value"))))));
      ThorDevCommands.registerChild(ThorScenes.OWNER, "debug scene-stop", node -> node.executes(ThorSceneCommands::stop));
   }

   private static ThorState thor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = context.getSource().getPlayerOrException();
      ThorState state = ThorState.of(player);
      if (state == null) {
         context.getSource().sendFailure(Component.literal("Not Thor"));
      }
      return state;
   }

   private static int grab(CommandContext<CommandSourceStack> context, Entity entity) throws CommandSyntaxException {
      ThorState state = thor(context);
      ServerPlayer player = context.getSource().getPlayerOrException();
      if (state == null || !(entity instanceof LivingEntity target)) {
         return fail(context, "needs Thor and a living target");
      }

      String refusal = GrabSessions.refusal(player, target);
      if (refusal != null || ThorBridge.sceneLocked(state)) {
         return fail(context, "cannot grab: " + (refusal == null ? "scene running" : refusal));
      }

      if (ThorActions.start(state, null, ThorScenes.GRAB, ThorHands.free(player), target.getId()) != ThorActions.Refusal.NONE) {
         return fail(context, "the timeline refused GRAB");
      }

      if (!GrabSessions.start(player, state, target)) {
         ThorScenes.stopMove(state, ThorScenes.GRAB);
         return fail(context, "the shared control refused the target");
      }

      context.getSource().sendSuccess(() -> Component.literal("grab: " + target.getName().getString()), false);
      return 1;
   }

   private static int fatality(CommandContext<CommandSourceStack> context, Entity entity, int number) throws CommandSyntaxException {
      ThorState state = thor(context);
      ServerPlayer player = context.getSource().getPlayerOrException();
      FatalityScene scene = FatalityScene.byNumber(number);
      if (state == null || scene == null || !(entity instanceof LivingEntity target) || !target.isAlive() || target == player) {
         return fail(context, "needs Thor, a scene 1-3 and another living target");
      }

      if (ThorBridge.sceneLocked(state) || ThorScenes.inScene(target.getUUID())) {
         return fail(context, "a scene is running");
      }

      if (!FatalitySessions.start(player, state, target, scene, true)) {
         return fail(context, "no room for scene " + number + " here (the release path would kill normally)");
      }

      context.getSource().sendSuccess(() -> Component.literal("fatality " + number + " (rehearsal, no kill): " + target.getName().getString()), false);
      return 1;
   }

   private static int grabWindow(CommandContext<CommandSourceStack> context, Entity entity) throws CommandSyntaxException {
      ThorState state = thor(context);
      if (state == null || !(entity instanceof LivingEntity target)) {
         return fail(context, "needs Thor and a living target");
      }

      ThorBridge.openGrabWindow(state, ThorSceneConfig.grabWindowTicks, target.getId());
      context.getSource().sendSuccess(() -> Component.literal("grab window " + ThorSceneConfig.grabWindowTicks + " ticks on " + target.getName().getString()),
         false);
      return 1;
   }

   private static int listConfig(CommandContext<CommandSourceStack> context) {
      StringBuilder text = new StringBuilder("PLACEHOLDER values:");
      ThorSceneConfig.values().forEach((key, value) -> text.append("\n ").append(key).append(" = ").append(value));
      context.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
      return 1;
   }

   private static int setConfig(CommandContext<CommandSourceStack> context, String key, String value) {
      if (!ThorSceneConfig.set(key, value)) {
         return fail(context, "unknown key or bad value: " + key + " " + value);
      }

      context.getSource().sendSuccess(() -> Component.literal(key + " = " + ThorSceneConfig.values().get(key)), false);
      return 1;
   }

   private static int stop(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = context.getSource().getPlayerOrException();
      ThorScenes.endAll(player);
      context.getSource().sendSuccess(() -> Component.literal("scenes ended"), false);
      return 1;
   }

   private static int fail(CommandContext<CommandSourceStack> context, String message) {
      context.getSource().sendFailure(Component.literal(message));
      return 0;
   }
}
