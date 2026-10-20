package dev.baranhan.viltrumitecore.hero.reinhard.blessing;

/** Independent automatic response channel; never owns the action timeline or input.
 * A short commitment prevents simultaneous threats from switching arms every tick.
 * Repeated beam contacts refresh a hold, not the preparation clock.
 */
public final class DefenseReaction {
   public enum Kind { NONE, PALM, WEAPON, EVADE, ARROW, PROJECTILE, BEAM }
   private Kind kind = Kind.NONE;
   private int left, age;
   private float yaw, pitch;
   public Kind kind() { return left > 0 ? kind : Kind.NONE; }
   public int age() { return age; }
   public boolean contact(Kind next, float yaw, float pitch) {
      boolean changed = left == 0 || age >= 8 && next != kind;
      if (changed) { kind = next; age = 0; this.yaw = yaw; this.pitch = pitch; }
      // Lock the facing through the commitment; a sustained threat may track slowly.
      if (age >= 8 && kind == next) { this.yaw = yaw; this.pitch = pitch; }
      left = Math.max(left, next == Kind.BEAM ? 7 : 10);
      return changed;
   }
   public void tick() { if (left > 0) { left--; age++; } }
   public void clear() { kind = Kind.NONE; left = age = 0; }
   /** Reserved Reinhard heroFlags: kind 15..17, yaw 18..22, pitch 23..26. */
   public int flags() {
      if (kind() == Kind.NONE) return 0;
      int y = Math.round((Math.max(-90, Math.min(90, yaw)) + 90) * 31 / 180);
      int p = Math.round((Math.max(-60, Math.min(60, pitch)) + 60) * 15 / 120);
      return kind.ordinal() << 15 | y << 18 | p << 23;
   }
   public static Kind kind(int flags) { int n = flags >>> 15 & 7; return n < Kind.values().length ? Kind.values()[n] : Kind.NONE; }
   public static float yaw(int flags) { return (flags >>> 18 & 31) * 180F / 31 - 90; }
   public static float pitch(int flags) { return (flags >>> 23 & 15) * 120F / 15 - 60; }
}
