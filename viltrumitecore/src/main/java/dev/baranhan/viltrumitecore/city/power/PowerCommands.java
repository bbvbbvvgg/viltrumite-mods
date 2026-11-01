package dev.baranhan.viltrumitecore.city.power;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.power.api.AlarmStage;
import dev.baranhan.viltrumitecore.city.power.api.InfrastructureIncident;
import dev.baranhan.viltrumitecore.city.power.api.OutageInfo;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /city power ...}: status for everyone; blackout, damage, cascade, restore and siren for operators. Test
 * and staging tools: every outage still follows the grid rules (budgets, restoration, ledger).
 */
public final class PowerCommands {
   private static final SimpleCommandExceptionType NO_CITY = new SimpleCommandExceptionType(Component.translatable("viltrumitecore.city.error.no_city"));
   private static final SimpleCommandExceptionType OUTSIDE = new SimpleCommandExceptionType(Component.translatable("viltrumitecore.city.power.outside"));
   private static final SimpleCommandExceptionType RESTORING = new SimpleCommandExceptionType(Component.translatable("viltrumitecore.city.error.restoring"));

   private PowerCommands() {
   }

   public static LiteralArgumentBuilder<CommandSourceStack> node() {
      return Commands.literal("power")
         .executes(PowerCommands::status)
         .then(Commands.literal("status").executes(PowerCommands::status))
         .then(Commands.literal("blackout").requires(s -> s.hasPermission(2))
            .executes(c -> damage(c, 10.0F, 0.5F, "command:blackout"))
            .then(Commands.argument("radius", FloatArgumentType.floatArg(1.0F, 128.0F))
               .executes(c -> damage(c, FloatArgumentType.getFloat(c, "radius"), 0.5F, "command:blackout"))))
         .then(Commands.literal("damage").requires(s -> s.hasPermission(2))
            .then(Commands.argument("radius", FloatArgumentType.floatArg(1.0F, 128.0F))
               .then(Commands.argument("severity", FloatArgumentType.floatArg(0.05F, 1.0F))
                  .executes(c -> damage(c, FloatArgumentType.getFloat(c, "radius"), FloatArgumentType.getFloat(c, "severity"), "command:damage")))))
         .then(Commands.literal("cascade").requires(s -> s.hasPermission(2)).executes(PowerCommands::cascade))
         .then(Commands.literal("restore").requires(s -> s.hasPermission(2)).executes(PowerCommands::restore))
         .then(Commands.literal("siren").requires(s -> s.hasPermission(2))
            .then(Commands.literal("warning").executes(c -> siren(c, AlarmStage.WARNING)))
            .then(Commands.literal("active").executes(c -> siren(c, AlarmStage.ACTIVE)))
            .then(Commands.literal("critical").executes(c -> siren(c, AlarmStage.CRITICAL)))
            .then(Commands.literal("clear").executes(c -> siren(c, AlarmStage.NONE))));
   }

   private static PowerGrid grid(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      CityManager m = CityManager.active();
      if (m == null) {
         throw NO_CITY.create();
      }
      return m.power();
   }

   private static Component t(String key, Object... args) {
      return Component.translatable("viltrumitecore.city.power." + key, args);
   }

   private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      PowerGrid g = grid(c);
      Map<String, Long> s = g.stats();
      CommandSourceStack src = c.getSource();
      src.sendSuccess(() -> t("status.title").copy().withStyle(ChatFormatting.GOLD), false);
      src.sendSuccess(() -> t("status.cells", s.get("dark") + s.get("damaged"), s.get("cells"), s.get("damaged"), s.get("failing") + s.get("surge"),
         s.get("restoring")), false);
      src.sendSuccess(() -> t("status.stations", s.get("stations_tripped"), s.get("stations_worn")), false);
      src.sendSuccess(() -> t("status.alarm", t("stage." + g.alarm().stage().key()), g.alarm().leases().size(), g.alarm().reason()), false);
      src.sendSuccess(() -> t("status.fixtures", s.get("ledger"), s.get("fixtures_indexed"), s.get("scanned_chunks"), s.get("writes")), false);
      src.sendSuccess(() -> t("status.queue", s.get("timeline"), s.get("sweeps"), s.get("scan_queue"), s.get("timeline_dropped")).copy()
         .withStyle(ChatFormatting.GRAY), false);
      for (OutageInfo o : g.outages()) {
         src.sendSuccess(() -> t("status.district", o.district(), o.darkCells(), o.cells(), o.damagedCells()).copy().withStyle(ChatFormatting.YELLOW), false);
      }
      return 1;
   }

   private static int damage(CommandContext<CommandSourceStack> c, float radius, float severity, String source) throws CommandSyntaxException {
      PowerGrid g = grid(c);
      if (g.paused()) {
         throw RESTORING.create();
      }
      Vec3 at = c.getSource().getPosition();
      InfrastructureIncident inc = g.damage(at.x, at.y, at.z, radius, severity, "command", source);
      if (inc == null) {
         throw OUTSIDE.create();
      }
      c.getSource().sendSuccess(() -> t("damaged", inc.id(), String.format(java.util.Locale.ROOT, "%.2f", severity), (int)radius), true);
      return 1;
   }

   private static int cascade(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      PowerGrid g = grid(c);
      if (g.paused()) {
         throw RESTORING.create();
      }
      Vec3 at = c.getSource().getPosition();
      g.tripAt(at.x, at.z, 0.85F);
      c.getSource().sendSuccess(() -> t("tripped"), true);
      return 1;
   }

   private static int restore(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      int n = grid(c).restoreAll();
      c.getSource().sendSuccess(() -> t("restored", n), true);
      return 1;
   }

   private static int siren(CommandContext<CommandSourceStack> c, AlarmStage stage) throws CommandSyntaxException {
      PowerGrid g = grid(c);
      if (stage == AlarmStage.NONE) {
         g.alarm().release("command");
      } else {
         g.alarm().lease("command", stage, c.getSource().getPosition(), 400.0F, 20 * 60 * 3);
      }
      c.getSource().sendSuccess(() -> t("siren", t("stage." + stage.key())), true);
      return 1;
   }
}
