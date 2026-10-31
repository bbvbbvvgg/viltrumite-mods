package dev.baranhan.viltrumitecore.entity.avengers;

/**
 * Body actions of a summoned Avenger: synced as an int (ordinal, plus a restart serial) and played on the
 * client from the kind's clip file by {@link #clip()}. A kind only uses the actions its file has. Append only.
 */
public enum AvengerAction {
   NONE("", 0),
   /** Arrival in flight / falling (Iron Man thrusters, Quinjet jump, Thor in the bolt, Hulk mid-leap). */
   ARRIVE("arrive", 200),
   /** Landing pose (three-point landing, Hulk smash landing, Thor hammer down). */
   LAND("land", 20),
   /** The circle: individual signature (faceplate, chest beat, hammer spin, bow draw, pistols). */
   CIRCLE("circle", 80),
   ATTACK_1("attack_1", 12),
   ATTACK_2("attack_2", 12),
   ATTACK_3("attack_3", 16),
   SPECIAL_1("special_1", 24),
   SPECIAL_2("special_2", 30),
   SPECIAL_3("special_3", 40),
   LEAP("leap", 40),
   DODGE("dodge", 12),
   DEPART("depart", 90),
   SHOOT("shoot", 10),
   HURT("hurt", 8),
   /**
    * Joint moves (TeamMoves, rework §15), clip per kind: Natasha cups her hands / Clint springs off them and flips up;
    * Thor channels the lightning into Iron Man / Tony takes the charge.
    */
   TEAM_1("team_1", 40),
   /** Natasha's heave / Clint's shot from the air; Thor raises Mjolnir for the shield strike / Tony's charged unibeam. */
   TEAM_2("team_2", 30),
   /** Clint drops back down; Thor's hammer blow on Steve's shield. */
   TEAM_3("team_3", 24);

   private final String clip;
   public final int length;

   AvengerAction(String clip, int length) {
      this.clip = clip;
      this.length = length;
   }

   public String clip() {
      return this.clip;
   }

   public static AvengerAction byOrdinal(int i) {
      AvengerAction[] v = values();
      return i >= 0 && i < v.length ? v[i] : NONE;
   }
}
