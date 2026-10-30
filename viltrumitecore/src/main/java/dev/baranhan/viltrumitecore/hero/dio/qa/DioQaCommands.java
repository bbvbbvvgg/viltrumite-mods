package dev.baranhan.viltrumitecore.hero.dio.qa;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.dio.DioRules;
import dev.baranhan.viltrumitecore.hero.dio.DioState;
import dev.baranhan.viltrumitecore.hero.dio.DioTimeStop;
import java.util.Arrays;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Opt-in operator QA harness ({@code -Dviltrumitecore.dio.qa=true}): become DIO,
 * press/release an ability, spawn a still target, set blood, clear cooldowns,
 * print state. Absent in a normal game.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class DioQaCommands {
   public static final boolean ENABLED = Boolean.getBoolean("viltrumitecore.dio.qa");

   private DioQaCommands() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      if (!ENABLED) {
         return;
      }

      event.getDispatcher().register(Commands.literal("dioqa").requires(s -> s.hasPermission(2))
         .then(Commands.literal("blood").then(Commands.argument("amount", IntegerArgumentType.integer(0, DioRules.BLOOD_MAX))
            .executes(c -> blood(c.getSource(), IntegerArgumentType.getInteger(c, "amount")))))
         .then(Commands.literal("reset").executes(c -> reset(c.getSource())))
         .then(Commands.literal("become").executes(c -> become(c.getSource())))
         .then(Commands.literal("dummy").executes(c -> dummy(c.getSource(), 4.0))
            .then(Commands.argument("distance", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(1.0, 16.0))
               .executes(c -> dummy(c.getSource(), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "distance")))))
         .then(Commands.literal("act").then(Commands.argument("action", StringArgumentType.word())
            .executes(c -> act(c.getSource(), StringArgumentType.getString(c, "action"), true))
            .then(Commands.argument("pressed", BoolArgumentType.bool())
               .executes(c -> act(c.getSource(), StringArgumentType.getString(c, "action"), BoolArgumentType.getBool(c, "pressed"))))))
         .then(Commands.literal("state").executes(c -> state(c.getSource())))
         .then(Commands.literal("foreignstop").then(Commands.argument("ticks", IntegerArgumentType.integer(0, 600))
            .executes(c -> foreignStop(c.getSource(), IntegerArgumentType.getInteger(c, "ticks")))))
         .then(Commands.literal("shadow").executes(c -> shadow(c.getSource())))
         .then(Commands.literal("probe").then(Commands.argument("label", StringArgumentType.word())
            .executes(c -> probe(c.getSource(), StringArgumentType.getString(c, "label"))))));
   }

   private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("DIOQA");
   /** A second, invisible DIO (Forge fake player) whose stopped time catches the QA player. */
   private static net.minecraftforge.common.util.FakePlayer foreign;

   /**
    * {@code /dioqa foreignstop <ticks>}: a fake-player DIO 6 blocks east stops time for
    * {@code ticks} around itself (radius 40), so the local player is frozen like any
    * victim; 0 ends that field at once (the "forced end" path). Ticked below.
    */
   private static int foreignStop(CommandSourceStack source, int ticks) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      ServerPlayer player = source.getPlayerOrException();
      if (foreign != null) {
         DioTimeStop.cleanupCaster(foreign.getUUID());
      }

      if (ticks == 0) {
         LOG.info("DIOQA foreignstop ended by command");
         return 1;
      }

      // Forge's FakePlayer refuses PvP (canHarmPlayer = false), which would exempt every player.
      foreign = new net.minecraftforge.common.util.FakePlayer(player.serverLevel(),
         new com.mojang.authlib.GameProfile(java.util.UUID.fromString("d10d10d1-0000-4000-8000-00000000d10d"), "QA_DIO")) {
         @Override
         public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other) {
            return true;
         }
      };
      Vec3 at = player.position().add(6.0, 0.0, 0.0);
      foreign.moveTo(at.x, at.y, at.z, 90.0F, 0.0F);
      DioTimeStop.start(foreign, at.add(0.0, 1.0, 0.0), DioRules.TIME_STOP_RADIUS, ticks, false);
      LOG.info("DIOQA foreignstop {} ticks at {}", ticks, at);
      return 1;
   }

   @SubscribeEvent
   public static void serverTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
      if (!ENABLED || event.phase != net.minecraftforge.event.TickEvent.Phase.END || foreign == null) {
         return;
      }

      if (DioTimeStop.fieldOf(foreign.getUUID()) == null) {
         LOG.info("DIOQA foreignstop field gone");
         foreign = null;
         return;
      }

      DioTimeStop.tick(foreign);
   }

   /**
    * The QA player becomes Jin-Woo and calls a real shadow squad (one infantry in the roster,
    * full mana): another hero's summons and hero runtime for the stopped-time checks.
    */
   private static int shadow(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      ServerPlayer player = source.getPlayerOrException();
      HeroRegistry.changeHero(player, HeroId.JINWOO);
      dev.baranhan.viltrumitecore.hero.jinwoo.JinwooState state = dev.baranhan.viltrumitecore.hero.jinwoo.JinwooState.of(player);
      if (state == null) {
         source.sendFailure(Component.literal("not Jin-Woo"));
         return 0;
      }

      if (state.roster.size() == 0) {
         state.roster.addExtracted(dev.baranhan.viltrumitecore.entity.jinwoo.ShadowKind.INFANTRY);
         state.roster.addExtracted(dev.baranhan.viltrumitecore.entity.jinwoo.ShadowKind.KNIGHT);
      }

      state.mana = dev.baranhan.viltrumitecore.hero.jinwoo.JinwooRules.MANA_MAX;
      boolean ok = dev.baranhan.viltrumitecore.hero.jinwoo.ShadowArmy.summonOrRecall(player, state);
      LOG.info("DIOQA shadow summon {}", ok);
      return 1;
   }

   /** Logs position, motion, age and frozen flag of everything within 24 blocks (plus the player's look). */
   private static int probe(CommandSourceStack source, String label) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      ServerPlayer player = source.getPlayerOrException();
      LOG.info("DIOQA probe {} player id={} pos={} yaw={} pitch={} frozen={} hp={} alive={} hero={}", label, player.getId(), fmt(player.position()),
         String.format(java.util.Locale.ROOT, "%.2f", player.getYRot()), String.format(java.util.Locale.ROOT, "%.2f", player.getXRot()),
         dev.baranhan.viltrumitecore.hero.control.TimeFreeze.isFrozen(player), player.getHealth(), player.isAlive(), HeroRegistry.get(player).id());
      for (net.minecraft.world.entity.Entity e : player.serverLevel().getEntities(player, player.getBoundingBox().inflate(24.0),
         e -> !(e instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
         LOG.info("DIOQA probe {} {} id={} pos={} motion={} age={} frozen={}", label, net.minecraft.world.entity.EntityType.getKey(e.getType()).getPath(),
            e.getId(), fmt(e.position()), fmt(e.getDeltaMovement()), e.tickCount, dev.baranhan.viltrumitecore.hero.control.TimeFreeze.isFrozen(e)
            + (e instanceof net.minecraft.world.entity.item.PrimedTnt tnt ? " fuse=" + tnt.getFuse() : ""));
      }

      return 1;
   }

   private static String fmt(Vec3 v) {
      return String.format(java.util.Locale.ROOT, "(%.3f %.3f %.3f)", v.x, v.y, v.z);
   }

   private static DioState state(ServerPlayer player) {
      return DioState.of(player);
   }

   private static int blood(CommandSourceStack source, int amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      DioState state = state(source.getPlayerOrException());
      if (state == null) {
         return 0;
      }

      state.blood = DioRules.clampBlood(amount);
      source.sendSuccess(() -> Component.literal("DIO blood = " + state.blood), false);
      return 1;
   }

   private static int become(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      ServerPlayer player = source.getPlayerOrException();
      HeroRegistry.changeHero(player, HeroId.DIO);
      DioState.ensure(player).blood = DioRules.BLOOD_MAX;
      source.sendSuccess(() -> Component.literal("You are DIO"), false);
      return 1;
   }

   /** A still, silent zombie ahead (4 blocks by default): a target for combo / knife / drain screenshots. */
   private static int dummy(CommandSourceStack source, double distance) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      ServerPlayer player = source.getPlayerOrException();
      Mob mob = EntityType.ZOMBIE.create(player.serverLevel());
      if (mob == null) {
         return 0;
      }

      Vec3 at = player.position().add(Vec3.directionFromRotation(0.0F, player.getYRot()).scale(distance));
      mob.moveTo(at.x, at.y, at.z, player.getYRot() + 180.0F, 0.0F);
      mob.setNoAi(true);
      mob.setSilent(true);
      mob.setPersistenceRequired();
      player.serverLevel().addFreshEntity(mob);
      return 1;
   }

   private static int act(CommandSourceStack source, String name, boolean pressed) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      ServerPlayer player = source.getPlayerOrException();
      HeroAction action;
      try {
         action = HeroAction.valueOf(name.toUpperCase(java.util.Locale.ROOT));
      } catch (IllegalArgumentException e) {
         source.sendFailure(Component.literal("Unknown action " + name));
         return 0;
      }

      HeroRegistry.get(player).handleInput(player, action, pressed);
      return 1;
   }

   private static int reset(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      DioState state = state(source.getPlayerOrException());
      if (state == null) {
         return 0;
      }

      Arrays.fill(state.cooldowns, 0);
      source.sendSuccess(() -> Component.literal("DIO cooldowns cleared"), false);
      return 1;
   }

   private static int state(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      ServerPlayer player = source.getPlayerOrException();
      DioState state = state(player);
      if (state == null) {
         return 0;
      }

      String text = "DIO blood=" + state.blood + " stand=" + state.standPhase + " action=" + state.action + "@" + state.elapsed
         + " cds=" + Arrays.toString(state.cooldowns) + " field=" + (DioTimeStop.fieldOf(player.getUUID()) != null) + " sun=" + state.inSun + " infinite=" + state.infiniteBlood;
      LOG.info("DIOQA {}", text);
      source.sendSuccess(() -> Component.literal(text), false);
      return 1;
   }
}
