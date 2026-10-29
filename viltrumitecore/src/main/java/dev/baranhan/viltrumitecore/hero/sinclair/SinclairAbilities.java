package dev.baranhan.viltrumitecore.hero.sinclair;

import dev.baranhan.viltrumitecore.ability.ViltrumiteAbility;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** Sinclair's loadout (spec §5): five own slots on the first page, keys R, Y, Z, V, B. */
public final class SinclairAbilities {
   public static final String VEXING_BOLT = "sinclair:vexing_bolt";
   public static final String SPECTRAL_ASSISTANT = "sinclair:spectral_assistant";
   public static final String RABBIT_HEX = "sinclair:rabbit_hex";
   public static final String MISDIRECTION = "sinclair:misdirection";
   public static final String AUDIENCE_PARTICIPATION = "sinclair:audience_participation";
   public static final String[] OWN = {VEXING_BOLT, SPECTRAL_ASSISTANT, RABBIT_HEX, MISDIRECTION, AUDIENCE_PARTICIPATION};

   private SinclairAbilities() {
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

   @Nullable
   public static HeroAction actionFor(@Nullable String abilityId) {
      if (abilityId == null) {
         return null;
      }

      return switch (abilityId) {
         case VEXING_BOLT -> HeroAction.VEXING_BOLT;
         case SPECTRAL_ASSISTANT -> HeroAction.SPECTRAL_ASSISTANT;
         case RABBIT_HEX -> HeroAction.RABBIT_HEX;
         case MISDIRECTION -> HeroAction.MISDIRECTION;
         case AUDIENCE_PARTICIPATION -> HeroAction.AUDIENCE_PARTICIPATION;
         default -> null;
      };
   }

   /** Snapshot cooldown index of an own slot, or -1. */
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
      return owns(abilityId) ? new ResourceLocation("viltrumitecore", "textures/gui/ability/sinclair/" + key(abilityId) + ".png") : null;
   }

   public static String[] defaultLoadout() {
      String[] loadout = new String[18];
      java.util.Arrays.fill(loadout, "");
      System.arraycopy(OWN, 0, loadout, 0, OWN.length);
      return loadout;
   }

   public static List<ViltrumiteAbility> panelAbilities() {
      List<ViltrumiteAbility> list = new ArrayList<>();
      for (String id : OWN) {
         String name = "sinclair_" + key(id);
         list.add(new ViltrumiteAbility(id, icon(id), "ability.viltrumitecore." + name + ".name", "ability.viltrumitecore." + name + ".desc", 0,
            player -> slotGrey(player, id)));
      }

      return list;
   }

   /** Grey on cooldown; the assistant and ult slots stay lit while their repeat (swap / perform) is available. */
   public static boolean slotGrey(Player player, String abilityId) {
      if (!(player instanceof HeroPlayer heroPlayer)) {
         return false;
      }

      HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
      if (snapshot == null || snapshot.heroId() != HeroId.SINCLAIR) {
         return false;
      }

      int index = cooldownIndex(abilityId);
      int flags = snapshot.heroFlags();
      if (SinclairFlags.has(flags, SinclairFlags.HEXED)) {
         return true;
      }

      if (index == SinclairFlags.COOLDOWN_ASSIST && SinclairFlags.has(flags, SinclairFlags.ASSIST_ON_STAGE)) {
         return snapshot.extraCooldown(SinclairFlags.EXTRA_SWAPS_LEFT) <= 0;
      }

      if (index == SinclairFlags.COOLDOWN_ULT && SinclairFlags.has(flags, SinclairFlags.CARD_HELD)) {
         return false;
      }

      return index >= 0 && index < snapshot.cooldowns().length && snapshot.cooldowns()[index] > 0;
   }
}
