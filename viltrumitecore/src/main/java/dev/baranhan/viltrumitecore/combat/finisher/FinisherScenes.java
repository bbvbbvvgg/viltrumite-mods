package dev.baranhan.viltrumitecore.combat.finisher;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Scene ids of the finisher (wave1-22, task T6). Two shared shots and one shot per hero that
 * registered a style in {@code HeroDefinition.finisherScene} — shared code picks them through
 * {@link #pick}, never by asking which concrete hero is acting.
 * <p>
 * The JSON files themselves are task T4's (the loader and {@code assets/viltrumitecore/cinematics/}
 * do not exist yet). Until they land every id plays the built-in orbit shot.
 */
public final class FinisherScenes {
   public static final String NAMESPACE = "viltrumitecore";
   /** Shared: an uppercut that sends the victim into the sky (an ordinary body). */
   public static final ResourceLocation GENERIC_A = id("finisher_generic_a");
   /** Shared: a slam that drives the victim into the ground (a big body or a boss). */
   public static final ResourceLocation GENERIC_B = id("finisher_generic_b");
   /** Homelander: the flagship race's execution, a carry-and-drop from above. */
   public static final ResourceLocation HOMELANDER = id("finisher_homelander");
   /** Thor: the hammer brought down as a fatality. */
   public static final ResourceLocation THOR = id("finisher_thor");

   private FinisherScenes() {
   }

   /** The hero's own shot when it has one, else the shared slam for a boss and the uppercut otherwise. */
   public static ResourceLocation pick(@Nullable ResourceLocation heroScene, boolean boss) {
      if (heroScene != null) {
         return heroScene;
      }
      return boss ? GENERIC_B : GENERIC_A;
   }

   private static ResourceLocation id(String path) {
      return new ResourceLocation(NAMESPACE, path);
   }
}
