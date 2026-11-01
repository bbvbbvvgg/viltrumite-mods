package dev.baranhan.viltrumitecore.worldevent.aftermath;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /aftermath} (operators): status and list of the ruin zones, clear them, add a test ruin through the
 * shared contract ({@code add}, {@code collapse}), move the nearest one to a stage ({@code stage}) and cut or
 * restore power around you ({@code power}) - the tools of the playtest in {@code docs/city-aftermath-integration.md}.
 */
public final class AftermathCommands {
   private static final SimpleCommandExceptionType DISABLED = new SimpleCommandExceptionType(Component.translatable("commands.viltrumitecore.aftermath.disabled"));
   private static final SimpleCommandExceptionType NONE = new SimpleCommandExceptionType(Component.translatable("commands.viltrumitecore.aftermath.none"));
   private static int added;

   private AftermathCommands() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> d) {
      d.register(Commands.literal("aftermath").requires(s -> s.hasPermission(2))
         .then(Commands.literal("status").executes(AftermathCommands::status))
         .then(Commands.literal("list").executes(AftermathCommands::list))
         .then(Commands.literal("clear").executes(c -> clear(c, false)).then(Commands.literal("nearest").executes(c -> clear(c, true))))
         .then(Commands.literal("add").then(Commands.argument("cause", StringArgumentType.word())
            .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(AftermathCause.values()).map(x -> x.id), b))
            .executes(c -> add(c, 10.0F, 0.6F, false))
            .then(Commands.argument("radius", FloatArgumentType.floatArg(2.0F, AftermathRules.MAX_RADIUS))
               .executes(c -> add(c, FloatArgumentType.getFloat(c, "radius"), 0.6F, false))
               .then(Commands.argument("severity", FloatArgumentType.floatArg(0.0F, 1.0F))
                  .executes(c -> add(c, FloatArgumentType.getFloat(c, "radius"), FloatArgumentType.getFloat(c, "severity"), false))))))
         .then(Commands.literal("collapse").executes(c -> add(c, 8.0F, 0.7F, true))
            .then(Commands.argument("radius", FloatArgumentType.floatArg(2.0F, AftermathRules.MAX_RADIUS))
               .executes(c -> add(c, FloatArgumentType.getFloat(c, "radius"), 0.7F, true))
               .then(Commands.argument("severity", FloatArgumentType.floatArg(0.0F, 1.0F))
                  .executes(c -> add(c, FloatArgumentType.getFloat(c, "radius"), FloatArgumentType.getFloat(c, "severity"), true)))))
         .then(Commands.literal("stage").then(Commands.argument("stage", StringArgumentType.word())
            .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("impact", "smolder", "late", "scar"), b))
            .executes(AftermathCommands::stage)))
         .then(Commands.literal("power")
            .then(Commands.literal("off").executes(c -> power(c, false, 48.0F))
               .then(Commands.argument("radius", FloatArgumentType.floatArg(4.0F, 512.0F)).executes(c -> power(c, false, FloatArgumentType.getFloat(c, "radius")))))
            .then(Commands.literal("on").executes(c -> power(c, true, 0.0F)))));
   }

   private static void check() throws CommandSyntaxException {
      if (!CityAftermath.enabled()) {
         throw DISABLED.create();
      }
   }

   private static void ok(CommandContext<CommandSourceStack> c, Component msg) {
      c.getSource().sendSuccess(() -> msg, false);
   }

   private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      check();
      ServerLevel level = c.getSource().getLevel();
      AftermathData data = AftermathData.get(level);
      ok(c, Component.translatable("commands.viltrumitecore.aftermath.status", data.zones.size(), data.outages.size(),
         String.format(Locale.ROOT, "%.2f", CityAftermath.scale())).withStyle(ChatFormatting.GOLD));
      AftermathZone zn = CityAftermath.nearest(level, c.getSource().getPosition().x, c.getSource().getPosition().y, c.getSource().getPosition().z, 4096.0);
      if (zn != null) {
         ok(c, line(level, zn));
      }

      return data.zones.size();
   }

   private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      check();
      ServerLevel level = c.getSource().getLevel();
      List<AftermathZone> zones = new ArrayList<>(AftermathData.get(level).zones);
      Vec3 at = c.getSource().getPosition();
      zones.sort((a, b) -> Double.compare(a.distanceSq(at.x, at.y, at.z), b.distanceSq(at.x, at.y, at.z)));
      if (zones.isEmpty()) {
         throw NONE.create();
      }

      for (int i = 0; i < zones.size() && i < 16; i++) {
         ok(c, line(level, zones.get(i)));
      }

      return zones.size();
   }

   static Component line(ServerLevel level, AftermathZone zn) {
      long now = level.getGameTime();
      double scale = CityAftermath.scale();
      StringBuilder causes = new StringBuilder();
      for (AftermathCause cause : AftermathCause.values()) {
         if ((zn.causes & cause.bit()) != 0) {
            causes.append(causes.length() == 0 ? "" : ",").append(cause.id);
         }
      }

      Component power = (zn.flags & AftermathRules.FLAG_POWER_OUT) != 0 ? Component.translatable("commands.viltrumitecore.aftermath.power_out") : Component.empty();
      return Component.translatable("commands.viltrumitecore.aftermath.zone", zn.id, (int)Math.floor(zn.x), (int)Math.floor(zn.y), (int)Math.floor(zn.z),
         String.format(Locale.ROOT, "%.1f", zn.radius), String.format(Locale.ROOT, "%.2f", zn.severity), zn.stage(now, scale).id, zn.age(now) / 20,
         causes.toString(), power);
   }

   private static int clear(CommandContext<CommandSourceStack> c, boolean nearest) throws CommandSyntaxException {
      ServerLevel level = c.getSource().getLevel();
      if (nearest) {
         Vec3 at = c.getSource().getPosition();
         AftermathZone zn = CityAftermath.nearest(level, at.x, at.y, at.z, 64.0);
         if (zn == null) {
            throw NONE.create();
         }

         CityAftermath.remove(level, zn);
         ok(c, Component.translatable("commands.viltrumitecore.aftermath.cleared", 1));
         return 1;
      }

      int n = AftermathData.get(level).zones.size();
      CityAftermath.clear(level);
      ok(c, Component.translatable("commands.viltrumitecore.aftermath.cleared", n));
      return n;
   }

   private static int add(CommandContext<CommandSourceStack> c, float radius, float severity, boolean collapse) throws CommandSyntaxException {
      check();
      ServerLevel level = c.getSource().getLevel();
      Vec3 at = c.getSource().getPosition();
      BlockPos center = BlockPos.containing(at);
      AftermathCause cause = collapse ? AftermathCause.COLLAPSE : AftermathCause.of(StringArgumentType.getString(c, "cause"));
      String id = "command:" + level.getGameTime() + ":" + ++added;
      AftermathEvent e = AftermathEvent.of(level, id, center, radius, severity, cause, List.of());
      int r = Math.round(radius);
      int zone = collapse
         ? CityAftermath.registerCollapse(level, e, BoundingBox.fromCorners(center.offset(-r / 2, -2, -r / 2), center.offset(r / 2, r, r / 2)))
         : CityAftermath.registerDamage(level, e);
      ok(c, Component.translatable("commands.viltrumitecore.aftermath.added", zone, cause.id));
      return zone;
   }

   private static int stage(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      check();
      ServerLevel level = c.getSource().getLevel();
      Vec3 at = c.getSource().getPosition();
      AftermathZone zn = CityAftermath.nearest(level, at.x, at.y, at.z, 64.0);
      if (zn == null) {
         throw NONE.create();
      }

      AftermathStage stage;
      try {
         stage = AftermathStage.valueOf(StringArgumentType.getString(c, "stage").toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         stage = AftermathStage.SMOLDER;
      }

      CityAftermath.setAge(level, zn, AftermathRules.stageStart(stage, zn.factor(CityAftermath.scale())) + 20L);
      ok(c, Component.translatable("commands.viltrumitecore.aftermath.aged", zn.id, stage.id));
      return zn.id;
   }

   private static int power(CommandContext<CommandSourceStack> c, boolean on, float radius) throws CommandSyntaxException {
      check();
      ServerLevel level = c.getSource().getLevel();
      CityAftermath.setPower(level, "command", BlockPos.containing(c.getSource().getPosition()), radius, on);
      ok(c, Component.translatable(on ? "commands.viltrumitecore.aftermath.power_on" : "commands.viltrumitecore.aftermath.power_off", Math.round(radius)));
      return 1;
   }
}
