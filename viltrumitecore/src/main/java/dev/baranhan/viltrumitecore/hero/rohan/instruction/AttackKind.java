package dev.baranhan.viltrumitecore.hero.rohan.instruction;

import dev.baranhan.viltrumitecore.entity.directive.DirectiveEffect;
import javax.annotation.Nullable;

/** The kinds of attack {@code no_attack} can forbid. */
public enum AttackKind {
   MELEE(DirectiveEffect.NO_MELEE),
   RANGED(DirectiveEffect.NO_RANGED),
   ABILITIES(DirectiveEffect.NO_ABILITIES);

   public final DirectiveEffect effect;

   AttackKind(DirectiveEffect effect) {
      this.effect = effect;
   }

   @Nullable
   public static AttackKind byIndex(int i) {
      return i >= 0 && i < values().length ? values()[i] : null;
   }
}
