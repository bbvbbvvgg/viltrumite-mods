package dev.baranhan.viltrumitecore.hero.reinhard.reid;

import dev.baranhan.viltrumitecore.hero.LiftLeases;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * The slow lift of the Reid draw (spec §7.3.3): every lifted entity rises
 * {@code rise(t)} over its own start Y, at most {@link ReinhardRules#REID_LIFT_MAX};
 * overlapping lifts never take it more than {@link ReinhardRules#REID_LIFT_CAP}
 * over the Y it had when the first lift took it. {@code noGravity} goes only
 * through {@link LiftLeases} (one owner UUID per event). Pure core; the server
 * scene feeds it entity adapters.
 */
public final class GravityLift {
   /** A lifted entity: the lease body plus its vertical velocity. */
   public interface Liftee extends LiftLeases.Body {
      /** Sets the vertical velocity (players: also marks it for the client). */
      void liftVelocity(double vy);

      /** The small downward push when its lease ended (it falls back at once). */
      void nudgeDown();
   }

   private final UUID owner;
   private final LiftLeases leases;
   private final Map<UUID, Double> startY = new HashMap<>();

   public GravityLift(UUID owner, LiftLeases leases) {
      this.owner = owner;
      this.leases = leases;
   }

   public UUID owner() {
      return this.owner;
   }

   /** Pure: target height over the start, {@code MAX · smoothstep(t / 100)}. */
   public static double rise(int t) {
      double s = Math.max(0.0, Math.min(1.0, t / (double)ReinhardRules.REID_LIFT_RISE_TICKS));
      return ReinhardRules.REID_LIFT_MAX * s * s * (3.0 - 2.0 * s);
   }

   /**
    * Pure: vertical velocity for the coming tick from this lift's own offset and
    * the total offset over the lease's base Y; clamped 0..0.1 (never pushes down).
    */
   public static double velocity(int t, double ownOffset, double totalOffset) {
      double wanted = Math.min(rise(t + 1) - ownOffset, ReinhardRules.REID_LIFT_CAP - totalOffset);
      return Math.max(0.0, Math.min(ReinhardRules.REID_LIFT_STEP, wanted));
   }

   /** One tick for one entity; a new entity (late entrant too) starts from its current Y and takes a lease. */
   public void step(int t, Liftee body) {
      UUID id = body.id();
      Double start = this.startY.get(id);
      if (start == null) {
         start = body.y();
         this.startY.put(id, start);
         this.leases.acquire(body, this.owner);
      }

      double base = this.leases.baseY(id);
      double own = body.y() - start;
      double total = Double.isNaN(base) ? own : body.y() - base;
      body.liftVelocity(velocity(t, own, total));
   }

   public boolean tracks(UUID id) {
      return this.startY.containsKey(id);
   }

   public Set<UUID> trackedIds() {
      return Set.copyOf(this.startY.keySet());
   }

   public int tracked() {
      return this.startY.size();
   }

   public double startY(UUID id) {
      Double start = this.startY.get(id);
      return start == null ? Double.NaN : start;
   }

   /** The end or any abort: every lease of this lift ends; bodies whose lease really ended get the nudge. */
   public List<LiftLeases.Body> end(Function<UUID, ? extends LiftLeases.Body> resolver) {
      List<LiftLeases.Body> ended = this.leases.releaseAll(this.owner, resolver);
      for (LiftLeases.Body body : ended) {
         if (body instanceof Liftee liftee) {
            liftee.nudgeDown();
         }
      }

      this.startY.clear();
      return ended;
   }
}
