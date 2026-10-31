package dev.baranhan.viltrumitecore.interaction;

/**
 * A hero's own side of a contextual interaction, answered by
 * {@link dev.baranhan.viltrumitecore.hero.HeroDefinition#react}: shared interaction code never names a hero, it
 * asks the participant to play its reaction (pose, state, line). Numbers live in {@link InteractionTuning}.
 */
public enum InteractionReaction {
   /** A world-severing blade broke the raised guard: the guard is gone for {@link InteractionTuning#SHATTER_TICKS}. */
   GUARD_SHATTERED,
   /** A lightning weapon struck the raised guard: brace under the blow, the charge goes into the guard. */
   GUARD_BRACED,
   /** The striker's side of {@link #GUARD_BRACED}: the weapon recoils off the guard. */
   STRIKE_RECOILED,
   /** A meteor burst on the raised guard: braced, guard fully charged. */
   METEOR_STOPPED,
   /** The lightning weapon in hand shattered a meteor. */
   METEOR_SHATTERED,
   /** Lightning poured into powered armour: reactor overcharged. */
   OVERCHARGED,
   /** Two divine weapons met mid-strike: both strikers recoil. */
   CLASH_RECOIL
}
