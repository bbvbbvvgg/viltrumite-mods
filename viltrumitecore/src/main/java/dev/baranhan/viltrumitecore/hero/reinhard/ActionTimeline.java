package dev.baranhan.viltrumitecore.hero.reinhard;

import javax.annotation.Nullable;

/**
 * The one action timeline of Reinhard (stage 3): ability, elapsed ticks, length,
 * target entity id and a phase (combo hit, catch snap, throw). Drives the public
 * snapshot fields {@code actionId/actionElapsed/actionLength/controlTargetId/variant},
 * which drive the clips in both views. Transient (never saved). Pure.
 */
public final class ActionTimeline {
   /** Phase of a window (catch / reflect) after a projectile was taken, and of the grab after the throw. */
   public static final int PHASE_SNAP = 1;
   @Nullable
   private ReinhardAbility ability;
   private int elapsed;
   private int length;
   private int targetId = -1;
   private int phase;
   private boolean done;

   public void start(ReinhardAbility ability, int length, int targetId) {
      this.start(ability, length, targetId, 0);
   }

   /** (Re)starts at tick 0; a length below 1 leaves the timeline idle. */
   public void start(ReinhardAbility ability, int length, int targetId, int phase) {
      if (length <= 0) {
         this.cancel();
         return;
      }

      this.ability = ability;
      this.done = false;
      this.elapsed = 0;
      this.length = length;
      this.targetId = targetId;
      this.phase = phase;
   }

   /** One server tick; the timeline ends by itself after {@link #length()} ticks. */
   public void tick() {
      if (this.ability == null) {
         return;
      }

      this.elapsed++;
      if (this.elapsed >= this.length) {
         this.cancel();
         this.done = true;
      }
   }

   /** True right after the tick that ended the timeline (until the next start). */
   public boolean done() {
      return this.done;
   }

   public void cancel() {
      this.ability = null;
      this.elapsed = 0;
      this.length = 0;
      this.targetId = -1;
      this.phase = 0;
      this.done = false;
   }

   public boolean running() {
      return this.ability != null;
   }

   public boolean is(ReinhardAbility ability) {
      return this.ability == ability;
   }

   @Nullable
   public ReinhardAbility ability() {
      return this.ability;
   }

   public int elapsed() {
      return this.elapsed;
   }

   public int length() {
      return this.length;
   }

   public int targetId() {
      return this.targetId;
   }

   public int phase() {
      return this.phase;
   }

   /** {@code actionId} on the wire: the ability ordinal, −1 = none. */
   public int wireId() {
      return this.ability == null ? -1 : this.ability.ordinal();
   }
}
