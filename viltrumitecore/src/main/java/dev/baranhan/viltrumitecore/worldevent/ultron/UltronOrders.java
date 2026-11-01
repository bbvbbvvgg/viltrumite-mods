package dev.baranhan.viltrumitecore.worldevent.ultron;

import static dev.baranhan.viltrumitecore.worldevent.ultron.UltronDirector.*;

import dev.baranhan.viltrumitecore.entity.ultron.UltronUnit;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronNetwork.Node;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The uprising's orders (docs/plans/2026-11-01-ultron-uprising.md): engineer jobs, guard posts, the
 * targets the network knows, the adaptive attack style, the role coordinator that hands every machine its
 * role and orders, the assault routes, capture targets and the doctrine. Called by {@link UltronDirector}.
 */
final class UltronOrders {
   private UltronOrders() {
   }


   /** Engineer work in priority order: the relocation site, disabled nodes, sites under construction, the next spread sites, damaged nodes. */
   static List<Integer> jobs(UltronSession s) {
      List<Integer> out = new ArrayList<>();
      Node reloc = s.network.get(s.relocationSite);
      if (reloc != null && (reloc.state == UltronNodeState.PLANNED || reloc.state == UltronNodeState.BUILDING)) {
         out.add(reloc.id);
      }
      for (Node n : s.network.all()) {
         if (n.state == UltronNodeState.DISABLED && !out.contains(n.id)) {
            out.add(n.id);
         }
      }
      for (Node n : s.network.all()) {
         if (n.state == UltronNodeState.BUILDING && !out.contains(n.id)) {
            out.add(n.id);
         }
      }
      if (s.phase.atLeast(UltronPhase.SPREAD)) {
         // the network grows outward: the planned sites closest to what already stands come first
         List<Node> planned = new ArrayList<>();
         for (Node n : s.network.all()) {
            if (n.state == UltronNodeState.PLANNED && UltronDirector.wave(s, n) == 1) {
               planned.add(n);
            }
         }
         planned.sort(Comparator.comparingDouble(n -> nearestStanding(s, n)));
         for (int i = 0; i < Math.min(2, planned.size()); i++) {
            out.add(planned.get(i).id);
         }
      }
      for (Node n : s.network.all()) {
         if (n.state == UltronNodeState.ONLINE && n.hullFraction() < 0.7F && !out.contains(n.id)) {
            out.add(n.id);
         }
      }
      return out;
   }

   static double nearestStanding(UltronSession s, Node to) {
      double best = Double.MAX_VALUE;
      for (Node n : s.network.all()) {
         if (n != to && n.standing()) {
            best = Math.min(best, n.distanceSq(to.x, to.y, to.z));
         }
      }
      return best;
   }

   static int sitesLeft(UltronSession s) {
      int c = 0;
      for (Node n : s.network.all()) {
         if (n.state == UltronNodeState.PLANNED && UltronDirector.wave(s, n) == 1) {
            c++;
         }
      }
      return c;
   }

   /** Posts the guards hold: the fortified node first, then the foundries and the core. */
   static List<Node> guardPosts(UltronSession s) {
      List<Node> out = new ArrayList<>();
      Node fort = s.network.get(s.adaptation.fortifiedNode());
      if (fort != null && fort.standing() && s.adaptation.active(UltronAdaptation.Protocol.FORTIFY)) {
         out.add(fort);
      }
      for (Node n : s.network.all()) {
         if (n.kind == UltronNodeKind.FOUNDRY && n.standing() && !out.contains(n)) {
            out.add(n);
         }
      }
      Node core = s.network.core();
      if (core != null && core.standing() && !out.contains(core)) {
         out.add(core);
      }
      return out;
   }

   /** Recent sightings by threat, without the sector REDISTRIBUTION pulls out of (unless nothing else is left). */
   static List<UltronIntel.Sighting> targets(UltronSession s) {
      List<UltronIntel.Sighting> all = new ArrayList<>();
      for (UltronIntel.Sighting t : s.intel.all()) {
         if (s.clock - t.tick <= UltronDirector.SIGHT_WINDOW) {
            all.add(t);
         }
      }
      all.sort((a, b) -> Float.compare(b.threat, a.threat));
      int focus = s.adaptation.active(UltronAdaptation.Protocol.REDISTRIBUTION) ? s.adaptation.focusSector() : -1;
      if (focus < 0) {
         return all;
      }
      List<UltronIntel.Sighting> out = new ArrayList<>();
      for (UltronIntel.Sighting t : all) {
         if (UltronIntel.sector(s.anchor.x, s.anchor.z, t.x, t.z) != focus) {
            out.add(t);
         }
      }
      return out.isEmpty() ? all : out;
   }

