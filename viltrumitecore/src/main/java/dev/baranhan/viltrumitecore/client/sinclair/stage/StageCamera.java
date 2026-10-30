package dev.baranhan.viltrumitecore.client.sinclair.stage;

import dev.baranhan.viltrumitecore.client.anim.RealTime;
import dev.baranhan.viltrumitecore.client.sinclair.SinclairPoseRules;
import dev.baranhan.viltrumitecore.client.sinclair.SinclairView;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairFlags;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairMove;
import dev.baranhan.viltrumitecore.hero.sinclair.act.ActScript;
import dev.baranhan.viltrumitecore.hero.sinclair.act.CopyCinematic;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Sinclair's staged cameras for the local player (design 2026-11-02 §2.2, §3.2), applied by
 * SinclairStageCameraMixin at the end of Camera.setup:
 * <ul>
 *   <li><b>copy cinematic</b> — a shot / reverse shot: Sinclair sees the volunteer over his own shoulder,
 *   the volunteer sees Sinclair over theirs; the look is locked and both cameras slowly push in;</li>
 *   <li><b>act camera</b> — the performer's third-person stage shot of each number: it follows the mouse
 *   (aiming still works), dollies with the four parts (tension close, main wide, climax push-in and
 *   pull-back) and turns to the front for the bow.</li>
 * </ul>
 * Weights come from the synced move clock (no local timers), so a relog or a late join lands on the same
 * shot. When the move ends early (interrupt) the last shot blends back over {@link #RELEASE_TICKS}.
 */
public final class StageCamera {
   public record Shot(Vec3 pos, float yaw, float pitch) {
      Shot lerp(Shot to, double t) {
         return new Shot(this.pos.lerp(to.pos, t), (float) (this.yaw + Mth.wrapDegrees(to.yaw - this.yaw) * t), (float) Mth.lerp(t, this.pitch, to.pitch));
      }
   }

   static final double RELEASE_TICKS = 6.0;
   @Nullable
   private static Shot last;
   private static double lastWeight;
   private static double releasedAt = -1.0;
   private static boolean locksLook;

   private StageCamera() {
   }

   /** The copy cinematic owns the look: mouse turns are dropped. */
   public static boolean locksLook() {
      return locksLook;
   }

   /** A staged (or easing-out) shot owns this frame's camera: the first-person arms must not be drawn. */
   public static boolean staged() {
      return last != null;
   }

   static void reset() {
      last = null;
      lastWeight = 0.0;
      releasedAt = -1.0;
      locksLook = false;
   }

   /** The camera of this frame, or null for the vanilla one ({@code vanilla} = what Camera.setup computed). */
   @Nullable
   public static Shot apply(Entity cameraEntity, float partial, Shot vanilla) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || cameraEntity != mc.player || mc.level == null) {
         reset();
         return null;
      }

      Player self = mc.player;
      CopyCinematicClient.Live copy = CopyCinematicClient.live(partial);
      Shot want = null;
      double weight = 0.0;
      boolean lock = false;
      if (copy != null) {
         Entity other = copy.asVolunteer() ? copy.sinclair() : copy.volunteer();
         if (other != null) {
            want = copyShot(mc.level, self, other, partial, copy.tick(), copy.asVolunteer());
            weight = CopyCinematic.cameraWeight(copy.tick());
            lock = true;
         }
      } else {
         HeroPublicSnapshot snapshot = SinclairView.of(self);
         SinclairMove move = snapshot == null ? null : SinclairView.move(snapshot);
         ActScript script = SinclairPoseRules.script(move);
         if (script != null && SinclairView.flag(snapshot, SinclairFlags.STAGE_CAMERA) && !mc.options.getCameraType().isMirrored()) {
            double t = SinclairView.moveTicks(self, snapshot, partial);
            want = actShot(mc.level, self, partial, script, t);
            weight = actWeight(script, t);
         }
      }

      locksLook = lock;
      double now = RealTime.seconds() * 20.0;
      if (want != null && weight > 0.0) {
         releasedAt = -1.0;
         last = vanilla.lerp(want, weight);
         lastWeight = weight;
         return last;
      }

      if (last == null) {
         return null;
      }

      // The move ended before its own blend-out (interrupt): ease back from the last shot.
      if (releasedAt < 0.0) {
         releasedAt = now;
      }

      double out = (now - releasedAt) / RELEASE_TICKS;
      if (out >= 1.0 || lastWeight < 0.02) {
         reset();
         return null;
      }

      return last.lerp(vanilla, CopyCinematic.smooth(out));
   }

   // --- copy cinematic --------------------------------------------------------------------------
   /** Over-the-shoulder shot from {@code self} toward {@code other}; the volunteer's is mirrored to the left shoulder. */
   static Shot copyShot(Level level, Player self, Entity other, float partial, double t, boolean mirrored) {
      Vec3 a = self.getEyePosition(partial);
      Vec3 b = other.getPosition(partial).add(0.0, other.getBbHeight() * 0.8, 0.0);
      Vec3 flat = new Vec3(b.x - a.x, 0.0, b.z - a.z);
      Vec3 dir = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, self.getViewYRot(partial)) : flat.normalize();
      Vec3 right = new Vec3(-dir.z, 0.0, dir.x);
      double side = mirrored ? -0.9 : 0.9;
      double push = CopyCinematic.smooth((t - CopyCinematic.CAMERA_IN) / (CopyCinematic.CAMERA_OUT - CopyCinematic.CAMERA_IN));
      double back = 2.6 - 0.7 * push;
      Vec3 want = a.subtract(dir.scale(back)).add(right.scale(side)).add(0.0, 0.3 - 0.1 * push, 0.0);
      Vec3 at = clip(level, a, want);
      Vec3 focus = b.lerp(a, 0.28);
      return look(at, focus);
   }

   // --- act cameras -----------------------------------------------------------------------------
   /** Pure: weight of the act camera (in over the first 8 ticks, out over the last 10). */
   public static double actWeight(ActScript script, double t) {
      if (t < 0.0 || t >= script.length()) {
         return 0.0;
      }

      return CopyCinematic.smooth(t / 8.0) * (1.0 - CopyCinematic.smooth((t - (script.length() - 10)) / 10.0));
   }

   /**
    * Pure: camera distance behind the performer per part — close in the tension, wide in the main part,
    * a push-in through the hush and a sharp pull-back on the reveal, settling for the finish.
    */
   public static double actDistance(ActScript script, double t) {
      double tension = 2.9;
      double main = 3.8;
      double hush = 2.5;
      double reveal = 4.6;
      double finish = 4.0;
      if (t < script.main()) {
         return Mth.lerp(CopyCinematic.smooth(t / Math.max(1.0, script.main())), 3.4, tension);
      }

      if (t < script.hushStart()) {
         return Mth.lerp(CopyCinematic.smooth((t - script.main()) / 8.0), tension, main);
      }

      if (t < script.reveal()) {
         return Mth.lerp(CopyCinematic.smooth((t - script.hushStart()) / Math.max(1.0, script.reveal() - script.hushStart())), main, hush);
      }

      if (t < script.pauseEnd()) {
         return Mth.lerp(CopyCinematic.smooth((t - script.reveal()) / 3.0), hush, reveal);
      }

      return Mth.lerp(CopyCinematic.smooth((t - script.pauseEnd()) / 10.0), reveal, finish);
   }

   /** Pure: 0 = over the shoulder, 1 = the front shot of the bow. */
   public static double bowTurn(ActScript script, double t) {
      return CopyCinematic.smooth((t - script.bowStart()) / 8.0);
   }

   static Shot actShot(Level level, Player self, float partial, ActScript script, double t) {
      float yaw = self.getViewYRot(partial);
      float pitch = self.getViewXRot(partial);
      Vec3 eye = self.getEyePosition(partial);
      Vec3 look = Vec3.directionFromRotation(pitch, yaw);
      Vec3 flat = Vec3.directionFromRotation(0.0F, yaw);
      Vec3 right = new Vec3(-flat.z, 0.0, flat.x);
      double d = actDistance(script, t);
      // Tension and hush sit a little lower (looking up at the magician); the reveal rises.
      ActScript.Part part = script.part(t);
      double rise = part == ActScript.Part.TENSION ? -0.15 : part == ActScript.Part.CLIMAX && t < script.reveal() ? -0.25 : 0.35;
      Vec3 shoulder = clip(level, eye, eye.subtract(look.scale(d)).add(right.scale(0.75)).add(0.0, rise, 0.0));
      Shot over = new Shot(shoulder, yaw, pitch);
      double turn = bowTurn(script, t);
      if (turn <= 0.0) {
         return over;
      }

      Vec3 front = clip(level, eye, eye.add(flat.scale(3.6)).add(right.scale(-0.8)).add(0.0, 0.1, 0.0));
      Shot bow = look(front, eye.add(0.0, -0.35, 0.0));
      return over.lerp(bow, turn);
   }

   // --- helpers ---------------------------------------------------------------------------------
   static Shot look(Vec3 from, Vec3 focus) {
      Vec3 d = focus.subtract(from);
      double flat = Math.sqrt(d.x * d.x + d.z * d.z);
      float yaw = (float) (Mth.atan2(d.z, d.x) * 180.0 / Math.PI) - 90.0F;
      float pitch = (float) (-(Mth.atan2(d.y, flat) * 180.0 / Math.PI));
      return new Shot(from, yaw, pitch);
   }

   /** Pull the camera in front of a wall between the anchor and the wanted spot. */
   static Vec3 clip(Level level, Vec3 anchor, Vec3 want) {
      BlockHitResult hit = level.clip(new ClipContext(anchor, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, Minecraft.getInstance().player));
      if (hit.getType() != HitResult.Type.BLOCK) {
         return want;
      }

      Vec3 dir = want.subtract(anchor);
      double length = dir.length();
      double keep = Math.max(0.4, anchor.distanceTo(hit.getLocation()) - 0.3);
      return length < 1.0E-4 ? want : anchor.add(dir.scale(keep / length));
   }
}
