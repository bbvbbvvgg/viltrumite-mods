package dev.baranhan.viltrumitecore.balance;

/**
 * Power tier of a hero in the global balance model
 * (docs/balance/2026-10-31-global-rebalance.md). Ordered from the strongest:
 * {@link #ordinal()} 0 is the absolute top. Shared code compares tiers only
 * through {@link #above}; it never asks which hero holds a tier.
 */
public enum HeroTier {
   /** The absolute leader (S+ inside S). */
   S_PLUS,
   S,
   A_PLUS,
   A,
   /** Base form below A, the ultimate lifts the hero to A. */
   A_ON_ULTIMATE,
   B_PLUS,
   B,
   /** No hero (a plain human): vanilla rules, no support system. */
   NONE;

   /** Strictly stronger than {@code other}. */
   public boolean above(HeroTier other) {
      return this.ordinal() < other.ordinal();
   }
}
