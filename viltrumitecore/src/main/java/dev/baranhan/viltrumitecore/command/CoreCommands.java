package dev.baranhan.viltrumitecore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.baranhan.viltrumitecore.hero.DivineDamage;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.OpenRaceScreenS2CPacket;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import dev.baranhan.viltrumitecore.util.ViltrumiteCosmeticsPlayer;
import net.minecraft.ChatFormatting;
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
                              ((CommandSourceStack)context.getSource()).sendSuccess(() -> cosmetic("skin", skinName), false);
                              return 1;
                           }))))
                        .then(Commands.literal("cape").then(Commands.argument("name", StringArgumentType.string()).executes(context -> {
                           String capeName = StringArgumentType.getString(context, "name");
                           ServerPlayer playerEntity = ((CommandSourceStack)context.getSource()).getPlayerOrException();
                           ViltrumiteCosmeticsPlayer player = (ViltrumiteCosmeticsPlayer)playerEntity;
                           player.setViltrumiteCape(capeName);
                           ((CommandSourceStack)context.getSource()).sendSuccess(() -> cosmetic("cape", capeName), false);
                           return 1;
                        }))))
                     .then(Commands.literal("model").then(Commands.argument("name", StringArgumentType.string()).executes(context -> {
                        String modelName = StringArgumentType.getString(context, "name").toLowerCase();
                        ServerPlayer playerEntity = ((CommandSourceStack)context.getSource()).getPlayerOrException();
                        ViltrumiteCosmeticsPlayer player = (ViltrumiteCosmeticsPlayer)playerEntity;
                        if (!modelName.equals("default") && !modelName.equals("slim")) {
                           ((CommandSourceStack)context.getSource())
                              .sendSuccess(() -> Component.translatable("commands.viltrumitecore.model.invalid").withStyle(ChatFormatting.RED), false);
                           return 0;
                        } else {
                           player.setViltrumiteModel(modelName);
                           ((CommandSourceStack)context.getSource())
                              .sendSuccess(() -> Component.translatable("commands.viltrumitecore.model.set", Component.literal(modelName).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.WHITE), false);
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
            .then(annihilate())
            .then(Commands.literal("pause").requires(source -> source.hasPermission(2))
               .then(Commands.argument("ticks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,1200))
                  .executes(ctx -> dev.baranhan.viltrumitecore.hero.control.WorldPause.begin(ctx.getSource().getServer(),
                     ctx.getSource().getPlayerOrException().getUUID(),
                     com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx,"ticks")) ? 1 : 0)))
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

   /**
    * Dev command (operators): {@code /viltrumite annihilate <targets>} strikes each
    * target with the Reid strike ({@code Annihilation.strike}, Reinhard spec §1.3)
    * from the calling player. Kept for testing future heroes.
    */
   private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> annihilate() {
      return Commands.literal("annihilate")
         .requires(source -> source.hasPermission(2))
         .then(Commands.argument("targets", EntityArgument.entities())
            .executes(context -> annihilate(context.getSource(), EntityArgument.getEntities(context, "targets"))));
   }

   private static int annihilate(CommandSourceStack source, java.util.Collection<? extends net.minecraft.world.entity.Entity> targets)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      net.minecraft.server.level.ServerPlayer attacker = source.getPlayerOrException();
      int[] counts = new int[dev.baranhan.viltrumitecore.hero.Annihilation.Outcome.values().length];
      for (net.minecraft.world.entity.Entity target : targets) {
         counts[dev.baranhan.viltrumitecore.hero.Annihilation.strike(attacker, target).ordinal()]++;
      }

      source.sendSuccess(() -> Component.translatable("commands.viltrumitecore.annihilate.success", counts[0], counts[1], counts[2], counts[3]), true);
      return counts[0];
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
      source.sendSuccess(() -> Component.translatable("commands.viltrumitecore.choose.opened", target.getDisplayName().copy().withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.WHITE), true);
      return 1;
   }

   private static int grantPower(CommandSourceStack source, ServerPlayer target) {
      if (target != null) {
         ViltrumiteCorePlayer corePlayer = (ViltrumiteCorePlayer)target;
         corePlayer.setViltrumite(true);
         target.displayClientMessage(powerSelf(true), true);
         source.sendSuccess(() -> powerFor(target, true), false);
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
         target.displayClientMessage(powerSelf(false), true);
         source.sendSuccess(() -> powerFor(target, false), false);
      }

      return 1;
   }

   /** "Viltrumite skin/cape: <name>" or "...: disabled" for "off" (localized). */
   static Component cosmetic(String what, String name) {
      return name.equalsIgnoreCase("off")
         ? Component.translatable("commands.viltrumitecore." + what + ".off").withStyle(ChatFormatting.WHITE)
         : Component.translatable("commands.viltrumitecore." + what + ".set", Component.literal(name).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.WHITE);
   }

   private static Component state(boolean on) {
      return Component.translatable(on ? "commands.viltrumitecore.state.enabled" : "commands.viltrumitecore.state.disabled")
         .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
   }

   private static Component powerSelf(boolean on) {
      return Component.translatable("commands.viltrumitecore.power.self", state(on)).withStyle(ChatFormatting.WHITE);
   }

   private static Component powerFor(ServerPlayer target, boolean on) {
      return Component.translatable("commands.viltrumitecore.power.for", target.getDisplayName().copy().withStyle(ChatFormatting.YELLOW), state(on))
         .withStyle(ChatFormatting.WHITE);
   }
}
