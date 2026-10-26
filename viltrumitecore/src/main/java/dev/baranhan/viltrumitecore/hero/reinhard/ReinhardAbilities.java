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
   public static final int SHIPPED_STAGE = 6;
   /** The shared kit chop joins Holy Sword slot 2 with Stage 4. */
   public static final String CHOP = "viltrumite:chop";
   public static final int CHOP_STAGE = 4;
   public static final int CHOP_INDEX = 13;
   /** Full layout of every stage: page = mode, 6 slots per page. */
   public static final String[] LOADOUT = {
      "", "", "reinhard:step_dash", "", "", "",
      "", "reinhard:sky_kick", "reinhard:step_dash", "", "", "",
      "reinhard:reid", CHOP, "reinhard:step_dash", "reinhard:saw_sphere", "reinhard:world_cut", "reinhard:world_erasure"
   };

   /**
    * Former shipped defaults (stage 6 before the quality rework, the rework, the
    * Diagonal World Cut slot, and the merged keys before the Saw Sphere replaced
    * Rain of Cuts). An untouched one moves to {@link #LOADOUT}.
    */
   static final String[][] PREVIOUS_DEFAULTS = {
      {"reinhard:light_punch", "reinhard:step_dash", "reinhard:catch", "", "", "",
         "reinhard:fist_combo", "reinhard:sky_kick", "reinhard:grab_throw", "reinhard:dash_behind", "reinhard:stomp", "reinhard:reflect",
         "reinhard:reid", CHOP, "reinhard:sheath_strike", "reinhard:cut_rain", "reinhard:world_cut", "reinhard:world_erasure"},
      {"", "reinhard:step_dash", "reinhard:catch", "", "", "",
         "", "reinhard:sky_kick", "reinhard:grab_throw", "reinhard:dash_behind", "reinhard:stomp", "reinhard:reflect",
         "reinhard:reid", CHOP, "reinhard:sheath_strike", "reinhard:cut_rain", "reinhard:world_cut", "reinhard:world_erasure"},
      {"", "reinhard:step_dash", "reinhard:catch", "", "", "",
         "", "reinhard:sky_kick", "reinhard:grab_throw", "reinhard:dash_behind", "reinhard:stomp", "reinhard:reflect",
         "reinhard:reid", CHOP, "reinhard:diagonal_world_cut", "reinhard:cut_rain", "reinhard:world_cut", "reinhard:world_erasure"},
      {"", "", "reinhard:step_dash", "", "", "",
         "", "reinhard:sky_kick", "reinhard:step_dash", "", "", "",
         "reinhard:reid", CHOP, "reinhard:step_dash", "reinhard:cut_rain", "reinhard:world_cut", "reinhard:world_erasure"}
   };

   /**
    * Loadout ids retired by the combat rework and what replaced them: Dash Behind,
    * Stomp and Diagonal World Cut became variants of the merged keys; the others
    * moved to LMB/RMB or the automatic defense ("" = the slot empties). Rain of Cuts
    * gave its slot to the Saw Sphere (overhaul item 19).
    */
   private static final java.util.Map<String, String> RETIRED = java.util.Map.of(
      "reinhard:dash_behind", "reinhard:step_dash",
      "reinhard:stomp", "reinhard:sky_kick",
      "reinhard:diagonal_world_cut", "reinhard:world_cut",
      "reinhard:light_punch", "",
      "reinhard:fist_combo", "",
      "reinhard:catch", "",
      "reinhard:reflect", "",
      "reinhard:grab_throw", "",
      "reinhard:sheath_strike", "",
      "reinhard:cut_rain", "reinhard:saw_sphere");

   private ReinhardAbilities() {
   }

   /**
    * Pure: a saved loadout with every retired id replaced, page by page (6 slots).
    * The successor takes the retired slot unless the page already holds it; a
    * freed slot then takes this version's default for that slot when the page
    * lacks that id. Every other slot stays where the player put it.
    */
   public static String[] migrate(String[] slots) {
      String[] out = new String[slots.length];
      for (int i = 0; i < slots.length; i++) {
         out[i] = slots[i] == null ? "" : slots[i];
      }

      for (int from = 0; from < out.length; from += 6) {
         int to = Math.min(out.length, from + 6);
         boolean[] freed = new boolean[to - from];
         for (int i = from; i < to; i++) {
            String next = RETIRED.get(out[i]);
            if (next == null) {
               continue;
            }

            out[i] = next.isEmpty() || onPage(out, from, to, next) ? "" : next;
            freed[i - from] = out[i].isEmpty();
         }

         for (int i = from; i < to; i++) {
            String id = i < LOADOUT.length && shipped(LOADOUT[i]) ? LOADOUT[i] : "";
            if (freed[i - from] && !id.isEmpty() && !onPage(out, from, to, id)) {
               out[i] = id;
            }
         }
      }

      return out;
   }

   private static boolean onPage(String[] slots, int from, int to, String id) {
      for (int i = from; i < to; i++) {
         if (id.equals(slots[i])) {
            return true;
         }
      }

      return false;
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

   public static boolean active(ReinhardAbility ability) {
      return ability.shipped() && switch (ability) {
         case STEP_DASH, SKY_KICK, REID, SAW_SPHERE, WORLD_CUT, WORLD_ERASURE -> true;
         default -> false;
      };
   }

   private static boolean shipped(String id) {
      if (id.isEmpty()) {
         return false;
      }

      if (CHOP.equals(id)) {
         return CHOP_STAGE <= SHIPPED_STAGE;
      }

      ReinhardAbility ability = byId(id);
      return ability != null && active(ability);
   }

   /**
    * Own shipped ids whose keys send hero input. Dash Behind has no slot: a Knight's press
    * of the merged dash key with a target is sent as {@code ReinhardDashBehindC2SPacket}
    * (target + spot) by {@code DashBehindClient} instead of this generic press.
    */
   public static String[] inputSlots() {
      return Arrays.stream(ReinhardAbility.values()).filter(ReinhardAbilities::active).filter(a -> a != ReinhardAbility.DASH_BEHIND)
         .map(ReinhardAbility::id).toArray(String[]::new);
   }

   @Nullable
   public static HeroAction actionFor(@Nullable String abilityId) {
      ReinhardAbility ability = byId(abilityId);
      return ability != null && active(ability) ? ability.action() : null;
   }

   @Nullable
   public static ResourceLocation icon(@Nullable String abilityId) {
      if (CHOP.equals(abilityId)) {
         // Stage 4: the kit chop is drawn as Reinhard's Lethal Chop.
         return new ResourceLocation("viltrumitecore", "textures/gui/ability/reinhard/lethal_chop.png");
      }

      ReinhardAbility ability = byId(abilityId);
      return ability == null || !active(ability) ? null : iconOf(ability);
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

   /** The one world-cut slot previews the variant selected by crouching. No extra binding. */
   @Nullable
   public static ResourceLocation icon(@Nullable String id,int flags,boolean crouching) {
      return byId(id)==ReinhardAbility.WORLD_CUT && crouching ? iconOf(ReinhardAbility.DIAGONAL_WORLD_CUT) : icon(id,flags);
   }

   public static ReinhardAbility contextual(ReinhardAbility ability,boolean crouching) {
      if(crouching && ability==ReinhardAbility.WORLD_CUT)return ReinhardAbility.DIAGONAL_WORLD_CUT;
      if(crouching && ability==ReinhardAbility.SKY_KICK)return ReinhardAbility.STOMP;
      return ability;
   }

   private static ResourceLocation iconOf(ReinhardAbility ability) {
      return new ResourceLocation("viltrumitecore", "textures/gui/ability/reinhard/" + ability.key() + ".png");
   }

   /** One panel entry per shipped ability (none in Stage 1). */
   public static List<ViltrumiteAbility> panelAbilities() {
      List<ViltrumiteAbility> list = new ArrayList<>();
      for (ReinhardAbility ability : ReinhardAbility.values()) {
         if (!active(ability)) {
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
      if (snapshot == null || snapshot.heroId() != HeroId.REINHARD) {
         return false;
      }

      boolean knight = ReinhardMode.byOrdinal(ReinhardFlags.mode(snapshot.heroFlags())) == ReinhardMode.KNIGHT;
      ReinhardAbility selected = selected(ability, player.isShiftKeyDown(), knight,
         knight && ability == ReinhardAbility.STEP_DASH && dev.baranhan.viltrumitecore.hero.reinhard.ability.DashBehind.lookTarget(player) != null);
      return slotGrey(ability, snapshot.extraCooldown(selected.snapshotIndex()), snapshot.heroFlags(),player.isShiftKeyDown());
   }

   /**
    * Pure: the variant a slot press starts now, whose cooldown the slot shows: the
    * crouch variants, and the Knight's merged dash key with a target is Dash Behind
    * (the same choice the client makes when it sends the press).
    */
   public static ReinhardAbility selected(ReinhardAbility root, boolean crouching, boolean knight, boolean behindTarget) {
      ReinhardAbility ability = contextual(root, crouching);
      return ability == ReinhardAbility.STEP_DASH && knight && behindTarget ? ReinhardAbility.DASH_BEHIND : ability;
   }

   /** Pure: cooldown running, or the slot condition of Reid's phase (from the snapshot flags) fails. */
   public static boolean slotGrey(ReinhardAbility ability, int cooldown, int heroFlags) {
      return slotGrey(ability,cooldown,heroFlags,false);
   }

   public static boolean slotGrey(ReinhardAbility root,int cooldown,int heroFlags,boolean crouching) {
      ReinhardAbility ability=contextual(root,crouching);
      return !active(root) || ((ability==ReinhardAbility.WORLD_CUT || ability==ReinhardAbility.DIAGONAL_WORLD_CUT) && ReinhardFlags.has(heroFlags,ReinhardFlags.WORLD_CUT_ACTIVE))
         || (ability==ReinhardAbility.WORLD_ERASURE && ReinhardFlags.has(heroFlags,ReinhardFlags.WORLD_ERASURE_ACTIVE))
         || (ability==ReinhardAbility.SAW_SPHERE && ReinhardFlags.has(heroFlags,ReinhardFlags.SAW_SPHERE_ACTIVE)) || cooldown > 0 || !dev.baranhan.viltrumitecore.hero.reinhard.reid.Reid.allows(ability, dev.baranhan.viltrumitecore.hero.reinhard.reid.Reid.phaseOf(heroFlags));
   }
}
