package dev.baranhan.viltrumitecore.client.thor.scene;

import static dev.baranhan.viltrumitecore.client.anim.pose.PoseKeys.key;

import dev.baranhan.viltrumitecore.client.anim.pose.PoseRig;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.thor.HammerState;
import dev.baranhan.viltrumitecore.hero.thor.ThorFlags;
import dev.baranhan.viltrumitecore.hero.thor.ThorHands;
import dev.baranhan.viltrumitecore.hero.thor.scene.FatalityPlan;
import dev.baranhan.viltrumitecore.hero.thor.scene.FatalityScene;
import dev.baranhan.viltrumitecore.hero.thor.scene.GrabAction;
import dev.baranhan.viltrumitecore.hero.thor.scene.GrabFlow;
import dev.baranhan.viltrumitecore.hero.thor.scene.SceneSync;
import dev.baranhan.viltrumitecore.hero.thor.scene.ThorSceneConfig;
import dev.baranhan.viltrumitecore.hero.thor.scene.ThorSceneView;
import dev.baranhan.viltrumiteflight.util.FlightState;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Scene and flight poses (absolute PoseKeys rows, degrees). Runs first in
 * ThorPoser for every player model: Thor in a grab / fatality, the victim
 * of either (any hero), and Thor's flight arms (hover: Mjölnir spinning
 * at the side; cruise: arm forward-up; sonic: straight ahead; no hammer:
 * a plain hover). Returns true when it posed the model, which then skips the
 * normal Thor clip. All keys PLACEHOLDER (storyboards are [ОТКРЫТО]).
 */
public final class ThorScenePoser {
   private static final float[][] GRAB_HOLD_ARM = {key(0, -95, 8, 0)};
   private static final float[][] VICTIM_ARMS = {key(0, -150, 0, 10), key(10, -140, 0, 16), key(20, -150, 0, 10)};
   private static final float[][] VICTIM_LEGS = {key(0, 12, 0, 0), key(8, -10, 0, 0), key(16, 12, 0, 0)};
   private static final float[][] SLAM_ARM = {key(0, -95, 8, 0), key(7, -175, 0, 0), key(12, -40, 0, 0), key(20, -30, 0, 0)};
   private static final float[][] THROW_ARM = {key(0, -95, 8, 0), key(4, -150, -20, 0), key(7, -80, 10, 0), key(14, -40, 0, 0)};
   private static final float[][] HAMMER_UP = {key(0, -20, 0, 0), key(14, -170, 0, 0), key(26, -175, 0, 0), key(29, -40, 0, 0), key(46, -30, 0, 0)};
   private static final float[][] HEAD_GRIP = {key(0, -60, 10, 0), key(10, -100, 10, 0), key(28, -100, 10, 0), key(32, -40, 0, 0)};
   private static final float[][] CHEST_PUNCH = {key(0, -20, 0, 0), key(11, -30, 0, 0), key(14, -95, 0, 0), key(28, -95, 0, 0), key(32, -60, 0, 0),
      key(42, -120, 0, 0), key(46, -40, 0, 0)};
   private static final float[][] UPPERCUT = {key(0, -20, 0, 0), key(4, 20, 0, 0), key(7, -170, 0, 0), key(20, -175, 0, 0), key(24, -160, 0, 0),
      key(27, -20, 0, 0), key(40, -30, 0, 0)};
   /** C-M12: the victim's body follows FatalityScene.Body (kneeling legs forward under the lowered anchor, limp arms, flat when lying). */
   private static final float[][] KNEEL_LEGS = {key(0, -85, 0, 8)};
   private static final float[][] LIMP_ARMS = {key(0, -12, 0, 8)};
   private static final float[][] LYING_ARMS = {key(0, -6, 0, 14)};
   private static final float[][] LYING_LEGS = {key(0, 0, 0, 4)};
   private static final float[][] HOVER_ARM = {key(0, -30, 0, 70)};
   private static final float[][] CRUISE_ARM = {key(0, -150, 0, 0)};
   private static final float[][] SONIC_ARM = {key(0, -180, 0, 0)};
   private static final float[][] HOVER_LEGS = {key(0, 8, 0, 0), key(20, 3, 0, 0), key(40, 8, 0, 0)};

   private ThorScenePoser() {
   }

