package dev.baranhan.viltrumitecore.hero.dio.qa;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.dio.DioRules;
import dev.baranhan.viltrumitecore.hero.dio.DioState;
import dev.baranhan.viltrumitecore.hero.dio.DioTimeStop;
import java.util.Arrays;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Opt-in operator QA harness ({@code -Dviltrumitecore.dio.qa=true}): become DIO,
 * press/release an ability, spawn a still target, set blood, clear cooldowns,
 * print state. Absent in a normal game.
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
         .then(Commands.literal("become").executes(c -> become(c.getSource())))
         .then(Commands.literal("dummy").executes(c -> dummy(c.getSource())))
         .then(Commands.literal("act").then(Commands.argument("action", StringArgumentType.word())
            .executes(c -> act(c.getSource(), StringArgumentType.getString(c, "action"), true))
            .then(Commands.argument("pressed", BoolArgumentType.bool())
               .executes(c -> act(c.getSource(), StringArgumentType.getString(c, "action"), BoolArgumentType.getBool(c, "pressed"))))))
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

   private static int become(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      ServerPlayer player = source.getPlayerOrException();
      HeroRegistry.changeHero(player, HeroId.DIO);
      DioState.ensure(player).blood = DioRules.BLOOD_MAX;
      source.sendSuccess(() -> Component.literal("You are DIO"), false);
      return 1;
   }

   /** A still, silent zombie four blocks ahead: a target for combo / knife / drain screenshots. */
   private static int dummy(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      ServerPlayer player = source.getPlayerOrException();
      Mob mob = EntityType.ZOMBIE.create(player.serverLevel());
      if (mob == null) {
         return 0;
      }

      Vec3 at = player.position().add(Vec3.directionFromRotation(0.0F, player.getYRot()).scale(4.0));
      mob.moveTo(at.x, at.y, at.z, player.getYRot() + 180.0F, 0.0F);
      mob.setNoAi(true);
      mob.setSilent(true);
      mob.setPersistenceRequired();
      player.serverLevel().addFreshEntity(mob);
      return 1;
   }

   private static int act(CommandSourceStack source, String name, boolean pressed) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      ServerPlayer player = source.getPlayerOrException();
      HeroAction action;
      try {
         action = HeroAction.valueOf(name.toUpperCase(java.util.Locale.ROOT));
      } catch (IllegalArgumentException e) {
         source.sendFailure(Component.literal("Unknown action " + name));
         return 0;
      }

      HeroRegistry.get(player).handleInput(player, action, pressed);
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
