package dev.baranhan.viltrumitecore.worldevent.godzilla;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.baranhan.viltrumitecore.entity.godzilla.GodzillaEntity;
import dev.baranhan.viltrumitecore.worldevent.WorldEventConflicts;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * {@code /godzilla start [force] | stop | status | cleanup} (operator, level 2) and the QA helpers
 * {@code move <move> | phase <phase> | health <percent> | passive on|off | creative on|off}. Manual only: the kaiju
 * never spawns by itself. {@code start} refuses while another big event runs unless {@code force} is given;
 * {@code stop} removes the kaiju and everything the event owns (debris, alarm, effects) and keeps the city damage,
 * which {@code /city reset} restores; {@code cleanup} removes orphaned bodies and lingering effects.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class GodzillaCommands {
   private static final SuggestionProvider<CommandSourceStack> MOVES = (c, b) -> SharedSuggestionProvider.suggest(
      Arrays.stream(GodzillaMove.values()).filter(m -> m != GodzillaMove.NONE && m != GodzillaMove.FIRE && m != GodzillaMove.BREATH_END
         && m != GodzillaMove.DOWN).map(GodzillaMove::key), b);
   private static final SuggestionProvider<CommandSourceStack> PHASES = (c, b) -> SharedSuggestionProvider.suggest(
      Arrays.stream(GodzillaPhase.values()).map(GodzillaPhase::key), b);

   private GodzillaCommands() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      event.getDispatcher().register(Commands.literal("godzilla").requires(s -> s.hasPermission(2))
         .then(Commands.literal("start")
            .executes(c -> start(c, false))
            .then(Commands.literal("force").executes(c -> start(c, true))))
         .then(Commands.literal("stop").executes(GodzillaCommands::stop))
         .then(Commands.literal("status").executes(GodzillaCommands::status))
         .then(Commands.literal("cleanup").executes(GodzillaCommands::cleanup))
         .then(Commands.literal("move").then(Commands.argument("move", StringArgumentType.word()).suggests(MOVES).executes(GodzillaCommands::move)))
         .then(Commands.literal("phase").then(Commands.argument("phase", StringArgumentType.word()).suggests(PHASES).executes(GodzillaCommands::phase)))
         .then(Commands.literal("health").then(Commands.argument("percent", IntegerArgumentType.integer(1, 100)).executes(GodzillaCommands::health)))
         .then(Commands.literal("passive")
            .then(Commands.literal("on").executes(c -> passive(c, true)))
            .then(Commands.literal("off").executes(c -> passive(c, false))))
         .then(Commands.literal("creative")
            .then(Commands.literal("on").executes(c -> creative(c, true)))
            .then(Commands.literal("off").executes(c -> creative(c, false)))));
   }

   /** Every big event that refuses to share the world with the catastrophe (each named once). */
   static List<Component> conflicts(ServerLevel level) {
      LinkedHashSet<Component> out = new LinkedHashSet<>(WorldEventConflicts.othersThanInvasion(level));
      out.addAll(WorldEventConflicts.invasion(level));
      out.remove(Component.translatable(GodzillaEvent.CONFLICT_KEY));
      return new ArrayList<>(out);
   }

   private static int start(CommandContext<CommandSourceStack> c, boolean force) {
      CommandSourceStack source = c.getSource();
      ServerLevel level = source.getLevel();
      if (GodzillaEvent.running(source.getServer())) {
         source.sendFailure(Component.translatable("commands.viltrumitecore.godzilla.already"));
         return 0;
      }
      List<Component> conflicts = conflicts(level);
      if (!WorldEventConflicts.mayStart(conflicts.size(), force)) {
         source.sendFailure(Component.translatable("commands.viltrumitecore.godzilla.conflict", ComponentUtils.formatList(conflicts, Component.literal(", "))));
         return 0;
      }
      Vec3 at = source.getPosition();
      GodzillaEvent.Start result = GodzillaEvent.start(level, at, level.getRandom().nextLong());
      switch (result) {
         case RUNNING -> {
            source.sendFailure(Component.translatable("commands.viltrumitecore.godzilla.already"));
            return 0;
         }
         case NO_ROOM -> {
            source.sendFailure(Component.translatable("commands.viltrumitecore.godzilla.no_room"));
            return 0;
         }
         default -> {
         }
      }
      GodzillaSession s = GodzillaEvent.current();
      Vec3 from = s != null ? s.home : at;
      source.sendSuccess(() -> Component.translatable(force && !conflicts.isEmpty() ? "commands.viltrumitecore.godzilla.started.forced"
         : "commands.viltrumitecore.godzilla.started", Mth.floor(from.x), Mth.floor(from.y), Mth.floor(from.z)).withStyle(ChatFormatting.DARK_AQUA), true);
      return 1;
   }

   private static int stop(CommandContext<CommandSourceStack> c) {
      if (!GodzillaEvent.stop(c.getSource().getServer())) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.godzilla.not_running"));
         return 0;
      }
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.godzilla.stopped").withStyle(ChatFormatting.YELLOW), true);
      return 1;
   }

   private static int cleanup(CommandContext<CommandSourceStack> c) {
      int removed = GodzillaEvent.cleanup(c.getSource().getServer());
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.godzilla.cleaned", removed).withStyle(ChatFormatting.YELLOW), true);
      return 1;
   }

   private static int status(CommandContext<CommandSourceStack> c) {
      CommandSourceStack source = c.getSource();
      GodzillaSession s = GodzillaEvent.current();
      if (s == null || !GodzillaEvent.running(source.getServer())) {
         source.sendSuccess(() -> Component.translatable("commands.viltrumitecore.godzilla.status.idle").withStyle(ChatFormatting.GRAY), false);
         return 1;
      }
      GodzillaEntity e = s.body;
      Vec3 at = e != null ? e.position() : s.home;
      String move = e != null ? e.move().key() : "-";
      int health = Math.round(s.healthShare() * 100.0F);
      int seconds = (int)(s.clock / 20);
      source.sendSuccess(() -> Component.translatable("commands.viltrumitecore.godzilla.status", Component.translatable("viltrumitecore.godzilla.phase." + s.phase.key()),
         health, move, Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z), seconds).withStyle(ChatFormatting.DARK_AQUA), false);
      source.sendSuccess(() -> Component.translatable("commands.viltrumitecore.godzilla.status.city", s.destruction.destroyed(), s.destruction.buildingsHit(),
         s.destruction.jobs(), s.destruction.flying(), s.breaths, s.threats.size(), Math.round(s.damageTaken)).withStyle(ChatFormatting.GRAY), false);
      return 1;
   }

   private static GodzillaSession live(CommandContext<CommandSourceStack> c) {
      GodzillaSession s = GodzillaEvent.current();
      if (s == null || !GodzillaEvent.running(c.getSource().getServer()) || s.body == null || !s.body.isAlive()) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.godzilla.not_running"));
         return null;
      }
      return s;
   }

   private static int move(CommandContext<CommandSourceStack> c) {
      GodzillaSession s = live(c);
      if (s == null) {
         return 0;
      }
      String key = StringArgumentType.getString(c, "move");
      GodzillaMove move = GodzillaMove.byKey(key);
      if (move == null || move == GodzillaMove.NONE || move == GodzillaMove.FIRE || move == GodzillaMove.BREATH_END || move == GodzillaMove.DOWN
         || s.phase.over()) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.godzilla.bad_move", key));
         return 0;
      }
      GodzillaEntity e = s.body;
      ServerLevel level = (ServerLevel)e.level();
      if (move == GodzillaMove.DEATH) {
         e.hurt(GodzillaDamage.crush(level, e), Float.MAX_VALUE);
         e.setHealth(0.0F);
         e.die(level.damageSources().genericKill());
      } else {
         if (move == GodzillaMove.CHARGE) {
            Entity src = c.getSource().getEntity();
            LivingEntity target = src instanceof LivingEntity living ? living : s.target(level);
            s.breath.begin(s, e, target, target == null ? c.getSource().getPosition() : null);
         }
         s.start(e, move, move == GodzillaMove.CLAW_L || move == GodzillaMove.TAIL ? 1 : 0);
      }
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.godzilla.move", move.key()).withStyle(ChatFormatting.DARK_AQUA), true);
      return 1;
   }

   private static int phase(CommandContext<CommandSourceStack> c) {
      GodzillaSession s = live(c);
      if (s == null) {
         return 0;
      }
      GodzillaPhase next = GodzillaPhase.byKey(StringArgumentType.getString(c, "phase"));
      if (next == null || next == GodzillaPhase.DEATH) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.godzilla.bad_phase", StringArgumentType.getString(c, "phase")));
         return 0;
      }
      GodzillaEntity e = s.body;
      ServerLevel level = (ServerLevel)e.level();
      if (next.enraged() && e.getHealth() > e.getMaxHealth() * GodzillaRules.RAGE_HEALTH) {
         e.setHealth(e.getMaxHealth() * (next == GodzillaPhase.FINALE ? GodzillaRules.FINALE_HEALTH * 0.95F : GodzillaRules.RAGE_HEALTH * 0.95F));
      }
      s.phase(level, e, next);
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.godzilla.phase", Component.translatable("viltrumitecore.godzilla.phase." + next.key()))
         .withStyle(ChatFormatting.DARK_AQUA), true);
      return 1;
   }

   private static int health(CommandContext<CommandSourceStack> c) {
      GodzillaSession s = live(c);
      if (s == null) {
         return 0;
      }
      int pct = IntegerArgumentType.getInteger(c, "percent");
      GodzillaEntity e = s.body;
      e.setHealth(e.getMaxHealth() * pct / 100.0F);
      s.updateBar((ServerLevel)e.level(), e);
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.godzilla.health", pct).withStyle(ChatFormatting.DARK_AQUA), true);
      return 1;
   }

   private static int passive(CommandContext<CommandSourceStack> c, boolean on) {
      GodzillaSession s = live(c);
      if (s == null) {
         return 0;
      }
      s.passive = on;
      c.getSource().sendSuccess(() -> Component.translatable(on ? "commands.viltrumitecore.godzilla.passive.on" : "commands.viltrumitecore.godzilla.passive.off")
         .withStyle(ChatFormatting.DARK_AQUA), true);
      return 1;
   }

   private static int creative(CommandContext<CommandSourceStack> c, boolean on) {
      GodzillaSession s = live(c);
      if (s == null) {
         return 0;
      }
      s.targetCreative = on;
      c.getSource().sendSuccess(() -> Component.translatable(on ? "commands.viltrumitecore.godzilla.creative.on" : "commands.viltrumitecore.godzilla.creative.off")
         .withStyle(ChatFormatting.DARK_AQUA), true);
      return 1;
   }
}
