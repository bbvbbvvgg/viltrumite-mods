package dev.baranhan.viltrumitecore.worldevent.ultron;

import static dev.baranhan.viltrumitecore.worldevent.ultron.UltronDirector.*;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.entity.ultron.UltronUnit;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriTerrain;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronNetwork.Node;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronFxS2CPacket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * What the uprising does once a second (docs/plans/2026-11-01-ultron-uprising.md): Ultron learns from what
 * his sensors and machines report (adaptation protocols on and off, the relocation of a lost foundry), the
 * nodes repair, fortify and call for help, players discover nodes, the machines heal near repair nodes, raise
 * deflectors and get recalled when they stray. Called by {@link UltronDirector}.
 */
final class UltronLearning {
   private UltronLearning() {
   }


   static void second(ServerLevel level, UltronSession s) {
      List<ServerPlayer> fighters = fighters(level, s);
      int sensors = s.network.active(UltronNodeKind.SENSOR);
      s.adaptation.setLearning(UltronAdaptation.learningFor(sensors));
      if (s.sensorsSeen > 0 && sensors == 0 && s.phase.atLeast(UltronPhase.MACHINE_WAR)) {
         UltronVoice.say(level, s, "frozen", false);
         for (ServerPlayer p : viewers(level, s)) {
            p.displayClientMessage(Component.translatable("viltrumitecore.ultron.frozen").withStyle(ChatFormatting.AQUA), true);
         }
      }
      s.sensorsSeen = sensors;
      for (UltronAdaptation.Change c : s.adaptation.second(s.phase.atLeast(UltronPhase.ADAPTATION))) {
         applyChange(level, s, c);
      }
      s.intel.second(s.clock);
      s.recentLosses *= 0.9F;
      s.styleTurn = s.phaseTick / STYLE_TURN;
      nodesSecond(level, s, fighters);
      discovery(level, s, fighters);
      unitsSecond(level, s);
      if (s.phase.atLeast(UltronPhase.MACHINE_WAR)) {
         s.capture.pulse(level, s, s.doctrine == UltronDoctrine.ASSIMILATION);
      }
      if (!s.capture.captives.isEmpty()) {
         s.capture.second(level, s);
      }
   }

