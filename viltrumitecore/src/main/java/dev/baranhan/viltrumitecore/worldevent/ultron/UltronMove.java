package dev.baranhan.viltrumitecore.worldevent.ultron;

/**
 * Timed actions of the machines and of Ultron (shared by server and client). The server starts a move,
 * applies damage / sound / FX on its contact ticks; the client plays the clip of the same name from the
 * same start tick, so the hit lands on the frame where the clip makes contact (anticipation, then
 * acceleration into contact, follow-through and recovery are in the clip). Lengths match the clips
 * (UltronAssetsTest checks every clip length against this table).
 */
public enum UltronMove {
   NONE("", 0),
   // drone
   DRONE_ZAP("zap", 16, 9),
   DRONE_SCAN("scan", 40),
   DRONE_MARK("mark", 30, 12),
   // sentry
   SENTRY_STRIKE_A("strike_a", 14, 6),
   SENTRY_STRIKE_B("strike_b", 11, 4),
   SENTRY_STRIKE_C("strike_c", 20, 11),
   SENTRY_BLAST("blast", 22, 12),
   SENTRY_LUNGE("lunge", 12, 8),
   // brute
   BRUTE_SLAM("slam", 30, 17),
   BRUTE_MISSILES("missiles", 34, 14, 18, 22, 26),
   BRUTE_CHARGE("charge", 14),
   BRUTE_SWING("swing", 18, 9),
   BRUTE_SHIELD("shield_up", 12),
   // interceptor
   INTERCEPTOR_BURST("burst", 18, 6, 9, 12),
   INTERCEPTOR_DIVE("dive", 30, 22),
   INTERCEPTOR_MISSILE("missile", 20, 10),
   INTERCEPTOR_BOOST("boost", 12),
   // engineer
   ENGINEER_HACK("hack", 40, 30),
   ENGINEER_PLANT("plant", 24, 14),
   // shared reactions
   HEAVY_HIT("heavy_hit", 20),
   LAND("land", 10),
   FABRICATE("fabricate", 60),
   // Ultron Prime
   PRIME_JAB_CROSS("jab_cross", 20, 6, 13),
   PRIME_UPPERCUT("uppercut", 22, 11),
   PRIME_BACKHAND("backhand", 18, 9),
   PRIME_PALM_BLAST("palm_blast", 26, 15),
   PRIME_BEAM("beam", 50, 16, 21, 26, 31, 36, 41),
   PRIME_SLAM("slam", 34, 20),
   PRIME_DIVE("dive", 36, 24),
   PRIME_DASH("dash", 14, 7),
   PRIME_SUMMON("summon", 30, 18),
   PRIME_ABSORB("absorb", 40, 20),
   PRIME_TRANSITION_AIR("transition_air", 80, 40),
   PRIME_TRANSITION_OVERDRIVE("transition_overdrive", 80, 44),
   PRIME_ASSEMBLE("assemble", 140, 120),
   PRIME_DEATH("death", 120),
   PRIME_ASCEND("ascend", 120);

   public final String clip;
   public final int length;
   private final int[] contacts;

   UltronMove(String clip, int length, int... contacts) {
      this.clip = clip;
      this.length = length;
      this.contacts = contacts;
   }

   /** Is {@code tick} (ticks since the start) a contact tick of this move? */
   public boolean contact(int tick) {
      for (int c : this.contacts) {
         if (c == tick) {
            return true;
         }
      }
      return false;
   }

   /** Index of the contact at {@code tick} (0 = first), -1 when none. */
   public int contactIndex(int tick) {
      for (int i = 0; i < this.contacts.length; i++) {
         if (this.contacts[i] == tick) {
            return i;
         }
      }
      return -1;
   }

   public int contacts() {
      return this.contacts.length;
   }

   public int firstContact() {
      return this.contacts.length == 0 ? -1 : this.contacts[0];
   }

   public static UltronMove byId(int id) {
      UltronMove[] all = values();
      return id >= 0 && id < all.length ? all[id] : NONE;
   }
}
