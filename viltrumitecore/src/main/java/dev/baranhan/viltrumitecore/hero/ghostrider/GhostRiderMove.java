package dev.baranhan.viltrumitecore.hero.ghostrider;

import javax.annotation.Nullable;

/**
 * Timeline moves of the Ghost Rider (snapshot {@code actionId}, append only). Length and contact tick (-1 = none) are
 * authoritative on the server; the client poses the same clip on the same clock (GhostRiderPoser).
 */
public enum GhostRiderMove {
   TRANSFORM("transform", GhostRiderRules.TRANSFORM_TICKS, GhostRiderRules.TRANSFORM_FLARE_TICK, -1),
   REVERT("revert", GhostRiderRules.REVERT_TICKS, -1, -1),
   LASH_1("lash_1", 10, 4, 7),
   LASH_2("lash_2", 10, 4, 7),
   LASH_3("lash_3", 14, 7, 11),
   HOOK("hook", 8, 3, -1),
   YANK("yank", GhostRiderRules.YANK_TICKS + 2, -1, 6),
   HOOK_MISS("hook_miss", 8, -1, 5),
   WHIRL("whirl", GhostRiderRules.WHIRL_TICKS, -1, -1),
   SLAM("slam", GhostRiderRules.SLAM_TICKS, GhostRiderRules.SLAM_CONTACT, 10),
   WAVE("wave", GhostRiderRules.WAVE_TICKS, GhostRiderRules.WAVE_RELEASE, 14),
   DASH("dash", GhostRiderRules.DASH_TICKS, 0, 6),
   STARE("stare", GhostRiderRules.STARE_TICKS, GhostRiderRules.STARE_VERDICT, -1),
   BIKE_LASH("bike_lash", 10, 4, 8),
   BIKE_HOOK("bike_hook", 8, 3, -1);

   private final String clip;
   public final int length;
   public final int contact;
   public final int cancelFrom;

   GhostRiderMove(String clip, int length, int contact, int cancelFrom) {
      this.clip = clip;
      this.length = length;
      this.contact = contact;
      this.cancelFrom = cancelFrom;
   }

   public String clip() {
      return this.clip;
   }

   @Nullable
   public static GhostRiderMove byId(int id) {
      GhostRiderMove[] values = values();
      return id >= 0 && id < values.length ? values[id] : null;
   }

   public static GhostRiderMove lash(int step) {
      return values()[LASH_1.ordinal() + Math.max(0, Math.min(GhostRiderRules.COMBO_LENGTH - 1, step))];
   }

   public boolean isLash() {
      return this == LASH_1 || this == LASH_2 || this == LASH_3;
   }

   /** Walking input is ignored (speed modifier). */
   public boolean roots() {
      return this == TRANSFORM || this == REVERT || this == STARE || this == SLAM;
   }

   /** Refuses every other input until it ends. */
   public boolean locks() {
      return this == TRANSFORM || this == REVERT || this == STARE;
   }

   /** Knockback does not interrupt these. */
   public boolean unstoppable() {
      return this == TRANSFORM || this == WHIRL || this == SLAM || this == STARE;
   }

   public boolean cancellable(int elapsed) {
      return this.cancelFrom >= 0 && elapsed >= this.cancelFrom;
   }

   /** Moves that may run while riding the Hell Cycle. */
   public boolean onBike() {
      return this == BIKE_LASH || this == BIKE_HOOK || this == WHIRL || this == SLAM || this == WAVE;
   }
}
