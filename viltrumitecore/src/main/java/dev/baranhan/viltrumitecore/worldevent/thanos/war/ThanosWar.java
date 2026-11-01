package dev.baranhan.viltrumitecore.worldevent.thanos.war;

import dev.baranhan.viltrumitecore.entity.thanos.ThanosEntity;
import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.ThanosWarS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosBrain;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosCombat;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosEvent;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * THANOS 2.0: the war around Thanos for one event. Owns the Sanctuary II ({@link SanctuaryShip}),
 * the Black Order and the Chitauri ({@link ThanosArmy}), the city front ({@link ThanosCityFront}:
 * alarms, blackouts), the shared hold ledger ({@link ThanosHolds}, {@link ThanosControlLedger}: no
 * endless stuns), the threat table his targeting reads ({@link ThanosThreat}) and the destruction
 * budget ({@link ThanosWarDestruction}). {@link ThanosEvent} drives it: begin, one tick per server
 * tick, the phase changes, victory, the snap and the end (everything it spawned is removed there).
 */
public final class ThanosWar {
   static final Logger LOG = LoggerFactory.getLogger("viltrumitecore/thanos_war");
   public static final String VOICE_THANOS = "thanos";
   public static final String VOICE_MAW = "ebony_maw";
   public static final String VOICE_CULL = "cull_obsidian";
   /** Clients within this (horizontal) range get the war state (the ship is seen from far away). */
   public static final double SYNC_RANGE = 512.0;
   static final int SYNC_EVERY = 10;
   /** A phase's forces deploy on his COMMAND beat, or after this long anyway (he was stunned). */
   static final int DEPLOY_GRACE = 100;
   /** Range of the war's voice lines. */
   static final double VOICE_RANGE = 160.0;

   public final ThanosEvent event;
   public final ServerLevel level;
   public final ThanosEntity thanos;
   public final ThanosCombat combat;
   public final ThanosControlLedger ledger = new ThanosControlLedger();
   public final ThanosThreat threat = new ThanosThreat();
   public final ThanosHolds holds;
   public final ThanosWarDestruction destruction;
   public final SanctuaryShip ship;
   public final ThanosArmy army;
   public final ThanosCityFront city;
   final RandomSource random;

   private ThanosPhase phase = ThanosPhase.ARRIVAL;
   private ThanosPhase deployed = ThanosPhase.ARRIVAL;
   private int deployIn = -1;
   private int ticks;
   private int defensive;
   private final float[] damage = new float[ThanosWarRules.HEAVY_WINDOW];
   private boolean over;
   private boolean ended;
   private final Map<String, Long> said = new HashMap<>();
   private final Set<UUID> viewers = new HashSet<>();

   public ThanosWar(ThanosEvent event, ServerLevel level, ThanosEntity thanos, ThanosCombat combat) {
      this.event = event;
      this.level = level;
      this.thanos = thanos;
      this.combat = combat;
      this.random = RandomSource.create(level.random.nextLong());
      this.holds = new ThanosHolds(this);
      this.destruction = new ThanosWarDestruction(level);
      this.ship = new SanctuaryShip(this);
      this.army = new ThanosArmy(this);
      this.city = new ThanosCityFront(this);
   }

   /** The running event's war in {@code level}, or null. */
   @Nullable
   public static ThanosWar of(@Nullable Level level) {
      ThanosEvent e = ThanosEvent.active();
      return e != null && level != null && e.war() != null && e.war().level == level && !e.war().ended ? e.war() : null;
   }

   public long now() {
      return this.level.getGameTime();
   }

   public ThanosPhase phase() {
      return this.phase;
   }

   public int ticks() {
      return this.ticks;
   }

   /** Victory or the snap: the war winds down (no new attacks, no new waves). */
   public boolean over() {
      return this.over;
   }

   public boolean ended() {
      return this.ended;
   }

   /** He took heavy damage lately: he and his allies fight defensively for a while. */
   public boolean defensive() {
      return this.defensive > 0;
   }

   public List<ServerPlayer> heroes() {
      return this.combat.heroes();
   }

   public int heroCount() {
      return this.event.heroCount();
   }

   // ---- lifecycle ----------------------------------------------------------------------------------
   public void begin() {
      this.ship.start(this.thanos.position());
      this.city.begin();
      this.say(VOICE_THANOS, this.thanos, "arrival", HeroLine.Priority.ULTIMATE, 0, true);
      this.sync(true);
   }

