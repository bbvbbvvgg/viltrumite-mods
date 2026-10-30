package dev.baranhan.viltrumitecore.city;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.city.profile.CityProfile;
import dev.baranhan.viltrumitecore.city.profile.WalkTile;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * /city: help and status for everyone; enable, disable, density, populate, clear and
 * reset for operators (permission level 2). Works right after the prepared world loads:
 * the profile ships with the world, there is nothing to define or scan.
 */
public final class CityCommands {
   private static final SimpleCommandExceptionType NO_CITY = new SimpleCommandExceptionType(Component.translatable("viltrumitecore.city.error.no_city"));
   private static final SimpleCommandExceptionType NOT_ENABLED = new SimpleCommandExceptionType(Component.translatable("viltrumitecore.city.error.disabled"));
   private static final SimpleCommandExceptionType RESTORING = new SimpleCommandExceptionType(Component.translatable("viltrumitecore.city.error.restoring"));
   private static final SimpleCommandExceptionType NO_CONFIRM = new SimpleCommandExceptionType(Component.translatable("viltrumitecore.city.reset.no_pending"));
   /** Ticks a /city reset warning stays confirmable. */
   static final int CONFIRM_WINDOW = 600;
   private static final Map<String, Long> PENDING_RESET = new HashMap<>();

