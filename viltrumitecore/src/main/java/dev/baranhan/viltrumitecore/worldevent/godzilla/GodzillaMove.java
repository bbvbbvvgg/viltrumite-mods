package dev.baranhan.viltrumitecore.worldevent.godzilla;

import java.util.Locale;

/**
 * The timed moves of the kaiju: the clip each one plays, its length and the contact window in which the server
 * lands damage, destruction, sound and effects (the clip is authored so its contact pose sits on {@code contact}).
 * {@link #FIRE} has no fixed length: the breath decides it ({@link GodzillaRules#fireTicks}). Moves that lock the
 * walk stop the body; the head may still track the target.
 */
public enum GodzillaMove {
   NONE("", 0, -1, -1, false),
   ROAR("roar", 100, 22, 72, true),
   CHARGE("breath_charge", GodzillaRules.CHARGE_TICKS, GodzillaRules.CHARGE_BRACE, GodzillaRules.CHARGE_TICKS, true),
   FIRE("breath_fire", 0, 0, 0, true),
   BREATH_END("breath_end", GodzillaRules.BREATH_END_TICKS, 0, 12, true),
   CLAW_R("claw_r", 44, 20, 27, true),
   CLAW_L("claw_l", 44, 20, 27, true),
   TAIL("tail_strike", 70, 24, 46, true),
   STOMP("stomp", 56, 32, 32, true),
   SLAM("body_slam", 80, 34, 50, true),
   HURT("hurt", 40, 4, 4, true),
   FALL("fall", 50, 34, 34, true),
   DOWN("down", 70, -1, -1, true),
   RISE("rise", 80, 46, 46, true),
   DEATH("death", 160, 62, 62, true);

   private static final GodzillaMove[] VALUES = values();

   /** Clip name in godzilla.animation.json. */
   public final String clip;
   /** Length in ticks (0: open-ended). */
   public final int length;
   /** First and last contact tick (inclusive; -1: none). */
   public final int contact;
   public final int contactEnd;
   public final boolean locksWalk;

   GodzillaMove(String clip, int length, int contact, int contactEnd, boolean locksWalk) {
      this.clip = clip;
      this.length = length;
      this.contact = contact;
      this.contactEnd = contactEnd;
      this.locksWalk = locksWalk;
   }

   public String key() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public boolean inContact(int tick) {
      return this.contact >= 0 && tick >= this.contact && tick <= this.contactEnd;
   }

   /** Part of the breath (the beam may be active, the plates glow). */
   public boolean breath() {
      return this == CHARGE || this == FIRE || this == BREATH_END;
   }

   /** Down on the ground: no attacks, no tracking. */
   public boolean grounded() {
      return this == FALL || this == DOWN || this == RISE || this == DEATH;
   }

   public static GodzillaMove byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
   }

   public static GodzillaMove byKey(String key) {
      for (GodzillaMove m : VALUES) {
         if (m.key().equals(key)) {
            return m;
         }
      }
      return null;
   }
}
