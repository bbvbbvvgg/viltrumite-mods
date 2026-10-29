package dev.baranhan.viltrumitecore.client.ironman.hulkbuster;

import dev.baranhan.viltrumitecore.client.ironman.IronManView;
import dev.baranhan.viltrumitecore.hero.HeroHeldTargets;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkMove;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterMotion;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterMovement;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterRules;
import dev.baranhan.viltrumitecore.hero.thor.scene.DoubleTap;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.HulkbusterMotionC2SPacket;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * The owner's half of the Hulkbuster movement (rework spec §5): reads W A S D, Space and Sneak, predicts its
 * own impulse with the same pure rules the server checks (HulkbusterMotion) and sends each op over the existing
 * HulkbusterMotionC2SPacket. A refusal (HulkbusterMotionS2CPacket) drops the prediction; the server already
 * snapped the position back. Vanilla jump is replaced: a tap is the low heavy jump, a hold past JET_TAP charges
 * the jet jump, a hold in the air is the hover, Sneak in the air is the dive; a double tap of W A S D is the dash.
 * A player held by a Hulkbuster mashes the same keys to break free (OP_STRUGGLE). During the suit-up scene the
 * owner has no movement control.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class HulkbusterMotionClient {
   private static final DoubleTap TAPS = new DoubleTap();
   /** Dash: ticks since the request (negative through the wind-up), direction; -1000 = no dash. */
   private static int dashTick = -1000;
   private static int dashDirection;
   private static boolean spaceWasDown;
   private static int spaceTicks;
   /** The current Space press began on the ground (tap jump / jet charge) or in the air (hover). */
   private static boolean pressFromGround;
   private static boolean charging;
   private static boolean hovering;
   private static int hoverTicks;
   private static boolean diving;
   private static boolean sneakWasDown;
   private static boolean backSent;
   private static final boolean[] STRUGGLE_DOWN = new boolean[5];

   private HulkbusterMotionClient() {
   }

   @SubscribeEvent
   public static void onMovementInput(MovementInputUpdateEvent event) {
      if (!(event.getEntity() instanceof LocalPlayer player)) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      struggle(mc, player);
      HeroPublicSnapshot snapshot = IronManView.of(player);
      HulkbusterLayer.Phase phase = snapshot == null ? HulkbusterLayer.Phase.NONE : HulkbusterView.phase(snapshot);
      Input input = event.getInput();
      if (HulkbusterView.scene(phase) || phase == HulkbusterLayer.Phase.EXITING) {
         // Scripted scene / climbing out: no control (spec §4).
         freeze(input);
         reset();
         return;
      }

      if (phase != HulkbusterLayer.Phase.ACTIVE) {
         if (backSent) {
            send(HulkbusterMovement.OP_BACK, 0);
         }

         reset();
         return;
      }

      boolean space = input.jumping;
      // Vanilla's jump never runs inside the machine: every Space press is ours.
      input.jumping = false;
      if (mc.screen != null) {
         space = false;
      }

      back(input.down);
      dash(mc, player, snapshot);
      jump(player, snapshot, space);
      dive(player, snapshot, input.shiftKeyDown);
      applyImpulses(player, snapshot);
   }

   private static void freeze(Input input) {
      input.forwardImpulse = 0.0F;
      input.leftImpulse = 0.0F;
      input.up = false;
      input.down = false;
      input.left = false;
      input.right = false;
      input.jumping = false;
      input.shiftKeyDown = false;
   }

   private static void reset() {
      TAPS.reset();
      dashTick = -1000;
      spaceWasDown = false;
      spaceTicks = 0;
      charging = false;
      hovering = false;
      hoverTicks = 0;
      diving = false;
      sneakWasDown = false;
      backSent = false;
   }

   /** S held: RMB in the hold slams into the ground instead of throwing (spec §6.2.5). Sent on change only. */
   private static void back(boolean down) {
      if (down != backSent) {
         backSent = down;
         send(HulkbusterMovement.OP_BACK, down ? 1 : 0);
      }
   }

   /** A committed move holds the machine (its cancel window lets the dash out), as HulkbusterMovement.busy. */
   static boolean busy(HeroPublicSnapshot snapshot) {
      HulkMove move = HulkbusterView.move(snapshot);
      if (move == null) {
         return false;
      }

      MovePhase window = move.cancellableFrom();
      return window == null || !HulkbusterView.movePhase(snapshot).atLeast(window);
   }

   static boolean energy(HeroPublicSnapshot snapshot, float cost) {
      return !snapshot.resourceLocked() && snapshot.resource() / 10.0F >= cost;
   }

   private static void dash(Minecraft mc, LocalPlayer player, HeroPublicSnapshot snapshot) {
      if (mc.screen != null) {
         TAPS.reset();
         return;
      }

      boolean[] keys = {mc.options.keyUp.isDown(), mc.options.keyLeft.isDown(), mc.options.keyDown.isDown(), mc.options.keyRight.isDown()};
      int direction = TAPS.tick(keys, player.level().getGameTime(), HulkbusterRules.DASH_TAP_WINDOW);
      if (direction < 0 || dashTick > -1000) {
         return;
      }

      HulkbusterMotion.State state = hovering ? HulkbusterMotion.State.HOVER : HulkbusterView.motion(snapshot);
      if (!HulkbusterMotion.dashAllowed(true, busy(snapshot), HeroHeldTargets.holder(player) != null, state, player.onGround(),
         HulkbusterView.dashCharges(snapshot), false, energy(snapshot, HulkbusterRules.DASH_COST))) {
         return;
      }

      dashTick = -HulkbusterRules.DASH_WINDUP;
      dashDirection = direction;
      send(HulkbusterMovement.OP_DASH, direction);
   }

   private static void jump(LocalPlayer player, HeroPublicSnapshot snapshot, boolean space) {
      boolean edge = space && !spaceWasDown;
      spaceWasDown = space;
      if (edge) {
         spaceTicks = 0;
         pressFromGround = player.onGround();
         if (!pressFromGround && !hovering && !diving && hoverAllowed(player, snapshot)) {
            hovering = true;
            hoverTicks = 0;
            send(HulkbusterMovement.OP_HOVER, 1);
         }
      }

      if (space) {
         spaceTicks++;
         if (pressFromGround && !charging && spaceTicks == HulkbusterRules.JET_TAP && player.onGround() && !busy(snapshot)
            && energy(snapshot, HulkbusterRules.JET_COST)) {
            charging = true;
            send(HulkbusterMovement.OP_JET_CHARGE, 0);
         }

         if (charging && !player.onGround()) {
            charging = false;
         }

         return;
      }

      if (hovering) {
         stopHover();
      }

      if (!pressFromGround || spaceTicks == 0) {
         spaceTicks = 0;
         return;
      }

      if (charging) {
         int charge = Math.min(HulkbusterRules.JET_CHARGE_MAX, spaceTicks - HulkbusterRules.JET_TAP);
         charging = false;
         send(HulkbusterMovement.OP_JET_RELEASE, charge);
         Vec3 launch = HulkbusterMotion.jetVelocity(charge, player.getYRot(), player.getXRot());
         player.setDeltaMovement(launch);
         player.hasImpulse = true;
      } else if (player.onGround() && !busy(snapshot) && HeroHeldTargets.holder(player) == null) {
         // The low heavy jump (tap).
         Vec3 v = player.getDeltaMovement();
         player.setDeltaMovement(v.x, HulkbusterMotion.HEAVY_JUMP, v.z);
         player.hasImpulse = true;
      }

      spaceTicks = 0;
   }

   private static boolean hoverAllowed(LocalPlayer player, HeroPublicSnapshot snapshot) {
      HulkbusterMotion.State state = HulkbusterView.motion(snapshot);
      return !player.onGround() && snapshot.resource() > 0 && HeroHeldTargets.holder(player) == null && state != HulkbusterMotion.State.DIVE
         && state != HulkbusterMotion.State.STAGGER;
   }

   private static void stopHover() {
      hovering = false;
      send(HulkbusterMovement.OP_HOVER, 0);
   }

   private static void dive(LocalPlayer player, HeroPublicSnapshot snapshot, boolean sneak) {
      boolean edge = sneak && !sneakWasDown;
      sneakWasDown = sneak;
      if (player.onGround()) {
         diving = false;
         return;
      }

      HulkbusterMotion.State state = diving ? HulkbusterMotion.State.DIVE : HulkbusterView.motion(snapshot);
      if (edge && HeroHeldTargets.holder(player) == null && HulkbusterMotion.diveAllowed(false, heightAboveGround(player), state)) {
         if (hovering) {
            hovering = false;
            send(HulkbusterMovement.OP_HOVER, 0);
         }

         diving = true;
         send(HulkbusterMovement.OP_DIVE, 0);
      }
   }

   /** The predicted impulses of this tick: the dash burst, the hover hold, the dive push. */
   private static void applyImpulses(LocalPlayer player, HeroPublicSnapshot snapshot) {
      if (dashTick > -1000) {
         dashTick++;
         if (dashTick > 0 && dashTick <= HulkbusterRules.DASH_BURST) {
            Vec3 v = HulkbusterMotion.dashVelocity(player.getYRot(), dashDirection).scale(HulkbusterMotion.dashSpeed(dashTick - 1));
            player.setDeltaMovement(v.x, player.getDeltaMovement().y, v.z);
            player.hasImpulse = true;
         } else if (dashTick > HulkbusterRules.DASH_BURST) {
            dashTick = -1000;
         }
      }

      if (hovering) {
         hoverTicks++;
         boolean serverDropped = hoverTicks > 4 && HulkbusterView.motion(snapshot) != HulkbusterMotion.State.HOVER;
         if (player.onGround() || hoverTicks >= HulkbusterRules.HOVER_MAX || serverDropped) {
            stopHover();
         } else {
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, HulkbusterMotion.hoverVertical(v.y), v.z);
            player.fallDistance = 0.0F;
         }
      }

      if (diving) {
         Vec3 v = player.getDeltaMovement();
         player.setDeltaMovement(v.x * 0.6, Math.min(v.y, -HulkbusterRules.DIVE_SPEED), v.z * 0.6);
         player.hasImpulse = true;
      }
   }

   static double heightAboveGround(Player player) {
      Vec3 from = player.position();
      BlockHitResult hit = player.level().clip(new ClipContext(from, from.add(0.0, -64.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
      return hit.getType() == HitResult.Type.MISS ? 64.0 : from.y - hit.getLocation().y;
   }

   /** A victim in a Hulkbuster's fist mashes W A S D / Space to break free (spec §6.2.6); one op per key press. */
   private static void struggle(Minecraft mc, LocalPlayer player) {
      Player holder = HeroHeldTargets.holder(player);
      HeroPublicSnapshot holderSnapshot = holder == null ? null : IronManView.of(holder);
      boolean held = holderSnapshot != null && HulkbusterView.big(HulkbusterView.phase(holderSnapshot)) && mc.screen == null;
      boolean[] keys = {mc.options.keyUp.isDown(), mc.options.keyLeft.isDown(), mc.options.keyDown.isDown(), mc.options.keyRight.isDown(),
         mc.options.keyJump.isDown()};
      for (int i = 0; i < keys.length; i++) {
         if (held && keys[i] && !STRUGGLE_DOWN[i]) {
            send(HulkbusterMovement.OP_STRUGGLE, i);
         }

         STRUGGLE_DOWN[i] = keys[i];
      }
   }

   private static void send(int op, int arg) {
      CoreMessages.sendToServer(new HulkbusterMotionC2SPacket(op, arg));
   }

   /** The server refused a predicted op (HulkbusterMotionS2CPacket): drop that prediction. */
   public static void refused(int op) {
      switch (op) {
         case HulkbusterMovement.OP_DASH -> dashTick = -1000;
         case HulkbusterMovement.OP_JET_CHARGE, HulkbusterMovement.OP_JET_RELEASE -> charging = false;
         case HulkbusterMovement.OP_HOVER -> hovering = false;
         case HulkbusterMovement.OP_DIVE -> diving = false;
         default -> {
         }
      }
   }

   /** Is the owner predicting a hover right now (HUD fuel bar). */
   public static boolean hovering() {
      return hovering;
   }

   @Nullable
   static Integer jetChargeTicks() {
      return charging ? Math.min(HulkbusterRules.JET_CHARGE_MAX, spaceTicks - HulkbusterRules.JET_TAP) : null;
   }
}
