package dev.baranhan.viltrumitecore.hero.budget.multiverse;

import dev.baranhan.viltrumitecore.hero.budget.BudgetScene;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.BudgetSceneS2CPacket;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * EPIC INSCRIPTIONS: vanilla titles, action-bar lines and the existing
 * BudgetScene camera shake + screen flash. No new HUD renderer.
 */
public final class MultiverseHud {
   private MultiverseHud() {
   }

   public static List<ServerPlayer> near(ServerLevel level, Vec3 center, double radius) {
      return level.players().stream().filter(p -> p.position().distanceToSqr(center) < radius * radius).map(p -> (ServerPlayer)p).toList();
   }

   public static void title(List<ServerPlayer> players, String titleKey, String subtitleKey, ChatFormatting color, Object... args) {
      Component title = Component.translatable(titleKey).withStyle(color, ChatFormatting.BOLD);
      Component subtitle = subtitleKey == null ? Component.empty() : Component.translatable(subtitleKey, args).withStyle(ChatFormatting.GRAY);
      for (ServerPlayer player : players) {
         player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
         player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
         player.connection.send(new ClientboundSetTitleTextPacket(title));
      }
   }

   public static void actionBar(List<ServerPlayer> players, Component text) {
      for (ServerPlayer player : players) {
         player.displayClientMessage(text, true);
      }
   }

   public static void chat(List<ServerPlayer> players, Component text) {
      for (ServerPlayer player : players) {
         player.sendSystemMessage(text);
      }
   }

   /** Camera shake + coloured flash through the existing BudgetScene client. */
   public static void flash(List<ServerPlayer> players, BudgetScene scene) {
      for (ServerPlayer player : players) {
         CoreMessages.sendToPlayer(new BudgetSceneS2CPacket(player.getId(), scene.ordinal(), scene.windup), player);
      }
   }

   public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
      level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
   }
}
