package dev.baranhan.viltrumitecore.hero.wish;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * Od Lagna's Wish sounds: original synthesis ({@code tools/sfx/od_lagna.py}, CC0), listed in
 * {@code tools/assets/od_lagna_sources.md}.
 *
 * <p>The scene is driven on every client from the synced timeline, so the server never sends these as packets and
 * they are not registry entries: each is defined in the separate {@code od_lagna} asset namespace
 * ({@code assets/od_lagna/sounds.json}, audio in {@code binassets/assets/od_lagna/sounds}) and played locally; the
 * subtitles live in {@code src/main/lang-overlays/od_lagna}. The shared sounds.json and lang files stay untouched.
 */
public final class WishSounds {
   public static final String NAMESPACE = "od_lagna";
   public static final SoundEvent CALL = event("call");
   public static final SoundEvent GRANTED = event("granted");
   public static final SoundEvent OPEN = event("open");
   public static final SoundEvent SELECT = event("select");
   public static final SoundEvent CONFIRM = event("confirm");
   public static final SoundEvent RETURN = event("return");

   private WishSounds() {
   }

   private static SoundEvent event(String name) {
      return SoundEvent.createVariableRangeEvent(new ResourceLocation(NAMESPACE, name));
   }
}
