package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.entity.MeteorEntity;
import dev.baranhan.viltrumitecore.entity.jinwoo.ShadowSoldierEntity;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.interaction.ContextualInteraction;
import dev.baranhan.viltrumitecore.interaction.InteractionContext;
import dev.baranhan.viltrumitecore.interaction.InteractionFx;
import dev.baranhan.viltrumitecore.interaction.InteractionOutcome;
import dev.baranhan.viltrumitecore.interaction.InteractionSounds;
import dev.baranhan.viltrumitecore.interaction.InteractionSpec;
import dev.baranhan.viltrumitecore.interaction.InteractionTrigger;
import dev.baranhan.viltrumitecore.interaction.InteractionTuning;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shadow Exchange under a meteor. A meteor about to hit the Shadow Monarch while his shadows are out: he swaps
 * places with the shadow standing furthest from the impact, the shadow takes the meteor where he stood and
 * dissolves back into his shadow (it returns to the roster as usual), and the meteor bursts there without a
 * crater — the blast throws everyone but him and his army.
 */
public final class ShadowExchangeMeteor implements ContextualInteraction {
   public static final InteractionSpec SPEC = new InteractionSpec("shadow_exchange_meteor", InteractionTrigger.PROJECTILE, 77, 20, 0);

   @Override
   public InteractionSpec spec() {
      return SPEC;
   }

   /** Pure rule (tests): a shadow is a refuge when it stands beyond the meteor's blast and inside the exchange reach. */
   public static boolean refuge(double fromImpact, double fromMonarch) {
      return fromImpact > InteractionTuning.METEOR_BLAST_RADIUS && fromMonarch <= InteractionTuning.EXCHANGE_RANGE;
   }

   @Nullable
   static ShadowSoldierEntity refugeOf(ServerPlayer monarch, Vec3 impact) {
      ShadowSoldierEntity best = null;
      double far = -1.0;
      AABB box = monarch.getBoundingBox().inflate(InteractionTuning.EXCHANGE_RANGE);
      for (ShadowSoldierEntity s : monarch.level().getEntitiesOfClass(ShadowSoldierEntity.class, box,
         s -> s.isAlive() && s.ownerId() == monarch.getId() && !s.hidden() && !s.dissolving())) {
         double d = s.position().distanceTo(impact);
         if (refuge(d, s.position().distanceTo(monarch.position())) && d > far) {
            far = d;
            best = s;
         }
      }
      return best;
   }

   @Override
   public boolean matches(InteractionContext ctx) {
      return ctx.projectile() instanceof MeteorEntity meteor && ctx.target() instanceof ServerPlayer monarch && monarch.isAlive()
         && meteor.getOwner() != monarch && !HeroDamage.isAnchored(monarch) && refugeOf(monarch, meteor.position()) != null;
   }

   @Override
   public InteractionOutcome apply(InteractionContext ctx) {
      MeteorEntity meteor = (MeteorEntity)ctx.projectile();
      ServerPlayer monarch = (ServerPlayer)ctx.target();
      ShadowSoldierEntity shadow = refugeOf(monarch, meteor.position());
      if (shadow == null) {
         return InteractionOutcome.PASS;
      }
      Vec3 mine = monarch.position();
      Vec3 theirs = shadow.position();
      monarch.teleportTo(theirs.x, theirs.y, theirs.z);
      monarch.fallDistance = 0.0F;
      shadow.teleportTo(mine.x, mine.y, mine.z);
      HeroFx.preset(ctx.level(), InteractionFx.SHADOW_EXCHANGE, mine.add(0, 1.0, 0), theirs, 1.0F, null);
      HeroFx.preset(ctx.level(), InteractionFx.SHADOW_EXCHANGE, theirs.add(0, 1.0, 0), mine, 1.0F, null);
      InteractionSounds.playAt(monarch, theirs, InteractionSounds.SHADOW_EXCHANGE, 1.6F, 1.0F);
      InteractionSounds.playAt(shadow, mine, InteractionSounds.SHADOW_EXCHANGE, 2.6F, 0.9F);
      Vec3 at = mine.add(0, 1.0, 0);
      HeroFx.preset(ctx.level(), InteractionFx.METEOR_BURST, at, meteor.position(), 1.2F, null);
      shadow.dissolve();
      MeteorBursts.burst(meteor, monarch, at);
      return InteractionOutcome.CONSUMED;
   }
}