   public void tick(ThanosEvent.Stage stage, boolean paused) {
      if (this.ended) {
         return;
      }

      if (paused) {
         // a hero's world pause holds the war too (the ship, the waves, the holds' clocks)
         if (this.level.getGameTime() % SYNC_EVERY == 0) {
            this.sync(false);
         }

         return;
      }

      this.ticks++;
      this.damage[this.ticks % this.damage.length] = 0.0F;
      this.threat.tick();
      if (this.defensive > 0) {
         this.defensive--;
      }

      if (this.ticks % 20 == 0) {
         this.ledger.prune(this.now());
      }

      if (this.deployIn > 0 && --this.deployIn == 0) {
         this.deploy(this.phase);
      }

      this.holds.tick(this.level);
      this.ship.tick(stage);
      this.army.tick(stage);
      this.city.tick();
      if (this.ticks % SYNC_EVERY == 0) {
         this.sync(false);
      }
   }

   public void onPhase(ThanosPhase next) {
      if (this.over) {
         return;
      }

      this.phase = next;
      this.ship.onPhase(next);
      this.city.onPhase(next);
      this.army.onPhase(next);
      this.deployIn = DEPLOY_GRACE;
      this.sync(true);
   }

   /** His COMMAND beat (first active tick of the move): what the phase brings deploys now. */
   public void command() {
      if (!this.over) {
         this.deploy(this.phase);
      }
   }

   private void deploy(ThanosPhase p) {
      this.deployIn = -1;
      if (this.over || this.deployed.atLeast(p)) {
         return;
      }

      ThanosPhase from = this.deployed;
      this.deployed = p;
      if (ThanosWarRules.orderPhase(p) && !ThanosWarRules.orderPhase(from)) {
         this.army.callOrder();
         this.say(VOICE_THANOS, this.thanos, "call_order", HeroLine.Priority.ULTIMATE, 0, true);
      }

      if (ThanosWarRules.armyPhase(p) && !ThanosWarRules.armyPhase(from)) {
         this.army.openFront();
         this.ship.bombard(true);
         this.say(VOICE_THANOS, this.thanos, "call_army", HeroLine.Priority.ULTIMATE, 0, true);
      }

      if (p == ThanosPhase.GAUNTLET) {
         this.army.enrage();
         this.say(VOICE_THANOS, this.thanos, "gauntlet", HeroLine.Priority.ULTIMATE, 0, true);
      }

      this.sync(true);
   }

   public void onVictory() {
      if (this.over) {
         return;
      }

      this.over = true;
      this.holds.clear();
      this.ship.leave();
      this.army.retreat();
      this.city.allClear();
      this.sync(true);
   }

   public void onSnap() {
      if (this.over) {
         return;
      }

      this.over = true;
      this.holds.clear();
      this.ship.cancelStrikes();
      this.army.standDown();
      this.sync(true);
   }

   /** The snap scene took him away: his war goes with him. */
   public void onThanosGone() {
      this.army.clear(true);
      this.ship.leave();
      this.city.allClear();
      this.sync(true);
   }

   /** The event is over (any reason): nothing of the war stays in the world. */
   public void end(ThanosEvent.EndKind kind) {
      if (this.ended) {
         return;
      }

      this.over = true;
      this.holds.clear();
      this.army.clear(kind == ThanosEvent.EndKind.VICTORY || kind == ThanosEvent.EndKind.SNAP);
      this.ship.clear();
      this.city.end();
      this.ledger.clear();
      this.threat.clear();
      this.ended = true;
      for (ServerPlayer p : this.level.getServer().getPlayerList().getPlayers()) {
         if (this.viewers.contains(p.getUUID()) || p.level() == this.level) {
            CoreMessages.sendToPlayer(ThanosWarS2CPacket.none(), p);
         }
      }

      this.viewers.clear();
      LOG.info("Thanos war ended ({}): {} block(s) destroyed, {} Chitauri brought in", kind, this.destruction.removed(),
         this.army.spawned());
   }

   // ---- hits and tactics -------------------------------------------------------------------------
   /** A hit Thanos took (health actually lost). */
   public void onHit(Entity attacker, float amount, boolean ranged) {
      if (this.ended) {
         return;
      }

      if (attacker instanceof LivingEntity) {
         this.threat.hit(attacker.getUUID(), amount, ranged);
      }

      this.damage[this.ticks % this.damage.length] += amount;
      float sum = 0.0F;
      for (float f : this.damage) {
         sum += f;
      }

      if (this.defensive <= 0 && !this.over && sum >= ThanosWarRules.HEAVY_DAMAGE * this.thanos.getMaxHealth()) {
         this.defensive = ThanosWarRules.DEFENSIVE_TICKS;
         Arrays.fill(this.damage, 0.0F);
         this.army.protect();
         this.say(VOICE_THANOS, this.thanos, "pressed", HeroLine.Priority.ABILITY, 600, false);
      }
   }

