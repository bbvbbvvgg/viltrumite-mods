package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.PersistentCooldowns;
import javax.annotation.Nullable;
import net.minecraft.world.entity.player.Player;

/**
 * Foundation allocation of Thor's cooldowns, like ThorFlags for bits: the
 * shared {@link PersistentCooldowns} keys the sessions write (online time;
 * relog, hero change and death keep them — OPEN(owner): whether death should
 * reset them; conservative: keep) and their indices in
 * {@link HeroPublicSnapshot#cooldowns()} (meaning owned by Thor). A slot
 * index equals the panel slot order of ThorAbilities, so the HUD greys the
 * slot while it recharges; the storm (held RMB, no slot) comes after the
 * slots. Append only.
 */
public final class ThorCooldowns {
   /** Session B: the electric storm (held RMB). */
   public static final String STORM = "thor:storm";
   /** Session B: the ultimate. */
   public static final String WRATH = "thor:wrath";

   /** Snapshot index of the ultimate = its panel slot (ThorAbilities: thunder, onslaught, wrath, teleport). */
   public static final int WRATH_INDEX = 2;
   /** Snapshot index of the storm (no panel slot). */
   public static final int STORM_INDEX = 4;

   private ThorCooldowns() {
   }

   /** Server: the snapshot cooldown array of this Thor (ticks left per index). */
   public static int[] wire(@Nullable Player player) {
      int[] cooldowns = new int[HeroPublicSnapshot.COOLDOWN_COUNT];
      if (player != null) {
         cooldowns[WRATH_INDEX] = PersistentCooldowns.get(player, WRATH);
         cooldowns[STORM_INDEX] = PersistentCooldowns.get(player, STORM);
      }

      return cooldowns;
   }

   /** Snapshot index of the cooldown a slot input waits for, -1 when the input has none. */
   public static int index(ThorInput input) {
      return input == ThorInput.ULTIMATE ? WRATH_INDEX : -1;
   }

   /** Pure: the input's slot is recharging in this snapshot cooldown array. */
   public static boolean recharging(@Nullable int[] cooldowns, ThorInput input) {
      int index = index(input);
      return cooldowns != null && index >= 0 && index < cooldowns.length && cooldowns[index] > 0;
   }
}
