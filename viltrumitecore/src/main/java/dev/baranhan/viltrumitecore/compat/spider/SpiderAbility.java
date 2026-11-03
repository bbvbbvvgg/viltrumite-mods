package dev.baranhan.viltrumitecore.compat.spider;

/**
 * Abilities of a possessed Spider that Venom may press (extended mode). Their ultimate is deliberately not driven: a
 * borrowed body should not carry the victim's strongest move. {@code slot} is the other mod's slot name.
 */
public enum SpiderAbility {
   SWING("ABILITY_1"),
   NET("ABILITY_2"),
   YANK("ABILITY_3"),
   FLIP("SUMMON");

   public final String slot;

   SpiderAbility(String slot) {
      this.slot = slot;
   }
}
