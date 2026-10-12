package dev.baranhan.viltrumitecore.hero.reinhard;

import dev.baranhan.viltrumitecore.hero.HeroDebris;
import javax.annotation.Nullable;

/**
 * Spec §5: how much a mode may break. Restrained never breaks a block, Knight
 * tears only the top layers with few flying blocks, Holy Sword is massive. Every
 * block change still goes through {@code HeroDestruction.canDestroy}
 * (mobGriefing, unbreakable blocks) inside {@link HeroDebris#erupt}. Pure.
 */
public enum DestructionLevel {
   NONE,
   MODERATE,
   MASSIVE;

   /** Knight caps: top layer(s), small radius, at most this many flying blocks (plan risk: ≤ 40). */
   public static final double MODERATE_MAX_DEPTH = 1.0;
   public static final double MODERATE_MAX_RADIUS = 4.0;
   public static final int MODERATE_MAX_FLYING = 40;

   public static DestructionLevel of(ReinhardMode mode) {
      return switch (mode) {
         case RESTRAINED -> NONE;
         case KNIGHT -> MODERATE;
         case HOLY_SWORD -> MASSIVE;
      };
   }

   public boolean mayBreak() {
      return this != NONE;
   }

   /** The eruption this level allows: none, the spec clamped to the Knight caps, or the spec unchanged. */
   @Nullable
   public HeroDebris.Eruption scaled(HeroDebris.Eruption spec) {
      return switch (this) {
         case NONE -> null;
         case MODERATE -> new HeroDebris.Eruption(Math.min(spec.radius(), MODERATE_MAX_RADIUS), Math.min(spec.depth(), MODERATE_MAX_DEPTH), spec.ahead(),
            Math.min(spec.maxFlying(), MODERATE_MAX_FLYING), spec.speed(), spec.power());
         case MASSIVE -> spec;
      };
   }
}
