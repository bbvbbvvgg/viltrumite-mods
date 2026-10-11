package dev.baranhan.viltrumitecore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.baranhan.viltrumitecore.hero.DivineDamage;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.OpenRaceScreenS2CPacket;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import dev.baranhan.viltrumitecore.util.ViltrumiteCosmeticsPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CoreCommands {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                 "viltrumite"
                              )
                              .requires(source -> true))
                           .then(Commands.literal("skin").then(Commands.argument("name", StringArgumentType.string()).executes(context -> {
                              String skinName = StringArgumentType.getString(context, "name");
                              ServerPlayer playerEntity = ((CommandSourceStack)context.getSource()).getPlayerOrException();
                              ViltrumiteCosmeticsPlayer player = (ViltrumiteCosmeticsPlayer)playerEntity;
                              player.setViltrumiteSkin(skinName);
                              String feedbackMsg = skinName.equalsIgnoreCase("off") ? "\u00a7cDISABLED" : "\u00a7e" + skinName;
                              ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("\u00a7fViltrumite skin: " + feedbackMsg), false);
                              return 1;
                           }))))
                        .then(Commands.literal("cape").then(Commands.argument("name", StringArgumentType.string()).executes(context -> {
                           String capeName = StringArgumentType.getString(context, "name");
                           ServerPlayer playerEntity = ((CommandSourceStack)context.getSource()).getPlayerOrException();
                           ViltrumiteCosmeticsPlayer player = (ViltrumiteCosmeticsPlayer)playerEntity;
                           player.setViltrumiteCape(capeName);
                           String feedbackMsg = capeName.equalsIgnoreCase("off") ? "\u00a7cDISABLED" : "\u00a7e" + capeName;
                           ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("\u00a7fViltrumite cape: " + feedbackMsg), false);
                           return 1;
                        }))))
                     .then(Commands.literal("model").then(Commands.argument("name", StringArgumentType.string()).executes(context -> {
                        String modelName = StringArgumentType.getString(context, "name").toLowerCase();
                        ServerPlayer playerEntity = ((CommandSourceStack)context.getSource()).getPlayerOrException();
                        ViltrumiteCosmeticsPlayer player = (ViltrumiteCosmeticsPlayer)playerEntity;
                        if (!modelName.equals("default") && !modelName.equals("slim")) {
                           ((CommandSourceStack)context.getSource())
                              .sendSuccess(() -> Component.literal("\u00a7cInvalid model! Use 'default' or 'slim'."), false);
                           return 0;
                        } else {
                           player.setViltrumiteModel(modelName);
                           ((CommandSourceStack)context.getSource())
                              .sendSuccess(() -> Component.literal("\u00a7fViltrumite model set to: \u00a7e" + modelName), false);
                           return 1;
                        }
                     }))))
                  .then(
                     ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("power").requires(source -> source.hasPermission(2)))
                           .executes(context -> grantPower((CommandSourceStack)context.getSource(), ((CommandSourceStack)context.getSource()).getPlayerOrException())))
                        .then(
                           Commands.argument("target", EntityArgument.player())
                              .executes(context -> grantPower((CommandSourceStack)context.getSource(), EntityArgument.getPlayer(context, "target")))
                        )
                  ))
               .then(
                  ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("depower").requires(source -> source.hasPermission(2)))
                        .executes(context -> revokePower((CommandSourceStack)context.getSource(), ((CommandSourceStack)context.getSource()).getPlayerOrException())))
                     .then(
                        Commands.argument("target", EntityArgument.player())
                           .executes(context -> revokePower((CommandSourceStack)context.getSource(), EntityArgument.getPlayer(context, "target")))
                     )
               ))
            .then(
               Commands.literal("choose")
                  .requires(source -> source.hasPermission(2))
                  .executes(context -> reopenChoice(context.getSource(), context.getSource().getPlayerOrException()))
                  .then(Commands.argument("target", EntityArgument.player())
                     .executes(context -> reopenChoice(context.getSource(), EntityArgument.getPlayer(context, "target"))))
            )
            .then(divine())
            .then(WorldEventCommands.build())
      );
   }

   /**
    * Dev command (operators): {@code /viltrumite divine <targets> <amount> [type]}
    * deals divine damage (spec Reinhard §3.2). The type must be in the
    * {@code viltrumitecore:divine} damage-type tag; default {@code viltrumitecore:divine}.
    */
   private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> divine() {
      return Commands.literal("divine")
         .requires(source -> source.hasPermission(2))
         .then(Commands.argument("targets", EntityArgument.entities())
            .then(Commands.argument("amount", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0.0F))
               .executes(context -> dealDivine(context.getSource(), EntityArgument.getEntities(context, "targets"),
                  com.mojang.brigadier.arguments.FloatArgumentType.getFloat(context, "amount"), DivineDamage.DIVINE))
               .then(Commands.argument("type", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                  .executes(context -> dealDivine(context.getSource(), EntityArgument.getEntities(context, "targets"),
                     com.mojang.brigadier.arguments.FloatArgumentType.getFloat(context, "amount"),
                     net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                        net.minecraft.commands.arguments.ResourceLocationArgument.getId(context, "type")))))));
   }

   private static int dealDivine(CommandSourceStack source, java.util.Collection<? extends net.minecraft.world.entity.Entity> targets, float amount,
      net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> type) {
      net.minecraft.server.level.ServerLevel level = source.getLevel();
      if (!DivineDamage.isDivineType(level, type)) {
         source.sendFailure(Component.translatable("commands.viltrumitecore.divine.not_divine", type.location().toString()));
         return 0;
      }

      int hit = 0;
      for (net.minecraft.world.entity.Entity target : targets) {
         if (target.hurt(DivineDamage.source(level, source.getEntity(), type), amount)) {
            hit++;
         }
      }

      int count = hit;
      source.sendSuccess(() -> Component.translatable("commands.viltrumitecore.divine.success", String.format(java.util.Locale.ROOT, "%.1f", amount), count), true);
      return count;
   }

   /**
    * Re-opens the hero choice screen (operators only). The one-time choice
    * flag is cleared, so the next HeroChoiceC2SPacket is accepted once and
    * goes through HeroRegistry.changeHero (old hero cleaned up properly).
    */
   private static int reopenChoice(CommandSourceStack source, ServerPlayer target) {
      ((ViltrumiteCorePlayer)target).setChosenRace(false);
      CoreMessages.sendToPlayer(new OpenRaceScreenS2CPacket(), target);
      source.sendSuccess(() -> Component.literal("\u00a7fHero choice opened for \u00a7e" + target.getName().getString()), true);
      return 1;
   }

   private static int grantPower(CommandSourceStack source, ServerPlayer target) {
      if (target != null) {
         ViltrumiteCorePlayer corePlayer = (ViltrumiteCorePlayer)target;
         corePlayer.setViltrumite(true);
         target.displayClientMessage(Component.literal("\u00a7fViltrumite powers \u00a7aENABLED"), true);
         source.sendSuccess(() -> Component.literal("\u00a7fViltrumite powers for \u00a7e" + target.getName().getString() + "\u00a7f: \u00a7aENABLED"), false);
      }

      return 1;
   }

   private static int revokePower(CommandSourceStack source, ServerPlayer target) {
      if (target != null) {
         ViltrumiteCorePlayer corePlayer = (ViltrumiteCorePlayer)target;
         corePlayer.setViltrumite(false);
         if (!target.isCreative() && !target.isSpectator()) {
            target.getAbilities().mayfly = false;
            target.getAbilities().flying = false;
            target.onUpdateAbilities();
         }

         corePlayer.setSuperSpeed(false);
         target.displayClientMessage(Component.literal("\u00a7fViltrumite powers \u00a7cDISABLED"), true);
         source.sendSuccess(() -> Component.literal("\u00a7fViltrumite powers for \u00a7e" + target.getName().getString() + "\u00a7f: \u00a7cDISABLED"), false);
      }

      return 1;
   }
}
