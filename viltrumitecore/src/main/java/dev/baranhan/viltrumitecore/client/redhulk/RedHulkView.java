package dev.baranhan.viltrumitecore.client.redhulk;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.redhulk.RedHulkFlags;
import dev.baranhan.viltrumitecore.hero.redhulk.RedHulkMove;
import dev.baranhan.viltrumitecore.hero.redhulk.RedHulkRules;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** Read-only client view of the synced Red Hulk snapshot plus the asset locations. Client only. */
public final class RedHulkView {
   public static final String NS = "viltrumitecore";
   /** The new body (tools/assets/red_hulk_model.py): 119 cubes, 22 bones with jaw, upper lip and brows. */
   public static final ResourceLocation BODY = rl("geo/red_hulk/red_hulk.geo.json");
   /** First-person arms (same cubes as the body's arms, vanilla arm bone names on top). */
   public static final ResourceLocation ARMS = rl("geo/red_hulk/red_hulk_arms.geo.json");
   /** Atlas of the body model (per-face UVs). */
   public static final ResourceLocation SKIN = rl("textures/entity/red_hulk/red_hulk.png");
   /** Emissive eyes (always lit, yellow-white). */
   public static final ResourceLocation EYES = rl("textures/entity/red_hulk/red_hulk_eyes.png");
   /** Emissive heat veins, chest core and fists; its brightness follows the heat. */
   public static final ResourceLocation HEAT = rl("textures/entity/red_hulk/red_hulk_heat.png");
   /** The same skin as a vanilla 64x64 player skin (vanilla hand with an item, anything drawing the skin). */
   public static final ResourceLocation VANILLA_SKIN = rl("textures/entity/red_hulk/red_hulk_skin.png");
   public static final ResourceLocation STANCES = rl("animations/red_hulk/red_hulk_stances.animation.json");
   public static final ResourceLocation COMBAT = rl("animations/red_hulk/red_hulk_combat.animation.json");
   public static final ResourceLocation SKILLS = rl("animations/red_hulk/red_hulk_skills.animation.json");
   public static final ResourceLocation ULT = rl("animations/red_hulk/red_hulk_ult.animation.json");
   public static final ResourceLocation FACE = rl("animations/red_hulk/red_hulk_face.animation.json");
   public static final ResourceLocation FP = rl("animations/red_hulk/red_hulk_fp.animation.json");

   private RedHulkView() {
   }

   public static ResourceLocation rl(String path) {
      return new ResourceLocation(NS, path);
   }

   @Nullable
   public static HeroPublicSnapshot of(@Nullable Entity entity) {
      if (entity instanceof HeroPlayer heroPlayer) {
         HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
         if (snapshot != null && snapshot.heroId() == HeroId.RED_HULK) {
            return snapshot;
         }
      }
      return null;
   }

   public static boolean flag(HeroPublicSnapshot s, int bit) {
      return RedHulkFlags.has(s.heroFlags(), bit);
   }

   @Nullable
   public static RedHulkMove move(HeroPublicSnapshot s) {
      return RedHulkMove.byId(s.actionId());
   }

   public static int heat(HeroPublicSnapshot s) {
      return RedHulkRules.clampHeat(s.resource());
   }

   public static float heatFraction(HeroPublicSnapshot s) {
      return heat(s) / (float)RedHulkRules.HEAT_MAX;
   }

   public static int tier(HeroPublicSnapshot s) {
      return RedHulkRules.tier(heat(s));
   }

   public static boolean overheated(HeroPublicSnapshot s) {
      return s.resourceLocked() || flag(s, RedHulkFlags.OVERHEAT);
   }

   public static boolean furious(HeroPublicSnapshot s) {
      return flag(s, RedHulkFlags.FURY);
   }

   /** Rage stance: hot (tier 2+), furious or overheated. */
   public static boolean rage(HeroPublicSnapshot s) {
      return tier(s) >= 2 || furious(s) || overheated(s);
   }

   /** Clip file of a move: melee and grabs in the combat file, the ultimate and the vent in the ult file, the rest in skills. */
   public static ResourceLocation fileOf(RedHulkMove move) {
      return switch (move) {
         case JAB_L, JAB_R, HOOK, UPPERCUT, HAMMER, GRAB, HOLD, THROW, GRAB_MISS -> COMBAT;
         case ULT_CHARGE, ULT_RELEASE, ULT_RECOVER, VENT -> ULT;
         default -> SKILLS;
      };
   }

   /**
    * Pure: brightness 0-1 of the heat-vein pass. Cold veins stay faintly lit (0.08), they brighten with the heat,
    * pulse with the heartbeat (faster when hotter), burn steady white-hot in fury and flicker while overheated.
    */
   public static float glow(int heat, boolean fury, boolean overheat, double seconds) {
      float h = RedHulkRules.clampHeat(heat) / (float)RedHulkRules.HEAT_MAX;
      double rate = 0.9 + 1.6 * h;
      float pulse = (float)(0.5 + 0.5 * Math.sin(seconds * Math.PI * 2.0 * rate));
      float base = 0.08F + 0.82F * h;
      float g = base * (0.82F + 0.18F * pulse);
      if (fury) {
         g = Math.max(g, 0.85F + 0.15F * pulse);
      }
      if (overheat) {
         float flicker = (float)(0.5 + 0.5 * Math.sin(seconds * 37.0) * Math.sin(seconds * 13.0));
         g = 0.55F + 0.45F * flicker;
      }
      return Math.max(0.0F, Math.min(1.0F, g));
   }

   /** Pure: the eye glow: always on, brighter with heat and fury, a hot white burst while overheated. */
   public static float eyeGlow(int heat, boolean fury, boolean overheat) {
      float h = RedHulkRules.clampHeat(heat) / (float)RedHulkRules.HEAT_MAX;
      float g = 0.7F + 0.3F * h;
      return fury || overheat ? 1.0F : g;
   }
}
