package dev.baranhan.viltrumitecore.t1000;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /t1000 status | spawn [pos] | remove | natural on|off|check|force | config chance|delay|interval|max <v> |
 * debug reveal|conceal|puddle|destroy|blade|hook|split} — operators (level 2). Every message is translatable
 * (lang overlay t1000, mirrored for dedicated consoles) and none names a position: the feedback goes to the
 * operator who asked only, and the infiltrator's hiding place never shows up in chat.
 */
public final class T1000Commands {
   private static final String K = "commands.viltrumitecore.t1000.";

   private T1000Commands() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> d) {
      d.register(Commands.literal("t1000").requires(s -> s.hasPermission(2))
         .then(Commands.literal("status").executes(c -> status(c.getSource())))
         .then(Commands.literal("spawn").executes(c -> spawn(c.getSource(), c.getSource().getPosition()))
            .then(Commands.argument("pos", Vec3Argument.vec3()).executes(c -> spawn(c.getSource(), Vec3Argument.getVec3(c, "pos")))))
         .then(Commands.literal("remove").executes(c -> remove(c.getSource())))
         .then(Commands.literal("natural")
            .then(Commands.literal("on").executes(c -> natural(c.getSource(), true)))
            .then(Commands.literal("off").executes(c -> natural(c.getSource(), false)))
            .then(Commands.literal("check").executes(c -> check(c.getSource(), false)))
            .then(Commands.literal("force").executes(c -> check(c.getSource(), true))))
         .then(Commands.literal("config")
            .then(Commands.literal("chance").then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
               .executes(c -> config(c.getSource(), "chance", DoubleArgumentType.getDouble(c, "percent")))))
            .then(Commands.literal("delay").then(Commands.argument("minutes", DoubleArgumentType.doubleArg(0.0, 144000.0))
               .executes(c -> config(c.getSource(), "delay", DoubleArgumentType.getDouble(c, "minutes")))))
            .then(Commands.literal("interval").then(Commands.argument("seconds", DoubleArgumentType.doubleArg(T1000Rules.MIN_CHECK_SECONDS, T1000Rules.MAX_CHECK_SECONDS))
               .executes(c -> config(c.getSource(), "interval", DoubleArgumentType.getDouble(c, "seconds")))))
            .then(Commands.literal("max").then(Commands.argument("count", IntegerArgumentType.integer(1, T1000Rules.MAX_ACTIVE_LIMIT))
               .executes(c -> config(c.getSource(), "max", IntegerArgumentType.getInteger(c, "count"))))))
         .then(Commands.literal("debug")
            .then(Commands.literal("reveal").executes(c -> debug(c.getSource(), "reveal")))
            .then(Commands.literal("conceal").executes(c -> debug(c.getSource(), "conceal")))
            .then(Commands.literal("puddle").executes(c -> debug(c.getSource(), "puddle")))
            .then(Commands.literal("blade").executes(c -> debug(c.getSource(), "blade")))
            .then(Commands.literal("hook").executes(c -> debug(c.getSource(), "hook")))
            .then(Commands.literal("split").executes(c -> debug(c.getSource(), "split")))
            .then(Commands.literal("hunt").executes(c -> debug(c.getSource(), "hunt")))
            .then(Commands.literal("destroy").executes(c -> debug(c.getSource(), "destroy")))));
   }

   private static String f(double v, int digits) {
      return String.format(Locale.ROOT, "%." + digits + "f", v);
   }

   private static List<T1000Entity> loaded(MinecraftServer server) {
      List<T1000Entity> out = new ArrayList<>();
      T1000SavedData data = T1000SavedData.get(server);
      for (T1000SavedData.Instance i : data.instances()) {
         if (i.dormant) {
            continue;
         }
         for (ServerLevel l : server.getAllLevels()) {
            Entity e = l.getEntity(i.id);
            if (e instanceof T1000Entity t) {
               out.add(t);
            }
         }
      }
      return out;
   }

   private static int status(CommandSourceStack src) {
      MinecraftServer server = src.getServer();
      T1000SavedData data = T1000SavedData.get(server);
      ViltrumiteCoreConfig cfg = ViltrumiteCoreConfig.INSTANCE;
      long now = server.overworld().getGameTime();
      long next = Math.max(0L, data.nextCheck() - now) / 20L;
      long delay = T1000Rules.respawnDelayTicks(cfg.t1000RespawnDelayMinutes);
      long delayLeft = data.lastDestroyed() == Long.MIN_VALUE ? 0L : Math.max(0L, data.lastDestroyed() + delay - now) / 20L;
      Component onOff = Component.translatable(K + (data.naturalEnabled() ? "on" : "off"));
      double expected = T1000SpawnRules.expectedMinutes(cfg.t1000NaturalCheckSeconds, cfg.t1000NaturalChancePercent);
      src.sendSuccess(() -> Component.translatable(K + "status.header", onOff, next, T1000Rules.checkIntervalTicks(cfg.t1000NaturalCheckSeconds) / 20L,
         f(T1000Rules.chance(cfg.t1000NaturalChancePercent) * 100.0, 3), Double.isInfinite(expected) ? "-" : f(expected / 60.0, 1),
         f(cfg.t1000RespawnDelayMinutes, 0), delayLeft, T1000Rules.maxActive(cfg.t1000MaxActive)), false);
      List<T1000Entity> live = loaded(server);
      src.sendSuccess(() -> Component.translatable(K + "status.counts", live.size(), data.dormantCount(), data.destroyedTotal()), false);
      int n = 0;
      for (T1000Entity e : live) {
         int idx = ++n;
         src.sendSuccess(() -> Component.translatable(K + "status.entry", idx, Component.translatable("t1000.viltrumitecore.state." + e.brain().state().name().toLowerCase(Locale.ROOT)),
            Component.translatable("t1000.viltrumitecore.form." + e.form().name().toLowerCase(Locale.ROOT)), f(e.integrity(), 0), f(e.getHealth(), 0),
            f(e.getMaxHealth(), 0)), false);
      }
      return live.size() + data.dormantCount();
   }

   private static int spawn(CommandSourceStack src, Vec3 at) {
      ServerLevel level = src.getLevel();
      T1000Entity e = T1000Spawner.spawnAt(level, at);
      if (e == null) {
         src.sendFailure(Component.translatable(K + "spawn.fail"));
         return 0;
      }
      if (CityManager.of(level) == null) {
         src.sendSuccess(() -> Component.translatable(K + "spawn.no_city"), false);
      }
      src.sendSuccess(() -> Component.translatable(K + "spawn.ok"), true);
      return 1;
   }

   private static int remove(CommandSourceStack src) {
      MinecraftServer server = src.getServer();
      List<T1000Entity> live = loaded(server);
      for (T1000Entity e : live) {
         e.removeByOperator();
      }
      int forgotten = T1000SavedData.get(server).forgetAll();
      int total = Math.max(live.size(), forgotten);
      src.sendSuccess(() -> Component.translatable(K + "remove.ok", total), true);
      return total;
   }

   private static int natural(CommandSourceStack src, boolean on) {
      T1000SavedData.get(src.getServer()).setNaturalEnabled(on);
      src.sendSuccess(() -> Component.translatable(K + "natural." + (on ? "on" : "off")), true);
      return 1;
   }

   private static int check(CommandSourceStack src, boolean force) {
      T1000Spawner.Attempt a = T1000Spawner.attempt(src.getServer(), force);
      Component result = Component.translatable(K + "result." + a.result().name().toLowerCase(Locale.ROOT));
      src.sendSuccess(() -> Component.translatable(K + "check", result), false);
      return a.result() == T1000SpawnRules.Result.SPAWNED ? 1 : 0;
   }

   private static int config(CommandSourceStack src, String key, double value) {
      ViltrumiteCoreConfig cfg = ViltrumiteCoreConfig.INSTANCE;
      switch (key) {
         case "chance" -> cfg.t1000NaturalChancePercent = value;
         case "delay" -> cfg.t1000RespawnDelayMinutes = value;
         case "interval" -> {
            cfg.t1000NaturalCheckSeconds = value;
            T1000SavedData data = T1000SavedData.get(src.getServer());
            long now = src.getServer().overworld().getGameTime();
            data.setNextCheck(Math.min(data.nextCheck(), now + T1000Rules.checkIntervalTicks(value)));
         }
         default -> cfg.t1000MaxActive = (int)value;
      }
      ViltrumiteCoreConfig.save();
      String shown = key.equals("max") ? String.valueOf((int)value) : f(value, key.equals("chance") ? 3 : 0);
      src.sendSuccess(() -> Component.translatable(K + "config." + key, shown), true);
      return 1;
   }

   @Nullable
   private static T1000Entity nearest(CommandSourceStack src) {
      Vec3 at = src.getPosition();
      T1000Entity best = null;
      double bd = 96.0 * 96.0;
      for (T1000Entity e : src.getLevel().getEntitiesOfClass(T1000Entity.class, new net.minecraft.world.phys.AABB(at, at).inflate(96.0), Entity::isAlive)) {
         double d = e.distanceToSqr(at);
         if (d < bd) {
            bd = d;
            best = e;
         }
      }
      return best;
   }

   private static int debug(CommandSourceStack src, String what) {
      T1000Entity e = nearest(src);
      if (e == null) {
         src.sendFailure(Component.translatable(K + "none_near"));
         return 0;
      }
      ServerPlayer p = src.getPlayer();
      switch (what) {
         case "reveal" -> e.brain().debugReveal(p != null && T1000Brain.huntable(p) ? p : null);
         case "conceal" -> e.brain().debugConceal();
         case "puddle" -> {
            if (e.form() == T1000Form.HUMAN) {
               e.setForm(T1000Form.LIQUID);
            }
            e.melt();
         }
         case "blade" -> {
            e.setArm(true, e.rightArm() == T1000Arm.BLADE ? T1000Arm.HAND : T1000Arm.BLADE, true);
            e.setArm(false, e.leftArm() == T1000Arm.SPIKE ? T1000Arm.HAND : T1000Arm.SPIKE, true);
         }
         case "hook" -> e.setArm(false, e.leftArm() == T1000Arm.HOOK ? T1000Arm.HAND : T1000Arm.HOOK, false);
         case "split" -> e.setAction(T1000Action.FACE_SPLIT);
         case "hunt" -> e.brain().wakeUp();
         default -> e.hurt(e.damageSources().genericKill(), Float.MAX_VALUE);
      }
      src.sendSuccess(() -> Component.translatable(K + "debug." + what), false);
      return 1;
   }
}
