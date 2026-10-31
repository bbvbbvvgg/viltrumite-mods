package dev.baranhan.viltrumitecore.worldevent.chitauri;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.baranhan.viltrumitecore.worldevent.WorldEventConflicts;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * {@code /chitauri start [force] | end | stop | status | wave | phase <name>} (operator, level 2).
 * Manual only: nothing starts the invasion by itself. {@code start} refuses while another world
 * event runs unless {@code force} is given; {@code end} plays the normal closing; {@code stop} is
 * the emergency stop (everything gone at once).
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class ChitauriCommands {
   private static final SuggestionProvider<CommandSourceStack> PHASES = (c, b) -> SharedSuggestionProvider.suggest(
      Arrays.stream(ChitauriPhase.values()).filter(p -> p != ChitauriPhase.ENDED).map(ChitauriPhase::key), b);

   private ChitauriCommands() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      event.getDispatcher().register(Commands.literal("chitauri").requires(s -> s.hasPermission(2))
         .then(Commands.literal("start")
            .executes(c -> start(c, false))
            .then(Commands.literal("force").executes(c -> start(c, true))))
         .then(Commands.literal("end").executes(ChitauriCommands::end))
         .then(Commands.literal("stop").executes(ChitauriCommands::stop))
         .then(Commands.literal("status").executes(ChitauriCommands::status))
         .then(Commands.literal("wave").executes(ChitauriCommands::wave))
         .then(Commands.literal("phase").then(Commands.argument("phase", StringArgumentType.word()).suggests(PHASES).executes(ChitauriCommands::phase))));
   }

   private static int start(CommandContext<CommandSourceStack> c, boolean force) {
      CommandSourceStack source = c.getSource();
      ServerLevel level = source.getLevel();
      if (ChitauriInvasion.running(source.getServer())) {
         source.sendFailure(Component.translatable("commands.viltrumitecore.chitauri.already"));
         return 0;
      }
      List<Component> conflicts = WorldEventConflicts.othersThanInvasion(level);
      if (!WorldEventConflicts.mayStart(conflicts.size(), force)) {
         source.sendFailure(Component.translatable("commands.viltrumitecore.chitauri.conflict", ComponentUtils.formatList(conflicts, Component.literal(", "))));
         return 0;
      }
      Vec3 at = source.getPosition();
      if (!ChitauriInvasion.start(level, at)) {
         source.sendFailure(Component.translatable("commands.viltrumitecore.chitauri.already"));
         return 0;
      }
      source.sendSuccess(() -> Component.translatable(force && !conflicts.isEmpty() ? "commands.viltrumitecore.chitauri.started.forced"
         : "commands.viltrumitecore.chitauri.started", Math.round(at.x), Math.round(at.y), Math.round(at.z)).withStyle(ChatFormatting.AQUA), true);
      return 1;
   }

   private static int end(CommandContext<CommandSourceStack> c) {
      if (!ChitauriInvasion.end(c.getSource().getServer())) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.chitauri.not_running"));
         return 0;
      }
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.chitauri.ending").withStyle(ChatFormatting.AQUA), true);
      return 1;
   }

   private static int stop(CommandContext<CommandSourceStack> c) {
      if (!ChitauriInvasion.stop(c.getSource().getServer())) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.chitauri.not_running"));
         return 0;
      }
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.chitauri.stopped").withStyle(ChatFormatting.YELLOW), true);
      return 1;
   }

   private static int wave(CommandContext<CommandSourceStack> c) {
      if (!ChitauriInvasion.running(c.getSource().getServer())) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.chitauri.not_running"));
         return 0;
      }
      if (!ChitauriInvasion.nextWave(c.getSource().getServer())) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.chitauri.no_wave"));
         return 0;
      }
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.chitauri.wave_sent").withStyle(ChatFormatting.AQUA), true);
      return 1;
   }

   private static int phase(CommandContext<CommandSourceStack> c) {
      String key = StringArgumentType.getString(c, "phase");
      ChitauriPhase phase = ChitauriPhase.byKey(key);
      if (phase == null || phase == ChitauriPhase.ENDED) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.chitauri.unknown_phase", key));
         return 0;
      }
      if (!ChitauriInvasion.jump(c.getSource().getServer(), phase)) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.chitauri.not_running"));
         return 0;
      }
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.chitauri.phase_set", ChitauriInvasion.phaseName(phase)).withStyle(ChatFormatting.AQUA), true);
      return 1;
   }

   private static int status(CommandContext<CommandSourceStack> c) {
      MinecraftServer server = c.getSource().getServer();
      ChitauriInvasion.Session s = ChitauriInvasion.session();
      if (s == null || !ChitauriInvasion.running(server)) {
         c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.chitauri.none").withStyle(ChatFormatting.GRAY), false);
         return 0;
      }
      int seconds = Math.max(0, ChitauriRules.length(s.phase()) - s.phaseTick()) / 20;
      Vec3 a = s.anchor();
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.chitauri.status", ChitauriInvasion.phaseName(s.phase()), seconds,
         s.dimension().location().toString(), Math.round(a.x), Math.round(a.y), Math.round(a.z)).withStyle(ChatFormatting.AQUA), false);
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.chitauri.status.forces", s.soldiers(), ChitauriRules.MAX_SOLDIERS,
         s.chariots(), ChitauriRules.MAX_CHARIOTS, s.leviathans(), ChitauriRules.MAX_LEVIATHANS, s.pending()).withStyle(ChatFormatting.GRAY), false);
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.chitauri.status.score", s.killedSoldiers(), s.killedChariots(),
         s.killedLeviathans(), ChitauriDestruction.destroyed(), ChitauriRules.MAX_DESTROYED_BLOCKS).withStyle(ChatFormatting.GRAY), false);
      return 1;
   }
}
