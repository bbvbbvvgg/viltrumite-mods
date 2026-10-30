package dev.baranhan.viltrumitecore.hero.thor.hammer;

import dev.baranhan.viltrumitecore.hero.thor.ThorSounds;
import dev.baranhan.viltrumitecore.hero.thor.ThorCue;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.thor.ThorFeedback;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveContext;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveDefinition;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveHandler;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves;
import dev.baranhan.viltrumitecore.hero.thor.dev.ThorDevCommands;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Session A registrations on the shared bridge: the moves it owns
 * (HAMMER_THROW, RECALL, HAMMER_PULL), the world-hammer HammerAccess and the
 * {@code /thor debug hammer ...} dev commands. Called once from the mod
 * constructor (both sides: the client mouse claim reads the authored moves).
 */
public final class MjolnirModule {
   /** PLACEHOLDER timings of the A moves (MjolnirTuning); RECALL never takes the timeline (ThorActions.overlay). */
   public static final Map<ThorMove, ThorMoveDefinition> DEFINITIONS = definitions();
   private static boolean bootstrapped;

   private MjolnirModule() {
   }

   private static Map<ThorMove, ThorMoveDefinition> definitions() {
      Map<ThorMove, ThorMoveDefinition> out = new EnumMap<>(ThorMove.class);
      // Wind-up, hold (charging, ends on the release), follow-through; any press may cut the follow-through.
      out.put(ThorMove.HAMMER_THROW, new ThorMoveDefinition(MjolnirTuning.THROW_WINDUP, MjolnirTuning.THROW_HOLD_MAX, MjolnirTuning.THROW_FOLLOW,
         MovePhase.FINISH, 1));
      out.put(ThorMove.RECALL, new ThorMoveDefinition(0, 1, 0, MovePhase.INTRO, 1));
      // Bend and grip, heave (the hammer is in the hand from its first tick), settle; a press may cut the settle.
      out.put(ThorMove.PICKUP, new ThorMoveDefinition(MjolnirTuning.PICKUP_REACH, MjolnirTuning.PICKUP_HEAVE, MjolnirTuning.PICKUP_SETTLE,
         MovePhase.FINISH, 1));
      out.put(ThorMove.HAMMER_PULL, new ThorMoveDefinition(2, MjolnirTuning.PULL_MAX_TICKS, 6, MovePhase.MAIN, 1));
      return out;
   }

   public static synchronized void bootstrap() {
      if (bootstrapped) {
         return;
      }

      bootstrapped = true;
      registerMoves();
      ThorBridge.installHammerAccess(MjolnirWorld.OWNER, MjolnirWorld::view);
      registerCommands();
   }

   /** The A moves on the shared registry (tests call it between ThorMoves resets). */
   public static void registerMoves() {
      ThorMoves.register(MjolnirWorld.OWNER, ThorMove.HAMMER_THROW, DEFINITIONS.get(ThorMove.HAMMER_THROW), new Throw());
      ThorMoves.register(MjolnirWorld.OWNER, ThorMove.RECALL, DEFINITIONS.get(ThorMove.RECALL), new Recall());
      ThorMoves.register(MjolnirWorld.OWNER, ThorMove.HAMMER_PULL, DEFINITIONS.get(ThorMove.HAMMER_PULL), new Pull());
      ThorMoves.register(MjolnirWorld.OWNER, ThorMove.PICKUP, DEFINITIONS.get(ThorMove.PICKUP), new Pickup());
   }

   /** Pure: a release after this many held ticks makes the throw charged (№98, PLACEHOLDER threshold). */
   public static boolean charged(int heldTicks) {
      return heldTicks >= MjolnirTuning.CHARGE_TICKS;
   }

   /**
    * MMB in the throw context: the wind-up starts on the press, the hammer
    * leaves on the release (charged when held long enough) or at the end of
    * the hold (always charged). A release during the wind-up throws as soon
    * as the wind-up ends.
    */
   /**
    * RMB on the resting hammer (owner playtest: it must not jump into the hand). The click only starts the bend;
    * the hammer is taken on the contact tick if it still rests within reach, else the move ends with nothing taken.
    */
   static final class Pickup implements ThorMoveHandler {
      @Override
      public void onStart(ThorMoveContext context) {
         if (!MjolnirWorld.canPickUp(context.player())) {
            ThorBridge.end(context.player(), context.state(), ThorMove.PICKUP);
         } else {
            MjolnirWorld.stepToHammer(context.player());
         }
      }

