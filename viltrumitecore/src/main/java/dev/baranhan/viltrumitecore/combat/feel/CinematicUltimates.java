package dev.baranhan.viltrumitecore.combat.feel;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * The scenes the wave's showpiece ultimates play (wave1-22, T5). One id per ultimate, kept in one
 * place so the four call sites cannot drift apart and a unit test can prove the shipped JSON is
 * there and parses. Every id is {@code viltrumitecore:<file>} in
 * {@code assets/viltrumitecore/cinematics/} — the same id space the client loader scans.
 *
 * <p>The camera itself is decided on the client (comfort settings, {@code watchOthers}, another
 * hero's own camera), so calling {@link Cinematics#play} never forces a shot on anyone.
 */
public final class CinematicUltimates {
   /** Homelander's Roar: the cone fired past the camera. */
   public static final ResourceLocation HOMELANDER_ROAR = new ResourceLocation("viltrumitecore", "homelander_roar");
   /** Thor's WRATH: the build-up under the storm, then the column onto the crosshair. */
   public static final ResourceLocation THOR_WRATH = new ResourceLocation("viltrumitecore", "thor_wrath");
   /** Reinhard's WORLD_CUT: the blade crossing the world, seen from the side. */
   public static final ResourceLocation REINHARD_WORLD_CUT = new ResourceLocation("viltrumitecore", "reinhard_world_cut");
   /** Jinwoo's Monarch's Arise: the rise. The hero's own scene camera keeps the view. */
   public static final ResourceLocation JINWOO_MONARCH = new ResourceLocation("viltrumitecore", "jinwoo_monarch");

   /** Every scene a shipped ultimate plays, one per ultimate, in roster order. */
   public static final List<ResourceLocation> ALL =
      List.of(HOMELANDER_ROAR, THOR_WRATH, REINHARD_WORLD_CUT, JINWOO_MONARCH);

   private CinematicUltimates() {
   }

   /** The client resource a scene id must ship as ({@code assets/<namespace>/cinematics/<path>.json}). */
   public static String resourcePath(ResourceLocation sceneId) {
      return "assets/" + sceneId.getNamespace() + "/cinematics/" + sceneId.getPath() + ".json";
   }
}
