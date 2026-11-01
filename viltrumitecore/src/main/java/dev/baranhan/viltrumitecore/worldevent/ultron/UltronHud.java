package dev.baranhan.viltrumitecore.worldevent.ultron;

import static dev.baranhan.viltrumitecore.worldevent.ultron.UltronDirector.*;

import dev.baranhan.viltrumitecore.entity.ultron.UltronPrime;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronNetwork.Node;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronStateS2CPacket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;

/**
 * The uprising's HUD (docs/plans/2026-11-01-ultron-uprising.md): the state packet the viewers get (phase,
 * progress, protocols, nodes, Ultron Prime, the upload, the captured machines, each player's own hack), the
 * phase and boss bars and the phase titles. Called by {@link UltronDirector}.
 */
final class UltronHud {
   private UltronHud() {
   }

   static void sendStateTo(ServerPlayer player) {
      UltronSession s = UltronUprising.current();
      ServerLevel level = player.serverLevel();
      if (s == null || !s.in(level) || horizontalSq(s.anchor, player.getX(), player.getZ()) > UltronRules.VIEW_RANGE * UltronRules.VIEW_RANGE) {
         CoreMessages.sendToPlayer(UltronStateS2CPacket.inactive(), player);
         if (s != null) {
            s.viewing.remove(player.getUUID());
         }
         return;
      }
      UltronStateS2CPacket base = state(level, s);
      UltronSession.Hack h = s.hacks.get(player.getUUID());
      CoreMessages.sendToPlayer(h == null ? base : base.withHack(h.progress / (float)UltronRules.HACK_TICKS, h.node), player);
      s.viewing.add(player.getUUID());
   }

   static void sendState(ServerLevel level, UltronSession s, List<ServerPlayer> viewers) {
      s.dirty = false;
      UltronStateS2CPacket base = state(level, s);
      Set<UUID> now = new HashSet<>();
      for (ServerPlayer p : viewers) {
         now.add(p.getUUID());
         UltronSession.Hack h = s.hacks.get(p.getUUID());
         CoreMessages.sendToPlayer(h == null ? base : base.withHack(h.progress / (float)UltronRules.HACK_TICKS, h.node), p);
      }
      if (!s.viewing.isEmpty()) {
         UltronStateS2CPacket off = null;
         for (UUID id : s.viewing) {
            if (!now.contains(id)) {
               ServerPlayer p = s.server.getPlayerList().getPlayer(id);
               if (p != null) {
                  if (off == null) {
                     off = UltronStateS2CPacket.inactive();
                  }
                  CoreMessages.sendToPlayer(off, p);
               }
            }
         }
      }
      s.viewing.clear();
      s.viewing.addAll(now);
   }

   static UltronStateS2CPacket state(ServerLevel level, UltronSession s) {
      UltronAdaptation.Protocol[] ps = UltronAdaptation.Protocol.values();
      int[] secs = new int[ps.length];
      int[] lvls = new int[ps.length];
      for (int i = 0; i < ps.length; i++) {
         secs[i] = (s.adaptation.ticksLeft(ps[i]) + UltronRules.TPS - 1) / UltronRules.TPS;
         lvls[i] = s.adaptation.level(ps[i]);
      }
      List<UltronStateS2CPacket.Node> nodes = new ArrayList<>();
      for (Node n : s.network.all()) {
         if (nodes.size() >= UltronStateS2CPacket.MAX_NODES) {
            break;
         }
         int flags = (n.powered ? UltronStateS2CPacket.FLAG_POWERED : 0) | (n.discovered ? UltronStateS2CPacket.FLAG_DISCOVERED : 0)
            | (n.fortified() ? UltronStateS2CPacket.FLAG_FORTIFIED : 0) | (n.state == UltronNodeState.DISABLED && n.rebootTicks > 0 ? UltronStateS2CPacket.FLAG_REBOOTING : 0);
         boolean known = n.discovered;
         int[] links = new int[n.links.size()];
         for (int i = 0; i < links.length; i++) {
            links[i] = n.links.get(i);
         }
         float maxShield = n.maxShield();
         nodes.add(new UltronStateS2CPacket.Node(n.id, n.kind.ordinal(), n.state.ordinal(), flags, n.hullFraction(), maxShield > 0.0F ? n.shield / maxShield : 0.0F,
            n.build, known ? Mth.floor(n.x) : 0, known ? Mth.floor(n.y) : 0, known ? Mth.floor(n.z) : 0, links));
      }
      int bossId = -1;
      float bossHealth = s.phase == UltronPhase.FINALE ? s.bossShare : 0.0F;
      if (boss(level, s) instanceof UltronPrime prime) {
         bossId = prime.getId();
         bossHealth = Mth.clamp(prime.getHealth() / prime.getMaxHealth(), 0.0F, 1.0F);
      }
      float upload = s.scenario.uploadTicks() > 0 ? Mth.clamp(s.upload / s.scenario.uploadTicks(), 0.0F, 1.0F) : 0.0F;
      return new UltronStateS2CPacket(true, s.phase.ordinal(), progress(s), s.scenario.variant().ordinal(), s.doctrine.ordinal(), s.outcome, s.anchor.x,
         s.anchor.z, (float)s.radius, secs, lvls, nodes, bossId, s.bossStage, bossHealth, upload, s.uploading, s.capture.ids(level), s.units.size(), 0.0F, -1);
   }

