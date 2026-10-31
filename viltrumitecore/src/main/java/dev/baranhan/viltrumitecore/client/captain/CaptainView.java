package dev.baranhan.viltrumitecore.client.captain;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.captain.CaptainFlags;
import dev.baranhan.viltrumitecore.hero.captain.CaptainMove;
import dev.baranhan.viltrumitecore.hero.captain.CaptainRules;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** Read-only client view of the synced Captain snapshot plus the asset locations. Client only. */
public final class CaptainView {
   public static final String NS = "viltrumitecore";
   public static final ResourceLocation BODY = rl("geo/captain/captain.geo.json");
   public static final ResourceLocation GEAR = rl("geo/captain/captain_gear.geo.json");
   public static final ResourceLocation ARMS = rl("geo/captain/captain_arms.geo.json");
   public static final ResourceLocation ARMS_GEAR = rl("geo/captain/captain_arms_gear.geo.json");
   public static final ResourceLocation SHIELD = rl("geo/captain/captain_shield.geo.json");
   /** HD suit skin (128x128) on the Steve layout; the geo UVs are in 64 space. */
   public static final ResourceLocation SKIN = rl("textures/entity/captain/captain.png");
   public static final ResourceLocation GEAR_TEX = rl("textures/entity/captain/captain_gear.png");
   public static final ResourceLocation GEAR_GLOW = rl("textures/entity/captain/captain_gear_glow.png");
   public static final ResourceLocation SHIELD_TEX = rl("textures/entity/captain/captain_shield.png");
   public static final ResourceLocation SHIELD_GLOW = rl("textures/entity/captain/captain_shield_glow.png");
   public static final ResourceLocation STANCES = rl("animations/captain/captain_stances.animation.json");
   public static final ResourceLocation COMBAT = rl("animations/captain/captain_combat.animation.json");
   public static final ResourceLocation SKILLS = rl("animations/captain/captain_skills.animation.json");
   public static final ResourceLocation FP = rl("animations/captain/captain_fp.animation.json");
   /** The shield model faces -Z; on the left_shield bone it is turned +90 degrees about Y (front outward). */
   public static final float SHIELD_YAW = 90.0F;

   private CaptainView() {
   }

   public static ResourceLocation rl(String path) {
      return new ResourceLocation(NS, path);
   }

   @Nullable
   public static HeroPublicSnapshot of(@Nullable Entity entity) {
      if (entity instanceof HeroPlayer heroPlayer) {
         HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
         if (snapshot != null && snapshot.heroId() == HeroId.CAPTAIN) {
            return snapshot;
         }
      }
      return null;
   }

   public static boolean flag(HeroPublicSnapshot s, int bit) {
      return CaptainFlags.has(s.heroFlags(), bit);
   }

   @Nullable
   public static CaptainMove move(HeroPublicSnapshot s) {
      return CaptainMove.byId(s.actionId());
   }

   /** Shield on the forearm (not thrown, not lost). */
   public static boolean shieldInHand(HeroPublicSnapshot s) {
      return !flag(s, CaptainFlags.SHIELD_OUT);
   }

   public static int kinetic(HeroPublicSnapshot s) {
      return Math.max(0, Math.min(CaptainRules.KINETIC_MAX, s.resource()));
   }

   public static float kineticFraction(HeroPublicSnapshot s) {
      return kinetic(s) / (float)CaptainRules.KINETIC_MAX;
   }

   /** Ticks left of the Avengers' fight (0 = no team). */
   public static int teamLeft(HeroPublicSnapshot s) {
      return flag(s, CaptainFlags.TEAM_ACTIVE) ? Math.max(0, s.lionWindowLeft()) : 0;
   }

   /** Clip file of a move: melee in the combat file, the rest in the skills file. */
   public static ResourceLocation fileOf(CaptainMove move) {
      return switch (move) {
         case SHIELD_1, SHIELD_2, SHIELD_3, SHIELD_4, FIST_1, FIST_2, FIST_3, COUNTER, RETURN_STRIKE, ROLL_SWEEP, PARRY -> COMBAT;
         default -> SKILLS;
      };
   }

   /** Moves whose clip loops while the server holds them (aim). */
   public static boolean loops(CaptainMove move) {
      return move == CaptainMove.AIM;
   }
}
