package dev.baranhan.viltrumitecore.client.thor;

import dev.baranhan.viltrumitecore.client.anim.pose.ActionClock;
import dev.baranhan.viltrumitecore.client.anim.pose.PoseRig;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.thor.HammerState;
import dev.baranhan.viltrumitecore.hero.thor.MjolnirPlace;
import dev.baranhan.viltrumitecore.hero.thor.ThorFlags;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;

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
      ThorClips.Clip clip = ThorClips.of(ThorView.move(snapshot), ThorView.step(snapshot), snapshot.resource());
      double time = clock.sample(clip == null ? -1 : snapshot.actionId(), snapshot.variant(), snapshot.actionElapsed(), snapshot.actionLength(),
         player.tickCount + partialTick);
      boolean holding = ThorView.place(snapshot, player) == MjolnirPlace.HAND;
      // #154 / #155: while Mjölnir flies back, the arm reaches for it, but only once the current strike is over.
      boolean reaching = clip == null && ThorFlags.hammer(snapshot.heroFlags()) == HammerState.RETURNING;
      if (clip == null && !holding && !reaching) {
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
      } else if (reaching) {
         rig.mainArm(ThorCombatClips.reach(player.getViewXRot(partialTick)), 0.0F, 1.0F, true);
      } else {
         rig.mainArm(ThorClips.HOLD, 0.0F, 1.0F, true);
      }

      rig.finish();
   }
}