   static UltronScenario.AttackStyle style(UltronSession s) {
      UltronScenario.AttackStyle st = s.scenario.style();
      if (st != UltronScenario.AttackStyle.ADAPTIVE) {
         return st;
      }
      if (s.adaptation.active(UltronAdaptation.Protocol.REDISTRIBUTION)) {
         return UltronScenario.AttackStyle.INFILTRATION; // spread out of the killing ground
      }
      UltronScenario.AttackStyle[] turn = {UltronScenario.AttackStyle.INFILTRATION, UltronScenario.AttackStyle.COLUMNS, UltronScenario.AttackStyle.PURSUIT};
      return turn[Math.floorMod(s.styleTurn + (int)(s.seed & 3), turn.length)];
   }

   static void roles(ServerLevel level, UltronSession s) {
      List<UltronUnit> live = new ArrayList<>();
      for (UltronUnit u : s.units.values()) {
         if (u.isAlive() && !u.isRemoved()) {
            live.add(u);
         }
      }
      s.captureTargets = s.phase.atLeast(UltronPhase.MACHINE_WAR) ? s.capture.targetsNear(level, s) : 0;
      if (live.isEmpty()) {
         return;
      }
      live.sort(Comparator.comparingInt(u -> u.netId));
      List<Integer> jobs = jobs(s);
      List<Node> posts = guardPosts(s);
      UltronRoles.Needs needs = new UltronRoles.Needs(s.doctrine, posts.size(), s.adaptation.active(UltronAdaptation.Protocol.FORTIFY), jobs.size(),
         s.intel.aerial(s.clock, 30 * UltronRules.TPS), s.adaptation.active(UltronAdaptation.Protocol.ANTI_AIR), s.captureTargets,
         s.phase.hostile());
      List<UltronRoles.Unit> list = new ArrayList<>();
      for (UltronUnit u : live) {
         list.add(new UltronRoles.Unit(u.netId, u.kind(), u.role()));
      }
      Map<Integer, UltronRole> roles = UltronRoles.assign(list, needs);
      int[] count = new int[UltronRole.values().length];
      for (UltronUnit u : live) {
         count[roles.getOrDefault(u.netId, u.role()).ordinal()]++;
      }
      int[] index = new int[UltronRole.values().length];
      List<UltronIntel.Sighting> targets = targets(s);
      List<Node> standing = new ArrayList<>();
      for (Node n : s.network.all()) {
         if (n.standing()) {
            standing.add(n);
         }
      }
      List<UltronUnit> engineers = new ArrayList<>();
      for (UltronUnit u : live) {
         if (u.kind() == UltronUnitKind.ENGINEER && u.job >= 0) {
            engineers.add(u);
         }
      }
      Map<Integer, Integer> perJob = new HashMap<>();
      for (UltronUnit u : live) {
         UltronRole r = roles.getOrDefault(u.netId, u.role());
         u.setRole(r);
         int i = index[r.ordinal()]++;
         if (u.tickCount < u.alertUntil && r != UltronRole.REPAIR && r != UltronRole.CAPTURE) {
            continue; // an alert or a mark holds its orders
         }
         switch (r) {
            case GUARD -> {
               Node post = posts.isEmpty() ? null : posts.get(i % posts.size());
               double a = i * 2.39996;
               u.post = post == null ? s.anchor : new Vec3(post.x + Math.cos(a) * 6.0, post.y, post.z + Math.sin(a) * 6.0);
               u.goal = null;
               u.follow = null;
               u.job = -1;
            }
            case PATROL -> {
               Node n = standing.isEmpty() ? null : standing.get((i + s.clock / 600) % standing.size());
               u.post = n == null ? s.anchor : new Vec3(n.x, n.y, n.z);
               u.goal = null;
               u.follow = null;
               u.job = -1;
            }
            case SCOUT -> {
               u.follow = null;
               u.job = -1;
               UltronIntel.Sighting t = targets.isEmpty() ? null : targets.get(i % targets.size());
               if (t != null && (s.doctrine == UltronDoctrine.HUNT || i % 2 == 0)) {
                  u.goal = new Vec3(t.x, t.y + 6.0, t.z);
                  u.hunt = s.doctrine == UltronDoctrine.HUNT ? t.id : null;
               } else {
                  // sweep: each scout circles its own slice of the area
                  double a = (s.clock / 400.0) * 0.6 + u.netId * 2.39996;
                  double rr = s.radius * (0.35 + 0.5 * ((u.netId * 7 + s.clock / 400) % 5) / 4.0);
                  u.goal = new Vec3(s.anchor.x + Math.cos(a) * rr, s.anchor.y + 8.0, s.anchor.z + Math.sin(a) * rr);
                  u.hunt = null;
               }
            }
            case ASSAULT -> assault(s, u, i, count[r.ordinal()], targets);
            case REPAIR -> {
               u.follow = null;
               u.hunt = null;
               if (jobs.isEmpty()) {
                  u.job = -1;
                  Node core = s.network.core();
                  u.post = core != null && core.standing() ? new Vec3(core.x, core.y, core.z) : s.anchor;
                  u.goal = null;
               } else {
                  // keep the current job while it is still wanted; at most two engineers on one job
                  int job = jobs.contains(u.job) && perJob.getOrDefault(u.job, 0) < 2 ? u.job : -1;
                  if (job < 0) {
                     for (int k = 0; k < jobs.size(); k++) {
                        int candidate = jobs.get((i + k) % jobs.size());
                        if (perJob.getOrDefault(candidate, 0) < 2) {
                           job = candidate;
                           break;
                        }
                     }
                  }
                  if (job < 0) {
                     job = jobs.get(i % jobs.size());
                  }
                  perJob.merge(job, 1, Integer::sum);
                  Node n = s.network.get(job);
                  u.job = job;
                  u.goal = n == null ? null : new Vec3(n.x, n.y, n.z);
               }
            }
            case INTERCEPT -> {
               u.follow = null;
               u.job = -1;
               UltronIntel.Sighting best = null;
               double bestD = Double.MAX_VALUE;
               for (UltronIntel.Sighting t : targets) {
                  if (t.flying) {
                     double d = u.distanceToSqr(t.x, t.y, t.z);
                     if (d < bestD) {
                        bestD = d;
                        best = t;
                     }
                  }
               }
               if (best != null) {
                  u.goal = new Vec3(best.x, best.y + 2.0, best.z);
                  u.hunt = best.id;
               } else {
                  Node core = s.network.core();
                  Vec3 c = core != null && core.standing() ? new Vec3(core.x, core.y, core.z) : s.anchor;
                  double a = s.clock / 300.0 + u.netId;
                  u.goal = c.add(Math.cos(a) * 20.0, 18.0, Math.sin(a) * 20.0);
                  u.hunt = null;
               }
            }
            case ESCORT -> {
               u.job = -1;
               Entity boss = s.phase == UltronPhase.FINALE ? UltronDirector.boss(level, s) : null;
               if (boss != null && boss.isAlive()) {
                  u.follow = boss.getUUID();
               } else if (!engineers.isEmpty()) {
                  u.follow = engineers.get(i % engineers.size()).getUUID();
               } else {
                  u.follow = null;
                  Node n = standing.isEmpty() ? null : standing.get(i % standing.size());
                  u.post = n == null ? s.anchor : new Vec3(n.x, n.y, n.z);
               }
               u.goal = null;
            }
            case CAPTURE -> {
               u.follow = null;
               u.job = -1;
               LivingEntity t = captureTarget(level, s, u);
               u.hunt = t == null ? null : t.getUUID();
               u.goal = t == null ? null : t.position();
            }
         }
      }
   }

