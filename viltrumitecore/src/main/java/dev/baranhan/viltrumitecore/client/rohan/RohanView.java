package dev.baranhan.viltrumitecore.client.rohan;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.rohan.RohanFlags;
import dev.baranhan.viltrumitecore.hero.rohan.RohanMove;
import dev.baranhan.viltrumitecore.hero.rohan.RohanRules;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;

/**
 * Read-only client view of Rohan's public snapshot: actionId = {@link RohanMove} ordinal, resource = ink,
 * resource2 = inspiration, heroFlags = {@link RohanFlags} bit masks (not bit indices: use {@link #flag}),
 * controlTargetId = the body he reads or aims at, actionTarget = where the air sketch hangs.
 */
public final class RohanView {
   private RohanView() {
   }

   @Nullable
   public static HeroPublicSnapshot of(@Nullable Entity entity) {
      if (entity instanceof HeroPlayer hp) {
         HeroPublicSnapshot s = hp.getHeroSnapshot();
         if (s != null && s.heroId() == HeroId.ROHAN) {
            return s;
         }
      }
      return null;
   }

   @Nullable
   public static RohanMove move(HeroPublicSnapshot s) {
      return RohanMove.byId(s.actionId());
   }

   public static boolean flag(HeroPublicSnapshot s, int mask) {
      return RohanFlags.has(s.heroFlags(), mask);
   }

   public static float ink(HeroPublicSnapshot s) {
      return Math.max(0, Math.min((int)RohanRules.INK_MAX, s.resource()));
   }

   public static float inspiration(HeroPublicSnapshot s) {
      return Math.max(0, Math.min((int)RohanRules.INSPIRATION_MAX, s.resource2()));
   }

   /** Heaven's Door is drawn (summoned, in a move, reading). */
   public static boolean standOut(HeroPublicSnapshot s) {
      return flag(s, RohanFlags.STAND_OUT);
   }

   public static int cooldown(HeroPublicSnapshot s, int index) {
      return s.extraCooldown(index);
   }
}
