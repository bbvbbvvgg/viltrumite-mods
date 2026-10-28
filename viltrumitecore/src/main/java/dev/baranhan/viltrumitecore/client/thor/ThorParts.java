package dev.baranhan.viltrumitecore.client.thor;

import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.PlayerGeoLayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.thor.MjolnirPlace;
import dev.baranhan.viltrumiteflight.util.FlightState;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import java.util.List;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Thor's 3D parts on the player model (PlayerGeoLayer): beard and belly
 * (third person; the belly hides under a chestplate), and Mjölnir in the
 * right hand (both views) or on the right hip. Models converted from the
 * source pack by tools/assets/convert_thor.py.
 *
 * <p>In flight the in-hand hammer whirls (MjolnirSpin, FX id
 * {@code thor:hammer_spin}): only the MjolnirBody bone turns about the fist,
 * followed by translucent ghost copies as motion blur; the arm pose is not
 * touched here. The in-hand part also records the hammer anchors (ThorAnchors).
 */
public final class ThorParts implements PlayerGeoLayer.Provider {
   public static final ResourceLocation BEARD = geo("beard");
   public static final ResourceLocation BELLY = geo("belly");
   public static final ResourceLocation MJOLNIR_HAND = geo("mjolnir_hand");
   public static final ResourceLocation MJOLNIR_BELT = geo("mjolnir_belt");
   private static final List<PlayerGeoLayer.Pass> BEARD_PASSES = List.of(PlayerGeoLayer.Pass.cutout(texture("beard")));
   private static final List<PlayerGeoLayer.Pass> BELLY_PASSES = List.of(PlayerGeoLayer.Pass.cutout(texture("belly")));
   /** The rune lights glow always, as the source pack's lights texture does. */
   private static final List<PlayerGeoLayer.Pass> MJOLNIR_PASSES = List.of(
      PlayerGeoLayer.Pass.cutout(texture("mjolnir")), PlayerGeoLayer.Pass.glow(texture("mjolnir_glow")));
   private static final String SPIN_BONE = "MjolnirBody";
   private static final ResourceLocation MJOLNIR_TEXTURE = texture("mjolnir");
   private static final ResourceLocation MJOLNIR_GLOW = texture("mjolnir_glow");

   @Override
   public void collect(AbstractClientPlayer player, float partialTick, boolean firstPerson, List<PlayerGeoLayer.Part> out) {
      HeroPublicSnapshot snapshot = ThorView.of(player);
      if (snapshot == null) {
         return;
      }

      MjolnirPlace place = ThorView.place(snapshot, player);
      if (place == MjolnirPlace.HAND) {
         hammerInHand(player, partialTick, out);
      }

      if (firstPerson) {
         return;
      }

      out.add(new PlayerGeoLayer.Part(BEARD, BEARD_PASSES));
      if (player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
         out.add(new PlayerGeoLayer.Part(BELLY, BELLY_PASSES));
      }

      if (place == MjolnirPlace.BELT) {
         out.add(new PlayerGeoLayer.Part(MJOLNIR_BELT, MJOLNIR_PASSES));
      }
   }

   private static void hammerInHand(AbstractClientPlayer player, float partialTick, List<PlayerGeoLayer.Part> out) {
      FlightState flight = player instanceof ViltrumiteFlightPlayer flyer ? flyer.getFlightState() : FlightState.NONE;
      MjolnirSpin.State spin = MjolnirSpin.update(player, player.tickCount + (double)partialTick, MjolnirSpin.targetRps(flight, true));
      PlayerGeoLayer.FrameSink anchors = (frame, top) -> ThorAnchors.capture(player, frame, top);
      if (!spin.spinning()) {
         out.add(new PlayerGeoLayer.Part(MJOLNIR_HAND, MJOLNIR_PASSES, null, anchors));
         return;
      }

      float angle = spin.angle;
      float rps = spin.rps;
      out.add(new PlayerGeoLayer.Part(MJOLNIR_HAND, MJOLNIR_PASSES, geo -> spin(geo, angle), anchors));
      for (int i = 1; i <= MjolnirSpin.GHOSTS; i++) {
         float alpha = MjolnirSpin.ghostAlpha(rps, i);
         if (alpha < 0.02F) {
            continue;
         }

         float ghost = MjolnirSpin.ghostAngle(angle, rps, i);
         out.add(new PlayerGeoLayer.Part(MJOLNIR_HAND, List.of(PlayerGeoLayer.Pass.translucent(MJOLNIR_TEXTURE, alpha),
            PlayerGeoLayer.Pass.glow(MJOLNIR_GLOW, alpha, alpha, alpha)), geo -> spin(geo, ghost)));
      }
   }

   private static void spin(BakedGeoModel geo, float angle) {
      GeoBone body = geo.getBone(SPIN_BONE);
      if (body == null) {
         return;
      }

      float[] pose = MjolnirSpin.bodyPose(angle);
      body.rotX = body.initRotX + pose[0];
      body.posX = pose[1];
      body.posY = pose[2];
      body.posZ = pose[3];
   }

   private static ResourceLocation geo(String name) {
      return new ResourceLocation("viltrumitecore", "geo/thor/" + name + ".geo.json");
   }

   private static ResourceLocation texture(String name) {
      return new ResourceLocation("viltrumitecore", "textures/entity/thor/" + name + ".png");
   }
}
