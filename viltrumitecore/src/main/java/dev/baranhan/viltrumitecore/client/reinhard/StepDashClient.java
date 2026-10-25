package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.client.AbilityInputManager;
import dev.baranhan.viltrumitecore.client.hero.SuperJumpClient;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardActions;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.StepDash;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

/**
 * Step Dash prediction (stage 3 Task 3): on the slot key edge, with the gate passing
 * on the mirrored snapshot and the local cooldown at 0, the local player gets the
 * dash velocity for {@link ReinhardRules#STEP_TICKS} ticks (re-applied each tick,
 * stopped by a wall or by a server correction). It only moves the player: the
 * press reaches the server through the generic hero slot loop, once.
 * Client only; the {@link Predictor} core is pure.
 */
public final class StepDashClient {
   private static final Predictor PREDICTOR = new Predictor();

   private StepDashClient() {
   }

   /** Registered on the Forge bus by {@link ReinhardClient}. */
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      LocalPlayer player = Minecraft.getInstance().player;
      HeroPublicSnapshot snapshot = ReinhardView.of(player);
      if (player == null || snapshot == null) {
         PREDICTOR.reset();
         return;
      }

      ReinhardAbility dash = ReinhardAbility.STEP_DASH;
      boolean down = AbilityInputManager.isAbilityKeyDown(player, dash.id());
      boolean gate = ReinhardActions.gate(new ReinhardActions.Gate(player.isAlive() && !player.isSpectator(), SuperJumpClient.anchored(player, snapshot),
         ReinhardView.mode(snapshot), dash.mode(), snapshot.extraCooldown(dash.ordinal()), snapshot.actionBusy(), false)) == ReinhardActions.Refusal.NONE;
      boolean started = PREDICTOR.onKey(down, gate);
      if (started) {
         PREDICTOR.aim(StepDash.direction(player.input.forwardImpulse, player.input.leftImpulse, player.getYRot()),
            StepDash.velocity(ReinhardRules.STEP_DISTANCE, ReinhardRules.STEP_TICKS, StepDash.friction(player)));
      }

      if (PREDICTOR.applying()) {
         Vec3 v = player.getDeltaMovement();
         if (!started && (player.horizontalCollision || Predictor.blocked(PREDICTOR.speed, v.horizontalDistance()))) {
            PREDICTOR.stop();
         } else {
            player.setDeltaMovement(PREDICTOR.dir.x * PREDICTOR.speed, v.y, PREDICTOR.dir.z * PREDICTOR.speed);
         }
      }

      PREDICTOR.tick();
   }

   /** Pure predictor state: key edge, local cooldown, the ticks left to re-apply the velocity. */
   public static final class Predictor {
      /** Below this share of the set speed the dash is over (a wall, or the server's stop). */
      static final double BLOCKED_SHARE = 0.25;
      private boolean wasDown;
      private int applyLeft;
      private int cooldown;
      private Vec3 dir = Vec3.ZERO;
      private double speed;

      /** One tick's key state; true = a dash starts now. */
      public boolean onKey(boolean down, boolean gateOk) {
         boolean edge = down && !this.wasDown;
         this.wasDown = down;
         if (!edge || !gateOk || this.cooldown > 0) {
            return false;
         }

         this.applyLeft = ReinhardRules.STEP_TICKS;
         this.cooldown = ReinhardRules.STEP_COOLDOWN;
         return true;
      }

      void aim(Vec3 dir, double speed) {
         this.dir = dir;
         this.speed = speed;
      }

      public boolean applying() {
         return this.applyLeft > 0;
      }

      public void tick() {
         if (this.applyLeft > 0) {
            this.applyLeft--;
         }

         if (this.cooldown > 0) {
            this.cooldown--;
         }
      }

      void stop() {
         this.applyLeft = 0;
      }

      void reset() {
         this.wasDown = false;
         this.applyLeft = 0;
         this.cooldown = 0;
      }

      /** The speed left after a tick of motion is too low: something stopped the dash. */
      public static boolean blocked(double setSpeed, double horizontalSpeed) {
         return horizontalSpeed < setSpeed * BLOCKED_SHARE;
      }
   }
}
