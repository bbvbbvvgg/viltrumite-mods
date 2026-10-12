package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.client.AbilityInputManager;
import dev.baranhan.viltrumitecore.client.hero.SuperJumpClient;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardActions;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.DashBehind;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.ReinhardDashBehindC2SPacket;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

/**
 * Dash Behind prediction (stage 3 Task 8): on the slot key edge the client picks
 * the living entity under the crosshair, finds the safe spot behind it with its
 * own level and, when the mirrored gate passes and the local cooldown is 0,
 * moves the player there turned to the target. Every key edge sends exactly one
 * {@link ReinhardDashBehindC2SPacket} (the server answers a refusal with its
 * sound and a position snap-back). Client only; {@link Edge} is pure.
 */
public final class DashBehindClient {
   private static final Edge EDGE = new Edge();

   private DashBehindClient() {
   }

   /** Registered on the Forge bus by {@link ReinhardClient}. */
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      LocalPlayer player = Minecraft.getInstance().player;
      HeroPublicSnapshot snapshot = ReinhardView.of(player);
      if (player == null || snapshot == null) {
         EDGE.reset();
         return;
      }

      ReinhardAbility dash = ReinhardAbility.DASH_BEHIND;
      boolean down = AbilityInputManager.isAbilityKeyDown(player, dash.id());
      boolean gate = ReinhardActions.gate(new ReinhardActions.Gate(player.isAlive() && !player.isSpectator(), SuperJumpClient.anchored(player, snapshot),
         ReinhardView.mode(snapshot), dash.mode(), snapshot.extraCooldown(dash.ordinal()), snapshot.actionBusy(), false)) == ReinhardActions.Refusal.NONE;
      if (EDGE.onKey(down)) {
         press(player, gate);
      }

      EDGE.tick();
   }

   private static void press(LocalPlayer player, boolean gate) {
      LivingEntity target = DashBehind.lookTarget(player);
      if (target == null) {
         // The server refuses (sound); nothing moves.
         CoreMessages.sendToServer(new ReinhardDashBehindC2SPacket(-1, player.position()));
         return;
      }

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

   /** Pure key-edge and local-cooldown state: one packet per edge, one predicted move per cooldown. */
   public static final class Edge {
      private boolean wasDown;
      private int cooldown;

      /** One tick's key state; true = an edge (send one packet). */
      public boolean onKey(boolean down) {
         boolean edge = down && !this.wasDown;
         this.wasDown = down;
         return edge;
      }

      /** True = move locally now (the gate passed and the local cooldown is over); starts the local cooldown. */
      public boolean predict(boolean gateOk) {
         if (!gateOk || this.cooldown > 0) {
            return false;
         }

         this.cooldown = ReinhardRules.BEHIND_COOLDOWN;
         return true;
      }

      public void tick() {
         if (this.cooldown > 0) {
            this.cooldown--;
         }
      }

      void reset() {
         this.wasDown = false;
         this.cooldown = 0;
      }
   }
}
