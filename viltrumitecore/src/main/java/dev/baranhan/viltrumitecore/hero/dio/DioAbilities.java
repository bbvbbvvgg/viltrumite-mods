package dev.baranhan.viltrumitecore.hero.dio;

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

/** DIO ability ids, slot order, icons and panel entries. */
public final class DioAbilities {
   public static final String THE_WORLD = "dio:the_world";
   public static final String BARRAGE = "dio:muda_barrage";
   public static final String KNIVES = "dio:knives";
   public static final String TIME_STOP = "dio:time_stop";
   public static final String DRAIN = "dio:vampire_drain";
   public static final String ROAD_ROLLER = "dio:road_roller";
   /** Panel order = page 1 slots 1..6. */
   private static final String[] OWN = {THE_WORLD, BARRAGE, KNIVES, TIME_STOP, DRAIN, ROAD_ROLLER};
   private static final HeroAction[] ACTIONS = {
      HeroAction.THE_WORLD, HeroAction.MUDA_BARRAGE, HeroAction.KNIFE_THROW, HeroAction.TIME_STOP, HeroAction.VAMPIRE_DRAIN, HeroAction.ROAD_ROLLER
   };

   private DioAbilities() {
   }

   public static String[] slotIds() {
      return OWN.clone();
   }

   public static boolean owns(@Nullable String abilityId) {
      return index(abilityId) >= 0;
   }

   static int index(@Nullable String abilityId) {
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

   @Nullable
   public static HeroAction actionFor(@Nullable String abilityId) {
      int i = index(abilityId);
      return i < 0 ? null : ACTIONS[i];
   }

   /** Cooldown slot of an action (same as the panel index). */
   public static int cooldownIndex(HeroAction action) {
      for (int i = 0; i < ACTIONS.length; i++) {
         if (ACTIONS[i] == action) {
            return i;
         }
      }

      return -1;
   }

   @Nullable
   public static ResourceLocation icon(@Nullable String abilityId) {
      if (!owns(abilityId)) {
         return null;
      }

      return new ResourceLocation("viltrumitecore", "textures/gui/ability/dio/" + abilityId.substring(abilityId.indexOf(':') + 1) + ".png");
   }

   public static List<ViltrumiteAbility> panelAbilities() {
      List<ViltrumiteAbility> list = new ArrayList<>();
      for (int i = 0; i < OWN.length; i++) {
         String id = OWN[i];
         String name = "dio_" + id.substring(id.indexOf(':') + 1);
         HeroAction action = ACTIONS[i];
         list.add(new ViltrumiteAbility(id, icon(id), "ability.viltrumitecore." + name + ".name", "ability.viltrumitecore." + name + ".desc", 0,
            player -> slotGrey(player, action)));
      }

      return list;
   }

   /** Grey while on cooldown or short of blood (time stop / road roller). */
   public static boolean slotGrey(Player player, HeroAction action) {
      if (!(player instanceof HeroPlayer heroPlayer)) {
         return false;
      }

      HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
      if (snapshot == null || snapshot.heroId() != HeroId.DIO) {
         return false;
      }

      int index = cooldownIndex(action);
      if (index >= 0 && index < snapshot.cooldowns().length && snapshot.cooldowns()[index] > 0) {
         return true;
      }

      int blood = snapshot.resource() / 10;
      return switch (action) {
         case TIME_STOP -> blood < DioRules.TIME_STOP_MIN_BLOOD && !snapshot.heroFlag(DioRules.FLAG_TIME_STOP);
         case ROAD_ROLLER -> blood < DioRules.ROLLER_BLOOD;
         default -> false;
      };
   }

   public static String[] defaultLoadout() {
      String[] loadout = new String[18];
      java.util.Arrays.fill(loadout, "");
      System.arraycopy(OWN, 0, loadout, 0, OWN.length);
      return loadout;
   }
}
