package dev.baranhan.viltrumitecore.worldevent.godzilla;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.power.api.AlarmStage;
import dev.baranhan.viltrumitecore.city.power.api.CityInfrastructure;
import dev.baranhan.viltrumitecore.entity.godzilla.GodzillaEntity;
import dev.baranhan.viltrumitecore.worldevent.godzilla.net.GodzillaFxS2CPacket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * One running catastrophe (memory only). Ticked from the kaiju's own entity tick, so every timer stops while the
 * body is not ticking (no player near, chunk not entity-ticking): nothing runs where nobody is. Holds the phase,
 * the threat table, the cooldowns, the rage and stagger meters, the alarm lease, the boss bar and the statistics;
 * {@link GodzillaBrain} picks the moves, {@link GodzillaActions} lands them, {@link GodzillaBreath} runs the beam.
 */
public final class GodzillaSession {
   /** One hero (or anything else that hurt it) the kaiju knows about. */
   static final class Threat {
      float value;
      float dealt;
      long seen;
      boolean airborne;
   }

   final MinecraftServer server;
   final ResourceKey<Level> dimension;
   final String id;
   final RandomSource random;
   final UUID bodyId;
   @Nullable
   GodzillaEntity body;
   final Vec3 anchor;
   final Vec3 home;
   GodzillaPhase phase = GodzillaPhase.ARRIVAL;
   int clock;
   int phaseTick;
   long lastBodyTick;
   long startedAt;
   @Nullable
   Vec3 waypoint;
   int waypointAge;
   int stuck;
   final Map<UUID, Threat> threats = new HashMap<>();
   @Nullable
   UUID target;
   int lostTarget;
   // cooldowns (ticks left)
   int cdClaw;
   int cdTail;
   int cdStomp;
   int cdSlam = 200;
   int cdRoar = 0;
   int cdBreath = 260;
   int cdHurt;
   int cdFall = 600;
   int cdExploreBreath = 900;
   int cdLook;
   // meters
   float burst;
   float stagger;
   // statistics
   float damageTaken;
   int heroesHit;
   int kills;
   int breaths;
   int moves;
   int roars;
   // debug switches (commands)
   boolean passive;
   boolean targetCreative;
   final Set<UUID> barPlayers = new HashSet<>();
   final ServerBossEvent bar;
   final GodzillaDestruction destruction;
   final GodzillaBreath breath = new GodzillaBreath();
   int endTicks = -1;
   boolean closed;
   int retreatSink;

