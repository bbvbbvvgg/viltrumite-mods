package dev.baranhan.viltrumitecore.hero.venom;

import dev.baranhan.viltrumitecore.compat.spider.SpiderCompat;
import dev.baranhan.viltrumitecore.compat.spider.SpiderLine;
import dev.baranhan.viltrumitecore.compat.spider.WebKind;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Venom against a Falbiks Spider-Man, real mechanics through {@link SpiderCompat} only (each one silently off when the
 * adapter could not bind its feature, and all of them off without Falbiks): claws and tendrils cut web lines, a
 * tendril climbs a line to its anchor and bites it through, webs are eaten in flight (food) or thrown back from the
 * shell, a cocoon is torn from the inside, a Spider is snatched off his line, aerial and facade clashes knock both
 * apart, and the symbiote stirs when a Spider is near.
 */
public final class VenomSpider {
   static final int WEB_FEEL_RANGE = 24;
   static final int COCOON_TEAR_TICKS = 15;

   private VenomSpider() {
   }

   /** Pure: the closest points of segments a..b and c..d ([0] on a..b, [1] on c..d). */
   public static Vec3[] closest(Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
      Vec3 u = b.subtract(a);
      Vec3 v = d.subtract(c);
      Vec3 w = a.subtract(c);
      double aa = u.dot(u), bb = u.dot(v), cc = v.dot(v), dd = u.dot(w), ee = v.dot(w);
      double den = aa * cc - bb * bb;
      double s = den < 1.0E-9 ? 0.0 : clamp01((bb * ee - cc * dd) / den);
      double t = cc < 1.0E-9 ? 0.0 : clamp01((bb * s + ee) / cc);
      s = aa < 1.0E-9 ? 0.0 : clamp01((bb * t - dd) / aa);
      return new Vec3[]{a.add(u.scale(s)), c.add(v.scale(t))};
   }

   private static double clamp01(double x) {
      return x < 0 ? 0 : x > 1 ? 1 : x;
   }

   /** Cuts every Spider line within {@code radius} of the segment; returns how many were cut. */
   static int cutLines(LivingEntity venom, VenomState state, Vec3 from, Vec3 to, double radius) {
      if (!SpiderCompat.present() || !(venom.level() instanceof net.minecraft.server.level.ServerLevel level)) {
         return 0;
      }
      int cut = 0;
      for (ServerPlayer p : level.players()) {
         if (p == venom || p.distanceToSqr(venom) > 64 * 64) {
            continue;
         }
         Optional<SpiderLine> line = SpiderCompat.line(p);
         if (line.isPresent() && line.get().distanceSqr(from, to) <= radius * radius && SpiderCompat.cutLine(p)) {
            cut++;
            Vec3[] c = closest(from, to, line.get().hand(), line.get().anchor());
            VenomFx.send(venom, VenomFx.WEB, c[1], null, 1.0F, 1);
            VenomSounds.playAt(venom, c[1], VenomSounds.WEB_TEAR, 1.0F, VenomSounds.jitter(venom, 1.05F));
            if (state != null) {
               state.hunger = VenomRules.feed(state.hunger, VenomRules.HUNGER_WEB / 2);
            }
         }
      }
      return cut;
   }

   /** A claw on a Spider becomes a clash when both are off the ground (in the air, on a line or on a wall). */
   static boolean clashes(LivingEntity venom, LivingEntity spider) {
      boolean venomUp = !venom.onGround() || venom instanceof Player vp && VenomHero.flag(vp, VenomFlags.WALL)
         || venom instanceof ServerPlayer sp && VenomState.of(sp) != null && VenomState.of(sp).wallTicks > 0;
      Vec3 wall = spider instanceof Player p ? SpiderCompat.wallNormal(p) : null;
      boolean spiderUp = !spider.onGround() || spider instanceof Player p && SpiderCompat.line(p).isPresent() || wall != null && wall.lengthSqr() > 0.01;
      return venomUp && spiderUp && venom.distanceToSqr(spider) <= VenomRules.CLASH_RANGE * VenomRules.CLASH_RANGE * 1.6;
   }

   static void clash(ServerPlayer venom, VenomState state, Player spider) {
      state.clashId = spider.getId();
      state.start(VenomMove.CLASH);
   }

