package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.ability.ViltrumiteAbility;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Regulus' loadout (spec §5): six own slots on the first page (keys R, Y, Z, V, B, N). Greed's
 * Touch is the empty-hand LMB and Little King the heart key (MMB); neither takes a slot. The
 * HeroAction values are the ones the old kit used (append-only enum), re-purposed.
 */
public final class RegulusAbilities {
   public static final String LIONS_HEART = "regulus:lions_heart";
   public static final String STOPPED_SAND = "regulus:stopped_sand";
   public static final String AIR_SEVERANCE = "regulus:air_severance";
   public static final String PROCESSION = "regulus:procession";
   public static final String RIGHT_OF_REPLY = "regulus:right_of_reply";
   public static final String KINGDOM_OF_ONE = "regulus:kingdom_of_one";
   /** Not slotted: the LMB combo and the heart key (lines and lang only). */
   public static final String GREEDS_TOUCH = "regulus:greeds_touch";
   public static final String LITTLE_KING = "regulus:little_king";

   public static final String[] OWN = {LIONS_HEART, STOPPED_SAND, AIR_SEVERANCE, PROCESSION, RIGHT_OF_REPLY, KINGDOM_OF_ONE};

   /** Retired ids of the first Regulus kit → their successors ("" = dropped). */
   static final Map<String, String> RETIRED = Map.of(
      "regulus:debris_kick", STOPPED_SAND,
      "regulus:mania", AIR_SEVERANCE,
      "regulus:greeds_embrace", PROCESSION,
      "regulus:counter", RIGHT_OF_REPLY,
      "regulus:evangelium", KINGDOM_OF_ONE,
      "regulus:punch", "");

   private RegulusAbilities() {
   }

   public static boolean owns(@Nullable String abilityId) {
      return cooldownIndex(abilityId) >= 0;
   }

   @Nullable
   public static HeroAction actionFor(@Nullable String abilityId) {
      if (abilityId == null) {
         return null;
      }

      return switch (abilityId) {
         case LIONS_HEART -> HeroAction.LIONS_HEART;
         case STOPPED_SAND -> HeroAction.DEBRIS_KICK;
         case AIR_SEVERANCE -> HeroAction.MANIA;
         case PROCESSION -> HeroAction.GREEDS_EMBRACE;
         case RIGHT_OF_REPLY -> HeroAction.COUNTER;
         case KINGDOM_OF_ONE -> HeroAction.ULTIMATE;
         default -> null;
      };
   }

   /** The slot ability of a hero action, or null (LMB / MMB actions have none). */
   @Nullable
   public static String abilityFor(HeroAction action) {
      for (String id : OWN) {
         if (actionFor(id) == action) {
            return id;
         }
      }

      return null;
   }

   /** Snapshot cooldown index of an own slot, or -1 (equals the RegulusFlags COOLDOWN_* order). */
   public static int cooldownIndex(@Nullable String abilityId) {
      if (abilityId == null) {
         return -1;
      }

      for (int i = 0; i < OWN.length; i++) {
         if (OWN[i].equals(abilityId)) {
            return i;
         }
      }

      return -1;
   }

   public static String key(String abilityId) {
      return abilityId.substring(abilityId.indexOf(':') + 1);
   }

   @Nullable
   public static ResourceLocation icon(@Nullable String abilityId) {
      return owns(abilityId) ? new ResourceLocation("viltrumitecore", "textures/gui/ability/regulus/" + key(abilityId) + ".png") : null;
   }

   public static String[] defaultLoadout() {
      String[] loadout = new String[18];
      Arrays.fill(loadout, "");
      System.arraycopy(OWN, 0, loadout, 0, OWN.length);
      return loadout;
   }

   /** The first kit's default page (five abilities and the fist in slot 6). */
   public static String[][] previousDefaultLoadouts() {
      String[] first = new String[18];
      Arrays.fill(first, "");
      String[] old = {"regulus:lions_heart", "regulus:debris_kick", "regulus:mania", "regulus:greeds_embrace", "regulus:counter", "regulus:punch"};
      System.arraycopy(old, 0, first, 0, old.length);
      String[] firstNoFist = first.clone();
      firstNoFist[5] = "";
      return new String[][]{first, firstNoFist};
   }

   /** Maps retired ids in place (customised layouts keep their order). Pure. */
   public static String[] migrate(String[] slots) {
      for (int i = 0; i < slots.length; i++) {
         String next = slots[i] == null ? null : RETIRED.get(slots[i]);
         if (next != null) {
            slots[i] = next;
         }
      }

      return slots;
   }

   public static List<ViltrumiteAbility> panelAbilities() {
      List<ViltrumiteAbility> list = new ArrayList<>();
      for (String id : OWN) {
         String name = "regulus_" + key(id);
         list.add(new ViltrumiteAbility(id, icon(id), "ability.viltrumitecore." + name + ".name", "ability.viltrumitecore." + name + ".desc", 0,
            player -> slotGrey(player, id)));
      }

      return list;
   }

   /** Grey on cooldown, during a seizure, Lion locked, or Procession without the Lion. */
   public static boolean slotGrey(Player player, String abilityId) {
      if (!(player instanceof HeroPlayer heroPlayer)) {
         return false;
      }

      HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
      if (snapshot == null || snapshot.heroId() != HeroId.REGULUS) {
         return false;
      }

      return grey(cooldownIndex(abilityId), snapshot.cooldowns(), snapshot.heroFlags());
   }

   /** Pure grey rule over the wire values. */
   static boolean grey(int index, int[] cooldowns, int flags) {
      if (index < 0) {
         return false;
      }

      if (RegulusFlags.has(flags, RegulusFlags.SEIZED)) {
         return true;
      }

      if (index == RegulusFlags.COOLDOWN_LION) {
         return RegulusFlags.has(flags, RegulusFlags.LION_LOCKED) || index < cooldowns.length && cooldowns[index] > 0 && !RegulusFlags.has(flags, RegulusFlags.LION);
      }

      if (index == RegulusFlags.COOLDOWN_PROCESSION && !RegulusFlags.has(flags, RegulusFlags.LION)) {
         return true;
      }

      return index < cooldowns.length && cooldowns[index] > 0;
   }
}
