package dev.baranhan.viltrumitecore.worldevent.ultron;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.baranhan.viltrumitecore.entity.ultron.UltronPrime;
import dev.baranhan.viltrumitecore.entity.ultron.UltronUnit;
import dev.baranhan.viltrumitecore.worldevent.WorldEventConflicts;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * {@code /ultron start [seed <n>] [force] | end victory|defeat | stop | status | nodes | phase <name> | adapt <protocol>
 * | spawn <kind> | reveal} (operator, level 2). Manual only: nothing starts the uprising by itself. {@code start} refuses
 * while another big event runs unless {@code force} is given; the same seed always gives the same scenario. {@code end}
 * plays the normal ending; {@code stop} is the emergency stop (everything gone at once, every machine handed back).
 * {@code adapt} feeds the real adaptation system (the protocol still needs a free slot and its cooldown over);
 * {@code spawn} obeys the per-kind caps.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class UltronCommands {
   private static final SuggestionProvider<CommandSourceStack> PHASES = (c, b) -> SharedSuggestionProvider.suggest(
      Arrays.stream(UltronPhase.values()).filter(p -> p != UltronPhase.ENDED).map(UltronPhase::key), b);
   private static final SuggestionProvider<CommandSourceStack> PROTOCOLS = (c, b) -> SharedSuggestionProvider.suggest(
      Arrays.stream(UltronAdaptation.Protocol.values()).map(UltronAdaptation.Protocol::key), b);
   private static final SuggestionProvider<CommandSourceStack> KINDS = (c, b) -> SharedSuggestionProvider.suggest(
      Arrays.stream(UltronUnitKind.values()).map(UltronUnitKind::key), b);

   private UltronCommands() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      event.getDispatcher().register(Commands.literal("ultron").requires(s -> s.hasPermission(2))
         .then(Commands.literal("start")
            .executes(c -> start(c, null, false))
            .then(Commands.literal("force").executes(c -> start(c, null, true)))
            .then(Commands.literal("seed").then(Commands.argument("seed", LongArgumentType.longArg())
               .executes(c -> start(c, LongArgumentType.getLong(c, "seed"), false))
               .then(Commands.literal("force").executes(c -> start(c, LongArgumentType.getLong(c, "seed"), true))))))
         .then(Commands.literal("end")
            .then(Commands.literal("victory").executes(c -> end(c, true)))
            .then(Commands.literal("defeat").executes(c -> end(c, false))))
         .then(Commands.literal("stop").executes(UltronCommands::stop))
         .then(Commands.literal("status").executes(UltronCommands::status))
         .then(Commands.literal("nodes").executes(UltronCommands::nodes))
         .then(Commands.literal("reveal").executes(UltronCommands::reveal))
         .then(Commands.literal("phase").then(Commands.argument("phase", StringArgumentType.word()).suggests(PHASES).executes(UltronCommands::phase)))
         .then(Commands.literal("adapt").then(Commands.argument("protocol", StringArgumentType.word()).suggests(PROTOCOLS).executes(UltronCommands::adapt)))
         .then(Commands.literal("spawn").then(Commands.argument("kind", StringArgumentType.word()).suggests(KINDS).executes(UltronCommands::spawn))));
   }

   /** Every big event that refuses to share the world with the uprising (the invasion included). */
   static List<Component> conflicts(ServerLevel level) {
      List<Component> out = new ArrayList<>(WorldEventConflicts.othersThanInvasion(level));
      out.addAll(WorldEventConflicts.invasion(level));
      return out;
   }

   private static int start(CommandContext<CommandSourceStack> c, Long seed, boolean force) {
      CommandSourceStack source = c.getSource();
      ServerLevel level = source.getLevel();
      if (UltronUprising.running(source.getServer())) {
         source.sendFailure(Component.translatable("commands.viltrumitecore.ultron.already"));
         return 0;
      }
      List<Component> conflicts = conflicts(level);
      if (!WorldEventConflicts.mayStart(conflicts.size(), force)) {
         source.sendFailure(Component.translatable("commands.viltrumitecore.ultron.conflict", ComponentUtils.formatList(conflicts, Component.literal(", "))));
         return 0;
      }
      long s = seed != null ? seed : level.getRandom().nextLong();
      Vec3 at = source.getPosition();
      if (!UltronUprising.start(level, at, s)) {
         source.sendFailure(Component.translatable("commands.viltrumitecore.ultron.already"));
         return 0;
      }
      UltronSession session = UltronUprising.current();
      String variant = session != null ? session.scenario.variant().key() : "?";
      source.sendSuccess(() -> Component.translatable(force && !conflicts.isEmpty() ? "commands.viltrumitecore.ultron.started.forced"
            : "commands.viltrumitecore.ultron.started", Math.round(at.x), Math.round(at.y), Math.round(at.z), Long.toString(s),
         Component.translatable("viltrumitecore.ultron.variant." + variant)).withStyle(ChatFormatting.AQUA), true);
      return 1;
   }

   private static int end(CommandContext<CommandSourceStack> c, boolean victory) {
      if (!UltronUprising.finish(c.getSource().getServer(), victory)) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.ultron.not_running"));
         return 0;
      }
      c.getSource().sendSuccess(() -> Component.translatable(victory ? "commands.viltrumitecore.ultron.ending.victory"
         : "commands.viltrumitecore.ultron.ending.defeat").withStyle(ChatFormatting.AQUA), true);
      return 1;
   }

   private static int stop(CommandContext<CommandSourceStack> c) {
      if (!UltronUprising.stop(c.getSource().getServer())) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.ultron.not_running"));
         return 0;
      }
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.ultron.stopped").withStyle(ChatFormatting.YELLOW), true);
      return 1;
   }

   private static int phase(CommandContext<CommandSourceStack> c) {
      String key = StringArgumentType.getString(c, "phase");
      UltronPhase phase = UltronPhase.byKey(key);
      if (phase == null || phase == UltronPhase.ENDED) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.ultron.unknown_phase", key));
         return 0;
      }
      if (!UltronUprising.jump(c.getSource().getServer(), phase)) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.ultron.not_running"));
         return 0;
      }
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.ultron.phase_set",
         Component.translatable("viltrumitecore.ultron.phase." + phase.key())).withStyle(ChatFormatting.AQUA), true);
      return 1;
   }

   private static int adapt(CommandContext<CommandSourceStack> c) {
      String key = StringArgumentType.getString(c, "protocol");
      UltronAdaptation.Protocol protocol = null;
      for (UltronAdaptation.Protocol p : UltronAdaptation.Protocol.values()) {
         if (p.key().equals(key)) {
            protocol = p;
         }
      }
      if (protocol == null) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.ultron.unknown_protocol", key));
         return 0;
      }
      UltronSession s = live(c);
      if (s == null) {
         return 0;
      }
      UltronAdaptation a = s.adaptation;
      if (a.frozen()) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.ultron.adapt_frozen"));
         return 0;
      }
      a.setPressure(protocol.tactic, Math.max(a.pressure(protocol.tactic), protocol.threshold / Math.max(0.01F, a.learning()) + 1.0F));
      UltronAdaptation.Protocol p = protocol;
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.ultron.adapt_fed",
         Component.translatable("viltrumitecore.ultron.protocol." + p.key())).withStyle(ChatFormatting.AQUA), true);
      return 1;
   }

   private static int spawn(CommandContext<CommandSourceStack> c) {
      String key = StringArgumentType.getString(c, "kind");
      UltronUnitKind kind = null;
      for (UltronUnitKind k : UltronUnitKind.values()) {
         if (k.key().equals(key)) {
            kind = k;
         }
      }
      if (kind == null) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.ultron.unknown_kind", key));
         return 0;
      }
      UltronSession s = live(c);
      if (s == null) {
         return 0;
      }
      ServerLevel level = c.getSource().getLevel();
      if (!s.in(level)) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.ultron.wrong_dimension"));
         return 0;
      }
      int alive = UltronDirector.counts(s)[kind.ordinal()];
      if (alive >= UltronRules.kindCap(kind)) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.ultron.spawn_cap", UltronRules.kindCap(kind)));
         return 0;
      }
      Vec3 at = c.getSource().getPosition();
      if (!UltronDirector.spawnUnit(level, s, new UltronDirector.Spawn(kind, at, -1, false, null))) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.ultron.spawn_failed"));
         return 0;
      }
      UltronUnitKind k = kind;
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.ultron.spawned",
         Component.translatable("entity.viltrumitecore.ultron_" + k.key())).withStyle(ChatFormatting.AQUA), true);
      return 1;
   }

   /** The node state in the reader's language. */
   private static Component state(UltronNodeState state) {
      return Component.translatable("viltrumitecore.ultron.node_state." + state.name().toLowerCase(Locale.ROOT));
   }

   private static int reveal(CommandContext<CommandSourceStack> c) {
      UltronSession s = live(c);
      if (s == null) {
         return 0;
      }
      ServerLevel level = c.getSource().getServer().getLevel(s.dimension);
      if (level == null) {
         return 0;
      }
      int count = 0;
      for (UltronNetwork.Node n : s.network.all()) {
         if (!n.discovered && n.standing()) {
            UltronDirector.discover(level, s, n, null);
            count++;
         }
      }
      int revealed = count;
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.ultron.revealed", revealed).withStyle(ChatFormatting.AQUA), true);
      return Math.max(1, count);
   }

   private static int status(CommandContext<CommandSourceStack> c) {
      MinecraftServer server = c.getSource().getServer();
      UltronSession s = UltronUprising.current();
      if (s == null || !UltronUprising.running(server)) {
         c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.ultron.none").withStyle(ChatFormatting.GRAY), false);
         return 0;
      }
      Vec3 a = s.anchor;
      int seconds = s.phaseTick / UltronRules.TPS;
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.ultron.status",
         Component.translatable("viltrumitecore.ultron.phase." + s.phase.key()), seconds, Long.toString(s.seed),
         Component.translatable("viltrumitecore.ultron.variant." + s.scenario.variant().key()), s.dimension.location().toString(),
         Math.round(a.x), Math.round(a.y), Math.round(a.z), Math.round(s.radius)).withStyle(ChatFormatting.AQUA), false);
      UltronNetwork net = s.network;
      UltronNetwork.Node core = net.core();
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.ultron.status.network", net.count(UltronNodeState.ONLINE),
         net.count(UltronNodeState.DISABLED), net.count(UltronNodeState.BUILDING), net.count(UltronNodeState.DESTROYED), net.all().size(),
         core == null ? Component.literal("-") : state(core.state)).withStyle(ChatFormatting.GRAY), false);
      int[] counts = UltronDirector.counts(s);
      int players = UltronDirector.fighters(server.getLevel(s.dimension) != null ? server.getLevel(s.dimension) : c.getSource().getLevel(), s).size();
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.ultron.status.forces", UltronDirector.sum(counts),
         UltronRules.aliveCap(s.phase, players), counts[0], counts[1], counts[2], counts[3], counts[4], s.builtTotal, UltronRules.EVENT_PRODUCTION_CAP,
         s.capture.golems(), s.capture.suits()).withStyle(ChatFormatting.GRAY), false);
      List<Component> active = new ArrayList<>();
      for (UltronAdaptation.Protocol p : UltronAdaptation.Protocol.values()) {
         if (s.adaptation.active(p)) {
            active.add(Component.translatable("viltrumitecore.ultron.protocol." + p.key()).append(" " + s.adaptation.ticksLeft(p) / UltronRules.TPS + "s"));
         }
      }
      MutableComponent protocols = active.isEmpty() ? Component.literal("-") : (MutableComponent)ComponentUtils.formatList(active, Component.literal(", "));
      String learning = String.format(Locale.ROOT, "%.1f", s.adaptation.learning());
      c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.ultron.status.mind",
         Component.translatable("viltrumitecore.ultron.doctrine." + s.doctrine.key()), protocols, learning, s.adaptation.activations())
         .withStyle(ChatFormatting.GRAY), false);
      ServerLevel level = server.getLevel(s.dimension);
      Entity boss = level != null ? UltronDirector.boss(level, s) : null;
      if (boss instanceof UltronPrime prime) {
         c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.ultron.status.boss",
            Component.translatable("viltrumitecore.ultron.stage." + prime.stage().key()), Math.round(prime.getHealth()), Math.round(prime.getMaxHealth()),
            Math.round(s.uploadShare() * 100.0F)).withStyle(ChatFormatting.GRAY), false);
      }
      return 1;
   }

   private static int nodes(CommandContext<CommandSourceStack> c) {
      UltronSession s = live(c);
      if (s == null) {
         return 0;
      }
      for (UltronNetwork.Node n : s.network.all()) {
         c.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.ultron.node_line", n.id,
            Component.translatable("viltrumitecore.ultron.node." + n.kind.key()), state(n.state), Math.round(n.hull),
            Math.round(UltronRules.hull(n.kind)), Math.round(n.shield), Math.round(n.x), Math.round(n.y), Math.round(n.z),
            n.discovered ? "+" : "-", n.powered ? "+" : "-").withStyle(n.online() ? ChatFormatting.RED : ChatFormatting.GRAY), false);
      }
      return s.network.all().size();
   }

   private static UltronSession live(CommandContext<CommandSourceStack> c) {
      UltronSession s = UltronUprising.current();
      if (s == null || !UltronUprising.running(c.getSource().getServer()) || s.outcome != 0) {
         c.getSource().sendFailure(Component.translatable("commands.viltrumitecore.ultron.not_running"));
         return null;
      }
      return s;
   }

   /** Units of the uprising alive now (tests and status). */
   static int alive(UltronSession s) {
      int n = 0;
      for (UltronUnit u : s.units.values()) {
         if (u.isAlive()) {
            n++;
         }
      }
      return n;
   }
}
