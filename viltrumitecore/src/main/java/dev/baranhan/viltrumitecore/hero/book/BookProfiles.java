package dev.baranhan.viltrumitecore.hero.book;

import dev.baranhan.viltrumitecore.balance.CombatBalance;
import dev.baranhan.viltrumitecore.entity.Bystander;
import dev.baranhan.viltrumitecore.entity.SummonOwners;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.Tags;

/** Resolves the book of any living body (players through their hero, subjects themselves, the rest by class). */
public final class BookProfiles {
   /** Bodies with at least this much health count as bosses when nothing else says so. */
   public static final float BOSS_HEALTH = 150.0F;

   private BookProfiles() {
   }

   private static final java.util.List<java.util.function.BiFunction<Player, BookProfile, BookProfile>> PLAYER_MODIFIERS =
      new java.util.concurrent.CopyOnWriteArrayList<>();

   /**
    * Adds a player-book modifier applied after {@link dev.baranhan.viltrumitecore.hero.HeroDefinition#bookProfile}: for
    * heroes whose class is too large to grow (Iron Man) or for add-ons. Must return the input when it does not apply.
    */
   public static void registerPlayerModifier(java.util.function.BiFunction<Player, BookProfile, BookProfile> modifier) {
      PLAYER_MODIFIERS.add(modifier);
   }

   public static BookProfile resolve(LivingEntity e) {
      if (e instanceof Player player) {
         BookProfile profile = HeroRegistry.get(player).bookProfile(player);
         for (java.util.function.BiFunction<Player, BookProfile, BookProfile> m : PLAYER_MODIFIERS) {
            profile = m.apply(player, profile);
         }
         return profile;
      }
      BookProfile base = classify(e);
      return e instanceof BookSubject subject ? subject.bookProfile(base) : base;
   }

   /** The class-based book of a non-player body. */
   public static BookProfile classify(LivingEntity e) {
      if (CombatBalance.isBoss(e) || e.getType().is(Tags.EntityTypes.BOSSES) || e.getMaxHealth() >= BOSS_HEALTH) {
         return BookProfile.BOSS;
      }
      if (SummonOwners.isSummon(e) || owner(e) != null && !(e instanceof Animal)) {
         return BookProfile.SUMMON;
      }
      if (Bystander.is(e)) {
         return BookProfile.CITIZEN;
      }
      if (e instanceof Warden) {
         // blind: the drawing means nothing to it; the touch still opens it, briefly
         return BookProfile.SMART_MOB.withSenses(true, false).withWindow(60).withNotes("blind");
      }
      if (e instanceof Animal || e instanceof WaterAnimal || e instanceof AmbientCreature) {
         return BookProfile.ANIMAL;
      }
      if (e instanceof AbstractVillager || e instanceof AbstractIllager || e instanceof AbstractPiglin || e instanceof Witch
         || e instanceof AbstractGolem) {
         return BookProfile.SMART_MOB;
      }
      return BookProfile.SIMPLE_MOB;
   }

   @Nullable
   static Object owner(LivingEntity e) {
      return e instanceof OwnableEntity ownable ? ownable.getOwnerUUID() : null;
   }
}
