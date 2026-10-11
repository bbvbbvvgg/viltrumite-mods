package dev.baranhan.viltrumitecore.client.ironman;

import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.PlayerGeoLayer;
import dev.baranhan.viltrumitecore.client.ironman.mark.MarkState;
import dev.baranhan.viltrumitecore.client.ironman.mark.MarkTextures;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ironman.IronManFlags;
import dev.baranhan.viltrumitecore.hero.ironman.combat.RightTool;
import java.util.List;
import java.util.WeakHashMap;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Stage 2 geo parts on the player (animation-system §7.1), converted from the
 * Satsu addon by tools/assets/convert_ironman_stage2_parts.py: the nano blade
 * (katar) or hammer (mallet) on the right fist, the shield plate on the left
 * forearm (nano texture, or the hex force field in a mark), the forearm
 * micro-missile pods (every suit, tinted to the plate colour, PR 19 iteration 2)
 * while missiles are held, and the repulsor palm glow while
 * charging. Forming and dissolving grow / shrink the weapon from the wrist
 * (nanite wave, spec §4.2, §8.4) from the synced NANO_ARSENAL timeline. Arm
 * parts are also drawn on the first-person arm.
 */
public final class IronManCombatParts {
   public static final ResourceLocation BLADE = geo("nano_blade");
   public static final ResourceLocation HAMMER = geo("nano_hammer");
   public static final ResourceLocation SHIELD = geo("nano_shield");
   public static final ResourceLocation LAUNCHERS = new ResourceLocation("viltrumitecore", "geo/ironman/missiles/forearm_launchers.geo.json");
   public static final ResourceLocation LAUNCHER_ROCKETS = new ResourceLocation("viltrumitecore", "geo/ironman/missiles/forearm_rockets.geo.json");
   private static final List<PlayerGeoLayer.Pass> BLADE_PASSES = List.of(PlayerGeoLayer.Pass.cutout(texture("nano_blade")), PlayerGeoLayer.Pass.glow(texture("nano_blade_glow")));
   private static final List<PlayerGeoLayer.Pass> HAMMER_PASSES = List.of(PlayerGeoLayer.Pass.cutout(texture("nano_hammer")), PlayerGeoLayer.Pass.glow(texture("nano_hammer_glow")));
   private static final List<PlayerGeoLayer.Pass> SHIELD_PASSES = List.of(PlayerGeoLayer.Pass.cutout(texture("nano_shield")));
   private static final List<PlayerGeoLayer.Pass> FIELD_PASSES = List.of(PlayerGeoLayer.Pass.glow(MarkTextures.ENERGY_SHIELD));
   private static final ResourceLocation LAUNCHER_TEXTURE = missileTexture("forearm_launcher");
   private static final List<PlayerGeoLayer.Pass> ROCKET_PASSES = List.of(PlayerGeoLayer.Pass.cutout(missileTexture("forearm_rockets")));
   /** Tinted launcher passes per suit colour (the grayscale housing takes the plate colour). */
   private static final java.util.Map<Integer, List<PlayerGeoLayer.Pass>> POD_PASSES = new java.util.HashMap<>();
   /** Pods slide this far (px) out of the forearm plating while opening. */
   private static final float POD_SLIDE = 1.6F;
   /** Muzzle petals open this far (deg). */
   private static final float PETAL_OPEN = 100.0F;
   /** Rocket tips slide this far (px) out of the muzzle. */
   private static final float ROCKET_SLIDE = 0.6F;
   /** Ticks the launchers stay open, empty, after the volley. */
   private static final float FIRED_HOLD_TICKS = 12.0F;
   /** Open / close speed of the launchers (1/s). */
   private static final float LAUNCHER_RATE = 14.0F;
   /** Last formed weapon per player: the dissolve wave still knows what to shrink. */
   private static final WeakHashMap<AbstractClientPlayer, RightTool> LAST_WEAPON = new WeakHashMap<>();
   /** Per player: launcher opening 0..1, last step (s), last tick the missiles were held, loaded flag (render thread). */
   private static final WeakHashMap<AbstractClientPlayer, float[]> FLAPS = new WeakHashMap<>();

   private IronManCombatParts() {
   }

   /** Weapon scale 0..1 from the forming / dissolving wave: grows from the wrist. */
   public static float weaponScale(boolean forming, boolean dissolving, float waveProgress) {
      if (forming) {
         return Mth.clamp(waveProgress, 0.05F, 1.0F);
      }

      return dissolving ? Mth.clamp(1.0F - waveProgress, 0.0F, 1.0F) : 1.0F;
   }

