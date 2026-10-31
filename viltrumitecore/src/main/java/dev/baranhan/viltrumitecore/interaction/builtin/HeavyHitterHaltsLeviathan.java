package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.entity.avengers.AvengerEntity;
import dev.baranhan.viltrumitecore.entity.avengers.AvengerKind;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriLeviathan;
import dev.baranhan.viltrumitecore.hero.combat.CombatPresence;
import dev.baranhan.viltrumitecore.hero.combat.CombatTags;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterTags;
import dev.baranhan.viltrumitecore.interaction.ContextualInteraction;
import dev.baranhan.viltrumitecore.interaction.InteractionContext;
import dev.baranhan.viltrumitecore.interaction.InteractionFx;
import dev.baranhan.viltrumitecore.interaction.InteractionOutcome;
import dev.baranhan.viltrumitecore.interaction.InteractionSounds;
import dev.baranhan.viltrumitecore.interaction.InteractionSpec;
import dev.baranhan.viltrumitecore.interaction.InteractionTargets;
import dev.baranhan.viltrumitecore.interaction.InteractionTrigger;
import dev.baranhan.viltrumitecore.interaction.InteractionTuning;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * "Hulk… smash." A Leviathan meets a fist that can stop it: the summoned Hulk, the Hulkbuster or a Viltrumite. The
 * punch halts the beast (staggered {@link InteractionTuning#HALT_STAGGER} ticks, thrown out of its dive), the
 * shock of it throws every Chitauri off the plates around the point of impact, and the striker rebounds off the
 * armour. Only a real hand-to-hand blow counts; the hit itself lands as usual.
 */
public final class HeavyHitterHaltsLeviathan implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("heavy_hitter_halts_leviathan", InteractionTrigger.HURT, 54, 160, 0);

   @Override
   public InteractionSpec spec() {
      return SPEC;
   }

   /** Pure rule (tests): the fists heavy enough — the mech, a Viltrumite body, or the summoned Hulk. */
   public static boolean heavy(CombatPresence striker, @Nullable AvengerKind npc) {
      return npc == AvengerKind.HULK || striker.has(HulkbusterTags.BODY_MECH_HEAVY) || striker.has(CombatTags.BODY_VILTRUMITE);
   }

   static boolean heavy(InteractionContext ctx) {
      Entity a = ctx.attacker();
      return heavy(ctx.attackerPresence(), a instanceof AvengerEntity avenger ? avenger.kind() : null);
   }

   @Override
   public boolean matches(InteractionContext ctx) {
      return ctx.melee() && ctx.amount() >= InteractionTuning.HALT_MIN_DAMAGE && Leviathans.of(ctx.target()) != null && heavy(ctx);
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      ChitauriLeviathan lev = Leviathans.of(ctx.target());
      if (!lev.staggerBy(InteractionTuning.HALT_STAGGER)) {
         return InteractionOutcome.PASS; // already reeling: an ordinary hit
      }
      Entity striker = ctx.attacker();
      Vec3 contact = Leviathans.nearest(lev, striker.getEyePosition());
      for (LivingEntity rider : Leviathans.riders(lev, InteractionTuning.HALT_RADIUS, 32)) {
         if (rider.getBoundingBox().distanceToSqr(contact) <= InteractionTuning.HALT_RADIUS * InteractionTuning.HALT_RADIUS) {
            InteractionTargets.push(rider, InteractionTargets.away(contact, rider.position(), lev.heading()).scale(InteractionTuning.HALT_PUSH).add(0, 0.45, 0));
         }
      }
      if (striker instanceof LivingEntity l) {
         InteractionTargets.push(l, InteractionTargets.away(contact, l.position(), l.getLookAngle().reverse()).scale(InteractionTuning.HALT_RECOIL).add(0, 0.3, 0));
      }
      HeroFx.preset(ctx.level(), InteractionFx.LEVIATHAN_HALT, contact, striker.getEyePosition(), 1.4F, null);
      InteractionSounds.playAt(lev, contact, InteractionSounds.LEVIATHAN_HALT, 5.0F, 1.0F);
      return InteractionOutcome.scaled(1.0F);
   }
}
