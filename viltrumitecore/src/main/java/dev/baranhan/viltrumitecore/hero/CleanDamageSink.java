package dev.baranhan.viltrumitecore.hero;

import net.minecraft.world.damagesource.DamageSource;

/**
 * An entity whose durability lives outside its health (an Ultron node keeps its hull in the event's network):
 * {@link HeroDamage#applyCleanDamage} hands the hit over with its source instead of writing the health, which
 * the entity would only restore.
 */
public interface CleanDamageSink {
   /** Server side, after the hero damage layers; the amount is already final. */
   void cleanDamage(DamageSource source, float amount);
}