   /** What the war adds to his brain's view of the fight. */
   public ThanosBrain.War brainWar(@Nullable LivingEntity target) {
      int flyers = 0;
      for (ServerPlayer p : this.heroes()) {
         if (this.combat.airborne(p)) {
            flyers++;
         }
      }

      boolean ranged = false;
      boolean pinned = false;
      boolean controllable = true;
      int allies = 0;
      if (target != null) {
         ranged = this.threat.rangedShare(target.getUUID()) >= ThanosWarRules.RANGED_SHARE;
         pinned = this.holds.pinned(target);
         controllable = this.ledger.controllable(target.getUUID(), this.now()) && ThanosHolds.allows(target, ThanosHolds.Kind.DRAG);
         allies = this.army.engaged(target);
      }

      return new ThanosBrain.War(!this.over && this.ship.ready(), ranged, flyers, this.defensive > 0, pinned, allies, controllable);
   }

   // ---- voice --------------------------------------------------------------------------------------
   /**
    * A voice line of the war ({@code subtitle.viltrumitecore.thanos_war.<voice>.<line>}) with the
    * speaker's portrait; at most once per {@code gap} ticks per line unless forced.
    */
   public boolean say(String voice, @Nullable Entity speaker, String line, HeroLine.Priority priority, int gap, boolean force) {
      long now = this.now();
      String key = voice + "." + line;
      Long last = this.said.get(key);
      if (!force && last != null && now - last < gap) {
         return false;
      }

      Vec3 at = speaker != null ? speaker.position() : this.thanos.position();
      boolean ok = HeroSpeech.worldLine(this.level, voice, speaker == null ? -1 : speaker.getId(), "subtitle.viltrumitecore.speaker." + voice, speakerName(voice),
         "thanos_war." + key, List.of("subtitle.viltrumitecore.thanos_war." + key), priority, 0, at, VOICE_RANGE, force);
      if (ok) {
         this.said.put(key, now);
      }

      return ok;
   }

   static String speakerName(String voice) {
      return switch (voice) {
         case VOICE_MAW -> "Ebony Maw";
         case VOICE_CULL -> "Cull Obsidian";
         default -> "Thanos";
      };
   }

   // ---- client sync ----------------------------------------------------------------------------------
   int flags() {
      int f = 0;
      if (this.city.sirenFallback()) {
         f |= ThanosWarS2CPacket.ALARM;
      }

      if (!this.over && this.phase.atLeast(ThanosPhase.INVASION)) {
         f |= ThanosWarS2CPacket.SMOKE;
      }

      if (!this.over && this.phase == ThanosPhase.GAUNTLET) {
         f |= ThanosWarS2CPacket.GAUNTLET;
      }

      if (this.army.orderPresent()) {
         f |= ThanosWarS2CPacket.ORDER;
      }

      if (this.army.front()) {
         f |= ThanosWarS2CPacket.ARMY;
      }

      if (this.ship.bombarding()) {
         f |= ThanosWarS2CPacket.BOMBARD;
      }

      return f;
   }

   /** War state for the players around (the ship's path, the phase, the flags); NONE to those who left. */
   public void sync(boolean force) {
      if (this.ended) {
         return;
      }

      Vec3 s = this.ship.start();
      Vec3 h = this.ship.hover();
      ThanosWarS2CPacket packet = new ThanosWarS2CPacket(ThanosWarS2CPacket.STATE, this.phase.ordinal(), this.ship.state(), this.ship.stateTick(), s.x, s.y,
         s.z, h.x, h.y, h.z, this.flags(), this.army.alive(), this.ship.strikes());
      Set<UUID> now = new HashSet<>();
      Vec3 c = this.thanos.position();
      for (ServerPlayer p : this.level.players()) {
         double dx = p.getX() - c.x;
         double dz = p.getZ() - c.z;
         if (dx * dx + dz * dz <= SYNC_RANGE * SYNC_RANGE) {
            now.add(p.getUUID());
            CoreMessages.sendToPlayer(packet, p);
         }
      }

      for (UUID id : this.viewers) {
         if (!now.contains(id)) {
            ServerPlayer p = this.level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
               CoreMessages.sendToPlayer(ThanosWarS2CPacket.none(), p);
            }
         }
      }

      this.viewers.clear();
      this.viewers.addAll(now);
   }

   /** A player joined (or came back): he gets the current state at once. */
   public void onJoin(ServerPlayer player) {
      this.sync(true);
   }
}
