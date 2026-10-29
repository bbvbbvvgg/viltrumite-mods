package dev.baranhan.viltrumitecore.client.sinclair;

import dev.baranhan.viltrumitecore.client.anim.pose.ActionClock;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairFlags;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairMove;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;

/** Read-only client view of the synced Sinclair snapshot (layout: {@link SinclairFlags}). */
public final class SinclairView {
   private static final Map<Entity, ActionClock> CLOCKS = new WeakHashMap<>();

   private SinclairView() {
   }

   /** Sinclair snapshot of this entity, or null. */
   @Nullable
   public static HeroPublicSnapshot of(@Nullable Entity entity) {
      if (entity instanceof HeroPlayer heroPlayer) {
         HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
         if (snapshot != null && snapshot.heroId() == HeroId.SINCLAIR) {
            return snapshot;
         }
      }

      return null;
   }

   @Nullable
   public static SinclairMove move(HeroPublicSnapshot snapshot) {
      return snapshot.actionId() < 0 ? null : SinclairMove.byId(snapshot.actionId());
   }

   public static boolean flag(HeroPublicSnapshot snapshot, int bit) {
      return SinclairFlags.has(snapshot.heroFlags(), bit);
   }

   public static int extra(HeroPublicSnapshot snapshot, int index) {
      return snapshot.extraCooldown(index);
   }

   /** Shape of the captured or playing act, or null. */
   @Nullable
   public static ActShape shape(HeroPublicSnapshot snapshot) {
      int shape = extra(snapshot, SinclairFlags.EXTRA_SHAPE) - 1;
      return shape < 0 || shape >= ActShape.values().length ? null : ActShape.values()[shape];
   }

   /** Act colour (RGB) of the captured or playing act; the Sinclair violet without one. */
   public static int color(HeroPublicSnapshot snapshot) {
      int color = extra(snapshot, SinclairFlags.EXTRA_COLOR);
      return color == 0 ? SinclairPalette.GHOST : color;
   }

   /**
    * Render clock of the current move in ticks: the shared bounded predictive {@link ActionClock}
    * (never rewinds, at most 3 ticks ahead of the synced elapsed).
    */
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
