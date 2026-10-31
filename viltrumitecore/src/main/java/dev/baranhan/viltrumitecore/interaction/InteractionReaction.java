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
   CLASH_RECOIL,
   /** An energy bolt split on the raised guard (the guard's own block: charge, parry window). */
   GUARD_DEFLECTED,
   /** A titan's blow crushed the raised guard: broken like {@link #GUARD_SHATTERED}, but the bearer stands up to it. */
   GUARD_CRUSHED,
   /** A blade that cuts what cannot be cut met a body whose time is stopped: the stopped heart takes the strain. */
   LION_STRAINED,
   /** A falling meteor was caught bare-handed: the catcher braces and throws it on. */
   METEOR_CAUGHT
}