   static void assault(UltronSession s, UltronUnit u, int i, int n, List<UltronIntel.Sighting> targets) {
      UltronScenario.AttackStyle style = style(s);
      double[] routes = s.scenario.routes();
      int group = style == UltronScenario.AttackStyle.COLUMNS ? i * 2 / Math.max(1, n) : i;
      double route = routes.length == 0 ? 0.0 : routes[Math.floorMod(group, routes.length)];
      u.follow = null;
      u.job = -1;
      UltronIntel.Sighting t = null;
      if (!targets.isEmpty()) {
         t = switch (style) {
            case PURSUIT -> targets.get(0);
            case COLUMNS -> targets.get(Math.floorMod(group, Math.min(2, targets.size())));
            default -> targets.get(Math.floorMod(i, targets.size()));
         };
         if (s.doctrine == UltronDoctrine.HUNT) {
            t = targets.get(0);
         }
      }
      if (t != null) {
         Vec3 aim = new Vec3(t.x, t.y, t.z);
         // columns and infiltrators come in along their route, not straight down the middle
         if (style != UltronScenario.AttackStyle.PURSUIT && u.distanceToSqr(aim) > 30.0 * 30.0) {
            aim = aim.add(Math.cos(route) * 14.0, 0.0, Math.sin(route) * 14.0);
         }
         u.goal = aim;
         u.hunt = s.doctrine == UltronDoctrine.HUNT || style == UltronScenario.AttackStyle.PURSUIT ? t.id : null;
         return;
      }
      u.hunt = null;
      if (s.doctrine == UltronDoctrine.EXPANSION) {
         // nobody seen: cover the construction
         for (Node node : s.network.all()) {
            if (node.state == UltronNodeState.BUILDING || node.id == s.relocationSite) {
               u.goal = new Vec3(node.x + Math.cos(i) * 8.0, node.y, node.z + Math.sin(i) * 8.0);
               return;
            }
         }
      }
      // no intel: march the routes outward through the area (the districts in a city)
      double rr = s.radius * (0.55 + 0.35 * Math.floorMod(i * 37 + s.clock / 400, 7) / 6.0);
      double a = route;
      int focus = s.adaptation.active(UltronAdaptation.Protocol.REDISTRIBUTION) ? s.adaptation.focusSector() : -1;
      Vec3 p = new Vec3(s.anchor.x + Math.cos(a) * rr, s.anchor.y, s.anchor.z + Math.sin(a) * rr);
      if (focus >= 0 && UltronIntel.sector(s.anchor.x, s.anchor.z, p.x, p.z) == focus) {
         a += Math.PI * 0.5;
         p = new Vec3(s.anchor.x + Math.cos(a) * rr, s.anchor.y, s.anchor.z + Math.sin(a) * rr);
      }
      u.goal = p;
   }

