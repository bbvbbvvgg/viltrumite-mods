package dev.baranhan.viltrumitecore.hero.thor.combat;

import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvent;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorLifecycle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Session B's server hooks on the Forge bus: one combat tick per Thor
 * player (movement direction, delayed effects, chain grace, storm, ultimate
 * charge) and the lethal-hit check on the damage path. Idle until
 * {@link ThorCombatModule#install()} ran.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class ThorCombatRuntime {
   private ThorCombatRuntime() {
   }

   @SubscribeEvent
   public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
      if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || !ThorCombatModule.installed()) {
         return;
      }

      ThorState state = ThorState.of(player);
      if (state != null) {
         tick(player, state);
      }
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onLivingDamage(LivingDamageEvent event) {
      if (ThorCombatModule.installed()) {
         CombatHits.onLivingDamage(event);
      }
   }

   static void tick(ServerPlayer player, ThorState state) {
      ThorCombatState combat = ThorCombatState.of(state);
      combat.ticks++;
      Vec3 position = player.position();
      combat.velocity = combat.lastPosition == null ? Vec3.ZERO : position.subtract(combat.lastPosition);
      combat.lastPosition = position;
      runScheduled(combat, task -> runs(player.isAlive(), !player.hasDisconnected(),
         task.dimension() == null || task.dimension() == player.level().dimension(), ThorBridge.sceneLocked(state)));
      MeleeMoves.tick(player, state, combat);
      ElectricMoves.tickStorm(player, state, combat);
      WrathMove.tick(player, state, combat);
   }

   /**
    * Pure: may a due delayed effect still run? Its Thor is alive, online, in
    * the dimension it was scheduled in and not held by a scene; otherwise it
    * is dropped (never lands in another dimension at stale coordinates).
    */
   static boolean runs(boolean alive, boolean online, boolean sameDimension, boolean sceneLocked) {
      return alive && online && sameDimension && !sceneLocked;
   }

   /**
    * Lifecycle stop (ThorLifecycle listener of B): drop the transient combat
    * state; the body itself is forgotten unless only the dimension changed.
    */
   static void onLifecycleStop(@Nullable ServerPlayer player, ThorState state, ThorLifecycle.Cause cause) {
      ThorCombatState combat = ThorCombatState.peek(state);
      if (combat != null) {
         combat.cancelTransient();
      }

      if (cause != ThorLifecycle.Cause.DIMENSION_CHANGE) {
         ThorCombatState.forget(state);
      }
   }

   /** Runs the delayed effects that are due (all of them: pure tests). */
   static void runScheduled(ThorCombatState combat) {
      runScheduled(combat, task -> true);
   }

   /** Runs the due effects the guard allows, drops the due ones it refuses (pure over the state, the actions touch the world). */
   static void runScheduled(ThorCombatState combat, Predicate<ThorCombatState.Scheduled> guard) {
      if (combat.scheduled.isEmpty()) {
         return;
      }

      List<ThorCombatState.Scheduled> due = new ArrayList<>();
      combat.scheduled.removeIf(task -> {
         if (task.dueTick() <= combat.ticks) {
            due.add(task);
            return true;
         }

         return false;
      });
      for (ThorCombatState.Scheduled task : due) {
         if (guard.test(task)) {
            task.action().run();
         }
      }
   }

   /** CATCH (A): Mjölnir is back in the hand; an open unarmed chain finishes first (#166), the window stays open for it. */
   static void onCatch(ThorEvent event) {
      ServerPlayer player = ElectricCombos.player(event.attackerId());
      ThorState state = player == null ? null : ThorState.of(player);
      if (state == null) {
         return;
      }

      ThorCombatState combat = ThorCombatState.of(state);
      if (combat.chainMove == ThorMove.FIST_COMBO && !combat.chainLast) {
         ThorBridge.openComboWindow(state, combat.chainMove, ThorCombatTimings.COMBO_GRACE * 2);
      }
   }
}
