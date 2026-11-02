package dev.baranhan.viltrumitecore.client.venom;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.venom.VenomFlags;
import dev.baranhan.viltrumitecore.hero.venom.VenomMove;
import dev.baranhan.viltrumitecore.hero.venom.VenomRules;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Read-only client view of Venom's synced snapshot, the asset locations and the pure timing of the staged
 * transformation (VenomViewTest). Client only (no client classes in the pure helpers).
 */
public final class VenomView {
   public static final String NS = "viltrumitecore";
   /** The body (tools/assets/venom_model.py): jaw, a six-bone tongue, teeth rows, claws, three-toed feet. */
   public static final ResourceLocation BODY = rl("geo/venom/venom.geo.json");
   /** First-person arms (the body's arm chains, vanilla arm bone names on top). */
   public static final ResourceLocation ARMS = rl("geo/venom/venom_arms.geo.json");
   /** Glossy black atlas of the body (no white marks: those are {@link #MARKS}). */
   public static final ResourceLocation SKIN = rl("textures/entity/venom/venom.png");
   /** The white eyes and the spider emblem (translucent pass: they form at the transformation's face stage). */
   public static final ResourceLocation MARKS = rl("textures/entity/venom/venom_marks.png");
   /** Faint emissive sheen of the eyes (always on in the form). */
   public static final ResourceLocation EYES = rl("textures/entity/venom/venom_eyes.png");
   /** The living surface: strands that pulse blue-violet over the black (RenderType.eyes, brightness = pulse). */
   public static final ResourceLocation PULSE = rl("textures/entity/venom/venom_pulse.png");
   /** Tendril strip (16 x 64, v along the length). */
   public static final ResourceLocation TENDRIL = rl("textures/entity/venom/venom_tendril.png");
   /** The symbiote coat as a vanilla 64x64 skin (vanilla hand, the possessed host, the boss's fallback). */
   public static final ResourceLocation VANILLA_SKIN = rl("textures/entity/venom/venom_skin.png");
   public static final ResourceLocation STANCES = rl("animations/venom/venom_stances.animation.json");
   public static final ResourceLocation MOVES = rl("animations/venom/venom_moves.animation.json");
   public static final ResourceLocation FACE = rl("animations/venom/venom_face.animation.json");
   public static final ResourceLocation FP = rl("animations/venom/venom_fp.animation.json");
   /** Coat frames (spreading from the hands and feet, the head last): coat_0 .. coat_{@link #COAT_FRAMES}. */
   public static final int COAT_FRAMES = 16;
   /** The reverse transformation draws the body until this tick, then the shrinking coat on the human. */
   public static final int REVERT_GEO_END = 18;

   private VenomView() {
   }

   public static ResourceLocation rl(String path) {
      return new ResourceLocation(NS, path);
   }

   public static ResourceLocation coat(int frame) {
      int f = Math.max(0, Math.min(COAT_FRAMES, frame));
      return rl("textures/entity/venom/coat/coat_" + f + ".png");
   }

   @Nullable
   public static HeroPublicSnapshot of(@Nullable Entity entity) {
      if (entity instanceof HeroPlayer heroPlayer) {
         HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
         if (snapshot != null && snapshot.heroId() == HeroId.VENOM) {
            return snapshot;
         }
      }
      return null;
   }

   public static boolean flag(HeroPublicSnapshot s, int bit) {
      return VenomFlags.has(s.heroFlags(), bit);
   }

   @Nullable
   public static VenomMove move(HeroPublicSnapshot s) {
      return VenomMove.byId(s.actionId());
   }

   public static float hunger(HeroPublicSnapshot s) {
      return Math.max(0, Math.min(VenomRules.HUNGER_MAX, s.resource())) / (float)VenomRules.HUNGER_MAX;
   }

   // --- the staged transformation (pure) ----------------------------------------------------------------

   /** What a Venom body looks like at one moment: the human with a coat, or the grown body with its marks. */
   public record Look(boolean geo, float coat, float growth, float marks, int stage) {
      public static final Look HUMAN = new Look(false, 0, 0, 0, -1);
      public static final Look VENOM = new Look(true, 1, 1, 1, 4);
   }

   /**
    * Pure: the look at a move tick. TRANSFORM: the coat spreads over the human from the hands and feet (limbs until
    * STAGE_TORSO, then torso and head), at STAGE_GROW the body takes over and grows to its proportions, at STAGE_FACE the
    * eyes and the emblem form, STAGE_POSE is the roar. REVERT runs it backwards: marks, shrink, then the coat recedes.
    */
   public static Look look(boolean form, @Nullable VenomMove move, double ticks) {
      if (move == VenomMove.TRANSFORM) {
         int stage = VenomRules.stage((int)Math.floor(ticks));
         if (ticks < VenomRules.STAGE_GROW) {
            return new Look(false, (float)Math.min(1.0, ticks / VenomRules.STAGE_GROW), 0, 0, stage);
         }
         float growth = (float)smooth((ticks - VenomRules.STAGE_GROW) / (VenomRules.STAGE_FACE - VenomRules.STAGE_GROW));
         float marks = (float)smooth((ticks - VenomRules.STAGE_FACE) / (VenomRules.STAGE_POSE - VenomRules.STAGE_FACE));
         return new Look(true, 1, growth, marks, stage);
      }
      if (move == VenomMove.REVERT) {
         if (ticks < REVERT_GEO_END) {
            float marks = 1.0F - (float)smooth(ticks / 8.0);
            float growth = 1.0F - (float)smooth((ticks - 6.0) / (REVERT_GEO_END - 6.0));
            return new Look(true, 1, growth, marks, 2);
         }
         float coat = 1.0F - (float)smooth((ticks - REVERT_GEO_END) / (VenomRules.REVERT_TICKS - REVERT_GEO_END));
         return new Look(false, coat, 0, 0, 0);
      }
      return form ? Look.VENOM : Look.HUMAN;
   }

   /** Pure: the coat frame (0 none .. COAT_FRAMES full) of a coat progress. */
   public static int coatFrame(float coat) {
      return Math.max(0, Math.min(COAT_FRAMES, Math.round(coat * COAT_FRAMES)));
   }

   /**
    * Pure: the living surface's brightness 0-1: a slow double heartbeat, faster and harder in rage, a surge when hurt
    * (the symbiote boils where it was hit) that settles as it reforms.
    */
   public static float pulse(double seconds, boolean rage, int hurtTime) {
      double rate = rage ? 1.6 : 0.75;
      double phase = (seconds * rate) % 1.0;
      double beat = Math.exp(-Math.pow((phase - 0.12) / 0.06, 2)) + 0.6 * Math.exp(-Math.pow((phase - 0.3) / 0.07, 2));
      float base = rage ? 0.32F : 0.16F;
      float p = base + (float)beat * (rage ? 0.45F : 0.28F);
      if (hurtTime > 0) {
         p = Math.max(p, 0.35F + 0.06F * hurtTime);
      }
      return Math.max(0.0F, Math.min(1.0F, p));
   }

   /** Pure: the body's breathing swell (bone scale offset) that makes the surface look alive. */
   public static float swell(double seconds, boolean rage) {
      double rate = rage ? 1.6 : 0.75;
      return (float)(Math.sin(seconds * rate * Math.PI * 2.0) * (rage ? 0.035 : 0.022));
   }

   static double smooth(double t) {
      t = Math.max(0, Math.min(1, t));
      return t * t * (3 - 2 * t);
   }
}
