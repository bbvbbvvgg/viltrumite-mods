package dev.baranhan.viltrumitecore.hero.rohan;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events of Rohan Kishibe and Heaven's Door. Every file comes from a CC0 recording found through Openverse
 * (Freesound), cut and processed for the mod; sources in docs/rohan/audio-sources.md and the JAR's
 * {@code assets/viltrumitecore/sounds/rohan/SOURCES.md}. No anime audio and no voice: spoken lines are subtitles
 * (RohanLines). {@link #init()} runs from the mod constructor.
 */
public final class RohanSounds {
   public static final RegistryObject<SoundEvent> DOOR_SUMMON = reg("rohan.door_summon");
   public static final RegistryObject<SoundEvent> DOOR_TOUCH = reg("rohan.door_touch");
   public static final RegistryObject<SoundEvent> DISMISS = reg("rohan.dismiss");
   public static final RegistryObject<SoundEvent> BOOK_OPEN = reg("rohan.book_open");
   public static final RegistryObject<SoundEvent> PAGE_FLIP = reg("rohan.page_flip");
   public static final RegistryObject<SoundEvent> WRITE = reg("rohan.write");
   public static final RegistryObject<SoundEvent> BOOK_CLOSE = reg("rohan.book_close");
   public static final RegistryObject<SoundEvent> PAGE_RIP = reg("rohan.page_rip");
   public static final RegistryObject<SoundEvent> SKETCH = reg("rohan.sketch");
   public static final RegistryObject<SoundEvent> NIB_THROW = reg("rohan.nib_throw");
   public static final RegistryObject<SoundEvent> NIB_HIT = reg("rohan.nib_hit");
   public static final RegistryObject<SoundEvent> COUNTER = reg("rohan.counter");
   public static final RegistryObject<SoundEvent> WHOOSH = reg("rohan.whoosh");
   public static final RegistryObject<SoundEvent> MANUSCRIPT = reg("rohan.manuscript");
   public static final RegistryObject<SoundEvent> RESIST = reg("rohan.resist");
   public static final RegistryObject<SoundEvent> REFUSE = reg("rohan.refuse");

   private RohanSounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   public static void init() {
   }

   public static void play(Entity at, RegistryObject<SoundEvent> sound, float volume, float pitch) {
      at.level().playSound(null, at.getX(), at.getY(), at.getZ(), sound.get(), SoundSource.PLAYERS, volume, pitch);
   }

   public static float jitter(Entity e, float base) {
      return base + (e.level().random.nextFloat() - 0.5F) * 0.12F;
   }
}
