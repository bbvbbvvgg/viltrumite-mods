package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.hero.fx.FxPreset;
import java.util.List;

/**
 * Thor's world-FX presets (rework spec §7). A move row refers to one of
 * these ids; the server sends it with {@code HeroFx.preset} and every
 * client plays the layered recipe in {@code client.thor.ThorFxRecipes}.
 * Net ids 100..199 belong to Thor. Never renumber a net id.
 *
 * <p>Ids ({@code thor:*}), all sized for the 3.2-block Thor at scale 1:
 * <ul>
 * <li>{@link #BOLT} — sky bolt: leader, white-blue core, wide glow, branches, sky flash, light pool, ground crawlers, scorch decal, re-strikes, afterglow.</li>
 * <li>{@link #BOLT_HEAVY} — the same, thicker and longer (transform, finishers).</li>
 * <li>{@link #WRATH_STRIKE} — the ultimate's contact: giant column, four satellite bolts, two crawler waves, dust, debris, shockwave, long tail.</li>
 * <li>{@link #ARC} — a short jagged discharge between two points (origin → target).</li>
 * <li>{@link #RING} — a ring of discharges on the ground (scale = radius in blocks).</li>
 * <li>{@link #CRACKLE} — micro arcs and sparks crawling on a point or body.</li>
 * <li>{@link #GROUND_CRAWL} — discharges crawling outward over the ground.</li>
 * <li>{@link #CHARGE} — one burst of arcs and head sparks around a point (Mjölnir head, palm).</li>
 * <li>{@link #IMPACT_FIST} — fist contact: puff, small dust, light shake.</li>
 * <li>{@link #IMPACT_HAMMER} — hammer contact: flash, dust, debris, shake, hit-stop.</li>
 * <li>{@link #IMPACT_HAMMER_HEAVY} — heavy hammer contact: more debris, ring, stronger shake and hit-stop.</li>
 * <li>{@link #IMPACT_GROUND} — finisher into the ground: crater dust, debris, double shockwave, scorch.</li>
 * <li>{@link #IMPACT_ELECTRIC} — a hammer contact in electric mode: impact plus arcs and crawlers.</li>
 * <li>{@link #CATCH} — Mjölnir caught: flash and arcs on the palm.</li>
 * </ul>
 * State-driven Thor FX without a packet (client reads the snapshot):
 * {@code thor:wrath_storm} (WRATH build-up and tail), {@code thor:charge_aura}
 * (electric mode / charging arcs on the arm and the hammer),
 * {@code thor:hammer_spin} (Mjölnir spin with motion blur).
 */
public final class ThorFx {
   public static final FxPreset BOLT = FxPreset.register("thor:bolt", 100);
   public static final FxPreset BOLT_HEAVY = FxPreset.register("thor:bolt_heavy", 101);
   public static final FxPreset WRATH_STRIKE = FxPreset.register("thor:wrath_strike", 102);
   public static final FxPreset ARC = FxPreset.register("thor:arc", 103);
   public static final FxPreset RING = FxPreset.register("thor:ring", 104);
   public static final FxPreset CRACKLE = FxPreset.register("thor:crackle", 105);
   public static final FxPreset GROUND_CRAWL = FxPreset.register("thor:ground_crawl", 106);
   public static final FxPreset CHARGE = FxPreset.register("thor:charge", 107);
   public static final FxPreset IMPACT_FIST = FxPreset.register("thor:impact_fist", 110);
   public static final FxPreset IMPACT_HAMMER = FxPreset.register("thor:impact_hammer", 111);
   public static final FxPreset IMPACT_HAMMER_HEAVY = FxPreset.register("thor:impact_hammer_heavy", 112);
   public static final FxPreset IMPACT_GROUND = FxPreset.register("thor:impact_ground", 113);
   public static final FxPreset IMPACT_ELECTRIC = FxPreset.register("thor:impact_electric", 114);
   public static final FxPreset CATCH = FxPreset.register("thor:catch", 115);

   /** State-driven FX ids (documentation and the move table; never sent). */
   public static final String WRATH_STORM = "thor:wrath_storm";
   public static final String CHARGE_AURA = "thor:charge_aura";
   public static final String HAMMER_SPIN = "thor:hammer_spin";

   public static final List<FxPreset> PRESETS = List.of(BOLT, BOLT_HEAVY, WRATH_STRIKE, ARC, RING, CRACKLE, GROUND_CRAWL, CHARGE,
      IMPACT_FIST, IMPACT_HAMMER, IMPACT_HAMMER_HEAVY, IMPACT_GROUND, IMPACT_ELECTRIC, CATCH);
   public static final List<String> STATE_FX = List.of(WRATH_STORM, CHARGE_AURA, HAMMER_SPIN);

   /** Power from which a plain bolt packet plays as {@link #BOLT_HEAVY}. */
   public static final float HEAVY_BOLT_POWER = 1.5F;

   private ThorFx() {
   }

   /** Loads the class (registers every preset); call once from common setup and the client. */
   public static void bootstrap() {
   }
}