   static void applyChange(ServerLevel level, UltronSession s, UltronAdaptation.Change c) {
      UltronAdaptation.Protocol p = c.protocol();
      s.dirty = true;
      s.saveDirty = true;
      Component name = Component.translatable("viltrumitecore.ultron.protocol." + p.key());
      if (c.active()) {
         switch (p) {
            case RELOCATION -> s.relocationSite = relocationSite(s);
            case REDISTRIBUTION -> s.adaptation.setFocusSector(s.intel.focus() >= 0 ? s.intel.focus() : s.lastLossSector);
            case FORTIFY -> {
               Node n = s.network.get(s.heldNode);
               if (n == null || !n.standing()) {
                  n = s.network.strongest(null);
               }
               s.adaptation.setFortifiedNode(n == null ? -1 : n.id);
               if (n != null) {
                  n.fortifyTicks = s.adaptation.ticksLeft(p);
                  UltronUprising.fx(level, UltronFxS2CPacket.NODE_BOOT, new Vec3(n.x, n.y + 2.5, n.z), Vec3.ZERO, 1.4F, n.kind.ordinal(), true);
               }
            }
            case DEFLECTOR -> {
               for (UltronUnit u : s.units.values()) {
                  if (u.isAlive() && (u.kind() == UltronUnitKind.SENTRY || u.kind() == UltronUnitKind.BRUTE)) {
                     u.setFlag(UltronUnit.FLAG_DEFLECTOR, true);
                  }
               }
            }
            default -> {
            }
         }
         if (c.reason() == UltronAdaptation.Reason.ACTIVATED) {
            UltronVoice.say(level, s, "protocol_" + p.key(), true);
         }
         UltronUprising.cue(level, s, UltronSounds.PROTOCOL, 1.0F, c.reason() == UltronAdaptation.Reason.RENEWED ? 1.15F : 1.0F);
         UltronUprising.fx(level, UltronFxS2CPacket.GLITCH, s.anchor.add(0.0, 2.0, 0.0), Vec3.ZERO, (float)s.radius, 3 + p.ordinal(), true);
         for (ServerPlayer pl : viewers(level, s)) {
            pl.displayClientMessage(Component.translatable(c.reason() == UltronAdaptation.Reason.RENEWED ? "viltrumitecore.ultron.protocol_renewed"
               : "viltrumitecore.ultron.protocol_activated", name).withStyle(ChatFormatting.RED), true);
         }
         UltronUprising.LOG.info("Ultron protocol {} {} (level {})", p.key(), c.reason().name().toLowerCase(java.util.Locale.ROOT), s.adaptation.level(p));
      } else {
         switch (p) {
            case RELOCATION -> s.relocationSite = -1;
            case REDISTRIBUTION -> s.adaptation.setFocusSector(-1);
            case FORTIFY -> {
               Node n = s.network.get(s.adaptation.fortifiedNode());
               if (n != null) {
                  n.fortifyTicks = 0;
               }
               s.adaptation.setFortifiedNode(-1);
            }
            case DEFLECTOR -> {
               for (UltronUnit u : s.units.values()) {
                  u.setFlag(UltronUnit.FLAG_DEFLECTOR, false);
               }
            }
            default -> {
            }
         }
         if (c.reason() == UltronAdaptation.Reason.COUNTERED) {
            if (p == UltronAdaptation.Protocol.ANTI_AIR) {
               UltronVoice.say(level, s, "countered_anti_air", false);
            } else if (p == UltronAdaptation.Protocol.DEFLECTOR) {
               UltronVoice.say(level, s, "countered_deflector", false);
            }
         }
         for (ServerPlayer pl : viewers(level, s)) {
            pl.displayClientMessage(Component.translatable(c.reason() == UltronAdaptation.Reason.COUNTERED ? "viltrumitecore.ultron.protocol_countered"
               : "viltrumitecore.ultron.protocol_lapsed", name).withStyle(ChatFormatting.AQUA), true);
         }
         UltronUprising.LOG.info("Ultron protocol {} ended ({})", p.key(), c.reason().name().toLowerCase(java.util.Locale.ROOT));
      }
   }

   /** Where production relocates: a planned reserve site (a foundry first) as far as possible from where the players were seen. */
   static int relocationSite(UltronSession s) {
      Node best = null;
      double bestScore = -1.0;
      for (Node n : s.network.all()) {
         if (n.state != UltronNodeState.PLANNED) {
            continue;
         }
         double away = 1000.0;
         for (UltronIntel.Sighting t : s.intel.all()) {
            if (s.clock - t.tick <= 120 * UltronRules.TPS) {
               away = Math.min(away, Math.sqrt(n.distanceSq(t.x, t.y, t.z)));
            }
         }
         double score = (n.kind == UltronNodeKind.FOUNDRY ? 2000.0 : 0.0) + (wave(s, n) == 2 ? 1000.0 : 0.0) + Math.min(away, 500.0);
         if (score > bestScore) {
            bestScore = score;
            best = n;
         }
      }
      return best == null ? -1 : best.id;
   }

