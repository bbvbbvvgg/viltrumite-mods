package dev.baranhan.viltrumitecore.hero.book;

import dev.baranhan.viltrumitecore.balance.HeroTier;
import dev.baranhan.viltrumitecore.entity.SummonOwners;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.ScanInfo;
import dev.baranhan.viltrumitecore.hero.ScanLine;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Writes the true book of a body from public data only (docs/rohan/heavens-door-design.md §4): the game's standard
 * APIs (type, registry, owning mod, attributes, effects, equipment, memories the brain exposes), the hero's public
 * snapshot data (balance tier, scanner lines, combat presence) and registered {@link BookPagesProvider}s. Arguments
 * starting with {@value #TRANSLATE} are lang keys the reader's client translates.
 */
public final class BookReader {
   /** Prefix of an argument that is itself a lang key. */
   public static final String TRANSLATE = "@";
   private static final List<BookPagesProvider> PROVIDERS = new CopyOnWriteArrayList<>();

   private BookReader() {
   }

   public static void registerProvider(BookPagesProvider provider) {
      if (provider != null && !PROVIDERS.contains(provider)) {
         PROVIDERS.add(provider);
      }
   }

   /**
    * The pages of {@code target}'s book as {@code reader} sees them.
    *
    * @param chronicle recent deeds of the body (may be empty)
    */
   public static BookPages read(LivingEntity target, BookProfile profile, List<BookPages.Line> chronicle) {
      BookPages pages = new BookPages();
      identity(target, profile, pages);
      condition(target, pages);
      memory(target, chronicle, pages);
      if (target instanceof Player player) {
         hero(player, pages);
      }
      if (target instanceof BookSubject subject) {
         subject.bookPages(target, pages);
      }
      for (BookPagesProvider provider : PROVIDERS) {
         if (provider.handles(target)) {
            provider.addPages(target, pages);
         }
      }
      if (!profile.notes().isEmpty()) {
         pages.page("book.viltrumitecore.page.notes");
         for (String note : profile.notes()) {
            pages.line("book.viltrumitecore.note." + note);
         }
      }
      if (profile.partialReading()) {
         pages.truncate(2);
         pages.page("book.viltrumitecore.page.illegible");
         pages.line("book.viltrumitecore.line.illegible");
      }
      return pages;
   }

   static void identity(LivingEntity target, BookProfile profile, BookPages pages) {
      pages.page("book.viltrumitecore.page.identity");
      pages.line("book.viltrumitecore.line.name", target.getName().getString());
      ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
      pages.line("book.viltrumitecore.line.species", TRANSLATE + target.getType().getDescriptionId());
      if (type != null) {
         String mod = ModList.get() == null ? type.getNamespace()
            : ModList.get().getModContainerById(type.getNamespace()).map(c -> c.getModInfo().getDisplayName()).orElse(type.getNamespace());
         pages.line("book.viltrumitecore.line.origin", mod, type.toString());
      }
      pages.line("book.viltrumitecore.line.class", TRANSLATE + "book.viltrumitecore.class." + profile.bookClass().name().toLowerCase(Locale.ROOT));
      pages.line("book.viltrumitecore.line.health", fmt(target.getHealth()), fmt(target.getMaxHealth()));
      pages.line("book.viltrumitecore.line.armor", Integer.toString(target.getArmorValue()));
      pages.line("book.viltrumitecore.line.age", Integer.toString(target.tickCount / 20));
      if (target.level() instanceof ServerLevel level) {
         pages.line("book.viltrumitecore.line.place", level.dimension().location().toString(), Integer.toString(target.getBlockX()),
            Integer.toString(target.getBlockY()), Integer.toString(target.getBlockZ()));
         level.getBiome(target.blockPosition()).unwrapKey().ifPresent(k -> pages.line("book.viltrumitecore.line.biome",
            TRANSLATE + "biome." + k.location().getNamespace() + "." + k.location().getPath()));
      }
   }

   static void condition(LivingEntity target, BookPages pages) {
      pages.page("book.viltrumitecore.page.condition");
      attribute(target, Attributes.ATTACK_DAMAGE, "book.viltrumitecore.line.attack", pages);
      attribute(target, Attributes.MOVEMENT_SPEED, "book.viltrumitecore.line.speed", pages);
      attribute(target, Attributes.KNOCKBACK_RESISTANCE, "book.viltrumitecore.line.footing", pages);
      if (target.isOnFire()) {
         pages.line("book.viltrumitecore.line.burning");
      }
      int effects = 0;
      for (MobEffectInstance effect : target.getActiveEffects()) {
         if (effects++ >= 6) {
            break;
         }
         pages.line("book.viltrumitecore.line.effect", TRANSLATE + effect.getDescriptionId(), Integer.toString(effect.getAmplifier() + 1),
            Integer.toString(effect.getDuration() / 20));
      }
      if (effects == 0) {
         pages.line("book.viltrumitecore.line.no_effects");
      }
      ItemStack main = target.getItemBySlot(EquipmentSlot.MAINHAND);
      if (!main.isEmpty()) {
         pages.line("book.viltrumitecore.line.holding", TRANSLATE + main.getDescriptionId());
      }
      int worn = 0;
      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         if (!target.getItemBySlot(slot).isEmpty()) {
            worn++;
         }
      }
      if (worn > 0) {
         pages.line("book.viltrumitecore.line.wearing", Integer.toString(worn));
      }
   }

   static void memory(LivingEntity target, List<BookPages.Line> chronicle, BookPages pages) {
      pages.page("book.viltrumitecore.page.memory");
      if (target instanceof Mob mob && mob.getTarget() != null) {
         pages.line("book.viltrumitecore.line.hunting", mob.getTarget().getName().getString());
      }
      LivingEntity hurt = target.getLastHurtByMob();
      if (hurt != null) {
         pages.line("book.viltrumitecore.line.hurt_by", hurt.getName().getString(),
            Integer.toString(Math.max(0, (target.tickCount - target.getLastHurtByMobTimestamp()) / 20)));
      }
      if (target instanceof OwnableEntity ownable && ownable.getOwner() != null) {
         pages.line("book.viltrumitecore.line.owner", ownable.getOwner().getName().getString());
      } else if (SummonOwners.ownerOf(target) != null) {
         pages.line("book.viltrumitecore.line.owner", SummonOwners.ownerOf(target).getName().getString());
      }
      if (target instanceof Villager villager) {
         var data = villager.getVillagerData();
         ResourceLocation prof = ForgeRegistries.VILLAGER_PROFESSIONS.getKey(data.getProfession());
         if (prof != null) {
            pages.line("book.viltrumitecore.line.profession", TRANSLATE + "entity.minecraft.villager." + prof.getPath(), Integer.toString(data.getLevel()));
         }
         villager.getBrain().getMemory(MemoryModuleType.HOME).ifPresent(home -> place("book.viltrumitecore.line.home", home, pages));
         villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).ifPresent(job -> place("book.viltrumitecore.line.job_site", job, pages));
      }
      for (BookPages.Line line : chronicle) {
         pages.line(line.key(), line.args().toArray(new String[0]));
      }
      if (chronicle.isEmpty() && hurt == null) {
         pages.line("book.viltrumitecore.line.quiet_life");
      }
   }

   static void hero(Player player, BookPages pages) {
      HeroId id = player instanceof HeroPlayer hp ? hp.getHeroId() : HeroId.HUMAN;
      HeroDefinition hero = HeroRegistry.get(player);
      pages.page("book.viltrumitecore.page.hero");
      pages.line("book.viltrumitecore.line.hero", TRANSLATE + "codex.viltrumitecore." + (id == HeroId.VILTRUMITE ? "homelander" : id.key()) + ".name");
      HeroTier tier = hero.balanceProfile(player).tier();
      pages.line("book.viltrumitecore.line.tier", TRANSLATE + "book.viltrumitecore.tier." + tier.name().toLowerCase(Locale.ROOT));
      ScanInfo scan = hero.scanInfo(player);
      for (ScanLine l : scan.protections()) {
         pages.line(l.key(), l.args().toArray(new String[0]));
      }
      for (ScanLine l : scan.weakSpots()) {
         pages.line(l.key(), l.args().toArray(new String[0]));
      }
      for (ScanLine l : scan.conditions()) {
         pages.line(l.key(), l.args().toArray(new String[0]));
      }
      hero.bookPages(player, pages);
   }

   private static void attribute(LivingEntity target, Attribute attribute, String key, BookPages pages) {
      AttributeInstance a = target.getAttribute(attribute);
      if (a != null) {
         pages.line(key, fmt((float)a.getValue()));
      }
   }

   private static void place(String key, GlobalPos pos, BookPages pages) {
      pages.line(key, Integer.toString(pos.pos().getX()), Integer.toString(pos.pos().getY()), Integer.toString(pos.pos().getZ()));
   }

   static String fmt(float v) {
      return v == Math.rint(v) ? Integer.toString((int)v) : String.format(Locale.ROOT, "%.1f", v);
   }

   /** Registry name of a body's type (for "already read" bookkeeping). */
   public static String typeKey(LivingEntity e) {
      if (e instanceof Player p) {
         return "hero:" + (p instanceof HeroPlayer hp ? hp.getHeroId().key() : "human");
      }
      ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
      return key == null ? "unknown" : key.toString();
   }
}
