package dev.baranhan.viltrumitecore.hero.thor.combat;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.baranhan.viltrumitecore.hero.thor.ThorActions;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Dev-only {@code /thor debug combat ...} (operators, never in production;
 * ThorDevCommands): test fixtures for session B without A / C. {@code
 * melee} injects the combat intent of the MMB melee branch into the
 * registered HAMMER_STRIKE handler (not a second MMB resolver: no enemy
 * search, no context choice); {@code action} feeds the electric recognizer
 * an A-style throw / recall action without posting bridge events.
 */
public final class ThorCombatCommands {
   private ThorCombatCommands() {
   }

   public static LiteralArgumentBuilder<CommandSourceStack> build(LiteralArgumentBuilder<CommandSourceStack> node) {
      return node
         .then(Commands.literal("melee").executes(ThorCombatCommands::melee))
         .then(Commands.literal("state").executes(ThorCombatCommands::state))
         .then(Commands.literal("reset_cooldowns").executes(ThorCombatCommands::resetCooldowns))
         .then(Commands.literal("sky").executes(ThorCombatCommands::sky))
         .then(Commands.literal("action").then(Commands.argument("kind", StringArgumentType.word())
            .executes(c -> action(c, StringArgumentType.getString(c, "kind")))));
   }

   private static ThorState thor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = context.getSource().getPlayerOrException();
      ThorState state = ThorState.of(player);
      if (state == null) {
         context.getSource().sendFailure(Component.literal("Not Thor"));
      }

      return state;
   }

   private static int melee(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ThorState state = thor(context);
      if (state == null) {
         return 0;
      }

      ServerPlayer player = context.getSource().getPlayerOrException();
      ThorActions.Refusal refusal = ThorBridge.start(player, state, ThorMove.HAMMER_STRIKE, -1);
      if (refusal != ThorActions.Refusal.NONE) {
         context.getSource().sendFailure(Component.literal("HAMMER_STRIKE refused: " + refusal));
         return 0;
      }

      context.getSource().sendSuccess(() -> Component.literal("HAMMER_STRIKE injected"), false);
      return 1;
   }

   private static int state(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ThorState state = thor(context);
      if (state == null) {
         return 0;
      }

      ServerPlayer player = context.getSource().getPlayerOrException();
      ThorCombatState combat = ThorCombatState.of(state);
      String text = "move=" + state.timeline.move() + " step=" + state.timeline.step() + " pose=" + state.combatPose()
         + " chain=" + combat.chainMove + (combat.chainRoute == null ? "" : "/" + combat.chainRoute.id()) + " " + combat.chainButtons
         + " window=" + ThorBridge.comboWindow(state) + " mode=" + state.mode() + " hammer=" + ThorBridge.hammer(player)
         + " actions=" + combat.recognizer.history().stream().map(a -> a.kind() + ":" + a.direction()).toList()
         + " storm=" + combat.cooldownLeft(context.getSource().getPlayer(), ThorCombatState.Cooldown.STORM) + " wrath=" + combat.cooldownLeft(context.getSource().getPlayer(), ThorCombatState.Cooldown.WRATH)
         + " policy=" + ThorCombatConfig.get().skyTargetPolicy;
      context.getSource().sendSuccess(() -> Component.literal(text), false);
      return 1;
   }

   private static int resetCooldowns(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ThorState state = thor(context);
      if (state == null) {
         return 0;
      }

      ThorCombatState.of(state).clearCooldowns(context.getSource().getPlayerOrException());
      context.getSource().sendSuccess(() -> Component.literal("cooldowns cleared"), false);
      return 1;
   }

   private static int sky(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ThorState state = thor(context);
      if (state == null) {
         return 0;
      }

      ServerPlayer player = context.getSource().getPlayerOrException();
      ThorCombatState combat = ThorCombatState.of(state);
      ElectricCombos.skyStrike(player, combat, null, ElectricFx.aim(player, 64.0), combat.nextAttackId());
      return 1;
   }

   private static int action(CommandContext<CommandSourceStack> context, String kind) throws CommandSyntaxException {
      ThorState state = thor(context);
      if (state == null) {
         return 0;
      }

      ServerPlayer player = context.getSource().getPlayerOrException();
      ElectricAction.Kind parsed;
      switch (kind.toLowerCase(Locale.ROOT)) {
         case "throw" -> parsed = ElectricAction.Kind.MMB_THROW;
         case "recall" -> parsed = ElectricAction.Kind.MMB_RECALL;
         default -> {
            context.getSource().sendFailure(Component.literal("throw | recall"));
            return 0;
         }
      }

      ThorCombatState combat = ThorCombatState.of(state);
      combat.actionPrimary.clear();
      combat.actionAll.clear();
      ElectricCombos.push(player, state, combat, parsed, MoveDirection.classify(combat.velocity.x, combat.velocity.z, player.getYRot()),
         !player.onGround(), parsed == ElectricAction.Kind.MMB_THROW || CombatHits.hammerInHand(player));
      context.getSource().sendSuccess(() -> Component.literal("action " + parsed), false);
      return 1;
   }
}