   GodzillaSession(MinecraftServer server, ResourceKey<Level> dimension, UUID bodyId, Vec3 anchor, Vec3 home, long seed) {
      this.server = server;
      this.dimension = dimension;
      this.bodyId = bodyId;
      this.anchor = anchor;
      this.home = home;
      this.random = RandomSource.create(seed);
      this.id = Long.toString(seed & 0xFFFFFFL, 36);
      this.destruction = new GodzillaDestruction(this.id);
      this.bar = new ServerBossEvent(Component.translatable("entity.viltrumitecore.godzilla"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
      this.bar.setDarkenScreen(false);
      this.bar.setCreateWorldFog(false);
   }

   boolean in(Level level) {
      return level.dimension() == this.dimension && level.getServer() == this.server;
   }

   @Nullable
   ServerLevel level() {
      return this.server.getLevel(this.dimension);
   }

   boolean rage() {
      return this.phase.enraged();
   }

   float healthShare() {
      GodzillaEntity e = this.body;
      return e == null ? 1.0F : Mth.clamp(e.getHealth() / e.getMaxHealth(), 0.0F, 1.0F);
   }

   GodzillaRules.Cooldowns cooldowns() {
      return new GodzillaRules.Cooldowns(this.cdClaw, this.cdTail, this.cdStomp, this.cdSlam, this.cdRoar, this.cdBreath);
   }

   void cool() {
      this.cdClaw = Math.max(0, this.cdClaw - 1);
      this.cdTail = Math.max(0, this.cdTail - 1);
      this.cdStomp = Math.max(0, this.cdStomp - 1);
      this.cdSlam = Math.max(0, this.cdSlam - 1);
      this.cdRoar = Math.max(0, this.cdRoar - 1);
      this.cdBreath = Math.max(0, this.cdBreath - 1);
      this.cdHurt = Math.max(0, this.cdHurt - 1);
      this.cdFall = Math.max(0, this.cdFall - 1);
      this.cdExploreBreath = Math.max(0, this.cdExploreBreath - 1);
      this.cdLook = Math.max(0, this.cdLook - 1);
      // the rage meter: damage within the burst window; the stagger meter: within the stagger window
      this.burst = Math.max(0.0F, this.burst - GodzillaRules.RAGE_BURST / GodzillaRules.RAGE_BURST_TICKS);
      this.stagger = Math.max(0.0F, this.stagger - GodzillaRules.STAGGER_FALL / GodzillaRules.STAGGER_TICKS * 0.5F);
   }

   /** Starts a move and its cooldown. */
   void start(GodzillaEntity e, GodzillaMove move, int arg) {
      boolean rage = this.rage();
      switch (move) {
         case CLAW_L, CLAW_R -> this.cdClaw = GodzillaRules.cooldown(GodzillaRules.CLAW_COOLDOWN, rage);
         case TAIL -> this.cdTail = GodzillaRules.cooldown(GodzillaRules.TAIL_COOLDOWN, rage);
         case STOMP -> this.cdStomp = GodzillaRules.cooldown(GodzillaRules.STOMP_COOLDOWN, rage);
         case SLAM -> this.cdSlam = GodzillaRules.cooldown(GodzillaRules.SLAM_COOLDOWN, rage);
         case ROAR -> {
            this.cdRoar = GodzillaRules.cooldown(GodzillaRules.ROAR_COOLDOWN, rage);
            this.roars++;
         }
         case CHARGE -> {
            this.cdBreath = GodzillaRules.breathCooldown(rage) + GodzillaRules.CHARGE_TICKS + GodzillaRules.FIRE_TICKS;
            this.breaths++;
         }
         case HURT -> this.cdHurt = GodzillaRules.HURT_COOLDOWN;
         case FALL -> this.cdFall = GodzillaRules.FALL_COOLDOWN;
         default -> {
         }
      }
      // a move started: claws and tail also keep each other from chaining back to back
      if (move == GodzillaMove.CLAW_L || move == GodzillaMove.CLAW_R) {
         this.cdTail = Math.max(this.cdTail, 30);
      } else if (move == GodzillaMove.TAIL) {
         this.cdClaw = Math.max(this.cdClaw, 30);
      }
      this.moves++;
      e.startMove(move, arg);
   }

   // ------------------------------------------------------------------ threat

   Threat threat(UUID id) {
      return this.threats.computeIfAbsent(id, k -> new Threat());
   }

   /** May this entity be fought (players: survival / adventure, or creative with the debug switch)? */
   boolean targetable(@Nullable Entity e) {
      if (!(e instanceof LivingEntity living) || !living.isAlive() || e instanceof GodzillaEntity) {
         return false;
      }
      if (e instanceof Player p) {
         return !p.isSpectator() && (!p.isCreative() || this.targetCreative);
      }
      return true;
   }

   /** Every 10 ticks: proximity raises the threat of the heroes near the body, old threats fade. */
   void updateThreats(ServerLevel level, GodzillaEntity e) {
      long now = level.getGameTime();
      Vec3 at = e.position();
      for (ServerPlayer p : level.players()) {
         if (!this.targetable(p)) {
            continue;
         }
         double dx = p.getX() - at.x;
         double dz = p.getZ() - at.z;
         double flat = Math.sqrt(dx * dx + dz * dz);
         if (flat > GodzillaRules.LOSE_RANGE) {
            continue;
         }
         Threat t = this.threat(p.getUUID());
         t.seen = now;
         t.airborne = GodzillaEvent.airborne(p);
         if (flat < GodzillaRules.AGGRO_RANGE) {
            t.value += 1.5F * (1.0F - (float)(flat / GodzillaRules.AGGRO_RANGE)) + (t.airborne ? 1.0F : 0.0F);
         }
      }
      Iterator<Map.Entry<UUID, Threat>> it = this.threats.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<UUID, Threat> en = it.next();
         Threat t = en.getValue();
         t.value *= 0.985F;
         Entity who = level.getEntity(en.getKey());
         if (!this.targetable(who) || now - t.seen > 1200 || t.value < 0.2F && now - t.seen > 200) {
            it.remove();
            continue;
         }
         if (who.distanceToSqr(e) <= GodzillaRules.LOSE_RANGE * GodzillaRules.LOSE_RANGE) {
            t.seen = now;
         }
      }
      // the target: the biggest threat in range (a big enough lead keeps it from flip-flopping)
      UUID best = null;
      float bestValue = 0.0F;
      for (Map.Entry<UUID, Threat> en : this.threats.entrySet()) {
         Entity who = level.getEntity(en.getKey());
         if (who == null || who.distanceToSqr(e) > GodzillaRules.LOSE_RANGE * GodzillaRules.LOSE_RANGE) {
            continue;
         }
         float v = en.getValue().value * (en.getKey().equals(this.target) ? 1.25F : 1.0F);
         if (v > bestValue) {
            bestValue = v;
            best = en.getKey();
         }
      }
      if (best != null && bestValue >= 2.0F) {
         this.target = best;
         this.lostTarget = 0;
      } else if (this.target != null && ++this.lostTarget * 10 > GodzillaRules.COMBAT_MEMORY) {
         this.target = null;
      }
   }

   @Nullable
   LivingEntity target(ServerLevel level) {
      if (this.target == null) {
         return null;
      }
      Entity e = level.getEntity(this.target);
      return this.targetable(e) ? (LivingEntity)e : null;
   }

   float targetThreat() {
      Threat t = this.target == null ? null : this.threats.get(this.target);
      return t == null ? 0.0F : t.value;
   }

   /** Something living took a hit from the kaiju (statistics). */
   void noteHit(LivingEntity living) {
      if (living instanceof Player) {
         this.heroesHit++;
      }
   }

   // ------------------------------------------------------------------ phase

   void phase(ServerLevel level, GodzillaEntity e, GodzillaPhase next) {
      if (this.phase == next) {
         return;
      }
      GodzillaPhase prev = this.phase;
      this.phase = next;
      this.phaseTick = 0;
      e.setFlag(GodzillaEntity.FLAG_RAGE, next.enraged());
      e.setFlag(GodzillaEntity.FLAG_FINALE, next == GodzillaPhase.FINALE);
      e.setFlag(GodzillaEntity.FLAG_RETREAT, next == GodzillaPhase.RETREAT);
      GodzillaEvent.LOG.info("Godzilla phase {} -> {} (health {}%, clock {})", prev.key(), next.key(), Math.round(this.healthShare() * 100.0F), this.clock);
      switch (next) {
         case RAGE -> {
            this.say(level, "viltrumitecore.godzilla.rage", ChatFormatting.RED);
            this.cdRoar = 0;
            this.cdBreath = Math.min(this.cdBreath, 120);
         }
         case FINALE -> {
            this.say(level, "viltrumitecore.godzilla.finale", ChatFormatting.DARK_RED);
            this.cdRoar = 0;
            this.cdBreath = Math.min(this.cdBreath, 60);
         }
         case RETREAT -> {
            this.say(level, "viltrumitecore.godzilla.retreat", ChatFormatting.GOLD);
            GodzillaNet.always(level, GodzillaFxS2CPacket.RETREAT, e.position(), GodzillaEntity.forward(e.getYRot()), 1.0F, 0, GodzillaRules.FX_FAR);
         }
         case COMBAT -> {
            if (prev == GodzillaPhase.EXPLORE || prev == GodzillaPhase.ARRIVAL) {
               this.say(level, "viltrumitecore.godzilla.combat", ChatFormatting.YELLOW);
            }
         }
         default -> {
         }
      }
      this.alarm(level, e, true);
   }

   /** The city alarm lease: warning on arrival, attack when it fights, critical in rage; released at the end. */
   void alarm(ServerLevel level, GodzillaEntity e, boolean now) {
      if (!now && this.clock % GodzillaRules.ALARM_REFRESH != 0) {
         return;
      }
      if (CityManager.of(level) == null) {
         return;
      }
      AlarmStage stage = switch (this.phase) {
         case ARRIVAL -> AlarmStage.WARNING;
         case EXPLORE, COMBAT -> AlarmStage.ACTIVE;
         case RAGE, FINALE -> AlarmStage.CRITICAL;
         default -> AlarmStage.NONE;
      };
      if (stage == AlarmStage.NONE) {
         CityInfrastructure.clearAlarm(level, "godzilla");
      } else {
         CityInfrastructure.raiseAlarm(level, "godzilla", stage, e.position(), GodzillaRules.HEIGHT * 3.0F, GodzillaRules.ALARM_LEASE);
      }
   }

   /** The boss bar for the players near the body (every 10 ticks). */
   void updateBar(ServerLevel level, GodzillaEntity e) {
      this.bar.setProgress(this.healthShare());
      this.bar.setColor(this.rage() ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.BLUE);
      double r2 = GodzillaRules.FX_NEAR * 1.5 * GodzillaRules.FX_NEAR * 1.5;
      for (ServerPlayer p : level.players()) {
         boolean near = p.distanceToSqr(e) < r2 && !this.phase.equals(GodzillaPhase.DEATH);
         if (near && this.barPlayers.add(p.getUUID())) {
            this.bar.addPlayer(p);
         } else if (!near && this.barPlayers.remove(p.getUUID())) {
            this.bar.removePlayer(p);
         }
      }
   }

   void say(ServerLevel level, String key, ChatFormatting style) {
      Component line = Component.translatable(key).withStyle(style);
      for (ServerPlayer p : level.players()) {
         if (p.distanceToSqr(this.body != null ? this.body.position() : this.anchor) < GodzillaRules.FX_FAR * GodzillaRules.FX_FAR) {
            p.sendSystemMessage(line);
         }
      }
   }

   void close() {
      this.closed = true;
      this.bar.removeAllPlayers();
      this.barPlayers.clear();
   }
}
