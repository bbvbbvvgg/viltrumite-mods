package dev.baranhan.viltrumitecore.hero.venom;

import dev.baranhan.viltrumitecore.hero.fx.FxPreset;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Venom's world FX presets (shared preset wire, net ids 740-749; never renumber). The client recipes draw the
 * neutral layers (shake, dust, rings); VenomVfx adds the symbiote goo through a preset listener.
 */
public final class VenomFx {
   /** The symbiote bursts over the host: at = chest; scale = stage (0..4). */
   public static final FxPreset TRANSFORM = FxPreset.register("venom:transform", 740);
   /** A claw rakes a body: at = contact, target = the hand. */
   public static final FxPreset CLAW = FxPreset.register("venom:claw", 741);
   /** A tendril sticks: at = the end, target = the shoulder it grew from. */
   public static final FxPreset STICK = FxPreset.register("venom:stick", 742);
   /** A slam (held body, lash slam, landing): at = ground impact, target = at + (radius, 0, 0). */
   public static final FxPreset SLAM = FxPreset.register("venom:slam", 743);
   /** The lash sweep: at = centre, target = at + (radius, 0, 0). */
   public static final FxPreset SWEEP = FxPreset.register("venom:sweep", 744);
   /** The symbiote recoils (fire, sound): at = chest. */
   public static final FxPreset RECOIL = FxPreset.register("venom:recoil", 745);
   /** A web is cut / torn / eaten: at = the point. */
   public static final FxPreset WEB = FxPreset.register("venom:web", 746);
   /** Venom and a Spider collide: at = midpoint. */
   public static final FxPreset CLASH = FxPreset.register("venom:clash", 747);
   /** The takeover: at = the host's chest, target = the husk's chest. scale 1 = engulf, 0.5 = release. */
   public static final FxPreset ENGULF = FxPreset.register("venom:engulf", 748);
   /** Mega jump launch / heavy landing: at = feet. */
   public static final FxPreset LEAP = FxPreset.register("venom:leap", 749);

   public static final java.util.List<FxPreset> PRESETS = java.util.List.of(TRANSFORM, CLAW, STICK, SLAM, SWEEP, RECOIL, WEB, CLASH, ENGULF, LEAP);

   private VenomFx() {
   }

   public static void init() {
   }

   /** From a player or any other Venom body (the boss): the preset goes to everybody tracking the point. */
   public static void send(Entity source, FxPreset preset, Vec3 at, @Nullable Vec3 target, float scale, int hitStop, int... ids) {
      if (source instanceof ServerPlayer player) {
         HeroFx.preset(player, preset, at, target, scale, null, hitStop, ids);
      } else if (source.level() instanceof ServerLevel level) {
         HeroFx.preset(level, preset, at, target, scale, null);
      }
   }

   public static Vec3 radius(Vec3 at, double r) {
      return at.add(r, 0, 0);
   }
}