   /** The nearest free golem / suit an engineer can take over (within a controller's hold). */
   @Nullable
   static LivingEntity captureTarget(ServerLevel level, UltronSession s, UltronUnit from) {
      LivingEntity best = null;
      double bestD = 64.0 * 64.0;
      for (Node n : s.network.all()) {
         if (!n.active() || !n.kind.controls()) {
            continue;
         }
         AABB box = new AABB(n.x, n.y, n.z, n.x, n.y, n.z).inflate(UltronRules.CAPTURE_RADIUS * 2.0);
         for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, s.capture::capturable)) {
            double d = from.distanceToSqr(e);
            if (d < bestD && s.capture.roomFor(e)) {
               bestD = d;
               best = e;
            }
         }
      }
      return best;
   }

   static void doctrine(ServerLevel level, UltronSession s) {
      if (!s.phase.atLeast(UltronPhase.SPREAD)) {
         return;
      }
      s.doctrineHeld += 100;
      int[] counts = UltronDirector.counts(s);
      int players = Math.max(1, UltronDirector.fighters(level, s).size());
      UltronStrategy.Situation sit = new UltronStrategy.Situation(s.phase, sitesLeft(s), counts[UltronUnitKind.ENGINEER.ordinal()], s.units.size(),
         UltronRules.aliveCap(s.phase, players), s.network.active(UltronNodeKind.FOUNDRY), s.network.activeControllers(), s.captureTargets,
         s.intel.topThreat(s.clock, 60 * UltronRules.TPS), s.intel.seen(s.clock, 60 * UltronRules.TPS), Math.round(s.recentLosses));
      UltronDoctrine next = UltronStrategy.decide(s.doctrine, s.doctrineHeld, sit, s.scenario);
      if (next == s.doctrine) {
         return;
      }
      UltronUprising.LOG.info("Ultron doctrine {} -> {}", s.doctrine.key(), next.key());
      s.doctrine = next;
      s.doctrineHeld = 0;
      s.dirty = true;
      s.saveDirty = true;
      if (s.phase.atLeast(UltronPhase.MACHINE_WAR)) {
         UltronVoice.say(level, s, "doctrine_" + next.key(), false);
      }
   }
}
