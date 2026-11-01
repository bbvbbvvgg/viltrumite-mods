package dev.baranhan.viltrumitecore.worldevent.ultron;

import dev.baranhan.viltrumitecore.entity.ultron.UltronBolt;
import dev.baranhan.viltrumitecore.entity.ultron.UltronEngineer;
import dev.baranhan.viltrumitecore.entity.ultron.UltronEntities;
import dev.baranhan.viltrumitecore.entity.ultron.UltronMissile;
import dev.baranhan.viltrumitecore.entity.ultron.UltronMob;
import dev.baranhan.viltrumitecore.entity.ultron.UltronNodeEntity;
import dev.baranhan.viltrumitecore.entity.ultron.UltronPrime;
import dev.baranhan.viltrumitecore.entity.ultron.UltronUnit;
import dev.baranhan.viltrumitecore.hero.control.TimeFreeze;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriTerrain;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronNetwork.Node;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronStateS2CPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The uprising's tick (docs/plans/2026-11-01-ultron-uprising.md): node bodies and their life, the players'
 * hacks, the foundries and the spawn queue, the finale and the upload, the phase beats and transitions and
 * the save; what is learned every second is in {@link UltronLearning}, the role coordinator and the doctrine
 * in {@link UltronOrders}, the HUD state, the bars and the titles in {@link UltronHud}. Chunk-safe: bodies and machines only appear in entity-ticking chunks, a site out of reach stays
 * data until a player brings it in. Nobody in range: the event waits (no phase time, no production).
 */
final class UltronDirector {
   /** One machine leaving a foundry ({@code goal}: where it heads first, or null for its role's orders). */
   record Spawn(UltronUnitKind kind, Vec3 at, int node, boolean reserve, @Nullable Vec3 goal) {
   }

   /** The core boots by itself during the awakening (engineers welding it speed this up). */
   static final int CORE_BOOT_TICKS = 50 * UltronRules.TPS;
   /** A destroyed node's links stay cut this long, then the network re-routes around the hole. */
   static final int REROUTE_TICKS = 20 * UltronRules.TPS;
   /** A node under attack calls the machines around it at most this often. */
   static final int ALERT_GAP = 5 * UltronRules.TPS;
   static final double ALERT_RANGE = 48.0;
   /** An attack on a node is over after this much quiet; it "held" when it took DEFENSE_SHARE of hull + shield and stands. */
   static final int DEFENSE_QUIET = 15 * UltronRules.TPS;
   static final float DEFENSE_SHARE = 0.15F;
   static final float DEFENSE_HELD = 12.0F;
   /** A lost foundry is an event Ultron always notices (his own telemetry). */
   static final float FOUNDRY_LOSS = 12.0F;
   /** Hull an engineer welds back per weld step on a damaged node. */
   static final float WELD_REPAIR = 1.2F;
   static final double BOSS_BAR_RANGE = 96.0;
   /** Sightings older than this do not steer the assault. */
   static final int SIGHT_WINDOW = 45 * UltronRules.TPS;
   /** The adaptive attack style turns every this many ticks of war. */
   static final int STYLE_TURN = 120 * UltronRules.TPS;
   /** Machines without a target this far out (area radius times) are recalled. */
   static final double LEASH = 1.7;
   /** While his machines fight, a taunt is tried this often (seconds). */
   static final int TAUNT_EVERY_S = 40;
   static final int BOSS_MISSING_TICKS = 5 * UltronRules.TPS;
   /** Orders from an alert or a mark hold this long against the role coordinator. */
   static final int ALERT_HOLD = 6 * UltronRules.TPS;

   private UltronDirector() {
   }

   // ------------------------------------------------------------------ lifecycle

   static void begin(ServerLevel level, UltronSession s) {
      List<ServerPlayer> viewers = viewers(level, s);
      for (ServerPlayer p : viewers) {
         UltronHud.title(p, UltronHud.phaseTitle(UltronPhase.AWAKENING), UltronHud.phaseSubtitle(UltronPhase.AWAKENING));
      }
      UltronUprising.cue(level, s, UltronSounds.EVENT_START, 1.0F, 1.0F);
      UltronUprising.fx(level, UltronFxS2CPacket.GLITCH, s.anchor.add(0.0, 2.0, 0.0), Vec3.ZERO, (float)s.radius, 0, true);
      s.dirty = true;
      UltronHud.sendState(level, s, viewers);
   }

   static void tick(MinecraftServer server, UltronSession s) {
      ServerLevel level = server.getLevel(s.dimension);
      if (level == null) {
         UltronUprising.LOG.warn("Ultron uprising: level {} is gone, the event closes", s.dimension.location());
         UltronUprising.close(s);
         return;
      }
      if (TimeFreeze.worldStopped(level)) {
         return;
      }
      s.clock++;
      List<ServerPlayer> viewers = viewers(level, s);
      if (level.players().isEmpty()) {
         if (++s.emptyTicks >= UltronRules.EMPTY_LEVEL_TICKS) {
            UltronUprising.LOG.info("Ultron uprising: nobody left in {}, the event ends", s.dimension.location());
            cleanup(level, s, true);
            UltronUprising.close(s);
            return;
         }
      } else {
         s.emptyTicks = 0;
      }
      if (s.clock % 10 == 0) {
         prune(level, s);
      }
      if (s.outcome != 0) {
         tickEnding(level, s);
         if (UltronUprising.current() != s) {
            return;
         }
      } else if (!viewers.isEmpty()) {
         s.phaseTick++;
         tickNodes(level, s);
         tickHacks(level, s);
         tickProduction(level, s);
         drain(level, s);
         if (s.phase == UltronPhase.FINALE) {
            tickFinale(level, s);
         }
         if (s.outcome == 0 && s.clock % 20 == 0) {
            UltronLearning.second(level, s);
         }
         if (s.outcome == 0 && s.clock % 40 == 0) {
            UltronOrders.roles(level, s);
         }
         if (s.outcome == 0 && s.clock % 100 == 0) {
            UltronOrders.doctrine(level, s);
         }
         if (s.outcome == 0) {
            beats(level, s);
         }
         if (s.outcome == 0 && s.clock % 10 == 0) {
            transitions(level, s);
         }
      }
      if (UltronUprising.current() != s) {
         return;
      }
      if (s.clock % 20 == 0) {
         bodies(level, s);
      }
      if (s.dirty || s.clock % 10 == 0) {
         UltronHud.sendState(level, s, viewers);
      }
      if (s.clock % 10 == 0) {
         UltronHud.bars(level, s, viewers);
      }
      if (s.saveDirty || s.clock % 200 == 0) {
         save(s);
      }
   }

   /** Operator jump (/ultron phase): what the skipped phases would have built is put in place. */
   static void stage(ServerLevel level, UltronSession s, UltronPhase phase) {
      Node core = s.network.core();
      if (phase.atLeast(UltronPhase.SPREAD) && core != null && core.state == UltronNodeState.BUILDING) {
         online(level, s, core);
      }
      if (phase.atLeast(UltronPhase.MACHINE_WAR)) {
         for (Node n : s.network.all()) {
            if (n.state == UltronNodeState.PLANNED && wave(s, n) == 1) {
               n.state = UltronNodeState.ONLINE;
               n.build = 1.0F;
               n.hull = UltronRules.hull(n.kind);
               n.shield = n.maxShield();
            }
         }
         s.network.relink();
         s.network.repower();
      }
      s.dirty = true;
      s.saveDirty = true;
   }

