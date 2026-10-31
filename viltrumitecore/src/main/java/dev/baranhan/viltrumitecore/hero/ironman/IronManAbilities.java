package dev.baranhan.viltrumitecore.hero.ironman;

import dev.baranhan.viltrumitecore.ability.ViltrumiteAbility;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Iron Man ability ids and default slot order (spec §6.2). */
public final class IronManAbilities {
   public static final String SUIT = "ironman:suit";
   public static final String UNIBEAM = "ironman:unibeam";
   public static final String MISSILES = "ironman:missiles";
   public static final String NANO_ARSENAL = "ironman:nano_arsenal";
   public static final String SCAN = "ironman:scan";
   public static final String COUNTERMEASURES = "ironman:countermeasures";
   public static final String HELMET = "ironman:helmet";
   public static final String VERONICA = "ironman:veronica";
   /** Stage 6: the Iron Legion (call up to three marks, then orders). */
   public static final String LEGION = "ironman:legion";
   /** Page 1 (slots 0-5), keys R Y Z V: everything for combat — Unibeam, missiles, nano arsenal / signature, countermeasures. */
   public static final int UNIBEAM_SLOT = 0;
   public static final int MISSILES_SLOT = 1;
   public static final int NANO_ARSENAL_SLOT = 2;
   public static final int COUNTERMEASURES_SLOT = 3;
   /** Page 2 (slots 6-11), keys R Y Z V: scan, Veronica, helmet, suit (utility and looks). */
   public static final int SCAN_SLOT = 6;
   public static final int VERONICA_SLOT = 6 + 1;
   public static final int HELMET_SLOT = 6 + 2;
   public static final int SUIT_SLOT = 6 + 3;
   /** Page 2, last slot of the second row: the Iron Legion (stage 6). */
   public static final int LEGION_SLOT = 11;
   /** Own slots in panel order. Later stages append. */
   private static final String[] OWN = {UNIBEAM, MISSILES, NANO_ARSENAL, COUNTERMEASURES, SCAN, VERONICA, HELMET, SUIT, LEGION};

   private IronManAbilities() {
   }

   public static String[] slotIds() {
      return OWN.clone();
   }

   public static boolean owns(@Nullable String abilityId) {
      if (abilityId == null) {
         return false;
      }

      for (String id : OWN) {
         if (id.equals(abilityId)) {
            return true;
         }
      }

      return false;
   }

   /** Slot 3 in a mark shows the mark's signature (spec §6.2). */
   @Nullable
   public static ResourceLocation icon(String abilityId, @Nullable dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot snapshot) {
      if (snapshot != null && snapshot.heroId() == dev.baranhan.viltrumitecore.hero.HeroId.IRON_MAN && hulkActive(snapshot)) {
         // Hulkbuster: slots 1–3 are grab, jump slam, hop (spec §14.3).
         String hulk = switch (abilityId) {
            case UNIBEAM -> "hulk_grab";
            case MISSILES -> "hulk_slam";
            case NANO_ARSENAL -> "hulk_hop";
            default -> null;
         };
         if (hulk != null) {
            return new ResourceLocation("viltrumitecore", "textures/gui/ability/ironman/" + hulk + ".png");
         }
      }

      if (NANO_ARSENAL.equals(abilityId) && snapshot != null && snapshot.heroId() == dev.baranhan.viltrumitecore.hero.HeroId.IRON_MAN) {
         dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId mark = IronManVariant.mark(snapshot.variant());
         if (mark != null) {
            return signatureIcon(mark);
         }
      }

      return icon(abilityId);
   }

   static boolean hulkActive(dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot snapshot) {
      return IronManFlags.get(snapshot.heroFlags(), IronManFlags.Field.HULKBUSTER_PHASE) == dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer.Phase.ACTIVE.ordinal();
   }

   /** Panel art of a mark signature: tools/assets/make_hero_icons.py (ironman_icon_art.py). */
   public static ResourceLocation signatureIcon(dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId mark) {
      return new ResourceLocation("viltrumitecore", "textures/gui/ability/ironman/sig_" + mark.signatureKey() + ".png");
   }

   @Nullable
   public static ResourceLocation icon(String abilityId) {
      if (!owns(abilityId)) {
         return null;
      }

      // Panel art: tools/assets/make_hero_icons.py (ironman_icon_art.py).
      return new ResourceLocation("viltrumitecore", "textures/gui/ability/ironman/" + abilityId.substring(abilityId.indexOf(':') + 1) + ".png");
   }

   public static List<ViltrumiteAbility> panelAbilities() {
      List<ViltrumiteAbility> list = new ArrayList<>();
      for (String id : OWN) {
         String name = "ironman_" + id.substring(id.indexOf(':') + 1);
         list.add(new ViltrumiteAbility(id, icon(id), "ability.viltrumitecore." + name + ".name", "ability.viltrumitecore." + name + ".desc", 0,
            player -> grey(id, player)));
      }

      return list;
   }

