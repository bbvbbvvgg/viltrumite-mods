package dev.baranhan.viltrumitecore.hero;

/**
 * Legacy-kit chassis numbers of a hero ({@link HeroDefinition#chassisStats}).
 * They replace the synced Viltrumite stats ({@code ViltrumiteStatHolder}) for
 * this hero: base strength (attack damage bonus and the kit punch/chop/clap
 * base), incoming damage reduction in percent and the ignore threshold.
 */
public record HeroChassis(float baseDamage, float damageReduction, float damageIgnoreThreshold) {
}
