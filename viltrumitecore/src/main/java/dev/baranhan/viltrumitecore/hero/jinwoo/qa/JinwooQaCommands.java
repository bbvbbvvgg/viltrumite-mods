package dev.baranhan.viltrumitecore.hero.jinwoo.qa;

import dev.baranhan.viltrumitecore.entity.jinwoo.ShadowKind;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooRules;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooState;
import java.util.Arrays;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Opt-in operator QA harness for playtests (army size, perf). Registered only with
 * {@code -Dviltrumitecore.jinwoo.qa=true}; no command exists in a normal game.
 * {@code /jinwooqa fill} fills the roster to 24 (Beru, Tank, knights, insects, beasts) and
 * restores mana/cooldowns; {@code /jinwooqa mana} restores mana and cooldowns only.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class JinwooQaCommands {
   public static final boolean ENABLED = Boolean.getBoolean("viltrumitecore.jinwoo.qa");

   private JinwooQaCommands() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      if (!ENABLED) {
         return;
      }
      event.getDispatcher().register(Commands.literal("jinwooqa").requires(s -> s.hasPermission(2))
         .then(Commands.literal("fill").executes(c -> fill(c.getSource().getPlayerOrException())))
         .then(Commands.literal("mana").executes(c -> mana(c.getSource().getPlayerOrException()))));
   }

   private static int mana(ServerPlayer player) {
      JinwooState state = JinwooState.of(player);
      if (state == null) {
         return 0;
      }
      state.mana = JinwooRules.MANA_MAX;
      Arrays.fill(state.cooldowns, 0);
      return 1;
   }

   private static int fill(ServerPlayer player) {
      JinwooState state = JinwooState.of(player);
      if (state == null) {
         return 0;
      }
      ShadowKind[] cycle = {ShadowKind.INSECT, ShadowKind.BEAST, ShadowKind.KNIGHT, ShadowKind.INFANTRY};
      int i = 0;
      while (!state.roster.full()) {
         state.roster.addExtracted(cycle[i++ % cycle.length]);
      }
      mana(player);
      player.sendSystemMessage(Component.literal("jinwooqa: roster " + state.roster.size()));
      return 1;
   }
}