   static void clashContact(ServerPlayer venom, VenomState state) {
      LivingEntity spider = VenomSkills.living(venom, state.clashId);
      state.clashId = -1;
      if (spider == null || !VenomTargets.mayHit(venom, spider)) {
         return;
      }
      Vec3 mid = VenomTargets.center(venom).add(VenomTargets.center(spider)).scale(0.5);
      Vec3 apart = VenomTargets.outward(venom.position(), spider);
      if (spider instanceof ServerPlayer sp) {
         SpiderCompat.cutLine(sp); // the clash tears him off his line
      }
      VenomTargets.strike(venom, spider, VenomRules.CLAW_DAMAGE[2]);
      VenomTargets.push(spider, apart.scale(1.2).add(0, 0.35, 0));
      venom.setDeltaMovement(apart.scale(-0.7).add(0, 0.3, 0));
      venom.hurtMarked = true;
      venom.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(venom));
      VenomFx.send(venom, VenomFx.CLASH, mid, null, 1.5F, 4, venom.getId(), spider.getId());
      VenomSounds.playAt(venom, mid, VenomSounds.CLASH, 1.3F, VenomSounds.jitter(venom, 0.95F));
      state.hunger = VenomRules.feed(state.hunger, VenomRules.HUNGER_WEB);
   }

   static void tearContact(LivingEntity venom, VenomState state) {
      if (SpiderCompat.tearWeb(venom)) {
         VenomFx.send(venom, VenomFx.WEB, VenomTargets.center(venom), null, 1.6F, 0);
         VenomSounds.play(venom, VenomSounds.WEB_TEAR, 1.2F, VenomSounds.jitter(venom, 0.85F));
         VenomSounds.play(venom, VenomSounds.SHRIEK, 0.7F, VenomSounds.jitter(venom, 1.2F));
         if (state != null) {
            state.hunger = VenomRules.feed(state.hunger, VenomRules.HUNGER_WEB);
         }
      }
   }

   /** A web the symbiote swallows (CATCH): food. */
   static void eat(ServerPlayer venom, VenomState state, Projectile web) {
      Vec3 at = web.position();
      web.discard();
      state.hunger = VenomRules.feed(state.hunger, VenomRules.HUNGER_WEB);
      VenomFx.send(venom, VenomFx.WEB, at, VenomTargets.center(venom), 0.8F, 0);
      VenomSounds.playAt(venom, at, VenomSounds.WEB_EAT, 1.0F, VenomSounds.jitter(venom, 1.0F));
   }

   /** The shell sends a web back at its Spider (REFLECT does the flight; this is the show). */
   static void thrownBack(ServerPlayer venom, VenomState state, Projectile web) {
      VenomFx.send(venom, VenomFx.WEB, web.position(), null, 0.6F, 0);
      VenomSounds.playAt(venom, web.position(), VenomSounds.SHELL, 0.8F, VenomSounds.jitter(venom, 1.2F));
   }

   static void tick(ServerPlayer venom, VenomState state) {
      if (!SpiderCompat.present() || !state.form) {
         state.spiderNear = false;
         state.cocoonTicks = 0;
         return;
      }
      // the symbiote feels a Spider near: it stirs (client), craves (hunger) and says so once in a while
      if (state.tickCount % 10 == 0) {
         boolean near = false;
         for (ServerPlayer p : venom.serverLevel().players()) {
            if (p != venom && p.distanceToSqr(venom) <= WEB_FEEL_RANGE * WEB_FEEL_RANGE && SpiderCompat.isSpider(p)) {
               near = true;
               break;
            }
         }
         if (near && !state.spiderNear) {
            HeroSpeech.say(venom, VenomLines.SPIDER);
            VenomSounds.play(venom, VenomSounds.SQUIRM, 0.6F, 1.25F);
         }
         state.spiderNear = near;
         if (near && state.tickCount % 20 == 0) {
            state.hunger = VenomRules.feed(state.hunger, 2);
         }
      }
      // a cocoon: the symbiote tears it from the inside after a moment
      if (SpiderCompat.cocooned(venom) || SpiderCompat.webStacks(venom) > 0) {
         state.cocoonTicks++;
         if (state.cocoonTicks >= COCOON_TEAR_TICKS && (state.move == null || state.move.cancellable(state.elapsed)) && !state.possessing()) {
            VenomSkills.interrupt(venom, state);
            state.start(VenomMove.TEAR_WEB);
            state.cocoonTicks = 0;
         }
      } else {
         state.cocoonTicks = 0;
      }
      // webs in flight: a tendril snatches the ones that come close (lash or claw running: thrown back at the Spider)
      if (state.move != null && state.move.locks() || state.possessing()) {
         return;
      }
      AABB box = venom.getBoundingBox().inflate(2.5);
      List<Projectile> webs = venom.level().getEntitiesOfClass(Projectile.class, box,
         p -> p.isAlive() && p.getOwner() != venom && SpiderCompat.isWebProjectile(p) && p.getDeltaMovement().lengthSqr() > 0.04);
      for (Projectile web : webs) {
         Vec3 to = VenomTargets.center(venom).subtract(web.position());
         if (to.dot(web.getDeltaMovement()) <= 0) {
            continue; // flying away
         }
         boolean striking = state.move != null && (state.move.isClaw() || state.move.isLash());
         if (striking && web.getOwner() != null && web.getOwner().isAlive()) {
            Vec3 back = VenomTargets.center(web.getOwner()).subtract(web.position()).normalize().scale(web.getDeltaMovement().length() * 1.1);
            web.setOwner(venom);
            web.setDeltaMovement(back);
            web.hurtMarked = true;
            VenomFx.send(venom, VenomFx.STICK, web.position(), VenomTargets.shoulder(venom, state.tendrilSide), 0.6F, 0);
            VenomSounds.playAt(venom, web.position(), VenomSounds.LASH, 0.8F, VenomSounds.jitter(venom, 1.3F));
         } else if (SpiderCompat.webKind(web) != WebKind.NET || state.hunger < VenomRules.HUNGER_MAX) {
            VenomFx.send(venom, VenomFx.STICK, web.position(), VenomTargets.shoulder(venom, state.tendrilSide), 0.5F, 0);
            eat(venom, state, web);
         }
         state.tendrilSide = -state.tendrilSide;
         break; // one per tick
      }
   }
}
