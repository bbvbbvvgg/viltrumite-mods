package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import java.util.EnumMap;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Server-side per-player Thor state. Saved under NBT key {@code Thor}: only
 * where Mjölnir is. The mode and the running move are transient.
 */
public final class ThorState {
   static final String KEY = "Thor";
   static final String HAMMER_KEY = "Hammer";

   public HammerState hammer = HammerState.HOLSTERED;
   /** Transient. */
   public ThorMode mode = ThorMode.NORMAL;
   /** The one staged move player; transient. */
   public final StagedTimeline<ThorMove> timeline = new StagedTimeline<>();
   /** Bridge grab window, ticks left (ThorBridge); transient. */
   public int grabWindow;
   /** Bridge scene lock owner, null when free (ThorBridge); transient. */
   @Nullable
   public String sceneLock;
   /** Move chosen on the press of a held input, with ticks held; the release goes to it. Transient. */
   final EnumMap<ThorInput, ThorMove> pressed = new EnumMap<>(ThorInput.class);
   final EnumMap<ThorInput, Integer> heldTicks = new EnumMap<>(ThorInput.class);

   public void save(CompoundTag nbt) {
      CompoundTag tag = new CompoundTag();
      tag.putString(HAMMER_KEY, this.hammer.key());
      nbt.put(KEY, tag);
   }

   /** A hammer saved in flight (logout mid-throw) is back in the hand; the mode starts normal. */
   public void load(CompoundTag nbt) {
      this.timeline.clear();
      this.mode = ThorMode.NORMAL;
      this.clearTransient();
      if (!nbt.contains(KEY, 10)) {
         return;
      }

      HammerState saved = HammerState.fromKey(nbt.getCompound(KEY).getString(HAMMER_KEY));
      this.hammer = saved.away() ? HammerState.IN_HAND : saved;
   }

   /** Drop the bridge flags and held presses (never saved). */
   void clearTransient() {
      this.grabWindow = 0;
      this.sceneLock = null;
      this.pressed.clear();
      this.heldTicks.clear();
   }

   /** A fresh body: Mjölnir on the belt, normal mode, nothing running. */
   public ThorState respawnCopy() {
      return new ThorState();
   }

   /** Same-entity death keeps nothing of the body either. */
   public ThorState erasedCopy() {
      return this.respawnCopy();
   }

   @Nullable
   public static ThorState of(Player player) {
      if (player instanceof HeroPlayer heroPlayer && heroPlayer.getHeroId() == HeroId.THOR) {
         return heroPlayer.viltrumitecore$getHeroState() instanceof ThorState state ? state : null;
      }

      return null;
   }

   public static ThorState ensure(Player player) {
      HeroPlayer heroPlayer = (HeroPlayer)player;
      if (heroPlayer.viltrumitecore$getHeroState() instanceof ThorState existing) {
         return existing;
      }

      ThorState state = new ThorState();
      heroPlayer.viltrumitecore$setHeroState(state);
      return state;
   }
}
