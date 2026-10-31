package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.entity.MeteorEntity;
import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.hero.combat.CombatPresence;
import dev.baranhan.viltrumitecore.hero.combat.CombatTags;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.interaction.ContextualInteraction;
import dev.baranhan.viltrumitecore.interaction.InteractionContext;
import dev.baranhan.viltrumitecore.interaction.InteractionFx;
import dev.baranhan.viltrumitecore.interaction.InteractionOutcome;
import dev.baranhan.viltrumitecore.interaction.InteractionSounds;
import dev.baranhan.viltrumitecore.interaction.InteractionSpec;
import dev.baranhan.viltrumitecore.interaction.InteractionTrigger;
import dev.baranhan.viltrumitecore.interaction.InteractionTuning;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * A meteor falls on the Sword Saint with Reid drawn. He does not stop it and does not dodge it: one cut and the
 * rock parts in two halves that fly past him on either side of the cut and crater the ground there — he stands
 * untouched between the two craters. The star iron the meteor carried stays in one of the halves.
 */
public final class ReidCleavesMeteor implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("reid_cleaves_meteor", InteractionTrigger.PROJECTILE, 79, 20, 0);
   private static final Vec3 UP = new Vec3(0, 1, 0);

   @Override
   public InteractionSpec spec() {
      return SPEC;
   }

   /** Pure rule (tests): Reid drawn (striking or not) — the cut is drawn faster than a meteor falls. */
   public static boolean cleaves(CombatPresence p) {
      return p.has(CombatTags.WEAPON_SWORD) && p.has(CombatTags.TRAIT_ANNIHILATING);
   }

   /**
    * Pure rule (tests): the flight of one half. {@code side} is the horizontal normal of the cut, {@code sign} ±1:
    * the half turns {@link InteractionTuning#CLEAVE_ANGLE} away from the cut and keeps
    * {@link InteractionTuning#CLEAVE_SPEED} of the speed.
    */
   public static Vec3 half(Vec3 flight, Vec3 side, int sign) {
      double speed = flight.length();
      Vec3 dir = speed < 1.0E-4 ? new Vec3(0, -1, 0) : flight.scale(1.0 / speed);
      double a = Math.toRadians(InteractionTuning.CLEAVE_ANGLE);
      return dir.scale(Math.cos(a)).add(side.scale(sign * Math.sin(a))).normalize().scale(Math.max(0.5, speed) * InteractionTuning.CLEAVE_SPEED);
   }

   /** Pure rule (tests): the horizontal normal of the cut — across the flight, or across the look for a vertical fall. */
   public static Vec3 cutNormal(Vec3 flight, Vec3 look) {
      Vec3 n = flight.cross(UP);
      if (n.lengthSqr() < 1.0E-4) {
         n = look.cross(UP);
      }
      if (n.lengthSqr() < 1.0E-4) {
         n = new Vec3(1, 0, 0);
      }
      return new Vec3(n.x, 0, n.z).normalize();
   }

   @Override
   public boolean matches(InteractionContext ctx) {
      return ctx.projectile() instanceof MeteorEntity meteor && ctx.target() instanceof ServerPlayer saint && saint.isAlive()
         && meteor.getOwner() != saint && cleaves(ctx.targetPresence());
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      MeteorEntity meteor = (MeteorEntity)ctx.projectile();
      ServerPlayer saint = (ServerPlayer)ctx.target();
      Vec3 flight = meteor.getDeltaMovement();
      Vec3 side = cutNormal(flight, saint.getLookAngle());
      Vec3 at = meteor.position();
      for (int sign = -1; sign <= 1; sign += 2) {
         MeteorEntity half = new MeteorEntity(ViltrumiteEntities.METEOR.get(), ctx.level());
         Vec3 p = at.add(side.scale(sign * 1.6));
         half.setPos(p.x, p.y, p.z);
         half.setOwner(saint);
         half.setDropsLoot(sign > 0 && meteor.dropsLoot());
         half.setDeltaMovement(half(flight, side, sign));
         ctx.level().addFreshEntity(half);
      }
      HeroFx.preset(ctx.level(), InteractionFx.METEOR_CLEAVE, at, saint.getEyePosition(), 1.3F, null);
      InteractionSounds.playAt(saint, at, InteractionSounds.METEOR_CLEAVE, 3.0F, 1.0F);
      meteor.discard();
      return InteractionOutcome.CONSUMED;
   }
}
