package dev.baranhan.viltrumitecore.client.thor;

import dev.baranhan.viltrumitecore.client.anim.pose.ActionClock;
import dev.baranhan.viltrumitecore.client.anim.pose.PoseRig;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.thor.MjolnirPlace;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Thor third-person poses (setupAnim TAIL, ThorModelMixin): the running
 * move's clip from ThorClips on the snapshot clock, otherwise the hold pose
 * while Mjölnir is in the hand. Additive over locomotion; Mjölnir is in the
 * right hand, so the rig is always the right side.
 */
public final class ThorPoser {
   private static final Map<LivingEntity, ActionClock> CLOCKS = new WeakHashMap<>();

   private ThorPoser() {
   }

   public static void poseThirdPerson(PlayerModel<?> model, LivingEntity entity) {
      if (entity instanceof Player lifter && MjolnirClient.lifting(lifter)) {
         poseLift(model, lifter);
         return;
      }

      HeroPublicSnapshot snapshot = ThorView.of(entity);
      if (snapshot == null || !(entity instanceof AbstractClientPlayer player)) {
         CLOCKS.remove(entity);
         return;
      }

      Minecraft minecraft = Minecraft.getInstance();
      boolean localFirstPerson = entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
      if (localFirstPerson && !ShaderCompat.isShadowPass()) {
         return;
      }

      float partialTick = minecraft.getFrameTime();
      ActionClock clock = CLOCKS.computeIfAbsent(entity, e -> new ActionClock());
      ThorClips.Clip clip = ThorClips.of(ThorView.move(snapshot), ThorView.step(snapshot));
      double time = clock.sample(clip == null ? -1 : snapshot.actionId(), snapshot.variant(), snapshot.actionElapsed(), snapshot.actionLength(),
         player.tickCount + partialTick);
      boolean holding = ThorView.place(snapshot, player) == MjolnirPlace.HAND;
      if (clip == null && !holding) {
         MjolnirEntity called = MjolnirClient.calledBy(player);
         if (called != null) {
            poseReach(model, player, called, partialTick);
         }

         return;
      }

      PoseRig rig = PoseRig.begin(model, entity, 1.0F, null);
      if (clip != null) {
         float t = (float)time;
         rig.mainArm(clip.rightArm(), t, 1.0F, true);
         if (clip.leftArm() != null) {
            rig.offArm(clip.leftArm(), t, 1.0F, true);
         }

         if (clip.body() != null) {
            rig.body(clip.body(), t, 1.0F, true);
         }
      } else {
         rig.mainArm(ThorClips.HOLD, 0.0F, 1.0F, true);
      }

      rig.finish();
   }

   /** №190: anyone straining at the lying hammer: both arms down-forward, torso bent, a tremble. */
   private static void poseLift(PlayerModel<?> model, Player player) {
      float shake = Mth.sin((player.tickCount + Minecraft.getInstance().getFrameTime()) * 2.3F) * 2.5F;
      PoseRig rig = PoseRig.begin(model, player, 1.0F, null);
      rig.add(model.body, 25.0F, 0.0F, 0.0F);
      rig.add(model.rightArm, -55.0F + shake, 0.0F, 8.0F);
      rig.add(model.leftArm, -55.0F - shake, 0.0F, -8.0F);
      rig.finish();
   }

   /** №55: the free arm reaches out to the returning hammer while it flies back. */
   private static void poseReach(PlayerModel<?> model, Player player, MjolnirEntity hammer, float partialTick) {
      Vec3 eye = player.getEyePosition(partialTick);
      Vec3 to = hammer.getPosition(partialTick).subtract(eye);
      double flat = Math.sqrt(to.x * to.x + to.z * to.z);
      float elevation = (float)Math.toDegrees(Math.atan2(to.y, Math.max(flat, 1.0E-4)));
      float heading = (float)Math.toDegrees(Math.atan2(-to.x, to.z));
      float yaw = Mth.clamp(Mth.wrapDegrees(heading - Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot)), -60.0F, 60.0F);
      model.rightArm.xRot = (float)Math.toRadians(Mth.clamp(-90.0F - elevation, -170.0F, -20.0F));
      model.rightArm.yRot = (float)Math.toRadians(yaw);
      model.rightArm.zRot = 0.0F;
      PoseRig.markDirty(model);
      PoseRig.copyLayers(model);
   }
}
