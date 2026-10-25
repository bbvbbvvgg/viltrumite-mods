package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.client.hero.SuperJumpClient;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardActions;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardMode;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.DashBehind;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.ReinhardDashBehindC2SPacket;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

/**
 * Dash Behind prediction (stage 3 Task 8) on the merged dash key: a Knight's
 * {@code reinhard:step_dash} press with a living entity under the crosshair is
 * taken over from the generic slot loop ({@link #intercept}). The client finds
 * the safe spot behind the target with its own level and, when the mirrored
 * gate passes and the local cooldown is 0, moves the player there turned to the
 * target. Each such press sends exactly one {@link ReinhardDashBehindC2SPacket}
 * instead of the generic press; the server answers it with its cooldown
 * ({@code ReinhardDashBehindAnswerS2CPacket}), which replaces the local one, so
 * a refused prediction (snap-back, no cooldown) does not block the next. Every
 * other press stays a Step Dash ({@link StepDashClient}), decided once per tick
 * by {@link #behindTarget}. Client only; {@link Edge}, {@link Route} and
 * {@link Decision} are pure.
 */
public final class DashBehindClient {
   private static final Edge EDGE = new Edge();
   private static final Route ROUTE = new Route();
   private static final Decision<LivingEntity> DECISION = new Decision<>();

   private DashBehindClient() {
   }

   /** Registered on the Forge bus by {@link ReinhardClient}: the local cooldown runs down. */
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null || ReinhardView.of(player) == null) {
         EDGE.reset();
         ROUTE.reset();
         DECISION.reset();
         return;
      }

      EDGE.tick();
   }

   /**
    * This tick's one decision for the merged dash key: the Knight's Dash Behind
    * target, or null for a Step Dash. {@link StepDashClient} and {@link #intercept}
    * read the same answer within a tick.
    */
   @Nullable
   static LivingEntity behindTarget(LocalPlayer player, HeroPublicSnapshot snapshot) {
      if (ReinhardView.mode(snapshot) != ReinhardMode.KNIGHT) {
         return null;
      }

      return DECISION.get(player, player.tickCount, () -> DashBehind.lookTarget(player));
   }

   /** Registered with {@code HeroClientInput}: claims the merged key's Knight press with a target (and its release). */
   public static boolean intercept(LocalPlayer player, HeroAction action, boolean down) {
      if (action != ReinhardAbility.STEP_DASH.action()) {
         return false;
      }

      HeroPublicSnapshot snapshot = ReinhardView.of(player);
      LivingEntity target = down && snapshot != null ? behindTarget(player, snapshot) : null;
      if (!ROUTE.claim(down, target != null)) {
         return false;
      }

      if (down) {
         ReinhardAbility dash = ReinhardAbility.DASH_BEHIND;
         boolean gate = ReinhardActions.gate(new ReinhardActions.Gate(player.isAlive() && !player.isSpectator(), SuperJumpClient.anchored(player, snapshot),
            ReinhardView.mode(snapshot), dash.mode(), snapshot.extraCooldown(dash.snapshotIndex()), snapshot.actionBusy(), false)) == ReinhardActions.Refusal.NONE;
         press(player, target, gate);
      }

      return true;
   }

   private static void press(LocalPlayer player, LivingEntity target, boolean gate) {
      Vec3 wanted = DashBehind.wanted(target.position(), target.getYRot());
      Optional<Vec3> spot = DashBehind.find(player.level(), player, target);
      if (spot.isPresent() && EDGE.predict(gate)) {
         Vec3 to = spot.get();
         Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
         float yaw = DashBehind.yawTowards(to, aim);
         player.moveTo(to.x, to.y, to.z, yaw, DashBehind.pitchTowards(to.add(0.0, player.getEyeHeight(), 0.0), aim));
         player.setYHeadRot(yaw);
         player.setDeltaMovement(Vec3.ZERO);
      }

      CoreMessages.sendToServer(new ReinhardDashBehindC2SPacket(target.getId(), spot.orElse(wanted)));
   }

   /** The server's answer to a press: its Dash Behind cooldown in ticks (0 = refused). */
   public static void onAnswer(int serverCooldown) {
      EDGE.onAnswer(serverCooldown);
   }

   /** Pure routing of the merged key: one path per press, and a release follows its own press. */
   public static final class Route {
      private boolean ownsPress;

      /** true = this edge is Dash Behind's (no generic press/release is sent for it). */
      public boolean claim(boolean down, boolean behindTarget) {
         if (!down) {
            boolean owned = this.ownsPress;
            this.ownsPress = false;
            return owned;
         }

         this.ownsPress = behindTarget;
         return behindTarget;
      }

      void reset() {
         this.ownsPress = false;
      }
   }

   /** Pure per-tick memo: the merged key resolves its variant once per client tick and player. */
   public static final class Decision<T> {
      private Object owner;
      private int tick;
      private T value;

      public T get(Object owner, int tick, Supplier<T> resolve) {
         if (owner != this.owner || tick != this.tick) {
            this.owner = owner;
            this.tick = tick;
            this.value = resolve.get();
         }

         return this.value;
      }

      void reset() {
         this.owner = null;
         this.value = null;
      }
   }

   /** Pure local-cooldown state: one predicted move per cooldown, corrected by the server's answer. */
   public static final class Edge {
      private int cooldown;

      /** True = move locally now (the gate passed and the local cooldown is over); starts the local cooldown. */
      public boolean predict(boolean gateOk) {
         if (!gateOk || this.cooldown > 0) {
            return false;
         }

         this.cooldown = ReinhardRules.BEHIND_COOLDOWN;
         return true;
      }

      /** The server's cooldown after a press replaces the local guess (a refusal frees the predictor at once). */
      public void onAnswer(int serverCooldown) {
         this.cooldown = Math.max(0, serverCooldown);
      }

      public void tick() {
         if (this.cooldown > 0) {
            this.cooldown--;
         }
      }

      void reset() {
         this.cooldown = 0;
      }
   }
}