   static void enter(ServerLevel level, UltronSession s, UltronPhase phase) {
      if (phase == UltronPhase.ENDED || s.outcome != 0) {
         return;
      }
      UltronPhase from = s.phase;
      s.phase = phase;
      s.phaseTick = 0;
      s.builtPhase = 0;
      s.dirty = true;
      s.saveDirty = true;
      Node core = s.network.core();
      if (phase.atLeast(UltronPhase.SPREAD) && core != null && core.state == UltronNodeState.BUILDING) {
         online(level, s, core);
      }
      for (ServerPlayer p : viewers(level, s)) {
         UltronHud.title(p, UltronHud.phaseTitle(phase), UltronHud.phaseSubtitle(phase));
      }
      UltronUprising.cue(level, s, UltronSounds.PHASE_CHANGE, 1.0F, 1.0F);
      UltronUprising.fx(level, UltronFxS2CPacket.GLITCH, s.anchor.add(0.0, 2.0, 0.0), Vec3.ZERO, (float)s.radius, 1, true);
      switch (phase) {
         case SPREAD -> UltronVoice.say(level, s, "spread", true);
         case MACHINE_WAR -> UltronVoice.say(level, s, "war", true);
         case ADAPTATION -> UltronVoice.say(level, s, "adaptation", true);
         case FINALE -> {
            UltronVoice.say(level, s, from.atLeast(UltronPhase.ADAPTATION) ? "finale" : "early_finale", true);
            beginFinale(level, s);
         }
         default -> {
         }
      }
      UltronUprising.LOG.info("Ultron uprising: {} -> {} ({} nodes standing, {} machines)", from.key(), phase.key(), s.network.standing(), s.units.size());
      save(s);
   }

   /** Victory (Ultron Prime destroyed, operator) or defeat (upload complete, operator): the closing sequence starts. */
   static void end(ServerLevel level, UltronSession s, boolean victory) {
      if (s.outcome != 0) {
         return;
      }
      s.outcome = victory ? UltronStateS2CPacket.OUTCOME_VICTORY : UltronStateS2CPacket.OUTCOME_DEFEAT;
      s.endTick = 0;
      s.dirty = true;
      s.saveDirty = true;
      s.hacks.clear();
      s.queue.clear();
      s.fabs.clear();
      s.uploading = false;
      // the Legion comes back at once, whatever happens next
      s.capture.releaseAll(level, s);
      for (ServerPlayer p : viewers(level, s)) {
         String key = victory ? "victory" : "defeat";
         UltronHud.title(p, Component.translatable("viltrumitecore.ultron.title." + key).withStyle(victory ? ChatFormatting.AQUA : ChatFormatting.DARK_RED),
            Component.translatable("viltrumitecore.ultron.subtitle." + key).withStyle(ChatFormatting.GRAY));
      }
      UltronUprising.cue(level, s, victory ? UltronSounds.VICTORY : UltronSounds.DEFEAT, 1.0F, 1.0F);
      UltronVoice.say(level, s, victory ? "victory" : "defeat", true);
      if (!victory && boss(level, s) instanceof UltronPrime prime && prime.isAlive()) {
         prime.ascend();
      }
      UltronUprising.LOG.info("Ultron uprising: {} (seed {}, phase {}, machines destroyed {}, nodes destroyed {}, disabled {})",
         victory ? "victory" : "defeat", s.seed, s.phase.key(), java.util.Arrays.toString(s.killed), s.nodesDestroyed, s.nodesDisabled);
      save(s);
   }

   static void tickEnding(ServerLevel level, UltronSession s) {
      s.endTick++;
      if (s.outcome == UltronStateS2CPacket.OUTCOME_VICTORY) {
         // the network dies with him: the machines shut down one after another, the nodes go dark and break
         if (s.endTick % 6 == 0) {
            int n = 0;
            for (UltronUnit u : s.units.values()) {
               if (u.isAlive() && n < 2) {
                  Vec3 at = u.position().add(0.0, u.getBbHeight() * 0.6, 0.0);
                  UltronUprising.fx(level, UltronFxS2CPacket.SHUTDOWN, at, Vec3.ZERO, u.getBbHeight(), u.getId(), false);
                  u.shutdown();
                  n++;
               }
            }
         }
         if (s.endTick % 25 == 10) {
            for (Node node : s.network.all()) {
               if (node.standing()) {
                  node.state = UltronNodeState.DESTROYED;
                  node.powered = false;
                  collapseBody(level, s, node);
                  break;
               }
            }
         }
         if (s.endTick >= UltronRules.VICTORY_TICKS) {
            cleanup(level, s, false);
            UltronUprising.close(s);
         }
      } else {
         if (s.endTick == UltronRules.DEFEAT_TICKS / 2) {
            for (UltronUnit u : s.units.values()) {
               if (u.isAlive()) {
                  u.setTarget(null);
               }
            }
            UltronUprising.fx(level, UltronFxS2CPacket.GLITCH, s.anchor.add(0.0, 2.0, 0.0), Vec3.ZERO, (float)s.radius, 2, true);
         }
         if (s.endTick >= UltronRules.DEFEAT_TICKS) {
            cleanup(level, s, false);
            UltronUprising.close(s);
         }
      }
   }

   /** Emergency stop: everything goes now, the subtitles too. */
   static void cleanup(ServerLevel level, UltronSession s) {
      cleanup(level, s, true);
   }