   static void collect(AbstractClientPlayer player, HeroPublicSnapshot snapshot, float partialTick, boolean firstPerson, List<PlayerGeoLayer.Part> out) {
      if (!IronManView.worn(snapshot)) {
         LAST_WEAPON.remove(player);
         FLAPS.remove(player);
         return;
      }

      RightTool tool = IronManView.tool(snapshot);
      boolean wave = IronManView.channel(snapshot, HeroAction.NANO_ARSENAL);
      float waveProgress = IronManView.progress(snapshot, HeroAction.NANO_ARSENAL, partialTick);
      boolean mark = MarkState.of(snapshot).markOn();
      RightTool weapon = tool.nanoWeapon() && !mark ? tool : null;
      if (weapon != null) {
         LAST_WEAPON.put(player, weapon);
      }

      boolean dissolving = weapon == null && wave;
      RightTool drawn = weapon != null ? weapon : dissolving ? LAST_WEAPON.get(player) : null;
      if (drawn != null) {
         float scale = weaponScale(weapon != null && wave, dissolving, waveProgress);
         boolean blade = drawn == RightTool.NANO_BLADE;
         String bone = blade ? "blade" : "hammer";
         out.add(new PlayerGeoLayer.Part(blade ? BLADE : HAMMER, blade ? BLADE_PASSES : HAMMER_PASSES, geo -> grow(geo, bone, scale)));
      } else if (!wave) {
         LAST_WEAPON.remove(player);
      }

      if (IronManFlags.is(snapshot.heroFlags(), IronManFlags.Field.SHIELD_UP)) {
         // Same forearm plate in both views; a mark fills it with the hex force field.
         out.add(new PlayerGeoLayer.Part(SHIELD, mark ? FIELD_PASSES : SHIELD_PASSES));
      }

      // Palm glow while the repulsor charges (reuses the palm flame geo, short and bright).
      if (tool == RightTool.REPULSOR && IronManView.channel(snapshot, HeroAction.SECONDARY_USE)) {
         float charge = IronManView.progress(snapshot, HeroAction.SECONDARY_USE, partialTick);
         float time = player.tickCount + partialTick;
         float length = 0.25F + 0.55F * charge + 0.05F * Mth.sin(time * 2.1F);
         boolean both = charge >= 1.0F;
         boolean right = !IronManFlags.is(snapshot.heroFlags(), IronManFlags.Field.SHOT_HAND) || both;
         out.add(new PlayerGeoLayer.Part(ThrusterFlames.PALMS, List.of(PlayerGeoLayer.Pass.glow(ThrusterFlames.texture(0), 0.55F, 0.85F, 1.0F)),
            geo -> palms(geo, length, right || both, !right || both)));
      }

      float k = flapOpen(player, snapshot, partialTick);
      if (k <= 0.02F) {
         return;
      }

      boolean loaded = launchersLoaded(player);
      // Forearm pods in both views (arm bones are drawn on the first-person arm too), tinted to the suit.
      int color = dev.baranhan.viltrumitecore.client.ironman.mark.SuitPalette.of(snapshot);
      List<PlayerGeoLayer.Pass> passes = POD_PASSES.computeIfAbsent(color, c -> List.of(PlayerGeoLayer.Pass.cutout(LAUNCHER_TEXTURE,
         dev.baranhan.viltrumitecore.client.ironman.mark.SuitPalette.r(c), dev.baranhan.viltrumitecore.client.ironman.mark.SuitPalette.g(c),
         dev.baranhan.viltrumitecore.client.ironman.mark.SuitPalette.b(c))));
      out.add(new PlayerGeoLayer.Part(LAUNCHERS, passes, geo -> pods(geo, k)));
      if (loaded) {
         out.add(new PlayerGeoLayer.Part(LAUNCHER_ROCKETS, ROCKET_PASSES, geo -> rockets(geo, k)));
      }
   }

