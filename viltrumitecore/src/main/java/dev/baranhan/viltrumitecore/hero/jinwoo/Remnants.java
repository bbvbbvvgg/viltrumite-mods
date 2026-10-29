package dev.baranhan.viltrumitecore.hero.jinwoo;

import dev.baranhan.viltrumitecore.entity.jinwoo.ShadowKind;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Soul remnants (spec §5.7): a creature killed by Jinwoo or his shadow leaves one for
 * {@link JinwooRules#REMNANT_LIFE} ticks, at most {@link JinwooRules#REMNANT_MAX} per
 * level (FIFO), three extraction attempts each. Server only, never saved.
 */
public final class Remnants {
   public static final class Remnant {
      public final int id;
      public final Vec3 pos;
      public final ShadowKind kind;
      public final JinwooRules.ShadowTier tier;
      public final UUID killer;
      public int life = JinwooRules.REMNANT_LIFE;
      public int failures;
      public boolean busy;

      Remnant(int id, Vec3 pos, ShadowKind kind, JinwooRules.ShadowTier tier, UUID killer) {
         this.id = id;
         this.pos = pos;
         this.kind = kind;
         this.tier = tier;
         this.killer = killer;
      }
   }

   private static final Map<ResourceKey<Level>, Deque<Remnant>> LEVELS = new HashMap<>();
   private static int nextId = 1;

   private Remnants() {
   }

   private static Deque<Remnant> of(ServerLevel level) {
      return LEVELS.computeIfAbsent(level.dimension(), k -> new ArrayDeque<>());
   }

   public static Remnant add(ServerLevel level, Vec3 pos, ShadowKind kind, JinwooRules.ShadowTier tier, UUID killer) {
      Deque<Remnant> list = of(level);
      while (list.size() >= JinwooRules.REMNANT_MAX) {
         Remnant old = list.pollFirst();
         if (old != null) {
            JinwooFx.near(level, old.pos, 96.0, JinwooFx.REMNANT_REMOVE, -1, old.id, old.pos, new Vec3(-1, 0, 0));
         }
      }
      Remnant remnant = new Remnant(nextId++, pos, kind, tier, killer);
      list.addLast(remnant);
      JinwooFx.near(level, pos, 96.0, JinwooFx.REMNANT_ADD, -1, remnant.id, pos, new Vec3(kind.ordinal(), tier.ordinal(), 0));
      return remnant;
   }

   /** Nearest free remnant within the radius, or null. */
   @Nullable
   public static Remnant nearest(ServerLevel level, Vec3 at, double radius) {
      Deque<Remnant> list = LEVELS.get(level.dimension());
      if (list == null) {
         return null;
      }
      Remnant best = null;
      double bestD = radius * radius;
      for (Remnant r : list) {
         double d = r.pos.distanceToSqr(at);
         if (!r.busy && d <= bestD) {
            best = r;
            bestD = d;
         }
      }
      return best;
   }

   @Nullable
   public static Remnant byId(ServerLevel level, int id) {
      Deque<Remnant> list = LEVELS.get(level.dimension());
      if (list != null) {
         for (Remnant r : list) {
            if (r.id == id) {
               return r;
            }
         }
      }
      return null;
   }

   /** outcome: 1 extracted, 0 crumbled, -1 expired. */
   public static void remove(ServerLevel level, Remnant remnant, int outcome) {
      Deque<Remnant> list = LEVELS.get(level.dimension());
      if (list != null && list.remove(remnant)) {
         JinwooFx.near(level, remnant.pos, 96.0, JinwooFx.REMNANT_REMOVE, -1, remnant.id, remnant.pos, new Vec3(outcome, 0, 0));
      }
   }

   public static int count(ServerLevel level) {
      Deque<Remnant> list = LEVELS.get(level.dimension());
      return list == null ? 0 : list.size();
   }

   public static void tick(ServerLevel level) {
      Deque<Remnant> list = LEVELS.get(level.dimension());
      if (list == null || list.isEmpty()) {
         return;
      }
      Iterator<Remnant> it = list.iterator();
      while (it.hasNext()) {
         Remnant r = it.next();
         if (!r.busy && --r.life <= 0) {
            it.remove();
            JinwooFx.near(level, r.pos, 96.0, JinwooFx.REMNANT_REMOVE, -1, r.id, r.pos, new Vec3(-1, 0, 0));
         }
      }
   }

   public static void clearAll() {
      LEVELS.clear();
   }
}
