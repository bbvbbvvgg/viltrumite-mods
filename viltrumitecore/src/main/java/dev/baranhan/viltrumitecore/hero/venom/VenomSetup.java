package dev.baranhan.viltrumitecore.hero.venom;

import com.mojang.brigadier.CommandDispatcher;
import dev.baranhan.viltrumitecore.compat.spider.SpiderCompat;
import dev.baranhan.viltrumitecore.hero.venom.boss.VenomBossEntity;
import dev.baranhan.viltrumitecore.hero.venom.boss.VenomEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Venom's registrations (spec §10): sounds, FX presets, the boss entity and the {@code /venom} command
 * ({@code boss} summons the boss in front of the caller, {@code compat} prints what the Spider adapter bound).
 */
public final class VenomSetup {
   private VenomSetup() {
   }

   public static void init(IEventBus modBus) {
      VenomSounds.init();
      VenomFx.init();
      VenomEntities.register(modBus);
      MinecraftForge.EVENT_BUS.addListener(VenomSetup::onCommands);
   }

   /** Common setup, after the hero registry: the takeover's action lock. */
   public static void commonSetup() {
      VenomPossession.init();
   }

   private static void onCommands(RegisterCommandsEvent event) {
      register(event.getDispatcher());
   }

   static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(Commands.literal("venom")
         .then(Commands.literal("boss").requires(s -> s.hasPermission(2)).executes(ctx -> {
            CommandSourceStack source = ctx.getSource();
            ServerLevel level = source.getLevel();
            if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
               // a monster is discarded on Peaceful at once: say so instead of announcing a boss that never appears
               source.sendFailure(Component.translatable("commands.viltrumitecore.venom.boss_peaceful"));
               return 0;
            }
            VenomBossEntity boss = VenomEntities.VENOM_BOSS.get().create(level);
            if (boss == null) {
               return 0;
            }
            Vec3 at = source.getPosition();
            if (source.getEntity() != null) {
               at = at.add(source.getEntity().getLookAngle().multiply(1, 0, 1).normalize().scale(6.0));
            }
            boss.moveTo(at.x, at.y, at.z, source.getRotation().y + 180.0F, 0.0F);
            boss.finalizeSpawn(level, level.getCurrentDifficultyAt(boss.blockPosition()), MobSpawnType.COMMAND, null, null);
            level.addFreshEntity(boss);
            VenomSounds.play(boss, VenomSounds.ROAR, 2.0F, 0.85F);
            source.sendSuccess(() -> Component.translatable("commands.viltrumitecore.venom.boss"), true);
            return 1;
         }))
         .then(Commands.literal("compat").executes(ctx -> {
            for (String line : SpiderCompat.describe()) {
               ctx.getSource().sendSuccess(() -> Component.literal(line), false);
            }
            return 1;
         })));
   }
}
