package dev.baranhan.viltrumitecore.hero.homelander;

import dev.baranhan.viltrumiteflight.util.FlightState;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.LegacyKit;

/** Homelander ability ids, kit subset and default slot order. */
public final class HomelanderAbilities {
   public static final String LASERS = "homelander:lasers";
   public static final String FOCUS = "homelander:focus";
   public static final String ROAR = "homelander:roar";
   /** HeroPublicSnapshot.heroFlags bits. */
   public static final int FLAG_LASER = 0;
   public static final int FLAG_FOCUS = 1;
   /** HeroPublicSnapshot.cooldowns index of the roar. */
   public static final int ROAR_COOLDOWN_INDEX = 0;
   /** Own slots in panel order. */
   private static final String[] OWN = {LASERS, FOCUS, ROAR};
   /** Shared kit abilities this hero keeps (spec §1.2). */
   private static final String[] KIT = {LegacyKit.PUNCH, LegacyKit.DASH, LegacyKit.THUNDERCLAP};
   /** Flight utility indicators he keeps (no speed lock: he has no super speed). */
   private static final String[] UTILITY = {"viltrumite:fast_takeoff", "viltrumite:supersonic_flight"};
   /** Page 1: punch and dash keep their Viltrumite keys (R, Y). */
   private static final String[] LOADOUT = {LegacyKit.PUNCH, LegacyKit.DASH, LASERS, FOCUS, ROAR, LegacyKit.THUNDERCLAP};

   private HomelanderAbilities() {
   }

   public static String[] slotIds() {
      return OWN.clone();
   }

   public static boolean owns(String abilityId) {
      if (abilityId == null) {
         return false;
      }

      for (String id : OWN) {
         if (id.equals(abilityId)) {
            return true;
         }
      }

      for (String id : UTILITY) {
         if (id.equals(abilityId)) {
            return true;
         }
      }

      return ownsKit(abilityId);
   }

   public static boolean ownsKit(String abilityId) {
      for (String id : KIT) {
         if (id.equals(abilityId)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Homelander draws every owned slot with his own icon: own, kit and the two flight-bar
    * indicators (fast_takeoff, supersonic_flight), art from tools/assets/make_hero_icons.py.
    */
   @javax.annotation.Nullable
   public static net.minecraft.resources.ResourceLocation icon(String abilityId) {
      if (!owns(abilityId)) {
         return null;
      }

      return new net.minecraft.resources.ResourceLocation("viltrumitecore", "textures/gui/ability/homelander/" + abilityId.substring(abilityId.indexOf(':') + 1) + ".png");
   }

   /** Panel entries for the own slots, greyed from the public snapshot. */
   public static java.util.List<dev.baranhan.viltrumitecore.ability.ViltrumiteAbility> panelAbilities() {
      java.util.List<dev.baranhan.viltrumitecore.ability.ViltrumiteAbility> list = new java.util.ArrayList<>();
      for (String id : OWN) {
         String name = "homelander_" + id.substring(id.indexOf(':') + 1);
         HeroAction action = actionFor(id);
         list.add(new dev.baranhan.viltrumitecore.ability.ViltrumiteAbility(
            id, icon(id), "ability.viltrumitecore." + name + ".name", "ability.viltrumitecore." + name + ".desc", 0, player -> slotGrey(player, action)));
      }

      return list;
   }

   /** Lasers/focus grey while the eyes are locked by overheat; roar grey on cooldown. */
   public static boolean slotGrey(net.minecraft.world.entity.player.Player player, HeroAction action) {
      if (!(player instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer heroPlayer)) {
         return false;
      }

      dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
      if (snapshot == null || snapshot.heroId() != dev.baranhan.viltrumitecore.hero.HeroId.HOMELANDER) {
         return false;
      }

      return switch (action) {
         case LASERS, FOCUS -> snapshot.resourceLocked();
         case ROAR -> snapshot.cooldowns().length > ROAR_COOLDOWN_INDEX && snapshot.cooldowns()[ROAR_COOLDOWN_INDEX] > 0;
         default -> false;
      };
   }

   public static String[] defaultLoadout() {
      String[] loadout = new String[18];
      java.util.Arrays.fill(loadout, "");
      System.arraycopy(LOADOUT, 0, loadout, 0, LOADOUT.length);
      return loadout;
   }

   /**
    * Spec §5.5: the dash works only in flight. The shared dash starts from a
    * hover (cruise/sonic are already a dash), so in flight that means HOVER.
    */
   public static boolean dashAllowed(FlightState state) {
      return state == FlightState.HOVER;
   }

   /** Input action for an own slot, or null. */
   public static HeroAction actionFor(String abilityId) {
      if (LASERS.equals(abilityId)) {
         return HeroAction.LASERS;
      } else if (FOCUS.equals(abilityId)) {
         return HeroAction.FOCUS;
      } else {
         return ROAR.equals(abilityId) ? HeroAction.ROAR : null;
      }
   }
}
