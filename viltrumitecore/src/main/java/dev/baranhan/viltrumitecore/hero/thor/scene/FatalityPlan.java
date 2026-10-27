package dev.baranhan.viltrumitecore.hero.thor.scene;

import dev.baranhan.viltrumitecore.hero.thor.scene.SceneFrame.Offset;
import net.minecraft.world.phys.Vec3;

/**
 * One fatality as data, identical on the server and every client (it
 * travels in the scene packet): the contextual intro moves both bodies from
 * where they were to the scene frame without any jump (bounded step and
 * turn per tick), then the scene's main part and finish play in the frame.
 * Pure.
 *
 * @param origin where Thor stands for the main part (his hit position, or
 *               a nearby safe spot reached by walking there in the intro)
 * @param frameYaw Thor faces this yaw in the main part
 * @param intro intro ticks (at least what the bounded step / turn needs)
 */
public record FatalityPlan(
   FatalityScene scene,
   SceneEntry entry,
   Vec3 thorStart,
   float thorStartYaw,
   Vec3 victimStart,
   float victimStartYaw,
   Vec3 victimVelocity,
   Vec3 origin,
   float frameYaw,
   int intro
) {
   /** Build a plan with the shortest intro that respects the step and turn limits. */
   public static FatalityPlan of(FatalityScene scene, SceneEntry entry, Vec3 thorStart, float thorStartYaw, Vec3 victimStart, float victimStartYaw,
      Vec3 victimVelocity, Vec3 origin, float frameYaw, int minIntro, int maxIntro, double maxStep, float turnRate) {
      FatalityPlan plan = new FatalityPlan(scene, entry, thorStart, thorStartYaw, victimStart, victimStartYaw, entry.keepsMomentum() ? victimVelocity : Vec3.ZERO,
         origin, frameYaw, Math.max(1, minIntro));
      int n = plan.intro;
      double turn = Math.max(Math.abs(SceneFrame.wrap(frameYaw - thorStartYaw)), Math.abs(SceneFrame.wrap(frameYaw + 180.0F - victimStartYaw)));
      n = Math.max(n, (int)Math.ceil(1.5 * turn / Math.max(1.0F, turnRate)));
      n = Math.max(n, (int)Math.ceil(1.5 * thorStart.distanceTo(origin) / Math.max(0.05, maxStep)));
      n = Math.min(Math.max(n, 1), Math.max(minIntro, maxIntro));
      while (true) {
         plan = plan.withIntro(n);
         if (n >= maxIntro || plan.maxIntroStep() <= maxStep) {
            return plan;
         }
         n++;
      }
   }

   public FatalityPlan withIntro(int ticks) {
      return new FatalityPlan(this.scene, this.entry, this.thorStart, this.thorStartYaw, this.victimStart, this.victimStartYaw, this.victimVelocity, this.origin,
         this.frameYaw, Math.max(1, ticks));
   }

   public int length() {
      return this.intro + this.scene.main() + this.scene.finish();
   }

   /** Ticks into the main part (negative inside the intro). */
   public double mainTick(double tick) {
      return tick - this.intro;
   }

   public float victimFrameYaw() {
      return this.frameYaw + 180.0F;
   }

   /** Where the victim stands when the main part starts. */
   public Vec3 anchor() {
      return SceneFrame.world(this.origin, this.frameYaw, this.scene.victim(0));
   }

   public Vec3 thorAt(double tick) {
      if (tick < this.intro) {
         double s = SceneFrame.smooth(tick / this.intro);
         return this.thorStart.add(this.origin.subtract(this.thorStart).scale(s));
      }

      return SceneFrame.world(this.origin, this.frameYaw, this.scene.thor(this.clampMain(tick)));
   }

   public float thorYawAt(double tick) {
      if (tick >= this.intro) {
         return this.frameYaw;
      }

      return this.thorStartYaw + (float)(SceneFrame.wrap(this.frameYaw - this.thorStartYaw) * SceneFrame.smooth(tick / this.intro));
   }

   public Vec3 victimAt(double tick) {
      if (tick >= this.intro) {
         return SceneFrame.world(this.origin, this.frameYaw, this.scene.victim(this.clampMain(tick)));
      }

      double t = Math.max(0.0, tick / this.intro);
      double s = SceneFrame.smooth(t);
      // Momentum: the victim's own motion carries on and fades (g'(0) = 1 tick of velocity per tick).
      double carry = this.intro * (t - t * t / 2.0);
      Vec3 drift = this.victimStart.add(this.victimVelocity.scale(carry));
      Vec3 anchor = this.anchor();
      Vec3 at = drift.add(anchor.subtract(drift).scale(s));
      double floor = Math.min(this.victimStart.y, anchor.y);
      return at.y < floor ? new Vec3(at.x, floor, at.z) : at;
   }

   public float victimYawAt(double tick) {
      float target = this.victimFrameYaw();
      if (tick >= this.intro) {
         return target;
      }

      return this.victimStartYaw + (float)(SceneFrame.wrap(target - this.victimStartYaw) * SceneFrame.smooth(tick / this.intro));
   }

   public FatalityScene.Body victimBody(double tick) {
      return tick < this.intro ? FatalityScene.Body.STAND : this.scene.body(this.mainTick(tick));
   }

   public double victimLying(double tick) {
      return tick < this.intro ? 0.0 : this.scene.lying(this.mainTick(tick));
   }

   /** The largest per-tick move of either body over the intro (blocks). */
   public double maxIntroStep() {
      double max = 0.0;
      Vec3 thor = this.thorAt(0);
      Vec3 victim = this.victimAt(0);
      for (int i = 1; i <= this.intro; i++) {
         Vec3 t = this.thorAt(i);
         Vec3 v = this.victimAt(i);
         max = Math.max(max, Math.max(t.distanceTo(thor), v.distanceTo(victim)));
         thor = t;
         victim = v;
      }
      return max;
   }

   private double clampMain(double tick) {
      return Math.max(0.0, Math.min(this.scene.main() + this.scene.finish(), tick - this.intro));
   }

   /** Thor's offset in the frame at the given scene tick (for the client puppet and cameras). */
   public Offset thorOffset(double tick) {
      return tick < this.intro ? Offset.ZERO : this.scene.thor(this.clampMain(tick));
   }
}