      @Override
      public void onPhase(ThorMoveContext context, StagedTimeline.Event event) {
         if (event == StagedTimeline.Event.ENTER_MAIN && !MjolnirWorld.pickUpResting(context.player())) {
            ThorBridge.end(context.player(), context.state(), ThorMove.PICKUP);
         }
      }
   }

   static final class Throw implements ThorMoveHandler {
      private final Map<UUID, Boolean> early = new HashMap<>();
      private final Set<UUID> thrown = new HashSet<>();

      @Override
      public void onStart(ThorMoveContext context) {
         UUID id = context.player().getUUID();
         this.early.remove(id);
         this.thrown.remove(id);
         MjolnirRecord record = MjolnirWorld.record(context.player().server);
         if (!context.player().getUUID().equals(record.activeOwner) || record.phase != MjolnirPhase.IN_HAND) {
            ThorBridge.end(context.player(), context.state(), ThorMove.HAMMER_THROW);
            ThorFeedback.refuse(context.player(), Component.translatable(MjolnirWorld.NO_HAMMER));
         } else {
            ThorSounds.play(context.player(), ThorCue.THROW_WINDUP);
         }
      }

      @Override
      public void onRelease(ThorMoveContext context, int heldTicks) {
         ThorState state = context.state();
         UUID id = context.player().getUUID();
         if (!state.timeline.is(ThorMove.HAMMER_THROW) || this.thrown.contains(id)) {
            return;
         }

         if (state.timeline.phase() == MovePhase.INTRO) {
            this.early.put(id, charged(heldTicks));
         } else if (state.timeline.phase() == MovePhase.MAIN) {
            this.fire(context, charged(heldTicks));
         }
      }

      @Override
      public void onPhase(ThorMoveContext context, StagedTimeline.Event event) {
         UUID id = context.player().getUUID();
         if (event == StagedTimeline.Event.ENTER_MAIN && this.early.containsKey(id)) {
            this.fire(context, this.early.remove(id));
         } else if (event == StagedTimeline.Event.ENTER_FINISH && !this.thrown.contains(id)) {
            // Held to the end of the hold: the full charge.
            this.fire(context, true);
         } else if (event == StagedTimeline.Event.END) {
            this.forget(id);
         }
      }

      @Override
      public void onStop(ThorMoveContext context) {
         this.forget(context.player().getUUID());
      }

      private void fire(ThorMoveContext context, boolean charged) {
         ServerPlayer player = context.player();
         this.thrown.add(player.getUUID());
         this.early.remove(player.getUUID());
         if (!MjolnirWorld.launch(player, context.state(), charged)) {
            ThorBridge.end(player, context.state(), ThorMove.HAMMER_THROW);
            return;
         }

         context.state().timeline.skipTo(MovePhase.FINISH);
      }

      private void forget(UUID id) {
         this.early.remove(id);
         this.thrown.remove(id);
      }
   }

   /** MMB with the hammer out of the hand: an overlay, the current strike goes on (§5.1). */
   static final class Recall implements ThorMoveHandler {
      @Override
      public void onStart(ThorMoveContext context) {
         ServerPlayer player = context.player();
         switch (MjolnirWorld.recall(player)) {
            case PENDING -> player.displayClientMessage(Component.translatable(MjolnirWorld.RECALL_WAIT), true);
            case NOT_OWNER -> ThorFeedback.refuse(player, Component.translatable(MjolnirWorld.NOT_OWNER));
            case NO_HAMMER -> ThorFeedback.refuse(player, Component.translatable(MjolnirWorld.NO_HAMMER));
            case STARTED -> ThorSounds.play(player, ThorCue.RECALL);
            default -> {
            }
         }
      }
   }

   /** Y: pulled to the flying hammer (№220). */
   static final class Pull implements ThorMoveHandler {
      @Override
      public void onStart(ThorMoveContext context) {
         if (!MjolnirPull.start(context.player())) {
            ThorBridge.end(context.player(), context.state(), ThorMove.HAMMER_PULL);
         }
      }

