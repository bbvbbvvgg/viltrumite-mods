package dev.baranhan.viltrumitecore.client.anim.pose;

/**
 * Per-player cast clock shared by the hero pose layers. The legacy tick/time policy
 * below follows snapshots exactly; sample() is an explicit bounded render policy.
 * While the server
 * reports a cast the clock is exactly the synced actionElapsed (never ahead of
 * it: no snapshot extrapolation); render code adds partialTick on top, the
 * same way the Viltrumite mixins add it to their synced tick counters. Only
 * after the server cleared a cast that reached its end does the clock count
 * locally through the client-only recovery tail.
 */
public class ActionClock {
   private int actionId = -1;
   private int ticks;
   private int visualLength;
   private boolean running;

   /**
    * One client tick. {@code syncedActionId} is -1 while the server has no cast.
    * A cast that reached its server end keeps ticking through the client-only
    * recovery tail; a cast cleared early (cancel) freezes so the layer weight
    * can fade it out.
    */
   public void tick(int syncedActionId, int syncedElapsed, int serverLength, int visualLength) {
      if (syncedActionId >= 0) {
         boolean restart = !this.running || syncedActionId != this.actionId || syncedElapsed + 2 < this.ticks;
         if (restart) {
            this.actionId = syncedActionId;
            this.ticks = Math.max(0, syncedElapsed);
            this.running = true;
         } else {
            this.ticks = syncedElapsed;
         }
         this.visualLength = Math.max(serverLength, visualLength);
         this.ticks = Math.min(this.ticks, this.visualLength);
         return;
      }

      if (!this.running) {
         return;
      }
      if (this.ticks >= serverLength - 2 && this.ticks < this.visualLength) {
         this.ticks++;
         this.running = this.ticks < this.visualLength;
      } else {
         this.running = false;
      }
   }

   public boolean isPlaying(int action) {
      return this.running && this.actionId == action;
   }

   /** Last cast id, also while frozen after a cancel. */
   public int actionId() {
      return this.actionId;
   }

   public int ticks() {
      return this.ticks;
   }

   /** Elapsed cast time for rendering; frozen once the cast stopped. */
   public float time(float partialTick) {
      return this.running ? Math.min((float)this.visualLength, (float)this.ticks + partialTick) : (float)this.ticks;
   }

   // Opt-in render policy. Legacy tick()/time() callers retain their exact behavior.
   private int renderAction = -1, renderPhase, renderElapsed;
   private double renderTime, renderChangedAt, renderSampledAt;
   private boolean renderRunning;

   /**
    * Monotonic render clock in CLIENT tick units (tickCount + partial tick).
    * Reconciles snapshots at 0.75..1.25x, never rewinds an active segment, and
    * predicts at most three ticks past the last authoritative sample. Repeated
    * view/shadow passes at the same timestamp are idempotent; pausing adds no time.
    * Phase changes and authoritative timeline restarts explicitly begin a new clip.
    */
   public double sample(int action, int phase, int elapsed, int length, double clientTicks) {
      if (!Double.isFinite(clientTicks)) return renderRunning ? renderTime : 0;
      if (action < 0) { renderRunning = false; return 0; }
      if (renderRunning && clientTicks < renderSampledAt) return renderTime;
      elapsed = Math.max(0, elapsed);
      if (!renderRunning || action != renderAction || phase != renderPhase
         || elapsed < renderElapsed) {
         renderAction = action; renderPhase = phase; renderElapsed = elapsed;
         renderTime = elapsed; renderSampledAt = renderChangedAt = clientTicks;
         renderRunning = true;
      } else {
         double dt = Math.max(0, clientTicks - renderSampledAt);
         if (elapsed != renderElapsed) {
            renderElapsed = elapsed;
            renderChangedAt = clientTicks;
         }
         double target = elapsed + Math.min(3, Math.max(0, clientTicks-renderChangedAt));
         double rate = Math.max(.75, Math.min(1.25, 1 + (target-renderTime-dt)*.25));
         renderTime = Math.min(elapsed + 3.0, renderTime + dt*rate);
         renderSampledAt = clientTicks;
      }
      if (length > 0) renderTime = Math.min(length, renderTime);
      return renderTime;
   }

   public void reset() {
      this.renderRunning = false;
      this.actionId = -1;
      this.ticks = 0;
      this.visualLength = 0;
      this.running = false;
   }
}
