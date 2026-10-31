package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.entity.MeteorEntity;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.combat.CombatTags;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.interaction.ContextualInteraction;
import dev.baranhan.viltrumitecore.interaction.InteractionContext;
import dev.baranhan.viltrumitecore.interaction.InteractionFx;
import dev.baranhan.viltrumitecore.interaction.InteractionOutcome;
import dev.baranhan.viltrumitecore.interaction.InteractionReaction;
import dev.baranhan.viltrumitecore.interaction.InteractionSounds;
import dev.baranhan.viltrumitecore.interaction.InteractionSpec;
import dev.baranhan.viltrumitecore.interaction.InteractionTargets;
import dev.baranhan.viltrumitecore.interaction.InteractionTrigger;
import dev.baranhan.viltrumitecore.interaction.InteractionTuning;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * A meteor hits a Viltrumite. He catches it bare-handed, is shoved back by the mass of it and throws it on along
 * his aim, faster than it came. The meteor is his now: it craters where he threw it (a Leviathan in the way takes
 * it — {@code meteor_downs_leviathan} credits him).
 */
public final class ViltrumiteHurlsMeteor implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("viltrumite_hurls_meteor", InteractionTrigger.PROJECTILE, 78, 20, 0);

   @Override
   public InteractionSpec spec() {
      return SPEC;
   }

   /** Pure rule (tests): the throw — along the aim, never into his own feet, at least as fast as it came. */
   public static Vec3 hurl(Vec3 aim, Vec3 incoming) {
      Vec3 a = aim.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : aim.normalize();
      Vec3 dir = new Vec3(a.x, Math.max(a.y, 0.15), a.z).normalize();
      return dir.scale(Math.max(InteractionTuning.HURL_SPEED, incoming.length()));
   }

   @Override
   public boolean matches(InteractionContext ctx) {
      return ctx.projectile() instanceof MeteorEntity meteor && ctx.target() instanceof ServerPlayer v && v.isAlive() && meteor.getOwner() != v
         && ctx.targetPresence().has(CombatTags.BODY_VILTRUMITE);
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      MeteorEntity meteor = (MeteorEntity)ctx.projectile();
      ServerPlayer v = (ServerPlayer)ctx.target();
      if (!HeroRegistry.get(v).react(v, InteractionReaction.METEOR_CAUGHT, ctx)) {
         return InteractionOutcome.PASS;
      }
      Vec3 incoming = meteor.getDeltaMovement();
      Vec3 at = meteor.position();
      Vec3 throwV = hurl(v.getLookAngle(), incoming);
      Vec3 from = v.getEyePosition().add(throwV.normalize().scale(2.5));
      meteor.setPos(from.x, from.y, from.z);
      meteor.setOwner(v);
      meteor.setDeltaMovement(throwV);
      meteor.hurtMarked = true;
      Vec3 recoil = incoming.lengthSqr() < 1.0E-4 ? Vec3.ZERO : incoming.normalize().scale(InteractionTuning.HURL_RECOIL);
      InteractionTargets.push(v, recoil);
      HeroFx.preset(ctx.level(), InteractionFx.METEOR_HURL, at, from, 1.2F, null);
      InteractionSounds.playAt(v, at, InteractionSounds.METEOR_HURL, 3.0F, 1.0F);
      return InteractionOutcome.CONSUMED;
   }
}