      @Override
      public void onPhase(ThorMoveContext context, StagedTimeline.Event event) {
         if (event == StagedTimeline.Event.ENTER_FINISH || event == StagedTimeline.Event.END) {
            MjolnirPull.stop(context.player());
         }
      }

      @Override
      public void onStop(ThorMoveContext context) {
         MjolnirPull.stop(context.player());
      }
   }

   // --- /thor debug hammer ... (dev / test only, shared registry) ---

   private static void registerCommands() {
      ThorDevCommands.registerChild(MjolnirWorld.OWNER, "debug hammer throw", node -> node.executes(c -> throwCommand(c, false))
         .then(Commands.literal("charged").executes(c -> throwCommand(c, true))));
      ThorDevCommands.registerChild(MjolnirWorld.OWNER, "debug hammer recall", node -> node.executes(MjolnirModule::recallCommand));
      ThorDevCommands.registerChild(MjolnirWorld.OWNER, "debug hammer state", node -> node.executes(MjolnirModule::stateCommand));
      ThorDevCommands.registerChild(MjolnirWorld.OWNER, "debug hammer spawn", node -> node.executes(MjolnirModule::spawnCommand)
         .then(Commands.literal("here").executes(MjolnirModule::spawnHereCommand)));
   }

   private static int throwCommand(CommandContext<CommandSourceStack> context, boolean charged) throws CommandSyntaxException {
      ServerPlayer player = context.getSource().getPlayerOrException();
      ThorState state = ThorState.of(player);
      if (state == null || !MjolnirWorld.launch(player, state, charged)) {
         context.getSource().sendFailure(Component.literal("Mjolnir: no throw (Thor with the hammer in the hand needed), phase "
            + MjolnirWorld.record(player.server).phase().key()));
         return 0;
      }

      // A-N2: a dev fixture: no move timeline, no scene lock and no free-hand check (the store's own owner / phase checks still hold).
      context.getSource().sendSuccess(() -> Component.literal("Mjolnir: thrown" + (charged ? " (charged)" : "") + " (dev: bypasses the move gate)"), false);
      return 1;
   }

   private static int recallCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = context.getSource().getPlayerOrException();
      MjolnirWorld.Recall result = MjolnirWorld.recall(player);
      context.getSource().sendSuccess(() -> Component.literal("Mjolnir: recall " + result.name().toLowerCase(java.util.Locale.ROOT)), false);
      return result == MjolnirWorld.Recall.STARTED || result == MjolnirWorld.Recall.PENDING ? 1 : 0;
   }

   private static int stateCommand(CommandContext<CommandSourceStack> context) {
      MjolnirRecord record = MjolnirWorld.snapshot(context.getSource().getServer());
      String text = "Mjolnir: id=" + record.logicalId() + " phase=" + record.phase().key() + " dim=" + record.dimension()
         + String.format(java.util.Locale.ROOT, " pos=%.1f %.1f %.1f", record.x(), record.y(), record.z())
         + " owner=" + record.activeOwner() + " last=" + record.lastOwner() + " fallArmed=" + record.fallArmedAt() + " fallDone=" + record.fallDone()
         + " fallCancelled=" + record.fallCancelled() + " embedded=" + record.embedded() + " lying=" + record.lying() + " charged=" + record.charged()
         + " recallPending=" + record.recallPending;
      context.getSource().sendSuccess(() -> Component.literal(text), false);
      return 1;
   }

   private static int spawnCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = context.getSource().getPlayerOrException();
      if (!MjolnirWorld.forceFirstFall(player.server, player)) {
         context.getSource().sendFailure(Component.literal("Mjolnir: no first fall (already exists / cancelled / no open site near you)"));
         return 0;
      }

      context.getSource().sendSuccess(() -> Component.literal("Mjolnir: first fall started"), false);
      return 1;
   }

   private static int spawnHereCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
      ServerPlayer player = context.getSource().getPlayerOrException();
      Vec3 at = player.position().add(player.getLookAngle().multiply(2.0, 0.0, 2.0)).add(0.0, 1.0, 0.0);
      MjolnirWorld.place(player, at);
      context.getSource().sendSuccess(() -> Component.literal("Mjolnir: placed (same hammer)"), false);
      return 1;
   }
}
