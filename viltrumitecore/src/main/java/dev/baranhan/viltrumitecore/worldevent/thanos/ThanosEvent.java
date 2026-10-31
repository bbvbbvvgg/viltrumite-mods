package dev.baranhan.viltrumitecore.worldevent.thanos;

import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.entity.thanos.ThanosEntity;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.TimeFreeze;
import dev.baranhan.viltrumitecore.hero.control.WorldPause;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.ThanosStateS2CPacket;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * The Thanos city event ({@code /thanos start}): one Thanos with the six stones, no army. A
 * session lives in memory only (a restart ends it; Thanos is never saved). Stages: INTRO (he
 * steps out of a portal, untouchable) -> FIGHT (6 minutes of active time) -> VICTORY (the heroes
 * beat him: he kneels, the stones go out, the gauntlet crumbles) or, when the time runs out, the
 * SNAP: every player's world stops (a bounded {@link WorldPause} event session), the universe
 * dissolves, Od Lagna speaks and gives the world back - destruction included, nothing is reset -
 * and Thanos is gone. The event never restarts by itself.
 *
 * <p>Order of one server tick (END, after every entity tick, so all damage has landed): the
 * fight tick (skipped while another pause stops the world), the defeat check, the clock, the
 * phase, the outcome (defeat wins ties, see {@link ThanosOutcome}).
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class ThanosEvent {
   public enum Stage {
      INTRO,
      FIGHT,
      VICTORY,
      SNAP_WAIT,
      SNAP
   }

   public enum StartResult {
      STARTED,
      ALREADY,
      PAUSED,
      NO_SPACE
   }

   public enum EndKind {
      VICTORY,
      SNAP,
      ABANDONED,
      STOPPED,
      LOST,
      SERVER
   }

   /** No hero within reach for this long: Thanos leaves. */
   public static final int ABANDON_TICKS = 30 * ThanosRules.TPS;
   private static final int SYNC_EVERY = 10;
   private static final int SCENE_SYNC_EVERY = 5;
   private static final int MOB_FREEZE_RADIUS = 48;
   @Nullable
   private static ThanosEvent active;
   private final MinecraftServer server;
   private final ResourceKey<Level> dimension;
   private final ServerLevel level;
   private final ThanosEntity thanos;
   private final ThanosCombat combat;
   private final ThanosClock clock = new ThanosClock(ThanosRules.DURATION_TICKS);
   private final ServerBossEvent bar;
   private List<ServerPlayer> heroes = List.of();
   private Stage stage = Stage.INTRO;
   private int stageTick;
   private int fightTicks;
   private int counted = 1;
   private int lonely;
   private ThanosPhase phase = ThanosPhase.OPENING;
   @Nullable
   private UUID pauseId;
   private boolean ended;
   /** Where he snapped (his feet, body yaw): the scene is staged there, also after he is gone. */
   private Vec3 snapAt = Vec3.ZERO;
   private float snapYaw;

   private ThanosEvent(MinecraftServer server, ServerLevel level, ThanosEntity thanos) {
      this.server = server;
      this.level = level;
      this.dimension = level.dimension();
      this.thanos = thanos;
      this.combat = new ThanosCombat(this, level, thanos, level.random.nextLong());
      this.bar = new ServerBossEvent(Component.translatable("entity.viltrumitecore.thanos"), BossEvent.BossBarColor.PURPLE,
         BossEvent.BossBarOverlay.NOTCHED_10);
      this.bar.setDarkenScreen(true);
      this.bar.setPlayBossMusic(false);
      this.bar.setCreateWorldFog(false);
   }

   // ---- api ---------------------------------------------------------------------------------------
   @Nullable
   public static ThanosEvent active() {
      return active;
   }

   public static boolean running() {
      return active != null && !active.ended;
   }

   public static StartResult start(ServerPlayer starter) {
      if (running()) {
         return StartResult.ALREADY;
      }

      MinecraftServer server = starter.getServer();
      if (server == null) {
         return StartResult.NO_SPACE;
      }

      if (WorldPause.active(server)) {
         return StartResult.PAUSED;
      }

      ServerLevel level = starter.serverLevel();
      EntityType<ThanosEntity> type = ViltrumiteEntities.THANOS.get();
      Vec3 look = starter.getLookAngle();
      Vec3 flat = new Vec3(look.x, 0.0, look.z);
      flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
      Vec3 spot = ThanosPlacement.near(level, null, type.getDimensions(), starter.position().add(flat.scale(ThanosRules.SPAWN_DISTANCE)), 3.0, 8, 12);
      if (spot == null) {
         spot = ThanosPlacement.near(level, null, type.getDimensions(), starter.position().add(flat.scale(6.0)), 3.0, 8, 12);
      }

      ThanosEntity thanos = spot == null ? null : type.create(level);
      if (thanos == null) {
         return StartResult.NO_SPACE;
      }

      float yaw = (float)(Mth.atan2(starter.getZ() - spot.z, starter.getX() - spot.x) * Mth.RAD_TO_DEG) - 90.0F;
      thanos.moveTo(spot.x, spot.y, spot.z, yaw, 0.0F);
      thanos.setYHeadRot(yaw);
      thanos.setYBodyRot(yaw);
      thanos.setState(ThanosEntity.STATE_INTRO);
      if (!level.addFreshEntity(thanos)) {
         return StartResult.NO_SPACE;
      }

      ThanosEvent event = new ThanosEvent(server, level, thanos);
      active = event;
      event.begin();
      return StartResult.STARTED;
   }

   /** Ends the running event now (command, admin): Thanos leaves, a running scene stops. */
   public static boolean stop() {
      ThanosEvent e = active;
      if (e == null || e.ended) {
         return false;
      }

      e.end(EndKind.STOPPED);
      return true;
   }

   /** QA: leave {@code seconds} on the clock. */
   public static boolean skipTo(int seconds) {
      ThanosEvent e = active;
      if (e == null || e.ended || e.stage != Stage.FIGHT && e.stage != Stage.INTRO) {
         return false;
      }

      if (e.stage == Stage.INTRO) {
         e.stageTick = ThanosRules.INTRO_TICKS - 1;
      }

      e.clock.skipTo(Math.max(0, seconds) * ThanosRules.TPS);
      return true;
   }

   public Stage stage() {
      return this.stage;
   }

   public int stageTick() {
      return this.stageTick;
   }

   public ThanosClock clock() {
      return this.clock;
   }

   public ThanosPhase phase() {
      return this.phase;
   }

   public ThanosEntity thanos() {
      return this.thanos;
   }

   public ServerLevel level() {
      return this.level;
   }

   /** Heroes in the fight right now (survival/adventure players within reach of Thanos). */
   public List<ServerPlayer> heroes() {
      return this.heroes;
   }

   /** Heroes counted for his scaling (at least one). */
   public int heroCount() {
      return Math.max(1, Math.max(this.counted, this.heroes.size()));
   }

   public boolean isParticipant(ServerPlayer player) {
      return this.heroes.contains(player);
   }

   public int movesStarted() {
      return this.combat.movesStarted();
   }

   // ---- lifecycle ----------------------------------------------------------------------------------
   private void begin() {
      this.heroes = this.findHeroes();
      this.counted = Math.max(1, this.heroes.size());
      this.thanos.scaleMaxHealth(ThanosRules.maxHealth(this.counted));
      this.thanos.setHealth(this.thanos.getMaxHealth());
      this.syncBar();
      ThanosTargets.fx(this.level, ThanosFxS2CPacket.ARRIVAL, this.thanos.getId(), -1, this.thanos.position(), this.thanos.getLookAngle(), 1.0F,
         ThanosRules.INTRO_TICKS);
      ThanosSounds.play(this.level, this.thanos.position(), ThanosSounds.ARRIVAL, 3.0F, 1.0F, 160.0);
      this.broadcast(Component.translatable("event.viltrumitecore.thanos.arrived").withStyle(ChatFormatting.DARK_PURPLE));
      this.sync(true);
   }

   private void tick() {
      if (this.ended) {
         return;
      }

      boolean gone = this.thanos.isRemoved() || this.thanos.level() != this.level;
      if (gone && !(this.stage == Stage.SNAP && this.stageTick >= ThanosSceneTimeline.THANOS_GONE)) {
         this.end(EndKind.LOST);
         return;
      }

      this.stageTick++;
      this.thanos.damageCap().advanceTo(this.server.getTickCount());
      this.heroes = this.findHeroes();
      switch (this.stage) {
         case INTRO -> this.tickIntro();
         case FIGHT -> this.tickFight();
         case VICTORY -> this.tickVictory();
         case SNAP_WAIT -> this.tickSnapWait();
         case SNAP -> this.tickSnap();
      }

      if (!this.ended) {
         this.sync(false);
      }
   }

   private List<ServerPlayer> findHeroes() {
      if (this.thanos.isRemoved()) {
         return List.of();
      }

      List<ServerPlayer> out = new ArrayList<>();
      double r2 = ThanosRules.PARTICIPANT_RADIUS * ThanosRules.PARTICIPANT_RADIUS;
      for (ServerPlayer p : this.level.players()) {
         if (ThanosTargets.hero(p) && p.distanceToSqr(this.thanos) <= r2) {
            out.add(p);
         }
      }

      return List.copyOf(out);
   }

   private boolean lonely() {
      if (!this.heroes.isEmpty()) {
         this.lonely = 0;
         return false;
      }

      if (++this.lonely >= ABANDON_TICKS) {
         this.end(EndKind.ABANDONED);
         return true;
      }

      return false;
   }

   private void tickIntro() {
      this.thanos.getNavigation().stop();
      if (this.lonely()) {
         return;
      }

      if (!this.heroes.isEmpty()) {
         ServerPlayer nearest = this.heroes.get(0);
         for (ServerPlayer p : this.heroes) {
            if (p.distanceToSqr(this.thanos) < nearest.distanceToSqr(this.thanos)) {
               nearest = p;
            }
         }

         this.thanos.faceTowards(nearest.position(), 6.0F);
      }

      if (this.stageTick >= ThanosRules.INTRO_TICKS) {
         this.stage = Stage.FIGHT;
         this.stageTick = 0;
         this.thanos.setState(ThanosEntity.STATE_FIGHT);
         this.rescale();
         this.syncBar();
         this.sync(true);
      }
   }

   private void tickFight() {
      this.fightTicks++;
      if (this.stageTick % 20 == 0) {
         if (this.fightTicks <= ThanosRules.RESCALE_WINDOW_TICKS) {
            this.rescale();
         }

         this.syncBar();
      }

      if (this.lonely()) {
         return;
      }

      boolean paused = WorldPause.active(this.server);
      // another system stopped time around him (DIO's field, a world stop): he waits, and so does his clock
      boolean timeStopped = !paused && (TimeFreeze.isFrozen(this.thanos) || TimeFreeze.worldStopped(this.level));
      if (!paused) {
         boolean held = timeStopped || ControlManager.get(this.level).isControlled(this.thanos);
         this.combat.tick(held, timeStopped);
      }

      boolean defeated = this.thanos.defeated() || this.thanos.getHealth() <= 0.0F;
      this.clock.advance(ThanosClock.holds(paused, timeStopped));
      float max = Math.max(1.0F, this.thanos.getMaxHealth());
      float fraction = Mth.clamp(this.thanos.getHealth() / max, 0.0F, 1.0F);
      ThanosPhase next = ThanosPhase.of(fraction, this.clock.elapsed(), this.clock.duration());
      if (next.ordinal() > this.phase.ordinal()) {
         this.phase = next;
         this.thanos.setPhase(next);
         this.announce(next);
      }

      this.bar.setProgress(fraction);
      switch (ThanosOutcome.resolve(defeated, this.clock.expired())) {
         case VICTORY -> this.beginVictory();
         case SNAP -> this.beginSnap();
         default -> {
         }
      }
   }

   private void announce(ThanosPhase next) {
      String key = switch (next) {
         case ESCALATION -> "event.viltrumitecore.thanos.phase.escalation";
         case ONSLAUGHT -> "event.viltrumitecore.thanos.phase.onslaught";
         case FINAL -> "event.viltrumitecore.thanos.phase.final";
         default -> null;
      };
      if (key != null) {
         this.broadcast(Component.translatable(key).withStyle(next == ThanosPhase.FINAL ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE));
      }

      if (next == ThanosPhase.FINAL) {
         ThanosSounds.play(this.level, this.thanos.position(), ThanosSounds.STONES_FLARE, 3.0F, 0.8F, 160.0);
      }
   }

   /** More heroes joined early: more health, same fraction. */
   private void rescale() {
      int now = Math.max(1, this.heroes.size());
      if (now > this.counted) {
         this.counted = Math.min(ThanosRules.MAX_COUNTED_HEROES, now);
         this.thanos.scaleMaxHealth(ThanosRules.maxHealth(this.counted));
      }
   }

   private void beginVictory() {
      this.clock.stop();
      this.combat.clearAll();
      // however the health reached 0 (a capped hit, a direct setHealth, /kill), no vanilla death runs:
      // the defeat scene plays on a living body and the event removes him at its end
      this.thanos.holdForDefeat();
      this.stage = Stage.VICTORY;
      this.stageTick = 0;
      this.thanos.setState(ThanosEntity.STATE_VICTORY);
      this.thanos.setDeltaMovement(Vec3.ZERO);
      this.bar.setProgress(0.0F);
      ThanosTargets.fx(this.level, ThanosFxS2CPacket.CLEAR, this.thanos.getId(), -1, this.thanos.position(), Vec3.ZERO, 0.0F, 0);
      ThanosSounds.play(this.level, this.thanos.position(), ThanosSounds.VICTORY, 3.0F, 1.0F, 160.0);
      this.broadcast(Component.translatable("event.viltrumitecore.thanos.victory").withStyle(ChatFormatting.GOLD));
      this.sync(true);
   }

   private void tickVictory() {
      this.thanos.getNavigation().stop();
      if (this.stageTick >= ThanosSceneTimeline.VICTORY_LENGTH) {
         this.end(EndKind.VICTORY);
      }
   }

   private void beginSnap() {
      this.clock.stop();
      this.combat.clearAll();
      this.snapAt = this.thanos.position();
      this.snapYaw = this.thanos.yBodyRot;
      this.stage = Stage.SNAP_WAIT;
      this.stageTick = 0;
      this.thanos.setState(ThanosEntity.STATE_SNAP);
      this.thanos.setDeltaMovement(Vec3.ZERO);
      ThanosTargets.fx(this.level, ThanosFxS2CPacket.CLEAR, this.thanos.getId(), -1, this.thanos.position(), Vec3.ZERO, 0.0F, 0);
      this.broadcast(Component.translatable("event.viltrumitecore.thanos.time_up").withStyle(ChatFormatting.DARK_RED));
      this.sync(true);
      this.tickSnapWait();
   }

   /** The snap waits for another running pause (a hero's own scene) to end first. */
   private void tickSnapWait() {
      this.thanos.getNavigation().stop();
      if (WorldPause.active(this.server) || !WorldPause.beginEvent(this.server, this.dimension, ThanosSceneTimeline.PAUSE_TICKS, true, MOB_FREEZE_RADIUS)) {
         return;
      }

      this.pauseId = WorldPause.id(this.server);
      this.stage = Stage.SNAP;
      this.stageTick = 0;
      this.bar.setVisible(false);
      WorldPause.listen(this.server, new WorldPause.Listener() {
         @Override
         public void tick(int elapsed) {
         }

         @Override
         public void ended(WorldPause.EndReason reason) {
            ThanosEvent.this.pauseEnded();
         }

         @Override
         public void onJoin(ServerPlayer player, int elapsed) {
            ThanosEvent.this.send(player);
         }
      });
      this.sync(true);
   }

   private void tickSnap() {
      if (this.stageTick == ThanosSceneTimeline.THANOS_GONE && !this.thanos.isRemoved()) {
         this.combat.clearAll();
         this.thanos.discard();
      }

      if (this.stageTick >= ThanosSceneTimeline.LENGTH) {
         this.end(EndKind.SNAP);
      }
   }

   private void pauseEnded() {
      this.pauseId = null;
      if (!this.ended) {
         this.end(EndKind.SNAP);
      }
   }

   private void end(EndKind kind) {
      if (this.ended) {
         return;
      }

      this.ended = true;
      if (active == this) {
         active = null;
      }

      try {
         this.combat.clearAll();
      } finally {
         if (!this.thanos.isRemoved()) {
            this.thanos.discard();
         }

         this.bar.removeAllPlayers();
         this.bar.setVisible(false);
         UUID pause = this.pauseId;
         this.pauseId = null;
         if (pause != null && pause.equals(WorldPause.id(this.server))) {
            WorldPause.end(this.server, WorldPause.EndReason.DONE);
         }

         for (ServerPlayer p : this.server.getPlayerList().getPlayers()) {
            CoreMessages.sendToPlayer(ThanosStateS2CPacket.none(), p);
         }
      }

      String key = switch (kind) {
         case VICTORY -> "event.viltrumitecore.thanos.gone_victory";
         case SNAP -> "event.viltrumitecore.thanos.restored";
         case ABANDONED -> "event.viltrumitecore.thanos.abandoned";
         case STOPPED -> "event.viltrumitecore.thanos.stopped";
         case LOST -> "event.viltrumitecore.thanos.lost";
         case SERVER -> null;
      };
      if (key != null) {
         Component message = Component.translatable(key).withStyle(kind == EndKind.SNAP || kind == EndKind.VICTORY ? ChatFormatting.GOLD : ChatFormatting.GRAY);
         if (kind == EndKind.SNAP) {
            for (ServerPlayer p : this.server.getPlayerList().getPlayers()) {
               p.sendSystemMessage(message);
            }
         } else {
            this.broadcast(message);
         }
      }
   }

   // ---- sync ----------------------------------------------------------------------------------------
   private void syncBar() {
      if (this.stage == Stage.SNAP || this.stage == Stage.SNAP_WAIT || this.ended) {
         return;
      }

      for (ServerPlayer p : List.copyOf(this.bar.getPlayers())) {
         if (!this.sees(p)) {
            this.bar.removePlayer(p);
         }
      }

      for (ServerPlayer p : this.level.players()) {
         if (this.sees(p)) {
            this.bar.addPlayer(p);
         }
      }

      float max = Math.max(1.0F, this.thanos.getMaxHealth());
      this.bar.setProgress(Mth.clamp(this.thanos.getHealth() / max, 0.0F, 1.0F));
      this.bar.setVisible(this.stage == Stage.FIGHT || this.stage == Stage.VICTORY);
   }

   /** Close enough to see the fight (heroes and onlookers, creative included). */
   private boolean sees(ServerPlayer p) {
      return p.level() == this.level && !p.isRemoved() && p.distanceToSqr(this.thanos) <= ThanosTargets.FX_RANGE * ThanosTargets.FX_RANGE;
   }

   private int stageId() {
      return switch (this.stage) {
         case INTRO -> ThanosStateS2CPacket.INTRO;
         case FIGHT -> ThanosStateS2CPacket.FIGHT;
         case VICTORY -> ThanosStateS2CPacket.VICTORY;
         case SNAP_WAIT -> ThanosStateS2CPacket.SNAP_WAIT;
         case SNAP -> ThanosStateS2CPacket.SNAP;
      };
   }

   private boolean scene() {
      return this.stage == Stage.SNAP || this.stage == Stage.SNAP_WAIT;
   }

   private void sync(boolean now) {
      int every = this.scene() ? SCENE_SYNC_EVERY : SYNC_EVERY;
      if (!now && this.stageTick % every != 0) {
         return;
      }

      if (this.scene()) {
         for (ServerPlayer p : this.server.getPlayerList().getPlayers()) {
            this.send(p);
         }
      } else {
         for (ServerPlayer p : this.level.players()) {
            if (this.sees(p)) {
               this.send(p);
            }
         }
      }
   }

   private void send(ServerPlayer p) {
      if (this.ended) {
         return;
      }

      boolean here = p.level() == this.level;
      boolean scene = this.scene();
      Vec3 at = scene || this.thanos.isRemoved() ? this.snapAt : this.thanos.position();
      float yaw = scene || this.thanos.isRemoved() ? this.snapYaw : this.thanos.yBodyRot;
      CoreMessages.sendToPlayer(new ThanosStateS2CPacket(this.stageId(), this.thanos.isRemoved() ? -1 : this.thanos.getId(), this.clock.remaining(),
         this.clock.duration(), this.phase.ordinal(), this.stageTick, here && this.sees(p), at.x, at.y, at.z, yaw, here), p);
   }

   private void broadcast(Component message) {
      for (ServerPlayer p : this.level.players()) {
         if (p.distanceToSqr(this.thanos) <= 4.0 * ThanosRules.PARTICIPANT_RADIUS * ThanosRules.PARTICIPANT_RADIUS) {
            p.sendSystemMessage(message);
         }
      }
   }

   // ---- forge -------------------------------------------------------------------------------------
   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase == TickEvent.Phase.END && active != null) {
         active.tick();
      }
   }

   @SubscribeEvent
   public static void onStopping(ServerStoppingEvent event) {
      if (active != null) {
         active.end(EndKind.SERVER);
      }

      active = null;
   }

   @SubscribeEvent
   public static void onLevelUnload(LevelEvent.Unload event) {
      if (active != null && event.getLevel() == active.level) {
         active.end(EndKind.LOST);
      }
   }

   @SubscribeEvent
   public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
      if (active != null && !active.ended && event.getEntity() instanceof ServerPlayer p && (active.scene() || active.sees(p))) {
         active.send(p);
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
      if (active != null && event.getEntity() instanceof ServerPlayer p) {
         active.bar.removePlayer(p);
      }
   }
}