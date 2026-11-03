package dev.baranhan.viltrumitecore.compat.spider;

/**
 * What the Spider-Man of another mod lets Venom do (docs/venom/spiderman-compatibility.md §4.2). Our stable contract:
 * values are appended, never removed or renamed. Each one is switched on only when the bound adapter found every
 * member it needs, and switched off for the session after the first failure.
 */
public enum SpiderFeature {
   /** Tell that a player is that mod's Spider-Man. */
   DETECT_HERO,
   /** Read a swinging Spider's web line (hand, anchor, rope length). */
   LINE_STATE,
   /** End a swing from outside (a claw or a tendril through the line). */
   CUT_LINE,
   /** Recognise that mod's web projectiles and their kind. */
   WEB_PROJECTILE,
   /** Read web stacks and the cocoon on a body. */
   WEB_STATUS,
   /** Tear webbing and the cocoon off a body. */
   TEAR_WEB,
   /** Release every ability key a possessed Spider still holds. */
   INPUT_RELEASE,
   /** Press a possessed Spider's own abilities (host takeover, extended mode). */
   DRIVE_ABILITIES,
   /** Drop the possessed Spider's own ability packets on the server (its network channel is known). */
   INPUT_GATE,
   /** Read whether a Spider clings to a wall. */
   WALL_STATE
}
