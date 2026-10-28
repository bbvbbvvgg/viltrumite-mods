package dev.baranhan.viltrumitecore.hero.jinwoo;

import dev.baranhan.viltrumitecore.ability.ViltrumiteAbility;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** Jinwoo's loadout and panel (spec §5): page 1 combat, page 2 army. */
public final class JinwooAbilities {
   public static final String[] LOADOUT = {
      JinwooAbility.SHADOW_STEP.id(), JinwooAbility.MUTILATION.id(), JinwooAbility.AUTHORITY.id(),
      JinwooAbility.EXTRACTION.id(), JinwooAbility.EXCHANGE.id(), JinwooAbility.MONARCH.id(),
      JinwooAbility.SUMMON.id(), JinwooAbility.ATTACK.id(), JinwooAbility.GUARD.id(),
      JinwooAbility.SENTINEL.id(), JinwooAbility.CHARGE.id(), "",
      "", "", "", "", "", ""
   };

   private JinwooAbilities() {
   }

   public static String[] defaultLoadout() {
      return LOADOUT.clone();
   }

   public static boolean owns(@Nullable String id) {
      return JinwooAbility.byId(id) != null;
   }

   public static String[] inputSlots() {
      return Arrays.stream(JinwooAbility.values()).map(JinwooAbility::id).toArray(String[]::new);
   }

   @Nullable
   public static HeroAction actionFor(@Nullable String id) {
      JinwooAbility ability = JinwooAbility.byId(id);
      return ability == null ? null : ability.action();
   }

   @Nullable
   public static ResourceLocation icon(@Nullable String id) {
      JinwooAbility ability = JinwooAbility.byId(id);
      return ability == null ? null : iconOf(ability);
   }

   public static ResourceLocation iconOf(JinwooAbility ability) {
      return new ResourceLocation("viltrumitecore", "textures/gui/ability/jinwoo/" + ability.key() + ".png");
   }

   public static List<ViltrumiteAbility> panelAbilities() {
      List<ViltrumiteAbility> list = new ArrayList<>();
      for (JinwooAbility ability : JinwooAbility.values()) {
         String name = "jinwoo_" + ability.key();
         list.add(new ViltrumiteAbility(ability.id(), iconOf(ability), "ability.viltrumitecore." + name + ".name",
            "ability.viltrumitecore." + name + ".desc", 0, player -> slotGrey(player, ability)));
      }
      return list;
   }

   /** Grey while the cooldown runs, mana is short, or the army it needs is missing. */
   public static boolean slotGrey(Player player, JinwooAbility ability) {
      if (!(player instanceof HeroPlayer heroPlayer)) {
         return false;
      }
      HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
      if (snapshot == null || snapshot.heroId() != HeroId.JINWOO) {
         return false;
      }
      return slotGrey(ability, snapshot.extraCooldown(ability.ordinal()), snapshot.resource() / 10.0F, snapshot.resource2() / 100,
         JinwooFlags.has(snapshot.heroFlags(), JinwooFlags.REMNANT_NEAR));
   }

   /** Pure: the slot state from snapshot values. */
   public static boolean slotGrey(JinwooAbility ability, int cooldown, float mana, int active, boolean remnantNear) {
      if (cooldown > 0 || mana + 1.0E-3F < ability.mana) {
         return true;
      }
      return switch (ability) {
         case ATTACK, GUARD, SENTINEL, CHARGE, EXCHANGE -> active <= 0;
         case EXTRACTION -> !remnantNear;
         default -> false;
      };
   }
}