   /** True when this player model was posed here. */
   public static boolean pose(PlayerModel<?> model, LivingEntity entity) {
      float partial = Minecraft.getInstance().getFrameTime();
      ThorSceneView.Live own = ThorSceneView.of(entity.getId());
      if (own != null) {
         return poseThor(model, entity, own, partial);
      }

      ThorSceneView.Live victim = ThorSceneView.victimOf(entity.getId());
      FatalityPlan victimPlan = victim != null && victim.sync().kind() == SceneSync.Kind.FATALITY ? victim.sync().plan() : null;
      if (victimPlan != null) {
         FatalityScene.Body body = victimPlan.victimBody(Math.min(ThorSceneView.tick(victim, partial), victimPlan.length()));
         if (body != FatalityScene.Body.STAND && body != FatalityScene.Body.AIRBORNE) {
            PoseRig rig = PoseRig.begin(model, entity, 1.0F, null);
            boolean kneel = body == FatalityScene.Body.KNEEL;
            float[][] arms = kneel ? LIMP_ARMS : LYING_ARMS;
            float[][] legs = kneel ? KNEEL_LEGS : LYING_LEGS;
            rig.mainArm(arms, 0, 1.0F, false);
            rig.offArm(arms, 0, 1.0F, true);
            rig.mainLeg(legs, 0, 1.0F, false);
            rig.offLeg(legs, 0, 1.0F, true);
            rig.finish();
            return true;
         }
      }

      if (victim != null && ThorSceneView.holds(victim.sync())) {
         PoseRig rig = PoseRig.begin(model, entity, 1.0F, null);
         float t = (float)((entity.tickCount + partial) % 20);
         rig.mainArm(VICTIM_ARMS, t, 1.0F, false);
         rig.offArm(VICTIM_ARMS, (t + 6) % 20, 1.0F, false);
         rig.mainLeg(VICTIM_LEGS, t % 16, 0.8F, false);
         rig.offLeg(VICTIM_LEGS, (t + 8) % 16, 0.8F, false);
         rig.finish();
         return true;
      }

      return poseFlight(model, entity, partial);
   }

   private static boolean poseThor(PlayerModel<?> model, LivingEntity entity, ThorSceneView.Live live, float partial) {
      SceneSync sync = live.sync();
      double tick = ThorSceneView.tick(live, partial);
      PoseRig rig;
      switch (sync.kind()) {
         case GRAB -> {
            GrabFlow.Stage stage = sync.grabStage();
            if (stage != GrabFlow.Stage.HOLD && stage != GrabFlow.Stage.ACTION) {
               return false;
            }

            rig = PoseRig.begin(model, entity, 1.0F, null);
            GrabAction action = stage == GrabFlow.Stage.ACTION ? GrabAction.byId(sync.variant()) : null;
            float t = (float)tick;
            if (action == GrabAction.SLAM) {
               rig.offArm(SLAM_ARM, t, 1.0F, false);
            } else if (action == GrabAction.THROW) {
               rig.offArm(THROW_ARM, t, 1.0F, false);
            } else {
               rig.offArm(GRAB_HOLD_ARM, 0, 1.0F, false);
               if (action == GrabAction.BARRAGE) {
                  int interval = Math.max(1, ThorSceneConfig.barrageInterval);
                  float phase = (float)(tick % interval) / interval;
                  rig.mainArm(new float[][]{key(0, -90 + 40 * phase, 0, 0)}, 0, 1.0F, false);
               }
            }
         }
         case FATALITY -> {
            FatalityPlan plan = sync.plan();
            if (plan == null) {
               return false;
            }

            float t = (float)plan.mainTick(tick);
            if (t < 0) {
               // MINOR-13: decided before the rig begins (no rig begun and dropped).
               return false;
            }

            rig = PoseRig.begin(model, entity, 1.0F, null);

            switch (plan.scene()) {
               case HEAD_CRUSH -> {
                  rig.offArm(HEAD_GRIP, t, 1.0F, false);
                  rig.mainArm(HAMMER_UP, t, 1.0F, false);
               }
               case HEART_RIP -> rig.mainArm(CHEST_PUNCH, t, 1.0F, false);
               case AERIAL -> {
                  rig.mainArm(UPPERCUT, t, 1.0F, false);
                  rig.offArm(UPPERCUT, Math.max(0, t - 1), 0.5F, false);
               }
            }
         }
         default -> {
            return false;
         }
      }

      rig.finish();
      return true;
   }

   private static boolean poseFlight(PlayerModel<?> model, LivingEntity entity, float partial) {
      if (!(entity instanceof HeroPlayer heroPlayer) || !(entity instanceof ViltrumiteFlightPlayer flight)) {
         return false;
      }

      HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
      FlightState state = flight.getFlightState();
      if (snapshot == null || snapshot.heroId() != HeroId.THOR || snapshot.actionBusy() || state == null || state == FlightState.NONE) {
         return false;
      }

      // A-M2: the swinging-Mjölnir arms only with Mjölnir really in the hand (an item keeps it on the belt).
      boolean hammer = ThorHands.holds(ThorFlags.hammer(snapshot.heroFlags()), !(entity instanceof Player player) || ThorHands.free(player));
      float t = entity.tickCount + partial;
      PoseRig rig = PoseRig.begin(model, entity, 1.0F, null);
      switch (state) {
         case HOVER -> {
            if (hammer) {
               // Mjölnir swung round at the side by its strap (PLACEHOLDER: an arm circle, no item spin).
               rig.mainArm(HOVER_ARM, 0, 1.0F, false);
               rig.add(model.rightArm, (float)Math.cos(t * 0.9F) * 25.0F, 0, (float)Math.sin(t * 0.9F) * 25.0F);
            }
            rig.mainLeg(HOVER_LEGS, t % 40, 0.6F, true);
            rig.offLeg(HOVER_LEGS, (t + 20) % 40, 0.6F, true);
         }
         case CRUISE -> rig.mainArm(CRUISE_ARM, 0, hammer ? 1.0F : 0.0F, false);
         case SONIC -> rig.mainArm(SONIC_ARM, 0, hammer ? 1.0F : 0.0F, false);
         default -> {
         }
      }

      rig.finish();
      return true;
   }
}
