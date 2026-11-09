package dev.baranhan.viltrumitecore.perf;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.baranhan.viltrumitecore.ViltrumiteCore;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.PerfStatsS2CPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * {@code /vperf} (op): {@code status} — server TPS, tick time, load and every budget; {@code overlay [on|off]} — the
 * on-screen performance overlay with live server numbers; {@code reset} — forget the measurements;
 * {@code stress ...} — see {@link PerfStress}.
 */
@Mod.EventBusSubscriber(modid = ViltrumiteCore.MOD_ID)
public final class PerfCommands {
   private static final Set<UUID> WATCHERS = ConcurrentHashMap.newKeySet();
   private static int ticks;

   private PerfCommands() {
   }

   @SubscribeEvent
   public static void onRegisterCommands(RegisterCommandsEvent event) {
      event.getDispatcher().register(Commands.literal("vperf").requires(source -> source.hasPermission(2))
         .executes(PerfCommands::status)
         .then(Commands.literal("status").executes(PerfCommands::status))
         .then(Commands.literal("overlay").executes(ctx -> overlay(ctx, null))
            .then(Commands.literal("on").executes(ctx -> overlay(ctx, Boolean.TRUE)))
            .then(Commands.literal("off").executes(ctx -> overlay(ctx, Boolean.FALSE))))
         .then(Commands.literal("reset").executes(PerfCommands::reset))
         .then(PerfStress.node()));
   }

   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase != TickEvent.Phase.END || WATCHERS.isEmpty() || ++ticks < 20) {
         return;
      }

      ticks = 0;
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server == null) {
         return;
      }

      for (UUID id : List.copyOf(WATCHERS)) {
         ServerPlayer player = server.getPlayerList().getPlayer(id);
         if (player == null) {
            WATCHERS.remove(id);
         } else {
            CoreMessages.sendToPlayer(snapshot(PerfStatsS2CPacket.STATS, player.serverLevel()), player);
         }
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
      WATCHERS.remove(event.getEntity().getUUID());
   }

   public static PerfStatsS2CPacket snapshot(byte kind, ServerLevel level) {
      MsptWindow.Stats stats = ServerPerf.stats();
      List<TickBudget> budgets = new ArrayList<>();
      for (TickBudget budget : PerfRegistry.all()) {
         if (!budget.name().equals("particles") && budgets.size() < PerfStatsS2CPacket.MAX_BUDGETS) {
            budgets.add(budget);
         }
      }

      int n = budgets.size();
      String[] names = new String[n];
      int[] used = new int[n];
      int[] capacity = new int[n];
      int[] denied = new int[n];
      for (int i = 0; i < n; i++) {
         TickBudget budget = budgets.get(i);
         names[i] = budget.name();
         used[i] = budget.lastUsed();
         capacity[i] = budget.lastCapacity();
         denied[i] = budget.lastDenied();
      }

      int entities = 0;
      for (Entity ignored : level.getAllEntities()) {
         entities++;
      }

      return new PerfStatsS2CPacket(kind, (float)ServerPerf.tps(), (float)stats.avgMs(), (float)stats.p95Ms(), (float)stats.maxMs(),
         (float)ServerPerf.pressure(), entities, names, used, capacity, denied);
   }

   private static int status(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack source = ctx.getSource();
      MsptWindow.Stats stats = ServerPerf.stats();
      source.sendSuccess(() -> Component.translatableWithFallback("viltrumitecore.perf.status.server",
         "Server: %s TPS, tick %s ms (p95 %s, max %s), load %s%%",
         f1(ServerPerf.tps()), f1(stats.avgMs()), f1(stats.p95Ms()), f1(stats.maxMs()), pct(ServerPerf.pressure())), false);
      for (TickBudget budget : PerfRegistry.all()) {
         source.sendSuccess(() -> Component.translatableWithFallback("viltrumitecore.perf.status.budget",
            "  %s: %s of %s last tick, %s deferred (since start: %s done, %s deferred)",
            budget.name(), budget.lastUsed(), budget.lastCapacity(), budget.lastDenied(), budget.grantedTotal(), budget.deniedTotal()), false);
      }

      return 1;
   }

   private static int overlay(CommandContext<CommandSourceStack> ctx, Boolean on) throws CommandSyntaxException {
      ServerPlayer player = ctx.getSource().getPlayerOrException();
      boolean show = on != null ? on : !WATCHERS.contains(player.getUUID());
      if (show) {
         WATCHERS.add(player.getUUID());
      } else {
         WATCHERS.remove(player.getUUID());
      }

      CoreMessages.sendToPlayer(snapshot(show ? PerfStatsS2CPacket.SHOW : PerfStatsS2CPacket.HIDE, player.serverLevel()), player);
      ctx.getSource().sendSuccess(() -> show
         ? Component.translatableWithFallback("viltrumitecore.perf.overlay.on", "Performance overlay on")
         : Component.translatableWithFallback("viltrumitecore.perf.overlay.off", "Performance overlay off"), false);
      return 1;
   }

   private static int reset(CommandContext<CommandSourceStack> ctx) {
      ServerPerf.reset();
      ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("viltrumitecore.perf.reset", "Performance measurements reset"), true);
      return 1;
   }

   static String f1(double value) {
      return String.format(Locale.ROOT, "%.1f", value);
   }

   static String pct(double share) {
      return Integer.toString((int)Math.round(Math.max(0.0, Math.min(1.0, share)) * 100.0));
   }
}
