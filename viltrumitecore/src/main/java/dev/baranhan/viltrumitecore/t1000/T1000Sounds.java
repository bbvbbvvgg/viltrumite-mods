package dev.baranhan.viltrumitecore.t1000;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events of the T-1000 (src/main/sound-overlays/t1000.json). Every file is an own CC0 recipe
 * (tools/sfx/t1000.py, sources in tools/assets/t1000_sources.md): no film audio, no vanilla stand-ins. While it is
 * disguised it sounds like any citizen (vanilla player steps and hurt sounds); these play only after the reveal.
 */
public final class T1000Sounds {
   /** Liquid metal moving: quiet wet glide while it walks or crawls in metal form. */
   public static final RegistryObject<SoundEvent> FLOW = reg("t1000.flow");
   /** A step of the metal body. */
   public static final RegistryObject<SoundEvent> STEP = reg("t1000.step");
   /** Metal stretching (an arm shooting out, the body pulling long). */
   public static final RegistryObject<SoundEvent> STRETCH = reg("t1000.stretch");
   /** A hand flowing into a blade, hook or spike. */
   public static final RegistryObject<SoundEvent> MORPH = reg("t1000.morph");
   /** A blade swing. */
   public static final RegistryObject<SoundEvent> SWISH = reg("t1000.swish");
   /** A blade striking a body. */
   public static final RegistryObject<SoundEvent> BLADE_HIT = reg("t1000.blade_hit");
   /** Blade against blade or armour: the parry. */
   public static final RegistryObject<SoundEvent> CLANG = reg("t1000.clang");
   /** A shot or blow sinking into the liquid metal. */
   public static final RegistryObject<SoundEvent> IMPACT = reg("t1000.impact");
   /** A heavy blow denting the whole body. */
   public static final RegistryObject<SoundEvent> DEFORM = reg("t1000.deform");
   /** Wounds closing, droplets flowing home. */
   public static final RegistryObject<SoundEvent> REGEN = reg("t1000.regen");
   /** Chrome spreading over the skin (the reveal) or flowing back (concealment). */
   public static final RegistryObject<SoundEvent> REVEAL = reg("t1000.reveal");
   /** Collapsing into a puddle. */
   public static final RegistryObject<SoundEvent> SPLASH = reg("t1000.splash");
   /** Rising out of the puddle, reassembling. */
   public static final RegistryObject<SoundEvent> REASSEMBLE = reg("t1000.reassemble");
   /** Frozen metal cracking. */
   public static final RegistryObject<SoundEvent> CRACK = reg("t1000.crack");
   /** Frozen metal shattering. */
   public static final RegistryObject<SoundEvent> SHATTER = reg("t1000.shatter");
   /** The final destruction. */
   public static final RegistryObject<SoundEvent> DESTROY = reg("t1000.destroy");

   private T1000Sounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   /** Class-load hook: the holders above register on first touch (mod constructor). */
   public static void init() {
   }
}
