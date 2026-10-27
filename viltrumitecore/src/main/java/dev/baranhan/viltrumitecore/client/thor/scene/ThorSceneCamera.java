package dev.baranhan.viltrumitecore.client.thor.scene;

import dev.baranhan.viltrumitecore.hero.thor.scene.FatalityPlan;
import dev.baranhan.viltrumitecore.hero.thor.scene.SceneFrame;
import dev.baranhan.viltrumitecore.hero.thor.scene.SceneSync;
import dev.baranhan.viltrumitecore.hero.thor.scene.TeleportRules;
import dev.baranhan.viltrumitecore.hero.thor.scene.ThorSceneView;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Scene cameras of the local player (decisions 89-96, 133-135): the
 * fatality shots (Thor: a slow orbit around the scene anchor; the victim:
 * a low side shot facing Thor) blended in from and back out to the normal
 * camera, and the V teleport camera (rise above Thor, then fixed; the look
 * follows the mouse). Applied by ThorSceneCameraMixin at the end of
 * Camera.setup. Client thread only.
 */
public final class ThorSceneCamera {
   public record Shot(Vec3 pos, float yaw, float pitch) {
      Shot lerp(Shot to, double t) {
         return new Shot(this.pos.lerp(to.pos, t), (float)(this.yaw + Mth.wrapDegrees(to.yaw - this.yaw) * t),
            (float)Mth.lerp(t, this.pitch, to.pitch));
      }
   }

   /** PLACEHOLDER blend ticks. */
   static final int BLEND_IN = 10;
   static final int BLEND_OUT = 8;

   @Nullable
   private static Shot last;
   private static double startedAt = -1;
   private static double endedAt = -1;
   private static boolean locksLook;
   @Nullable
   private static Vec3 lastTeleportCamera;

   private ThorSceneCamera() {
   }

   /** Does a scene direct the camera this frame (hand, outline and mouse turn off)? */
   public static boolean active() {
      return last != null;
   }

   /** The fatality camera owns the look: mouse turns are dropped. */
   public static boolean locksLook() {
      return locksLook;
   }

   /** Camera position of the teleport view (the client raycast starts here), or null. */
   @Nullable
   public static Vec3 teleportCamera() {
      return lastTeleportCamera;
   }

   static void reset() {
      last = null;
      startedAt = -1;
      endedAt = -1;
      locksLook = false;
      lastTeleportCamera = null;
   }

   /** The camera of this frame, or null for the vanilla one ({@code vanilla} = what Camera.setup computed). */
   @Nullable
   public static Shot apply(Entity cameraEntity, float partial, Shot vanilla) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player == null || cameraEntity != minecraft.player) {
         reset();
         return null;
      }

      double now = ThorSceneView.clientTicks() + partial;
      Target target = target(minecraft.player.getId(), minecraft.player.level(), partial);
      if (target != null) {
         if (startedAt < 0) {
            startedAt = now;
         }
         endedAt = -1;
         double blend = target.teleport ? 1.0 : SceneFrame.smooth(Math.min(1.0, (now - startedAt) / BLEND_IN));
         last = blend >= 1.0 ? target.shot : vanilla.lerp(target.shot, blend);
         locksLook = target.locksLook;
         return last;
      }

      locksLook = false;
      lastTeleportCamera = null;
      if (last == null) {
         startedAt = -1;
         return null;
      }

      if (endedAt < 0) {
         endedAt = now;
      }

      double out = (now - endedAt) / BLEND_OUT;
      if (out >= 1.0) {
         reset();
         return null;
      }

      return last.lerp(vanilla, SceneFrame.smooth(out));
   }

   private record Target(Shot shot, boolean locksLook, boolean teleport) {
   }

   @Nullable
   private static Target target(int localId, Level level, float partial) {
      ThorSceneView.Live own = ThorSceneView.of(localId);
      if (own != null && own.sync().kind() == SceneSync.Kind.TELEPORT) {
         SceneSync sync = own.sync();
         Vec3 at = TeleportRules.rising(sync.a(), sync.b(), ThorSceneView.tick(own, partial), Math.max(1, sync.intro()));
         lastTeleportCamera = at;
         Minecraft minecraft = Minecraft.getInstance();
         return new Target(new Shot(at, minecraft.player.getViewYRot(partial), minecraft.player.getViewXRot(partial)), false, true);
      }

      lastTeleportCamera = null;
      if (own != null && own.sync().kind() == SceneSync.Kind.FATALITY) {
         FatalityPlan plan = own.sync().plan();
         return plan == null ? null : new Target(thorShot(level, plan, ThorSceneView.tick(own, partial)), true, false);
      }

      ThorSceneView.Live victim = ThorSceneView.victimOf(localId);
      if (victim != null && victim.sync().kind() == SceneSync.Kind.FATALITY) {
         FatalityPlan plan = victim.sync().plan();
         return plan == null ? null : new Target(victimShot(level, plan, ThorSceneView.tick(victim, partial)), true, false);
      }

      return null;
   }

   /** Thor's shot: a slow orbit around the anchor, a little above the action. */
   static Shot thorShot(Level level, FatalityPlan plan, double tick) {
      double t = Math.min(tick, plan.length());
      Vec3 focus = plan.thorAt(t).lerp(plan.victimAt(t), 0.5).add(0, 1.2, 0);
      float yaw = plan.frameYaw() + 70.0F + (float)(t * 0.9);
      double distance = plan.scene() == dev.baranhan.viltrumitecore.hero.thor.scene.FatalityScene.AERIAL ? 6.5 : 4.2;
      Vec3 want = focus.add(SceneFrame.forward(yaw).scale(-distance)).add(0, 0.9, 0);
      return look(level, focus, clip(level, focus, want));
   }

   /** The victim's shot: low, from the side, Thor in frame. */
   static Shot victimShot(Level level, FatalityPlan plan, double tick) {
      double t = Math.min(tick, plan.length());
      Vec3 victim = plan.victimAt(t);
      Vec3 focus = plan.thorAt(t).add(0, 1.5, 0).lerp(victim.add(0, 1.0, 0), 0.35);
      Vec3 side = SceneFrame.right(plan.frameYaw()).scale(-2.8);
      Vec3 want = victim.add(0, 0.6, 0).add(side).add(SceneFrame.forward(plan.frameYaw()).scale(1.2));
      return look(level, focus, clip(level, focus, want));
   }

   private static Shot look(Level level, Vec3 focus, Vec3 from) {
      Vec3 d = focus.subtract(from);
      double flat = Math.sqrt(d.x * d.x + d.z * d.z);
      float yaw = (float)(Mth.atan2(d.z, d.x) * 180.0 / Math.PI) - 90.0F;
      float pitch = (float)(-(Mth.atan2(d.y, flat) * 180.0 / Math.PI));
      return new Shot(from, yaw, pitch);
   }

   /** Pull the camera in front of a wall between the focus and the wanted spot. */
   static Vec3 clip(Level level, Vec3 focus, Vec3 want) {
      BlockHitResult hit = level.clip(new ClipContext(focus, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, Minecraft.getInstance().player));
      if (hit.getType() != HitResult.Type.BLOCK) {
         return want;
      }

      Vec3 dir = want.subtract(focus);
      double length = dir.length();
      double keep = Math.max(0.6, focus.distanceTo(hit.getLocation()) - 0.3);
      return length < 1.0E-4 ? want : focus.add(dir.scale(keep / length));
   }
}
