package dev.baranhan.viltrumitecore.client.jinwoo;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooFlags;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooMove;
import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooRules;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** Read-only client view of the synced Jinwoo snapshot plus the asset locations. Client only. */
public final class JinwooView {
   public static final String NS = "viltrumitecore";
   public static final ResourceLocation BODY = rl("geo/jinwoo/jinwoo.geo.json");
   public static final ResourceLocation GEAR = rl("geo/jinwoo/jinwoo_gear.geo.json");
   public static final ResourceLocation ARMS = rl("geo/jinwoo/jinwoo_arms.geo.json");
   public static final ResourceLocation ARMS_GEAR = rl("geo/jinwoo/jinwoo_arms_gear.geo.json");
   /** HD Jinwoo skin (128x128) on the Steve layout; the geo UVs are in 64 space. */
   public static final ResourceLocation SKIN = rl("textures/entity/jinwoo/jinwoo.png");
   public static final ResourceLocation GEAR_TEX = rl("textures/entity/jinwoo/jinwoo_gear.png");
   public static final ResourceLocation GEAR_GLOW = rl("textures/entity/jinwoo/jinwoo_gear_glow.png");
   public static final ResourceLocation STANCES = rl("animations/jinwoo/jinwoo_stances.animation.json");
   public static final ResourceLocation COMBAT = rl("animations/jinwoo/jinwoo_combat.animation.json");
   public static final ResourceLocation SKILLS = rl("animations/jinwoo/jinwoo_skills.animation.json");
   public static final ResourceLocation FP = rl("animations/jinwoo/jinwoo_fp.animation.json");

   private JinwooView() {
   }

   public static ResourceLocation rl(String path) {
      return new ResourceLocation(NS, path);
   }

   @Nullable
   public static HeroPublicSnapshot of(@Nullable Entity entity) {
      if (entity instanceof HeroPlayer heroPlayer) {
         HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
         if (snapshot != null && snapshot.heroId() == HeroId.JINWOO) {
            return snapshot;
         }
      }
      return null;
   }

   public static boolean flag(HeroPublicSnapshot s, int bit) {
      return JinwooFlags.has(s.heroFlags(), bit);
   }

   @Nullable
   public static JinwooMove move(HeroPublicSnapshot s) {
      return JinwooMove.byId(s.actionId());
   }

   public static float mana(HeroPublicSnapshot s) {
      return s.resource() / 10.0F;
   }

   public static float manaFraction(HeroPublicSnapshot s) {
      return Math.max(0.0F, Math.min(1.0F, mana(s) / JinwooRules.MANA_MAX));
   }

   public static int active(HeroPublicSnapshot s) {
      return Math.max(0, s.resource2()) / 100;
   }

   public static int roster(HeroPublicSnapshot s) {
      return Math.max(0, s.resource2()) % 100;
   }

   public static int domainLeft(HeroPublicSnapshot s) {
      return flag(s, JinwooFlags.DOMAIN) ? Math.max(0, s.lionWindowLeft()) : 0;
   }

   /** Clip file of a move: blade moves in the combat file, the rest in the skills file. */
   public static ResourceLocation fileOf(JinwooMove move) {
      return move.ordinal() <= JinwooMove.STEP.ordinal() ? COMBAT : SKILLS;
   }
}
