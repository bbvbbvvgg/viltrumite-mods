package dev.baranhan.viltrumitecore.hero.sinclair;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Server switch of Sinclair's staging cinematics (design 2026-11-02 §2.6): the copy cinematic (freeze, staged
 * cameras, the card) and the act cameras. {@code /sinclair cinematic on|off|status} (operators, level 2).
 * Off changes only the presentation: the capture, the copied number, damage and cooldowns stay the same.
 * Stored per world (SavedData on the overworld), on by default.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class SinclairCinema {
   static final String DATA = "viltrumitecore_sinclair";

   private SinclairCinema() {
   }

   /** World-saved setting. */
   static final class Data extends SavedData {
      boolean enabled = true;

      static Data load(CompoundTag tag) {
         Data data = new Data();
         data.enabled = !tag.contains("cinematic") || tag.getBoolean("cinematic");
         return data;
      }

      @Override
      public CompoundTag save(CompoundTag tag) {
         tag.putBoolean("cinematic", this.enabled);
         return tag;
      }
   }

   static Data data(MinecraftServer server) {
      ServerLevel overworld = server.overworld();
      return overworld.getDataStorage().computeIfAbsent(Data::load, Data::new, DATA);
   }

   /** Is the cinematic presentation on for this server (true without a server, e.g. in unit tests). */
   public static boolean enabled(MinecraftServer server) {
      return server == null || data(server).enabled;
   }

   public static void set(MinecraftServer server, boolean on) {
      Data data = data(server);
      data.enabled = on;
      data.setDirty();
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      event.getDispatcher().register(Commands.literal("sinclair").requires(s -> s.hasPermission(2))
         .then(Commands.literal("cinematic")
            .then(Commands.literal("on").executes(c -> toggle(c, true)))
            .then(Commands.literal("off").executes(c -> toggle(c, false)))
            .then(Commands.literal("status").executes(SinclairCinema::status))
            .executes(SinclairCinema::status)));
   }

   private static int toggle(CommandContext<CommandSourceStack> context, boolean on) {
      MinecraftServer server = context.getSource().getServer();
      set(server, on);
      context.getSource().sendSuccess(() -> Component.translatable(on ? "commands.viltrumitecore.sinclair.cinematic.on" : "commands.viltrumitecore.sinclair.cinematic.off"), true);
      return 1;
   }

   private static int status(CommandContext<CommandSourceStack> context) {
      boolean on = enabled(context.getSource().getServer());
      context.getSource().sendSuccess(() -> Component.translatable("commands.viltrumitecore.sinclair.cinematic.status",
         Component.translatable(on ? "commands.viltrumitecore.sinclair.cinematic.state_on" : "commands.viltrumitecore.sinclair.cinematic.state_off")), false);
      return on ? 1 : 0;
   }
}
