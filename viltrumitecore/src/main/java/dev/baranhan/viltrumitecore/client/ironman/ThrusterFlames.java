package dev.baranhan.viltrumitecore.client.ironman;

import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.PlayerGeoLayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ironman.IronManVariant;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkSpec;
import dev.baranhan.viltrumiteflight.util.FlightState;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Repulsor flames as geo parts on the player (spec §7.5): feet and palms,
 * plus two back stabilizers in hover. Length by flight mode and throttle,
 * flicker by time; none while gliding, during the nano wave or without flight.
 */
public final class ThrusterFlames {
   public static final ResourceLocation FEET = geo("feet");
   public static final ResourceLocation PALMS = geo("palms");
   public static final ResourceLocation STABILIZER = geo("stabilizer");
   /** Nano stabilizer rods (Satsu nano_stabilizer); the four stabilizer flames sit on their tips. */
   public static final ResourceLocation STABILIZER_RODS = new ResourceLocation("viltrumitecore", "geo/ironman/nano/stabilizer.geo.json");
   private static final List<PlayerGeoLayer.Pass> RODS_PASSES = List.of(
      PlayerGeoLayer.Pass.cutout(new ResourceLocation("viltrumitecore", "textures/entity/ironman/nano/stabilizer.png")),
      PlayerGeoLayer.Pass.glow(new ResourceLocation("viltrumitecore", "textures/entity/ironman/nano/stabilizer_glow.png")));
   private static final int TEXTURE_FRAMES = 8;
   private static final ResourceLocation[] TEXTURES = new ResourceLocation[TEXTURE_FRAMES];

   static {
      for (int i = 0; i < TEXTURE_FRAMES; i++) {
         TEXTURES[i] = new ResourceLocation("viltrumitecore", "textures/entity/hero/ironman_flame_" + i + ".png");
      }
   }

   private ThrusterFlames() {
   }

   /** One flame texture frame (shared with the repulsor palm glow). */
   public static ResourceLocation texture(int frame) {
      return TEXTURES[Math.floorMod(frame, TEXTURE_FRAMES)];
   }

   public enum Mode {
      NONE,
      HOVER,
      CRUISE,
      SONIC
   }

   /** Flame lengths as bone scale (1 = geo length). */
   public record Lengths(float feet, float palms, float stabilizers) {
      public static final Lengths NONE = new Lengths(0.0F, 0.0F, 0.0F);

      public boolean any() {
         return this.feet > 0.0F || this.palms > 0.0F || this.stabilizers > 0.0F;
      }
   }

   /** Thruster mode: only with the full suit, flying and not gliding. */
   public static Mode mode(boolean suitReady, boolean glide, FlightState flight) {
      if (!suitReady || glide || flight == null) {
         return Mode.NONE;
      }

      return switch (flight) {
         case HOVER -> Mode.HOVER;
         case CRUISE -> Mode.CRUISE;
         case SONIC -> Mode.SONIC;
         default -> Mode.NONE;
      };
   }

   public static Lengths lengths(Mode mode, float throttle) {
      float t = Math.max(0.0F, Math.min(1.0F, throttle));
      return switch (mode) {
         case HOVER -> new Lengths(1.4F, 1.3F, 0.8F);
         case CRUISE -> new Lengths(1.4F + 1.2F * t, 1.0F + 0.4F * t, 0.0F);
         case SONIC -> new Lengths(3.2F, 1.8F, 0.0F);
         case NONE -> Lengths.NONE;
      };
   }

   /** Mode of a player this frame (synced flight state + snapshot). */
   public static Mode mode(AbstractClientPlayer player, HeroPublicSnapshot snapshot) {
      FlightState flight = player instanceof ViltrumiteFlightPlayer flyer ? flyer.getFlightState() : FlightState.NONE;
      return mode(IronManView.suitReady(snapshot), IronManView.glide(snapshot), flight);
   }

   public static float throttle(AbstractClientPlayer player, float partialTick) {
      return player instanceof ViltrumiteFlightPlayer flyer ? flyer.getLerpedFlightThrottle(partialTick) : 0.0F;
   }

   /** Mark 15 flies silent without flames (MarkSpec.silentFlight). */
   public static boolean silent(@Nullable HeroPublicSnapshot snapshot) {
      MarkId mark = snapshot == null ? null : IronManVariant.mark(snapshot.variant());
      return mark != null && MarkSpec.of(mark).silentFlight();
   }

   /** Adds the flame parts for this frame. First person: palms only. */
   static void collect(AbstractClientPlayer player, HeroPublicSnapshot snapshot, float partialTick, boolean firstPerson, List<PlayerGeoLayer.Part> out) {
      if (silent(snapshot)) {
         return;
      }

      Mode mode = mode(player, snapshot);
      Lengths lengths = lengths(mode, throttle(player, partialTick));
      if (!lengths.any()) {
         return;
      }

      float time = player.tickCount + partialTick + (player.getId() % 16) * 3.7F;
      ResourceLocation texture = TEXTURES[(int)(time * 0.75F) % TEXTURE_FRAMES];
      boolean sonic = mode == Mode.SONIC;
      // Two additive passes: tinted outer flame + white-hot core (one pass reads too faint).
      List<PlayerGeoLayer.Pass> passes = List.of(
         sonic ? PlayerGeoLayer.Pass.glow(texture, 0.85F, 0.95F, 1.0F) : PlayerGeoLayer.Pass.glow(texture, 0.45F, 0.8F, 1.0F),
         PlayerGeoLayer.Pass.glow(texture, 0.8F, 0.9F, 1.0F));
      if (lengths.palms() > 0.0F && (!firstPerson || mode == Mode.HOVER)) {
         out.add(new PlayerGeoLayer.Part(PALMS, passes, geo -> flicker(geo, lengths.palms(), time, "flameRight", "flameLeft")));
      }

      if (firstPerson) {
         return;
      }

      out.add(new PlayerGeoLayer.Part(FEET, passes, geo -> flicker(geo, lengths.feet(), time, "flameRight", "flameLeft")));
      // Stabilizers are nano hardware: marks hover on feet and palms only.
      if (lengths.stabilizers() > 0.0F && IronManVariant.mark(snapshot.variant()) == null) {
         out.add(new PlayerGeoLayer.Part(STABILIZER_RODS, RODS_PASSES));
         out.add(new PlayerGeoLayer.Part(STABILIZER, passes,
            geo -> flicker(geo, lengths.stabilizers(), time, "stabRight", "stabLeft", "stabRightLow", "stabLeftLow")));
      }
   }

   /** Length along the flame axis (bone Y) plus a small width wobble; each side out of phase. */
   public static void flicker(BakedGeoModel geo, float length, float time, String... bones) {
      for (int i = 0; i < bones.length; i++) {
         GeoBone bone = geo.getBone(bones[i]);
         if (bone == null) {
            continue;
         }

         float phase = time * 1.9F + i * 2.1F;
         float k = length * (0.86F + 0.08F * (float)Math.sin(phase) + 0.06F * (float)Math.sin(phase * 2.7F + 1.3F));
         float wobble = 1.0F + 0.07F * (float)Math.sin(phase * 1.6F + 0.5F);
         bone.scaleY = k;
         bone.scaleX = wobble;
         bone.scaleZ = wobble;
      }
   }

   private static ResourceLocation geo(String name) {
      return new ResourceLocation("viltrumitecore", "geo/ironman/flames/" + name + ".geo.json");
   }
}