   private CityCommands() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> d) {
      d.register(Commands.literal("city")
         .executes(CityCommands::help)
         .then(Commands.literal("help").executes(CityCommands::help))
         .then(Commands.literal("status").executes(CityCommands::status))
         .then(Commands.literal("enable").requires(s -> s.hasPermission(2)).executes(c -> enable(c, true)))
         .then(Commands.literal("disable").requires(s -> s.hasPermission(2)).executes(c -> enable(c, false)))
         .then(Commands.literal("density").requires(s -> s.hasPermission(2))
            .executes(CityCommands::showDensity)
            .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, CityManager.MAX_DENSITY))
               .suggests((c, b) -> {
                  for (String v : new String[]{"0.5", "1.0", "1.5", "2.0"}) {
                     b.suggest(v);
                  }

                  return b.buildFuture();
               })
               .executes(c -> density(c, DoubleArgumentType.getDouble(c, "value")))))
         .then(Commands.literal("populate").requires(s -> s.hasPermission(2))
            .then(Commands.argument("count", IntegerArgumentType.integer(1, CityManager.ABSOLUTE_MAX))
               .suggests((c, b) -> b.suggest(20).suggest(50).suggest(100).buildFuture())
               .executes(c -> populate(c, IntegerArgumentType.getInteger(c, "count")))))
         .then(Commands.literal("clear").requires(s -> s.hasPermission(2)).executes(CityCommands::clear))
         .then(Commands.literal("reset").requires(s -> s.hasPermission(2))
            .executes(CityCommands::resetWarn)
            .then(Commands.literal("confirm").executes(CityCommands::resetConfirm))
            .then(Commands.literal("cancel").executes(CityCommands::resetCancel)))
         .then(Commands.literal("here").requires(s -> s.hasPermission(2)).executes(CityCommands::here)));
   }

   private static CityManager city(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      CityManager m = CityManager.active();
      if (m == null) {
         throw NO_CITY.create();
      }

      return m;
   }

   private static void ok(CommandContext<CommandSourceStack> c, Component msg, boolean broadcast) {
      c.getSource().sendSuccess(() -> msg, broadcast);
   }

   private static MutableComponent line(String key, Object... args) {
      return Component.translatable(key, args);
   }

   private static int help(CommandContext<CommandSourceStack> c) {
      ok(c, line("viltrumitecore.city.help.title").withStyle(ChatFormatting.GOLD), false);
      for (String k : new String[]{"enable", "disable", "density", "populate", "clear", "status", "reset", "help"}) {
         ok(c, Component.literal(" /city " + k + " ").withStyle(ChatFormatting.YELLOW)
            .append(line("viltrumitecore.city.help." + k).withStyle(ChatFormatting.GRAY)), false);
      }

      if (CityManager.active() == null) {
         ok(c, line("viltrumitecore.city.error.no_city").withStyle(ChatFormatting.RED), false);
      }

      return 1;
   }

   private static Component onOff(boolean on) {
      return line(on ? "viltrumitecore.city.on" : "viltrumitecore.city.off").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
   }

   private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      CityManager m = city(c);
      CityProfile p = m.profile;
      ok(c, line("viltrumitecore.city.status.title", p.name, p.mapVersion).withStyle(ChatFormatting.GOLD), false);
      ok(c, line("viltrumitecore.city.status.enabled", onOff(m.enabled)), false);
      int cap = CityPopulation.levelCap(p.maxTotal, m.density);
      ok(c, line("viltrumitecore.city.status.citizens", m.citizenCount(), cap,
         CityPopulation.targetPerPlayer(p.perPlayer, m.density)), false);
      ok(c, line("viltrumitecore.city.status.density", String.format(Locale.ROOT, "%.2f", m.density)), false);
      // active districts: where players or citizens are now
      Map<String, int[]> active = new LinkedHashMap<>();
      for (ServerPlayer pl : m.level.players()) {
         CityProfile.District d = p.districtAt(pl.getX(), pl.getZ());
         if (d != null) {
            active.computeIfAbsent(d.name(), k -> new int[2])[0]++;
         }
      }

      for (CitizenEntity ce : m.citizens) {
         CityProfile.District d = p.districtAt(ce.getX(), ce.getZ());
         if (d != null) {
            active.computeIfAbsent(d.name(), k -> new int[2])[1]++;
         }
      }

      if (active.isEmpty()) {
         ok(c, line("viltrumitecore.city.status.districts_none", p.districts.size()), false);
      } else {
         StringBuilder sb = new StringBuilder();
         for (Map.Entry<String, int[]> e : active.entrySet()) {
            if (sb.length() > 0) {
               sb.append(", ");
            }

            sb.append(e.getKey()).append(" (").append(e.getValue()[1]).append(')');
         }

         ok(c, line("viltrumitecore.city.status.districts", sb.toString(), p.districts.size()), false);
      }

      ok(c, line("viltrumitecore.city.status.danger", m.danger.size()), false);
      if (m.restore.running()) {
         ok(c, line("viltrumitecore.city.status.restore_running", m.restore.restoredBlocks(), m.restore.chunksLeft(), m.restore.pass())
            .withStyle(ChatFormatting.YELLOW), false);
      } else if (m.journal.isEmpty()) {
         ok(c, line("viltrumitecore.city.status.restore_clean").withStyle(ChatFormatting.GREEN), false);
      } else {
         ok(c, line("viltrumitecore.city.status.restore_dirty", m.journal.size(), m.journal.chunkCount()), false);
      }

      return m.citizenCount();
   }

   private static int enable(CommandContext<CommandSourceStack> c, boolean on) throws CommandSyntaxException {
      CityManager m = city(c);
      if (on && m.restore.running()) {
         throw RESTORING.create();
      }

      int had = m.citizenCount();
      m.setEnabled(on);
      if (on) {
         ok(c, line("viltrumitecore.city.enabled", m.profile.name).withStyle(ChatFormatting.GREEN), true);
      } else {
         ok(c, line("viltrumitecore.city.disabled", had).withStyle(ChatFormatting.YELLOW), true);
      }

      return 1;
   }

   private static int showDensity(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      CityManager m = city(c);
      ok(c, line("viltrumitecore.city.status.density", String.format(Locale.ROOT, "%.2f", m.density)), false);
      return (int)Math.round(m.density * 100);
   }

   private static int density(CommandContext<CommandSourceStack> c, double v) throws CommandSyntaxException {
      CityManager m = city(c);
      m.setDensity(v);
      ok(c, line("viltrumitecore.city.density.set", String.format(Locale.ROOT, "%.2f", m.density),
         CityPopulation.targetPerPlayer(m.profile.perPlayer, m.density)), true);
      if (!m.enabled) {
         ok(c, line("viltrumitecore.city.density.hint").withStyle(ChatFormatting.GRAY), false);
      }

      return 1;
   }

   private static int populate(CommandContext<CommandSourceStack> c, int n) throws CommandSyntaxException {
      CityManager m = city(c);
      if (m.restore.running()) {
         throw RESTORING.create();
      }

      if (!m.enabled) {
         throw NOT_ENABLED.create();
      }

      Vec3 at = c.getSource().getPosition();
      int made = m.population.populate(at, n);
      ok(c, line("viltrumitecore.city.populated", made, n), true);
      return made;
   }

   private static int clear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      CityManager m = city(c);
      int n = m.population.clearAll();
      ok(c, line("viltrumitecore.city.cleared", n), true);
      if (m.enabled) {
         ok(c, line("viltrumitecore.city.cleared.hint").withStyle(ChatFormatting.GRAY), false);
      }

      return n;
   }

   private static String who(CommandSourceStack s) {
      return s.getEntity() == null ? "#server" : s.getEntity().getStringUUID();
   }

   private static int resetWarn(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      CityManager m = city(c);
      if (m.restore.running()) {
         ok(c, line("viltrumitecore.city.status.restore_running", m.restore.restoredBlocks(), m.restore.chunksLeft(), m.restore.pass())
            .withStyle(ChatFormatting.YELLOW), false);
         return 0;
      }

      if (m.journal.isEmpty()) {
         ok(c, line("viltrumitecore.city.reset.nothing").withStyle(ChatFormatting.GREEN), false);
         return 0;
      }

      PENDING_RESET.put(who(c.getSource()), m.now() + CONFIRM_WINDOW);
      ok(c, line("viltrumitecore.city.reset.warn", m.journal.size(), m.journal.chunkCount()).withStyle(ChatFormatting.GOLD), false);
      ok(c, line("viltrumitecore.city.reset.warn2", CONFIRM_WINDOW / 20).withStyle(ChatFormatting.YELLOW), false);
      return 1;
   }

   private static int resetConfirm(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      CityManager m = city(c);
      Long until = PENDING_RESET.remove(who(c.getSource()));
      if (until == null || m.now() > until) {
         throw NO_CONFIRM.create();
      }

      if (m.restore.running()) {
         throw RESTORING.create();
      }

      if (m.journal.isEmpty()) {
         ok(c, line("viltrumitecore.city.reset.nothing").withStyle(ChatFormatting.GREEN), false);
         return 0;
      }

      long blocks = m.journal.size();
      m.startReset();
      ok(c, line("viltrumitecore.city.reset.started", blocks, m.journal.chunkCount()).withStyle(ChatFormatting.YELLOW), true);
      return 1;
   }

   private static int resetCancel(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      CityManager m = city(c);
      Long pending = PENDING_RESET.remove(who(c.getSource()));
      if (!m.restore.running()) {
         ok(c, line(pending != null && m.now() <= pending ? "viltrumitecore.city.reset.request_cancelled" : "viltrumitecore.city.reset.not_running"), false);
         return 0;
      }

      m.restore.abort();
      m.journal.restoring = false;
      m.journal.markDirty();
      m.saveJournal(false);
      ok(c, line("viltrumitecore.city.reset.cancelled", m.journal.size()).withStyle(ChatFormatting.YELLOW), true);
      return 1;
   }

   /** Developer probe: district and pedestrian cell under the caller. */
   private static int here(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      CityManager m = city(c);
      BlockPos p = BlockPos.containing(c.getSource().getPosition());
      CityProfile.District d = m.profile.districtAt(p.getX(), p.getZ());
      int cell = m.grid.cell(p.getX(), p.getZ());
      String cls = cell == 0 ? "-" : WalkTile.cls(cell) == WalkTile.PREFERRED ? "preferred" : "walk";
      String feet = cell == 0 ? "-" : Integer.toString(WalkTile.feetY(cell));
      ok(c, Component.literal(String.format(Locale.ROOT, "[city] %d %d %d district=%s arena=%s cell=%s feetY=%s standable=%s danger=%s",
         p.getX(), p.getY(), p.getZ(), d == null ? "-" : d.name(), m.profile.inArena(p.getX(), p.getY(), p.getZ()), cls, feet,
         m.grid.standable(p.getX(), p.getY(), p.getZ()), m.danger.dangerous(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, m.now()))), false);
      return cell == 0 ? 0 : 1;
   }

}
