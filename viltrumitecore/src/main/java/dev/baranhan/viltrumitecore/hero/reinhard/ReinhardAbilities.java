package dev.baranhan.viltrumitecore.hero.reinhard;

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

/** Reinhard's loadout: one page per mode (spec §6, §7.1). */
public final class ReinhardAbilities {
   /** Highest plan stage whose abilities are in the game. Bumped by every stage. */
   public static final int SHIPPED_STAGE = 4;
   /** The shared kit chop joins Holy Sword slot 2 with Stage 4. */
   public static final String CHOP = "viltrumite:chop";
   public static final int CHOP_STAGE = 4;
   public static final int CHOP_INDEX = 13;
   /** Full layout of every stage: page = mode, 6 slots per page. */
   public static final String[] LOADOUT = {
      "reinhard:light_punch", "reinhard:step_dash", "reinhard:catch", "", "", "",
      "reinhard:fist_combo", "reinhard:sky_kick", "reinhard:grab_throw", "reinhard:dash_behind", "reinhard:stomp", "reinhard:reflect",
      "reinhard:reid", CHOP, "reinhard:sheath_strike", "reinhard:cut_rain", "reinhard:world_cut", "reinhard:world_erasure"
   };

   private ReinhardAbilities() {
   }

   @Nullable
   public static ReinhardAbility byId(@Nullable String abilityId) {
      if (abilityId == null) {
         return null;
      }

      for (ReinhardAbility ability : ReinhardAbility.values()) {
         if (ability.id().equals(abilityId)) {
            return ability;
         }
      }

      return null;
   }

   /** LOADOUT with every not yet shipped id blanked. */
   public static String[] defaultLoadout() {
      String[] loadout = new String[LOADOUT.length];
      for (int i = 0; i < LOADOUT.length; i++) {
         loadout[i] = shipped(LOADOUT[i]) ? LOADOUT[i] : "";
      }

      return loadout;
   }

   /** Owned = in the shipped default loadout. */
   public static boolean owns(@Nullable String abilityId) {
      return abilityId != null && !abilityId.isEmpty() && Arrays.asList(LOADOUT).contains(abilityId) && shipped(abilityId);
   }

   private static boolean shipped(String id) {
      if (id.isEmpty()) {
         return false;
      }

      if (CHOP.equals(id)) {
         return CHOP_STAGE <= SHIPPED_STAGE;
      }

      ReinhardAbility ability = byId(id);
      return ability != null && ability.shipped();
   }

   /**
    * Own shipped ids whose keys send hero input. Dash Behind is left out: its key sends
    * {@code ReinhardDashBehindC2SPacket} (target + spot) from {@code DashBehindClient} instead.
    */
   public static String[] inputSlots() {
      return Arrays.stream(ReinhardAbility.values()).filter(ReinhardAbility::shipped).filter(a -> a != ReinhardAbility.DASH_BEHIND)
         .map(ReinhardAbility::id).toArray(String[]::new);
   }

   @Nullable
   public static HeroAction actionFor(@Nullable String abilityId) {
      ReinhardAbility ability = byId(abilityId);
      return ability != null && ability.shipped() ? ability.action() : null;
   }

   @Nullable
   public static ResourceLocation icon(@Nullable String abilityId) {
      if (CHOP.equals(abilityId)) {
         // Stage 4: the kit chop is drawn as Reinhard's Lethal Chop.
         return new ResourceLocation("viltrumitecore", "textures/gui/ability/reinhard/lethal_chop.png");
      }

      ReinhardAbility ability = byId(abilityId);
      return ability == null || !ability.shipped() ? null : iconOf(ability);
   }

   /** Stage 4: the Reid slot shows the drawn blade while Reid is out of the sheath (drawing, drawn, sheathing). */
   @Nullable
   public static ResourceLocation icon(@Nullable String abilityId, int heroFlags) {
      ResourceLocation base = icon(abilityId);
      if (base != null && byId(abilityId) == ReinhardAbility.REID && (ReinhardFlags.has(heroFlags, ReinhardFlags.REID_DRAWN)
         || ReinhardFlags.has(heroFlags, ReinhardFlags.REID_DRAWING) || ReinhardFlags.has(heroFlags, ReinhardFlags.REID_SHEATHING))) {
         return new ResourceLocation("viltrumitecore", "textures/gui/ability/reinhard/" + ReinhardAbility.REID.key() + "_drawn.png");
      }

      return base;
   }

   private static ResourceLocation iconOf(ReinhardAbility ability) {
      return new ResourceLocation("viltrumitecore", "textures/gui/ability/reinhard/" + ability.key() + ".png");
   }

   /** One panel entry per shipped ability (none in Stage 1). */
   public static List<ViltrumiteAbility> panelAbilities() {
      List<ViltrumiteAbility> list = new ArrayList<>();
      for (ReinhardAbility ability : ReinhardAbility.values()) {
         if (!ability.shipped()) {
            continue;
         }

         String name = "reinhard_" + ability.key();
         list.add(new ViltrumiteAbility(ability.id(), iconOf(ability), "ability.viltrumitecore." + name + ".name",
            "ability.viltrumitecore." + name + ".desc", 0, player -> slotGrey(player, ability)));
      }

      return list;
   }

   /** Grey while the ability's extra cooldown runs or Reid's phase forbids it (spec §7.1). */
   public static boolean slotGrey(Player player, ReinhardAbility ability) {
      if (!(player instanceof HeroPlayer heroPlayer)) {
         return false;
      }

      HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
      return snapshot != null && snapshot.heroId() == HeroId.REINHARD && slotGrey(ability, snapshot.extraCooldown(ability.ordinal()), snapshot.heroFlags());
   }

   /** Pure: cooldown running, or the slot condition of Reid's phase (from the snapshot flags) fails. */
   public static boolean slotGrey(ReinhardAbility ability, int cooldown, int heroFlags) {
      return cooldown > 0 || !dev.baranhan.viltrumitecore.hero.reinhard.reid.Reid.allows(ability, dev.baranhan.viltrumitecore.hero.reinhard.reid.Reid.phaseOf(heroFlags));
   }
}
