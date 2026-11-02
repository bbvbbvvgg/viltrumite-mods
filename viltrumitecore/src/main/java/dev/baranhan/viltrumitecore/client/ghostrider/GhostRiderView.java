package dev.baranhan.viltrumitecore.client.ghostrider;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderFlags;
import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderMove;
import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderRules;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleEntity;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Read-only client view of the synced Ghost Rider snapshot plus the body assets (tools/assets/ghost_rider_body.py).
 * Client only.
 */
public final class GhostRiderView {
   /** skin_0 = Johnny Blaze, skin_1..5 = the burn-away frames, skin_6 = the Rider (empty head cube: 3D skull). */
   public static final ResourceLocation[] SKINS = new ResourceLocation[GhostRiderRules.BURN_FRAMES + 1];
   /** Emissive overlay per burn frame (index 0 unused). */
   public static final ResourceLocation[] SKIN_GLOWS = new ResourceLocation[GhostRiderRules.BURN_FRAMES + 1];
   public static final ResourceLocation SKIN_GLOW_GEO = GhostRiderAssets.rl("geo/ghost_rider/skin_glow.geo.json");
   public static final ResourceLocation GEAR_GEO = GhostRiderAssets.rl("geo/ghost_rider/rider_gear.geo.json");
   public static final ResourceLocation GEAR_TEX = GhostRiderAssets.rl("textures/entity/ghost_rider/rider_gear.png");
   public static final ResourceLocation GEAR_GLOW = GhostRiderAssets.rl("textures/entity/ghost_rider/rider_gear_glow.png");
   public static final ResourceLocation CHAIN = GhostRiderAssets.rl("textures/entity/ghost_rider/chain.png");
   /** From this burn frame on the head is the 3D skull. */
   public static final int SKULL_FRAME = 5;

   static {
      for (int i = 0; i < SKINS.length; i++) {
         SKINS[i] = GhostRiderAssets.rl("textures/entity/ghost_rider/skin_" + i + ".png");
         SKIN_GLOWS[i] = GhostRiderAssets.rl("textures/entity/ghost_rider/skin_glow_" + Math.max(1, i) + ".png");
      }
   }

   private GhostRiderView() {
   }

   @Nullable
   public static HeroPublicSnapshot of(@Nullable Entity entity) {
      if (entity instanceof HeroPlayer heroPlayer) {
         HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
         if (snapshot != null && snapshot.heroId() == HeroId.GHOST_RIDER) {
            return snapshot;
         }
      }
      return null;
   }

   public static boolean flag(HeroPublicSnapshot s, int bit) {
      return GhostRiderFlags.has(s.heroFlags(), bit);
   }

   public static boolean rider(HeroPublicSnapshot s) {
      return flag(s, GhostRiderFlags.RIDER);
   }

   public static boolean riding(HeroPublicSnapshot s) {
      return flag(s, GhostRiderFlags.RIDING);
   }

   @Nullable
   public static GhostRiderMove move(HeroPublicSnapshot s) {
      return GhostRiderMove.byId(s.actionId());
   }

   /** Ticks into the current move (partial tick added, clamped to its length); 0 without a move. */
   public static float elapsed(HeroPublicSnapshot s, float partial) {
      GhostRiderMove move = move(s);
      if (move == null) {
         return 0.0F;
      }
      return Math.min((float)move.length, s.actionElapsed() + partial);
   }

   /** Skin frame 0 (Johnny) .. BURN_FRAMES (the Rider) on the move clock (pure on the snapshot). */
   public static int burnFrame(HeroPublicSnapshot s) {
      GhostRiderMove move = move(s);
      if (move == GhostRiderMove.TRANSFORM) {
         return GhostRiderRules.burnFrame(false, s.actionElapsed());
      }
      if (move == GhostRiderMove.REVERT) {
         return GhostRiderRules.burnFrame(true, s.actionElapsed());
      }
      return rider(s) ? GhostRiderRules.BURN_FRAMES : 0;
   }

   /** Burning: the flames on the skull and hands (also at the end of the transformation). */
   public static float fire(HeroPublicSnapshot s, float partial) {
      GhostRiderMove move = move(s);
      if (move == GhostRiderMove.TRANSFORM) {
         float t = elapsed(s, partial);
         return clamp01((t - (GhostRiderRules.TRANSFORM_FLARE_TICK - 6)) / 6.0F);
      }
      if (move == GhostRiderMove.REVERT) {
         return clamp01(1.0F - elapsed(s, partial) / (GhostRiderRules.REVERT_TICKS * 0.7F));
      }
      return rider(s) ? 1.0F : 0.0F;
   }

   /** The chain is in his hand (not wrapped round his chest). */
   public static boolean chainOut(HeroPublicSnapshot s) {
      if (flag(s, GhostRiderFlags.CHAIN) || flag(s, GhostRiderFlags.TOW)) {
         return true;
      }
      GhostRiderMove move = move(s);
      return move != null && switch (move) {
         case LASH_1, LASH_2, LASH_3, HOOK, YANK, HOOK_MISS, WHIRL, SLAM, BIKE_LASH, BIKE_HOOK -> true;
         default -> false;
      };
   }

   @Nullable
   public static HellCycleEntity bike(Level level, HeroPublicSnapshot s) {
      int id = s.resource2() - 1;
      return id >= 0 && level.getEntity(id) instanceof HellCycleEntity bike ? bike : null;
   }

   static float clamp01(float v) {
      return v < 0.0F ? 0.0F : Math.min(1.0F, v);
   }
}
