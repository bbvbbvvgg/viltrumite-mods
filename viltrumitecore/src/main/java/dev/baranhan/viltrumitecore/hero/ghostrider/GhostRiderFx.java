package dev.baranhan.viltrumitecore.hero.ghostrider;

import dev.baranhan.viltrumitecore.hero.fx.FxPreset;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Ghost Rider's world FX presets (shared preset wire, net ids 720-729; never renumber). The client recipes draw
 * the neutral layers (rings, dust, shake) and GhostRiderVfx adds the hellfire through a preset listener. The bike's
 * continuous FX (trails, exhaust) are client-side from the synced controls, not presets.
 */
public final class GhostRiderFx {
   /** The skull bursts into hellfire: at = chest. */
   public static final FxPreset TRANSFORM = FxPreset.register("ghost_rider:transform", 720);
   /** A chain lash lands: at = contact, target = the hand. */
   public static final FxPreset LASH = FxPreset.register("ghost_rider:lash", 721);
   /** The chain bites a body: at = the body, target = the hand. */
   public static final FxPreset HOOK = FxPreset.register("ghost_rider:hook", 722);
   /** Chain slam: at = ground impact, target = at + (radius, 0, 0). */
   public static final FxPreset SLAM = FxPreset.register("ghost_rider:slam", 723);
   /** Hellfire Wave: at = origin on the ground, target = the end of the cone axis. */
   public static final FxPreset WAVE = FxPreset.register("ghost_rider:wave", 724);
   /** Penance verdict: at = the victim's eyes, target = the Rider's eyes; scale 0 = innocent. */
   public static final FxPreset VERDICT = FxPreset.register("ghost_rider:verdict", 725);
   /** The bike rams a body during Hell Dash: at = contact. */
   public static final FxPreset RAM = FxPreset.register("ghost_rider:ram", 726);
   /** The Hell Cycle forms: at = the bike. */
   public static final FxPreset BIKE_FORM = FxPreset.register("ghost_rider:bike_form", 727);
   /** Hell Dash on foot: at = start, target = the end of the dash. */
   public static final FxPreset DASH = FxPreset.register("ghost_rider:dash", 728);

   public static final java.util.List<FxPreset> PRESETS = java.util.List.of(TRANSFORM, LASH, HOOK, SLAM, WAVE, VERDICT, RAM, BIKE_FORM, DASH);

   private GhostRiderFx() {
   }

   public static void init() {
   }

   public static void send(ServerPlayer player, FxPreset preset, Vec3 at, @Nullable Vec3 target, float scale, @Nullable BlockState material,
      int hitStop, int... ids) {
      HeroFx.preset(player, preset, at, target, scale, material, hitStop, ids);
   }

   public static Vec3 radius(Vec3 at, double r) {
      return at.add(r, 0, 0);
   }
}
