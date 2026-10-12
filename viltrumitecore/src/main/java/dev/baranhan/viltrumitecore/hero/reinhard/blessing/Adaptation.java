package dev.baranhan.viltrumitecore.hero.reinhard.blessing;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Divine Protection of Protection Acquisition (spec §4.8), pure. Only divine
 * hits count: {@link ReinhardRules#ADAPT_HITS} hits of one damage type in a
 * row make him immune to that type until death (the last of them still
 * lands). Another type restarts the streak. Ordinary damage never calls it.
 */
public record Adaptation(@Nullable ResourceLocation streakType, int streak, Set<ResourceLocation> immune) {
   public static final Adaptation NONE = new Adaptation(null, 0, Set.of());
   public static final String NBT_KEY = "Adaptation";

   public Adaptation {
      immune = Set.copyOf(immune);
   }

   public boolean immuneTo(ResourceLocation type) {
      return this.immune.contains(type);
   }

   /** Registers one landed divine hit of {@code type}. */
   public Adaptation onDivineHit(ResourceLocation type) {
      int next = type.equals(this.streakType) ? this.streak + 1 : 1;
      if (next >= ReinhardRules.ADAPT_HITS) {
         Set<ResourceLocation> grown = new HashSet<>(this.immune);
         grown.add(type);
         return new Adaptation(type, next, grown);
      }

      return new Adaptation(type, next, this.immune);
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      if (this.streakType != null) {
         tag.putString("Type", this.streakType.toString());
         tag.putInt("Streak", this.streak);
      }

      ListTag list = new ListTag();
      for (ResourceLocation type : this.immune) {
         list.add(StringTag.valueOf(type.toString()));
      }

      tag.put("Immune", list);
      return tag;
   }

   public static Adaptation load(CompoundTag tag) {
      ResourceLocation type = tag.contains("Type", Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString("Type")) : null;
      Set<ResourceLocation> immune = new HashSet<>();
      ListTag list = tag.getList("Immune", Tag.TAG_STRING);
      for (int i = 0; i < list.size(); i++) {
         ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
         if (id != null) {
            immune.add(id);
         }
      }

      return new Adaptation(type, type == null ? 0 : Math.max(0, tag.getInt("Streak")), immune);
   }
}