   public static String[] defaultLoadout() {
      String[] loadout = new String[18];
      Arrays.fill(loadout, "");
      loadout[SUIT_SLOT] = SUIT;
      loadout[UNIBEAM_SLOT] = UNIBEAM;
      loadout[MISSILES_SLOT] = MISSILES;
      loadout[NANO_ARSENAL_SLOT] = NANO_ARSENAL;
      loadout[SCAN_SLOT] = SCAN;
      loadout[COUNTERMEASURES_SLOT] = COUNTERMEASURES;
      loadout[HELMET_SLOT] = HELMET;
      loadout[VERONICA_SLOT] = VERONICA;
      loadout[LEGION_SLOT] = LEGION;
      return loadout;
   }

   /**
    * Former default layouts (scan/flares/Veronica/helmet on page 2, suit on B).
    * A save still holding one exactly is moved to the current default on login.
    */
   public static String[][] previousDefaultLoadouts() {
      String[] old = new String[18];
      Arrays.fill(old, "");
      old[0] = UNIBEAM;
      old[1] = MISSILES;
      old[2] = NANO_ARSENAL;
      old[6] = SCAN;
      old[7] = COUNTERMEASURES;
      old[8] = VERONICA;
      old[9] = HELMET;
      old[10] = SUIT;
      // Stages 4–5 default (before the Legion took slot 11).
      String[] stage5 = new String[18];
      Arrays.fill(stage5, "");
      stage5[SUIT_SLOT] = SUIT;
      stage5[UNIBEAM_SLOT] = UNIBEAM;
      stage5[MISSILES_SLOT] = MISSILES;
      stage5[NANO_ARSENAL_SLOT] = NANO_ARSENAL;
      stage5[SCAN_SLOT] = SCAN;
      stage5[COUNTERMEASURES_SLOT] = COUNTERMEASURES;
      stage5[HELMET_SLOT] = HELMET;
      stage5[VERONICA_SLOT] = VERONICA;
      return new String[][]{old, stage5};
   }

   @Nullable
   public static HeroAction actionFor(String abilityId) {
      if (abilityId == null) {
         return null;
      }

      return switch (abilityId) {
         case SUIT -> HeroAction.SUIT;
         case UNIBEAM -> HeroAction.UNIBEAM;
         case MISSILES -> HeroAction.MISSILES;
         case NANO_ARSENAL -> HeroAction.NANO_ARSENAL;
         case SCAN -> HeroAction.SCAN;
         case COUNTERMEASURES -> HeroAction.COUNTERMEASURES;
         case HELMET -> HeroAction.HELMET;
         case VERONICA -> HeroAction.VERONICA;
         case LEGION -> HeroAction.LEGION;
         default -> null;
      };
   }

   /** Panel grey state from the synced snapshot (client) — pure on the snapshot. */
   static boolean grey(String abilityId, net.minecraft.world.entity.player.Player player) {
      if (!(player instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer heroPlayer)) {
         return false;
      }

      return greyFor(abilityId, heroPlayer.getHeroSnapshot());
   }

   public static boolean greyFor(String abilityId, @Nullable dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot snapshot) {
      if (snapshot == null || snapshot.heroId() != dev.baranhan.viltrumitecore.hero.HeroId.IRON_MAN) {
         return false;
      }

      int flags = snapshot.heroFlags();
      boolean worn = IronManFlags.is(flags, IronManFlags.Field.SUIT_WORN);
      boolean mark = IronManVariant.mark(snapshot.variant()) != null;
      if (hulkActive(snapshot)) {
         return switch (abilityId) {
            case UNIBEAM -> false;
            case MISSILES -> snapshot.extraCooldown(5) > 0;
            case NANO_ARSENAL -> snapshot.extraCooldown(6) > 0 || snapshot.resourceLocked();
            case HELMET -> true;
            case LEGION -> snapshot.extraCooldown(4) > 0;
            default -> false;
         };
      }

      return switch (abilityId) {
         // Nano lost after a core explosion: extra cooldown [0].
         case SUIT -> snapshot.extraCooldown(0) > 0;
         case UNIBEAM -> !worn || snapshot.resourceLocked() || IronManFlags.is(flags, IronManFlags.Field.OVERHEAT_LOCK);
         case MISSILES -> !worn || snapshot.resourceLocked();
         // In a mark slot 3 is the signature (spec §6.2): its cooldown is extra [3].
         case NANO_ARSENAL -> !worn || snapshot.resourceLocked() || mark && snapshot.extraCooldown(3) > 0;
         // Veronica: cooldown after the pod left, extra [2]; callable without armor (spec §4.1).
         case VERONICA -> snapshot.extraCooldown(2) > 0;
         // Scan needs the closed helmet (spec §10); flares: cooldown in extra [1], no energy.
         case SCAN -> !worn || !IronManFlags.is(flags, IronManFlags.Field.HELMET_CLOSED);
         case COUNTERMEASURES -> !worn || snapshot.extraCooldown(1) > 0;
         case HELMET -> !worn;
         // Legion: call cooldown in extra [4]; usable without armor.
         case LEGION -> snapshot.extraCooldown(4) > 0;
         default -> false;
      };
   }
}
