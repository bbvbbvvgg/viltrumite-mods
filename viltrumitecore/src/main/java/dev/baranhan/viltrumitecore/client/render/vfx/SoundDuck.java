package dev.baranhan.viltrumitecore.client.render.vfx;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/**
 * Shared volume ducking: each owner lowers one category ({@code MASTER} = all)
 * to a target with a fade in client ticks, sparing an exempt set of sound ids
 * (the owner's own sounds). The factor of a sound = the minimum over the
 * owners that match it. Applied by {@code SoundEngineVolumeMixin} on
 * {@code SoundEngine.calculateVolume(SoundInstance)}; {@link SoundDuckEvents}
 * ticks the fades and refreshes playing channels. No hero names here.
 */
public final class SoundDuck {
   public static final SoundDuck INSTANCE = new SoundDuck();
   private static final float EPS = 1.0E-4F;

   private static final class Entry {
      SoundSource category;
      float target;
      float step;
      Set<ResourceLocation> exempt;
      float level = 1.0F;
      boolean releasing;
   }

   private final Map<Object, Entry> entries = new LinkedHashMap<>();

   public void duck(Object owner, SoundSource category, float target, int fadeTicks, Set<ResourceLocation> exempt) {
      Entry entry = this.entries.computeIfAbsent(owner, key -> new Entry());
      entry.category = category;
      entry.target = Math.max(0.0F, Math.min(1.0F, target));
      entry.step = (1.0F - entry.target) / (float)Math.max(1, fadeTicks);
      entry.exempt = Set.copyOf(exempt);
      entry.releasing = false;
      if (fadeTicks <= 0) {
         entry.level = entry.target;
      }
   }

   /** Fades the owner's duck back to full volume, then forgets it. */
   public void release(Object owner) {
      Entry entry = this.entries.get(owner);
      if (entry != null) {
         entry.releasing = true;
      }
   }

   public void clear() {
      this.entries.clear();
   }

   public boolean active() {
      return !this.entries.isEmpty();
   }

   /** One client tick of every fade; true when a level changed (playing channels need a refresh). */
   public boolean tick() {
      boolean changed = false;
      Iterator<Entry> it = this.entries.values().iterator();
      while (it.hasNext()) {
         Entry entry = it.next();
         float before = entry.level;
         if (entry.releasing) {
            entry.level = Math.min(1.0F, entry.level + entry.step);
            if (entry.level >= 1.0F - EPS) {
               it.remove();
               changed = true;
               continue;
            }
         } else {
            entry.level = Math.max(entry.target, entry.level - entry.step);
         }
         changed |= entry.level != before;
      }

      return changed;
   }

   /** Volume multiplier of one playing sound. No allocation (runs for every channel). */
   public float volumeFactor(SoundSource category, ResourceLocation id) {
      float factor = 1.0F;
      for (Entry entry : this.entries.values()) {
         if ((entry.category == SoundSource.MASTER || entry.category == category) && !exempt(id, entry.exempt)) {
            factor = Math.min(factor, entry.level);
         }
      }

      return factor;
   }

   /** Pure: the minimum of the targets, 1 when none. */
   public static float factor(Collection<Float> targets) {
      float factor = 1.0F;
      for (float target : targets) {
         factor = Math.min(factor, target);
      }

      return factor;
   }

   /** Pure: whether a sound id is spared. */
   public static boolean exempt(ResourceLocation id, Set<ResourceLocation> set) {
      return id != null && set.contains(id);
   }
}
