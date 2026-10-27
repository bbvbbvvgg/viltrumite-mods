package dev.baranhan.viltrumitecore.hero.thor.scene;

import dev.baranhan.viltrumitecore.hero.HeroChangeDeferral;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.combat.InterruptReason;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.thor.ThorActions;
import dev.baranhan.viltrumitecore.hero.thor.ThorFeedback;
import dev.baranhan.viltrumitecore.hero.thor.ThorFlags;
import dev.baranhan.viltrumitecore.hero.thor.ThorHands;
import dev.baranhan.viltrumitecore.hero.thor.ThorInput;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvent;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEventType;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvents;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveContext;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveDefinition;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveHandler;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves;
import dev.baranhan.viltrumitecore.hero.thor.scene.net.ThorSceneC2SPacket;
import dev.baranhan.viltrumitecore.hero.thor.scene.net.ThorSceneS2CPacket;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumiteflight.util.FlightState;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Session C entry point (cinematics: grabs, fatalities, the V teleport,
 * dashes): registers the C moves and bridge listeners once, routes Thor's
 * presses while a C scene holds the lock, and drives the server sessions.
 * No second MMB resolver: the grab starts only when {@code ThorControls}
 * resolved MMB to the grab move; this class only plays it. Server main
 * thread (the client-side reads go through the synced snapshot).
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class ThorScenes {
   /** Bridge owner id of session C (scene lock, camera lease, move and command registrations). */
   public static final String OWNER = "C";
   /** The grab scene's timeline move (chosen only by ThorControls' MMB resolver; never decided here). */
   public static final ThorMove GRAB = ThorMove.GRAB/* played, not resolved */;
   public static final ThorMove FATALITY = ThorMove.FATALITY;
   public static final ThorMove TELEPORT = ThorMove.TELEPORT_SELECT;
   /** Timeline length of every scene move: the session ends it (the timeline is only the lock-step and the wire id). */
   static final int SCENE_MOVE_TICKS = 1200;

   private static final Map<UUID, GrabWindowRule.Combo> COMBOS = new HashMap<>();
   private static boolean bootstrapped;

   private ThorScenes() {
   }

   /** Once, from common setup (after the hero registry). */
   public static synchronized void bootstrap() {
      if (bootstrapped) {
         return;
      }

      bootstrapped = true;
      ThorMoves.register(OWNER, GRAB, new ThorMoveDefinition(0, SCENE_MOVE_TICKS, 0, null, 1), new GrabHandler());
      ThorMoves.register(OWNER, FATALITY, new ThorMoveDefinition(0, SCENE_MOVE_TICKS, 0, null, 1), new FatalityHandler());
      ThorMoves.register(OWNER, TELEPORT, new ThorMoveDefinition(0, SCENE_MOVE_TICKS, 0, null, 1), new TeleportHandler());
      ThorEvents.subscribe(OWNER, ThorEventType.MELEE_HIT, ThorScenes::onMeleeHit);
      ThorEvents.subscribe(OWNER, ThorEventType.COMBO_END, ThorScenes::onComboEnd);
      ThorEvents.subscribe(OWNER, ThorEventType.LETHAL_HIT, ThorScenes::onLethalHit);
      ThorSceneCommands.register();
      HeroChangeDeferral.register(FatalitySessions::defer);
   }

   // --- move handlers ---

   private static final class GrabHandler implements ThorMoveHandler {
      @Override
      public void onStart(ThorMoveContext context) {
         ServerPlayer thor = context.player();
         if (thor == null || GrabSessions.of(thor.getUUID()) != null) {
            return;
         }

         int targetId = ThorBridge.grabTarget(context.state());
         Entity entity = targetId < 0 ? null : thor.level().getEntity(targetId);
         LivingEntity target = entity instanceof LivingEntity living ? living : null;
         if (target == null || !inReach(thor, target) || !GrabSessions.start(thor, context.state(), target)) {
            ThorBridge.closeGrabWindow(context.state());
            stopMove(context.state(), GRAB);
            ThorFeedback.refuse(thor, Component.translatable("hud.viltrumitecore.thor.grab.missed"));
         }
      }

      @Override
      public void onPhase(ThorMoveContext context, StagedTimeline.Event event) {
         // The timeline running out does not end a grab: only an escape or Thor's one action does (no hold time limit).
      }

      @Override
      public void onStop(ThorMoveContext context) {
         if (context.player() != null) {
            GrabSessions.endFor(context.player());
         }
      }
   }

   private static final class FatalityHandler implements ThorMoveHandler {
      @Override
      public void onPhase(ThorMoveContext context, StagedTimeline.Event event) {
         if (event == StagedTimeline.Event.END && context.player() != null) {
            FatalitySessions.abortFor(context.player().getUUID());
         }
      }

      @Override
      public void onStop(ThorMoveContext context) {
         if (context.player() != null) {
            FatalitySessions.abortFor(context.player().getUUID());
         }
      }
   }

   private static final class TeleportHandler implements ThorMoveHandler {
      @Override
      public void onStart(ThorMoveContext context) {
         ServerPlayer thor = context.player();
         if (thor != null && TeleportSessions.of(thor.getUUID()) == null && !TeleportSessions.start(thor, context.state())) {
            stopMove(context.state(), TELEPORT);
         }
      }

      @Override
      public void onPhase(ThorMoveContext context, StagedTimeline.Event event) {
         if (event == StagedTimeline.Event.END && context.player() != null) {
            TeleportSessions.endFor(context.player());
         }
      }

      @Override
      public void onStop(ThorMoveContext context) {
         if (context.player() != null) {
            TeleportSessions.endFor(context.player());
         }
      }
   }

   /** Thor's eye to the target's box within the grab reach. */
   static boolean inReach(ServerPlayer thor, LivingEntity target) {
      Vec3 eye = thor.getEyePosition();
      Vec3 nearest = new Vec3(
         Math.max(target.getBoundingBox().minX, Math.min(eye.x, target.getBoundingBox().maxX)),
         Math.max(target.getBoundingBox().minY, Math.min(eye.y, target.getBoundingBox().maxY)),
         Math.max(target.getBoundingBox().minZ, Math.min(eye.z, target.getBoundingBox().maxZ)));
      return eye.distanceTo(nearest) <= ThorSceneConfig.grabReach;
   }

   // --- bridge listeners ---

   private static void onMeleeHit(ThorEvent event) {
      ServerPlayer thor = player(event.attackerId());
      ThorState state = thor == null ? null : ThorState.of(thor);
      if (state == null || event.targetId() == null) {
         return;
      }

      Entity entity = thor.serverLevel().getEntity(event.targetId());
      LivingEntity target = entity instanceof LivingEntity living ? living : null;
      boolean valid = target != null && target.isAlive() && GrabSessions.refusal(thor, target) == null && !ThorBridge.sceneLocked(state);
      GrabWindowRule.Combo combo = COMBOS.computeIfAbsent(thor.getUUID(), id -> new GrabWindowRule.Combo());
      if (GrabWindowRule.onHit(combo, event.move(), thor.level().getGameTime(), thor.getRandom().nextFloat(), ThorSceneConfig.grabWindowChance,
         ThorSceneConfig.comboIdleTicks, valid)) {
         ThorBridge.openGrabWindow(state, ThorSceneConfig.grabWindowTicks, target.getId());
      }
   }

   private static void onComboEnd(ThorEvent event) {
      GrabWindowRule.Combo combo = COMBOS.get(event.attackerId());
      if (combo != null) {
         GrabWindowRule.onComboEnd(combo);
      }
   }

   /** Decisions 53, 66, 86, 93: only from a lethal hammer-in-hand hit of B's combat on a player (any hero); never from a key. */
   private static void onLethalHit(ThorEvent event) {
      ServerPlayer thor = player(event.attackerId());
      ThorState state = thor == null ? null : ThorState.of(thor);
      if (state == null || event.targetId() == null) {
         return;
      }

      ServerPlayer victim = player(event.targetId());
      FatalityRules.Verdict verdict = FatalityRules.decide(event.type(), event.hammerInHand(), victim != null, victim != null && victim.isAlive(),
         victim != null && inScene(victim.getUUID()), ThorBridge.sceneLocked(state) || inScene(thor.getUUID()));
      if (verdict != FatalityRules.Verdict.START || victim == thor || victim.level() != thor.level()) {
         return;
      }

      FatalityScene scene = FatalityRules.pick(FatalitySessions.last(thor.getUUID()), thor.getRandom().nextInt());
      FatalitySessions.start(thor, state, victim, scene, false);
   }

   // --- shared helpers of the C sessions ---

   /**
    * Start a scene move on the timeline at once (cancelling whatever runs;
    * its owner hears onStop) with a partner entity id.
    */
   static ThorActions.Refusal begin(ServerPlayer thor, ThorState state, ThorMove move, int partnerId) {
      ThorMove previous = state.timeline.move();
      if (previous != null && previous != move) {
         state.timeline.interrupt(InterruptReason.INTERACTION);
      }

      ThorActions.Refusal refusal = ThorActions.start(state, null, move, ThorHands.free(thor), partnerId);
      if (previous != null && previous != move) {
         ThorMoves.handler(previous).onStop(new ThorMoveContext(thor, state, previous, null));
      }

      return refusal == ThorActions.Refusal.NONE && !state.timeline.is(move) ? ThorActions.Refusal.BUSY : refusal;
   }

   static void send(ServerPlayer thor, @Nullable Entity victim, SceneSync sync) {
      ThorSceneS2CPacket packet = new ThorSceneS2CPacket(sync);
      CoreMessages.sendToTrackingAndSelf(packet, thor);
      if (victim instanceof ServerPlayer player && player != thor && !player.hasDisconnected()) {
         CoreMessages.sendToPlayer(new ThorSceneS2CPacket(sync), player);
      }
   }

   static void stopFlight(@Nullable Entity entity) {
      if (entity instanceof ViltrumiteFlightPlayer flight && flight.getFlightState() != FlightState.NONE) {
         flight.stopFlight();
      }
   }

   @Nullable
   static FlightState flightState(Player player) {
      return player instanceof ViltrumiteFlightPlayer flight ? flight.getFlightState() : null;
   }

   /** CRUISE / SONIC: no attacks, no teleport. */
   public static boolean fullFlight(Player player) {
      return ThorMovementRules.fullFlight(flightState(player));
   }

   static void stopMove(ThorState state, ThorMove move) {
      if (state.timeline.is(move)) {
         state.timeline.interrupt(InterruptReason.LIFECYCLE);
      }
   }

   /** Is this entity in any C scene (Thor or victim)? */
   public static boolean inScene(UUID entity) {
      return GrabSessions.of(entity) != null || GrabSessions.victimOf(entity) != null || FatalitySessions.participant(entity)
         || TeleportSessions.of(entity) != null;
   }

   /** Server: does a C scene hold this Thor? */
   public static boolean sceneLocked(ThorState state) {
      return OWNER.equals(state.sceneLock);
   }

   /**
    * Server, ThorHero.handleInput: a press while a C scene holds Thor. True =
    * consumed (the scene lock refuses every other Thor press anyway).
    */
   public static boolean routeInput(ServerPlayer thor, ThorState state, ThorInput input, boolean pressed) {
      if (!pressed || !sceneLocked(state)) {
         return false;
      }

      GrabSessions.Session grab = GrabSessions.of(thor.getUUID());
      if (grab != null && grab.flow.stage() == GrabFlow.Stage.HOLD) {
         GrabAction action = GrabAction.of(input);
         if (action != null) {
            GrabSessions.choose(thor, grab, action);
         }
      }
      // Teleport selection: LMB confirms and Escape cancels on the scene's own path (controls canon); V does nothing here.

      return true;
   }

   /** Both sides: a C scene owns Thor's mouse (grab choices, teleport confirm). */
   public static boolean claimsMouse(@Nullable Player player) {
      if (player == null) {
         return false;
      } else if (player.level().isClientSide) {
         HeroPublicSnapshot snapshot = player instanceof HeroPlayer heroPlayer ? heroPlayer.getHeroSnapshot() : null;
         return snapshot != null && snapshot.heroId() == HeroId.THOR && ThorFlags.scene(snapshot.heroFlags());
      }

      ThorState state = ThorState.of(player);
      return state != null && sceneLocked(state);
   }

   /** Server: the entity a C scene holds for this Thor, or -1. */
   public static int heldId(Player thor) {
      GrabSessions.Session grab = GrabSessions.of(thor.getUUID());
      if (grab != null && GrabSessions.holding(grab)) {
         return grab.victimId;
      }

      FatalitySessions.Session fatality = FatalitySessions.of(thor.getUUID());
      return fatality == null ? -1 : fatality.victimId;
   }

   @Nullable
   public static Vec3 heldPosition(Player thor, Entity target) {
      GrabSessions.Session grab = GrabSessions.of(thor.getUUID());
      if (grab != null && grab.victimId == target.getId() && thor instanceof ServerPlayer player && target instanceof LivingEntity living) {
         return GrabSessions.anchor(player, grab, living);
      }

      FatalitySessions.Session fatality = FatalitySessions.of(thor.getUUID());
      return fatality != null && fatality.victimId == target.getId() ? fatality.plan.victimAt(fatality.tick) : null;
   }

   /** Every C scene of this player ends (an aborted real fatality still kills its victim; a rehearsal does not). */
   static void endAll(ServerPlayer player) {
      GrabSessions.endFor(player);
      GrabSessions.endVictim(player.getUUID(), ReleaseReason.INTERRUPTED);
      FatalitySessions.abortFor(player.getUUID());
      TeleportSessions.endFor(player);
   }

   /** C2S from the scene client (validated here). */
   public static void onClientInput(ServerPlayer sender, ThorSceneC2SPacket.Op op, int arg, BlockPos pos) {
      switch (op) {
         case RESIST -> {
            ResistKey key = ResistKey.byId(arg);
            if (key != null) {
               GrabSessions.resist(sender, key);
            }
         }
         case TELEPORT_CONFIRM -> TeleportSessions.confirm(sender, pos);
         case TELEPORT_CANCEL -> TeleportSessions.cancel(sender);
         case DASH -> ThorDash.onDash(sender, arg);
      }
   }

   @Nullable
   private static ServerPlayer player(UUID id) {
      var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
      return server == null ? null : server.getPlayerList().getPlayer(id);
   }

   // --- Forge events ---

   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      GrabSessions.tick(event.getServer());
      FatalitySessions.tick(event.getServer());
      TeleportSessions.tick(event.getServer());
      ThrownBodies.tick(event.getServer());
   }

   /** A decided victim dies once, by the scene's own kill; any other death during the scene is held. */
   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onDeath(LivingDeathEvent event) {
      LivingEntity entity = event.getEntity();
      if (entity.level().isClientSide || FatalitySessions.executing(entity.getUUID())) {
         return;
      }

      if (FatalitySessions.victimOf(entity.getUUID()) != null || FatalitySessions.doomed(entity.getUUID())) {
         event.setCanceled(true);
         entity.setHealth(1.0F);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onAttack(LivingAttackEvent event) {
      LivingEntity entity = event.getEntity();
      if (entity.level().isClientSide || FatalitySessions.executing(entity.getUUID())) {
         return;
      }

      UUID id = entity.getUUID();
      boolean cancel = FatalitySessions.victimOf(id) != null
         || ThorSceneConfig.fatalityThorInvulnerable && FatalitySessions.of(id) != null
         || TeleportSessions.of(id) != null
         || GrabSessions.victimOf(id) != null && event.getSource().is(DamageTypes.IN_WALL);
      Entity source = event.getSource().getEntity();
      if (!cancel && source != null && source != entity) {
         // A held body or a fatality victim cannot hit anyone.
         cancel = GrabSessions.victimOf(source.getUUID()) != null && GrabSessions.holding(GrabSessions.victimOf(source.getUUID()))
            || FatalitySessions.victimOf(source.getUUID()) != null;
      }

      if (cancel) {
         event.setCanceled(true);
      }
   }

   /**
    * Server: a held body or a fatality participant uses nothing of its own
    * (decision 61: the victim only looks and resists; PULL alone would leave
    * items, pearls and block interaction open).
    */
   static boolean bound(@Nullable Entity entity) {
      if (entity == null || entity.level().isClientSide) {
         return false;
      }

      GrabSessions.Session held = GrabSessions.victimOf(entity.getUUID());
      return held != null && GrabSessions.holding(held) || FatalitySessions.participant(entity.getUUID());
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onAttackEntity(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
      if (bound(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onArrowLoose(net.minecraftforge.event.entity.player.ArrowLooseEvent event) {
      if (bound(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onUseItem(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Start event) {
      if (bound(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onRightClickItem(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
      if (bound(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
      if (bound(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onLeftClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
      if (bound(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onEntityInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
      if (bound(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onEntityInteractSpecific(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific event) {
      if (bound(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
      if (bound(event.getPlayer())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
      if (!(event.getEntity() instanceof ServerPlayer player)) {
         return;
      }

      UUID id = player.getUUID();
      // Leaving mid-fatality does not save the victim: the abort still executes a decided death.
      FatalitySessions.abortFor(id);
      GrabSessions.endFor(player);
      GrabSessions.endVictim(id, ReleaseReason.CASTER_DISCONNECT);
      TeleportSessions.endFor(player);
      ThorDash.forget(id);
      COMBOS.remove(id);
   }

   @SubscribeEvent
   public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         endAll(player);
      }
   }

   @SubscribeEvent
   public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         endAll(player);
      }
   }

   @SubscribeEvent
   public static void onServerStopped(ServerStoppedEvent event) {
      GrabSessions.clear();
      FatalitySessions.clear();
      TeleportSessions.clear();
      ThrownBodies.clear();
      COMBOS.clear();
   }
}
