package dev.baranhan.viltrumitecore.scp.scp096;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /scp096 status | spawn [pos] [sit|stand|wander] | despawn | natural on|off|check|force | trigger [targets] |
 * calm} (operators, level 2). Every message is translatable (lang overlay scp096, mirrored for dedicated consoles).
 */
public final class Scp096Commands {
   private static final String K = "commands.viltrumitecore.scp096.";

   private Scp096Commands() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> d) {
      d.register(Commands.literal("scp096").requires(s -> s.hasPermission(2))
         .then(Commands.literal("status").executes(c -> status(c.getSource())))
         .then(Commands.literal("spawn").executes(c -> spawn(c.getSource(), null, Scp096State.SIT))
            .then(Commands.argument("pos", Vec3Argument.vec3()).executes(c -> spawn(c.getSource(), Vec3Argument.getVec3(c, "pos"), Scp096State.SIT))
               .then(Commands.literal("sit").executes(c -> spawn(c.getSource(), Vec3Argument.getVec3(c, "pos"), Scp096State.SIT)))
               .then(Commands.literal("stand").executes(c -> spawn(c.getSource(), Vec3Argument.getVec3(c, "pos"), Scp096State.STAND)))
               .then(Commands.literal("wander").executes(c -> spawn(c.getSource(), Vec3Argument.getVec3(c, "pos"), Scp096State.WANDER)))))
         .then(Commands.literal("despawn").executes(c -> despawn(c.getSource())))
         .then(Commands.literal("natural")
            .then(Commands.literal("on").executes(c -> natural(c.getSource(), true)))
            .then(Commands.literal("off").executes(c -> natural(c.getSource(), false)))
            .then(Commands.literal("check").executes(c -> check(c.getSource(), false)))
            .then(Commands.literal("force").executes(c -> check(c.getSource(), true))))
         .then(Commands.literal("trigger").executes(c -> trigger(c.getSource(), List.of(c.getSource().getEntityOrException())))
            .then(Commands.argument("targets", EntityArgument.entities()).executes(c -> trigger(c.getSource(), EntityArgument.getEntities(c, "targets")))))
         .then(Commands.literal("calm").executes(c -> calm(c.getSource()))));
   }

   private static Component state(Scp096State s) {
      return Component.translatable("scp096.viltrumitecore.state." + s.name().toLowerCase(Locale.ROOT));
   }

   private static String pos(double x, double y, double z) {
      return String.format(Locale.ROOT, "%.0f %.0f %.0f", x, y, z);
   }

   private static int status(CommandSourceStack src) {
      MinecraftServer server = src.getServer();
      Scp096SavedData data = Scp096SavedData.get(server);
      ViltrumiteCoreConfig cfg = ViltrumiteCoreConfig.INSTANCE;
      long now = server.overworld().getGameTime();
      long next = Math.max(0L, data.nextCheck() - now) / 20L;
      long cd = Scp096Rules.cooldownTicks(cfg.scp096NaturalCooldownMinutes);
      long cdLeft = data.lastNaturalSpawn() == Long.MIN_VALUE ? 0L : Math.max(0L, data.lastNaturalSpawn() + cd - now) / 20L;
      Component onOff = Component.translatable(K + (data.naturalEnabled() ? "on" : "off"));
      src.sendSuccess(() -> Component.translatable(K + "status.header", onOff, next, cdLeft,
         Scp096Rules.checkIntervalTicks(cfg.scp096NaturalCheckSeconds) / 20, String.format(Locale.ROOT, "%.3f", cfg.scp096NaturalChancePercent),
         String.format(Locale.ROOT, "%.0f", cfg.scp096NaturalCooldownMinutes)), false);
      if (data.instances().isEmpty()) {
         src.sendSuccess(() -> Component.translatable(K + "status.none"), false);
      }
      for (Scp096SavedData.Instance i : data.instances()) {
         ServerLevel level = Scp096Spawner.levelOf(server, i.dimension);
         Entity e = level == null ? null : level.getEntity(i.id);
         Component kind = Component.translatable(K + (i.natural ? "natural" : "manual"));
         if (e instanceof Scp096Entity s) {
            String extra = s.describe() + " " + s.chase.describe();
            src.sendSuccess(() -> Component.translatable(K + "status.loaded", i.id.toString().substring(0, 8), kind, state(s.state()),
               pos(s.getX(), s.getY(), s.getZ()), i.dimension, s.targetCount(), extra), false);
         } else {
            src.sendSuccess(() -> Component.translatable(K + "status.unloaded", i.id.toString().substring(0, 8), kind,
               state(Scp096State.byName(i.state)), pos(i.x, i.y, i.z), i.dimension), false);
         }
      }
      return data.instances().size();
   }

   private static int spawn(CommandSourceStack src, @Nullable Vec3 at, Scp096State mode) throws CommandSyntaxException {
      ServerLevel level = src.getLevel();
      Vec3 p = at;
      float yaw;
      Entity who = src.getEntity();
      if (p == null) {
         Entity e = src.getEntityOrException();
         Vec3 look = e.getViewVector(1.0F);
         Vec3 h = new Vec3(look.x, 0.0, look.z);
         h = h.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : h.normalize();
         p = ground(level, e.position().add(h.scale(7.0)));
         if (p == null) {
            src.sendFailure(Component.translatable(K + "spawn.fail"));
            return 0;
         }
      }
      if (who != null) {
         yaw = Scp096SpawnRules.yawAwayFrom(who.getX() - p.x, who.getZ() - p.z);
      } else {
         yaw = level.random.nextFloat() * 360.0F;
      }
      Scp096Entity s = Scp096Entities.SCP096.get().create(level);
      if (s == null) {
         src.sendFailure(Component.translatable(K + "spawn.fail"));
         return 0;
      }
      s.moveTo(p.x, p.y, p.z, yaw, 0.0F);
      s.setYHeadRot(yaw);
      s.yBodyRot = yaw;
      s.setNatural(false);
      s.setState(mode);
      if (!level.noCollision(s) || !level.addFreshEntity(s)) {
         src.sendFailure(Component.translatable(K + "spawn.fail"));
         return 0;
      }
      Vec3 fp = p;
      src.sendSuccess(() -> Component.translatable(K + "spawn.done", pos(fp.x, fp.y, fp.z), state(mode)), true);
      return 1;
   }

   @Nullable
   private static Vec3 ground(ServerLevel level, Vec3 around) {
      BlockPos base = BlockPos.containing(around);
      for (int dy = 4; dy >= -6; dy--) {
         BlockPos q = base.above(dy);
         if (!level.isLoaded(q)) {
            return null;
         }
         if (level.getBlockState(q.below()).isFaceSturdy(level, q.below(), Direction.UP) && level.getBlockState(q).getCollisionShape(level, q).isEmpty()
            && level.getBlockState(q.above()).getCollisionShape(level, q.above()).isEmpty()) {
            return new Vec3(q.getX() + 0.5, q.getY(), q.getZ() + 0.5);
         }
      }
      return null;
   }

   private static int despawn(CommandSourceStack src) {
      MinecraftServer server = src.getServer();
      Scp096SavedData data = Scp096SavedData.get(server);
      int removed = 0;
      for (Scp096Entity e : Scp096Entity.loaded()) {
         e.discard();
         removed++;
      }
      int later = 0;
      for (Scp096SavedData.Instance i : new ArrayList<>(data.instances())) {
         data.removeOnLoad(i.id);
         later++;
      }
      int r = removed;
      int l = later;
      src.sendSuccess(() -> Component.translatable(K + "despawn.done", r, l), true);
      return removed + later;
   }

   private static int natural(CommandSourceStack src, boolean on) {
      Scp096SavedData.get(src.getServer()).setNaturalEnabled(on);
      src.sendSuccess(() -> Component.translatable(K + (on ? "natural.on" : "natural.off")), true);
      return 1;
   }

   private static int check(CommandSourceStack src, boolean force) {
      Scp096Spawner.Attempt a = Scp096Spawner.attempt(src.getServer(), force, true);
      Scp096Entity e = a.spawned();
      if (e != null) {
         src.sendSuccess(() -> Component.translatable(K + "natural.result.spawned", pos(e.getX(), e.getY(), e.getZ())), true);
      } else {
         src.sendSuccess(() -> Component.translatable(K + "natural.result." + a.result().name().toLowerCase(Locale.ROOT)), false);
      }
      return a.result() == Scp096Spawner.Result.SPAWNED ? 1 : 0;
   }

   @Nullable
   private static Scp096Entity nearest(CommandSourceStack src) {
      Scp096Entity best = null;
      double bd = Double.MAX_VALUE;
      for (Scp096Entity e : Scp096Entity.loaded()) {
         if (e.level() != src.getLevel()) {
            continue;
         }
         double d = e.position().distanceToSqr(src.getPosition());
         if (d < bd) {
            bd = d;
            best = e;
         }
      }
      return best;
   }

   private static int trigger(CommandSourceStack src, Collection<? extends Entity> who) {
      Scp096Entity e = nearest(src);
      if (e == null) {
         src.sendFailure(Component.translatable(K + "none_loaded"));
         return 0;
      }
      List<LivingEntity> living = new ArrayList<>();
      for (Entity w : who) {
         if (w instanceof LivingEntity le && !(w instanceof Scp096Entity)) {
            living.add(le);
         }
      }
      e.forceTrigger(src.getLevel(), living);
      int n = living.size();
      src.sendSuccess(() -> Component.translatable(K + "trigger.done", n), true);
      return n;
   }

   private static int calm(CommandSourceStack src) {
      int n = 0;
      for (Scp096Entity e : Scp096Entity.loaded()) {
         if (e.level() instanceof ServerLevel sl) {
            e.calmDown(sl);
            n++;
         }
      }
      int c = n;
      src.sendSuccess(() -> Component.translatable(K + "calm.done", c), true);
      return n;
   }
}
