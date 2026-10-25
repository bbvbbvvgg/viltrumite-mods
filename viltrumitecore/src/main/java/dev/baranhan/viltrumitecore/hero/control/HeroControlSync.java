package dev.baranhan.viltrumitecore.hero.control;

import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.HeroControlS2CPacket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * World-control snapshot distribution (plan Task 4 contract): domes and
 * anchored/pull controls live per dimension and every client tracking that
 * world gets the same baseline — including observers who never see the
 * caster's entity. The manager flags structural changes; the level tick
 * flushes one full snapshot to all players in the dimension.
 */
public final class HeroControlSync {
   private HeroControlSync() {
   }

   /** Full snapshot to every player in the dimension (dirty flush). */
   public static void broadcast(ServerLevel level) {
      HeroControlS2CPacket packet = snapshot(level);
      for (ServerPlayer player : level.players()) {
         CoreMessages.sendToPlayer(packet, player);
      }
   }

   /** Baseline for a player who just joined, respawned or changed dimension. */
   public static void sendBaseline(ServerPlayer player) {
      if (player.level() instanceof ServerLevel level) {
         CoreMessages.sendToPlayer(snapshot(level), player);
      }
   }

   static HeroControlS2CPacket snapshot(ServerLevel level) {
      ControlManager manager = ControlManager.get(level);
      List<HeroControlS2CPacket.DomeInfo> domes = new ArrayList<>();
      for (DomeRecord dome : manager.domes().values()) {
         domes.add(new HeroControlS2CPacket.DomeInfo(
            dome.id(), dome.caster(), dome.center().x, dome.center().y, dome.center().z,
            dome.radius(), dome.createdAt(), dome.expiresAt(), List.copyOf(dome.captured())));
      }

      List<HeroControlS2CPacket.ControlInfo> controls = new ArrayList<>();
      for (ControlManager.ControlRecord record : manager.records()) {
         Entity entity = level.getEntity(record.targetId);
         controls.add(new HeroControlS2CPacket.ControlInfo(
            record.targetId, entity == null ? -1 : entity.getId(), record.kind.ordinal(), record.expiresAt, record.caster, record.effectId));
      }

      return new HeroControlS2CPacket(level.dimension(), domes, controls);
   }
}
