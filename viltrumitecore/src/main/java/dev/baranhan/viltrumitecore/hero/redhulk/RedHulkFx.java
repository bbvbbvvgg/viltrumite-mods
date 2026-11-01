package dev.baranhan.viltrumitecore.hero.redhulk;

import dev.baranhan.viltrumitecore.hero.fx.FxPreset;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Red Hulk's world FX presets (shared preset wire, net ids 700-712; never renumber). The client recipes
 * (client.redhulk.RedHulkFxRecipes) draw the neutral layers (rings, dust, debris, scorch, shake) and
 * RedHulkVfx adds the heat-coloured part (embers, fire rings, heat haze) through a preset listener. Scale 1 is
 * sized for the 2.5-block body; the server passes the heat tier through the scale (1 + 0.15 x tier).
 */
public final class RedHulkFx {
   /** A landed punch: at = contact, target = attacker eye. */
   public static final FxPreset PUNCH = FxPreset.register("red_hulk:punch", 700);
   /** The two-hand hammer finisher: at = ground impact. */
   public static final FxPreset HAMMER = FxPreset.register("red_hulk:hammer", 701);
   /** Thermal Clap: at = hands, target = the end of the cone axis. */
   public static final FxPreset CLAP = FxPreset.register("red_hulk:clap", 702);
   /** Seismic Slam: at = ground under the fists, target = at + (radius, 0, 0). */
   public static final FxPreset SEISMIC = FxPreset.register("red_hulk:seismic", 703);
   public static final FxPreset LEAP_LAUNCH = FxPreset.register("red_hulk:leap_launch", 704);
   /** Destruction Leap landing: at = feet, target = at + (radius, 0, 0). */
   public static final FxPreset LEAP_LAND = FxPreset.register("red_hulk:leap_land", 705);
   public static final FxPreset THROW_IMPACT = FxPreset.register("red_hulk:throw_impact", 706);
   /** One Heat Blast pulse: at = chest, target = where the beam ends. */
   public static final FxPreset BLAST = FxPreset.register("red_hulk:blast", 707);
   /** Thermal Fury roar ring: at = chest. */
   public static final FxPreset FURY = FxPreset.register("red_hulk:fury", 708);
   /** Overheat steam vent: at = chest. */
   public static final FxPreset VENT = FxPreset.register("red_hulk:vent", 709);
   /** Critical Overheat charge start: at = chest. */
   public static final FxPreset ULT_CHARGE = FxPreset.register("red_hulk:ult_charge", 710);
   /** Critical Overheat blast: at = chest, target = at + (radius, 0, 0). */
   public static final FxPreset ULT_RELEASE = FxPreset.register("red_hulk:ult_release", 711);
   /** A heavy landing from a fall. */
   public static final FxPreset LANDING = FxPreset.register("red_hulk:landing", 712);

   public static final java.util.List<FxPreset> PRESETS = java.util.List.of(PUNCH, HAMMER, CLAP, SEISMIC, LEAP_LAUNCH, LEAP_LAND, THROW_IMPACT, BLAST,
      FURY, VENT, ULT_CHARGE, ULT_RELEASE, LANDING);

   private RedHulkFx() {
   }

   public static void init() {
   }

   /** Scale carrying the heat tier (the client reads it back for colour and density). */
   public static float scale(int heat, float base) {
      return base * (1.0F + 0.15F * RedHulkRules.tier(heat));
   }

   public static void send(ServerPlayer player, FxPreset preset, Vec3 at, @Nullable Vec3 target, float scale, @Nullable BlockState material,
      int hitStop, int... ids) {
      HeroFx.preset(player, preset, at, target, scale, material, hitStop, ids);
   }

   /** The radius rides in the target point (at + (r, 0, 0)), the listener reads it back. */
   public static Vec3 radius(Vec3 at, double r) {
      return at.add(r, 0, 0);
   }
}