   /** Every machine, node body, bolt and missile goes; captives are handed back; HUD and bars off. */
   static void cleanup(ServerLevel level, UltronSession s, boolean hush) {
      s.capture.releaseAll(level, s);
      List<Entity> purge = new ArrayList<>();
      for (Entity e : level.getAllEntities()) {
         if (e instanceof UltronMob || e instanceof UltronBolt || e instanceof UltronMissile) {
            purge.add(e);
         }
      }
      for (Entity e : purge) {
         e.discard();
      }
      s.units.clear();
      s.queue.clear();
      s.fabs.clear();
      s.hacks.clear();
      s.boss = null;
      for (Node n : s.network.all()) {
         n.entity = null;
      }
      UltronStateS2CPacket off = UltronStateS2CPacket.inactive();
      for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
         if (s.viewing.contains(p.getUUID()) || p.level() == level) {
            CoreMessages.sendToPlayer(off, p);
         }
      }
      s.viewing.clear();
      s.bar.removeAllPlayers();
      if (s.bossBar != null) {
         s.bossBar.removeAllPlayers();
      }
      if (hush) {
         HeroSpeech.stopWorld(level.getServer(), UltronVoice.VOICE);
      }
   }

   static void save(UltronSession s) {
      s.saveDirty = false;
      UltronSavedData.get(s.server).put(s.save());
   }

   /**
    * Bodies that left the world: removed whole (another dimension, an unload) → back to the reserve (redeployed
    * later), destroyed → gone. Machines are not saved, so a chunk unload does not remove them: Minecraft keeps
    * them hidden (still counted, not ticking) and they resume when the chunk loads again; a restart brings them
    * back through the saved reserve.
    */
   static void prune(ServerLevel level, UltronSession s) {
      Iterator<Map.Entry<UUID, UltronUnit>> it = s.units.entrySet().iterator();
      while (it.hasNext()) {
         UltronUnit u = it.next().getValue();
         if (!u.isRemoved()) {
            continue;
         }
         Entity.RemovalReason reason = u.getRemovalReason();
         if (reason != null && UltronRules.backToReserve(reason.shouldDestroy(), s.outcome, u.getHealth())) {
            s.reserve[u.kind().ordinal()]++;
         }
         it.remove();
         s.dirty = true;
      }
      if (s.boss != null && level.getEntity(s.boss) == null && s.phase != UltronPhase.FINALE) {
         s.boss = null;
      }
   }

   // ------------------------------------------------------------------ nodes

   static void tickNodes(ServerLevel level, UltronSession s) {
      for (Node n : s.network.all()) {
         if (!n.standing()) {
            continue;
         }
         if (n.sinceHit < 1_000_000) {
            n.sinceHit++;
         }
         if (n.attackDamage > 0.0F) {
            n.attackTicks++;
         }
         if (n.fortifyTicks > 0) {
            n.fortifyTicks--;
         }
         switch (n.state) {
            case BUILDING -> {
               if (n.kind == UltronNodeKind.CORE && s.phase == UltronPhase.AWAKENING) {
                  n.build = Math.min(1.0F, n.build + 1.0F / (CORE_BOOT_TICKS * s.scenario.timeScale()));
                  n.hull = Math.max(n.hull, UltronRules.hull(n.kind) * (0.4F + 0.6F * n.build));
                  if (n.build >= 1.0F) {
                     online(level, s, n);
                  }
               }
            }
            case ONLINE -> {
               float max = n.maxShield();
               if (n.shield > max) {
                  n.shield = max;
               } else if (n.shield < max && n.sinceHit >= UltronRules.SHIELD_DELAY) {
                  n.shield = Math.min(max, n.shield + UltronRules.shieldRegenPerTick(s.network.bulwarkLinked(n), n.fortified()));
               }
            }
            case DISABLED -> {
               if (--n.disabledTicks <= 0) {
                  reboot(level, s, n);
               }
            }
            default -> {
            }
         }
      }
      if (s.relinkAt >= 0 && s.clock >= s.relinkAt) {
         s.relinkAt = -1;
         s.network.relink();
         s.network.repower();
         s.dirty = true;
         s.saveDirty = true;
      }
   }

   /** Bodies for the standing nodes in ticking chunks (placed on the ground the first time), synced every second. */
   static void bodies(ServerLevel level, UltronSession s) {
      if (s.outcome != 0 && s.endTick > 0 && s.network.standing() == 0) {
         return;
      }
      for (Node n : s.network.all()) {
         if (!n.standing()) {
            continue;
         }
         UltronNodeEntity body = body(level, n);
         if (body != null) {
            body.sync(n, s.network.bulwarkLinked(n), n.id == s.relocationSite);
            continue;
         }
         spawnBody(level, s, n);
      }
   }

   @Nullable
   static UltronNodeEntity body(ServerLevel level, Node n) {
      if (n.entity == null) {
         return null;
      }
      return level.getEntity(n.entity) instanceof UltronNodeEntity b && b.isAlive() && !b.isRemoved() ? b : null;
   }

   static void spawnBody(ServerLevel level, UltronSession s, Node n) {
      if (!ChitauriTerrain.ticking(level, n.x, n.y + 1.0, n.z) || !placeNode(level, s, n)) {
         return;
      }
      UltronNodeEntity body = UltronEntities.NODE.get().create(level);
      if (body == null) {
         return;
      }
      float yaw = (float)(Mth.atan2(s.anchor.z - n.z, s.anchor.x - n.x) * Mth.RAD_TO_DEG) - 90.0F;
      body.bind(n.id, n.kind);
      body.moveTo(n.x, n.y, n.z, yaw, 0.0F);
      body.sync(n, s.network.bulwarkLinked(n), n.id == s.relocationSite);
      if (level.addFreshEntity(body)) {
         n.entity = body.getUUID();
      }
   }

   static void syncBody(ServerLevel level, UltronSession s, Node n) {
      UltronNodeEntity body = body(level, n);
      if (body != null) {
         body.sync(n, s.network.bulwarkLinked(n), n.id == s.relocationSite);
      }
   }

   static void collapseBody(ServerLevel level, UltronSession s, Node n) {
      Vec3 at = new Vec3(n.x, n.y + 2.0, n.z);
      UltronNodeEntity body = body(level, n);
      if (body != null) {
         body.collapse();
      }
      n.entity = null;
      UltronUprising.fx(level, UltronFxS2CPacket.NODE_DESTROYED, at, Vec3.ZERO, 1.0F, n.kind.ordinal(), true);
      UltronSounds.playWide(level, at, UltronSounds.NODE_DESTROYED, 3.0F, 1.0F, 128.0);
   }

   /**
    * The node's place on the ground, once, when its column is loaded: the spot of a small grid around the
    * site closest to the area's ground level (streets and plazas rather than roofs), never in water.
    */
   static boolean placeNode(ServerLevel level, UltronSession s, Node n) {
      if (s.placed.contains(n.id)) {
         return true;
      }
      if (!ChitauriTerrain.loaded(level, n.x, n.z)) {
         return false;
      }
      double bestX = n.x;
      double bestZ = n.z;
      int bestY = ChitauriTerrain.UNKNOWN;
      double bestScore = Double.MAX_VALUE;
      for (int dx = -8; dx <= 8; dx += 4) {
         for (int dz = -8; dz <= 8; dz += 4) {
            double x = n.x + dx;
            double z = n.z + dz;
            int y = ChitauriTerrain.surface(level, x, z);
            if (y == ChitauriTerrain.UNKNOWN) {
               continue;
            }
            BlockPos below = BlockPos.containing(x, y - 1, z);
            if (!level.getFluidState(below).isEmpty()) {
               continue;
            }
            int rough = 0;
            for (int k = 0; k < 4; k++) {
               int ny = ChitauriTerrain.surface(level, x + (k == 0 ? 2 : k == 1 ? -2 : 0), z + (k == 2 ? 2 : k == 3 ? -2 : 0));
               rough += ny == ChitauriTerrain.UNKNOWN ? 4 : Math.abs(ny - y);
            }
            double score = Math.abs(y - s.anchor.y) * 2.0 + rough * 1.5 + Math.sqrt(dx * dx + dz * dz) * 0.4;
            if (score < bestScore) {
               bestScore = score;
               bestX = x;
               bestZ = z;
               bestY = y;
            }
         }
      }
      if (bestY == ChitauriTerrain.UNKNOWN) {
         return false;
      }
      n.x = Mth.floor(bestX) + 0.5;
      n.z = Mth.floor(bestZ) + 0.5;
      n.y = bestY;
      s.placed.add(n.id);
      s.saveDirty = true;
      return true;
   }

   static void online(ServerLevel level, UltronSession s, Node n) {
      boolean wasCore = n.kind == UltronNodeKind.CORE;
      n.state = UltronNodeState.ONLINE;
      n.build = 1.0F;
      n.hull = UltronRules.hull(n.kind);
      n.shield = n.maxShield();
      n.disabledTicks = 0;
      n.rebootTicks = 0;
      s.network.relink();
      s.network.repower();
      Vec3 at = new Vec3(n.x, n.y + 2.5, n.z);
      UltronUprising.fx(level, UltronFxS2CPacket.NODE_BOOT, at, Vec3.ZERO, 1.0F, n.kind.ordinal(), true);
      UltronSounds.playWide(level, at, UltronSounds.NODE_ONLINE, 2.5F, 1.0F, 96.0);
      if (wasCore) {
         UltronVoice.say(level, s, "core_online", false);
      } else {
         UltronVoice.say(level, s, "node_online", false);
      }
      if (n.id == s.relocationSite) {
         s.relocationSite = -1;
         UltronAdaptation.Change c = s.adaptation.complete(UltronAdaptation.Protocol.RELOCATION);
         if (c != null) {
            UltronLearning.applyChange(level, s, c);
         }
         UltronVoice.say(level, s, "relocated", false);
      }
      syncBody(level, s, n);
      s.dirty = true;
      s.saveDirty = true;
   }

   static void reboot(ServerLevel level, UltronSession s, Node n) {
      n.state = UltronNodeState.ONLINE;
      n.disabledTicks = 0;
      n.rebootTicks = 0;
      n.shield = 0.0F;
      s.network.repower();
      Vec3 at = new Vec3(n.x, n.y + 2.5, n.z);
      UltronUprising.fx(level, UltronFxS2CPacket.NODE_BOOT, at, Vec3.ZERO, 0.6F, n.kind.ordinal(), true);
      UltronSounds.play(level, at, UltronSounds.NODE_REBOOT, 2.0F, 1.0F);
      UltronVoice.say(level, s, "node_rebooted", false);
      syncBody(level, s, n);
      s.dirty = true;
      s.saveDirty = true;
   }

   static void disable(ServerLevel level, UltronSession s, Node n, @Nullable ServerPlayer by) {
      n.state = UltronNodeState.DISABLED;
      n.disabledTicks = UltronRules.DISABLE_TICKS;
      n.rebootTicks = 0;
      n.shield = 0.0F;
      n.powered = false;
      s.nodesDisabled++;
      s.network.repower();
      // a hacked node gives its links away: the players learn where the next ones stand
      for (int id : n.links) {
         Node m = s.network.get(id);
         if (m != null) {
            discover(level, s, m, by);
         }
      }
      Vec3 at = new Vec3(n.x, n.y + 2.5, n.z);
      UltronUprising.fx(level, UltronFxS2CPacket.NODE_DISABLED, at, Vec3.ZERO, 1.0F, n.kind.ordinal(), true);
      UltronSounds.playWide(level, at, UltronSounds.NODE_DISABLED, 2.5F, 1.0F, 96.0);
      UltronVoice.say(level, s, "node_disabled", false);
      if (by != null) {
         alert(level, s, n, by);
      }
      syncBody(level, s, n);
      s.dirty = true;
      s.saveDirty = true;
   }

   static void nodeDestroyed(ServerLevel level, UltronSession s, Node n, @Nullable Player by) {
      s.nodesDestroyed++;
      s.fabs.remove(n.id);
      collapseBody(level, s, n);
      int sector = UltronIntel.sector(s.anchor.x, s.anchor.z, n.x, n.z);
      s.lastLossSector = sector;
      for (int i = 0; i < 3; i++) {
         s.intel.lost(sector);
      }
      if (n.kind == UltronNodeKind.FOUNDRY) {
         s.adaptation.setPressure(UltronAdaptation.Tactic.FACTORY_LOSS, s.adaptation.pressure(UltronAdaptation.Tactic.FACTORY_LOSS) + FOUNDRY_LOSS);
         UltronVoice.say(level, s, "foundry_lost", false);
      } else {
         UltronVoice.say(level, s, "node_lost", false);
      }
      if (n.id == s.relocationSite) {
         s.relocationSite = -1;
      }
      if (n.id == s.heldNode) {
         s.heldNode = -1;
      }
      for (UltronUnit u : s.units.values()) {
         if (u.job == n.id) {
            u.job = -1;
            u.goal = null;
         }
      }
      // the links stay cut for a while: the branch behind the hole is dark until the network re-routes
      s.network.repower();
      s.relinkAt = s.clock + REROUTE_TICKS;
      if (by != null) {
         s.intel.killedBy(by.getUUID(), 15.0F);
      }
      UltronUprising.LOG.info("Ultron uprising: {} node #{} destroyed ({} standing)", n.kind.key(), n.id, s.network.standing());
      s.dirty = true;
      s.saveDirty = true;
   }

   static int wave(UltronSession s, Node n) {
      List<UltronScenario.Site> sites = s.scenario.sites();
      return n.site >= 0 && n.site < sites.size() ? sites.get(n.site).wave() : 1;
   }
   // ------------------------------------------------------------------ hooks: discovery, engineers, hits, hacks

   /** A node becomes known to the players (HUD marker, links): close sight, salvage, a hack, a hit, the finale. */
   static void discover(ServerLevel level, UltronSession s, Node n, @Nullable ServerPlayer by) {
      if (n.discovered || !n.standing()) {
         return;
      }
      n.discovered = true;
      s.dirty = true;
      s.saveDirty = true;
      Component what = Component.translatable("viltrumitecore.ultron.node." + n.kind.key());
      for (ServerPlayer p : viewers(level, s)) {
         p.playNotifySound(UltronSounds.DISCOVER.get(), SoundSource.HOSTILE, 0.8F, 1.0F);
         p.displayClientMessage(Component.translatable("viltrumitecore.ultron.discovered", what).withStyle(ChatFormatting.RED), true);
      }
   }

   /** One weld step of an engineer at a node: build, reboot or repair. False when there is nothing left to do there. */
   static boolean weld(ServerLevel level, UltronSession s, Node n, UltronEngineer engineer) {
      float full = UltronRules.hull(n.kind);
      switch (n.state) {
         case BUILDING -> {
            n.build = Math.min(1.0F, n.build + UltronRules.WELD_EVERY / (UltronRules.BUILD_TICKS * s.scenario.timeScale()));
            n.hull = Math.max(n.hull, full * (0.25F + 0.75F * n.build));
            if (n.build >= 1.0F) {
               online(level, s, n);
            }
            return true;
         }
         case DISABLED -> {
            n.rebootTicks += UltronRules.WELD_EVERY;
            if (n.rebootTicks >= UltronRules.REBOOT_TICKS) {
               reboot(level, s, n);
            }
            return true;
         }
         case ONLINE -> {
            if (n.hull >= full - 0.01F) {
               return false;
            }
            n.hull = Math.min(full, n.hull + WELD_REPAIR);
            return true;
         }
         default -> {
            return false;
         }
      }
   }

   /** An engineer plants a planned site (spread sites from the spread on, the relocation site while it is wanted). */
   static boolean plant(ServerLevel level, UltronSession s, Node n) {
      if (n.state != UltronNodeState.PLANNED || !s.phase.atLeast(UltronPhase.SPREAD)) {
         return false;
      }
      if (wave(s, n) == 2 && n.id != s.relocationSite) {
         return false;
      }
      n.state = UltronNodeState.BUILDING;
      n.build = 0.02F;
      n.hull = UltronRules.hull(n.kind) * 0.25F;
      n.shield = 0.0F;
      n.sinceHit = 1_000_000;
      placeNode(level, s, n);
      s.network.relink();
      s.network.repower();
      Vec3 at = new Vec3(n.x, n.y + 1.0, n.z);
      UltronSounds.play(level, at, UltronSounds.NODE_BUILD, 1.8F, 1.0F);
      UltronUprising.fx(level, UltronFxS2CPacket.NODE_BOOT, at, Vec3.ZERO, 0.35F, n.kind.ordinal(), false);
      spawnBody(level, s, n);
      s.dirty = true;
      s.saveDirty = true;
      return true;
   }

   /** A hit on a node: learning from the attacker, shield then hull (capped), effects, the defenders called. */
   static boolean damageNode(ServerLevel level, UltronSession s, Node n, UltronNodeEntity body, DamageSource source, float amount) {
      Vec3 at = source.getSourcePosition() != null ? source.getSourcePosition() : body.position().add(0.0, 2.0, 0.0);
      Vec3 contact = body.position().add(0.0, 2.0, 0.0).add(at.subtract(body.position().add(0.0, 2.0, 0.0)).normalize().scale(1.3));
      if (s.phase == UltronPhase.AWAKENING && n.kind == UltronNodeKind.CORE && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         // the boot lattice: the core cannot be touched while it wakes
         UltronUprising.fx(level, UltronFxS2CPacket.NODE_SHIELD_HIT, contact, Vec3.ZERO, 1.0F, n.kind.ordinal(), false);
         UltronSounds.play(level, contact, UltronSounds.NODE_SHIELD_HIT, 1.4F, 0.8F);
         if (UltronUprising.playerBehind(source.getEntity()) instanceof ServerPlayer p) {
            p.displayClientMessage(Component.translatable("viltrumitecore.ultron.core_booting").withStyle(ChatFormatting.GRAY), true);
         }
         return false;
      }
      Player player = UltronUprising.playerBehind(source.getEntity());
      if (player != null) {
         if (player instanceof ServerPlayer sp) {
            discover(level, s, n, sp);
         }
         boolean ranged = source.is(DamageTypeTags.IS_PROJECTILE) || player.distanceTo(body) > 6.0F;
         boolean air = UltronUprising.airborne(player);
         s.adaptation.observe(ranged ? UltronAdaptation.Tactic.RANGED : UltronAdaptation.Tactic.MELEE, amount * 0.25F);
         if (air) {
            s.adaptation.observe(UltronAdaptation.Tactic.AERIAL, amount * 0.3F);
         }
         s.intel.saw(player.getUUID(), player.getX(), player.getY(), player.getZ(), s.clock, air);
         s.intel.hurtBy(player.getUUID(), amount * 0.5F);
      }
      UltronNetwork.Hit hit = UltronNetwork.damage(n, amount, s.clock);
      if (hit == UltronNetwork.Hit.NONE) {
         return false;
      }
      if (hit.toShield() > 0.0F) {
         UltronUprising.fx(level, UltronFxS2CPacket.NODE_SHIELD_HIT, contact, Vec3.ZERO, Math.min(2.0F, hit.toShield() / 20.0F + 0.3F), n.kind.ordinal(), false);
         UltronSounds.play(level, contact, UltronSounds.NODE_SHIELD_HIT, 1.4F, 0.9F + level.random.nextFloat() * 0.2F);
      }
      if (hit.shieldBroke()) {
         UltronUprising.fx(level, UltronFxS2CPacket.NODE_SHIELD_BREAK, body.position().add(0.0, 2.0, 0.0), Vec3.ZERO, 1.0F, n.kind.ordinal(), true);
         UltronSounds.play(level, body.position(), UltronSounds.NODE_SHIELD_BREAK, 2.4F, 1.0F);
      }
      if (hit.toHull() > 0.0F) {
         UltronUprising.fx(level, UltronFxS2CPacket.NODE_HIT, contact, Vec3.ZERO, Math.min(2.0F, hit.toHull() / 20.0F + 0.3F), n.kind.ordinal(), false);
         UltronSounds.play(level, contact, UltronSounds.BOLT_IMPACT, 1.2F, 0.7F + level.random.nextFloat() * 0.2F);
      }
      if (player != null) {
         alert(level, s, n, player);
      }
      if (hit.destroyed()) {
         nodeDestroyed(level, s, n, player);
      } else {
         syncBody(level, s, n);
      }
      s.dirty = true;
      return true;
   }

   /** Sneak + use at a node core: progress (half speed through a live shield), disabled at {@link UltronRules#HACK_TICKS}. */
   static boolean hack(ServerLevel level, UltronSession s, Node n, UltronNodeEntity body, ServerPlayer player) {
      if (n.state != UltronNodeState.ONLINE && n.state != UltronNodeState.BUILDING) {
         return false;
      }
      if (s.phase == UltronPhase.AWAKENING && n.kind == UltronNodeKind.CORE) {
         player.displayClientMessage(Component.translatable("viltrumitecore.ultron.core_booting").withStyle(ChatFormatting.GRAY), true);
         return false;
      }
      double reach = UltronRules.HACK_REACH + body.getBbWidth() * 0.5;
      if (player.distanceToSqr(body.getX(), Mth.clamp(player.getY(), body.getY(), body.getY() + body.getBbHeight()), body.getZ()) > reach * reach) {
         return false;
      }
      UltronSession.Hack h = s.hacks.get(player.getUUID());
      if (h == null || h.node != n.id) {
         h = new UltronSession.Hack(n.id, s.clock - 4);
         s.hacks.put(player.getUUID(), h);
      }
      int step = Mth.clamp(s.clock - h.last, 1, 4);
      if (n.state == UltronNodeState.ONLINE && n.shield > 0.5F) {
         step = Math.max(1, step / 2);
      }
      h.progress += step;
      h.last = s.clock;
      discover(level, s, n, player);
      Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.6)).add(0.0, -0.35, 0.0);
      UltronUprising.fx(level, UltronFxS2CPacket.HACK, hand, body.position().add(0.0, 1.6, 0.0), h.progress / (float)UltronRules.HACK_TICKS, n.kind.ordinal(),
         false);
      if (h.progress % 8 < step) {
         UltronSounds.play(level, body.position().add(0.0, 1.6, 0.0), UltronSounds.NODE_HACK, 1.0F, 0.8F + 0.6F * h.progress / UltronRules.HACK_TICKS);
      }
      alert(level, s, n, player);
      if (h.progress >= UltronRules.HACK_TICKS) {
         s.hacks.remove(player.getUUID());
         disable(level, s, n, player);
      }
      UltronHud.sendStateTo(player);
      return true;
   }

   static void tickHacks(ServerLevel level, UltronSession s) {
      if (s.hacks.isEmpty()) {
         return;
      }
      Iterator<Map.Entry<UUID, UltronSession.Hack>> it = s.hacks.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<UUID, UltronSession.Hack> e = it.next();
         UltronSession.Hack h = e.getValue();
         ServerPlayer p = s.server.getPlayerList().getPlayer(e.getKey());
         Node n = s.network.get(h.node);
         boolean gone = p == null || p.level() != level || n == null || n.state != UltronNodeState.ONLINE && n.state != UltronNodeState.BUILDING
            || p.distanceToSqr(n.x, n.y + 1.5, n.z) > 64.0;
         if (!gone && s.clock - h.last > 6) {
            h.progress -= UltronRules.HACK_DECAY;
            gone = h.progress <= 0;
         }
         if (gone) {
            it.remove();
            if (p != null) {
               UltronHud.sendStateTo(p);
            }
         }
      }
   }

   /** Machines near an attacked node go for the attacker (at most once per {@link #ALERT_GAP} per node). */
   static void alert(ServerLevel level, UltronSession s, Node n, Player attacker) {
      if (n.id < 0 || n.id >= s.alertAt.length || s.clock - s.alertAt[n.id] < ALERT_GAP && s.alertAt[n.id] != 0) {
         return;
      }
      s.alertAt[n.id] = s.clock;
      double r2 = ALERT_RANGE * ALERT_RANGE;
      for (UltronUnit u : s.units.values()) {
         if (!u.isAlive() || u.getTarget() != null || u.role() == UltronRole.REPAIR || u.role() == UltronRole.CAPTURE) {
            continue;
         }
         if (u.distanceToSqr(n.x, n.y, n.z) <= r2) {
            u.goal = attacker.position();
            u.hunt = attacker.getUUID();
            u.alertUntil = u.tickCount + ALERT_HOLD;
         }
      }
   }

   // ------------------------------------------------------------------ hooks: Ultron Prime

   static UltronBossRules.Tethers tethers(UltronSession s) {
      if (s.phase != UltronPhase.FINALE || s.outcome != 0) {
         return UltronBossRules.Tethers.NONE;
      }
      Node core = s.network.core();
      return UltronBossRules.tethers(s.network.active(UltronNodeKind.BULWARK), s.network.active(UltronNodeKind.REPAIR), s.network.active(UltronNodeKind.UPLINK),
         core != null && core.active(), s.network.active(UltronNodeKind.FOUNDRY), s.network.active(UltronNodeKind.SENSOR), s.adaptation.activations(),
         s.scenario.boss() == UltronScenario.BossStyle.ASCENDANT);
   }

   /** He stands: the upload into the world's networks starts (the uplinks carry it). */
   static void startUpload(ServerLevel level, UltronSession s) {
      if (s.uploading || s.outcome != 0) {
         return;
      }
      s.uploading = true;
      s.dirty = true;
      s.saveDirty = true;
      UltronUprising.cue(level, s, UltronSounds.UPLOAD_ALARM, 1.0F, 1.0F);
      for (Node n : s.network.all()) {
         if (n.active() && (n.kind == UltronNodeKind.UPLINK || n.kind == UltronNodeKind.CORE)) {
            Vec3 at = new Vec3(n.x, n.y + 4.5, n.z);
            UltronUprising.fx(level, UltronFxS2CPacket.UPLOAD, at, at.add(0.0, 60.0, 0.0), 1.0F, n.id, true);
         }
      }
   }

   /** Help from the live foundries (the core when none): up to three machines head for him. */
   static int summon(ServerLevel level, UltronSession s, UltronPrime prime) {
      int players = Math.max(1, fighters(level, s).size());
      int[] counts = counts(s);
      int alive = sum(counts);
      List<Node> sources = producers(s);
      int made = 0;
      for (Node n : sources) {
         if (made >= 3) {
            break;
         }
         if (!ChitauriTerrain.ticking(level, n.x, n.y + 1.0, n.z)) {
            continue;
         }
         if (!UltronProduction.mayBuild(UltronPhase.FINALE, alive, players, s.builtPhase, s.builtTotal)) {
            break;
         }
         UltronUnitKind k = s.intel.aerial(s.clock, SIGHT_WINDOW) && counts[UltronUnitKind.INTERCEPTOR.ordinal()] < UltronRules.kindCap(UltronUnitKind.INTERCEPTOR)
            ? UltronUnitKind.INTERCEPTOR
            : counts[UltronUnitKind.SENTRY.ordinal()] < UltronRules.kindCap(UltronUnitKind.SENTRY) ? UltronUnitKind.SENTRY : UltronUnitKind.DRONE;
         if (counts[k.ordinal()] >= UltronRules.kindCap(k)) {
            continue;
         }
         s.queue.add(new Spawn(k, spawnPoint(level, s, n, k), n.id, false, prime.position()));
         s.builtPhase++;
         s.builtTotal++;
         counts[k.ordinal()]++;
         alive++;
         made++;
      }
      return made;
   }

   /** He drains the nearest live node (within 48 blocks): the node pays in hull, he heals (capped). */
   static float absorb(ServerLevel level, UltronSession s, UltronPrime prime) {
      Node best = null;
      double bestD = 48.0 * 48.0;
      for (Node n : s.network.all()) {
         if (n.state == UltronNodeState.ONLINE) {
            double d = n.distanceSq(prime.getX(), prime.getY(), prime.getZ());
            if (d < bestD) {
               bestD = d;
               best = n;
            }
         }
      }
      if (best == null) {
         return 0.0F;
      }
      float got = Math.min(best.hull, UltronRules.hull(best.kind) * 0.15F);
      best.hull -= got;
      best.sinceHit = 0;
      Vec3 from = new Vec3(best.x, best.y + 3.5, best.z);
      UltronUprising.fx(level, UltronFxS2CPacket.UPLOAD, from, prime.position().add(0.0, 1.6, 0.0), 1.0F, prime.getId(), true);
      UltronSounds.play(level, prime.position(), UltronSounds.PRIME_ABSORB, 2.4F, 1.0F);
      UltronVoice.say(level, s, "prime_absorb", false);
      if (best.hull <= 0.5F) {
         best.hull = 0.0F;
         best.shield = 0.0F;
         best.state = UltronNodeState.DESTROYED;
         best.powered = false;
         nodeDestroyed(level, s, best, null);
      } else {
         syncBody(level, s, best);
      }
      s.dirty = true;
      return Math.min(prime.getMaxHealth() * 0.06F, got * 2.0F);
   }

   // ------------------------------------------------------------------ production

   /** Active foundries; the core alone (at half speed) while no foundry works. */
   static List<Node> producers(UltronSession s) {
      List<Node> out = new ArrayList<>();
      for (Node n : s.network.all()) {
         if (n.kind == UltronNodeKind.FOUNDRY && n.active()) {
            out.add(n);
         }
      }
      if (out.isEmpty()) {
         Node core = s.network.core();
         if (core != null && core.active()) {
            out.add(core);
         }
      }
      return out;
   }

   /** Machines per kind: alive, queued and in the foundries. */
   static int[] counts(UltronSession s) {
      int[] c = new int[UltronUnitKind.values().length];
      for (UltronUnit u : s.units.values()) {
         if (u.isAlive()) {
            c[u.kind().ordinal()]++;
         }
      }
      for (Spawn sp : s.queue) {
         c[sp.kind().ordinal()]++;
      }
      for (UltronSession.Fab f : s.fabs.values()) {
         c[f.kind.ordinal()]++;
      }
      return c;
   }

   static int sum(int[] c) {
      int t = 0;
      for (int v : c) {
         t += v;
      }
      return t;
   }

   static void tickProduction(ServerLevel level, UltronSession s) {
      if (s.phase == UltronPhase.ENDED) {
         return;
      }
      List<Node> producers = producers(s);
      if (producers.isEmpty()) {
         return;
      }
      boolean coreOnly = producers.size() == 1 && producers.get(0).kind == UltronNodeKind.CORE;
      boolean rush = s.adaptation.active(UltronAdaptation.Protocol.RELOCATION);
      int players = Math.max(1, fighters(level, s).size());
      int[] counts = null;
      for (Node n : producers) {
         if (!ChitauriTerrain.ticking(level, n.x, n.y + 1.0, n.z)) {
            continue; // a foundry out of reach sleeps (its build waits)
         }
         UltronSession.Fab f = s.fabs.get(n.id);
         if (f == null) {
            if (counts == null) {
               counts = counts(s);
            }
            f = startFab(s, counts, players, coreOnly);
            if (f == null) {
               continue;
            }
            s.fabs.put(n.id, f);
            counts[f.kind.ordinal()]++;
            UltronUprising.fx(level, UltronFxS2CPacket.FABRICATE, new Vec3(n.x, n.y + 3.5, n.z), Vec3.ZERO, 0.4F, f.kind.ordinal(), false);
         }
         f.left -= rush && s.clock % 3 == 0 ? 2 : 1;
         if (f.left <= 0) {
            s.fabs.remove(n.id);
            s.queue.add(new Spawn(f.kind, spawnPoint(level, s, n, f.kind), n.id, f.reserve, null));
         }
      }
   }

   @Nullable
   static UltronSession.Fab startFab(UltronSession s, int[] counts, int players, boolean coreOnly) {
      int alive = sum(counts);
      if (alive >= UltronRules.aliveCap(s.phase, players)) {
         return null;
      }
      int slow = coreOnly ? 2 : 1;
      // machines lost to an unloaded chunk or a restart come back first, without spending the budget
      for (UltronUnitKind k : UltronUnitKind.values()) {
         if (s.reserve[k.ordinal()] > 0 && counts[k.ordinal()] < UltronRules.kindCap(k)) {
            s.reserve[k.ordinal()]--;
            s.saveDirty = true;
            return new UltronSession.Fab(k, Math.max(20, UltronRules.fabTicks(k) / 2 * slow), true);
         }
      }
      if (!UltronProduction.mayBuild(s.phase, alive, players, s.builtPhase, s.builtTotal)) {
         return null;
      }
      // what the phase allows: scouts and builders while he wakes, no heavies before the war
      int[] masked = counts.clone();
      for (UltronUnitKind k : UltronUnitKind.values()) {
         boolean allowed = switch (s.phase) {
            case AWAKENING -> k == UltronUnitKind.DRONE || k == UltronUnitKind.ENGINEER;
            case SPREAD -> k != UltronUnitKind.BRUTE && k != UltronUnitKind.INTERCEPTOR;
            default -> true;
         };
         if (!allowed) {
            masked[k.ordinal()] = UltronRules.kindCap(k);
         }
      }
      UltronUnitKind k = UltronProduction.next(s.scenario, masked, s.doctrine, s.adaptation.active(UltronAdaptation.Protocol.ANTI_AIR),
         s.adaptation.active(UltronAdaptation.Protocol.DEFLECTOR), UltronOrders.jobs(s).size());
      if (k == null) {
         return null;
      }
      s.builtPhase++;
      s.builtTotal++;
      s.saveDirty = true;
      return new UltronSession.Fab(k, Math.round(UltronRules.fabTicks(k) * s.scenario.timeScale() * slow), false);
   }

   /** Where a machine leaves a pad: the node's side facing the area centre, on the ground. */
   static Vec3 spawnPoint(ServerLevel level, UltronSession s, Node n, UltronUnitKind kind) {
      double a = Math.atan2(s.anchor.z - n.z, s.anchor.x - n.x) + (level.random.nextDouble() - 0.5) * 1.6;
      double x = n.x + Math.cos(a) * 3.2;
      double z = n.z + Math.sin(a) * 3.2;
      int ground = ChitauriTerrain.surface(level, x, z);
      double y = ground == ChitauriTerrain.UNKNOWN ? n.y : ground;
      if (Math.abs(y - n.y) > 4.0) {
         x = n.x + Math.cos(a) * 1.5;
         z = n.z + Math.sin(a) * 1.5;
         y = n.y + 0.2;
      }
      return new Vec3(x, kind.flies() ? y + 1.2 : y, z);
   }

   static void drain(ServerLevel level, UltronSession s) {
      for (int i = 0; i < UltronRules.SPAWNS_PER_TICK && !s.queue.isEmpty(); i++) {
         Spawn sp = s.queue.poll();
         if (!spawnUnit(level, s, sp)) {
            s.reserve[sp.kind().ordinal()]++;
            s.saveDirty = true;
         }
      }
   }

   static EntityType<? extends UltronUnit> type(UltronUnitKind kind) {
      return switch (kind) {
         case DRONE -> UltronEntities.DRONE.get();
         case SENTRY -> UltronEntities.SENTRY.get();
         case BRUTE -> UltronEntities.BRUTE.get();
         case INTERCEPTOR -> UltronEntities.INTERCEPTOR.get();
         case ENGINEER -> UltronEntities.ENGINEER.get();
      };
   }

   static boolean spawnUnit(ServerLevel level, UltronSession s, Spawn sp) {
      Vec3 at = sp.at();
      if (!ChitauriTerrain.ticking(level, at.x, at.y, at.z)) {
         return false;
      }
      UltronUnit u = type(sp.kind()).create(level);
      if (u == null) {
         return false;
      }
      Node n = s.network.get(sp.node());
      float yaw = (float)(Mth.atan2(s.anchor.z - at.z, s.anchor.x - at.x) * Mth.RAD_TO_DEG) - 90.0F;
      u.moveTo(at.x, at.y, at.z, yaw, 0.0F);
      u.setYHeadRot(yaw);
      u.setYBodyRot(yaw);
      u.netId = s.nextNetId();
      u.post = n != null ? new Vec3(n.x, n.y, n.z) : s.anchor;
      if (sp.goal() != null) {
         u.goal = sp.goal();
         u.alertUntil = ALERT_HOLD * 3;
      }
      if (!level.addFreshEntity(u)) {
         return false;
      }
      s.units.put(u.getUUID(), u);
      u.forceMove(UltronMove.FABRICATE, null);
      Vec3 from = n != null ? new Vec3(n.x, n.y + 3.5, n.z) : at.add(0.0, 3.0, 0.0);
      UltronUprising.fx(level, UltronFxS2CPacket.FABRICATE, from, at, 1.0F, sp.kind().ordinal(), false);
      UltronSounds.play(level, at, UltronSounds.FABRICATE, 1.6F, switch (sp.kind()) {
         case DRONE -> 1.25F;
         case SENTRY -> 1.0F;
         case BRUTE -> 0.75F;
         case INTERCEPTOR -> 1.1F;
         case ENGINEER -> 0.95F;
      });
      s.dirty = true;
      return true;
   }

   // ------------------------------------------------------------------ the finale

   @Nullable
   static Entity boss(ServerLevel level, UltronSession s) {
      return s.boss == null ? null : level.getEntity(s.boss);
   }

   /** Every node lights up on the HUD; Ultron picks where he assembles (the scenario's rule). */
   static void beginFinale(ServerLevel level, UltronSession s) {
      for (Node n : s.network.all()) {
         if (n.standing()) {
            n.discovered = true;
         }
      }
      Vec3 at = null;
      int nodeId = -1;
      Node pick = switch (s.scenario.finale()) {
         case UPLINK -> s.network.strongest(UltronNodeKind.UPLINK);
         case FOUNDRY -> s.network.strongest(UltronNodeKind.FOUNDRY);
         case CORE -> s.network.coreStanding() ? s.network.core() : s.network.strongest(null);
         case HUNT -> null;
      };
      if (s.scenario.finale() == UltronScenario.FinaleRule.HUNT) {
         UltronIntel.Sighting t = s.intel.top(s.clock, 120 * UltronRules.TPS);
         if (t != null && horizontal(s.anchor, t.x, t.z) <= s.radius * 1.3) {
            // in front of the most dangerous hero he has seen
            double a = Math.atan2(s.anchor.z - t.z, s.anchor.x - t.x);
            at = new Vec3(t.x + Math.cos(a) * 14.0, t.y, t.z + Math.sin(a) * 14.0);
         } else {
            pick = s.network.strongest(null);
         }
      }
      if (at == null && pick != null) {
         double a = Math.atan2(s.anchor.z - pick.z, s.anchor.x - pick.x);
         at = new Vec3(pick.x + Math.cos(a) * 7.0, pick.y, pick.z + Math.sin(a) * 7.0);
         nodeId = pick.id;
      }
      if (at == null) {
         at = s.anchor;
      }
      s.assembly = at;
      s.assemblyNode = nodeId;
      s.bossStage = UltronBossRules.Stage.ASSEMBLY.ordinal();
      s.bossShare = 1.0F;
      s.upload = 0.0F;
      s.uploading = false;
      s.bossMissing = 0;
      s.summonClock = 0;
      UltronUprising.fx(level, UltronFxS2CPacket.MARK, at.add(0.0, 0.2, 0.0), Vec3.ZERO, 3.0F, 0, true);
      s.dirty = true;
      s.saveDirty = true;
   }

   static void tickFinale(ServerLevel level, UltronSession s) {
      Entity e = boss(level, s);
      UltronPrime prime = e instanceof UltronPrime p && !p.isRemoved() ? p : null;
      if (prime == null) {
         if (s.boss != null && ++s.bossMissing < BOSS_MISSING_TICKS) {
            return;
         }
         s.boss = null;
         s.bossMissing = 0;
         if (s.assembly != null && ChitauriTerrain.ticking(level, s.assembly.x, s.assembly.y + 1.0, s.assembly.z)) {
            spawnPrime(level, s);
         }
      } else {
         s.bossMissing = 0;
         if (prime.isAlive()) {
            s.bossShare = Mth.clamp(prime.getHealth() / prime.getMaxHealth(), 0.0F, 1.0F);
         }
      }
      if (!s.uploading) {
         return;
      }
      Node core = s.network.core();
      int uplinks = s.network.active(UltronNodeKind.UPLINK) + (core != null && core.active() ? 1 : 0);
      float rate = UltronRules.uploadRate(uplinks);
      float total = Math.max(1, s.scenario.uploadTicks());
      s.upload = Math.min(total, s.upload + rate);
      float share = s.upload / total;
      if (s.upload >= 100.0F && !s.said.containsKey("upload_start")) {
         UltronVoice.say(level, s, "upload_start", false);
      }
      if (share >= 0.5F && !s.said.containsKey("upload_half")) {
         UltronVoice.say(level, s, "upload_half", false);
      }
      if (share >= 0.85F && !s.said.containsKey("upload_near")) {
         UltronVoice.say(level, s, "upload_near", false);
      }
      if (share >= 0.85F && s.clock % 200 == 0) {
         UltronUprising.cue(level, s, UltronSounds.UPLOAD_ALARM, 0.9F, 1.15F);
      }
      if (rate <= 0.0F && s.clock % 400 == 0) {
         for (ServerPlayer p : viewers(level, s)) {
            p.displayClientMessage(Component.translatable("viltrumitecore.ultron.upload_stalled").withStyle(ChatFormatting.AQUA), true);
         }
      }
      if (s.upload >= total) {
         UltronUprising.LOG.info("Ultron uprising: upload complete (seed {}): defeat", s.seed);
         end(level, s, false);
      }
   }

   static void spawnPrime(ServerLevel level, UltronSession s) {
      Vec3 at = s.assembly;
      int ground = ChitauriTerrain.surface(level, at.x, at.z);
      double y = ground == ChitauriTerrain.UNKNOWN ? at.y : ground;
      UltronPrime prime = UltronEntities.PRIME.get().create(level);
      if (prime == null) {
         return;
      }
      float yaw = (float)(Mth.atan2(s.anchor.z - at.z, s.anchor.x - at.x) * Mth.RAD_TO_DEG) - 90.0F;
      prime.moveTo(at.x, y, at.z, yaw, 0.0F);
      prime.setYHeadRot(yaw);
      prime.setYBodyRot(yaw);
      prime.setup(UltronBossRules.Stage.byId(s.bossStage), s.bossShare, Math.max(1, fighters(level, s).size()));
      if (!level.addFreshEntity(prime)) {
         return;
      }
      s.boss = prime.getUUID();
      Vec3 c = new Vec3(at.x, y + 1.5, at.z);
      UltronUprising.fx(level, UltronFxS2CPacket.PRIME_ASSEMBLE, c, Vec3.ZERO, 1.0F, prime.getId(), true);
      UltronSounds.playWide(level, c, UltronSounds.PRIME_ASSEMBLE, 3.0F, 1.0F, 160.0);
      // the network sends him its matter: beams from every live node
      for (Node n : s.network.all()) {
         if (n.active()) {
            UltronUprising.fx(level, UltronFxS2CPacket.UPLOAD, new Vec3(n.x, n.y + 3.5, n.z), c, 1.0F, prime.getId(), true);
         }
      }
      s.dirty = true;
      UltronUprising.LOG.info("Ultron Prime at {} {} {} (stage {}, health share {})", Mth.floor(at.x), Mth.floor(y), Mth.floor(at.z),
         UltronBossRules.Stage.byId(s.bossStage).key(), s.bossShare);
   }

   // ------------------------------------------------------------------ beats and transitions

   static void beats(ServerLevel level, UltronSession s) {
      if (s.phase == UltronPhase.AWAKENING) {
         if (s.phaseTick == 40) {
            UltronVoice.say(level, s, "awaken", true);
         }
         if (s.phaseTick % 160 == 80) {
            // glitch pulses: screens, lamps and radios of the area go mad for a moment
            double a = level.random.nextDouble() * Math.PI * 2.0;
            double r = s.radius * (0.2 + level.random.nextDouble() * 0.6);
            double x = s.anchor.x + Math.cos(a) * r;
            double z = s.anchor.z + Math.sin(a) * r;
            int ground = ChitauriTerrain.surface(level, x, z);
            if (ground != ChitauriTerrain.UNKNOWN) {
               Vec3 at = new Vec3(x, ground + 1.5, z);
               UltronUprising.fx(level, UltronFxS2CPacket.GLITCH, at, Vec3.ZERO, 6.0F, 0, false);
               UltronSounds.play(level, at, UltronSounds.TRANSMISSION, 1.6F, 0.6F + level.random.nextFloat() * 0.3F);
            }
         }
         return;
      }
      if (s.phase.atLeast(UltronPhase.MACHINE_WAR) && s.phaseTick % (TAUNT_EVERY_S * UltronRules.TPS) == 0) {
         boolean fighting = false;
         for (UltronUnit u : s.units.values()) {
            if (u.isAlive() && u.flag(UltronUnit.FLAG_COMBAT)) {
               fighting = true;
               break;
            }
         }
         String taunt = fighting ? UltronVoice.nextTaunt(s) : null;
         if (taunt != null) {
            UltronVoice.say(level, s, taunt, false);
         }
      }
   }

   static void transitions(ServerLevel level, UltronSession s) {
      UltronNetwork net = s.network;
      int standing = net.standing();
      if (UltronRules.earlyFinale(s.phase, net.coreStanding(), standing)) {
         enter(level, s, UltronPhase.FINALE);
         return;
      }
      int built;
      if (s.phase == UltronPhase.AWAKENING) {
         Node core = net.core();
         built = core != null && core.online() ? 1 : 0;
      } else {
         built = 0;
         for (Node n : net.all()) {
            if (wave(s, n) == 1 && n.state != UltronNodeState.PLANNED) {
               built++;
            }
         }
      }
      if (UltronRules.phaseDone(s.phase, s.phaseTick, s.scenario.timeScale(), built, s.scenario.spreadTarget(), s.adaptation.combatPressure(),
         s.adaptation.activations(), standing)) {
         enter(level, s, s.phase.next());
      }
   }

   // ------------------------------------------------------------------ players, HUD state, bars

   /** Players of the event's level within its view range (spectators and creative included: they see the HUD). */
   static List<ServerPlayer> viewers(ServerLevel level, UltronSession s) {
      if (s.viewersCache != null && s.viewersAt == s.clock && s.viewersLevel == level) {
         return s.viewersCache;
      }
      List<ServerPlayer> out = new ArrayList<>();
      if (s.in(level)) {
         double r2 = UltronRules.VIEW_RANGE * UltronRules.VIEW_RANGE;
         for (ServerPlayer p : level.players()) {
            if (horizontalSq(s.anchor, p.getX(), p.getZ()) <= r2) {
               out.add(p);
            }
         }
      }
      s.viewersCache = out;
      s.viewersAt = s.clock;
      s.viewersLevel = level;
      return out;
   }

   /** The viewers who fight (survival / adventure): group scaling, caps, boss health. */
   static List<ServerPlayer> fighters(ServerLevel level, UltronSession s) {
      List<ServerPlayer> out = new ArrayList<>();
      for (ServerPlayer p : viewers(level, s)) {
         if (!p.isSpectator() && !p.isCreative()) {
            out.add(p);
         }
      }
      return out;
   }

   static double horizontalSq(Vec3 c, double x, double z) {
      double dx = x - c.x;
      double dz = z - c.z;
      return dx * dx + dz * dz;
   }

   static double horizontal(Vec3 c, double x, double z) {
      return Math.sqrt(horizontalSq(c, x, z));
   }
}
