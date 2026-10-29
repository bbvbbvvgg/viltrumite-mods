package dev.baranhan.viltrumitecore.hero.sinclair.ability;

import dev.baranhan.viltrumitecore.hero.sinclair.SinclairHero;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairMove;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairRules;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairSounds;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairState;
import dev.baranhan.viltrumitecore.hero.sinclair.Stagecraft;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.AssistantEntity;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.SinclairEntities;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.VexingBoltEntity;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Vexing Bolt (spec §5.1): 8 t wind-up, throw, 6 t recovery; a second press redirects the flying bolt(s) once. */
public final class VexingBolt {
   private VexingBolt() {
   }

   public static void press(ServerPlayer player, SinclairState state) {
      VexingBoltEntity bolt = bolt(player, state.boltId);
      if (bolt != null && !state.boltRedirected) {
         redirect(player, state, bolt);
         return;
      }

      if (state.boltCooldown > 0 || state.busy()) {
         SinclairHero.refuse(player, state);
         return;
      }

      state.startMove(SinclairMove.BOLT_CAST);
      SinclairSounds.play(player.level(), player.position().add(0.0, 1.3, 0.0), SinclairSounds.BOLT_CAST, 0.9F, 1.0F);
   }

   private static void redirect(ServerPlayer player, SinclairState state, VexingBoltEntity bolt) {
      Vec3 point = Stagecraft.aimPoint(player, 64.0);
      if (!bolt.redirect(point)) {
         return;
      }

      state.boltRedirected = true;
      VexingBoltEntity second = bolt(player, state.assistBoltId);
      if (second != null) {
         second.redirect(point);
      }

      if (!state.busy()) {
         state.startMove(SinclairMove.BOLT_SNAP);
      }

      SinclairSounds.play(player.level(), player.position().add(0.0, 1.4, 0.0), SinclairSounds.BOLT_SNAP, 1.0F, 1.0F);
      SinclairSounds.play(player.level(), bolt.position(), SinclairSounds.BOLT_SNAP, 0.7F, 1.25F);
   }

   /** Called on the cast move's clock. */
   public static void tickMove(ServerPlayer player, SinclairState state) {
      if (state.moveTick != SinclairRules.BOLT_WINDUP) {
         return;
      }

      Vec3 from = Stagecraft.wandTip(player);
      Vec3 aim = Stagecraft.aimPoint(player, 64.0);
      Vec3 dir = aim.subtract(from);
      dir = dir.lengthSqr() < 1.0E-4 ? player.getLookAngle() : dir.normalize();
      VexingBoltEntity bolt = spawn(player, from, dir, false);
      state.boltId = bolt == null ? -1 : bolt.getId();
      state.assistBoltId = -1;
      state.boltRedirected = false;
      state.boltCooldown = SinclairRules.BOLT_COOLDOWN;
      AssistantEntity assistant = Stagecraft.onStage(player, state);
      if (assistant != null) {
         Vec3 hand = assistant.handPoint();
         Vec3 to = aim.subtract(hand);
         if (to.lengthSqr() > 1.0E-4) {
            VexingBoltEntity copy = spawn(player, hand, to.normalize(), true);
            state.assistBoltId = copy == null ? -1 : copy.getId();
            assistant.gesture(AssistantEntity.CAST, 10);
         }
      }
   }

   @Nullable
   private static VexingBoltEntity spawn(ServerPlayer player, Vec3 from, Vec3 dir, boolean assistant) {
      VexingBoltEntity bolt = SinclairEntities.VEXING_BOLT.get().create(player.level());
      if (bolt == null) {
         return null;
      }

      bolt.setFromAssistant(assistant);
      bolt.launch(player, from, dir.scale(SinclairRules.BOLT_SPEED));
      player.level().addFreshEntity(bolt);
      return bolt;
   }

   @Nullable
   private static VexingBoltEntity bolt(ServerPlayer player, int id) {
      if (id < 0) {
         return null;
      }

      Entity entity = player.level().getEntity(id);
      return entity instanceof VexingBoltEntity bolt && bolt.isAlive() ? bolt : null;
   }

   public static void discard(ServerPlayer player, SinclairState state) {
      VexingBoltEntity a = bolt(player, state.boltId);
      VexingBoltEntity b = bolt(player, state.assistBoltId);
      if (a != null) {
         a.discard();
      }

      if (b != null) {
         b.discard();
      }

      state.boltId = -1;
      state.assistBoltId = -1;
   }
}