   /** The phase's progress for the bar and the HUD (0..1). */
   static float progress(UltronSession s) {
      return switch (s.phase) {
         case AWAKENING -> {
            Node core = s.network.core();
            yield core == null ? 0.0F : core.online() ? 1.0F : core.build;
         }
         case SPREAD -> {
            int target = Math.max(1, s.scenario.spreadTarget());
            int raised = 0;
            for (Node n : s.network.all()) {
               if (wave(s, n) == 1 && n.state != UltronNodeState.PLANNED) {
                  raised++;
               }
            }
            yield Mth.clamp((float)raised / target, 0.0F, 1.0F);
         }
         case MACHINE_WAR, ADAPTATION -> Mth.clamp(s.phaseTick / (UltronRules.maxTicks(s.phase) * s.scenario.timeScale()), 0.0F, 1.0F);
         case FINALE -> s.scenario.uploadTicks() > 0 ? Mth.clamp(s.upload / s.scenario.uploadTicks(), 0.0F, 1.0F) : 0.0F;
         case ENDED -> 1.0F;
      };
   }

   static void bars(ServerLevel level, UltronSession s, List<ServerPlayer> viewers) {
      if (s.outcome != 0) {
         s.bar.setName(Component.translatable("viltrumitecore.ultron.bar." + (s.outcome == UltronStateS2CPacket.OUTCOME_VICTORY ? "victory" : "defeat")));
         s.bar.setColor(s.outcome == UltronStateS2CPacket.OUTCOME_VICTORY ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.RED);
         s.bar.setProgress(s.outcome == UltronStateS2CPacket.OUTCOME_VICTORY ? 0.0F : 1.0F);
      } else if (s.phase == UltronPhase.FINALE && s.uploading) {
         s.bar.setName(Component.translatable("viltrumitecore.ultron.bar.upload", Math.round(progress(s) * 100.0F)));
         s.bar.setColor(BossEvent.BossBarColor.RED);
         s.bar.setProgress(progress(s));
      } else {
         s.bar.setName(Component.translatable("viltrumitecore.ultron.bar", Component.translatable("viltrumitecore.ultron.phase." + s.phase.key())));
         s.bar.setColor(switch (s.phase) {
            case AWAKENING -> BossEvent.BossBarColor.WHITE;
            case SPREAD -> BossEvent.BossBarColor.YELLOW;
            case MACHINE_WAR -> BossEvent.BossBarColor.RED;
            case ADAPTATION -> BossEvent.BossBarColor.PURPLE;
            default -> BossEvent.BossBarColor.RED;
         });
         s.bar.setProgress(progress(s));
      }
      sync(s.bar, viewers);
      Entity e = boss(level, s);
      if (e instanceof UltronPrime prime && prime.isAlive() && s.outcome == 0) {
         if (s.bossBar == null) {
            s.bossBar = new ServerBossEvent(Component.literal(""), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
            s.bossBar.setDarkenScreen(false);
         }
         s.bossBar.setName(Component.translatable("viltrumitecore.ultron.bar.boss", Component.translatable("viltrumitecore.ultron.stage." + prime.stage().key())));
         s.bossBar.setProgress(Mth.clamp(prime.getHealth() / prime.getMaxHealth(), 0.0F, 1.0F));
         List<ServerPlayer> near = new ArrayList<>();
         for (ServerPlayer p : viewers) {
            if (p.distanceToSqr(prime) <= BOSS_BAR_RANGE * BOSS_BAR_RANGE) {
               near.add(p);
            }
         }
         sync(s.bossBar, near);
      } else if (s.bossBar != null) {
         s.bossBar.removeAllPlayers();
      }
   }

   static void sync(ServerBossEvent bar, List<ServerPlayer> want) {
      Set<ServerPlayer> keep = new HashSet<>(want);
      for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
         if (!keep.contains(p)) {
            bar.removePlayer(p);
         }
      }
      for (ServerPlayer p : want) {
         if (!bar.getPlayers().contains(p)) {
            bar.addPlayer(p);
         }
      }
   }

   static Component phaseTitle(UltronPhase phase) {
      return Component.translatable("viltrumitecore.ultron.title." + phase.key()).withStyle(ChatFormatting.RED);
   }

   static Component phaseSubtitle(UltronPhase phase) {
      return Component.translatable("viltrumitecore.ultron.subtitle." + phase.key()).withStyle(ChatFormatting.GRAY);
   }

   static void title(ServerPlayer p, Component title, Component subtitle) {
      p.connection.send(new ClientboundSetTitlesAnimationPacket(12, 70, 24));
      p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
      p.connection.send(new ClientboundSetTitleTextPacket(title));
   }
}
