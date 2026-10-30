package dev.baranhan.viltrumitecore.hero.speech.qa;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.slf4j.Logger;

/**
 * Opt-in operator harness of the subtitle playtest (global subtitles expansion). Registered only with
 * {@code -Dviltrumitecore.subtitles.qa=true}; no command exists in a normal game. Works from a dedicated
 * server console too, so a multiplayer run can give real clients their heroes.
 * <ul>
 * <li>{@code /subtitleqa hero <targets> <hero>}: the full hero change (as the choice screen does).</li>
 * <li>{@code /subtitleqa prep <targets>}: cooldowns reset, full health, DIO blood and Jinwoo mana full.</li>
 * <li>{@code /subtitleqa victim <targets> [distance]}: a still 1000 HP husk in front of each target.</li>
 * <li>{@code /subtitleqa kill <targets>}: a zombie killed by the target (the shared kill remark).</li>
 * <li>{@code /subtitleqa lowhp <targets>}: a survived hit to 25% health (the shared low-health remark).</li>
 * <li>{@code /subtitleqa active}: logs the server's active lines ({@code SPEECHQA}).</li>
 * </ul>
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class SubtitleQaCommands {
   public static final boolean ENABLED = Boolean.getBoolean("viltrumitecore.subtitles.qa");
   private static final Logger LOG = LogUtils.getLogger();

   private SubtitleQaCommands() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      if (!ENABLED) {
         return;
      }

      event.getDispatcher().register(Commands.literal("subtitleqa").requires(s -> s.hasPermission(2))
         .then(Commands.literal("hero").then(Commands.argument("targets", EntityArgument.players()).then(Commands.argument("hero", StringArgumentType.word())
            .executes(c -> hero(EntityArgument.getPlayers(c, "targets"), StringArgumentType.getString(c, "hero"))))))
         .then(Commands.literal("prep").then(Commands.argument("targets", EntityArgument.players())
            .executes(c -> prep(c.getSource(), EntityArgument.getPlayers(c, "targets")))))
         .then(Commands.literal("victim").then(Commands.argument("targets", EntityArgument.players())
            .executes(c -> victim(EntityArgument.getPlayers(c, "targets"), 4.0))
            .then(Commands.argument("distance", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(1.0, 24.0))
               .executes(c -> victim(EntityArgument.getPlayers(c, "targets"), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "distance"))))))
         .then(Commands.literal("kill").then(Commands.argument("targets", EntityArgument.players())
            .executes(c -> kill(EntityArgument.getPlayers(c, "targets")))))
         .then(Commands.literal("lowhp").then(Commands.argument("targets", EntityArgument.players())
            .executes(c -> lowHealth(EntityArgument.getPlayers(c, "targets")))))
         .then(Commands.literal("active").executes(c -> {
            LOG.info("SPEECHQA active {}", HeroSpeech.qaDump(c.getSource().getServer()));
            return 1;
         })));
   }

   private static int hero(Collection<ServerPlayer> targets, String key) {
      HeroId id = HeroId.fromKey(key);
      if (id == null) {
         return 0;
      }

      for (ServerPlayer player : targets) {
         boolean changed = HeroRegistry.changeHero(player, id);
         HeroRegistry.repairLoadout(player);
         LOG.info("SPEECHQA hero {} -> {} changed={}", player.getGameProfile().getName(), id, changed);
      }

      return targets.size();
   }

   private static int prep(CommandSourceStack source, Collection<ServerPlayer> targets) {
      for (ServerPlayer player : targets) {
         source.getServer().getCommands().performPrefixedCommand(source.withSuppressedOutput().withPermission(4),
            "hero cooldown reset " + player.getGameProfile().getName());
         player.setHealth(player.getMaxHealth());
         dev.baranhan.viltrumitecore.hero.dio.DioState dio = dev.baranhan.viltrumitecore.hero.dio.DioState.of(player);
         if (dio != null) {
            dio.blood = dev.baranhan.viltrumitecore.hero.dio.DioRules.BLOOD_MAX;
         }

         dev.baranhan.viltrumitecore.hero.jinwoo.JinwooState jinwoo = dev.baranhan.viltrumitecore.hero.jinwoo.JinwooState.of(player);
         if (jinwoo != null) {
            jinwoo.mana = dev.baranhan.viltrumitecore.hero.jinwoo.JinwooRules.MANA_MAX;
         }

         HeroRegistry.syncSnapshot(player);
      }

      return targets.size();
   }

   private static int victim(Collection<ServerPlayer> targets, double distance) {
      for (ServerPlayer player : targets) {
         Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
         Husk husk = EntityType.HUSK.create(player.serverLevel());
         if (husk == null) {
            continue;
         }

         Vec3 at = player.position().add(look.scale(distance));
         husk.moveTo(at.x, at.y, at.z, player.getYRot() + 180.0F, 0.0F);
         husk.setNoAi(true);
         husk.setSilent(true);
         husk.setPersistenceRequired();
         husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
         husk.setHealth(1000.0F);
         player.serverLevel().addFreshEntity(husk);
      }

      return targets.size();
   }

   private static int kill(Collection<ServerPlayer> targets) {
      for (ServerPlayer player : targets) {
         Zombie zombie = EntityType.ZOMBIE.create(player.serverLevel());
         if (zombie == null) {
            continue;
         }

         Vec3 at = player.position().add(Vec3.directionFromRotation(0.0F, player.getYRot()).scale(2.0));
         zombie.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
         zombie.finalizeSpawn(player.serverLevel(), player.serverLevel().getCurrentDifficultyAt(zombie.blockPosition()), MobSpawnType.COMMAND, null, null);
         zombie.setNoAi(true);
         player.serverLevel().addFreshEntity(zombie);
         zombie.hurt(player.damageSources().playerAttack(player), 1000.0F);
         LOG.info("SPEECHQA kill by {} dead={}", player.getGameProfile().getName(), zombie.isDeadOrDying());
      }

      return targets.size();
   }

   private static int lowHealth(Collection<ServerPlayer> targets) {
      for (ServerPlayer player : targets) {
         player.setHealth(player.getMaxHealth());
         player.invulnerableTime = 0;
         player.hurt(player.damageSources().generic(), player.getMaxHealth() * 0.75F);
         LOG.info("SPEECHQA lowhp {} health={}/{}", player.getGameProfile().getName(), player.getHealth(), player.getMaxHealth());
      }

      return targets.size();
   }
}
