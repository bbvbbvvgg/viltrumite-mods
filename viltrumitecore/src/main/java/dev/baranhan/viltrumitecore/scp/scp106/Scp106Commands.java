package dev.baranhan.viltrumitecore.scp.scp106;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketWorld;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /scp106 status | spawn [player] | despawn | natural on|off|check|force | send <players> | release <players> |
 * rescue <players> | session <player> | route <player> | grant <player> sigils|key | stalk <player>} (operators, level 2).
 * {@code rescue} is the emergency recovery: whatever state a player is in, they are brought home and the session ends.
 */
public final class Scp106Commands {
   private static final String K = "commands.viltrumitecore.scp106.";

   private Scp106Commands() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> d) {
      d.register(Commands.literal("scp106").requires(s -> s.hasPermission(2))
         .then(Commands.literal("status").executes(c -> status(c.getSource())))
         .then(Commands.literal("spawn").executes(c -> spawn(c.getSource(), c.getSource().getPlayerOrException()))
            .then(Commands.argument("player", EntityArgument.player()).executes(c -> spawn(c.getSource(), EntityArgument.getPlayer(c, "player")))))
         .then(Commands.literal("despawn").executes(c -> despawn(c.getSource())))
         .then(Commands.literal("natural")
            .then(Commands.literal("on").executes(c -> natural(c.getSource(), true)))
            .then(Commands.literal("off").executes(c -> natural(c.getSource(), false)))
            .then(Commands.literal("check").executes(c -> check(c.getSource(), false)))
            .then(Commands.literal("force").executes(c -> check(c.getSource(), true))))
         .then(Commands.literal("send").then(Commands.argument("players", EntityArgument.players())
            .executes(c -> send(c.getSource(), EntityArgument.getPlayers(c, "players")))))
         .then(Commands.literal("release").then(Commands.argument("players", EntityArgument.players())
            .executes(c -> release(c.getSource(), EntityArgument.getPlayers(c, "players"), false))))
         .then(Commands.literal("rescue").then(Commands.argument("players", EntityArgument.players())
            .executes(c -> release(c.getSource(), EntityArgument.getPlayers(c, "players"), true))))
         .then(Commands.literal("session").then(Commands.argument("player", EntityArgument.player())
            .executes(c -> session(c.getSource(), EntityArgument.getPlayer(c, "player")))))
         .then(Commands.literal("route").then(Commands.argument("player", EntityArgument.player())
            .executes(c -> route(c.getSource(), EntityArgument.getPlayer(c, "player")))))
         .then(Commands.literal("grant").then(Commands.argument("player", EntityArgument.player())
            .then(Commands.literal("sigils").executes(c -> grant(c.getSource(), EntityArgument.getPlayer(c, "player"), 3, false))
               .then(Commands.argument("count", IntegerArgumentType.integer(0, 3))
                  .executes(c -> grant(c.getSource(), EntityArgument.getPlayer(c, "player"), IntegerArgumentType.getInteger(c, "count"), false))))
            .then(Commands.literal("key").executes(c -> grant(c.getSource(), EntityArgument.getPlayer(c, "player"), -1, true)))))
         .then(Commands.literal("stalk").then(Commands.argument("player", EntityArgument.player())
            .executes(c -> stalk(c.getSource(), EntityArgument.getPlayer(c, "player"))))));
   }

   private static Component msg(String key, String fallback, Object... args) {
      return Component.translatableWithFallback(K + key, fallback, args);
   }

   private static void ok(CommandSourceStack src, Component c, boolean broadcast) {
      src.sendSuccess(() -> c, broadcast);
   }

   private static int status(CommandSourceStack src) {
      MinecraftServer server = src.getServer();
      Scp106SavedData data = Scp106SavedData.get(server);
      ViltrumiteCoreConfig cfg = ViltrumiteCoreConfig.INSTANCE;
      long now = server.overworld().getGameTime();
      long next = Math.max(0L, data.nextCheck() - now) / 20L;
      long cd = Scp106Rules.cooldownTicks(cfg.scp106NaturalCooldownMinutes);
      long cdLeft = data.lastNatural() == Long.MIN_VALUE ? 0L : Math.max(0L, data.lastNatural() + cd - now) / 20L;
      ok(src, msg("status.header", "SCP-106: natural %s, next roll in %ss, cooldown %ss left (roll every %ss, %s%%, cooldown %s min)",
         msg(data.naturalEnabled() ? "on" : "off", data.naturalEnabled() ? "on" : "off"), next, cdLeft,
         Scp106Rules.checkTicks(cfg.scp106NaturalCheckSeconds) / 20, String.format(Locale.ROOT, "%.2f", cfg.scp106NaturalChancePercent),
         String.format(Locale.ROOT, "%.0f", cfg.scp106NaturalCooldownMinutes)), false);
      List<Scp106Entity> all = Scp106Entity.loaded();
      if (all.isEmpty()) {
         ok(src, msg("status.none", "No Old Man is loaded."), false);
      }
      for (Scp106Entity e : all) {
         ok(src, Component.literal(" - " + e.describe()), false);
      }
      ok(src, msg("status.sessions", "Players in the pocket dimension: %s (next instance %s)", data.sessions().size(), data.nextSlot()), false);
      for (Map.Entry<UUID, PocketSession> en : data.sessions().entrySet()) {
         ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
         ok(src, Component.literal(" - " + (p == null ? en.getKey() + " (offline)" : p.getGameProfile().getName()) + ": " + line(en.getValue(), now)), false);
      }
      return all.size();
   }

   private static String line(PocketSession s, long now) {
      return String.format(Locale.ROOT, "instance %d, phase %d, sigils %d/3, key %s, %ds inside, deaths %d, drags %d", s.slot, s.phase,
         s.sigilCount(), s.key ? "yes" : "no", Math.max(0L, now - s.entered) / 20L, s.deaths, s.drags);
   }

   private static int spawn(CommandSourceStack src, ServerPlayer p) {
      ServerLevel level = p.serverLevel();
      Scp106Entity e = Scp106Entity.emergeNear(level, p, 5.0, 10.0, false);
      if (e == null) {
         src.sendFailure(msg("spawn.no_site", "No free floor near %s for it to rise from.", p.getDisplayName()));
         return 0;
      }
      if (!Scp106Pocket.canTake(p)) {
         ok(src, msg("spawn.cannot_take", "Note: %s cannot be taken right now (creative, spectator, inside, or recently escaped); it will hunt someone else or leave.",
            p.getDisplayName()), false);
      }
      ok(src, msg("spawn.done", "The Old Man rises near %s.", p.getDisplayName()), true);
      return 1;
   }

   private static int despawn(CommandSourceStack src) {
      int n = 0;
      for (Scp106Entity e : Scp106Entity.loaded()) {
         e.discard();
         n++;
      }
      final int count = n;
      ok(src, msg("despawn.done", "Removed %s Old Man instance(s).", count), true);
      return n;
   }

   private static int natural(CommandSourceStack src, boolean on) {
      Scp106SavedData.get(src.getServer()).setNaturalEnabled(on);
      ok(src, msg("natural." + (on ? "on" : "off"), on ? "Natural appearances enabled." : "Natural appearances disabled."), true);
      return 1;
   }

   private static int check(CommandSourceStack src, boolean force) {
      Scp106Spawner.Attempt a = Scp106Spawner.attempt(src.getServer(), force);
      String r = a.result().name().toLowerCase(Locale.ROOT);
      ok(src, msg("natural.result." + r, "Natural attempt: " + r), false);
      return a.result() == Scp106Spawner.Result.SPAWNED ? 1 : 0;
   }

   private static int send(CommandSourceStack src, Collection<ServerPlayer> players) {
      int n = 0;
      for (ServerPlayer p : players) {
         if (Scp106Pocket.capture(p)) {
            n++;
            ok(src, msg("send.done", "%s is now in pocket instance %s.", p.getDisplayName(), Scp106SavedData.get(p.server).session(p.getUUID()).slot), true);
         } else {
            src.sendFailure(msg("send.failed", "%s cannot be sent (already inside, or the dimension is missing).", p.getDisplayName()));
         }
      }
      return n;
   }

   private static int release(CommandSourceStack src, Collection<ServerPlayer> players, boolean emergency) {
      int n = 0;
      for (ServerPlayer p : players) {
         PocketSession s = Scp106SavedData.get(p.server).session(p.getUUID());
         boolean done;
         if (emergency) {
            done = Scp106Pocket.rescue(p);
         } else if (s != null) {
            Scp106Pocket.escape(p, s, Scp106Pocket.ROUTE_RELEASED);
            done = true;
         } else {
            done = false;
         }
         if (done) {
            n++;
            ok(src, msg(emergency ? "rescue.done" : "release.done", emergency ? "%s was brought back (emergency recovery)." : "%s was released.",
               p.getDisplayName()), true);
         } else {
            src.sendFailure(msg("release.none", "%s is not in the pocket dimension.", p.getDisplayName()));
         }
      }
      return n;
   }

   private static PocketSession need(CommandSourceStack src, ServerPlayer p) {
      PocketSession s = Scp106SavedData.get(p.server).session(p.getUUID());
      if (s == null) {
         src.sendFailure(msg("release.none", "%s is not in the pocket dimension.", p.getDisplayName()));
      }
      return s;
   }

   private static int session(CommandSourceStack src, ServerPlayer p) {
      PocketSession s = need(src, p);
      if (s == null) {
         return 0;
      }
      long now = src.getServer().overworld().getGameTime();
      ok(src, Component.literal(p.getGameProfile().getName() + ": " + line(s, now) + ", origin " + s.originDim
         + String.format(Locale.ROOT, " %.0f %.0f %.0f", s.ox, s.oy, s.oz) + ", seed " + Long.toHexString(PocketWorld.layout(s.slot).seed)
         + ", origin x/z " + PocketLayout.slotOriginX(s.slot) + "/" + PocketLayout.slotOriginZ(s.slot)), false);
      return 1;
   }

   private static int route(CommandSourceStack src, ServerPlayer p) {
      PocketSession s = need(src, p);
      if (s == null) {
         return 0;
      }
      for (String line : Scp106Pocket.route(s)) {
         ok(src, Component.literal(" " + line), false);
      }
      return 1;
   }

   private static int grant(CommandSourceStack src, ServerPlayer p, int sigils, boolean key) {
      PocketSession s = need(src, p);
      if (s == null) {
         return 0;
      }
      if (key) {
         s.key = true;
      } else {
         s.sigils = (1 << sigils) - 1;
      }
      Scp106SavedData.get(p.server).setDirty();
      Scp106Pocket.sendState(p, s);
      ok(src, msg("grant.done", "%s: sigils %s/3, key %s.", p.getDisplayName(), s.sigilCount(), s.key ? "yes" : "no"), true);
      return 1;
   }

   private static int stalk(CommandSourceStack src, ServerPlayer p) throws CommandSyntaxException {
      PocketSession s = need(src, p);
      if (s == null) {
         return 0;
      }
      PocketLayout l = PocketWorld.layout(s.slot);
      PocketLayout.Room room = l.roomAtBlock(p.getBlockX() - PocketLayout.slotOriginX(s.slot), p.getBlockZ() - PocketLayout.slotOriginZ(s.slot));
      Scp106Entity e = room == null ? null : Scp106Entity.spawnPocketHunter(p.serverLevel(), p, l, room);
      if (e == null) {
         src.sendFailure(msg("stalk.failed", "No hunt possible here (a hunter already exists, or no room for it)."));
         return 0;
      }
      ok(src, msg("stalk.done", "A hunt begins for %s.", p.getDisplayName()), true);
      return 1;
   }
}
