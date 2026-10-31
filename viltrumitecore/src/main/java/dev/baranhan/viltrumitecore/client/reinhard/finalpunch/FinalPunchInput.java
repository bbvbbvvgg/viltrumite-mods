package dev.baranhan.viltrumitecore.client.reinhard.finalpunch;

import dev.baranhan.viltrumitecore.client.AbilityInputManager;
import dev.baranhan.viltrumitecore.client.hero.SuperJumpClient;
import dev.baranhan.viltrumitecore.client.reinhard.ReinhardView;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardActions;
import dev.baranhan.viltrumitecore.hero.reinhard.finalpunch.FinalPunchRules;
import dev.baranhan.viltrumitecore.hero.reinhard.finalpunch.FinalPunchTargets;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.ReinhardFinalPunchC2SPacket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;

/**
 * The secret chord: Shift + the ability-6 key (N by default), Reinhard only, any mode. While Shift
 * is held the slot-6 edges are swallowed ({@link #intercept}), so Holy Sword's World Erasure (or
 * whatever sits in slot 6) never fires with it. One press = one request: the client checks the
 * cooldown, a running action and a target in its look cone first (clear feedback, nothing sent
 * without a target); the server re-checks everything and owns the start.
 * <p>During the local scene the caster's movement, jump, sneak and clicks are dropped and the
 * mouse is locked (TimeFreezeMouseMixin) until the scene ends or is cancelled.
 */
public final class FinalPunchInput {
   private static boolean wasDown;

   private FinalPunchInput() {
   }

   static boolean shiftDown(Minecraft mc) {
      return Screen.hasShiftDown() || mc.options.keyShift.isDown();
   }

   /** Pure: the chord is held (Shift + the slot-6 key). */
   public static boolean chord(boolean shift, boolean key) {
      return shift && key;
   }

   /** HeroClientInput interceptor: with Shift held, slot 6's own action is swallowed. */
   public static boolean intercept(LocalPlayer player, HeroAction action, boolean down) {
      Minecraft mc = Minecraft.getInstance();
      if (!shiftDown(mc) && !(down == false && wasDown)) return false;
      HeroAction slot6 = slotSix(player);
      return slot6 != null && slot6 == action;
   }

   static HeroAction slotSix(LocalPlayer player) {
      if (!(player instanceof dev.baranhan.viltrumitecore.util.ViltrumiteAbilityUser user)) return null;
      HeroDefinition hero = HeroRegistry.get(player);
      String id = user.getAbilityInSlot(user.getActivePage() * 6 + 5);
      return id == null ? null : hero.heroActionFor(id);
   }

   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) return;
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null || mc.level == null) {
         wasDown = false;
         return;
      }
      if (FinalPunchClient.locksLocal()) {
         // The scene owns the hands: no swing, use, pick or drop slips through.
         while (mc.options.keyAttack.consumeClick()) {
         }
         while (mc.options.keyUse.consumeClick()) {
         }
         while (mc.options.keyPickItem.consumeClick()) {
         }
         while (mc.options.keyDrop.consumeClick()) {
         }
      }
      boolean reinhard = HeroRegistry.get(player).id() == HeroId.REINHARD;
      boolean down = reinhard && mc.screen == null && mc.isWindowActive() && chord(shiftDown(mc), AbilityInputManager.abilityKey6.isDown());
      if (down && !wasDown) press(player);
      wasDown = down;
   }

   static void press(LocalPlayer player) {
      if (FinalPunchClient.local() != null) return;
      HeroPublicSnapshot snapshot = ReinhardView.of(player);
      if (snapshot == null) return;
      ReinhardAbility ability = ReinhardAbility.FINAL_PUNCH;
      ReinhardActions.Refusal refusal = ReinhardActions.gate(new ReinhardActions.Gate(player.isAlive() && !player.isSpectator(),
         SuperJumpClient.anchored(player, snapshot), ReinhardView.mode(snapshot), ReinhardView.mode(snapshot),
         snapshot.extraCooldown(ability.snapshotIndex()), snapshot.actionBusy(), false));
      if (refusal == ReinhardActions.Refusal.COOLDOWN) {
         int seconds = (snapshot.extraCooldown(ability.snapshotIndex()) + 19) / 20;
         tell(player, Component.translatable("message.viltrumitecore.reinhard.final_punch.cooldown", seconds));
         return;
      }
      if (refusal != ReinhardActions.Refusal.NONE) {
         tell(player, Component.translatable("message.viltrumitecore.reinhard.final_punch.busy"));
         return;
      }
      int target = pick(player);
      if (target < 0) {
         tell(player, Component.translatable("message.viltrumitecore.reinhard.final_punch.no_target"));
         return;
      }
      CoreMessages.sendToServer(new ReinhardFinalPunchC2SPacket(target));
   }

   /** The client's look pick (same pure rule as the server's, which re-validates it). */
   static int pick(LocalPlayer player) {
      Vec3 eye = player.getEyePosition();
      double r = FinalPunchRules.TARGET_RADIUS;
      List<FinalPunchTargets.Candidate> candidates = new ArrayList<>();
      for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r), e -> e != player && FinalPunchTargets.of(e) != null)) {
         boolean valid = e.isAlive() && !e.isSpectator() && !e.isInvisible();
         candidates.add(new FinalPunchTargets.Candidate(e.getId(), e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye), valid, player.hasLineOfSight(e)));
      }
      return FinalPunchTargets.pick(player.getLookAngle(), candidates, r, FinalPunchRules.TARGET_CONE_DEGREES);
   }

   private static void tell(LocalPlayer player, Component message) {
      player.displayClientMessage(message, true);
   }

   /** MovementInputUpdateEvent: the caster stands still for the whole scene. */
   public static void onMovementInput(MovementInputUpdateEvent event) {
      if (!FinalPunchClient.locksLocal() || event.getEntity() != Minecraft.getInstance().player) return;
      var input = event.getInput();
      input.forwardImpulse = 0.0F;
      input.leftImpulse = 0.0F;
      input.up = false;
      input.down = false;
      input.left = false;
      input.right = false;
      input.jumping = false;
      input.shiftKeyDown = false;
   }
}
