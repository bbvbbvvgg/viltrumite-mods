package dev.baranhan.viltrumitecore.hero.thor.combat;

import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvent;
import java.util.ArrayList;
import java.util.List;
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
      runScheduled(combat);
      MeleeMoves.tick(player, state, combat);
      ElectricMoves.tickStorm(player, state, combat);
      WrathMove.tick(player, state, combat);
   }

   /** Runs the delayed effects that are due (pure over the state, the actions touch the world). */
   static void runScheduled(ThorCombatState combat) {
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
         task.action().run();
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