   static void nodesSecond(ServerLevel level, UltronSession s, List<ServerPlayer> fighters) {
      CityManager city = s.inCity ? CityManager.of(level) : null;
      for (Node n : s.network.all()) {
         if (!n.standing()) {
            continue;
         }
         float full = UltronRules.hull(n.kind);
         if (n.state != UltronNodeState.DISABLED && n.hull < full && n.sinceHit > 3 * UltronRules.TPS && s.network.repairLinked(n)) {
            n.hull = Math.min(full, n.hull + UltronRules.REPAIR_NODE_HEAL);
         }
         // an attack is over: did the node hold?
         if (n.attackDamage > 0.0F && n.sinceHit >= DEFENSE_QUIET) {
            if (n.attackDamage >= DEFENSE_SHARE * (full + UltronRules.shield(n.kind)) && !s.adaptation.frozen()) {
               s.adaptation.setPressure(UltronAdaptation.Tactic.DEFENSE_HELD, s.adaptation.pressure(UltronAdaptation.Tactic.DEFENSE_HELD) + DEFENSE_HELD);
               s.heldNode = n.id;
            }
            n.attackDamage = 0.0F;
            n.attackTicks = 0;
         }
         if (n.id == s.adaptation.fortifiedNode() && s.adaptation.active(UltronAdaptation.Protocol.FORTIFY)) {
            n.fortifyTicks = Math.max(n.fortifyTicks, s.adaptation.ticksLeft(UltronAdaptation.Protocol.FORTIFY));
         }
         // the sensors watch: who stands in sight, who flies
         if (n.kind == UltronNodeKind.SENSOR && n.active()) {
            Vec3 top = new Vec3(n.x, n.y + 4.2, n.z);
            double r2 = UltronRules.SENSOR_RADIUS * UltronRules.SENSOR_RADIUS;
            for (ServerPlayer p : fighters) {
               if (p.distanceToSqr(top) <= r2 && ChitauriTerrain.clear(level, top, p.getEyePosition())) {
                  boolean air = UltronUprising.airborne(p);
                  s.intel.saw(p.getUUID(), p.getX(), p.getY(), p.getZ(), s.clock, air);
                  if (air) {
                     s.adaptation.observe(UltronAdaptation.Tactic.AERIAL, 0.6F);
                  }
               }
            }
         }
         if (city != null && n.state != UltronNodeState.PLANNED) {
            city.danger(n.x, n.y, n.z, 7.0F, 40);
         }
      }
   }

   /** Players find nodes: close by, or in clear sight a bit further. */
   static void discovery(ServerLevel level, UltronSession s, List<ServerPlayer> fighters) {
      double near2 = UltronRules.DISCOVER_NEAR * UltronRules.DISCOVER_NEAR;
      double sight2 = UltronRules.DISCOVER_SIGHT * UltronRules.DISCOVER_SIGHT;
      for (Node n : s.network.all()) {
         if (!n.standing() || n.discovered) {
            continue;
         }
         Vec3 top = new Vec3(n.x, n.y + 3.0, n.z);
         for (ServerPlayer p : fighters) {
            double d2 = n.distanceSq(p.getX(), p.getY(), p.getZ());
            if (d2 <= near2 || d2 <= sight2 && ChitauriTerrain.clear(level, p.getEyePosition(), top)) {
               discover(level, s, n, p);
               break;
            }
         }
      }
   }

   static void unitsSecond(ServerLevel level, UltronSession s) {
      boolean deflector = s.adaptation.active(UltronAdaptation.Protocol.DEFLECTOR);
      CityManager city = s.inCity ? CityManager.of(level) : null;
      List<Node> repairs = new ArrayList<>();
      for (Node n : s.network.all()) {
         if (n.kind == UltronNodeKind.REPAIR && n.active()) {
            repairs.add(n);
         }
      }
      double heal2 = UltronRules.REPAIR_RADIUS * UltronRules.REPAIR_RADIUS;
      double leash2 = s.radius * LEASH * s.radius * LEASH;
      for (UltronUnit u : s.units.values()) {
         if (!u.isAlive()) {
            continue;
         }
         u.setFlag(UltronUnit.FLAG_DEFLECTOR, deflector && (u.kind() == UltronUnitKind.SENTRY || u.kind() == UltronUnitKind.BRUTE));
         if (u.getHealth() < u.getMaxHealth()) {
            for (Node r : repairs) {
               if (r.distanceSq(u.getX(), u.getY(), u.getZ()) <= heal2) {
                  u.heal(UltronRules.REPAIR_UNIT_HEAL);
                  break;
               }
            }
         }
         if (city != null && u.flag(UltronUnit.FLAG_COMBAT)) {
            city.danger(u.getX(), u.getY(), u.getZ(), 9.0F, 60);
         }
         if (u.getTarget() == null && horizontalSq(s.anchor, u.getX(), u.getZ()) > leash2) {
            Node home = s.network.nearestActive(u.getX(), u.getY(), u.getZ(), null);
            u.goal = home != null ? new Vec3(home.x, home.y, home.z) : s.anchor;
            u.hunt = null;
            u.alertUntil = u.tickCount + ALERT_HOLD * 2;
         }
      }
   }
}
