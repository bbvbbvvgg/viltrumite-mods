package dev.baranhan.viltrumitecore.entity.directive;

/**
 * One behaviour rule a directive puts on a body (shared behaviour-control layer, docs/rohan/heavens-door-design.md §6).
 * A directive carries a bit set of these; the layer enforces each one the same way for every author (Heaven's Door
 * today, any future mind-control power tomorrow). Bits are saved in entity NBT: append only.
 */
public enum DirectiveEffect {
   /** Never target or harm the author (Heaven's Door's Safety Lock). */
   SAFETY_LOCK,
   /** Drop the current target (the subject) and never pick it again. */
   FORGET_TARGET,
   /** No target and no harm to anybody. */
   CALM,
   /** Never target or harm villagers, citizens and other bystanders. */
   SPARE_PEACEFUL,
   /** Stand still: no walking, no navigation. */
   ROOT,
   /** Keep at least {@link MobDirectives#KEEP_AWAY_DISTANCE} blocks from the author. */
   KEEP_AWAY,
   /** Walk to the directive's point and wait there. */
   GO_TO,
   /** Follow the author at a few blocks. */
   FOLLOW,
   /** Stay beside the subject and attack whoever hurts it. */
   GUARD,
   /** No melee hits. */
   NO_MELEE,
   /** No projectiles. */
   NO_RANGED,
   /** No hero abilities (players: ActionLocks; mobs: no indirect / magic damage). */
   NO_ABILITIES,
   /** Fly along the directive's vector (Self-Edit, "fly backwards at 70 km/h"). */
   FLY,
   /** SCP-096: the author's face no longer triggers it. */
   FORGET_FACES,
   /** The book is open: the body stands dazed, harms nobody and uses nothing. */
   OPEN,
   /** A ripped page: knockback taken is multiplied by {@link MobDirectives#RIPPED_KNOCKBACK}. */
   RIPPED;

   public int bit() {
      return 1 << this.ordinal();
   }

   public boolean in(int mask) {
      return (mask & this.bit()) != 0;
   }

   public static int mask(DirectiveEffect... effects) {
      int m = 0;
      for (DirectiveEffect e : effects) {
         m |= e.bit();
      }
      return m;
   }

   /** Effects that drive the body's movement (the injected goal runs only for these). */
   public static final int MOVEMENT = mask(ROOT, KEEP_AWAY, GO_TO, FOLLOW, GUARD, FLY, OPEN);
   /** Effects that restrict whom the body may target. */
   public static final int TARGETING = mask(SAFETY_LOCK, FORGET_TARGET, CALM, SPARE_PEACEFUL, OPEN, FORGET_FACES, RIPPED);
}
