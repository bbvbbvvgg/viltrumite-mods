package dev.baranhan.viltrumitecore.client.regulus;

import dev.baranhan.viltrumitecore.client.anim.pose.ActionClock;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusFlags;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusMove;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;

/** Read-only client view of the synced Regulus snapshot (layout: {@link RegulusFlags}). */
public final class RegulusView {
   private static final Map<Entity, ActionClock> CLOCKS = new WeakHashMap<>();

   private RegulusView() {
   }

   /** Regulus snapshot of this entity, or null. */
   @Nullable
   public static HeroPublicSnapshot of(@Nullable Entity entity) {
      if (entity instanceof HeroPlayer heroPlayer) {
         HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
         if (snapshot != null && snapshot.heroId() == HeroId.REGULUS) {
            return snapshot;
         }
      }

      return null;
   }

   @Nullable
   public static RegulusMove move(HeroPublicSnapshot snapshot) {
      return snapshot.actionId() < 0 ? null : RegulusMove.byId(snapshot.actionId());
   }

   public static boolean flag(HeroPublicSnapshot snapshot, int bit) {
      return RegulusFlags.has(snapshot.heroFlags(), bit);
   }

   public static int extra(HeroPublicSnapshot snapshot, int index) {
      return snapshot.extraCooldown(index);
   }

   public static boolean lion(HeroPublicSnapshot snapshot) {
      return flag(snapshot, RegulusFlags.LION);
   }

   /** Heart strain 0..1000. */
   public static int strain(HeroPublicSnapshot snapshot) {
      return snapshot.resource();
   }

   /** Entity id of the heart's host, or -1. */
   public static int hostId(HeroPublicSnapshot snapshot) {
      return snapshot.controlTargetId();
   }

   /** Entity id of the ultimate's target while Kingdom of One plays, or -1. */
   public static int ultTargetId(HeroPublicSnapshot snapshot) {
      return flag(snapshot, RegulusFlags.ULT) ? snapshot.resource2() : -1;
   }

   /** Render clock of the current move in ticks (shared bounded predictive {@link ActionClock}). */
   public static double moveTicks(Entity entity, HeroPublicSnapshot snapshot, float partialTick) {
      if (snapshot.actionId() < 0) {
         ActionClock clock = CLOCKS.get(entity);
         if (clock != null) {
            clock.reset();
         }

         return 0.0;
      }

      ActionClock clock = CLOCKS.computeIfAbsent(entity, e -> new ActionClock());
      return clock.sample(snapshot.actionId(), 0, snapshot.actionElapsed(), snapshot.actionLength(), entity.tickCount + partialTick);
   }

   public static void forget(Entity entity) {
      CLOCKS.remove(entity);
   }
}