   /**
    * Launcher opening 0..1: open while the missiles are held (or the synced flap
    * flag), held open and empty for a moment after the volley, then closed.
    * Eased by real time, so it runs the same at any frame rate.
    */
   public static float flapOpen(AbstractClientPlayer player, HeroPublicSnapshot snapshot, float partialTick) {
      float[] st = FLAPS.computeIfAbsent(player, p -> new float[]{0.0F, Float.NaN, -1000.0F, 0.0F});
      float now = player.tickCount + partialTick;
      boolean held = IronManFlags.is(snapshot.heroFlags(), IronManFlags.Field.MISSILE_FLAPS) || IronManView.channel(snapshot, HeroAction.MISSILES);
      if (held) {
         st[2] = now;
         st[3] = 1.0F;
      } else if (now - st[2] > FIRED_HOLD_TICKS || now < st[2]) {
         st[3] = 0.0F;
      }

      boolean open = held || now - st[2] <= FIRED_HOLD_TICKS && now >= st[2];
      float seconds = dev.baranhan.viltrumitecore.client.anim.RealTime.seconds();
      float dt = Float.isNaN(st[1]) ? 0.0F : Math.min(0.1F, seconds - st[1]);
      st[1] = seconds;
      st[0] = Mth.lerp(1.0F - (float)Math.exp(-LAUNCHER_RATE * dt), st[0], open ? 1.0F : 0.0F);
      return st[0];
   }

   /** Rockets sit in the tubes while the missiles are held; empty after the volley. */
   private static boolean launchersLoaded(AbstractClientPlayer player) {
      float[] st = FLAPS.get(player);
      return st != null && st[3] > 0.5F && st[2] >= player.tickCount - 1;
   }

   private static void grow(BakedGeoModel geo, String bone, float scale) {
      GeoBone part = geo.getBone(bone);
      if (part != null) {
         part.scaleX = Math.max(0.2F, scale);
         part.scaleY = scale;
         part.scaleZ = Math.max(0.2F, scale);
         part.hidden = scale <= 0.01F;
      }
   }

   private static void palms(BakedGeoModel geo, float length, boolean right, boolean left) {
      GeoBone r = geo.getBone("flameRight");
      GeoBone l = geo.getBone("flameLeft");
      if (r != null) {
         r.scaleY = length;
         r.hidden = !right;
      }

      if (l != null) {
         l.scaleY = length;
         l.hidden = !left;
      }
   }

   /** Smoothstep of {@code (t - from) / span}, clamped. */
   private static float phase(float t, float from, float span) {
      float u = Mth.clamp((t - from) / span, 0.0F, 1.0F);
      return u * u * (3.0F - 2.0F * u);
   }

   /** Forearm pods: slide out of the plating, then the two muzzle petals fold back. */
   private static void pods(BakedGeoModel geo, float open) {
      float slide = phase(open, 0.0F, 0.6F);
      float petals = phase(open, 0.45F, 0.55F);
      for (String side : new String[]{"Right", "Left"}) {
         float inward = side.equals("Right") ? 1.0F : -1.0F;
         GeoBone pod = geo.getBone("pod" + side);
         if (pod != null) {
            pod.posX = inward * POD_SLIDE * (1.0F - slide);
         }

         GeoBone a = geo.getBone("petalA" + side);
         if (a != null) {
            a.rotX = a.initRotX + (float)Math.toRadians(PETAL_OPEN * petals);
         }

         GeoBone b = geo.getBone("petalB" + side);
         if (b != null) {
            b.rotX = b.initRotX - (float)Math.toRadians(PETAL_OPEN * petals);
         }
      }
   }

   /** Rocket tips follow the pods and poke out of the open muzzle. */
   private static void rockets(BakedGeoModel geo, float open) {
      float slide = phase(open, 0.0F, 0.6F);
      float out = phase(open, 0.6F, 0.4F);
      for (String side : new String[]{"Right", "Left"}) {
         float inward = side.equals("Right") ? 1.0F : -1.0F;
         GeoBone pod = geo.getBone("pod" + side);
         if (pod != null) {
            pod.posX = inward * POD_SLIDE * (1.0F - slide);
         }

         GeoBone tips = geo.getBone("rockets" + side);
         if (tips != null) {
            tips.posY = -ROCKET_SLIDE * out;
            tips.hidden = out < 0.05F;
         }
      }
   }

   private static ResourceLocation geo(String name) {
      return new ResourceLocation("viltrumitecore", "geo/ironman/nano/" + name + ".geo.json");
   }

   private static ResourceLocation missileTexture(String name) {
      return new ResourceLocation("viltrumitecore", "textures/entity/ironman/missiles/" + name + ".png");
   }

   private static ResourceLocation texture(String name) {
      return new ResourceLocation("viltrumitecore", "textures/entity/ironman/nano/" + name + ".png");
   }
}
